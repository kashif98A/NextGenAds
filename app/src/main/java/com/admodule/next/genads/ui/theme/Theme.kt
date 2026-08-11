package com.admodule.next.genads.ui.theme

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

private val DarkColorScheme =
  darkColorScheme(
    primary = ProfSoftBlue,
    onPrimary = ProfDarkCardBg,
    primaryContainer = ProfBlueDark,
    onPrimaryContainer = Color.White,
    secondary = ProfPink,
    background = ProfDarkCardBg,
    surface = ProfDarkCardBg,
    onBackground = Color.White,
    onSurface = Color.White,
    outline = ProfBorder
  )

private val LightColorScheme =
  lightColorScheme(
    primary = ProfBlue,
    onPrimary = Color.White,
    primaryContainer = ProfSoftBlue,
    onPrimaryContainer = ProfBlue,
    secondary = ProfGray,
    background = ProfBg,
    surface = Color.White,
    onBackground = ProfText,
    onSurface = ProfText,
    outline = ProfBorder,
    surfaceVariant = ProfSoftBlueDim,
    onSurfaceVariant = ProfGray
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Disable dynamic color to enforce our precise Professional Polish design theme
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
