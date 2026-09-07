package com.steamcalc.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ─── Custom Colors ────────────────────────────────────────────────────────

private val SteamBlue = Color(0xFF1565C0)
private val SprayCyan = Color(0xFF0288D1)
private val BurnerOrange = Color(0xFFFF6F00)
private val MetalGray = Color(0xFF616161)
private val SuccessGreen = Color(0xFF1B5E20)
private val WarningRed = Color(0xFFC62828)

// ─── Light Theme ──────────────────────────────────────────────────────────

private val LightColorScheme = lightColorScheme(
    primary = SteamBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1E4FF),
    onPrimaryContainer = Color(0xFF001D36),
    secondary = SprayCyan,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCBE6FF),
    onSecondaryContainer = Color(0xFF001E2E),
    tertiary = BurnerOrange,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDCBE),
    onTertiaryContainer = Color(0xFF2B1600),
    background = Color(0xFFFAFAFA),
    onBackground = Color(0xFF1A1C1E),
    surface = Color.White,
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFDFE2EB),
    onSurfaceVariant = Color(0xFF43474E),
    error = WarningRed,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF73777F),
)

// ─── Theme Composable ─────────────────────────────────────────────────────

@Composable
fun SteamCalcTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography(),
        content = content
    )
}
