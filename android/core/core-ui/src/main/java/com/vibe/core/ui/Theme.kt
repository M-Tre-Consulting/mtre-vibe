package com.vibe.core.ui

import android.graphics.Bitmap
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import com.vibe.core.model.ThemeMode

private val DarkColors = darkColorScheme(
    primary = Color(0xFF1DB954),
    onPrimary = Color.Black,
    secondary = Color(0xFF1ED760),
    background = Color(0xFF121212),
    surface = Color(0xFF181818),
    onSurface = Color.White
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF1DB954),
    onPrimary = Color.White,
    secondary = Color(0xFF1AA34A),
    background = Color(0xFFFAFAFA),
    surface = Color(0xFFFFFFFF),
    onSurface = Color.Black
)

@Composable
fun VibeTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    tintColor: Color? = null,
    isAlbumArtTintEnabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val baseColors = if (isDark) DarkColors else LightColors
    val effectiveColors: ColorScheme = if (isAlbumArtTintEnabled && tintColor != null) {
        baseColors.copy(
            primary = tintColor,
            surface = if (isDark) tintColor.copy(alpha = 0.15f) else tintColor.copy(alpha = 0.08f)
        )
    } else {
        baseColors
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
