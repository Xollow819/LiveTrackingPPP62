package com.ppp62.livetracking.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp

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
    background = Color(0xFFF4F7F4),
    onBackground = Color(0xFF161D1B),
    surface = Color(0xFFFFFEFA),
    onSurface = Color(0xFF161D1B),
    surfaceVariant = Color(0xFFE5ECE7),
    onSurfaceVariant = Color(0xFF3F4A46),
    error = Color(0xFFBA1A1A),
    outline = Color(0xFF87958E),
    surfaceTint = Color(0xFF0B6E5F)
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
    surfaceVariant = Color(0xFF263630),
    onSurfaceVariant = Color(0xFFBECBC4),
    outline = Color(0xFF899A91)
)

@Composable
fun VenzaTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = if (isSystemInDarkTheme()) VenzaDark else VenzaLight,
    typography = Typography(
        displayLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 48.sp, lineHeight = 54.sp, letterSpacing = (-1.5).sp),
        headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.6).sp),
        headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 25.sp, lineHeight = 31.sp),
        titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 25.sp),
        bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 24.sp)
    ),
    shapes = Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(24.dp),
        extraLarge = RoundedCornerShape(32.dp)
    ),
    content = content
)
