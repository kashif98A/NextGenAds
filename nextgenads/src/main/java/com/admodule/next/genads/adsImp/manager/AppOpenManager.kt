package com.admodule.next.genads.adsImp.manager

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAd
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.admodule.next.genads.adsImp.AdUnitIds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

/**
 * App Open ads. Besides manual [showAd], the ad is shown automatically every time the app returns
 * to the foreground ([showOnResume]) over the current Activity — unless another full-screen ad is
 * on screen, the user is coming back from an ad click / [AdsManager.skipNextAppOpen], or the
 * Activity is listed in [excludedActivities] (e.g. a splash screen).
 */
class AppOpenManager(
    private val adsManager: AdsManager,
    var adUnitId: String = AdUnitIds.APP_OPEN,
    var isEnabled: Boolean = true
) : DefaultLifecycleObserver, Application.ActivityLifecycleCallbacks {

    private val TAG = "AppOpenManager"

    /** Show the cached ad automatically when the app comes back to the foreground. */
    var showOnResume: Boolean = true

    /** Activities over which the resume ad must never appear (splash, checkout, video player…). */
    val excludedActivities: MutableSet<Class<out Activity>> = mutableSetOf()

    /** AdMob App Open ads expire after 4 hours. */
    var expiryMs: Long = 4 * 3600 * 1000L

    @Volatile
    private var appOpenAd: AppOpenAd? = null
    @Volatile
    private var loadTime: Long = 0
    @Volatile
    private var currentActivity: WeakReference<Activity>? = null

    private val loader = AdLoadController(adsManager, TAG)

    @Volatile
    private var requestStartedAt: Long = 0

    /** Ad unit the cached ad was loaded from (events credit it, even if [adUnitId] changes later). */
    @Volatile
    private var loadedUnitId: String = adUnitId

    private val _isAdLoaded = MutableStateFlow(false)
    val isAdLoaded: StateFlow<Boolean> = _isAdLoaded.asStateFlow()

    init {
        // Track the foreground Activity so the resume ad has something to show over.
        (adsManager.context.applicationContext as Application).registerActivityLifecycleCallbacks(this)
        // Register observer to detect app foreground events dynamically
        CoroutineScope(Dispatchers.Main).launch {
            ProcessLifecycleOwner.get().lifecycle.addObserver(this@AppOpenManager)
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        Log.d(TAG, "App coming to core foreground. Checking state of App Open Ad.")
        showAdIfReady()
    }

    /**
     * Checks if cached ad is loaded and has not expired.
     */
    fun isAdAvailable(): Boolean {
        val isExpired = System.currentTimeMillis() - loadTime > expiryMs
        return isEnabled && appOpenAd != null && !isExpired
    }

    /**
     * Preloads the App Open Ad.
     */
    fun loadAd() {
        loader.resetRetries()
        load()
    }

    /** Drops a cached ad that expired unseen and reports it (a lost impression). */
    @Synchronized
    private fun discardIfExpired() {
        if (appOpenAd != null && System.currentTimeMillis() - loadTime > expiryMs) {
            appOpenAd = null
            _isAdLoaded.value = false
            adsManager.emit(AdEvent.Expired(AdFormat.APP_OPEN, loadedUnitId))
        }
    }

    private fun load() {
        if (!isEnabled) {
            Log.d(TAG, "App Open disabled (isEnabled=false); skipping load.")
            return
        }
        // Never request an ad before the SDK is initialized (which only happens after GDPR consent).
        if (!adsManager.isInitialized.value) {
            Log.d(TAG, "Ads SDK not initialized yet; skipping App Open load until consent + init.")
            return
        }
        discardIfExpired()
        if (isAdAvailable() || !loader.tryStart()) return

        Log.d(TAG, "Triggering load for App Open Ad unit ID: $adUnitId")

        // The Next Gen SDK requires ads to be loaded off the main thread.
        adsManager.scope.launch {
            if (!adsManager.isNetworkAvailable()) {
                Log.d(TAG, "No network available. Load resumes when the network returns.")
                loader.onAborted()
                return@launch
            }

            val unit = adUnitId
            requestStartedAt = System.currentTimeMillis()
            adsManager.emit(AdEvent.LoadRequested(AdFormat.APP_OPEN, unit))
            try {
                AppOpenAd.load(
                    adsManager.buildAdRequest(unit),
                    object : AdLoadCallback<AppOpenAd> {
                        override fun onAdLoaded(ad: AppOpenAd) {
                            // Publish the ad LAST, so whoever sees it also sees its loadTime and unit
                            // (otherwise a concurrent showAd() reads loadTime == 0 and drops it as expired).
                            loadTime = System.currentTimeMillis()
                            loadedUnitId = unit
                            appOpenAd = ad
                            _isAdLoaded.value = true
                            loader.onLoaded()
                            adsManager.emit(AdEvent.Loaded(AdFormat.APP_OPEN, unit, System.currentTimeMillis() - requestStartedAt))
                            Log.i(TAG, "App Open Ad loaded successfully.")
                        }

                        override fun onAdFailedToLoad(adError: LoadAdError) {
                            _isAdLoaded.value = false
                            appOpenAd = null
                            Log.e(TAG, "App Open Ad failed to load: ${adError.message} (code: ${adError.code})")
                            val retryIn = loader.onFailed(adError.code) { load() }
                            adsManager.emit(AdEvent.LoadFailed(AdFormat.APP_OPEN, unit, adError.code, adError.message, retryIn))
                        }
                    }
                )
            } catch (e: Exception) {
                // A synchronous SDK throw must not leave the load slot claimed forever.
                Log.e(TAG, "App Open load() threw: ${e.message}")
                loader.onAborted()
            }
        }
    }

    /**
     * Shows an App Open ad if available. Pass the active foreground Activity to ensure safety.
     */
    fun showAd(activity: Activity, onDismiss: () -> Unit = {}) {
        if (!isEnabled) {
            Log.d(TAG, "App Open disabled (isEnabled=false); nothing to show.")
            onDismiss()
            return
        }
        discardIfExpired()
        if (!isAdAvailable()) {
            Log.d(TAG, "Cannot show App Open Ad: Ad is not available.")
            loadAd()
            onDismiss()
            return
        }
        // AdMob policy: never pop a full-screen ad once the user has left the app (e.g. they pressed
        // Home while a loader was waiting for this ad).
        if (!adsManager.canShowFullScreenAd || activity.isFinishing || activity.isDestroyed) {
            Log.d(TAG, "App is in the background or the activity is gone; not showing App Open.")
            onDismiss()
            return
        }
        if (!adsManager.fullScreenAdShowing.compareAndSet(false, true)) {
            Log.d(TAG, "Another full-screen ad is already showing; skipping App Open.")
            onDismiss()
            return
        }

        val ad = appOpenAd ?: run {
            adsManager.fullScreenAdShowing.set(false)
            onDismiss()
            return
        }

        // Take the ad out of the cache while it is on screen: a preload during the show then fills
        // the cache with the NEXT ad (instead of being nulled by this ad's dismiss), and an ad that
        // crosses its expiry while showing is never reported as Expired.
        val shownUnit = loadedUnitId
        appOpenAd = null
        _isAdLoaded.value = false

        ad.adEventCallback = object : AppOpenAdEventCallback {
            override fun onAdImpression() = adsManager.emit(AdEvent.Impression(AdFormat.APP_OPEN, shownUnit))
            override fun onAdClicked() = adsManager.emit(AdEvent.Clicked(AdFormat.APP_OPEN, shownUnit))
            override fun onAdPaid(value: AdValue) = adsManager.emit(value.toPaid(AdFormat.APP_OPEN, shownUnit))

            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "App Open Ad dismissed.")
                adsManager.emit(AdEvent.Dismissed(AdFormat.APP_OPEN, shownUnit))
                adsManager.fullScreenAdShowing.set(false)
                // Immediately preload the next App Open Ad
                loadAd()
                // SDK callbacks arrive on a background thread; run the UI-facing onDismiss on main.
                CoroutineScope(Dispatchers.Main).launch { onDismiss() }
            }

            override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) {
                Log.e(TAG, "App Open Ad failed to show: ${fullScreenContentError.message}")
                adsManager.emit(AdEvent.ShowFailed(AdFormat.APP_OPEN, shownUnit, fullScreenContentError.message))
                adsManager.fullScreenAdShowing.set(false)
                // Immediately preload next App Open Ad
                loadAd()
                CoroutineScope(Dispatchers.Main).launch { onDismiss() }
            }

            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "App Open Ad shown successfully.")
            }
        }

        try {
            ad.show(activity)
        } catch (e: Exception) {
            Log.e(TAG, "App Open show() threw: ${e.message}")
            adsManager.fullScreenAdShowing.set(false)
            // The ad was never shown: put it back unless a newer one has been cached meanwhile.
            if (appOpenAd == null) {
                appOpenAd = ad
                _isAdLoaded.value = true
            }
            onDismiss()
        }
    }

    /**
     * Shows the cached ad over the current foreground Activity when the app returns to the
     * foreground. Called automatically from [onStart]; never stacks on another full-screen ad.
     */
    fun showAdIfReady() {
        if (!isEnabled || !showOnResume) return
        if (adsManager.consumeAppOpenSkip()) {
            Log.d(TAG, "Resume App Open skipped (returning from an ad click / skipNextAppOpen).")
            return
        }
        if (adsManager.isFullScreenAdShowing) {
            Log.d(TAG, "A full-screen ad is on screen; not stacking an App Open ad.")
            return
        }
        val activity = currentActivity?.get()
        if (activity == null || activity.isFinishing || activity.isDestroyed) {
            // Cold start: no Activity yet. Just make sure an ad is cached for the next resume.
            loadAd()
            return
        }
        if (excludedActivities.any { it.isInstance(activity) }) {
            Log.d(TAG, "${activity.javaClass.simpleName} is excluded from App Open ads.")
            return
        }
        if (!isAdAvailable()) {
            loadAd()
            return
        }
        showAd(activity)
    }

    // --- Application.ActivityLifecycleCallbacks: track the foreground Activity ---

    override fun onActivityStarted(activity: Activity) {
        // Ignore the SDK's own ad Activities — they must never host an App Open ad.
        if (!isAdActivity(activity)) currentActivity = WeakReference(activity)
    }

    override fun onActivityResumed(activity: Activity) {
        if (!isAdActivity(activity)) currentActivity = WeakReference(activity)
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivity?.get() === activity) currentActivity = null
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    private fun isAdActivity(activity: Activity): Boolean {
        val name = activity.javaClass.name
        return name.startsWith("com.google.android.libraries.ads.") ||
            name.startsWith("com.google.android.gms.ads.")
    }
}
