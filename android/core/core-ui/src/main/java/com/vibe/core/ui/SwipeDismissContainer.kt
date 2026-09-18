package com.vibe.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Interactive vertical swipe-to-dismiss gesture container.
 * Smoothly translates and fades the screen content on downward swipe,
 * animating fluidly on release and dispatching [onDismiss].
 */
@Composable
fun SwipeDismissContainer(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val offsetY = remember { Animatable(0f) }

    val draggableState = rememberDraggableState { delta ->
        if (enabled) {
            coroutineScope.launch {
                val newOffset = (offsetY.value + delta).coerceAtLeast(0f)
                offsetY.snapTo(newOffset)
            }
        }
    }

    Box(
        modifier = modifier
            .offset { IntOffset(0, offsetY.value.roundToInt()) }
            .graphicsLayer {
                alpha = (1f - (offsetY.value / 1600f)).coerceIn(0.4f, 1f)
            }
            .draggable(
                state = draggableState,
                orientation = Orientation.Vertical,
                enabled = enabled,
                onDragStopped = { velocity ->
                    if (offsetY.value > 220f || velocity > 800f) {
                        coroutineScope.launch {
                            offsetY.animateTo(
                                targetValue = 1600f,
                                animationSpec = spring(stiffness = Spring.StiffnessMedium)
                            )
                            onDismiss()
                            offsetY.snapTo(0f)
                        }
                    } else {
                        coroutineScope.launch {
                            offsetY.animateTo(
                                targetValue = 0f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            )
                        }
                    }
                }
            )
    ) {
        content()
    }
}
