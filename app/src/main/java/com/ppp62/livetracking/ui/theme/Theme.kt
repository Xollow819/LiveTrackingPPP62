package com.ppp62.livetracking.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import com.ppp62.livetracking.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp

private val InstrumentSans = FontFamily(Font(R.font.instrument_sans))

/** PPPVenza brand palette: deep teal + warm amber on soft neutrals. */
private val VenzaLight = lightColorScheme(
    primary = Color(0xFF0A84FF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEBFF),
    onPrimaryContainer = Color(0xFF142432),
    secondary = Color(0xFF0B756F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD2E9E6),
    tertiary = Color(0xFFC98232),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDDB3),
    background = Color(0xFFF5F7FA),
    onBackground = Color(0xFF142432),
    surface = Color(0xFFF8FAFC),
    onSurface = Color(0xFF142432),
    surfaceVariant = Color(0xFFE5EBF1),
    onSurfaceVariant = Color(0xFF526473),
    error = Color(0xFFBA1A1A),
    outline = Color(0xFF8697A5),
    surfaceTint = Color(0xFF0A84FF)
)
private val VenzaDark = darkColorScheme(
    primary = Color(0xFF79B7FF),
    onPrimary = Color(0xFF142432),
    primaryContainer = Color(0xFF164B80),
    onPrimaryContainer = Color(0xFFDCEBFF),
    secondary = Color(0xFFA9C9EE),
    tertiary = Color(0xFFF0B45C),
    background = Color(0xFF0C141F),
    onBackground = Color(0xFFE6EDF5),
    surface = Color(0xFF142432),
    onSurface = Color(0xFFE6EDF5),
    surfaceVariant = Color(0xFF243647),
    onSurfaceVariant = Color(0xFFB7C8D7),
    outline = Color(0xFF879CAB)
)

@Composable
fun VenzaTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = if (isSystemInDarkTheme()) VenzaDark else VenzaLight,
    typography = Typography(
        displayLarge = TextStyle(fontFamily = InstrumentSans, fontWeight = FontWeight.Bold, fontSize = 48.sp, lineHeight = 54.sp, letterSpacing = (-1.5).sp),
        displayMedium = TextStyle(fontFamily=InstrumentSans,fontSize=42.sp,lineHeight=48.sp),
        displaySmall = TextStyle(fontFamily=InstrumentSans,fontSize=36.sp,lineHeight=42.sp),
        headlineLarge = TextStyle(fontFamily = InstrumentSans, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.6).sp),
        headlineMedium = TextStyle(fontFamily = InstrumentSans, fontWeight = FontWeight.Bold, fontSize = 25.sp, lineHeight = 31.sp),
        headlineSmall = TextStyle(fontFamily=InstrumentSans,fontWeight=FontWeight.SemiBold,fontSize=22.sp,lineHeight=28.sp),
        titleLarge = TextStyle(fontFamily = InstrumentSans, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 25.sp),
        titleMedium = TextStyle(fontFamily=InstrumentSans,fontWeight=FontWeight.SemiBold,fontSize=16.sp,lineHeight=22.sp),
        titleSmall = TextStyle(fontFamily=InstrumentSans,fontWeight=FontWeight.Medium,fontSize=14.sp,lineHeight=20.sp),
        bodyLarge = TextStyle(fontFamily = InstrumentSans, fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontFamily=InstrumentSans,fontSize=14.sp,lineHeight=21.sp),
        bodySmall = TextStyle(fontFamily=InstrumentSans,fontSize=13.sp,lineHeight=19.sp),
        labelLarge = TextStyle(fontFamily=InstrumentSans,fontWeight=FontWeight.SemiBold,fontSize=14.sp,lineHeight=20.sp),
        labelMedium = TextStyle(fontFamily=InstrumentSans,fontWeight=FontWeight.Medium,fontSize=12.sp,lineHeight=18.sp),
        labelSmall = TextStyle(fontFamily=InstrumentSans,fontWeight=FontWeight.Medium,fontSize=11.sp,lineHeight=16.sp)
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
