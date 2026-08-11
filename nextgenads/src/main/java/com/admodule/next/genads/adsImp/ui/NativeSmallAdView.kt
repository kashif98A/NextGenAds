package com.admodule.next.genads.adsImp.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.nativead.MediaView
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoader
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoaderCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdRequest
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.admodule.next.genads.adsImp.AdUnitIds
import com.admodule.next.genads.adsImp.manager.AdsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "NativeSmallAdView"

@Composable
fun NativeSmallAdView(
    adsManager: AdsManager,
    modifier: Modifier = Modifier,
    adUnitId: String = AdUnitIds.NATIVE,
    isEnabled: Boolean = true
) {
    // Gate: show nothing unless this unit is enabled (remote-config style flag) AND online.
    val isOnline by adsManager.networkMonitor.isOnline.collectAsStateWithLifecycle(initialValue = true)
    if (!isEnabled || !isOnline) return

    // Material 3 Colors extracted safely for XML rendering integration
    val primaryColor = MaterialTheme.colorScheme.primary.toArgb()
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary.toArgb()
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant.toArgb()
    val onSurfaceColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()

    var nativeAdState by remember { mutableStateOf<NativeAd?>(null) }
    var isLoadingAd by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }

    // Fetch Native Ad inside a disposable side effect
    LaunchedEffect(adUnitId) {
        isLoadingAd = true
        hasError = false
        try {
            val adRequest = NativeAdRequest.Builder(
                adUnitId,
                listOf(NativeAd.NativeAdType.NATIVE)
            ).build()

            // The Next Gen SDK requires ads to be loaded off the main thread.
            withContext(Dispatchers.IO) {
                NativeAdLoader.load(
                    adRequest,
                    object : NativeAdLoaderCallback {
                        override fun onNativeAdLoaded(nativeAd: NativeAd) {
                            nativeAdState = nativeAd
                            isLoadingAd = false
                            Log.d(TAG, "Native Small Ad Loaded successfully.")
                        }

                        override fun onAdFailedToLoad(adError: LoadAdError) {
                            isLoadingAd = false
                            hasError = true
                            Log.e(TAG, "Native Small Ad failed to load: ${adError.message}")
                        }
                    }
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Critical error creating NativeAdLoader: ${e.message}")
            isLoadingAd = false
            hasError = true
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        contentAlignment = Alignment.Center
    ) {
        if (isLoadingAd) {
            NativeAdPlaceholder(
                modifier = Modifier.fillMaxWidth(),
                isMedium = false
            )
        } else if (!hasError && nativeAdState != null) {
            val loadedAd = nativeAdState!!

            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                factory = { ctx ->
                    createDynamicSmallNativeView(
                        ctx,
                        loadedAd,
                        primaryColor,
                        onPrimaryColor,
                        surfaceColor,
                        onSurfaceColor,
                        textColor
                    )
                },
                update = {},
                onRelease = {
                    Log.d(TAG, "Releasing Small NativeAd assets.")
                    nativeAdState?.destroy()
                }
            )
        } else {
            Spacer(modifier = Modifier.height(0.dp))
        }
    }
}

/**
 * Programmatic creation of standard NativeAdView without a single line of XML layouts.
 */
private fun createDynamicSmallNativeView(
    context: Context,
    nativeAd: NativeAd,
    primaryColor: Int,
    onPrimaryColor: Int,
    surfaceColor: Int,
    onSurfaceColor: Int,
    textColor: Int
): NativeAdView {
    val nativeAdView = NativeAdView(context).apply {
        layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        setPadding(12, 12, 12, 12)
        background = GradientDrawable().apply {
            setColor(surfaceColor)
            cornerRadius = 16f
            setStroke(2, primaryColor)
        }
    }

    // Top content row (icon + headline/body)
    val mainLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        gravity = Gravity.CENTER_VERTICAL
    }

    // Square Icon View Setup
    val iconContainer = FrameLayout(context).apply {
        layoutParams = LinearLayout.LayoutParams(60.dpToPx(context), 60.dpToPx(context)).apply {
            marginEnd = 12.dpToPx(context)
        }
    }

    val iconImageView = ImageView(context).apply {
        layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        scaleType = ImageView.ScaleType.CENTER_CROP
        clipToOutline = true
        background = GradientDrawable().apply {
            setColor(Color.LTGRAY)
            cornerRadius = 12f
        }
    }
    iconContainer.addView(iconImageView)

    // Center Text Layout (Headline and Body)
    val textLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1.0f
        )
    }

    // "Ad" Badge Text Indicator
    val adBadge = TextView(context).apply {
        text = "Ad"
        setTextColor(onPrimaryColor)
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 10f)
        setPadding(6, 2, 6, 2)
        gravity = Gravity.CENTER
        background = GradientDrawable().apply {
            setColor(primaryColor)
            cornerRadius = 4f
        }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = 4
        }
    }
    textLayout.addView(adBadge)

    // Headline TextView
    val headlineTextView = TextView(context).apply {
        text = nativeAd.headline
        setTextColor(textColor)
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
    textLayout.addView(headlineTextView)

    // Body description
    val bodyTextView = TextView(context).apply {
        text = nativeAd.body
        setTextColor(onSurfaceColor)
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
        maxLines = 2
        ellipsize = android.text.TextUtils.TruncateAt.END
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = 4
        }
    }
    textLayout.addView(bodyTextView)

    // Call To Action (CTA) Button — full width, placed beneath the content row.
    val ctaButton = Button(context).apply {
        text = nativeAd.callToAction ?: "GO"
        setTextColor(onPrimaryColor)
        textSize = 13f
        gravity = Gravity.CENTER
        setPadding(16, 10, 16, 10)
        background = GradientDrawable().apply {
            setColor(primaryColor)
            cornerRadius = 24f
        }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = 12.dpToPx(context)
        }
    }

    // Hidden MediaView required by the Next Gen SDK's registerNativeAd binding.
    val mediaView = MediaView(context).apply {
        layoutParams = LinearLayout.LayoutParams(1, 1)
        visibility = View.GONE
    }

    // Build overall hierarchy: icon + text content row on top, full-width CTA beneath.
    mainLayout.addView(iconContainer)
    mainLayout.addView(textLayout)

    val outerLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
    outerLayout.addView(mainLayout)
    outerLayout.addView(ctaButton)
    outerLayout.addView(mediaView)

    nativeAdView.addView(outerLayout)

    // Map NativeAd Assets for proper AdMob analytics tracking and Click interactions
    nativeAdView.headlineView = headlineTextView
    nativeAdView.bodyView = bodyTextView
    nativeAdView.callToActionView = ctaButton
    nativeAdView.iconView = iconImageView

    // Bind resources safely
    if (nativeAd.icon != null) {
        iconImageView.setImageDrawable(nativeAd.icon?.drawable)
        iconContainer.visibility = View.VISIBLE
    } else {
        iconContainer.visibility = View.GONE
    }

    // Inform the SDK that the views are populated for this native ad.
    nativeAdView.registerNativeAd(nativeAd, mediaView)

    return nativeAdView
}

// Utility conversion function
private fun Int.dpToPx(context: Context): Int {
    val density = context.resources.displayMetrics.density
    return (this * density).toInt()
}
