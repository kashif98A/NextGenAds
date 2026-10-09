package com.admodule.next.genads.adsImp.ui

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.banner.AdView
import com.admodule.next.genads.adsImp.AdUnitIds
import com.admodule.next.genads.adsImp.manager.AdsManager

private const val TAG = "CollapsibleBannerAd"

@Composable
fun CollapsibleBannerAdView(
    adsManager: AdsManager,
    modifier: Modifier = Modifier,
    direction: String = "bottom",
    adUnitId: String = AdUnitIds.COLLAPSIBLE_BANNER,
    isEnabled: Boolean = true
) {
    // Gate: show nothing unless this unit is enabled (remote-config style flag). Being offline only
    // blocks the FIRST request; a banner already on screen survives a brief network drop.
    if (!isEnabled || !rememberCanShowInlineAd(adsManager)) return

    val context = LocalContext.current

    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp

    // Collapsible banners always work in sync with adaptive sizing guidelines
    val adSize = remember(screenWidthDp) {
        AdSize.getLargeAnchoredAdaptiveBannerAdSize(context, screenWidthDp)
    }

    var isLoaded by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        contentAlignment = Alignment.Center
    ) {
        if (!isLoaded && !hasError) {
            BannerAdPlaceholder(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
            )
        }

        if (!hasError) {
            AndroidView(
                modifier = Modifier.fillMaxWidth(),
                factory = { ctx ->
                    AdView(ctx).apply {
                        // Load using collapsible banner request params built via AdsManager
                        loadTrackedBanner(
                            adsManager = adsManager,
                            request = adsManager.buildBannerAdRequest(
                                adUnitId,
                                adSize,
                                isCollapsible = true,
                                collapsibleDirection = direction
                            ),
                            adUnitId = adUnitId,
                            onLoaded = {
                                isLoaded = true
                                Log.d(TAG, "Collapsible Banner Loaded successfully.")
                            },
                            onFailed = { adError ->
                                hasError = true
                                Log.e(TAG, "Collapsible Banner failed to load: ${adError.message}")
                            }
                        )
                    }
                },
                update = {},
                onRelease = { adView ->
                    Log.d(TAG, "Releasing collapsible Banner AdView.")
                    adView.destroy()
                }
            )
        } else {
            Spacer(modifier = Modifier.height(0.dp))
        }
    }
}
