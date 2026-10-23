package com.admodule.next.genads.adsImp.manager

import android.app.Activity
import android.util.Log
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdEventCallback
import com.admodule.next.genads.adsImp.AdUnitIds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class InterstitialManager(
    private val adsManager: AdsManager,
    var adUnitId: String = AdUnitIds.INTERSTITIAL,
    var isEnabled: Boolean = true
) {
    private val TAG = "InterstitialManager"

    /**
     * How long a cached ad is considered fresh. Kept at 1 hour (not the 4 hours App Open allows):
     * a stale cached ad is a matched request that will likely never turn into an impression.
     */
    var expiryMs: Long = 3600 * 1000L

    @Volatile
    private var interstitialAd: InterstitialAd? = null
    @Volatile
    private var loadTime: Long = 0

    private val loader = AdLoadController(adsManager, TAG)

    @Volatile
    private var requestStartedAt: Long = 0

    /** Ad unit the cached ad was loaded from (events credit it, even if [adUnitId] changes later). */
    @Volatile
    private var loadedUnitId: String = adUnitId

    private val _isAdLoaded = MutableStateFlow(false)
    val isAdLoaded: StateFlow<Boolean> = _isAdLoaded.asStateFlow()

    /** True from a [loadAndShow] call until its ad closes or fails — one on-demand flow at a time. */
    private val onDemandInFlight = AtomicBoolean(false)

    /**
     * Verifies if an interstitial ad is loaded and unexpired.
     */
    fun isAdAvailable(): Boolean {
        return isEnabled && interstitialAd != null && !isExpired()
    }

    /**
     * Preloads the Interstitial ad if not already loaded or currently loading.
     */
    fun loadAd() {
        loader.resetRetries()
        load()
    }

    /** Drops a cached ad that expired unseen and reports it (a lost impression). */
    @Synchronized
    private fun discardIfExpired() {
        if (interstitialAd != null && System.currentTimeMillis() - loadTime > expiryMs) {
            interstitialAd = null
            _isAdLoaded.value = false
            adsManager.emit(AdEvent.Expired(AdFormat.INTERSTITIAL, loadedUnitId))
        }
    }

    private fun load() {
        if (!isEnabled) {
            Log.d(TAG, "Interstitial disabled (isEnabled=false); skipping load.")
            return
        }
        // Never request an ad before the SDK is initialized (which only happens after GDPR consent).
        if (!adsManager.isInitialized.value) {
            Log.d(TAG, "Ads SDK not initialized yet; skipping Interstitial load until consent + init.")
            return
        }
        discardIfExpired()
        if ((interstitialAd != null && !isExpired()) || !loader.tryStart()) return

        Log.d(TAG, "Triggering load for Interstitial Ad: $adUnitId")

        // The Next Gen SDK requires ads to be loaded off the main thread.
        adsManager.scope.launch {
            if (!adsManager.isNetworkAvailable()) {
                Log.d(TAG, "No network available. Load resumes when the network returns.")
                loader.onAborted()
                return@launch
            }

            val unit = adUnitId
            requestStartedAt = System.currentTimeMillis()
            adsManager.emit(AdEvent.LoadRequested(AdFormat.INTERSTITIAL, unit))
            try {
                InterstitialAd.load(
                    adsManager.buildAdRequest(unit),
                    object : AdLoadCallback<InterstitialAd> {
                        override fun onAdLoaded(ad: InterstitialAd) {
                            // Publish the ad LAST, so whoever sees it also sees its loadTime and unit
                            // (otherwise a concurrent showAd() reads loadTime == 0 and drops it as expired).
                            loadTime = System.currentTimeMillis()
                            loadedUnitId = unit
                            interstitialAd = ad
                            _isAdLoaded.value = true
                            loader.onLoaded()
                            adsManager.emit(AdEvent.Loaded(AdFormat.INTERSTITIAL, unit, System.currentTimeMillis() - requestStartedAt))
                            Log.i(TAG, "Interstitial Ad loaded successfully.")
                        }

                        override fun onAdFailedToLoad(adError: LoadAdError) {
                            _isAdLoaded.value = false
                            interstitialAd = null
                            Log.e(TAG, "Interstitial Ad failed to load: ${adError.message} (code: ${adError.code})")
                            val retryIn = loader.onFailed(adError.code) { load() }
                            adsManager.emit(AdEvent.LoadFailed(AdFormat.INTERSTITIAL, unit, adError.code, adError.message, retryIn))
                        }
                    }
                )
            } catch (e: Exception) {
                // A synchronous SDK throw must not leave the load slot claimed forever.
                Log.e(TAG, "Interstitial load() threw: ${e.message}")
                loader.onAborted()
            }
        }
    }

    private fun isExpired(): Boolean {
        return System.currentTimeMillis() - loadTime > expiryMs
    }

    /**
     * Displays Interstitial Ad. Safety-ensured with an active Activity parameter.
     */
    fun showAd(activity: Activity, onDismiss: () -> Unit = {}) {
        if (!isEnabled) {
            Log.d(TAG, "Interstitial disabled (isEnabled=false); nothing to show.")
            onDismiss()
            return
        }
        discardIfExpired()
        if (!isAdAvailable()) {
            Log.d(TAG, "Cannot show Interstitial Ad. Reason: " +
                if (interstitialAd == null) "Ad not loaded." else "Cached ad expired."
            )
            // Load if missing or expired
            loadAd()
            onDismiss()
            return
        }

        // AdMob policy: never pop a full-screen ad once the user has left the app (e.g. they pressed
        // Home while a loader was waiting for this ad).
        if (!adsManager.canShowFullScreenAd || activity.isFinishing || activity.isDestroyed) {
            Log.d(TAG, "App is in the background or the activity is gone; not showing Interstitial.")
            onDismiss()
            return
        }
        // Claim the single full-screen slot: never stack on another full-screen ad (e.g. an App Open
        // on screen) and never show the same cached ad twice on a double tap.
        if (!adsManager.fullScreenAdShowing.compareAndSet(false, true)) {
            Log.d(TAG, "Another full-screen ad is already showing; skipping Interstitial.")
            onDismiss()
            return
        }

        val ad = interstitialAd ?: run {
            adsManager.fullScreenAdShowing.set(false)
            onDismiss()
            return
        }

        // Take the ad out of the cache while it is on screen: a preload during the show then fills
        // the cache with the NEXT ad (instead of being nulled by this ad's dismiss), and an ad that
        // crosses its expiry while showing is never reported as Expired.
        val shownUnit = loadedUnitId
        interstitialAd = null
        _isAdLoaded.value = false

        ad.adEventCallback = object : InterstitialAdEventCallback {
            override fun onAdImpression() = adsManager.emit(AdEvent.Impression(AdFormat.INTERSTITIAL, shownUnit))
            override fun onAdClicked() = adsManager.emit(AdEvent.Clicked(AdFormat.INTERSTITIAL, shownUnit))
            override fun onAdPaid(value: AdValue) = adsManager.emit(value.toPaid(AdFormat.INTERSTITIAL, shownUnit))

            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Interstitial Ad dismissed.")
                adsManager.emit(AdEvent.Dismissed(AdFormat.INTERSTITIAL, shownUnit))
                adsManager.fullScreenAdShowing.set(false)
                // Trigger auto-preload for the next interstitial session
                loadAd()
                // The Next Gen SDK delivers these callbacks on a background thread; the consumer's
                // onDismiss() typically touches the UI (Toast/navigation), so hop to the main thread.
                CoroutineScope(Dispatchers.Main).launch { onDismiss() }
            }

            override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) {
                Log.e(TAG, "Interstitial Ad failed to show: ${fullScreenContentError.message}")
                adsManager.emit(AdEvent.ShowFailed(AdFormat.INTERSTITIAL, shownUnit, fullScreenContentError.message))
                adsManager.fullScreenAdShowing.set(false)
                // Load replacement ad
                loadAd()
                CoroutineScope(Dispatchers.Main).launch { onDismiss() }
            }

            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "Interstitial Ad shown successfully.")
            }
        }

        try {
            ad.show(activity)
        } catch (e: Exception) {
            Log.e(TAG, "Interstitial show() threw: ${e.message}")
            adsManager.fullScreenAdShowing.set(false)
            // The ad was never shown: put it back unless a newer one has been cached meanwhile.
            if (interstitialAd == null) {
                interstitialAd = ad
                _isAdLoaded.value = true
            }
            onDismiss()
        }
    }

    /**
     * ON-DEMAND strategy: loads a FRESH interstitial right now and shows it the moment it is ready.
     *
     * This path is completely independent of the preloaded cache used by [loadAd]/[showAd]: it
     * loads into a local ad instance and never reads or overwrites [interstitialAd] or
     * [_isAdLoaded]. That keeps "preload" and "on-demand" fully separate — triggering one never
     * disturbs the other.
     *
     * @param onLoading toggled true while the ad is being fetched and false once it is shown or fails
     *                  (always delivered on the main thread, so it is safe to drive Compose state).
     * @param onDismiss fired after the ad closes, or immediately if it cannot be loaded/shown.
     *
     * Only one on-demand flow runs at a time: a call made while another is still loading/showing is
     * ignored (neither callback fires for it) — the running flow's callbacks still arrive.
     */
    fun loadAndShow(
        activity: Activity,
        onLoading: (Boolean) -> Unit = {},
        onDismiss: () -> Unit = {}
    ) {
        if (!isEnabled) {
            Log.d(TAG, "Interstitial disabled (isEnabled=false); on-demand show skipped.")
            // The caller may already have raised its loader; always lower it.
            onLoading(false)
            onDismiss()
            return
        }
        if (!adsManager.isInitialized.value) {
            Log.d(TAG, "Ads SDK not initialized yet; on-demand Interstitial skipped until consent + init.")
            onLoading(false)
            onDismiss()
            return
        }

        // A double tap must not fire two requests and then show two interstitials back to back.
        if (!onDemandInFlight.compareAndSet(false, true)) {
            Log.d(TAG, "On-demand: a load is already in progress; ignoring this call.")
            return
        }
        // Every exit below goes through finish(), which frees the slot exactly once.
        val finish: () -> Unit = {
            onDemandInFlight.set(false)
            onLoading(false)
            onDismiss()
        }

        Log.d(TAG, "On-demand: loading a fresh Interstitial Ad: $adUnitId")
        onLoading(true)

        adsManager.scope.launch {
            if (!adsManager.isNetworkAvailable()) {
                Log.d(TAG, "On-demand: no network available; aborting.")
                withMain(finish)
                return@launch
            }

            val unit = adUnitId
            val startedAt = System.currentTimeMillis()
            adsManager.emit(AdEvent.LoadRequested(AdFormat.INTERSTITIAL, unit))
            try {
                InterstitialAd.load(
                    adsManager.buildAdRequest(unit),
                    object : AdLoadCallback<InterstitialAd> {
                        override fun onAdLoaded(ad: InterstitialAd) {
                            Log.i(TAG, "On-demand Interstitial loaded; showing over the loader.")
                            adsManager.emit(AdEvent.Loaded(AdFormat.INTERSTITIAL, unit, System.currentTimeMillis() - startedAt))
                            ad.adEventCallback = object : InterstitialAdEventCallback {
                                override fun onAdImpression() = adsManager.emit(AdEvent.Impression(AdFormat.INTERSTITIAL, unit))
                                override fun onAdClicked() = adsManager.emit(AdEvent.Clicked(AdFormat.INTERSTITIAL, unit))
                                override fun onAdPaid(value: AdValue) = adsManager.emit(value.toPaid(AdFormat.INTERSTITIAL, unit))

                                override fun onAdShowedFullScreenContent() {
                                    Log.d(TAG, "On-demand Interstitial shown successfully.")
                                    // The ad now covers the screen — dismiss the loader behind it. Doing it
                                    // here (instead of before show()) guarantees the full-screen loader is
                                    // shown FIRST and the ad never appears before it.
                                    withMain { onLoading(false) }
                                }

                                override fun onAdDismissedFullScreenContent() {
                                    Log.d(TAG, "On-demand Interstitial dismissed.")
                                    adsManager.emit(AdEvent.Dismissed(AdFormat.INTERSTITIAL, unit))
                                    adsManager.fullScreenAdShowing.set(false)
                                    withMain(finish)
                                }

                                override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) {
                                    Log.e(TAG, "On-demand Interstitial failed to show: ${fullScreenContentError.message}")
                                    adsManager.emit(AdEvent.ShowFailed(AdFormat.INTERSTITIAL, unit, fullScreenContentError.message))
                                    adsManager.fullScreenAdShowing.set(false)
                                    withMain(finish)
                                }
                            }
                            // Keep the loader up and show the ad ON TOP of it; the loader is dismissed in
                            // onAdShowedFullScreenContent. show() must run on the main thread (the load
                            // callback arrives on a worker).
                            withMain {
                                if (activity.isFinishing || activity.isDestroyed || !adsManager.canShowFullScreenAd) {
                                    // Popping an interstitial after the user left the app is a policy
                                    // violation (and the activity may be gone).
                                    Log.d(TAG, "On-demand: activity gone or app backgrounded before the ad loaded; not showing.")
                                    finish()
                                    return@withMain
                                }
                                if (!adsManager.fullScreenAdShowing.compareAndSet(false, true)) {
                                    Log.d(TAG, "On-demand: another full-screen ad is showing; not stacking.")
                                    finish()
                                    return@withMain
                                }
                                try {
                                    ad.show(activity)
                                } catch (e: Exception) {
                                    Log.e(TAG, "On-demand show() threw: ${e.message}")
                                    adsManager.fullScreenAdShowing.set(false)
                                    finish()
                                }
                            }
                        }

                        override fun onAdFailedToLoad(adError: LoadAdError) {
                            Log.e(TAG, "On-demand Interstitial failed to load: ${adError.message} (code: ${adError.code})")
                            // On-demand never auto-retries: the user is waiting on a loader.
                            adsManager.emit(AdEvent.LoadFailed(AdFormat.INTERSTITIAL, unit, adError.code, adError.message, null))
                            withMain(finish)
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "On-demand load() threw: ${e.message}")
                withMain(finish)
            }
        }
    }

    /** Runs [block] on the main thread; SDK load/show callbacks arrive on a worker thread. */
    private fun withMain(block: () -> Unit) {
        CoroutineScope(Dispatchers.Main).launch { block() }
    }
}
