package io.github.kolossus03.sonometro.chart

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import io.github.kolossus03.sonometro.core.DayPoint
import io.github.kolossus03.sonometro.core.HourStat
import io.github.kolossus03.sonometro.core.Thresholds
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Todo se mide en múltiplos del tamaño de texto. El mismo código dibuja sobre una
 * pantalla a 3x de densidad y sobre una página A4 a 72 dpi; una constante en
 * píxeles absolutos quedaría bien en exactamente uno de los dos.
 */
private val ChartStyle.u: Float get() = textSizePx / 9f
private val ChartStyle.axisLeft: Float get() = textSizePx * 2.9f
private val ChartStyle.axisBottom: Float get() = textSizePx * 1.9f
private val ChartStyle.legendTop: Float get() = textSizePx * 1.7f

/** Rango del eje Y, redondeado a decenas y con una amplitud mínima legible. */
private fun yRange(values: List<Double>, threshold: Double): Pair<Double, Double> {
    if (values.isEmpty()) return 20.0 to 90.0
    var lo = floor((values.min() - 3) / 10.0) * 10.0
    var hi = ceil((max(values.max(), threshold) + 3) / 10.0) * 10.0
    if (hi - lo < 40) hi = lo + 40
    lo = max(0.0, lo)
    return lo to hi
}

private fun paint(color: Int, stroke: Float = 0f, fill: Boolean = false) = Paint().apply {
    isAntiAlias = true
    this.color = color
    style = if (fill) Paint.Style.FILL else Paint.Style.STROKE
    strokeWidth = stroke
}

private fun textPaint(color: Int, size: Float, align: Paint.Align = Paint.Align.LEFT) = Paint().apply {
    isAntiAlias = true
    this.color = color
    textSize = size
    textAlign = align
    style = Paint.Style.FILL
}

/** Rejilla horizontal + etiquetas del eje Y. Hairlines sólidas, nunca discontinuas. */
private fun drawGrid(
    canvas: Canvas,
    plot: RectF,
    lo: Double,
    hi: Double,
    style: ChartStyle,
) {
    val gridPaint = paint(style.grid, 1f)
    val labelPaint = textPaint(style.muted, style.labelTextSizePx, Paint.Align.RIGHT)
    var v = ceil(lo / 10.0) * 10.0
    while (v <= hi) {
        val y = plot.bottom - ((v - lo) / (hi - lo)).toFloat() * plot.height()
        canvas.drawLine(plot.left, y, plot.right, y, gridPaint)
        canvas.drawText(
            v.roundToInt().toString(),
            plot.left - 5f * style.u,
            y + style.labelTextSizePx * 0.35f,
            labelPaint,
        )
        v += 10.0
    }
}

private fun drawThreshold(
    canvas: Canvas,
    plot: RectF,
    lo: Double,
    hi: Double,
    threshold: Double,
    style: ChartStyle,
    label: String,
) {
    if (threshold < lo || threshold > hi) return
    val y = plot.bottom - ((threshold - lo) / (hi - lo)).toFloat() * plot.height()
    canvas.drawLine(plot.left, y, plot.right, y, paint(style.statusNoisy, style.strokePx * 0.9f))
    canvas.drawText(
        label,
        plot.right - 2f * style.u,
        y - style.labelTextSizePx * 0.4f,
        textPaint(style.statusNoisy, style.labelTextSizePx, Paint.Align.RIGHT),
    )
}

/**
 * Curva del día. Dos series (Leq y pico), así que lleva leyenda.
 *
 * Los huecos sin datos no se interpolan: si el registro estuvo parado dos horas,
 * la línea se corta. Unir los extremos inventaría una medida que nadie tomó.
 */
