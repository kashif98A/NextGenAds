package com.admodule.next.genads.adsImp.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.libraries.ads.mobile.sdk.nativead.MediaView
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView
import com.admodule.next.genads.adsImp.AdUnitIds
import com.admodule.next.genads.adsImp.manager.AdsManager

/**
 * A custom NATIVE ad template styled to look like a "collapsible banner" card: a large media
 * image on top with a chevron that collapses/expands it, then an app icon + headline + body with
 * an "Ad" badge, and a full-width gradient call-to-action button.
 *
 * NOTE: a true Google "collapsible banner" cannot be custom-designed — its creative is served and
 * controlled by Google. This reproduces the look using a native ad, which is fully customizable.
 */
@Composable
fun NativeCollapsibleAdView(
    adsManager: AdsManager,
    modifier: Modifier = Modifier,
    adUnitId: String = AdUnitIds.NATIVE,
    isEnabled: Boolean = true
) {
    // Gate: show nothing unless this unit is enabled (remote-config style flag). Being offline only
    // blocks the FIRST request; an ad already on screen survives a brief network drop.
    if (!isEnabled || !rememberCanShowInlineAd(adsManager)) return

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
                factory = { ctx -> createCollapsibleNativeView(ctx, slot.ad) },
                update = {}
            )
            NativeAdSlot.Empty -> Unit
        }
    }
}

/**
 * Builds the card layout in pure Kotlin (no XML), mirroring the reference design.
 */
