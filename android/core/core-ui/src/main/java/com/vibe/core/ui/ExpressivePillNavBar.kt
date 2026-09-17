package com.vibe.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ripple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class ExpressiveNavItem(
    val id: String,
    val label: String,
    val icon: ImageVector
)

/**
 * Material 3 Expressive Floating Pill Navigation Bar.
 * Encapsulated inside a floating capsule with smooth spring transitions
 * and pill indicators matching Google's latest Expressive design language.
 */
@Composable
fun ExpressivePillNavBar(
    items: List<ExpressiveNavItem>,
    selectedItemId: String,
    onItemSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 8.dp,
            shadowElevation = 10.dp,
            modifier = Modifier
                .wrapContentWidth()
                .height(64.dp)
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { item ->
                    val isSelected = item.id == selectedItemId
                    val interactionSource = remember { MutableInteractionSource() }

                    val bgColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "pillBgColor"
                    )

                    val contentColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "pillContentColor"
                    )

                    Row(
                        modifier = Modifier
                            .height(48.dp)
                            .clip(CircleShape)
                            .background(bgColor)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = ripple(bounded = true, radius = 28.dp)
                            ) {
                                onItemSelected(item.id)
                            }
                            .padding(horizontal = if (isSelected) 16.dp else 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            tint = contentColor,
                            modifier = Modifier.size(24.dp)
                        )
                        AnimatedVisibility(
                            visible = isSelected,
                            enter = fadeIn() + expandHorizontally(),
                            exit = fadeOut() + shrinkHorizontally()
                        ) {
                            Row {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = item.label,
                                    color = contentColor,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
