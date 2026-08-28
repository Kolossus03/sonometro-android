package io.github.kolossus03.sonometro.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import io.github.kolossus03.sonometro.core.Thresholds
import io.github.kolossus03.sonometro.ui.theme.SonometroTheme

/**
 * Tira de barras con las lecturas recientes: la prueba visual, a 10 Hz, de que el
 * micrófono está respondiendo. Las barras se colorean por clase de ruido, igual
 * que el medidor.
 */
@Composable
fun LevelStrip(
    levels: List<Float>,
    thresholds: Thresholds,
    minDb: Float = 20f,
    maxDb: Float = 110f,
    modifier: Modifier = Modifier,
) {
    val palette = SonometroTheme.levels
    val emptyColor = MaterialTheme.colorScheme.surfaceVariant

    Canvas(modifier.fillMaxSize()) {
        val capacity = 120
        val slotWidth = size.width / capacity
        val barWidth = slotWidth * 0.55f

        // Alineado a la derecha: lo más reciente entra por ese lado.
        val startIndex = capacity - levels.size

        for (i in 0 until capacity) {
            val x = slotWidth * i + slotWidth / 2f
            val reading = levels.getOrNull(i - startIndex)

            if (reading == null) {
                drawLine(
                    color = emptyColor,
                    start = Offset(x, size.height),
                    end = Offset(x, size.height - 2f),
                    strokeWidth = barWidth,
                    cap = StrokeCap.Round,
                )
                continue
            }

            val fraction = ((reading - minDb) / (maxDb - minDb)).coerceIn(0f, 1f)
            val height = (size.height * fraction).coerceAtLeast(2f)
            drawLine(
                color = palette.colorFor(thresholds.classify(reading.toDouble())),
                start = Offset(x, size.height),
                end = Offset(x, size.height - height),
                strokeWidth = barWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}
