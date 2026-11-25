package ru.gigapisar.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Tonal palette built from the site green. Every role is set, so no stock Material
 * purple leaks in. Light or dark follows the phone.
 */
/*
 * The palette of the phone's own settings: a grey page, near-white cards, blue for what is on.
 */
private val LightColors =
    lightColorScheme(
        primary = Color(0xFF3D7CF7),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFDCE8FE),
        onPrimaryContainer = Color(0xFF0B2A66),
        secondary = Color(0xFF5A6072),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFE3E6EE),
        onSecondaryContainer = Color(0xFF181B24),
        tertiary = Color(0xFF1FA3D6),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFCFEAFB),
        onTertiaryContainer = Color(0xFF062C40),
        background = Color(0xFFF2F2F4),
        onBackground = Color(0xFF141416),
        surface = Color(0xFFFCFCFE),
        onSurface = Color(0xFF141416),
        surfaceVariant = Color(0xFFE4E4E8),
        onSurfaceVariant = Color(0xFF6F7178),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF7F7F9),
        surfaceContainer = Color(0xFFF2F2F4),
        surfaceContainerHigh = Color(0xFFECECEF),
        surfaceContainerHighest = Color(0xFFE4E4E8),
        outline = Color(0xFFA9A9AF),
        outlineVariant = Color(0xFFE4E4E8),
    )

private val DarkColors =
    darkColorScheme(
        primary = Color(0xFF4C8DFF),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFF1B2F55),
        onPrimaryContainer = Color(0xFFD8E4FF),
        secondary = Color(0xFFB4B9C8),
        onSecondary = Color(0xFF1E222C),
        secondaryContainer = Color(0xFF2B2F3A),
        onSecondaryContainer = Color(0xFFDDE1EC),
        tertiary = Color(0xFF63C6EA),
        onTertiary = Color(0xFF00344A),
        tertiaryContainer = Color(0xFF0E4A63),
        onTertiaryContainer = Color(0xFFCFEAFB),
        background = Color(0xFF000000),
        onBackground = Color(0xFFE6E6EA),
        surface = Color(0xFF17171A),
        onSurface = Color(0xFFE6E6EA),
        surfaceVariant = Color(0xFF2A2A2E),
        onSurfaceVariant = Color(0xFF9A9AA2),
        surfaceContainerLowest = Color(0xFF000000),
        surfaceContainerLow = Color(0xFF121214),
        surfaceContainer = Color(0xFF17171A),
        surfaceContainerHigh = Color(0xFF1F1F23),
        surfaceContainerHighest = Color(0xFF2A2A2E),
        outline = Color(0xFF5C5C64),
        outlineVariant = Color(0xFF2A2A2E),
    )

@Composable
internal fun GigaPisarTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
