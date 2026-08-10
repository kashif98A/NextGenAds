package com.example.adsImp.manager

import android.app.Activity
import android.util.Log
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.rewarded.OnUserEarnedRewardListener
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardItem
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAd
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAdEventCallback
import com.example.adsImp.AdUnitIds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RewardedManager(
    private val adsManager: AdsManager,
    var adUnitId: String = AdUnitIds.REWARDED,
    var isEnabled: Boolean = true
) {
    private val TAG = "RewardedManager"

    private var rewardedAd: RewardedAd? = null
    private var isLoading = false
    private var loadTime: Long = 0

    private val _isAdLoaded = MutableStateFlow(false)
    val isAdLoaded: StateFlow<Boolean> = _isAdLoaded.asStateFlow()

    private var retryAttempt = 0
    private val maxRetryDelayMs = 64000L

    /**
     * Verifies if a Rewarded Ad is loaded and unexpired.
     */
    fun isAdAvailable(): Boolean {
        return isEnabled && rewardedAd != null && !isExpired()
    }

    /**
     * Setup call to preload the Rewarded Ad.
     */
    fun loadAd() {
        if (!isEnabled) {
            Log.d(TAG, "Rewarded disabled (isEnabled=false); skipping load.")
            return
        }
        if (isLoading || (rewardedAd != null && !isExpired())) return

        isLoading = true
        Log.d(TAG, "Triggering load for Rewarded Ad: $adUnitId")

        // The Next Gen SDK requires ads to be loaded off the main thread.
        CoroutineScope(Dispatchers.IO).launch {
            if (!adsManager.isNetworkAvailable()) {
                Log.d(TAG, "No network available. Canceling load and scheduling retry.")
                handleLoadFailureQuietly()
                return@launch
            }

            RewardedAd.load(
                adsManager.buildAdRequest(adUnitId),
                object : AdLoadCallback<RewardedAd> {
                    override fun onAdLoaded(ad: RewardedAd) {
                        isLoading = false
                        rewardedAd = ad
                        loadTime = System.currentTimeMillis()
                        _isAdLoaded.value = true
                        retryAttempt = 0
                        Log.i(TAG, "Rewarded Ad loaded successfully.")
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
        rewardedAd = null
        Log.e(TAG, "Rewarded Ad failed to load: ${loadAdError.message} (code: ${loadAdError.code})")
        scheduleRetry()
    }

    private fun scheduleRetry() {
        retryAttempt++
        val delayMs = (Math.pow(2.0, retryAttempt.toDouble()).toLong() * 1000L).coerceAtMost(maxRetryDelayMs)
        Log.d(TAG, "Scheduling Rewarded Ad reload in $delayMs ms (Attempt #$retryAttempt)")
        CoroutineScope(Dispatchers.IO).launch {
            delay(delayMs)
            loadAd()
        }
    }

    /**
     * Shows a Rewarded ad.
     * @param onUserEarnedReward Triggered with reward type and reward quantity when user fully views the ad.
     */
    fun showAd(activity: Activity, onUserEarnedReward: (String, Int) -> Unit, onDismiss: () -> Unit = {}) {
        if (!isEnabled) {
            Log.d(TAG, "Rewarded disabled (isEnabled=false); nothing to show.")
            onDismiss()
            return
        }
        if (!isAdAvailable()) {
            Log.d(TAG, "Cannot show Rewarded Ad. Reason: " +
                if (rewardedAd == null) "Ad not loaded." else "Cached ad expired."
            )
            loadAd()
            onDismiss()
            return
        }

        val ad = rewardedAd ?: return

        ad.adEventCallback = object : RewardedAdEventCallback {
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Rewarded Ad dismissed.")
                rewardedAd = null
                _isAdLoaded.value = false

                // Preload the next rewarded ad unit automatically
                loadAd()
                // SDK callbacks arrive on a background thread; run the UI-facing onDismiss on main.
                CoroutineScope(Dispatchers.Main).launch { onDismiss() }
            }

            override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) {
                Log.e(TAG, "Rewarded Ad failed to show: ${fullScreenContentError.message}")
                rewardedAd = null
                _isAdLoaded.value = false

                // Preload immediately to recover
                loadAd()
                CoroutineScope(Dispatchers.Main).launch { onDismiss() }
            }

            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "Rewarded Ad shown successfully.")
            }
        }

        // Show the ad and capture the reward callback
        ad.show(
            activity,
            object : OnUserEarnedRewardListener {
                override fun onUserEarnedReward(reward: RewardItem) {
                    val amount = reward.amount
                    val label = reward.type
                    Log.i(TAG, "User completed Rewarded Ad. Reward Granted: $amount x $label")
                    // Reward callback also arrives on a background thread; hop to main for UI updates.
                    CoroutineScope(Dispatchers.Main).launch { onUserEarnedReward(label, amount) }
                }
            }
        )
    }
}
