package com.admodule.next.genads.adsImp.manager

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.gms.ads.mediation.admob.AdMobAdapter
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig
import com.admodule.next.genads.adsImp.AdUnitIds
import com.admodule.next.genads.adsImp.data.NetworkMonitor
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class AdsManager(
    internal val context: Context,
    val networkMonitor: NetworkMonitor
) {
    private val TAG = "AdsManager"

    companion object {
        // Google's sample AdMob App ID. Only a fallback: the real App ID is read from the
        // com.google.android.gms.ads.APPLICATION_ID manifest meta-data (see [appId]).
        const val APP_ID = "ca-app-pub-3940256099942544~3347511713"

        private const val APP_ID_META_DATA = "com.google.android.gms.ads.APPLICATION_ID"

        /** How long a [skipNextAppOpen] request stays valid (covers a long visit to an ad's landing page). */
        private const val APP_OPEN_SKIP_WINDOW_MS = 30 * 60 * 1000L
    }

    /** Process-wide background scope for SDK init, loads and retries (the SDK must run off main). */
    internal val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e ->
            // Last-resort net: an unexpected SDK exception must never crash the host app.
            Log.e(TAG, "Uncaught exception in ads scope", e)
        }
    )

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val initStarted = AtomicBoolean(false)
    private val pendingInitCallbacks = ConcurrentLinkedQueue<() -> Unit>()

    /** True between a full-screen ad's show() and its dismiss/fail — blocks a stacked App Open ad. */
    internal val fullScreenAdShowing = AtomicBoolean(false)
    val isFullScreenAdShowing: Boolean get() = fullScreenAdShowing.get()

    private val skipNextAppOpenAt = AtomicLong(0L)

    @Volatile
    var isAppInForeground: Boolean = false
        private set

    /**
     * True while the process is STARTED, read straight from [ProcessLifecycleOwner] — unlike
     * [isAppInForeground] it never depends on observer ordering. Full-screen ads may only be shown
     * while this holds: popping one after the user left the app violates AdMob policy.
     */
    internal val canShowFullScreenAd: Boolean
        get() = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

    private val nativeAdManagers = ConcurrentHashMap<String, NativeAdManager>()

    private val eventListeners = CopyOnWriteArrayList<AdEventListener>()

    /**
     * Process-wide connectivity, backed by ONE network callback. [NetworkMonitor.isOnline] is cold:
     * collecting it per ad load / per ad slot registered a new ConnectivityManager callback each time
     * (an IPC, and Android caps an app at 100 registered callbacks). Starts optimistic so a slow first
     * callback never blocks a load; a real offline request just fails with NETWORK_ERROR.
     */
    val isOnline: StateFlow<Boolean> =
        networkMonitor.isOnline.stateIn(scope, SharingStarted.Eagerly, true)

    init {
        // Collect network changes to reactively preload or handle states if needed
        scope.launch {
            isOnline.collect { online ->
                Log.d(TAG, "Network status changed: Online=$online")
                if (online && _isInitialized.value) {
                    // Trigger preloads when internet returns if not already loaded
                    preloadAllWhenReady()
                }
            }
        }

        // Track foreground state: retries pause in the background, and coming back to the foreground
        // refills any cache that expired or gave up while the app was away.
        CoroutineScope(Dispatchers.Main).launch {
            ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    isAppInForeground = true
                    if (_isInitialized.value) preloadAllWhenReady()
                }

                override fun onStop(owner: LifecycleOwner) {
                    isAppInForeground = false
                }
            })
        }
    }

    /**
     * Initializes the Next Gen GMA SDK. Safe to call more than once (e.g. early for returning users
     * who already consented AND again after the consent refresh) — only the first call initializes,
     * and every [onComplete] is delivered on the main thread once initialization finishes (or fails —
     * check [isInitialized] inside it).
     */
    fun initialize(onComplete: () -> Unit = {}) {
        if (_isInitialized.value) {
            onComplete()
            return
        }
        pendingInitCallbacks.add(onComplete)
        // Initialization may have completed between the check above and the enqueue.
        if (_isInitialized.value) {
            drainInitCallbacks()
            return
        }
        if (!initStarted.compareAndSet(false, true)) return

        // The Next Gen SDK must be initialized (and ads loaded) off the main thread.
        scope.launch {
            try {
                MobileAds.initialize(
                    context,
                    InitializationConfig.Builder(appId).build()
                ) {
                    Log.i(TAG, "Next Gen GMA SDK initialized.")
                    _isInitialized.value = true
                    preloadAllWhenReady()
                    drainInitCallbacks()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing Next Gen GMA SDK: ${e.message}")
                _isInitialized.value = false
                initStarted.set(false)
                drainInitCallbacks()
            }
        }
    }

    private fun drainInitCallbacks() {
        // The SDK reports init on a worker thread; callers typically touch UI in onComplete.
        CoroutineScope(Dispatchers.Main).launch {
            while (true) {
                val callback = pendingInitCallbacks.poll() ?: return@launch
                try {
                    callback()
                } catch (e: Exception) {
                    Log.e(TAG, "initialize() onComplete threw", e)
                }
            }
        }
    }

    /**
     * The AdMob App ID from the manifest meta-data (set via manifestPlaceholders["admobAppId"]).
     * Same value UMP uses, so consent and ads can never disagree on the app.
     */
    val appId: String by lazy {
        val fromManifest = try {
            context.packageManager
                .getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
                .metaData?.getString(APP_ID_META_DATA)
        } catch (e: Exception) {
            null
        }
        if (fromManifest.isNullOrBlank()) {
            Log.w(TAG, "No $APP_ID_META_DATA meta-data found; falling back to the sample App ID.")
            APP_ID
        } else {
            fromManifest
        }
    }

    /**
     * Builds a standard full-screen / generic AdRequest. In the Next Gen SDK the ad unit ID
     * is supplied on the request itself rather than on the load call.
     */
    fun buildAdRequest(adUnitId: String): AdRequest {
        return AdRequest.Builder(adUnitId).build()
    }

    /**
     * Builds a banner AdRequest, optionally enabling a collapsible banner via the AdMob
     * source extras bundle (the Next Gen SDK still uses the "collapsible" extra).
     */
    fun buildBannerAdRequest(
        adUnitId: String,
        adSize: AdSize,
        isCollapsible: Boolean = false,
        collapsibleDirection: String = "bottom"
    ): BannerAdRequest {
        val builder = BannerAdRequest.Builder(adUnitId, adSize)
        if (isCollapsible) {
            val extras = Bundle().apply {
                putString("collapsible", collapsibleDirection)
            }
            builder.putAdSourceExtrasBundle(AdMobAdapter::class.java, extras)
            Log.d(TAG, "Added collapsible extras direction=$collapsibleDirection to BannerAdRequest")
        }
        return builder.build()
    }

    /**
     * Checks if the app is online before requesting ads.
     */
    suspend fun isNetworkAvailable(): Boolean = isOnline.value

    /**
     * The shared native ad pool for [adUnitId]. Every native composable using the same unit draws
     * from (and refills) this one pool, so a loaded native ad is never thrown away unseen.
     */
    fun nativeAds(adUnitId: String = AdUnitIds.NATIVE): NativeAdManager =
        nativeAdManagers.computeIfAbsent(adUnitId) { NativeAdManager(this, it) }

    /**
     * Registers a listener for every [AdEvent] (requests, loads, failures, impressions, clicks,
     * revenue…) across all formats — e.g. to forward to analytics, or use [AdStats] to compute match
     * rate and show rate. May be called on any thread.
     */
    fun addEventListener(listener: AdEventListener) {
        eventListeners.addIfAbsent(listener)
    }

    fun removeEventListener(listener: AdEventListener) {
        eventListeners.remove(listener)
    }

    internal fun emit(event: AdEvent) {
        for (listener in eventListeners) {
            try {
                listener.onAdEvent(event)
            } catch (e: Exception) {
                Log.e(TAG, "AdEventListener threw on $event", e)
            }
        }
    }

    /**
     * Suppresses the App Open ad on the NEXT return to foreground. Called automatically when a
     * banner/native ad is clicked; call it yourself before sending the user out of the app on purpose
     * (share sheet, in-app purchase, system settings, a permission screen…).
     */
    fun skipNextAppOpen() = skipNextAppOpenAt.set(System.currentTimeMillis())

    /**
     * True (once) if a skip was requested recently. The window bounds a skip that never got consumed
     * — e.g. a click that didn't leave the app — so it can't silently eat an App Open hours later.
     */
    internal fun consumeAppOpenSkip(): Boolean {
        val at = skipNextAppOpenAt.getAndSet(0L)
        return at != 0L && System.currentTimeMillis() - at <= APP_OPEN_SKIP_WINDOW_MS
    }

    // Forward preloads to specific lazy-initialized manager hooks
    @Volatile
    private var preloaderCallback: (() -> Unit)? = null

    fun setPreloaderCallback(callback: () -> Unit) {
        preloaderCallback = callback
        if (_isInitialized.value) {
            callback.invoke()
        }
    }

    private fun preloadAllWhenReady() {
        preloaderCallback?.invoke()
    }
}
