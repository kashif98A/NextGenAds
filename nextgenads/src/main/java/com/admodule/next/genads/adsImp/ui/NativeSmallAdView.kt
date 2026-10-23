package com.admodule.next.genads.adsImp.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.admodule.next.genads.adsImp.AdUnitIds
import com.admodule.next.genads.adsImp.manager.AdsManager
import com.google.android.libraries.ads.mobile.sdk.nativead.MediaView
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView

/** The designs available for [NativeSmallAdView]. All are compact, white and borderless. */
enum class NativeSmallStyle {
    /** Icon + "Ad" badge, headline and body; full-width button underneath. */
    CLASSIC,

    /** One row: icon, headline + body, and a compact button on the right. The shortest style. */
    COMPACT,

    /** App-install focused: icon, headline, "★★★★☆ 4.5 · Store · Price" line, body, full-width button. */
    RATING,

    /** Everything centered: icon on top, then headline, body and button. Suits dialogs and grids. */
    CENTERED,

    /** Text first: "Ad · advertiser" line, large headline and body, small icon + outlined button below. */
    MINIMAL
}

/**
 * A compact native ad with several designs — pick one with [style] (default [NativeSmallStyle.CLASSIC]).
 *
 * Image ads stay compact and register no MediaView (a tiny or hidden one would break AdMob's 120dp
 * media policy). A VIDEO ad must play in a MediaView, so for those a media block (>= 120dp) is added
 * on top, in every style.
 */
@Composable
fun NativeSmallAdView(
    adsManager: AdsManager,
    modifier: Modifier = Modifier,
    adUnitId: String = AdUnitIds.NATIVE,
    isEnabled: Boolean = true,
    style: NativeSmallStyle = NativeSmallStyle.CLASSIC
) {
    NativeTemplateHost(
        adsManager = adsManager,
        modifier = modifier,
        adUnitId = adUnitId,
        isEnabled = isEnabled,
        placeholder = {
            if (style == NativeSmallStyle.COMPACT) NativeBannerPlaceholder()
            else NativeAdPlaceholder(isMedium = false)
        },
        build = { context, nativeAd, colors ->
            when (style) {
                NativeSmallStyle.CLASSIC -> buildClassic(context, nativeAd, colors)
                NativeSmallStyle.COMPACT -> buildCompact(context, nativeAd, colors)
                NativeSmallStyle.RATING -> buildRating(context, nativeAd, colors)
                NativeSmallStyle.CENTERED -> buildCentered(context, nativeAd, colors)
                NativeSmallStyle.MINIMAL -> buildMinimal(context, nativeAd, colors)
            }
        }
    )
}

/** Card + root column, with the video MediaView (if the ad has video) already added on top. */
private class SmallFrame(val adView: NativeAdView, val root: LinearLayout, val media: MediaView?)

private fun smallFrame(context: Context, nativeAd: NativeAd, colors: NativeTemplateColors): SmallFrame {
    val adView = templateCard(context, colors)
    val root = vertical(context)
    val media = videoMediaOrNull(context, colors, nativeAd, horizontalInsetDp = 60)
    if (media != null) root.addView(media)
    adView.addView(root)
    return SmallFrame(adView, root, media)
}

/** Registers the optional asset views that are actually shown, then the ad itself. */
private fun SmallFrame.register(
    nativeAd: NativeAd,
    headline: View,
    cta: View,
    icon: View? = null,
    body: View? = null,
    advertiser: View? = null,
    rating: View? = null,
    store: View? = null,
    price: View? = null
) {
    fun View?.shown() = this?.takeIf { it.visibility == View.VISIBLE }
    adView.headlineView = headline
    adView.callToActionView = cta
    icon.shown()?.let { adView.iconView = it }
    body.shown()?.let { adView.bodyView = it }
    advertiser.shown()?.let { adView.advertiserView = it }
    rating.shown()?.let { adView.starRatingView = it }
    store.shown()?.let { adView.storeView = it }
    price.shown()?.let { adView.priceView = it }
    adView.registerNativeAd(nativeAd, media)
}

private fun buildClassic(context: Context, nativeAd: NativeAd, colors: NativeTemplateColors): NativeAdView {
    val frame = smallFrame(context, nativeAd, colors)
    val row = horizontal(context)
    val icon = templateIcon(context, colors, nativeAd, sizeDp = 56)
    val textColumn = vertical(context, linearParams(0, WRAP, 1f))
    val headline = templateText(context, colors.title, 15f, bold = true).apply {
        layoutParams = linearParams(MATCH, WRAP) { topMargin = context.dp(4) }
        text = nativeAd.headline
    }
    val body = templateText(context, colors.muted, 12f, maxLines = 2).apply {
        layoutParams = linearParams(MATCH, WRAP) { topMargin = context.dp(3) }
        bindOrHide(nativeAd.body)
    }
    textColumn.addView(adBadge(context, colors))
    textColumn.addView(headline)
    textColumn.addView(body)
    row.addView(icon)
    row.addView(textColumn)
    frame.root.addView(row)

    val cta = templateCta(context, colors, nativeAd.callToAction, fallback = "Open").apply {
        layoutParams = linearParams(MATCH, context.dp(44)) { topMargin = context.dp(12) }
    }
    frame.root.addView(cta)

    frame.register(nativeAd, headline, cta, icon = icon, body = body)
    return frame.adView
}

