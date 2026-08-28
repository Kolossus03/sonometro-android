package io.github.kolossus03.sonometro.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kolossus03.sonometro.core.NoiseClass
import io.github.kolossus03.sonometro.core.Thresholds
import io.github.kolossus03.sonometro.ui.theme.SonometroTheme
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val START_ANGLE = 135f
private const val SWEEP_ANGLE = 270f
private const val MIN_DB = 20.0
private const val MAX_DB = 110.0

/**
 * Medidor circular. El arco recorre 270° desde abajo-izquierda; el color del
 * progreso codifica la clase de ruido, de modo que la lectura se entiende de un
 * vistazo sin leer el número.
 */
@Composable
fun GaugeMeter(
    db: Double?,
    thresholds: Thresholds,
    unit: String,
    modifier: Modifier = Modifier,
) {
    val levels = SonometroTheme.levels
    val noiseClass = db?.let { thresholds.classify(it) }
    val targetColor = noiseClass?.let { levels.colorFor(it) }
        ?: MaterialTheme.colorScheme.onSurfaceVariant

    val animatedColor by animateColorAsState(targetColor, tween(400), label = "gaugeColor")
    val animatedFraction by animateFloatAsState(
        targetValue = db?.let { fractionOf(it) } ?: 0f,
        animationSpec = tween(120),
        label = "gaugeFraction",
    )

    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val tickColor = MaterialTheme.colorScheme.outline
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val measurer = rememberTextMeasurer()

    Box(modifier = modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        // fillMaxSize, no fillMaxWidth: el Canvas no tiene altura propia y el arco
        // colapsaría en el centro.
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.075f
            val radius = (size.minDimension - stroke) / 2f - size.minDimension * 0.10f
            val center = Offset(size.width / 2f, size.height / 2f)
            val arcTopLeft = Offset(center.x - radius, center.y - radius)
            val arcSize = Size(radius * 2, radius * 2)

            drawArc(
                color = trackColor,
                startAngle = START_ANGLE,
                sweepAngle = SWEEP_ANGLE,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )

            drawZoneRing(center, radius + stroke * 0.85f, thresholds, levels.toList())

            if (animatedFraction > 0f) {
                drawArc(
                    color = animatedColor,
                    startAngle = START_ANGLE,
                    sweepAngle = SWEEP_ANGLE * animatedFraction,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }

            drawTicks(center, radius, stroke, tickColor, labelColor, measurer)
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = db?.let { it.roundToInt().toString() } ?: "––",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = unit,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                text = noiseClass?.label ?: "En espera",
                style = MaterialTheme.typography.titleSmall,
                color = animatedColor,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

private fun DrawScope.drawZoneRing(
    center: Offset,
    radius: Float,
    thresholds: Thresholds,
    zoneColors: List<Color>,
) {
    val bounds = listOf(MIN_DB, thresholds.quietBelow, thresholds.moderateBelow, thresholds.noisyBelow, MAX_DB)
    val ringStroke = size.minDimension * 0.014f
    val topLeft = Offset(center.x - radius, center.y - radius)
    val arcSize = Size(radius * 2, radius * 2)

    for (i in 0 until 4) {
        val from = fractionOf(bounds[i])
        val to = fractionOf(bounds[i + 1])
        if (to <= from) continue
        drawArc(
            color = zoneColors[i].copy(alpha = 0.55f),
            startAngle = START_ANGLE + SWEEP_ANGLE * from + 0.8f,
            sweepAngle = SWEEP_ANGLE * (to - from) - 1.6f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = ringStroke, cap = StrokeCap.Butt),
        )
    }
}

private fun DrawScope.drawTicks(
    center: Offset,
    radius: Float,
    stroke: Float,
    tickColor: Color,
    labelColor: Color,
    measurer: TextMeasurer,
) {
    val innerEdge = radius - stroke / 2f
    val labelStyle = TextStyle(fontSize = 10.sp, color = labelColor)

    var value = MIN_DB
    while (value <= MAX_DB) {
        val fraction = fractionOf(value)
        val angleRad = Math.toRadians((START_ANGLE + SWEEP_ANGLE * fraction).toDouble())
        val cosA = cos(angleRad).toFloat()
        val sinA = sin(angleRad).toFloat()

        val major = (value.roundToInt() % 20) == 0
        val tickLength = if (major) stroke * 0.55f else stroke * 0.3f
        val outer = innerEdge - stroke * 0.35f
        val inner = outer - tickLength

        drawLine(
            color = tickColor,
            start = Offset(center.x + outer * cosA, center.y + outer * sinA),
            end = Offset(center.x + inner * cosA, center.y + inner * sinA),
            strokeWidth = if (major) 2.5f else 1.5f,
        )

        if (major) {
            val label = value.roundToInt().toString()
            val layout = measurer.measure(label, labelStyle)
            val labelRadius = inner - layout.size.height * 0.75f
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(
                    center.x + labelRadius * cosA - layout.size.width / 2f,
                    center.y + labelRadius * sinA - layout.size.height / 2f,
                ),
            )
        }
        value += 10.0
    }
}

private fun fractionOf(db: Double): Float =
    (((db - MIN_DB) / (MAX_DB - MIN_DB)).coerceIn(0.0, 1.0)).toFloat()

private fun io.github.kolossus03.sonometro.ui.theme.LevelPalette.toList(): List<Color> =
    NoiseClass.entries.map { colorFor(it) }
