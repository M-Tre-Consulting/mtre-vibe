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
 * Interactive swipe-back gesture container.
 * Smoothly translates and fades the screen content on horizontal right-swipe,
 * animating fluidly on release and dispatching [onBack].
 */
@Composable
fun SwipeBackContainer(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }

    val draggableState = rememberDraggableState { delta ->
        if (enabled) {
            coroutineScope.launch {
                val newOffset = (offsetX.value + delta).coerceAtLeast(0f)
                offsetX.snapTo(newOffset)
            }
        }
    }

    Box(
        modifier = modifier
            .offset { IntOffset(offsetX.value.roundToInt(), 0) }
            .graphicsLayer {
                alpha = (1f - (offsetX.value / 1200f)).coerceIn(0.5f, 1f)
            }
            .draggable(
                state = draggableState,
                orientation = Orientation.Horizontal,
                enabled = enabled,
                onDragStopped = { velocity ->
                    if (offsetX.value > 250f || velocity > 1000f) {
                        coroutineScope.launch {
                            offsetX.animateTo(
                                targetValue = 1200f,
                                animationSpec = spring(stiffness = Spring.StiffnessMedium)
                            )
                            onBack()
                        }
                    } else {
                        coroutineScope.launch {
                            offsetX.animateTo(
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
