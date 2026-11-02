package com.rednote.lite.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val RedPrimary = Color(0xFFE8434A)
private val RedPrimaryDark = Color(0xFFFF7A7F)
private val LightBackground = Color(0xFFFAFAFA)
private val DarkBackground = Color(0xFF121212)

private val LightColors = lightColorScheme(
    primary = RedPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD8),
    onPrimaryContainer = Color(0xFF410007),
    secondary = Color(0xFF775651),
    background = LightBackground,
    onBackground = Color(0xFF201A19),
    surface = Color.White,
    onSurface = Color(0xFF201A19),
    surfaceVariant = Color(0xFFF5DDDA),
    onSurfaceVariant = Color(0xFF534341),
)

private val DarkColors = darkColorScheme(
    primary = RedPrimaryDark,
    onPrimary = Color(0xFF561E1F),
    primaryContainer = Color(0xFF7A2A2C),
    onPrimaryContainer = Color(0xFFFFDAD8),
    secondary = Color(0xFFE7BDB7),
    background = DarkBackground,
    onBackground = Color(0xFFEDE0DE),
    surface = Color(0xFF1E1B1B),
    onSurface = Color(0xFFEDE0DE),
    surfaceVariant = Color(0xFF534341),
    onSurfaceVariant = Color(0xFFD8C2BF),
)

@Composable
fun RedNoteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
