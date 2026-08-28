package io.github.kolossus03.sonometro.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.kolossus03.sonometro.core.NoiseClass

private val LightScheme = lightColorScheme(
    primary = Sky600,
    onPrimary = Color.White,
    primaryContainer = Sky200,
    onPrimaryContainer = Sky700,
    secondary = Slate700,
    onSecondary = Color.White,
    background = Slate50,
    onBackground = Slate900,
    surface = Color.White,
    onSurface = Slate900,
    surfaceVariant = Slate100,
    onSurfaceVariant = Slate500,
    outline = Slate300,
    outlineVariant = Slate100,
    error = Color(0xFFB91C1C),
)

private val DarkScheme = darkColorScheme(
    primary = Sky400,
    onPrimary = Slate950,
    primaryContainer = Sky700,
    onPrimaryContainer = Sky200,
    secondary = Slate300,
    onSecondary = Slate950,
    background = Slate950,
    onBackground = Slate50,
    surface = Slate900,
    onSurface = Slate50,
    surfaceVariant = Slate800,
    onSurfaceVariant = Slate300,
    outline = Slate700,
    outlineVariant = Slate800,
    error = Color(0xFFF87171),
)

/** Paleta de niveles resuelta para el tema activo. */
data class LevelPalette(
    val quiet: Color,
    val moderate: Color,
    val noisy: Color,
    val veryNoisy: Color,
) {
    fun colorFor(noiseClass: NoiseClass): Color = when (noiseClass) {
        NoiseClass.QUIET -> quiet
        NoiseClass.MODERATE -> moderate
        NoiseClass.NOISY -> noisy
        NoiseClass.VERY_NOISY -> veryNoisy
    }
}

private val LightLevels = LevelPalette(
    quiet = LevelColors.QuietLight,
    moderate = LevelColors.ModerateLight,
    noisy = LevelColors.NoisyLight,
    veryNoisy = LevelColors.VeryNoisyLight,
)

private val DarkLevels = LevelPalette(
    quiet = LevelColors.QuietDark,
    moderate = LevelColors.ModerateDark,
    noisy = LevelColors.NoisyDark,
    veryNoisy = LevelColors.VeryNoisyDark,
)

private val LocalLevelPalette = staticCompositionLocalOf { LightLevels }

object SonometroTheme {
    val levels: LevelPalette
        @Composable @ReadOnlyComposable get() = LocalLevelPalette.current
}

private val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Light,
        fontSize = 72.sp,
        letterSpacing = (-2).sp,
    ),
)

/**
 * Sin color dinámico a propósito: la rampa verde→rojo del medidor es semántica y
 * debe leerse igual en todos los teléfonos. El wallpaper del usuario no puede
 * repintar lo que significa "muy ruidoso".
 */
@Composable
fun SonometroTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkScheme else LightScheme
    val levels = if (darkTheme) DarkLevels else LightLevels

    CompositionLocalProvider(LocalLevelPalette provides levels) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            content = content,
        )
    }
}
