package com.admodule.next.genads.adsImp.manager

import android.util.Log
import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoader
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoaderCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

/**
 * A small pool of preloaded native ads for one ad unit. Obtain it with [AdsManager.nativeAds].
 *
 * Native composables no longer load their own ad: they [acquire] one from the pool. A load that
 * finishes after its view has left the screen simply stays in the pool for the next view, so a
 * matched native ad is not wasted, and coming back to a screen shows an ad instantly instead of
 * firing a new request. The caller destroys an acquired ad once it has been displayed.
 */
class NativeAdManager internal constructor(
    private val adsManager: AdsManager,
    val adUnitId: String
) {
    private val TAG = "NativeAdManager"

    /** How many ready-to-show ads to keep cached. */
    var poolSize: Int = 2

    /** How long a cached native ad is considered fresh (1 hour). */
    var expiryMs: Long = 3600 * 1000L

    var isEnabled: Boolean = true

    private class Entry(val ad: NativeAd, val loadTime: Long)

    private val pool = MutableStateFlow<List<Entry>>(emptyList())
    private val inFlight = AtomicInteger(0)
    private val failureCount = MutableStateFlow(0)

    /** Tops the pool up to [poolSize]. Cheap to call repeatedly — never over-requests. */
    fun preload() {
        if (!isEnabled || !adsManager.isInitialized.value) return
        dropExpired()
        while (true) {
            val loading = inFlight.get()
            if (pool.value.size + loading >= poolSize) return
            if (inFlight.compareAndSet(loading, loading + 1)) loadOne()
        }
    }

    /**
     * Returns a ready native ad — straight from the pool when one is cached, otherwise after the
     * pending load completes. Returns null when nothing could be loaded (no fill, offline, disabled).
     * Cancellation-safe: if the caller goes away while waiting, the ad stays in the pool.
     */
    suspend fun acquire(): NativeAd? {
        if (!isEnabled || !adsManager.isInitialized.value) return null
        take()?.let { preload(); return it }
        preload()
        while (true) {
            val failuresBefore = failureCount.value
            if (inFlight.get() == 0) return take()
            // Wake on a newly pooled ad OR on a failed load, whichever comes first.
            combine(pool, failureCount) { ads, failures ->
                ads.isNotEmpty() || failures != failuresBefore
            }.first { it }
            take()?.let { preload(); return it }
        }
    }

    /** Destroys every cached ad (e.g. when the user buys "remove ads"). */
    fun clear() {
        pool.getAndUpdate { emptyList() }.forEach { it.ad.destroy() }
    }

    private fun loadOne() {
        adsManager.scope.launch {
            if (!adsManager.isNetworkAvailable()) {
                Log.d(TAG, "No network available; native load skipped.")
                inFlight.decrementAndGet()
                failureCount.update { it + 1 }
                return@launch
            }
            Log.d(TAG, "Loading native ad for pool: $adUnitId")
            val startedAt = System.currentTimeMillis()
            adsManager.emit(AdEvent.LoadRequested(AdFormat.NATIVE, adUnitId))
            try {
                NativeAdLoader.load(
                    NativeAdRequest.Builder(adUnitId, listOf(NativeAd.NativeAdType.NATIVE)).build(),
                    object : NativeAdLoaderCallback {
                        override fun onNativeAdLoaded(nativeAd: NativeAd) {
                            // A click sends the user out of the app — don't greet them with an App
                            // Open ad when they come back.
                            nativeAd.adEventCallback = object : NativeAdEventCallback {
                                override fun onAdClicked() {
                                    adsManager.skipNextAppOpen()
                                    adsManager.emit(AdEvent.Clicked(AdFormat.NATIVE, adUnitId))
                                }
                                override fun onAdImpression() = adsManager.emit(AdEvent.Impression(AdFormat.NATIVE, adUnitId))
                                override fun onAdPaid(value: AdValue) = adsManager.emit(value.toPaid(AdFormat.NATIVE, adUnitId))
                            }
                            adsManager.emit(AdEvent.Loaded(AdFormat.NATIVE, adUnitId, System.currentTimeMillis() - startedAt))
                            pool.update { it + Entry(nativeAd, System.currentTimeMillis()) }
                            inFlight.decrementAndGet()
                            Log.d(TAG, "Native ad pooled (${pool.value.size}/$poolSize).")
                        }

                        override fun onAdFailedToLoad(adError: LoadAdError) {
                            inFlight.decrementAndGet()
                            failureCount.update { it + 1 }
                            Log.e(TAG, "Native ad failed to load: ${adError.message} (code: ${adError.code})")
                            // The pool never auto-retries; the next acquire()/preload() requests again.
                            adsManager.emit(AdEvent.LoadFailed(AdFormat.NATIVE, adUnitId, adError.code, adError.message, null))
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Critical error creating NativeAdLoader: ${e.message}")
                inFlight.decrementAndGet()
                failureCount.update { it + 1 }
            }
        }
    }

    /** Atomically removes and returns the oldest fresh ad, destroying any expired ones on the way. */
    private fun take(): NativeAd? {
        while (true) {
            val current = pool.value
            val first = current.firstOrNull() ?: return null
            if (!pool.compareAndSet(current, current.drop(1))) continue
            if (isExpired(first)) {
                first.ad.destroy()
                adsManager.emit(AdEvent.Expired(AdFormat.NATIVE, adUnitId))
                continue
            }
            return first.ad
        }
    }

    private fun dropExpired() {
        val expired = mutableListOf<Entry>()
        pool.update { current ->
            expired.clear()
            current.filter { entry -> isExpired(entry).also { if (it) expired += entry }.not() }
        }
        expired.forEach {
            it.ad.destroy()
            adsManager.emit(AdEvent.Expired(AdFormat.NATIVE, adUnitId))
        }
    }

    private fun isExpired(entry: Entry): Boolean =
        System.currentTimeMillis() - entry.loadTime > expiryMs
}
