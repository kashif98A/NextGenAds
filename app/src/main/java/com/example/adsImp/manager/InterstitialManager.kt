package com.example.adsImp.manager

import android.app.Activity
import android.util.Log
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdEventCallback
import com.example.adsImp.AdUnitIds
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
}