private fun buildCompact(context: Context, nativeAd: NativeAd, colors: NativeTemplateColors): NativeAdView {
    val frame = smallFrame(context, nativeAd, colors)
    val row = horizontal(context)
    val icon = templateIcon(context, colors, nativeAd, sizeDp = 52, marginEndDp = 10)
    val textColumn = vertical(context, linearParams(0, WRAP, 1f))
    val titleRow = horizontal(context)
    val headline = templateText(context, colors.title, 14f, bold = true).apply {
        layoutParams = linearParams(0, WRAP, 1f)
        text = nativeAd.headline
    }
    titleRow.addView(adBadge(context, colors).apply {
        layoutParams = linearParams(WRAP, WRAP) { marginEnd = context.dp(6) }
    })
    titleRow.addView(headline)
    val body = templateText(context, colors.muted, 12f, maxLines = 2).apply {
        layoutParams = linearParams(MATCH, WRAP) { topMargin = context.dp(4) }
        bindOrHide(nativeAd.body)
    }
    textColumn.addView(titleRow)
    textColumn.addView(body)

    val cta = templateCta(
        context, colors, nativeAd.callToAction, fallback = "Open",
        heightDp = 36, cornerDp = 18, textSp = 13f, fullWidth = false
    ).apply {
        layoutParams = linearParams(WRAP, context.dp(36)) { marginStart = context.dp(10) }
    }
    row.addView(icon)
    row.addView(textColumn)
    row.addView(cta)
    frame.root.addView(row)

    frame.register(nativeAd, headline, cta, icon = icon, body = body)
    return frame.adView
}

private fun buildRating(context: Context, nativeAd: NativeAd, colors: NativeTemplateColors): NativeAdView {
    val frame = smallFrame(context, nativeAd, colors)
    val row = horizontal(context)
    val icon = templateIcon(context, colors, nativeAd, sizeDp = 60, cornerDp = 14)
    val textColumn = vertical(context, linearParams(0, WRAP, 1f))
    val titleRow = horizontal(context)
    val headline = templateText(context, colors.title, 15f, bold = true).apply {
        layoutParams = linearParams(0, WRAP, 1f)
        text = nativeAd.headline
    }
    titleRow.addView(headline)
    titleRow.addView(adBadge(context, colors).apply {
        layoutParams = linearParams(WRAP, WRAP) { marginStart = context.dp(6) }
    })
    val metaRow = horizontal(context).apply {
        layoutParams = linearParams(MATCH, WRAP) { topMargin = context.dp(5) }
    }
    val rating = starRatingText(context, colors, nativeAd.starRating).apply {
        layoutParams = linearParams(WRAP, WRAP) { marginEnd = context.dp(8) }
    }
    val store = templateText(context, colors.muted, 12f).apply {
        layoutParams = linearParams(WRAP, WRAP) { marginEnd = context.dp(8) }
        bindOrHide(nativeAd.store)
    }
    val price = templateText(context, colors.primary, 12f, bold = true).apply {
        layoutParams = linearParams(WRAP, WRAP)
        bindOrHide(nativeAd.price)
    }
    metaRow.addView(rating)
    metaRow.addView(store)
    metaRow.addView(price)
    // No rating/store/price (e.g. a non-app ad): show the advertiser on that line instead.
    val advertiser = templateText(context, colors.muted, 12f).apply {
        layoutParams = linearParams(MATCH, WRAP) { topMargin = context.dp(5) }
        val hasMeta = listOf(rating, store, price).any { it.visibility == View.VISIBLE }
        bindOrHide(if (hasMeta) null else nativeAd.advertiser)
    }
    if (listOf(rating, store, price).none { it.visibility == View.VISIBLE }) metaRow.visibility = View.GONE
    textColumn.addView(titleRow)
    textColumn.addView(metaRow)
    textColumn.addView(advertiser)
    row.addView(icon)
    row.addView(textColumn)
    frame.root.addView(row)

    val body = templateText(context, colors.muted, 13f, maxLines = 2).apply {
        layoutParams = linearParams(MATCH, WRAP) { topMargin = context.dp(10) }
        bindOrHide(nativeAd.body)
    }
    frame.root.addView(body)

    val cta = templateCta(context, colors, nativeAd.callToAction, fallback = "Install").apply {
        layoutParams = linearParams(MATCH, context.dp(44)) { topMargin = context.dp(12) }
    }
    frame.root.addView(cta)

    frame.register(
        nativeAd, headline, cta,
        icon = icon, body = body, advertiser = advertiser, rating = rating, store = store, price = price
    )
    return frame.adView
}