private fun createCollapsibleNativeView(context: Context, nativeAd: NativeAd): NativeAdView {
    val headlineColor = Color.parseColor("#7C3AED") // brand-style purple
    val bodyColor = Color.parseColor("#5F6368")     // muted grey
    val cardColor = Color.WHITE
    val borderColor = Color.parseColor("#E1E2EC")

    val nativeAdView = NativeAdView(context).apply {
        layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        setPadding(12.dpToPx(context), 12.dpToPx(context), 12.dpToPx(context), 12.dpToPx(context))
        background = GradientDrawable().apply {
            setColor(cardColor)
            cornerRadius = 18.dpToPx(context).toFloat()
            setStroke(2, borderColor)
        }
    }

    val root = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    // 0) Always-visible, tappable header BAR that shrinks / expands the media.
    //    Kept OUTSIDE the media so it can never be covered — neither by the media itself nor by
    //    the ad network's mandatory attribution overlay (e.g. "Ad served by Meta" / AdChoices),
    //    which is always drawn in the media's top-right corner. The whole bar is clickable (big hit
    //    target) and its label changes with state so the expand control is impossible to miss.
    val headerRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        isClickable = true
        isFocusable = true
        setPadding(12.dpToPx(context), 9.dpToPx(context), 12.dpToPx(context), 9.dpToPx(context))
        background = GradientDrawable().apply {
            setColor(Color.parseColor("#F3F0FF")) // light purple tint so it reads as a tappable bar
            cornerRadius = 12.dpToPx(context).toFloat()
        }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 10.dpToPx(context) }
    }
    val headerLabel = TextView(context).apply {
        text = "Sponsored — tap to shrink"
        setTextColor(Color.parseColor("#7C3AED"))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        typeface = Typeface.DEFAULT_BOLD
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f)
    }
    val chevron = TextView(context).apply {
        text = "▾" // down chevron when expanded; rotates to point up when collapsed
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        includeFontPadding = false
        setPadding(0, 0, 0, 0)
        val s = 28.dpToPx(context)
        layoutParams = LinearLayout.LayoutParams(s, s)
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#7C3AED")) // brand purple — clearly visible against the bar
        }
    }
    headerRow.addView(headerLabel)
    headerRow.addView(chevron)
    root.addView(headerRow)

    // 1) Collapsible media area (no overlay controls — they live in the header above).
    //    POLICY: a registered MediaView must never be hidden (GONE / 0px) — AdMob treats that as an
    //    obscured ad asset and impressions may not count. "Collapsing" therefore shrinks the media to
    //    AdMob's 120dp minimum MediaView size instead of hiding it; expanded, it follows the
    //    creative's own aspect ratio so landscape images/videos aren't cropped.
    val collapsedMediaHeight = MIN_MEDIA_VIEW_DP.dpToPx(context)
    val expandedMediaHeight = expandedMediaHeightPx(context, nativeAd, collapsedMediaHeight)
    val mediaContainer = FrameLayout(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = 12.dpToPx(context)
        }
    }

    val mediaView = MediaView(context).apply {
        layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            expandedMediaHeight
        )
        clipToOutline = true
        background = GradientDrawable().apply {
            setColor(Color.parseColor("#11000000"))
            cornerRadius = 14.dpToPx(context).toFloat()
        }
    }
    mediaContainer.addView(mediaView)
    root.addView(mediaContainer)

    // Tapping anywhere on the header bar shrinks/expands the media (it always stays visible).
    var isExpanded = true
    headerRow.setOnClickListener {
        isExpanded = !isExpanded
        mediaView.layoutParams = mediaView.layoutParams.apply {
            height = if (isExpanded) expandedMediaHeight else collapsedMediaHeight
        }
        chevron.rotation = if (isExpanded) 0f else 180f
        headerLabel.text = if (isExpanded) "Sponsored — tap to shrink" else "Sponsored — tap to expand"
    }

    // 2) Icon + headline/body row, with an "Ad" badge on the right.
    val contentRow = LinearLayout(context).apply {
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
            setColor(Color.parseColor("#EDE9FE"))
            cornerRadius = 12.dpToPx(context).toFloat()
        }
    }
    iconContainer.addView(iconImageView)

    val textColumn = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f)
    }
    val headlineTextView = TextView(context).apply {
        text = nativeAd.headline
        setTextColor(headlineColor)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        typeface = Typeface.DEFAULT_BOLD
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }
    val bodyTextView = TextView(context).apply {
        text = nativeAd.body
        setTextColor(bodyColor)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 2.dpToPx(context) }
    }
    textColumn.addView(headlineTextView)
    textColumn.addView(bodyTextView)

    val adBadge = TextView(context).apply {
        text = "Ad"
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
        gravity = Gravity.CENTER
        setPadding(8.dpToPx(context), 2.dpToPx(context), 8.dpToPx(context), 2.dpToPx(context))
        background = GradientDrawable().apply {
            setColor(Color.parseColor("#FFA726"))
            cornerRadius = 6.dpToPx(context).toFloat()
        }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP
            marginStart = 8.dpToPx(context)
        }
    }

    contentRow.addView(iconContainer)
    contentRow.addView(textColumn)
    contentRow.addView(adBadge)
    root.addView(contentRow)

    // 3) Full-width gradient call-to-action button ("Install").
    val ctaButton = Button(context).apply {
        text = nativeAd.callToAction ?: "Install"
        isAllCaps = false
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        typeface = Typeface.DEFAULT_BOLD
        stateListAnimator = null
        setPadding(16.dpToPx(context), 14.dpToPx(context), 16.dpToPx(context), 14.dpToPx(context))
        background = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(Color.parseColor("#FF2E63"), Color.parseColor("#FF5E3A"))
        ).apply {
            cornerRadius = 28.dpToPx(context).toFloat()
        }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 14.dpToPx(context) }
    }
    root.addView(ctaButton)

    nativeAdView.addView(root)

    // Register asset views so the SDK tracks clicks/impressions correctly.
    nativeAdView.headlineView = headlineTextView
    nativeAdView.bodyView = bodyTextView
    nativeAdView.iconView = iconImageView
    nativeAdView.callToActionView = ctaButton

    if (nativeAd.icon != null) {
        iconImageView.setImageDrawable(nativeAd.icon?.drawable)
        iconContainer.visibility = View.VISIBLE
    } else {
        iconContainer.visibility = View.GONE
    }

    nativeAdView.registerNativeAd(nativeAd, mediaView)

    return nativeAdView
}

/** AdMob native policy: a MediaView must be at least 120dp x 120dp whenever it is on screen. */
private const val MIN_MEDIA_VIEW_DP = 120

/**
 * Height for the expanded media: the card's approximate content width divided by the creative's
 * aspect ratio, kept between the policy minimum and a sensible maximum.
 */
private fun expandedMediaHeightPx(context: Context, nativeAd: NativeAd, minHeightPx: Int): Int {
    val fallback = 170.dpToPx(context)
    val aspectRatio = nativeAd.mediaContent.aspectRatio.takeIf { it > 0f } ?: return fallback
    // Screen width minus the card padding + typical screen gutters.
    val contentWidth = context.resources.displayMetrics.widthPixels - 56.dpToPx(context)
    return (contentWidth / aspectRatio).toInt().coerceIn(minHeightPx, 260.dpToPx(context))
}

private fun Int.dpToPx(context: Context): Int {
    val density = context.resources.displayMetrics.density
    return (this * density).toInt()
}
