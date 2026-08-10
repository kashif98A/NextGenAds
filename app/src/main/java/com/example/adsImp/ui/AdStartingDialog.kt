package com.example.adsImp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay

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
    LaunchedEffect(Unit) {
        val pollMillis = 150L
        var waited = 0L

        // Fast path: if the ad is already preloaded/cached, show it immediately — no artificial wait.
        // Only when we actually have to wait for a fresh load do we keep the overlay up (with a brief
        // minimum so it doesn't flash), capped at maxWaitMillis so it can never hang.
        if (!isAdReady()) {
            delay(minVisibleMillis)
            waited += minVisibleMillis

            while (!isAdReady() && waited < maxWaitMillis) {
                delay(pollMillis)
                waited += pollMillis
            }
        }

        onReadyToShow()
    }

    Dialog(
        onDismissRequest = { /* non-cancelable while waiting */ },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(20.dp))
                Text(
                    text = "$message…",
                    color = Color(0xFF1C1B1F),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Please wait",
                    color = Color(0xFF1C1B1F).copy(alpha = 0.6f),
                    fontSize = 12.sp
                )
            }
        }
    }
}
