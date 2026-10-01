package com.ppp62.livetracking.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** PPPVenza brand palette: deep teal + warm amber on soft neutrals. */
private val VenzaLight = lightColorScheme(
    primary = Color(0xFF0B6E5F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB8EBD6),
    onPrimaryContainer = Color(0xFF063B2E),
    secondary = Color(0xFF2F5D8A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD3E4F5),
    tertiary = Color(0xFFB26A00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDDB3),
    background = Color(0xFFF3F7F5),
    onBackground = Color(0xFF161D1B),
    surface = Color(0xFFFDFEFD),
    onSurface = Color(0xFF161D1B),
    surfaceVariant = Color(0xFFDEE7E2),
    onSurfaceVariant = Color(0xFF3F4A46),
    error = Color(0xFFBA1A1A)
)
private val VenzaDark = darkColorScheme(
    primary = Color(0xFF84D8BC),
    onPrimary = Color(0xFF063B2E),
    primaryContainer = Color(0xFF0B5A4B),
    onPrimaryContainer = Color(0xFFB8EBD6),
    secondary = Color(0xFFA9C9EE),
    tertiary = Color(0xFFF0B45C),
    background = Color(0xFF0E1513),
    onBackground = Color(0xFFE2E8E5),
    surface = Color(0xFF131B18),
    onSurface = Color(0xFFE2E8E5),
    surfaceVariant = Color(0xFF2A3531),
    onSurfaceVariant = Color(0xFFBFC9C4)
)

@Composable
fun VenzaTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = if (isSystemInDarkTheme()) VenzaDark else VenzaLight,
    typography = Typography(),
    content = content
)