private fun buildCentered(context: Context, nativeAd: NativeAd, colors: NativeTemplateColors): NativeAdView {
    val frame = smallFrame(context, nativeAd, colors)
    val column = vertical(context).apply { gravity = Gravity.CENTER_HORIZONTAL }

    val icon = templateIcon(context, colors, nativeAd, sizeDp = 60, cornerDp = 16, marginEndDp = 0).apply {
        layoutParams = linearParams(context.dp(60), context.dp(60)) { bottomMargin = context.dp(10) }
    }
    val badge = adBadge(context, colors)
    val headline = templateText(context, colors.title, 16f, maxLines = 2, bold = true).apply {
        layoutParams = linearParams(MATCH, WRAP) { topMargin = context.dp(6) }
        gravity = Gravity.CENTER
        text = nativeAd.headline
    }
    val rating = starRatingText(context, colors, nativeAd.starRating).apply {
        layoutParams = linearParams(WRAP, WRAP) { topMargin = context.dp(4) }
    }
    val body = templateText(context, colors.muted, 13f, maxLines = 2).apply {
        layoutParams = linearParams(MATCH, WRAP) { topMargin = context.dp(6) }
        gravity = Gravity.CENTER
        bindOrHide(nativeAd.body)
    }
    val cta = templateCta(context, colors, nativeAd.callToAction, fallback = "Open", cornerDp = 22).apply {
        layoutParams = linearParams(MATCH, context.dp(44)) { topMargin = context.dp(14) }
    }
    column.addView(icon)
    column.addView(badge)
    column.addView(headline)
    column.addView(rating)
    column.addView(body)
    column.addView(cta)
    frame.root.addView(column)

    frame.register(nativeAd, headline, cta, icon = icon, body = body, rating = rating)
    return frame.adView
}

private fun buildMinimal(context: Context, nativeAd: NativeAd, colors: NativeTemplateColors): NativeAdView {
    val frame = smallFrame(context, nativeAd, colors)

    // "Ad · advertiser" line.
    val topRow = horizontal(context)
    val advertiser = templateText(context, colors.muted, 12f).apply {
        layoutParams = linearParams(0, WRAP, 1f) { marginStart = context.dp(8) }
        bindOrHide(nativeAd.advertiser)
    }
    topRow.addView(adBadge(context, colors))
    topRow.addView(advertiser)
    frame.root.addView(topRow)

    val headline = templateText(context, colors.title, 17f, maxLines = 2, bold = true).apply {
        layoutParams = linearParams(MATCH, WRAP) { topMargin = context.dp(8) }
        text = nativeAd.headline
    }
    val body = templateText(context, colors.muted, 13f, maxLines = 2).apply {
        layoutParams = linearParams(MATCH, WRAP) { topMargin = context.dp(4) }
        setLineSpacing(context.dp(2).toFloat(), 1f)
        bindOrHide(nativeAd.body)
    }
    frame.root.addView(headline)
    frame.root.addView(body)

    // Footer: small icon + rating on the left, outlined button on the right.
    val footer = horizontal(context).apply {
        layoutParams = linearParams(MATCH, WRAP) { topMargin = context.dp(12) }
    }
    val icon = templateIcon(context, colors, nativeAd, sizeDp = 32, cornerDp = 8, marginEndDp = 8)
    val rating = starRatingText(context, colors, nativeAd.starRating)
    val spacer = View(context).apply { layoutParams = linearParams(0, 1, 1f) }
    val cta = outlinedCta(context, colors, nativeAd.callToAction ?: "Learn more")
    footer.addView(icon)
    footer.addView(rating)
    footer.addView(spacer)
    footer.addView(cta)
    frame.root.addView(footer)

    frame.register(nativeAd, headline, cta, icon = icon, body = body, advertiser = advertiser, rating = rating)
    return frame.adView
}

/** A white pill with a theme-colored outline and label — a lighter call-to-action for [buildMinimal]. */
private fun outlinedCta(context: Context, colors: NativeTemplateColors, label: String) = Button(context).apply {
    text = label.ifBlank { "Learn more" }
    isAllCaps = false
    setTextColor(colors.primary)
    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
    typeface = android.graphics.Typeface.DEFAULT_BOLD
    gravity = Gravity.CENTER
    stateListAnimator = null
    minHeight = 0
    minimumHeight = 0
    minWidth = 0
    minimumWidth = 0
    setPadding(context.dp(18), 0, context.dp(18), 0)
    background = GradientDrawable().apply {
        setColor(colors.card)
        cornerRadius = context.dp(18).toFloat()
        setStroke(context.dp(1).coerceAtLeast(2), colors.primary)
    }
    layoutParams = linearParams(WRAP, context.dp(36))
}
