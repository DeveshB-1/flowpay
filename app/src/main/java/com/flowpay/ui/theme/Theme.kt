package com.flowpay.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// GPay-inspired blue
val FlowPayBlue = Color(0xFF1A73E8)
val FlowPayBlueDark = Color(0xFF1557B0)
val FlowPayBlueLight = Color(0xFF8AB4F8)
val FlowPayGreen = Color(0xFF34A853)
val FlowPayRed = Color(0xFFEA4335)
val FlowPayYellow = Color(0xFFFBBC04)

private val LightColorScheme = lightColorScheme(
    primary = FlowPayBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD2E3FC),
    onPrimaryContainer = Color(0xFF1A4D8F),
    secondary = Color(0xFF5F6368),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF1F3F4),
    onSecondaryContainer = Color(0xFF3C4043),
    tertiary = Color(0xFF34A853),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD4F5DC),
    onTertiaryContainer = Color(0xFF1B5E20),
    surface = Color.White,
    onSurface = Color(0xFF202124),
    surfaceVariant = Color(0xFFF8F9FA),
    onSurfaceVariant = Color(0xFF5F6368),
    background = Color(0xFFF8F9FA),
    onBackground = Color(0xFF202124),
    error = Color(0xFFD93025),
    onError = Color.White,
    errorContainer = Color(0xFFFCE8E6),
    onErrorContainer = Color(0xFFC5221F),
    outline = Color(0xFFDADCE0),
    outlineVariant = Color(0xFFE8EAED),
    surfaceTint = FlowPayBlue,
)

private val DarkColorScheme = darkColorScheme(
    primary = FlowPayBlueLight,
    onPrimary = Color(0xFF003A75),
    primaryContainer = Color(0xFF1A3A5C),
    onPrimaryContainer = Color(0xFF8AB4F8),
    secondary = Color(0xFFBDC1C6),
    onSecondary = Color(0xFF303134),
    secondaryContainer = Color(0xFF3C4043),
    onSecondaryContainer = Color(0xFFE8EAED),
    tertiary = Color(0xFF81C995),
    onTertiary = Color(0xFF003A19),
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFE8EAED),
    surfaceVariant = Color(0xFF2D2D2D),
    onSurfaceVariant = Color(0xFFBDC1C6),
    background = Color(0xFF121212),
    onBackground = Color(0xFFE8EAED),
    error = Color(0xFFF28B82),
    onError = Color(0xFF601410),
    outline = Color(0xFF5F6368),
    outlineVariant = Color(0xFF3C4043),
    surfaceTint = FlowPayBlueLight,
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
