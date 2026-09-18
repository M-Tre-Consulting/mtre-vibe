package com.vibe.core.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Animated 4-bar equalizer indicator displayed for the currently playing track.
 * Oscillates smoothly when [isPlaying] is true, or rests at calm static heights when paused.
 */
@Composable
fun AnimatedEqualizerBars(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    isPlaying: Boolean = true,
    width: Dp = 15.dp,
    height: Dp = 14.dp
) {
    val transition = rememberInfiniteTransition(label = "equalizerBars")

    val bar1 by if (isPlaying) {
        transition.animateFloat(
            initialValue = 0.25f,
            targetValue = 0.95f,
            animationSpec = infiniteRepeatable(
                animation = tween(450, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar1"
        )
    } else {
        remember { mutableFloatStateOf(0.4f) }
    }

    val bar2 by if (isPlaying) {
        transition.animateFloat(
            initialValue = 0.95f,
            targetValue = 0.3f,
            animationSpec = infiniteRepeatable(
                animation = tween(360, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar2"
        )
    } else {
        remember { mutableFloatStateOf(0.75f) }
    }

    val bar3 by if (isPlaying) {
        transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(520, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar3"
        )
    } else {
        remember { mutableFloatStateOf(0.55f) }
    }

    val bar4 by if (isPlaying) {
        transition.animateFloat(
            initialValue = 0.75f,
            targetValue = 0.2f,
            animationSpec = infiniteRepeatable(
                animation = tween(410, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar4"
        )
    } else {
        remember { mutableFloatStateOf(0.35f) }
    }

    Row(
        modifier = modifier.size(width = width, height = height),
        horizontalArrangement = Arrangement.spacedBy(1.5.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        listOf(bar1, bar2, bar3, bar4).forEach { fraction ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(fraction.coerceIn(0.15f, 1f))
                    .clip(RoundedCornerShape(1.dp))
                    .background(color)
            )
        }
    }
}
