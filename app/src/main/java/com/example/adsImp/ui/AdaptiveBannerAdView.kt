package com.example.adsImp.ui

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
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.adsImp.AdUnitIds
import com.example.adsImp.manager.AdsManager

private const val TAG = "AdaptiveBannerAdView"

@Composable
fun AdaptiveBannerAdView(
    adsManager: AdsManager,
    modifier: Modifier = Modifier,
    adUnitId: String = AdUnitIds.BANNER,
    isEnabled: Boolean = true
) {
    // Gate: show nothing unless this unit is enabled (remote-config style flag) AND online.
    val isOnline by adsManager.networkMonitor.isOnline.collectAsStateWithLifecycle(initialValue = true)
    if (!isEnabled || !isOnline) return

    val context = LocalContext.current

    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp

    // Dynamically calculate the anchored adaptive banner size
    val adaptiveAdSize = remember(screenWidthDp) {
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
                        loadAd(
                            adsManager.buildBannerAdRequest(adUnitId, adaptiveAdSize),
                            object : AdLoadCallback<BannerAd> {
                                override fun onAdLoaded(ad: BannerAd) {
                                    isLoaded = true
                                    Log.d(TAG, "Adaptive Banner Loaded successfully. Size: $adaptiveAdSize")
                                }

                                override fun onAdFailedToLoad(adError: LoadAdError) {
                                    hasError = true
                                    Log.e(TAG, "Adaptive Banner failed to load: ${adError.message}")
                                }
                            }
                        )
                    }
                },
                update = {},
                onRelease = { adView ->
                    Log.d(TAG, "Releasing and destroying Adaptive AdView.")
                    adView.destroy()
                }
            )
        } else {
            Spacer(modifier = Modifier.height(0.dp))
        }
    }
}
