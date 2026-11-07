package dev.pocketserver.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pocketserver.core.config.ThemeMode

val TerminalGreen = Color(0xFF4ADE80)
val TerminalBlue = Color(0xFF38BDF8)
val TerminalAmber = Color(0xFFFBBF24)
val TerminalRed = Color(0xFFF87171)
val TerminalMuted = Color(0xFF94A3B8)
val CodeFont = FontFamily.Monospace

private val DarkColors = darkColorScheme(
    primary = TerminalGreen,
    onPrimary = Color(0xFF052E16),
    primaryContainer = Color(0xFF12351F),
    onPrimaryContainer = Color(0xFFBBF7D0),
    secondary = TerminalBlue,
    onSecondary = Color(0xFF082F49),
    secondaryContainer = Color(0xFF0C3040),
    onSecondaryContainer = Color(0xFFBAE6FD),
    tertiary = TerminalAmber,
    onTertiary = Color(0xFF451A03),
    background = Color(0xFF070A0E),
    onBackground = Color(0xFFE6EDF3),
    surface = Color(0xFF0D1218),
    onSurface = Color(0xFFE6EDF3),
    surfaceVariant = Color(0xFF17202A),
    onSurfaceVariant = Color(0xFFA7B4C2),
    outline = Color(0xFF2B3948),
    outlineVariant = Color(0xFF1D2732),
    error = TerminalRed,
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF3A1212),
    onErrorContainer = Color(0xFFFECACA),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF047857),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1FAE5),
    onPrimaryContainer = Color(0xFF064E3B),
    secondary = Color(0xFF0369A1),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF0C4A6E),
    tertiary = Color(0xFFB45309),
    onTertiary = Color.White,
    background = Color(0xFFF5F7F9),
    onBackground = Color(0xFF101820),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF101820),
    surfaceVariant = Color(0xFFEDF1F5),
    onSurfaceVariant = Color(0xFF4B5968),
    outline = Color(0xFFC5CFDA),
    outlineVariant = Color(0xFFDFE6ED),
    error = Color(0xFFB91C1C),
)

private val PocketTypography = Typography().let { base ->
    base.copy(
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Medium),
    )
}

private val PocketShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun PocketServerTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = PocketTypography,
        shapes = PocketShapes,
        content = content,
    )
}
