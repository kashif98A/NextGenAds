package com.example.adsImp.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A single animated shimmer brush shared by every skeleton "bone" in a placeholder, so they all
 * sweep in sync. A bright band travels left-to-right across a muted base color.
 */
@Composable
private fun rememberShimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translate by transition.animateFloat(
        initialValue = -250f,
        targetValue = 1250f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )
    val base = MaterialTheme.colorScheme.surfaceVariant
    val shimmerColors = listOf(
        base.copy(alpha = 0.55f),
        base.copy(alpha = 0.20f),
        base.copy(alpha = 0.55f)
    )
    return Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translate - 250f, 0f),
        end = Offset(translate, 0f)
    )
}

/** A rounded shimmering rectangle — one skeleton element. */
@Composable
private fun Bone(brush: Brush, modifier: Modifier, corner: Dp = 8.dp) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .clip(RoundedCornerShape(corner))
            .background(brush)
    )
}

/**
 * Loading skeleton for banner ads: a clean shimmering bar. Height is supplied by the caller
 * (e.g. .height(50.dp) for standard, .height(60.dp) for adaptive/collapsible).
 */
@Composable
fun BannerAdPlaceholder(modifier: Modifier = Modifier) {
    val brush = rememberShimmerBrush()
    Bone(
        brush = brush,
        modifier = modifier.fillMaxWidth(),
        corner = 10.dp
    )
}

/**
 * Loading skeleton for native ads that mirrors the real layout.
 *  - small  (isMedium = false): icon ▢ + two text lines + a small CTA pill on the right.
 *  - medium (isMedium = true):  top media block, icon + headline, two body lines, full-width CTA.
 */
@Composable
fun NativeAdPlaceholder(
    modifier: Modifier = Modifier,
    isMedium: Boolean = false
) {
    val brush = rememberShimmerBrush()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (isMedium) {
            // Top media block
            Bone(brush, Modifier.fillMaxWidth().height(150.dp), corner = 14.dp)
        }

        // Icon + headline/body row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Bone(brush, Modifier.size(if (isMedium) 44.dp else 56.dp), corner = 12.dp)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Bone(brush, Modifier.fillMaxWidth(0.75f).height(12.dp), corner = 6.dp)
                Bone(brush, Modifier.fillMaxWidth(0.5f).height(10.dp), corner = 6.dp)
            }
        }

        if (isMedium) {
            // Two body lines (medium only)
            Bone(brush, Modifier.fillMaxWidth().height(10.dp), corner = 6.dp)
            Bone(brush, Modifier.fillMaxWidth(0.9f).height(10.dp), corner = 6.dp)
        }

        // Full-width CTA bar — matches the real layout for both small and medium.
        Bone(brush, Modifier.fillMaxWidth().height(if (isMedium) 42.dp else 38.dp), corner = 22.dp)
    }
}
