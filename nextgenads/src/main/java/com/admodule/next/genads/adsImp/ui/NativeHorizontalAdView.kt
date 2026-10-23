package com.admodule.next.genads.adsImp.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.admodule.next.genads.adsImp.AdUnitIds
import com.admodule.next.genads.adsImp.manager.AdsManager
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView

/**
 * An article / news-list style native ad: square media on the LEFT (120dp — AdMob's minimum media
 * size), "Ad" badge, two-line headline, advertiser and body on the right, and a full-width
 * call-to-action underneath. Blends into content feeds of thumbnails + titles.
 */
@Composable
fun NativeHorizontalAdView(
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
        placeholder = { NativeHorizontalPlaceholder() },
        build = ::createNativeHorizontalView
    )
}

private fun createNativeHorizontalView(
    context: Context,
    nativeAd: NativeAd,
    colors: NativeTemplateColors
): NativeAdView {
    val adView = templateCard(context, colors, paddingDp = 12)
    val root = vertical(context)

    val topRow = horizontal(context).apply { gravity = android.view.Gravity.TOP }

    val mediaSize = context.dp(MIN_MEDIA_DP)
    val media = templateMedia(context, colors, mediaSize, mediaSize).apply {
        layoutParams = linearParams(mediaSize, mediaSize) { marginEnd = context.dp(12) }
    }

    val textColumn = vertical(context, linearParams(0, WRAP, 1f))
    val badge = adBadge(context, colors)
    val headline = templateText(context, colors.title, 15f, maxLines = 2, bold = true).apply {
        layoutParams = linearParams(MATCH, WRAP) { topMargin = context.dp(6) }
        text = nativeAd.headline
    }
    val advertiser = templateText(context, colors.primary, 12f, bold = true).apply {
        layoutParams = linearParams(MATCH, WRAP) { topMargin = context.dp(6) }
        bindOrHide(nativeAd.advertiser)
    }
    val body = templateText(context, colors.muted, 13f, maxLines = 3).apply {
        layoutParams = linearParams(MATCH, WRAP) { topMargin = context.dp(6) }
        bindOrHide(nativeAd.body)
    }
    textColumn.addView(badge)
    textColumn.addView(headline)
    textColumn.addView(advertiser)
    textColumn.addView(body)

    topRow.addView(media)
    topRow.addView(textColumn)
    root.addView(topRow)

    val cta = templateCta(context, colors, nativeAd.callToAction, fallback = "Learn more").apply {
        layoutParams = linearParams(MATCH, context.dp(44)) { topMargin = context.dp(12) }
    }
    root.addView(cta)
    adView.addView(root)

    adView.headlineView = headline
    adView.callToActionView = cta
    if (advertiser.visibility == android.view.View.VISIBLE) adView.advertiserView = advertiser
    if (body.visibility == android.view.View.VISIBLE) adView.bodyView = body

    adView.registerNativeAd(nativeAd, media)
    return adView
}
