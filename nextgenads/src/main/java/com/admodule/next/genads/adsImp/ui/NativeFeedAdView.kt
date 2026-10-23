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
 * A social-feed "sponsored post": a header with a round icon, the advertiser (or headline) and an
 * "Ad · Sponsored" line, the body as the post text, edge-to-edge media, and a bottom action bar
 * with the headline, store / price and a call-to-action. Made to sit between posts in a feed.
 */
@Composable
fun NativeFeedAdView(
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
        build = ::createNativeFeedView
    )
}

private fun createNativeFeedView(
    context: Context,
    nativeAd: NativeAd,
    colors: NativeTemplateColors
): NativeAdView {
    // No card padding: the media runs edge to edge and the card's outline clips its corners.
    val adView = templateCard(context, colors, paddingDp = 0)
    val root = vertical(context)
    val inset = context.dp(CARD_HORIZONTAL_PADDING_DP)

    // 1) Header: round icon + name + "Ad · Sponsored".
    val header = horizontal(context).apply { setPadding(inset, inset, inset, context.dp(10)) }
    val icon = templateIcon(context, colors, nativeAd, sizeDp = 40, cornerDp = 20, marginEndDp = 10)
    val nameColumn = vertical(context, linearParams(0, WRAP, 1f))
    val hasAdvertiser = !nativeAd.advertiser.isNullOrBlank()
    // The header names the advertiser when there is one; otherwise it carries the headline.
    val name = templateText(context, colors.title, 14f, bold = true).apply {
        text = if (hasAdvertiser) nativeAd.advertiser else nativeAd.headline
    }
    val sponsoredRow = horizontal(context).apply {
        layoutParams = linearParams(MATCH, WRAP) { topMargin = context.dp(3) }
    }
    val sponsored = templateText(context, colors.muted, 12f).apply {
        layoutParams = linearParams(WRAP, WRAP) { marginStart = context.dp(6) }
        text = "Sponsored"
    }
    sponsoredRow.addView(adBadge(context, colors))
    sponsoredRow.addView(sponsored)
    nameColumn.addView(name)
    nameColumn.addView(sponsoredRow)
    header.addView(icon)
    header.addView(nameColumn)
    root.addView(header)

    // 2) Post text.
    val body = templateText(context, colors.title, 14f, maxLines = 3).apply {
        layoutParams = linearParams(MATCH, WRAP)
        setPadding(inset, 0, inset, context.dp(10))
        setLineSpacing(context.dp(2).toFloat(), 1f)
        bindOrHide(nativeAd.body)
    }
    root.addView(body)

    // 3) Edge-to-edge media following the creative's aspect ratio.
    val media = templateMedia(
        context, colors,
        widthPx = MATCH,
        heightPx = mediaHeightFor(context, nativeAd, horizontalInsetDp = 32, maxDp = 280),
        cornerDp = 0
    )
    root.addView(media)

    // 4) Action bar: headline + store/price on the left, CTA on the right.
    val actionBar = horizontal(context).apply {
        setPadding(inset, inset, inset, inset)
        setBackgroundColor(colors.subtleSurface)
    }
    val actionText = vertical(context, linearParams(0, WRAP, 1f) { marginEnd = context.dp(10) })
    val barTitle = templateText(context, colors.title, 14f, bold = true).apply {
        bindOrHide(if (hasAdvertiser) nativeAd.headline else null)
    }
    val metaRow = horizontal(context).apply {
        layoutParams = linearParams(MATCH, WRAP) { topMargin = context.dp(3) }
    }
    val store = templateText(context, colors.muted, 12f).apply {
        layoutParams = linearParams(WRAP, WRAP)
        bindOrHide(nativeAd.store)
    }
    val price = templateText(context, colors.muted, 12f).apply {
        layoutParams = linearParams(WRAP, WRAP) { marginStart = context.dp(8) }
        bindOrHide(nativeAd.price)
    }
    val rating = starRatingText(context, colors, nativeAd.starRating).apply {
        layoutParams = linearParams(WRAP, WRAP) { marginStart = context.dp(8) }
    }
    metaRow.addView(store)
    metaRow.addView(price)
    metaRow.addView(rating)
    actionText.addView(barTitle)
    actionText.addView(metaRow)

    val cta = templateCta(
        context, colors, nativeAd.callToAction, fallback = "Learn more",
        heightDp = 40, cornerDp = 10, textSp = 13f, fullWidth = false
    )
    actionBar.addView(actionText)
    actionBar.addView(cta)
    root.addView(actionBar)

    adView.addView(root)

    if (hasAdvertiser) {
        adView.advertiserView = name
        adView.headlineView = barTitle
    } else {
        adView.headlineView = name
    }
    adView.callToActionView = cta
    if (icon.visibility == View.VISIBLE) adView.iconView = icon
    if (body.visibility == View.VISIBLE) adView.bodyView = body
    if (store.visibility == View.VISIBLE) adView.storeView = store
    if (price.visibility == View.VISIBLE) adView.priceView = price
    if (rating.visibility == View.VISIBLE) adView.starRatingView = rating

    adView.registerNativeAd(nativeAd, media)
    return adView
}
