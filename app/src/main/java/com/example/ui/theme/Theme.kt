package com.example.ui.theme

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

private val DarkColorScheme = darkColorScheme(
    primary = TealAccent,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    background = SlateDark,
    surface = CardDark,
    onBackground = Color.White,
    onSurface = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = TealPrimary,
    secondary = PurpleGrey40,
    tertiary = Pink40,
    background = Color.White,
    surface = LightGray,
    onBackground = Color.Black,
    onSurface = Color.Black
)

private val BentoLightColorScheme = lightColorScheme(
    primary = BentoPrimary,
    secondary = BentoContainer,
    tertiary = BentoLightBlue,
    background = BentoBg,
    surface = Color.White,
    onBackground = BentoText,
    onSurface = BentoText,
    onSurfaceVariant = BentoText.copy(alpha = 0.7f),
    primaryContainer = BentoContainer,
    onPrimaryContainer = BentoOnContainer,
    surfaceVariant = BentoLightBlue,
    outline = BentoBorder
)

private val BentoDarkColorScheme = darkColorScheme(
    primary = BentoPrimary,
    secondary = BentoContainer,
    tertiary = BentoLightBlue,
    background = BentoDarkContainer,
    surface = Color(0xFF202325),
    onBackground = Color.White,
    onSurface = Color.White,
    primaryContainer = BentoPrimary,
    onPrimaryContainer = Color.White,
    surfaceVariant = Color(0xFF282B2E),
    outline = Color(0xFF3A3E42)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    isAmoled: Boolean = false,
    accentColor: String = "Bento", // "Bento", "Teal", "Blue", "Orange" or Hex like "#FF0000"
    dynamicColor: Boolean = false, // Set to false to prioritize our gorgeous branding
    content: @Composable () -> Unit,
) {
    val baseScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        accentColor == "Bento" -> {
            if (darkTheme) {
                if (isAmoled) darkColorScheme(
                    primary = BentoPrimary,
                    secondary = BentoContainer,
                    tertiary = BentoLightBlue,
                    background = PureBlack,
                    surface = PureBlack,
                    onBackground = Color.White,
                    onSurface = Color.White,
                    primaryContainer = BentoPrimary,
                    onPrimaryContainer = Color.White,
                    surfaceVariant = Color(0xFF282B2E),
                    outline = Color(0xFF3A3E42)
                ) else BentoDarkColorScheme
            } else BentoLightColorScheme
        }
        darkTheme -> {
            // Apply Custom Dark Accent Color
            val primaryColor = when (accentColor) {
                "Blue" -> BlueAccent
                "Orange" -> OrangeAccent
                "Teal" -> TealAccent
                else -> {
                    try {
                        Color(android.graphics.Color.parseColor(accentColor))
                    } catch (e: Exception) {
                        TealAccent
                    }
                }
            }
            darkColorScheme(
                primary = primaryColor,
                background = if (isAmoled) PureBlack else SlateDark,
                surface = if (isAmoled) PureBlack else CardDark,
                onBackground = Color.White,
                onSurface = Color.White
            )
        }
        else -> {
            // Apply Custom Light Accent Color
            val primaryColor = when (accentColor) {
                "Blue" -> BluePrimary
                "Orange" -> OrangePrimary
                "Teal" -> TealPrimary
                else -> {
                    try {
                        Color(android.graphics.Color.parseColor(accentColor))
                    } catch (e: Exception) {
                        TealPrimary
                    }
                }
            }
            lightColorScheme(
                primary = primaryColor,
                background = Color.White,
                surface = LightGray,
                onBackground = Color.Black,
                onSurface = Color.Black
            )
        }
    }

    MaterialTheme(
        colorScheme = baseScheme,
        typography = Typography,
        content = content
    )
}
