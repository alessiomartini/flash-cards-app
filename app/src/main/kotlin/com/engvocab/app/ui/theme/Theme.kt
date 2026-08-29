package com.engvocab.app.ui.theme

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

private val EngVocabBlue = Color(0xFF3760F0)
private val EngVocabBlueDark = Color(0xFFAEC1FF)
private val EngVocabGreen = Color(0xFF16A34A)

private val LightColors = lightColorScheme(
    primary = EngVocabBlue,
    secondary = EngVocabGreen,
)

private val DarkColors = darkColorScheme(
    primary = EngVocabBlueDark,
    secondary = EngVocabGreen,
)

@Composable
fun EngVocabTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = EngVocabTypography,
        content = content,
    )
}
