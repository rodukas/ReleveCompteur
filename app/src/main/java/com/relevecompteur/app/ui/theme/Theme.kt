package com.relevecompteur.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val PrimaryBlue = Color(0xFF0B57D0)
val SecondaryTeal = Color(0xFF006A6A)
val TertiaryAmber = Color(0xFFB35F00)
val WaterBlue = Color(0xFF0277BD)
val ElecGreen = Color(0xFF558B2F)
val VehicleIndigo = Color(0xFF3949AB)
val AlertRed = Color(0xFFB3261E)
val WarningOrange = Color(0xFFE65100)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E3FD),
    onPrimaryContainer = Color(0xFF041E49),
    secondary = SecondaryTeal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF9CF1F0),
    onSecondaryContainer = Color(0xFF002020),
    tertiary = TertiaryAmber,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDCC1),
    onTertiaryContainer = Color(0xFF2E1500),
    error = AlertRed,
    background = Color(0xFFF8F9FC),
    surface = Color.White,
    surfaceVariant = Color(0xFFEEF1F8)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFA8C7FA),
    onPrimary = Color(0xFF062E6F),
    primaryContainer = Color(0xFF0842A0),
    onPrimaryContainer = Color(0xFFD3E3FD),
    secondary = Color(0xFF80D5D4),
    onSecondary = Color(0xFF003737),
    secondaryContainer = Color(0xFF004F4F),
    onSecondaryContainer = Color(0xFF9CF1F0),
    tertiary = Color(0xFFFFB779),
    onTertiary = Color(0xFF4C2700),
    tertiaryContainer = Color(0xFF6D3A00),
    onTertiaryContainer = Color(0xFFFFDCC1)
)

@Composable
fun ReleveCompteurTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

