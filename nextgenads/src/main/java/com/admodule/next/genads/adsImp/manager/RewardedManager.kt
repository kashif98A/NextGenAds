package com.admodule.next.genads.adsImp.manager

import android.app.Activity
import android.util.Log
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.rewarded.OnUserEarnedRewardListener
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardItem
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAd
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAdEventCallback
import com.admodule.next.genads.adsImp.AdUnitIds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Rewarded ads. Not preloaded at app start: call [loadAd] when the screen that OFFERS the reward
 * opens, so the ad is ready by the time the user taps — without caching an ad they never see.
 */
class RewardedManager(
    private val adsManager: AdsManager,
    var adUnitId: String = AdUnitIds.REWARDED,
    var isEnabled: Boolean = true
) {
    private val TAG = "RewardedManager"

    /** How long a cached ad is considered fresh (1 hour). */
    var expiryMs: Long = 3600 * 1000L

    @Volatile
    private var rewardedAd: RewardedAd? = null
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
        loader.resetRetries()
        load()
    }

    /** Drops a cached ad that expired unseen and reports it (a lost impression). */
    @Synchronized
    private fun discardIfExpired() {
        if (rewardedAd != null && System.currentTimeMillis() - loadTime > expiryMs) {
            rewardedAd = null
            _isAdLoaded.value = false
            adsManager.emit(AdEvent.Expired(AdFormat.REWARDED, loadedUnitId))
        }
    }

    private fun load() {
        if (!isEnabled) {
            Log.d(TAG, "Rewarded disabled (isEnabled=false); skipping load.")
            return
        }
        // Never request an ad before the SDK is initialized (which only happens after GDPR consent).
        if (!adsManager.isInitialized.value) {
            Log.d(TAG, "Ads SDK not initialized yet; skipping Rewarded load until consent + init.")
            return
        }
        discardIfExpired()
        if ((rewardedAd != null && !isExpired()) || !loader.tryStart()) return

        Log.d(TAG, "Triggering load for Rewarded Ad: $adUnitId")

        // The Next Gen SDK requires ads to be loaded off the main thread.
        adsManager.scope.launch {
            if (!adsManager.isNetworkAvailable()) {
                Log.d(TAG, "No network available. Load resumes when the network returns.")
                loader.onAborted()
                return@launch
            }

            val unit = adUnitId
            requestStartedAt = System.currentTimeMillis()
            adsManager.emit(AdEvent.LoadRequested(AdFormat.REWARDED, unit))
            RewardedAd.load(
                adsManager.buildAdRequest(unit),
                object : AdLoadCallback<RewardedAd> {
                    override fun onAdLoaded(ad: RewardedAd) {
                        rewardedAd = ad
                        loadTime = System.currentTimeMillis()
                        _isAdLoaded.value = true
                        loader.onLoaded()
                        loadedUnitId = unit
                        adsManager.emit(AdEvent.Loaded(AdFormat.REWARDED, unit, System.currentTimeMillis() - requestStartedAt))
                        Log.i(TAG, "Rewarded Ad loaded successfully.")
                    }

                    override fun onAdFailedToLoad(adError: LoadAdError) {
                        _isAdLoaded.value = false
                        rewardedAd = null
                        Log.e(TAG, "Rewarded Ad failed to load: ${adError.message} (code: ${adError.code})")
                        val retryIn = loader.onFailed(adError.code) { load() }
                        adsManager.emit(AdEvent.LoadFailed(AdFormat.REWARDED, unit, adError.code, adError.message, retryIn))
                    }
                }
            )
        }
    }

    private fun isExpired(): Boolean {
        return System.currentTimeMillis() - loadTime > expiryMs
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
        discardIfExpired()
        if (!isAdAvailable()) {
            Log.d(TAG, "Cannot show Rewarded Ad. Reason: " +
                if (rewardedAd == null) "Ad not loaded." else "Cached ad expired."
            )
            loadAd()
            onDismiss()
            return
        }

        val ad = rewardedAd ?: run {
            onDismiss()
            return
        }

        ad.adEventCallback = object : RewardedAdEventCallback {
            override fun onAdImpression() = adsManager.emit(AdEvent.Impression(AdFormat.REWARDED, loadedUnitId))
            override fun onAdClicked() = adsManager.emit(AdEvent.Clicked(AdFormat.REWARDED, loadedUnitId))
            override fun onAdPaid(value: AdValue) = adsManager.emit(value.toPaid(AdFormat.REWARDED, loadedUnitId))

            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Rewarded Ad dismissed.")
                adsManager.emit(AdEvent.Dismissed(AdFormat.REWARDED, loadedUnitId))
                rewardedAd = null
                _isAdLoaded.value = false
                adsManager.fullScreenAdShowing.set(false)
                // Preload the next rewarded ad unit automatically
                loadAd()
                // SDK callbacks arrive on a background thread; run the UI-facing onDismiss on main.
                CoroutineScope(Dispatchers.Main).launch { onDismiss() }
            }

            override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) {
                Log.e(TAG, "Rewarded Ad failed to show: ${fullScreenContentError.message}")
                adsManager.emit(AdEvent.ShowFailed(AdFormat.REWARDED, loadedUnitId, fullScreenContentError.message))
                rewardedAd = null
                _isAdLoaded.value = false
                adsManager.fullScreenAdShowing.set(false)
                // Preload immediately to recover
                loadAd()
                CoroutineScope(Dispatchers.Main).launch { onDismiss() }
            }

            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "Rewarded Ad shown successfully.")
            }
        }

        // Show the ad and capture the reward callback
        adsManager.fullScreenAdShowing.set(true)
        try {
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
        } catch (e: Exception) {
            Log.e(TAG, "Rewarded show() threw: ${e.message}")
            adsManager.fullScreenAdShowing.set(false)
            onDismiss()
        }
    }
}
