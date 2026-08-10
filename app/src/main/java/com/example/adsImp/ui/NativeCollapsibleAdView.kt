package com.example.adsImp.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.Log
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.nativead.MediaView
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoader
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoaderCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdRequest
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView
import com.example.adsImp.AdUnitIds
import com.example.adsImp.manager.AdsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "NativeCollapsibleAdView"

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
    // Gate: show nothing unless this unit is enabled (remote-config style flag) AND online.
    val isOnline by adsManager.networkMonitor.isOnline.collectAsStateWithLifecycle(initialValue = true)
    if (!isEnabled || !isOnline) return

    var nativeAdState by remember { mutableStateOf<NativeAd?>(null) }
    var isLoadingAd by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }

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
                            Log.d(TAG, "Collapsible-style native ad loaded.")
                        }

                        override fun onAdFailedToLoad(adError: LoadAdError) {
                            isLoadingAd = false
                            hasError = true
                            Log.e(TAG, "Collapsible-style native ad failed to load: ${adError.message}")
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
                isMedium = true
            )
        } else if (!hasError && nativeAdState != null) {
            val loadedAd = nativeAdState!!

            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                factory = { ctx -> createCollapsibleNativeView(ctx, loadedAd) },
                update = {},
                onRelease = {
                    Log.d(TAG, "Releasing Collapsible NativeAd assets.")
                    nativeAdState?.destroy()
                }
            )
        } else {
            Spacer(modifier = Modifier.height(0.dp))
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

    // 0) Always-visible, tappable header BAR that toggles the media.
    //    Kept OUTSIDE the media so it can never be hidden — neither by collapsing the media nor by
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
        text = "Sponsored — tap to hide"
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
            170.dpToPx(context)
        )
        clipToOutline = true
        background = GradientDrawable().apply {
            setColor(Color.parseColor("#11000000"))
            cornerRadius = 14.dpToPx(context).toFloat()
        }
    }
    mediaContainer.addView(mediaView)
    root.addView(mediaContainer)

    // Tapping anywhere on the header bar collapses/expands the whole media block (incl. its margin).
    headerRow.setOnClickListener {
        if (mediaContainer.visibility == View.VISIBLE) {
            mediaContainer.visibility = View.GONE
            chevron.rotation = 180f
            headerLabel.text = "Sponsored — tap to show"
        } else {
            mediaContainer.visibility = View.VISIBLE
            chevron.rotation = 0f
            headerLabel.text = "Sponsored — tap to hide"
        }
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

private fun Int.dpToPx(context: Context): Int {
    val density = context.resources.displayMetrics.density
    return (this * density).toInt()
}
