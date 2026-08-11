package com.admodule.next.genads.adsImp.manager

import android.app.Activity
import android.util.Log
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdEventCallback
import com.admodule.next.genads.adsImp.AdUnitIds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class InterstitialManager(
    private val adsManager: AdsManager,
    var adUnitId: String = AdUnitIds.INTERSTITIAL,
    var isEnabled: Boolean = true
) {
    private val TAG = "InterstitialManager"

    private var interstitialAd: InterstitialAd? = null
    private var isLoading = false
    private var loadTime: Long = 0

    private val _isAdLoaded = MutableStateFlow(false)
    val isAdLoaded: StateFlow<Boolean> = _isAdLoaded.asStateFlow()

    private var retryAttempt = 0
    private val maxRetryDelayMs = 64000L

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
        if (!isEnabled) {
            Log.d(TAG, "Interstitial disabled (isEnabled=false); skipping load.")
            return
        }
        // Never request an ad before the SDK is initialized (which only happens after GDPR consent).
        if (!adsManager.isInitialized.value) {
            Log.d(TAG, "Ads SDK not initialized yet; skipping Interstitial load until consent + init.")
            return
        }
        if (isLoading || (interstitialAd != null && !isExpired())) return

        isLoading = true
        Log.d(TAG, "Triggering load for Interstitial Ad: $adUnitId")

        // The Next Gen SDK requires ads to be loaded off the main thread.
        CoroutineScope(Dispatchers.IO).launch {
            if (!adsManager.isNetworkAvailable()) {
                Log.d(TAG, "No network available. Canceling load and scheduling retry.")
                handleLoadFailureQuietly()
                return@launch
            }

            InterstitialAd.load(
                adsManager.buildAdRequest(adUnitId),
                object : AdLoadCallback<InterstitialAd> {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        isLoading = false
                        interstitialAd = ad
                        loadTime = System.currentTimeMillis()
                        _isAdLoaded.value = true
                        retryAttempt = 0
                        Log.i(TAG, "Interstitial Ad loaded successfully.")
                    }

                    override fun onAdFailedToLoad(adError: LoadAdError) {
                        isLoading = false
                        handleLoadFailure(adError)
                    }
                }
            )
        }
    }

    private fun isExpired(): Boolean {
        val fourHoursMs = 4 * 3600 * 1000
        return System.currentTimeMillis() - loadTime > fourHoursMs
    }

    private fun handleLoadFailureQuietly() {
        isLoading = false
        _isAdLoaded.value = false
        scheduleRetry()
    }

    private fun handleLoadFailure(loadAdError: LoadAdError) {
        _isAdLoaded.value = false
        interstitialAd = null
        Log.e(TAG, "Interstitial Ad failed to load: ${loadAdError.message} (code: ${loadAdError.code})")
        scheduleRetry()
    }

    private fun scheduleRetry() {
        retryAttempt++
        val delayMs = (Math.pow(2.0, retryAttempt.toDouble()).toLong() * 1000L).coerceAtMost(maxRetryDelayMs)
        Log.d(TAG, "Scheduling Interstitial Ad reload in $delayMs ms (Attempt #$retryAttempt)")
        CoroutineScope(Dispatchers.IO).launch {
            delay(delayMs)
            loadAd()
        }
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
        if (!isAdAvailable()) {
            Log.d(TAG, "Cannot show Interstitial Ad. Reason: " +
                if (interstitialAd == null) "Ad not loaded." else "Cached ad expired."
            )
            // Load if missing or expired
            loadAd()
            onDismiss()
            return
        }

        val ad = interstitialAd ?: return

        ad.adEventCallback = object : InterstitialAdEventCallback {
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Interstitial Ad dismissed.")
                interstitialAd = null
                _isAdLoaded.value = false

                // Trigger auto-preload for the next interstitial session
                loadAd()
                // The Next Gen SDK delivers these callbacks on a background thread; the consumer's
                // onDismiss() typically touches the UI (Toast/navigation), so hop to the main thread.
                CoroutineScope(Dispatchers.Main).launch { onDismiss() }
            }

            override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) {
                Log.e(TAG, "Interstitial Ad failed to show: ${fullScreenContentError.message}")
                interstitialAd = null
                _isAdLoaded.value = false

                // Load replacement ad
                loadAd()
                CoroutineScope(Dispatchers.Main).launch { onDismiss() }
            }

            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "Interstitial Ad shown successfully.")
            }
        }

        ad.show(activity)
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
     */
    fun loadAndShow(
        activity: Activity,
        onLoading: (Boolean) -> Unit = {},
        onDismiss: () -> Unit = {}
    ) {
        if (!isEnabled) {
            Log.d(TAG, "Interstitial disabled (isEnabled=false); on-demand show skipped.")
            onDismiss()
            return
        }
        if (!adsManager.isInitialized.value) {
            Log.d(TAG, "Ads SDK not initialized yet; on-demand Interstitial skipped until consent + init.")
            onDismiss()
            return
        }

        Log.d(TAG, "On-demand: loading a fresh Interstitial Ad: $adUnitId")
        onLoading(true)

        CoroutineScope(Dispatchers.IO).launch {
            if (!adsManager.isNetworkAvailable()) {
                Log.d(TAG, "On-demand: no network available; aborting.")
                withMain { onLoading(false); onDismiss() }
                return@launch
            }

            InterstitialAd.load(
                adsManager.buildAdRequest(adUnitId),
                object : AdLoadCallback<InterstitialAd> {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        Log.i(TAG, "On-demand Interstitial loaded; showing over the loader.")
                        ad.adEventCallback = object : InterstitialAdEventCallback {
                            override fun onAdShowedFullScreenContent() {
                                Log.d(TAG, "On-demand Interstitial shown successfully.")
                                // The ad now covers the screen — dismiss the loader behind it. Doing it
                                // here (instead of before show()) guarantees the full-screen loader is
                                // shown FIRST and the ad never appears before it.
                                withMain { onLoading(false) }
                            }

                            override fun onAdDismissedFullScreenContent() {
                                Log.d(TAG, "On-demand Interstitial dismissed.")
                                withMain { onLoading(false); onDismiss() }
                            }

                            override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) {
                                Log.e(TAG, "On-demand Interstitial failed to show: ${fullScreenContentError.message}")
                                withMain { onLoading(false); onDismiss() }
                            }
                        }
                        // Keep the loader up and show the ad ON TOP of it; the loader is dismissed in
                        // onAdShowedFullScreenContent. show() must run on the main thread (the load
                        // callback arrives on a worker).
                        withMain { ad.show(activity) }
                    }

                    override fun onAdFailedToLoad(adError: LoadAdError) {
                        Log.e(TAG, "On-demand Interstitial failed to load: ${adError.message} (code: ${adError.code})")
                        withMain { onLoading(false); onDismiss() }
                    }
                }
            )
        }
    }

    /** Runs [block] on the main thread; SDK load/show callbacks arrive on a worker thread. */
    private fun withMain(block: () -> Unit) {
        CoroutineScope(Dispatchers.Main).launch { block() }
    }
}
