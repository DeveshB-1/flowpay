package com.flowpay.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1A73E8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD2E3FC),
    onPrimaryContainer = Color(0xFF1A73E8),
    secondary = Color(0xFF5F6368),
    secondaryContainer = Color(0xFFF1F3F4),
    surface = Color.White,
    surfaceVariant = Color(0xFFF8F9FA),
    background = Color(0xFFFAFAFA),
    error = Color(0xFFD93025),
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    onPrimary = Color(0xFF003A75),
    primaryContainer = Color(0xFF1A3A5C),
    onPrimaryContainer = Color(0xFF8AB4F8),
    secondary = Color(0xFFBDC1C6),
    secondaryContainer = Color(0xFF3C4043),
    surface = Color(0xFF1E1E1E),
    surfaceVariant = Color(0xFF2D2D2D),
    background = Color(0xFF121212),
    error = Color(0xFFF28B82),
)

@Composable
fun FlowPayTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
