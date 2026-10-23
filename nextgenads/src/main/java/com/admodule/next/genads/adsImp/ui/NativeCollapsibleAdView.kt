package com.admodule.next.genads.adsImp.ui

import android.animation.LayoutTransition
import android.content.Context
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import com.admodule.next.genads.adsImp.AdUnitIds
import com.admodule.next.genads.adsImp.manager.AdsManager
import com.google.android.libraries.ads.mobile.sdk.common.AdChoicesView
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView

/**
 * A native ad that works like Google's collapsible banner, anchored at the BOTTOM of the screen:
 *  - it opens EXPANDED, sliding up over the app content (media, icon, headline, body, full-width
 *    call-to-action);
 *  - the tab on the right of its top edge COLLAPSES it to a slim strip pinned at the bottom (small icon,
 *    "Ad" + headline, inline call-to-action) and EXPANDS it again;
 *  - the screen content only ever reserves the collapsed strip's height, so the expanded ad
 *    overlays the content exactly like a collapsible banner does.
 *
 *     CollapsibleNativeAdScaffold(adsManager) { padding ->
 *         MyScreenContent(Modifier.padding(padding))   // never hidden behind the collapsed strip
 *     }
 *
 * Media while collapsed: IMAGE ads hide it and the ad is re-registered without a MediaView (a
 * registered-but-hidden asset counts as obscured). VIDEO must stay visible in a MediaView of at
 * least 120dp (AdMob policy), so video ads keep a 120dp media block.
 *
 * NOTE: a true Google collapsible banner can't be custom-designed — its creative is served and
 * controlled by Google. This reproduces the experience with a fully customizable native ad.
 *
 * @param startCollapsed open as the collapsed strip (the user can still expand it).
 * @param content the screen, given the bottom padding that keeps it clear of the collapsed ad
 *                and the navigation bar.
 */
@Composable
fun CollapsibleNativeAdScaffold(
    adsManager: AdsManager,
    modifier: Modifier = Modifier,
    adUnitId: String = AdUnitIds.NATIVE,
    isEnabled: Boolean = true,
    startCollapsed: Boolean = false,
    content: @Composable (PaddingValues) -> Unit
) {
    var collapsedHeightPx by remember { mutableIntStateOf(0) }
    LaunchedEffect(isEnabled) { if (!isEnabled) collapsedHeightPx = 0 }
    val density = LocalDensity.current
    val navigationBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(modifier.fillMaxSize()) {
        content(PaddingValues(bottom = with(density) { collapsedHeightPx.toDp() } + navigationBar))

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            NativeCollapsibleAdContent(
                adsManager = adsManager,
                modifier = Modifier.fillMaxWidth(),
                adUnitId = adUnitId,
                isEnabled = isEnabled,
                startCollapsed = startCollapsed,
                showPlaceholder = false, // like a banner: nothing until the ad is ready
                onCollapsedHeightChange = { collapsedHeightPx = it }
            )
            if (collapsedHeightPx > 0) {
                // The ad's white surface continues behind the navigation bar.
                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .background(androidx.compose.ui.graphics.Color.White)
                        .windowInsetsBottomHeight(WindowInsets.navigationBars)
                )
            }
        }
    }
}

/**
 * The collapsible native ad on its own, for when you anchor it yourself (e.g. in a Scaffold's
 * bottomBar). Placed in a layout it takes its full height, so the expanded ad pushes content up
 * instead of overlaying it — use [CollapsibleNativeAdScaffold] for the overlay behaviour.
 */
@Composable
fun NativeCollapsibleAdView(
    adsManager: AdsManager,
    modifier: Modifier = Modifier,
    adUnitId: String = AdUnitIds.NATIVE,
    isEnabled: Boolean = true,
    startCollapsed: Boolean = false
) {
    NativeCollapsibleAdContent(
        adsManager = adsManager,
        modifier = modifier,
        adUnitId = adUnitId,
        isEnabled = isEnabled,
        startCollapsed = startCollapsed,
        showPlaceholder = true,
        onCollapsedHeightChange = {}
    )
}

@Composable
private fun NativeCollapsibleAdContent(
    adsManager: AdsManager,
    modifier: Modifier,
    adUnitId: String,
    isEnabled: Boolean,
    startCollapsed: Boolean,
    showPlaceholder: Boolean,
    onCollapsedHeightChange: (Int) -> Unit
) {
    NativeTemplateHost(
        adsManager = adsManager,
        modifier = modifier,
        adUnitId = adUnitId,
        isEnabled = isEnabled,
        placeholder = {
            if (showPlaceholder) {
                if (startCollapsed) NativeBannerPlaceholder() else NativeAdPlaceholder(isMedium = true)
            }
        },
        build = { context, nativeAd, colors ->
            createCollapsibleNativeView(context, nativeAd, colors, startCollapsed, onCollapsedHeightChange)
        }
    )
}

