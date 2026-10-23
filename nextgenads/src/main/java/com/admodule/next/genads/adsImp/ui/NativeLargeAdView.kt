package com.admodule.next.genads.adsImp.ui

import android.content.Context
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.admodule.next.genads.adsImp.AdUnitIds
import com.admodule.next.genads.adsImp.manager.AdsManager
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView

/**
 * The richest template, built for app-install ads: large media following the creative's aspect
 * ratio, a big icon with headline and a "★★★★☆ 4.5 · Store · Price" line, the body, and a tall
 * full-width call-to-action. Suited to result screens, "level complete" pages and empty states.
 */
@Composable
fun NativeLargeAdView(
    adsManager: AdsManager,
    modifier: Modifier = Modifier,
    adUnitId: String = AdUnitIds.NATIVE,
    isEnabled: Boolean = true
) {
    NativeTemplateHost(
        adsManager = adsManager,
        modifier = modifier,
        adUnitId = adUnitId,
        isEnabled = isEnabled,
        placeholder = { NativeAdPlaceholder(isMedium = true) },
        build = ::createNativeLargeView
    )
}

private fun createNativeLargeView(
    context: Context,
    nativeAd: NativeAd,
    colors: NativeTemplateColors
): NativeAdView {
    val adView = templateCard(context, colors, paddingDp = 14)
    val root = vertical(context)

    // 1) Large media.
    val media = templateMedia(
        context, colors,
        widthPx = MATCH,
        heightPx = mediaHeightFor(context, nativeAd, horizontalInsetDp = 60, maxDp = 280)
    ).apply {
        layoutParams = (layoutParams as android.widget.LinearLayout.LayoutParams).apply {
            bottomMargin = context.dp(14)
        }
    }
    root.addView(media)

    // 2) Icon + badge/headline + rating · store · price.
    val infoRow = horizontal(context)
    val icon = templateIcon(context, colors, nativeAd, sizeDp = 56, cornerDp = 14)
    val infoColumn = vertical(context, linearParams(0, WRAP, 1f))
    val headline = templateText(context, colors.title, 16f, maxLines = 2, bold = true).apply {
        layoutParams = linearParams(MATCH, WRAP) { topMargin = context.dp(4) }
        text = nativeAd.headline
    }
    val metaRow = horizontal(context).apply {
        layoutParams = linearParams(MATCH, WRAP) { topMargin = context.dp(4) }
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
    infoColumn.addView(adBadge(context, colors))
    infoColumn.addView(headline)
    infoColumn.addView(metaRow)
    infoRow.addView(icon)
    infoRow.addView(infoColumn)
    root.addView(infoRow)

    // 3) Body.
    val body = templateText(context, colors.muted, 13f, maxLines = 3).apply {
        layoutParams = linearParams(MATCH, WRAP) { topMargin = context.dp(12) }
        setLineSpacing(context.dp(2).toFloat(), 1f)
        bindOrHide(nativeAd.body)
    }
    root.addView(body)

    // 4) Tall call-to-action.
    val cta = templateCta(context, colors, nativeAd.callToAction, fallback = "Install", heightDp = 48, textSp = 15f).apply {
        layoutParams = linearParams(MATCH, context.dp(48)) { topMargin = context.dp(14) }
    }
    root.addView(cta)

    adView.addView(root)

    adView.headlineView = headline
    adView.callToActionView = cta
    if (icon.visibility == View.VISIBLE) adView.iconView = icon
    if (body.visibility == View.VISIBLE) adView.bodyView = body
    if (rating.visibility == View.VISIBLE) adView.starRatingView = rating
    if (store.visibility == View.VISIBLE) adView.storeView = store
    if (price.visibility == View.VISIBLE) adView.priceView = price

    adView.registerNativeAd(nativeAd, media)
    return adView
}
