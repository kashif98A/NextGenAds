package com.admodule.next.genads.adsImp.manager

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.android.gms.ads.mediation.admob.AdMobAdapter
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig
import com.admodule.next.genads.adsImp.data.NetworkMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AdsManager(
    private val context: Context,
    val networkMonitor: NetworkMonitor
) {
    private val TAG = "AdsManager"

    companion object {
        // Next Gen GMA SDK consumes the AdMob App ID programmatically via InitializationConfig.
        // Sample AdMob App ID; replace with your production App ID.
        const val APP_ID = "ca-app-pub-3940256099942544~3347511713"
    }

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    init {
        // Collect network changes to reactively preload or handle states if needed
        CoroutineScope(Dispatchers.IO).launch {
            networkMonitor.isOnline.collect { online ->
                Log.d(TAG, "Network status changed: Online=$online")
                if (online && _isInitialized.value) {
                    // Trigger preloads when internet returns if not already loaded
                    preloadAllWhenReady()
                }
            }
        }
    }

    /**
     * Initializes the Next Gen GMA SDK.
     */
    fun initialize(onComplete: () -> Unit = {}) {
        if (_isInitialized.value) {
            onComplete()
            return
        }

        // The Next Gen SDK must be initialized (and ads loaded) off the main thread.
        CoroutineScope(Dispatchers.IO).launch {
            try {
                MobileAds.initialize(
                    context,
                    InitializationConfig.Builder(APP_ID).build()
                ) {
                    Log.i(TAG, "Next Gen GMA SDK initialized.")
                    _isInitialized.value = true
                    preloadAllWhenReady()
                    onComplete()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing Next Gen GMA SDK: ${e.message}")
                _isInitialized.value = false
                onComplete()
            }
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
    suspend fun isNetworkAvailable(): Boolean {
        return networkMonitor.isOnline.first()
    }

    // Forward preloads to specific lazy-initialized manager hooks
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
