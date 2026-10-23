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
 * A slim, banner-height native ad (~70dp): icon, "Ad" badge + headline, a second line with the star
 * rating (or the advertiser / body), and a compact call-to-action pill on the right.
 *
 * Fits list rows and bottom bars where a medium card is too tall. Image ads stay a slim strip with
 * no MediaView (a tiny or hidden one would break AdMob's 120dp media policy). A VIDEO ad must play
 * in a MediaView, so for those a media block (>= 120dp) is added above the strip.
 */
@Composable
fun NativeBannerAdView(
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
        placeholder = { NativeBannerPlaceholder() },
        build = ::createNativeBannerView
    )
}

private fun createNativeBannerView(
    context: Context,
    nativeAd: NativeAd,
    colors: NativeTemplateColors
): NativeAdView {
    val adView = templateCard(context, colors, paddingDp = 10, cornerDp = 14, horizontalPaddingDp = 8)
    val root = vertical(context)
    val media = videoMediaOrNull(context, colors, nativeAd, horizontalInsetDp = 52)
    if (media != null) root.addView(media)
    val row = horizontal(context)

    val icon = templateIcon(context, colors, nativeAd, sizeDp = 48, cornerDp = 10, marginEndDp = 10)

    val textColumn = vertical(context, linearParams(0, WRAP, 1f))
    val titleRow = horizontal(context)
    val badge = adBadge(context, colors).apply {
        layoutParams = linearParams(WRAP, WRAP) { marginEnd = context.dp(6) }
    }
    val headline = templateText(context, colors.title, 14f, bold = true).apply {
        layoutParams = linearParams(0, WRAP, 1f)
        text = nativeAd.headline
    }
    titleRow.addView(badge)
    titleRow.addView(headline)
    textColumn.addView(titleRow)

    // Second line: the star rating when there is one, else the advertiser, else the body.
    val rating = starRatingText(context, colors, nativeAd.starRating).apply {
        layoutParams = linearParams(WRAP, WRAP) { topMargin = context.dp(4) }
    }
    val secondary = templateText(context, colors.muted, 12f).apply {
        layoutParams = linearParams(MATCH, WRAP) { topMargin = context.dp(4) }
    }
    textColumn.addView(rating)
    textColumn.addView(secondary)

    val cta = templateCta(
        context, colors, nativeAd.callToAction, fallback = "Open",
        heightDp = 36, cornerDp = 18, textSp = 13f, fullWidth = false
    ).apply {
        layoutParams = linearParams(WRAP, context.dp(36)) { marginStart = context.dp(10) }
    }

    row.addView(icon)
    row.addView(textColumn)
    row.addView(cta)
    root.addView(row)
    adView.addView(root)

    adView.headlineView = headline
    adView.callToActionView = cta
    if (icon.visibility == View.VISIBLE) adView.iconView = icon
    if (rating.visibility == View.VISIBLE) {
        adView.starRatingView = rating
        secondary.visibility = View.GONE
    } else if (!nativeAd.advertiser.isNullOrBlank()) {
        secondary.bindOrHide(nativeAd.advertiser)
        adView.advertiserView = secondary
    } else {
        secondary.bindOrHide(nativeAd.body)
        if (secondary.visibility == View.VISIBLE) adView.bodyView = secondary
    }

    adView.registerNativeAd(nativeAd, media)
    return adView
}
