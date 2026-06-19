package dev.vaultmesh.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Teal = Color(0xFF2DD4BF)
private val TealDark = Color(0xFF0F766E)
private val Indigo = Color(0xFF6366F1)

private val DarkColors = darkColorScheme(
    primary = Teal,
    onPrimary = Color(0xFF06241F),
    secondary = Indigo,
    background = Color(0xFF0B0F14),
    surface = Color(0xFF121821),
    surfaceVariant = Color(0xFF1B2430),
    onBackground = Color(0xFFE6EDF3),
    onSurface = Color(0xFFE6EDF3),
    error = Color(0xFFF87171),
)

private val LightColors = lightColorScheme(
    primary = TealDark,
    secondary = Indigo,
    background = Color(0xFFF7FAFC),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEDF1F5),
)

@Composable
fun VaultMeshTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = Typography(),
        content = content,
    )
}
