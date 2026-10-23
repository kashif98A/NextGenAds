package com.admodule.next.genads.adsImp.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.admodule.next.genads.adsImp.manager.AdsManager
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd

/**
 * Gate for inline (banner / native) ad slots: false until the Ads SDK is initialized (i.e. consent
 * resolved — loading a banner/native ad before that throws in the Next Gen SDK and would request an
 * ad without consent), then true once the device has been online while this slot is on screen, and
 * it STAYS true. Being offline only prevents the initial request — a brief drop (e.g. Wi-Fi → mobile
 * handover) must never tear down an ad that is already loaded and showing, which would waste the
 * matched request and fire a new one when the network returns.
 */
@Composable
internal fun rememberCanShowInlineAd(adsManager: AdsManager): Boolean {
    val isInitialized by adsManager.isInitialized.collectAsStateWithLifecycle()
    val isOnline by adsManager.isOnline.collectAsStateWithLifecycle()
    var hasBeenOnline by remember { mutableStateOf(false) }
    LaunchedEffect(isOnline) {
        if (isOnline) hasBeenOnline = true
    }
    return isInitialized && (isOnline || hasBeenOnline)
}

internal sealed interface NativeAdSlot {
    data object Loading : NativeAdSlot
    data object Empty : NativeAdSlot
    class Ready(val ad: NativeAd) : NativeAdSlot
}

/**
 * Takes one native ad from the shared pool for [adUnitId] and destroys it when this slot leaves
 * composition (it has been displayed by then). If the slot leaves while still waiting, nothing is
 * taken — the ad stays pooled for the next slot instead of being lost.
 */
@Composable
internal fun rememberNativeAdSlot(adsManager: AdsManager, adUnitId: String): NativeAdSlot {
    val nativeAds = remember(adsManager, adUnitId) { adsManager.nativeAds(adUnitId) }
    var slot by remember(nativeAds) { mutableStateOf<NativeAdSlot>(NativeAdSlot.Loading) }

    LaunchedEffect(nativeAds) {
        val ad = nativeAds.acquire()
        slot = if (ad != null) NativeAdSlot.Ready(ad) else NativeAdSlot.Empty
    }

    DisposableEffect(nativeAds) {
        onDispose { (slot as? NativeAdSlot.Ready)?.ad?.destroy() }
    }

    return slot
}
