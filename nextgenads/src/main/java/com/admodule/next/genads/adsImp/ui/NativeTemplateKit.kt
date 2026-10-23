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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import com.admodule.next.genads.adsImp.manager.AdsManager
import com.google.android.libraries.ads.mobile.sdk.nativead.MediaView
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView
import kotlin.math.roundToInt

/*
 * Shared building blocks for the native ad templates (Banner, Horizontal, Feed, Large): one Compose
 * host that handles gating + the shared pool, and small View factories so every template has the
 * same look — white borderless card, fixed dark text (readable on white in dark mode too), and the
 * app theme's primary color for the "Ad" badge and the call-to-action.
 */

internal const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
internal const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

/** AdMob native policy: a MediaView must be at least 120dp x 120dp whenever it is on screen. */
internal const val MIN_MEDIA_DP = 120

/** Palette shared by the templates. Only the accent comes from the app theme. */
internal class NativeTemplateColors(val primary: Int, val onPrimary: Int) {
    val card = Color.WHITE
    val title = Color.parseColor("#1C1B1F")
    val muted = Color.parseColor("#5F6368")
    val placeholder = Color.parseColor("#F1F3F4")
    val subtleSurface = Color.parseColor("#F8F9FA")
    val star = Color.parseColor("#F9AB00")
}

/**
 * Compose host shared by the templates: renders nothing until the SDK is initialized and the device
 * has been online, shows [placeholder] while an ad is taken from the shared pool, then builds the
 * native view with [build]. The ad is destroyed when the slot leaves composition.
 */
@Composable
internal fun NativeTemplateHost(
    adsManager: AdsManager,
    modifier: Modifier,
    adUnitId: String,
    isEnabled: Boolean,
    placeholder: @Composable () -> Unit,
    build: (Context, NativeAd, NativeTemplateColors) -> NativeAdView
) {
    if (!isEnabled || !rememberCanShowInlineAd(adsManager)) return

    val colors = NativeTemplateColors(
        primary = MaterialTheme.colorScheme.primary.toArgb(),
        onPrimary = MaterialTheme.colorScheme.onPrimary.toArgb()
    )
    val slot = rememberNativeAdSlot(adsManager, adUnitId)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        contentAlignment = Alignment.Center
    ) {
        when (slot) {
            NativeAdSlot.Loading -> placeholder()
            is NativeAdSlot.Ready -> AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                factory = { ctx -> build(ctx, slot.ad, colors) }
            )
            NativeAdSlot.Empty -> Unit
        }
    }
}

internal fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

internal fun linearParams(
    width: Int,
    height: Int,
    weight: Float = 0f,
    block: LinearLayout.LayoutParams.() -> Unit = {}
) = LinearLayout.LayoutParams(width, height, weight).apply(block)

internal fun roundedBackground(color: Int, radiusPx: Float) = GradientDrawable().apply {
    setColor(color)
    cornerRadius = radiusPx
}

/** Left/right inner padding of every native card — kept tight so content gets the width. */
internal const val CARD_HORIZONTAL_PADDING_DP = 10

/**
 * The root ad view: a white, borderless card with rounded corners. [paddingDp] is the top/bottom
 * padding; left/right use [horizontalPaddingDp] (tighter by default).
 */
internal fun templateCard(
    context: Context,
    colors: NativeTemplateColors,
    paddingDp: Int = 14,
    cornerDp: Int = 16,
    horizontalPaddingDp: Int = minOf(paddingDp, CARD_HORIZONTAL_PADDING_DP)
) = NativeAdView(context).apply {
    layoutParams = FrameLayout.LayoutParams(MATCH, WRAP)
    val vertical = context.dp(paddingDp)
    val horizontal = context.dp(horizontalPaddingDp)
    setPadding(horizontal, vertical, horizontal, vertical)
    background = roundedBackground(colors.card, context.dp(cornerDp).toFloat())
    // Lets edge-to-edge content (e.g. the feed template's media) follow the rounded corners.
    clipToOutline = true
}

internal fun vertical(context: Context, params: LinearLayout.LayoutParams = linearParams(MATCH, WRAP)) =
    LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = params
    }

internal fun horizontal(context: Context, params: LinearLayout.LayoutParams = linearParams(MATCH, WRAP)) =
    LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = params
    }

/** The mandatory "Ad" attribution badge. */
internal fun adBadge(context: Context, colors: NativeTemplateColors) = TextView(context).apply {
    text = "Ad"
    setTextColor(colors.onPrimary)
    setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
    typeface = Typeface.DEFAULT_BOLD
    gravity = Gravity.CENTER
    setPadding(context.dp(6), context.dp(1), context.dp(6), context.dp(1))
    background = roundedBackground(colors.primary, context.dp(4).toFloat())
    layoutParams = linearParams(WRAP, WRAP)
}

