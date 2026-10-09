package com.admodule.next.genads.adsImp.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
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
import com.google.android.libraries.ads.mobile.sdk.nativead.MediaView
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView
import com.admodule.next.genads.adsImp.AdUnitIds
import com.admodule.next.genads.adsImp.manager.AdsManager

@Composable
fun NativeMediumAdView(
    adsManager: AdsManager,
    modifier: Modifier = Modifier,
    adUnitId: String = AdUnitIds.NATIVE,
    isEnabled: Boolean = true
) {
    // Gate: show nothing unless this unit is enabled (remote-config style flag). Being offline only
    // blocks the FIRST request; an ad already on screen survives a brief network drop.
    if (!isEnabled || !rememberCanShowInlineAd(adsManager)) return

    val primaryColor = MaterialTheme.colorScheme.primary.toArgb()
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary.toArgb()
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant.toArgb()
    val onSurfaceColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()

    // Drawn from the shared pool: instant when an ad is cached, and never wasted if this view leaves
    // the screen before the load finishes. The ad is destroyed when this slot leaves composition.
    val slot = rememberNativeAdSlot(adsManager, adUnitId)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        contentAlignment = Alignment.Center
    ) {
        when (slot) {
            NativeAdSlot.Loading -> NativeAdPlaceholder(
                modifier = Modifier.fillMaxWidth(),
                isMedium = true
            )
            is NativeAdSlot.Ready -> AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                factory = { ctx ->
                    createDynamicMediumNativeView(
                        ctx,
                        slot.ad,
                        primaryColor,
                        onPrimaryColor,
                        surfaceColor,
                        onSurfaceColor,
                        textColor
                    )
                },
                update = {}
            )
            NativeAdSlot.Empty -> Unit
        }
    }
}

/**
 * Modern XML-free dynamic vertical implementation of Medium Native AdView in pure Kotlin
 */
private fun createDynamicMediumNativeView(
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
        setPadding(16, 16, 16, 16)
        background = GradientDrawable().apply {
            setColor(surfaceColor)
            cornerRadius = 18f
            setStroke(2, primaryColor)
        }
    }

    // Outer Main Vertical Layout
    val mainLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    // 1. Top Media View to render rich images or videos
    val mediaView = MediaView(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            160.dpToPx(context)
        ).apply {
            bottomMargin = 12.dpToPx(context)
        }
        background = GradientDrawable().apply {
            setColor(Color.DKGRAY)
            cornerRadius = 12f
        }
    }
    mainLayout.addView(mediaView)

    // 2. Icon + Header Row info
    val headerRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        gravity = Gravity.CENTER_VERTICAL
    }

    val iconContainer = FrameLayout(context).apply {
        layoutParams = LinearLayout.LayoutParams(48.dpToPx(context), 48.dpToPx(context)).apply {
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
            cornerRadius = 10f
        }
    }
    iconContainer.addView(iconImageView)

    val titleCol = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1.0f
        )
    }

    val tagAndBadge = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        gravity = Gravity.CENTER_VERTICAL
    }

    val adBadge = TextView(context).apply {
        text = "Ad"
        setTextColor(onPrimaryColor)
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 9f)
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
            marginEnd = 6.dpToPx(context)
        }
    }
    tagAndBadge.addView(adBadge)

    val headlineTextView = TextView(context).apply {
        text = nativeAd.headline
        setTextColor(textColor)
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15f)
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
    titleCol.addView(tagAndBadge)
    titleCol.addView(headlineTextView)

    headerRow.addView(iconContainer)
    headerRow.addView(titleCol)
    mainLayout.addView(headerRow)

    // 3. Description body text area
    val bodyTextView = TextView(context).apply {
        text = nativeAd.body
        setTextColor(onSurfaceColor)
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f)
        maxLines = 3
        ellipsize = android.text.TextUtils.TruncateAt.END
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = 10.dpToPx(context)
            bottomMargin = 12.dpToPx(context)
        }
    }
    mainLayout.addView(bodyTextView)

    // 4. Prominent CTA Button filled across full-width
    val ctaButton = Button(context).apply {
        text = nativeAd.callToAction ?: "LEARN MORE"
        setTextColor(onPrimaryColor)
        textSize = 14f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        setPadding(24, 12, 24, 12)
        background = GradientDrawable().apply {
            setColor(primaryColor)
            cornerRadius = 24f
        }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
    mainLayout.addView(ctaButton)

    nativeAdView.addView(mainLayout)

    // Wire up Native Ad registration views
    nativeAdView.headlineView = headlineTextView
    nativeAdView.bodyView = bodyTextView
    nativeAdView.callToActionView = ctaButton
    nativeAdView.iconView = iconImageView

    // Bind asset sources safely
    if (nativeAd.icon != null) {
        iconImageView.setImageDrawable(nativeAd.icon?.drawable)
        iconContainer.visibility = View.VISIBLE
    } else {
        iconContainer.visibility = View.GONE
    }

    // Inform the SDK that the views are populated; the MediaView handles rich media binding.
    nativeAdView.registerNativeAd(nativeAd, mediaView)

    return nativeAdView
}

private fun Int.dpToPx(context: Context): Int {
    val density = context.resources.displayMetrics.density
    return (this * density).toInt()
}
