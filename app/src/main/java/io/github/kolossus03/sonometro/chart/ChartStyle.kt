package io.github.kolossus03.sonometro.chart

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * Parámetros de pintura de las gráficas.
 *
 * Se dibuja contra [android.graphics.Canvas], no contra el DrawScope de Compose,
 * para que exactamente el mismo código produzca la gráfica de la pantalla y la del
 * informe PDF. Una gráfica dibujada dos veces sería una gráfica que diverge.
 */
data class ChartStyle(
    val surface: Int,
    val onSurface: Int,
    val muted: Int,
    val grid: Int,
    val primary: Int,
    val statusNoisy: Int,
    val heatRamp: List<Int>,
    /** Tamaño base del texto, en píxeles del lienzo destino. */
    val textSizePx: Float,
    val strokePx: Float,
) {
    val labelTextSizePx: Float get() = textSizePx * 0.9f

    companion object {
        fun from(
            surface: Color,
            onSurface: Color,
            muted: Color,
            grid: Color,
            primary: Color,
            statusNoisy: Color,
            heatRamp: List<Color>,
            textSizePx: Float,
            strokePx: Float,
        ) = ChartStyle(
            surface = surface.toArgb(),
            onSurface = onSurface.toArgb(),
            muted = muted.toArgb(),
            grid = grid.toArgb(),
            primary = primary.toArgb(),
            statusNoisy = statusNoisy.toArgb(),
            heatRamp = heatRamp.map { it.toArgb() },
            textSizePx = textSizePx,
            strokePx = strokePx,
        )

        /** Estilo fijo para el PDF: siempre claro, con tinta oscura sobre papel. */
        fun forPdf(textSizePx: Float = 9f, strokePx: Float = 1.6f) = ChartStyle(
            surface = 0xFFFFFFFF.toInt(),
            onSurface = 0xFF0F172A.toInt(),
            muted = 0xFF64748B.toInt(),
            grid = 0xFFE2E8F0.toInt(),
            primary = 0xFF0284C7.toInt(),
            statusNoisy = 0xFFE11D48.toInt(),
            heatRamp = listOf(
                0xFFF0F9FF, 0xFFE0F2FE, 0xFFBAE6FD, 0xFF7DD3FC,
                0xFF38BDF8, 0xFF0EA5E9, 0xFF0284C7, 0xFF075985,
            ).map { it.toInt() },
            textSizePx = textSizePx,
            strokePx = strokePx,
        )
    }
}
