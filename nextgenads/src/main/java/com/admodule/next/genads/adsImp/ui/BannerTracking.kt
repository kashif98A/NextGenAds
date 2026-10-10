package com.admodule.next.genads.adsImp.ui

import com.admodule.next.genads.adsImp.manager.AdEvent
import com.admodule.next.genads.adsImp.manager.AdFormat
import com.admodule.next.genads.adsImp.manager.AdsManager
import com.admodule.next.genads.adsImp.manager.toPaid
import com.google.android.libraries.ads.mobile.sdk.banner.AdView
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRefreshCallback
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError

/**
 * Loads a banner into this [AdView] and reports its whole lifecycle as [AdEvent]s — the initial
 * request, every auto-refresh (each is a new request), impressions, clicks and revenue. A click also
 * suppresses the next App Open ad, since it sends the user out of the app.
 */
internal fun AdView.loadTrackedBanner(
    adsManager: AdsManager,
    request: BannerAdRequest,
    adUnitId: String,
    onLoaded: (BannerAd) -> Unit,
    onFailed: (LoadAdError) -> Unit
) {
    val startedAt = System.currentTimeMillis()
    adsManager.emit(AdEvent.LoadRequested(AdFormat.BANNER, adUnitId))
    loadAd(request, object : AdLoadCallback<BannerAd> {
        override fun onAdLoaded(ad: BannerAd) {
            ad.adEventCallback = object : BannerAdEventCallback {
                override fun onAdClicked() {
                    adsManager.skipNextAppOpen()
                    adsManager.emit(AdEvent.Clicked(AdFormat.BANNER, adUnitId))
                }
                override fun onAdImpression() = adsManager.emit(AdEvent.Impression(AdFormat.BANNER, adUnitId))
                override fun onAdPaid(value: AdValue) = adsManager.emit(value.toPaid(AdFormat.BANNER, adUnitId))
            }
            ad.bannerAdRefreshCallback = object : BannerAdRefreshCallback {
                override fun onAdRefreshed() {
                    adsManager.emit(AdEvent.LoadRequested(AdFormat.BANNER, adUnitId))
                    adsManager.emit(AdEvent.Loaded(AdFormat.BANNER, adUnitId, latencyMs = -1))
                }
                override fun onAdFailedToRefresh(adError: LoadAdError) {
                    // The banner was destroyed (e.g. its screen closed) before the next refresh: no
                    // request was made, so don't count one — it would understate the match rate.
                    if (adError.code == LoadAdError.ErrorCode.CANCELLED) return
                    adsManager.emit(AdEvent.LoadRequested(AdFormat.BANNER, adUnitId))
                    adsManager.emit(AdEvent.LoadFailed(AdFormat.BANNER, adUnitId, adError.code, adError.message, null))
                }
            }
            adsManager.emit(AdEvent.Loaded(AdFormat.BANNER, adUnitId, System.currentTimeMillis() - startedAt))
            onLoaded(ad)
        }

        override fun onAdFailedToLoad(adError: LoadAdError) {
            adsManager.emit(AdEvent.LoadFailed(AdFormat.BANNER, adUnitId, adError.code, adError.message, null))
            onFailed(adError)
        }
    })
}
