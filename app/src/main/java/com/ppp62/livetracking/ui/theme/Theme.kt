package com.ppp62.livetracking.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(primary = Color(0xFF0B6E5F), onPrimary = Color.White, primaryContainer = Color(0xFF9EF2DA), secondary = Color(0xFF35618A), tertiary = Color(0xFF9A4F00), background = Color(0xFFF5F8F7), surface = Color(0xFFFBFDFB), error = Color(0xFFBA1A1A))
private val Dark = darkColorScheme(primary = Color(0xFF82D5BE), secondary = Color(0xFFA3C9F4), tertiary = Color(0xFFFFB875))
@Composable fun PPP62Theme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, typography = Typography(), content = content)