fun renderDayChart(
    canvas: Canvas,
    bounds: RectF,
    points: List<DayPoint>,
    thresholds: Thresholds,
    style: ChartStyle,
    zone: ZoneId = ZoneId.systemDefault(),
    gapMs: Long = 10 * 60 * 1000L,
) {
    val plot = RectF(
        bounds.left + style.axisLeft,
        bounds.top + style.legendTop,
        bounds.right - 2f * style.u,
        bounds.bottom - style.axisBottom,
    )
    if (points.isEmpty()) {
        drawEmpty(canvas, bounds, style, "Sin datos para este día")
        return
    }

    val (lo, hi) = yRange(points.flatMap { listOf(it.leqDb, it.peakDb) }, thresholds.exceedanceLimit)
    drawGrid(canvas, plot, lo, hi, style)

    val day = Instant.ofEpochMilli(points.first().timestampMs).atZone(zone).toLocalDate()
    val dayStart = day.atStartOfDay(zone).toInstant().toEpochMilli()
    val dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    fun x(ts: Long) = plot.left + ((ts - dayStart).toDouble() / (dayEnd - dayStart)).toFloat() * plot.width()
    fun y(db: Double) = plot.bottom - ((db - lo) / (hi - lo)).toFloat() * plot.height()

    // Segmentos continuos: se rompe la serie en cada hueco de registro.
    val segments = mutableListOf<MutableList<DayPoint>>()
    var current = mutableListOf<DayPoint>()
    for (p in points) {
        if (current.isNotEmpty() && p.timestampMs - current.last().timestampMs > gapMs) {
            segments += current
            current = mutableListOf()
        }
        current += p
    }
    if (current.isNotEmpty()) segments += current

    // Envolvente de picos, translúcida.
    val envelope = paint(style.primary, fill = true).apply { alpha = 46 }
    for (seg in segments) {
        if (seg.size < 2) continue
        val path = Path()
        path.moveTo(x(seg.first().timestampMs), y(seg.first().peakDb))
        seg.forEach { path.lineTo(x(it.timestampMs), y(it.peakDb)) }
        for (i in seg.indices.reversed()) path.lineTo(x(seg[i].timestampMs), y(seg[i].leqDb))
        path.close()
        canvas.drawPath(path, envelope)
    }

    drawThreshold(canvas, plot, lo, hi, thresholds.exceedanceLimit, style, "${thresholds.exceedanceLimit.roundToInt()} dB")

    // Línea del Leq.
    val linePaint = paint(style.primary, style.strokePx).apply {
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    for (seg in segments) {
        val path = Path()
        path.moveTo(x(seg.first().timestampMs), y(seg.first().leqDb))
        seg.drop(1).forEach { path.lineTo(x(it.timestampMs), y(it.leqDb)) }
        if (seg.size == 1) {
            canvas.drawCircle(x(seg[0].timestampMs), y(seg[0].leqDb), style.strokePx, linePaint)
        } else {
            canvas.drawPath(path, linePaint)
        }
    }

    // Eje X: cada 6 horas.
    val axisPaint = textPaint(style.muted, style.labelTextSizePx, Paint.Align.CENTER)
    for (h in 0..24 step 6) {
        val ts = dayStart + h * 3600_000L
        val label = if (h == 24) "24" else "%02d".format(h)
        val cx = x(ts).coerceIn(plot.left + 6f * style.u, plot.right - 6f * style.u)
        canvas.drawText(label, cx, bounds.bottom - 4f * style.u, axisPaint)
    }

    drawLegend(
        canvas, bounds.left + style.axisLeft, bounds.top + style.textSizePx * 1.1f, style,
        listOf("Leq (promedio)" to style.primary, "Pico" to withAlpha(style.primary, 70)),
    )
}

/**
 * Promedio por hora del día. Una sola serie, así que no lleva leyenda: el color
 * destaca (emphasis) solo las horas que superan el umbral, en vez de repintar
 * cada barra según su altura, que sería doble codificación.
 */
fun renderHourlyChart(
    canvas: Canvas,
    bounds: RectF,
    hourly: List<HourStat>,
    thresholds: Thresholds,
    style: ChartStyle,
) {
    val plot = RectF(
        bounds.left + style.axisLeft,
        bounds.top + 8f * style.u,
        bounds.right - 2f * style.u,
        bounds.bottom - style.axisBottom,
    )
    val values = hourly.mapNotNull { it.leqDb }
    if (values.isEmpty()) {
        drawEmpty(canvas, bounds, style, "Sin datos suficientes")
        return
    }

    val (lo, hi) = yRange(values, thresholds.exceedanceLimit)
    drawGrid(canvas, plot, lo, hi, style)

    val slot = plot.width() / 24f
    val barWidth = slot * 0.62f
    val radius = min(4f * style.u, barWidth / 2f)
    fun y(db: Double) = plot.bottom - ((db - lo) / (hi - lo)).toFloat() * plot.height()

    val loudest = hourly.filter { it.leqDb != null }.maxByOrNull { it.leqDb!! }

    for (stat in hourly) {
        val cx = plot.left + slot * stat.hour + slot / 2f
        val db = stat.leqDb
        if (db == null) {
            canvas.drawLine(
                cx - barWidth / 2f, plot.bottom, cx + barWidth / 2f, plot.bottom,
                paint(style.grid, 2f * style.u),
            )
            continue
        }
        val exceeds = db >= thresholds.exceedanceLimit
        val color = if (exceeds) style.statusNoisy else withAlpha(style.primary, 165)
        val top = y(db)
        canvas.drawRoundRect(
            RectF(cx - barWidth / 2f, top, cx + barWidth / 2f, plot.bottom),
            radius, radius,
            paint(color, fill = true),
        )
    }

    drawThreshold(canvas, plot, lo, hi, thresholds.exceedanceLimit, style, "${thresholds.exceedanceLimit.roundToInt()} dB")

    // Etiqueta directa solo en la hora más ruidosa: un número en cada barra sería caos.
    loudest?.let { stat ->
        val cx = plot.left + slot * stat.hour + slot / 2f
        val top = y(stat.leqDb!!)
        canvas.drawText(
            stat.leqDb.roundToInt().toString(),
            cx,
            max(plot.top + style.labelTextSizePx, top - 4f * style.u),
            textPaint(style.onSurface, style.labelTextSizePx, Paint.Align.CENTER),
        )
    }

    val axisPaint = textPaint(style.muted, style.labelTextSizePx, Paint.Align.CENTER)
    for (h in 0..23 step 3) {
        val cx = plot.left + slot * h + slot / 2f
        canvas.drawText("%02d".format(h), cx, bounds.bottom - 4f * style.u, axisPaint)
    }
}

/**
 * Mapa de calor día × hora. Magnitud, no identidad: un solo tono claro→oscuro, con
 * escala. Siete líneas superpuestas serían espagueti ilegible.
 *
 * Las celdas sin datos se pintan como hueco, nunca como "silencioso": no haber
 * medido no es lo mismo que no haber ruido.
 */
fun renderWeekHeatmap(
    canvas: Canvas,
    bounds: RectF,
    matrix: Map<LocalDate, List<Double?>>,
    style: ChartStyle,
    locale: Locale = Locale("es"),
) {
    if (matrix.isEmpty() || matrix.values.all { row -> row.all { it == null } }) {
        drawEmpty(canvas, bounds, style, "Sin datos en el periodo")
        return
    }

    val labelWidth = style.textSizePx * 4.2f
    val legendHeight = style.textSizePx * 2.4f
    val plot = RectF(
        bounds.left + labelWidth,
        bounds.top + 12f * style.u,
        bounds.right - 2f * style.u,
        bounds.bottom - style.axisBottom - legendHeight,
    )

    val present = matrix.values.flatten().filterNotNull()
    val lo = floor(present.min())
    val hi = ceil(present.max()).let { if (it - lo < 5) lo + 5 else it }

    val days = matrix.keys.sorted()
    val rowHeight = plot.height() / days.size
    val colWidth = plot.width() / 24f
    val gap = 1.5f * style.u

    val dayLabelPaint = textPaint(style.muted, style.labelTextSizePx, Paint.Align.RIGHT)
    val emptyPaint = paint(style.grid, fill = true).apply { alpha = 90 }

    days.forEachIndexed { row, day ->
        val top = plot.top + rowHeight * row
        val label = day.dayOfWeek.getDisplayName(TextStyle.SHORT, locale).take(3) +
            " " + day.dayOfMonth
        canvas.drawText(
            label,
            plot.left - 5f * style.u,
            top + rowHeight / 2f + style.labelTextSizePx * 0.35f,
            dayLabelPaint,
        )

        val r = 2f * style.u
        matrix.getValue(day).forEachIndexed { hour, db ->
            val cell = RectF(
                plot.left + colWidth * hour + gap,
                top + gap,
                plot.left + colWidth * (hour + 1) - gap,
                top + rowHeight - gap,
            )
            if (db == null) {
                canvas.drawRoundRect(cell, r, r, emptyPaint)
            } else {
                val t = ((db - lo) / (hi - lo)).coerceIn(0.0, 1.0)
                val idx = (t * (style.heatRamp.size - 1)).roundToInt()
                canvas.drawRoundRect(cell, r, r, paint(style.heatRamp[idx], fill = true))
            }
        }
    }

    val axisPaint = textPaint(style.muted, style.labelTextSizePx, Paint.Align.CENTER)
    for (h in 0..23 step 3) {
        canvas.drawText(
            "%02d".format(h),
            plot.left + colWidth * h + colWidth / 2f,
            plot.bottom + style.labelTextSizePx + 2f * style.u,
            axisPaint,
        )
    }

    // Escala: sin leyenda de valores, un mapa de calor no se puede leer.
    val legendY = bounds.bottom - legendHeight + 6f * style.u
    val swatch = 14f * style.u
    val swatchGap = 1.5f * style.u
    val swatchHeight = 8f * style.u
    val legendX = plot.left
    style.heatRamp.forEachIndexed { i, color ->
        val left = legendX + i * (swatch + swatchGap)
        canvas.drawRect(RectF(left, legendY, left + swatch, legendY + swatchHeight), paint(color, fill = true))
    }
    val legendEnd = legendX + style.heatRamp.size * (swatch + swatchGap) - swatchGap
    val legendTextY = legendY + swatchHeight + style.labelTextSizePx + 1f * style.u
    canvas.drawText(
        "${lo.roundToInt()} dB",
        legendX,
        legendTextY,
        textPaint(style.muted, style.labelTextSizePx, Paint.Align.LEFT),
    )
    canvas.drawText(
        "${hi.roundToInt()} dB",
        legendEnd,
        legendTextY,
        textPaint(style.muted, style.labelTextSizePx, Paint.Align.RIGHT),
    )
}

private fun drawLegend(
    canvas: Canvas,
    x: Float,
    y: Float,
    style: ChartStyle,
    entries: List<Pair<String, Int>>,
) {
    val labelPaint = textPaint(style.muted, style.labelTextSizePx, Paint.Align.LEFT)
    val box = 10f * style.u
    var cursor = x
    for ((label, color) in entries) {
        canvas.drawRoundRect(
            RectF(cursor, y - box * 0.6f, cursor + box, y + box * 0.4f),
            2f * style.u, 2f * style.u,
            paint(color, fill = true),
        )
        cursor += box + 4f * style.u
        canvas.drawText(label, cursor, y + box * 0.35f, labelPaint)
        cursor += labelPaint.measureText(label) + 12f * style.u
    }
}

private fun drawEmpty(canvas: Canvas, bounds: RectF, style: ChartStyle, message: String) {
    canvas.drawText(
        message,
        bounds.centerX(),
        bounds.centerY(),
        textPaint(style.muted, style.textSizePx, Paint.Align.CENTER),
    )
}

private fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha shl 24)
