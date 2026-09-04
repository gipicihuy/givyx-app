package com.givy.downloader.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// Givy Downloader is dark-only by design (per product spec), regardless of
// system theme — this keeps the brand look consistent everywhere.
private val GivyDarkColorScheme = darkColorScheme(
    primary = GivyPrimary,
    onPrimary = GivyOnPrimary,
    primaryContainer = GivyPrimaryVariant,
    background = GivyBackground,
    onBackground = GivyOnBackground,
    surface = GivySurface,
    onSurface = GivyOnBackground,
    surfaceVariant = GivySurfaceVariant,
    onSurfaceVariant = GivyOnSurfaceMuted,
    error = GivyError,
    outline = GivyOutline
)

@Composable
fun GivyDownloaderTheme(
    // Parameter kept for API familiarity, intentionally ignored: the app is
    // always dark per the design spec.
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = GivyDarkColorScheme,
        typography = GivyTypography,
        content = content
    )
}
