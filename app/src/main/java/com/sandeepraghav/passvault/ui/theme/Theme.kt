package com.sandeepraghav.passvault.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val VaultBlue = Color(0xFF4C8DFF)
val VaultBlueDark = Color(0xFF2D5FCC)
val VaultBackground = Color(0xFFF5F7FA)
val VaultSurfaceDark = Color(0xFF11151C)
val VaultDanger = Color(0xFFD32F2F)

private val LightColors = lightColorScheme(
    primary = VaultBlueDark,
    secondary = VaultBlue,
    background = VaultBackground,
    error = VaultDanger
)

private val DarkColors = darkColorScheme(
    primary = VaultBlue,
    secondary = VaultBlueDark,
    background = VaultSurfaceDark,
    error = Color(0xFFEF5350)
)

@Composable
fun PassVaultTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = MaterialTheme.typography,
        content = content
    )
}