private fun createCollapsibleNativeView(
    context: Context,
    nativeAd: NativeAd,
    colors: NativeTemplateColors,
    startCollapsed: Boolean,
    onCollapsedHeightChange: (Int) -> Unit
): NativeAdView {
    val tabHeight = context.dp(26)
    val cornerPx = context.dp(16).toFloat()
    val tabCornerPx = context.dp(13).toFloat()

    // Transparent root: only the tabs and the card are drawn, so the app shows through beside them.
    val adView = NativeAdView(context).apply {
        layoutParams = FrameLayout.LayoutParams(MATCH, WRAP)
        clipChildren = false
        clipToPadding = false
    }
    val outer = vertical(context).apply {
        clipChildren = false
        clipToPadding = false
    }

    // 1) Band on the top edge: the collapse/expand tab on the right, AdChoices on the left. They
    //    sit apart so neither can ever cover the other (AdMob forbids obscuring AdChoices).
    val band = FrameLayout(context).apply {
        layoutParams = linearParams(MATCH, tabHeight)
        clipChildren = false
    }
    val handle = TextView(context).apply {
        setTextColor(colors.primary)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        includeFontPadding = false
        background = topRoundedBackground(colors.card, tabCornerPx)
        elevation = context.dp(6).toFloat()
        outlineProvider = topRoundedOutline(tabCornerPx)
        layoutParams = FrameLayout.LayoutParams(context.dp(72), tabHeight, Gravity.END or Gravity.BOTTOM).apply {
            marginEnd = context.dp(12)
        }
        isClickable = true
        isFocusable = true
    }
    // No background of its own: when the ad carries no AdChoices icon it stays invisible instead
    // of leaving an empty white sliver.
    val adChoicesTab = FrameLayout(context).apply {
        layoutParams = FrameLayout.LayoutParams(WRAP, tabHeight, Gravity.START or Gravity.BOTTOM).apply {
            marginStart = context.dp(12)
        }
    }
    val adChoices = AdChoicesView(context).apply {
        layoutParams = FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER)
    }
    adChoicesTab.addView(adChoices)
    band.addView(handle)
    band.addView(adChoicesTab)
    outer.addView(band)

    // 2) The card: white, top corners rounded, square at the bottom edge of the screen, with a
    //    shadow cast upwards over the content. Drawn after the tabs, so it hides their lower shadow.
    val card = vertical(context).apply {
        background = topRoundedBackground(colors.card, cornerPx)
        elevation = context.dp(6).toFloat()
        outlineProvider = topRoundedOutline(cornerPx)
        setPadding(context.dp(CARD_HORIZONTAL_PADDING_DP), context.dp(12), context.dp(CARD_HORIZONTAL_PADDING_DP), context.dp(10))
    }
    outer.addView(card)

    // Media.
    val hasVideo = nativeAd.mediaContent.hasVideoContent
    val minMediaHeight = context.dp(MIN_MEDIA_DP)
    val expandedMediaHeight = mediaHeightFor(context, nativeAd, horizontalInsetDp = 20, maxDp = 160)
    val mediaBottomMargin = context.dp(10)
    val media = templateMedia(context, colors, MATCH, expandedMediaHeight).apply {
        layoutParams = linearParams(MATCH, expandedMediaHeight) { bottomMargin = mediaBottomMargin }
    }
    card.addView(media)

    // Icon + "Ad" badge / headline / body + (collapsed only) the inline call-to-action.
    val row = horizontal(context)
    val icon = templateIcon(context, colors, nativeAd, sizeDp = 48)
    val textColumn = vertical(context, linearParams(0, WRAP, 1f))
    val titleRow = horizontal(context)
    val headline = templateText(context, colors.title, 15f, bold = true).apply {
        layoutParams = linearParams(0, WRAP, 1f)
        text = nativeAd.headline
    }
    titleRow.addView(adBadge(context, colors).apply {
        layoutParams = linearParams(WRAP, WRAP) { marginEnd = context.dp(6) }
    })
    titleRow.addView(headline)
    val body = templateText(context, colors.muted, 13f, maxLines = 2).apply {
        layoutParams = linearParams(MATCH, WRAP) { topMargin = context.dp(3) }
        bindOrHide(nativeAd.body)
    }
    textColumn.addView(titleRow)
    textColumn.addView(body)
    val inlineCtaSlot = horizontal(context, linearParams(WRAP, WRAP))
    row.addView(icon)
    row.addView(textColumn)
    row.addView(inlineCtaSlot)
    card.addView(row)

    // Full-width call-to-action slot (expanded). The ONE button moves between the two slots.
    val fullCtaSlot = vertical(context)
    card.addView(fullCtaSlot)
    val cta = templateCta(context, colors, nativeAd.callToAction, fallback = "Install")

    adView.addView(outer)

    adView.headlineView = headline
    adView.callToActionView = cta
    adView.adChoicesView = adChoices
    if (icon.visibility == View.VISIBLE) adView.iconView = icon
    if (body.visibility == View.VISIBLE) adView.bodyView = body

    fun applyState(expanded: Boolean) {
        // Call-to-action: full width below (expanded) or a compact pill on the right (collapsed).
        (cta.parent as? ViewGroup)?.removeView(cta)
        if (expanded) {
            cta.layoutParams = linearParams(MATCH, context.dp(44)) { topMargin = context.dp(12) }
            cta.background = roundedBackground(colors.primary, context.dp(12).toFloat())
            fullCtaSlot.addView(cta)
        } else {
            cta.layoutParams = linearParams(WRAP, context.dp(36)) { marginStart = context.dp(10) }
            cta.background = roundedBackground(colors.primary, context.dp(18).toFloat())
            inlineCtaSlot.addView(cta)
        }
        fullCtaSlot.visibility = if (expanded) View.VISIBLE else View.GONE
        inlineCtaSlot.visibility = if (expanded) View.GONE else View.VISIBLE

        // Icon: 48dp → 36dp; body: two lines → one.
        val iconSize = context.dp(if (expanded) 48 else 36)
        icon.layoutParams = (icon.layoutParams as LinearLayout.LayoutParams).apply {
            width = iconSize
            height = iconSize
            marginEnd = context.dp(10)
        }
        icon.background = roundedBackground(colors.placeholder, context.dp(if (expanded) 12 else 9).toFloat())
        body.maxLines = if (expanded) 2 else 1

        // Media: hidden for image ads, policy-minimum for video ads.
        if (hasVideo) {
            media.layoutParams = (media.layoutParams as LinearLayout.LayoutParams).apply {
                height = if (expanded) expandedMediaHeight else minMediaHeight
            }
            adView.registerNativeAd(nativeAd, media)
        } else {
            media.visibility = if (expanded) View.VISIBLE else View.GONE
            // Only register the MediaView while it is actually on screen.
            adView.registerNativeAd(nativeAd, if (expanded) media else null)
        }

        handle.text = if (expanded) "▾" else "▴"
        handle.contentDescription = if (expanded) "Collapse ad" else "Expand ad"
    }

    var expanded = !startCollapsed
    applyState(expanded)
    // Installed after the initial state so only user toggles animate (media in/out + resize).
    card.layoutTransition = LayoutTransition().apply { enableTransitionType(LayoutTransition.CHANGING) }
    handle.setOnClickListener {
        expanded = !expanded
        applyState(expanded)
    }

    // Height of the COLLAPSED strip (tabs + card padding + content row [+ the 120dp video block]).
    // The scaffold reserves exactly this for the screen content, so the expanded ad overlays it.
    var reportedHeight = -1
    adView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        val collapsedHeight = band.height + card.paddingTop + card.paddingBottom + row.height +
            (if (hasVideo) minMediaHeight + mediaBottomMargin else 0)
        if (collapsedHeight != reportedHeight) {
            reportedHeight = collapsedHeight
            onCollapsedHeightChange(collapsedHeight)
        }
    }

    // Slide up from the bottom edge when the ad first appears, like a collapsible banner.
    adView.alpha = 0f
    adView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {
            v.removeOnAttachStateChangeListener(this)
            v.post {
                v.translationY = v.height.toFloat()
                v.alpha = 1f
                v.animate()
                    .translationY(0f)
                    .setDuration(350)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
        }

        override fun onViewDetachedFromWindow(v: View) = Unit
    })

    return adView
}

/** White surface with only the TOP corners rounded (the bottom sits on the screen edge). */
private fun topRoundedBackground(color: Int, radiusPx: Float) = GradientDrawable().apply {
    setColor(color)
    cornerRadii = floatArrayOf(radiusPx, radiusPx, radiusPx, radiusPx, 0f, 0f, 0f, 0f)
}

/**
 * Shadow outline matching [topRoundedBackground]: a rounded rect that extends past the bottom
 * edge, so the top corners are round and the bottom ones square.
 */
private fun topRoundedOutline(radiusPx: Float) = object : ViewOutlineProvider() {
    override fun getOutline(view: View, outline: Outline) {
        outline.setRoundRect(0, 0, view.width, view.height + radiusPx.toInt(), radiusPx)
    }
}