internal fun templateText(
    context: Context,
    color: Int,
    sizeSp: Float,
    maxLines: Int = 1,
    bold: Boolean = false
) = TextView(context).apply {
    setTextColor(color)
    setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
    if (bold) typeface = Typeface.DEFAULT_BOLD
    this.maxLines = maxLines
    ellipsize = TextUtils.TruncateAt.END
    includeFontPadding = false
    layoutParams = linearParams(MATCH, WRAP)
}

/** Sets [value] on the view, or hides it when the ad doesn't provide that (optional) asset. */
internal fun TextView.bindOrHide(value: String?) {
    if (value.isNullOrBlank()) {
        visibility = View.GONE
    } else {
        text = value
        visibility = View.VISIBLE
    }
}

/** "★★★★☆ 4.5" — hidden when the ad has no star rating. */
internal fun starRatingText(context: Context, colors: NativeTemplateColors, rating: Double?) =
    templateText(context, colors.star, 12f).apply {
        layoutParams = linearParams(WRAP, WRAP)
        if (rating == null || rating <= 0.0) {
            visibility = View.GONE
        } else {
            val full = rating.roundToInt().coerceIn(0, 5)
            text = "★".repeat(full) + "☆".repeat(5 - full) + "  " + String.format("%.1f", rating)
        }
    }

/** Square app icon with a soft placeholder; collapses when the ad has no icon. */
internal fun templateIcon(
    context: Context,
    colors: NativeTemplateColors,
    nativeAd: NativeAd,
    sizeDp: Int,
    cornerDp: Int = 12,
    marginEndDp: Int = 12
) = ImageView(context).apply {
    layoutParams = linearParams(context.dp(sizeDp), context.dp(sizeDp)) {
        marginEnd = context.dp(marginEndDp)
    }
    scaleType = ImageView.ScaleType.CENTER_CROP
    clipToOutline = true
    background = roundedBackground(colors.placeholder, context.dp(cornerDp).toFloat())
    val drawable = nativeAd.icon?.drawable
    if (drawable != null) setImageDrawable(drawable) else visibility = View.GONE
}

/** Solid call-to-action in the theme color, with no Material shadow or forced caps. */
internal fun templateCta(
    context: Context,
    colors: NativeTemplateColors,
    label: String?,
    fallback: String,
    heightDp: Int = 44,
    cornerDp: Int = 12,
    textSp: Float = 14f,
    fullWidth: Boolean = true
) = Button(context).apply {
    text = label?.takeIf { it.isNotBlank() } ?: fallback
    isAllCaps = false
    setTextColor(colors.onPrimary)
    setTextSize(TypedValue.COMPLEX_UNIT_SP, textSp)
    typeface = Typeface.DEFAULT_BOLD
    gravity = Gravity.CENTER
    stateListAnimator = null
    minHeight = 0
    minimumHeight = 0
    minWidth = 0
    minimumWidth = 0
    setPadding(context.dp(16), 0, context.dp(16), 0)
    background = roundedBackground(colors.primary, context.dp(cornerDp).toFloat())
    layoutParams = linearParams(if (fullWidth) MATCH else WRAP, context.dp(heightDp))
}

internal fun templateMedia(
    context: Context,
    colors: NativeTemplateColors,
    widthPx: Int,
    heightPx: Int,
    cornerDp: Int = 12
) = MediaView(context).apply {
    layoutParams = linearParams(widthPx, heightPx)
    clipToOutline = cornerDp > 0
    background = roundedBackground(colors.placeholder, context.dp(cornerDp).toFloat())
}

/**
 * Media height that follows the creative's aspect ratio for a media [horizontalInsetDp] narrower
 * than the screen, kept between the policy minimum and [maxDp].
 */
internal fun mediaHeightFor(context: Context, nativeAd: NativeAd, horizontalInsetDp: Int, maxDp: Int = 260): Int {
    val min = context.dp(MIN_MEDIA_DP)
    val aspectRatio = nativeAd.mediaContent.aspectRatio.takeIf { it > 0f } ?: return context.dp(180)
    val width = context.resources.displayMetrics.widthPixels - context.dp(horizontalInsetDp)
    return (width / aspectRatio).roundToInt().coerceIn(min, context.dp(maxDp))
}

/**
 * For compact templates that have no media slot: a MediaView to show ONLY when the ad carries video,
 * or null. AdMob requires video to play in a registered MediaView (the native ad validator flags
 * "MediaView not used for main image or video asset"), and a compact template can't choose which
 * ad the shared pool hands it. Image ads keep the compact layout; video ads get a media block of at
 * least the policy minimum height above it.
 */
internal fun videoMediaOrNull(
    context: Context,
    colors: NativeTemplateColors,
    nativeAd: NativeAd,
    horizontalInsetDp: Int
): MediaView? {
    if (!nativeAd.mediaContent.hasVideoContent) return null
    return templateMedia(
        context, colors,
        widthPx = MATCH,
        heightPx = mediaHeightFor(context, nativeAd, horizontalInsetDp, maxDp = 200)
    ).apply {
        layoutParams = (layoutParams as LinearLayout.LayoutParams).apply { bottomMargin = context.dp(10) }
    }
}
