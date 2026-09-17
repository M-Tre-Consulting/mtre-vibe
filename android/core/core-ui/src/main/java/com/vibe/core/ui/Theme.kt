package com.vibe.core.ui

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import com.vibe.core.model.ThemeMode

// Material 3 Optimized Dark Color Scheme (High contrast, refined container hierarchy)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF1DB954),
    onPrimary = Color(0xFF003915),
    primaryContainer = Color(0xFF005322),
    onPrimaryContainer = Color(0xFF70F990),
    secondary = Color(0xFF52DB7A),
    onSecondary = Color(0xFF003915),
    secondaryContainer = Color(0xFF005323),
    onSecondaryContainer = Color(0xFF70F990),
    background = Color(0xFF0C0F0D),
    onBackground = Color(0xFFE1E3DF),
    surface = Color(0xFF111412),
    onSurface = Color(0xFFE1E3DF),
    surfaceVariant = Color(0xFF1E2420),
    onSurfaceVariant = Color(0xFFC2C8C0),
    surfaceContainerLowest = Color(0xFF070A08),
    surfaceContainerLow = Color(0xFF0E1210),
    surfaceContainer = Color(0xFF141916),
    surfaceContainerHigh = Color(0xFF1A211D),
    surfaceContainerHighest = Color(0xFF222B25),
    outline = Color(0xFF8C938B),
    outlineVariant = Color(0xFF424941)
)

// Material 3 Optimized Light Color Scheme
private val LightColors = lightColorScheme(
    primary = Color(0xFF006D31),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF70F990),
    onPrimaryContainer = Color(0xFF00210A),
    secondary = Color(0xFF006D32),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF70F990),
    onSecondaryContainer = Color(0xFF00210A),
    background = Color(0xFFFCFDF7),
    onBackground = Color(0xFF1A1C19),
    surface = Color(0xFFFCFDF7),
    onSurface = Color(0xFF1A1C19),
    surfaceVariant = Color(0xFFDDE5DB),
    onSurfaceVariant = Color(0xFF424941),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6F8F1),
    surfaceContainer = Color(0xFFF0F2EB),
    surfaceContainerHigh = Color(0xFFEAEDE5),
    surfaceContainerHighest = Color(0xFFE5E7DF),
    outline = Color(0xFF727970),
    outlineVariant = Color(0xFFC2C8C0)
)

/**
 * Native Vibe Theme with full Material You dynamic system color support.
 * Adapts to system wallpaper dynamic palette on Android 12+ (API 31+)
 * with fallback to optimized Material 3 expressive palettes.
 */
@Composable
fun VibeTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    useSystemDynamicColors: Boolean = true,
    tintColor: Color? = null,
    isAlbumArtTintEnabled: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val isDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val dynamicColorAvailable = useSystemDynamicColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val systemColorScheme = when {
        dynamicColorAvailable && isDark -> dynamicDarkColorScheme(context)
        dynamicColorAvailable && !isDark -> dynamicLightColorScheme(context)
        isDark -> DarkColors
        else -> LightColors
    }

    val effectiveColors: ColorScheme = if (isAlbumArtTintEnabled && tintColor != null) {
        systemColorScheme.copy(
            primary = tintColor,
            primaryContainer = tintColor.copy(alpha = 0.25f),
            onPrimaryContainer = if (isDark) Color.White else Color.Black
        )
    } else {
        systemColorScheme
    }

    MaterialTheme(
        colorScheme = effectiveColors,
        content = content
    )
}

object DynamicPaletteExtractor {
    fun extractDominantColor(bitmap: Bitmap, fallback: Int = 0xFF1DB954.toInt()): Int {
        val palette = Palette.from(bitmap).generate()
        return palette.getDominantColor(palette.getVibrantColor(fallback))
    }
}
