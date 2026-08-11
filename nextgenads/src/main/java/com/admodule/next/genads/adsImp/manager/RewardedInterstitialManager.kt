package com.admodule.next.genads.adsImp.manager

import android.app.Activity
import android.util.Log
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.rewarded.OnUserEarnedRewardListener
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardItem
import com.google.android.libraries.ads.mobile.sdk.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.libraries.ads.mobile.sdk.rewardedinterstitial.RewardedInterstitialAdEventCallback
import com.admodule.next.genads.adsImp.AdUnitIds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RewardedInterstitialManager(
    private val adsManager: AdsManager,
    var adUnitId: String = AdUnitIds.REWARDED_INTERSTITIAL,
    var isEnabled: Boolean = true
) {
    private val TAG = "RewardedInterstitialMgr"

    private var rewardedInterstitialAd: RewardedInterstitialAd? = null
    private var isLoading = false
    private var loadTime: Long = 0

    private val _isAdLoaded = MutableStateFlow(false)
    val isAdLoaded: StateFlow<Boolean> = _isAdLoaded.asStateFlow()

    private var retryAttempt = 0
    private val maxRetryDelayMs = 64000L

    /**
     * Verifies if a Rewarded Interstitial Ad is loaded and unexpired.
     */
    fun isAdAvailable(): Boolean {
        return isEnabled && rewardedInterstitialAd != null && !isExpired()
    }

    /**
     * Preloads the Rewarded Interstitial Ad.
     */
    fun loadAd() {
        if (!isEnabled) {
            Log.d(TAG, "Rewarded Interstitial disabled (isEnabled=false); skipping load.")
            return
        }
        // Never request an ad before the SDK is initialized (which only happens after GDPR consent).
        if (!adsManager.isInitialized.value) {
            Log.d(TAG, "Ads SDK not initialized yet; skipping Rewarded Interstitial load until consent + init.")
            return
        }
        if (isLoading || (rewardedInterstitialAd != null && !isExpired())) return

        isLoading = true
        Log.d(TAG, "Triggering load for Rewarded Interstitial Ad: $adUnitId")

        // The Next Gen SDK requires ads to be loaded off the main thread.
        CoroutineScope(Dispatchers.IO).launch {
            if (!adsManager.isNetworkAvailable()) {
                Log.d(TAG, "No network available. Canceling load and scheduling retry.")
                handleLoadFailureQuietly()
                return@launch
            }

            RewardedInterstitialAd.load(
                adsManager.buildAdRequest(adUnitId),
                object : AdLoadCallback<RewardedInterstitialAd> {
                    override fun onAdLoaded(ad: RewardedInterstitialAd) {
                        isLoading = false
                        rewardedInterstitialAd = ad
                        loadTime = System.currentTimeMillis()
                        _isAdLoaded.value = true
                        retryAttempt = 0
                        Log.i(TAG, "Rewarded Interstitial Ad loaded successfully.")
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
        rewardedInterstitialAd = null
        Log.e(TAG, "Rewarded Interstitial Ad failed to load: ${loadAdError.message} (code: ${loadAdError.code})")
        scheduleRetry()
    }

    private fun scheduleRetry() {
        retryAttempt++
        val delayMs = (Math.pow(2.0, retryAttempt.toDouble()).toLong() * 1000L).coerceAtMost(maxRetryDelayMs)
        Log.d(TAG, "Scheduling Rewarded Interstitial Ad reload in $delayMs ms (Attempt #$retryAttempt)")
        CoroutineScope(Dispatchers.IO).launch {
            delay(delayMs)
            loadAd()
        }
    }

    /**
     * Shows a Rewarded Interstitial ad.
     * @param onUserEarnedReward Triggered with reward details if rewarded.
     */
    fun showAd(activity: Activity, onUserEarnedReward: (String, Int) -> Unit, onDismiss: () -> Unit = {}) {
        if (!isEnabled) {
            Log.d(TAG, "Rewarded Interstitial disabled (isEnabled=false); nothing to show.")
            onDismiss()
            return
        }
        if (!isAdAvailable()) {
            Log.d(TAG, "Cannot show Rewarded Interstitial Ad. Reason: " +
                if (rewardedInterstitialAd == null) "Ad not loaded." else "Cached ad expired."
            )
            loadAd()
            onDismiss()
            return
        }

        val ad = rewardedInterstitialAd ?: return

        ad.adEventCallback = object : RewardedInterstitialAdEventCallback {
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Rewarded Interstitial Ad dismissed.")
                rewardedInterstitialAd = null
                _isAdLoaded.value = false

                // Preload the next rewarded interstitial ad
                loadAd()
                // SDK callbacks arrive on a background thread; run the UI-facing onDismiss on main.
                CoroutineScope(Dispatchers.Main).launch { onDismiss() }
            }

            override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) {
                Log.e(TAG, "Rewarded Interstitial Ad failed to show: ${fullScreenContentError.message}")
                rewardedInterstitialAd = null
                _isAdLoaded.value = false

                // Preload to try to recover State Flow
                loadAd()
                CoroutineScope(Dispatchers.Main).launch { onDismiss() }
            }

            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "Rewarded Interstitial Ad shown successfully.")
            }
        }

        // Show Rewarded Interstitial ad
        ad.show(
            activity,
            object : OnUserEarnedRewardListener {
                override fun onUserEarnedReward(reward: RewardItem) {
                    val amount = reward.amount
                    val label = reward.type
                    Log.i(TAG, "User completed Rewarded Interstitial Ad. Reward: $amount x $label")
                    // Reward callback also arrives on a background thread; hop to main for UI updates.
                    CoroutineScope(Dispatchers.Main).launch { onUserEarnedReward(label, amount) }
                }
            }
        )
    }
}
