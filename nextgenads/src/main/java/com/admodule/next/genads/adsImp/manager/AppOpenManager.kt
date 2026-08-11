package com.admodule.next.genads.adsImp.manager

import android.app.Activity
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAd
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.admodule.next.genads.adsImp.AdUnitIds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AppOpenManager(
    private val adsManager: AdsManager,
    var adUnitId: String = AdUnitIds.APP_OPEN,
    var isEnabled: Boolean = true
) : DefaultLifecycleObserver {

    private val TAG = "AppOpenManager"

    private var appOpenAd: AppOpenAd? = null
    private var isLoading = false
    private var loadTime: Long = 0

    private val _isAdLoaded = MutableStateFlow(false)
    val isAdLoaded: StateFlow<Boolean> = _isAdLoaded.asStateFlow()

    private var retryAttempt = 0
    private val maxRetryDelayMs = 64000L // 64 seconds backoff limit

    init {
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
     * Checks if cached ad is loaded and has not expired (AdMob ads expire after 4 hours).
     */
    fun isAdAvailable(): Boolean {
        val fourHoursMs = 4 * 3600 * 1000
        val isExpired = System.currentTimeMillis() - loadTime > fourHoursMs
        return isEnabled && appOpenAd != null && !isExpired
    }

    /**
     * Preloads the App Open Ad.
     */
    fun loadAd() {
        if (!isEnabled) {
            Log.d(TAG, "App Open disabled (isEnabled=false); skipping load.")
            return
        }
        // Never request an ad before the SDK is initialized (which only happens after GDPR consent).
        if (!adsManager.isInitialized.value) {
            Log.d(TAG, "Ads SDK not initialized yet; skipping App Open load until consent + init.")
            return
        }
        if (isLoading || isAdAvailable()) return

        isLoading = true
        Log.d(TAG, "Triggering load for App Open Ad unit ID: $adUnitId")

        // The Next Gen SDK requires ads to be loaded off the main thread.
        CoroutineScope(Dispatchers.IO).launch {
            if (!adsManager.isNetworkAvailable()) {
                Log.d(TAG, "No network available. Canceling load and scheduling retry.")
                handleLoadFailureQuietly()
                return@launch
            }

            AppOpenAd.load(
                adsManager.buildAdRequest(adUnitId),
                object : AdLoadCallback<AppOpenAd> {
                    override fun onAdLoaded(ad: AppOpenAd) {
                        isLoading = false
                        appOpenAd = ad
                        loadTime = System.currentTimeMillis()
                        _isAdLoaded.value = true
                        retryAttempt = 0
                        Log.i(TAG, "App Open Ad loaded successfully.")
                    }

                    override fun onAdFailedToLoad(adError: LoadAdError) {
                        isLoading = false
                        handleLoadFailure(adError)
                    }
                }
            )
        }
    }

    private fun handleLoadFailureQuietly() {
        isLoading = false
        _isAdLoaded.value = false
        scheduleRetry()
    }

    private fun handleLoadFailure(loadAdError: LoadAdError) {
        _isAdLoaded.value = false
        appOpenAd = null
        Log.e(TAG, "App Open Ad failed to load: ${loadAdError.message} (code: ${loadAdError.code})")
        scheduleRetry()
    }

    private fun scheduleRetry() {
        retryAttempt++
        val delayMs = (Math.pow(2.0, retryAttempt.toDouble()).toLong() * 1000L).coerceAtMost(maxRetryDelayMs)
        Log.d(TAG, "Scheduling App Open Ad reload in $delayMs ms (Attempt #$retryAttempt)")
        CoroutineScope(Dispatchers.IO).launch {
            delay(delayMs)
            loadAd()
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
        if (!isAdAvailable()) {
            Log.d(TAG, "Cannot show App Open Ad: Ad is not available.")
            loadAd()
            onDismiss()
            return
        }

        val ad = appOpenAd ?: return

        ad.adEventCallback = object : AppOpenAdEventCallback {
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "App Open Ad dismissed.")
                appOpenAd = null
                _isAdLoaded.value = false

                // Immediately preload the next App Open Ad
                loadAd()
                // SDK callbacks arrive on a background thread; run the UI-facing onDismiss on main.
                CoroutineScope(Dispatchers.Main).launch { onDismiss() }
            }

            override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) {
                Log.e(TAG, "App Open Ad failed to show: ${fullScreenContentError.message}")
                appOpenAd = null
                _isAdLoaded.value = false

                // Immediately preload next App Open Ad
                loadAd()
                CoroutineScope(Dispatchers.Main).launch { onDismiss() }
            }

            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "App Open Ad shown successfully.")
            }
        }

        ad.show(activity)
    }

    /**
     * Fallback show callback targeting active top activities (e.g. from launch/wake-up triggers).
     */
    fun showAdIfReady() {
        // Find top activity context safely, or let specific activities trigger this manually
        // Inside custom integrations, we call showAd(activity) directly.
    }
}
