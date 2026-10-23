package com.admodule.next.genads.adsImp.ui

import android.view.ViewGroup
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import kotlinx.coroutines.delay

// Brand palette (kept in sync with the demo app's design language).
private val BrandBlue = Color(0xFF005FB0)
private val BrandBlueSoft = Color(0xFFD1E1FF)
private val AppBackground = Color(0xFFF7F9FF)
private val CardBorder = Color(0xFFE1E2EC)
private val TextPrimary = Color(0xFF1C1B1F)
private val TextMuted = Color(0xFF44474E)

/**
 * A brief full-screen "Ad starting…" overlay shown BEFORE a full-screen ad (interstitial,
 * rewarded, app-open, etc.) is displayed. Instead of a fixed countdown, it keeps the overlay up
 * until the ad actually reports ready — [isAdReady] returning true — and only THEN invokes
 * [onReadyToShow], which is where the caller triggers the manager's showAd(...).
 *
 * To guarantee it can never hang (e.g. the ad never loads on a bad network), the wait is capped at
 * [maxWaitMillis]; once that ceiling is hit it proceeds anyway, and showAd(...) handles the
 * not-ready case by re-loading and dismissing. A short [minVisibleMillis] floor keeps the overlay
 * from flashing when the ad is already cached.
 *
 * This is UI-only and manager-agnostic, so it is reusable for any full-screen ad type. It is
 * non-cancelable while waiting (back press / outside tap are ignored).
 *
 * Typical usage:
 *
 *     var showLoader by remember { mutableStateOf(false) }
 *     if (showLoader) {
 *         AdStartingDialog(
 *             isAdReady = { interstitialManager.isAdAvailable() },
 *             onReadyToShow = {
 *                 showLoader = false
 *                 interstitialManager.showAd(activity) { /* dismissed */ }
 *             }
 *         )
 *     }
 *     Button(onClick = { interstitialManager.loadAd(); showLoader = true }) { Text("Continue") }
 */
@Composable
fun AdStartingDialog(
    isAdReady: () -> Boolean,
    onReadyToShow: () -> Unit,
    maxWaitMillis: Long = 8000L,
    minVisibleMillis: Long = 600L,
    message: String = "Ad starting"
) {
    // The effect outlives recompositions; always call the LATEST lambdas, not the first ones.
    val currentIsAdReady by rememberUpdatedState(isAdReady)
    val currentOnReadyToShow by rememberUpdatedState(onReadyToShow)

    LaunchedEffect(Unit) {
        val pollMillis = 150L
        var waited = 0L

        // Fast path: if the ad is already preloaded/cached, show it immediately — no artificial wait.
        // Only when we actually have to wait for a fresh load do we keep the overlay up (with a brief
        // minimum so it doesn't flash), capped at maxWaitMillis so it can never hang.
        if (!currentIsAdReady()) {
            delay(minVisibleMillis)
            waited += minVisibleMillis

            while (!currentIsAdReady() && waited < maxWaitMillis) {
                delay(pollMillis)
                waited += pollMillis
            }
        }

        currentOnReadyToShow()
    }

    Dialog(
        onDismissRequest = { /* non-cancelable while waiting */ },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        FullScreenDialogWindow()
        AdLoadingContent(title = message, subtitle = "Getting your ad ready")
    }
}

/**
 * A minimal, purely caller-driven full-screen loading overlay. Unlike [AdStartingDialog], it has no
 * polling or auto-proceed logic — it stays visible exactly as long as the caller renders it. Used by
 * the ON-DEMAND interstitial path, where the manager loads a fresh ad and drives visibility via an
 * onLoading(Boolean) callback. Non-cancelable (back press / outside tap ignored).
 */
@Composable
fun AdLoadingDialog(message: String = "Loading ad") {
    Dialog(
        onDismissRequest = { /* non-cancelable while loading */ },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        FullScreenDialogWindow()
        AdLoadingContent(title = message, subtitle = "Fetching a fresh ad")
    }
}

/**
 * Forces the hosting dialog window to cover the ENTIRE screen with no scrim, so the overlay reads as
 * a true full-screen takeover (like the interstitial it precedes) rather than a centered, dimmed card.
 */
@Composable
private fun FullScreenDialogWindow() {
    val view = LocalView.current
    SideEffect {
        (view.parent as? DialogWindowProvider)?.window?.apply {
            setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setDimAmount(0f)
        }
    }
}

/**
 * Shared, on-brand loading visual for both interstitial loaders: a soft full-screen backdrop with a
 * centered rounded card — a "SPONSORED" pill, a branded spinner, the [title]/[subtitle] copy, and a
 * slim indeterminate progress bar — so the wait feels intentional and matches the app's UI.
 */
@Composable
private fun AdLoadingContent(title: String, subtitle: String) {
    // Subtle pulse so the card feels alive even on a fast (near-instant) cached show.
    val transition = rememberInfiniteTransition(label = "adLoadingPulse")
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, CardBorder),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 44.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // "SPONSORED" pill — signals an ad is on its way (AdMob UX best practice).
                Box(
                    modifier = Modifier
                        .background(BrandBlueSoft, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "SPONSORED",
                        color = BrandBlue,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                }

                Spacer(Modifier.height(24.dp))

                // Branded spinner sitting inside a soft tinted disc.
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .alpha(pulseAlpha)
                        .clip(RoundedCornerShape(36.dp))
                        .background(BrandBlueSoft.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = BrandBlue,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(44.dp)
                    )
                }

                Spacer(Modifier.height(22.dp))

                Text(
                    text = "$title…",
                    color = TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = subtitle,
                    color = TextMuted,
                    fontSize = 12.sp
                )

                Spacer(Modifier.height(24.dp))

                // Slim indeterminate track reinforces "working…" without a misleading fake percentage.
                LinearProgressIndicator(
                    color = BrandBlue,
                    trackColor = BrandBlueSoft,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                )
            }
        }
    }
}
