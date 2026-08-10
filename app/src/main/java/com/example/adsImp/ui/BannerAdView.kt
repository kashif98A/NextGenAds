package com.example.adsImp.ui

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

private const val TAG = "BannerAdView"

@Composable
fun BannerAdView(
    adsManager: AdsManager,
    modifier: Modifier = Modifier,
    adUnitId: String = AdUnitIds.BANNER,
    isEnabled: Boolean = true
) {
    // Gate: show nothing unless this unit is enabled (remote-config style flag) AND online.
    val isOnline by adsManager.networkMonitor.isOnline.collectAsStateWithLifecycle(initialValue = true)
    if (!isEnabled || !isOnline) return

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
                    .height(50.dp)
            )
        }

        if (!hasError) {
            AndroidView(
                modifier = Modifier.fillMaxWidth(),
                factory = { ctx ->
                    AdView(ctx).apply {
                        // Next Gen SDK: the ad unit ID and size are supplied on the BannerAdRequest.
                        loadAd(
                            adsManager.buildBannerAdRequest(adUnitId, AdSize.BANNER),
                            object : AdLoadCallback<BannerAd> {
                                override fun onAdLoaded(ad: BannerAd) {
                                    isLoaded = true
                                    Log.d(TAG, "Standard Banner loaded.")
                                }

                                override fun onAdFailedToLoad(adError: LoadAdError) {
                                    hasError = true
                                    Log.e(TAG, "Standard Banner failed to load: ${adError.message}")
                                }
                            }
                        )
                    }
                },
                update = {},
                onRelease = { adView ->
                    Log.d(TAG, "Destroying Banner AdView to prevent memory leakage.")
                    adView.destroy()
                }
            )
        } else {
            // Quietly display nothing or standard spacer if load fails in order to respect layout boundaries
            Spacer(modifier = Modifier.height(0.dp))
        }
    }
}
