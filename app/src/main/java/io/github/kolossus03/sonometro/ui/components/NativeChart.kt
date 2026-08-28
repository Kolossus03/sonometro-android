package io.github.kolossus03.sonometro.ui.components

import android.graphics.Canvas
import android.graphics.RectF
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kolossus03.sonometro.chart.ChartStyle
import io.github.kolossus03.sonometro.core.NoiseClass
import io.github.kolossus03.sonometro.ui.theme.HeatRamp
import io.github.kolossus03.sonometro.ui.theme.SonometroTheme

/**
 * Pinta con un [Canvas] de Android dentro de un composable. Los renderizadores de
 * `chart/Charts.kt` se usan tal cual desde aquí y desde el generador de PDF.
 */
@Composable
fun NativeChart(
    modifier: Modifier = Modifier,
    draw: (Canvas, RectF) -> Unit,
) {
    ComposeCanvas(modifier) {
        drawIntoCanvas { canvas ->
            draw(canvas.nativeCanvas, RectF(0f, 0f, size.width, size.height))
        }
    }
}

/** Estilo de gráfica derivado del tema activo, para que claro/oscuro salgan gratis. */
@Composable
fun rememberChartStyle(darkTheme: Boolean): ChartStyle {
    val colors = MaterialTheme.colorScheme
    val levels = SonometroTheme.levels
    val density = LocalDensity.current
    val textSizePx = with(density) { 11.sp.toPx() }
    val strokePx = with(density) { 2.dp.toPx() }
    val ramp = if (darkTheme) HeatRamp.Dark else HeatRamp.Light

    return remember(colors, levels, textSizePx, darkTheme) {
        ChartStyle.from(
            surface = colors.surface,
            onSurface = colors.onSurface,
            muted = colors.onSurfaceVariant,
            grid = colors.outlineVariant,
            primary = colors.primary,
            statusNoisy = levels.colorFor(NoiseClass.NOISY),
            heatRamp = ramp,
            textSizePx = textSizePx,
            strokePx = strokePx,
        )
    }
}
