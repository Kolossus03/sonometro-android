package io.github.kolossus03.sonometro.export

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import io.github.kolossus03.sonometro.chart.ChartStyle
import io.github.kolossus03.sonometro.chart.renderDayChart
import io.github.kolossus03.sonometro.chart.renderHourlyChart
import io.github.kolossus03.sonometro.core.Analysis
import io.github.kolossus03.sonometro.core.Calibration
import io.github.kolossus03.sonometro.core.Thresholds
import io.github.kolossus03.sonometro.core.formatDb
import io.github.kolossus03.sonometro.data.db.MeasurementEntity
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Informe PDF pensado para adjuntarse a una reclamación por ruido.
 *
 * Las gráficas las dibujan las mismas funciones que la pantalla
 * (`chart/Charts.kt`) sobre el Canvas de la página: si la app enseña una curva y el
 * informe enseñara otra, el informe no valdría nada.
 */
object PdfReportGenerator {

    // A4 a 72 dpi, las unidades nativas de PdfDocument.
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f

    private val ES = Locale("es", "ES")
    private val DATE = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", ES)
    private val STAMP = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", ES)
    private val TIME = DateTimeFormatter.ofPattern("HH:mm:ss", ES)

    private val ink = 0xFF0F172A.toInt()
    private val muted = 0xFF64748B.toInt()
    private val rule = 0xFFE2E8F0.toInt()
    private val warn = 0xFF9A3412.toInt()
    private val warnBg = 0xFFFFF7ED.toInt()

    fun write(
        target: File,
        date: LocalDate,
        rows: List<MeasurementEntity>,
        calibration: Calibration,
        thresholds: Thresholds,
        locationLabel: String,
        zone: ZoneId = ZoneId.systemDefault(),
    ): File {
        target.parentFile?.mkdirs()
        val document = PdfDocument()
        val comparable = Analysis.comparable(rows)
        val summary = Analysis.summarize(comparable, calibration, thresholds)
        val style = ChartStyle.forPdf()

        val page = document.startPage(
            PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        )
        drawFirstPage(page.canvas, date, comparable, calibration, thresholds, locationLabel, summary, style, zone)
        document.finishPage(page)

        val events = Analysis.peakEvents(comparable, calibration, thresholds)
        if (events.isNotEmpty()) {
            val page2 = document.startPage(
                PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 2).create()
            )
            drawEventsPage(page2.canvas, events, summary.weighting, thresholds, zone)
            document.finishPage(page2)
        }

        target.outputStream().use { document.writeTo(it) }
        document.close()
        return target
    }

    private fun drawFirstPage(
        canvas: Canvas,
        date: LocalDate,
        rows: List<MeasurementEntity>,
        calibration: Calibration,
        thresholds: Thresholds,
        locationLabel: String,
        summary: io.github.kolossus03.sonometro.core.NoiseSummary,
        style: ChartStyle,
        zone: ZoneId,
    ) {
        var y = MARGIN

        canvas.drawText("Informe de ruido ambiental", MARGIN, y + 16f, title())
        y += 26f
        canvas.drawText(
            date.format(DATE).replaceFirstChar { it.uppercase() },
            MARGIN, y + 10f, body(muted),
        )
        y += 16f
        val where = locationLabel.ifBlank { "sin etiqueta de ubicación" }
        canvas.drawText(
            "Ubicación: $where · Generado el ${LocalDateTime.now().format(STAMP)}",
            MARGIN, y + 10f, small(muted),
        )
        y += 20f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint(rule))
        y += 18f

        // La nota de calibración va arriba, no en letra pequeña al final: es la
        // condición bajo la que hay que leer todo lo demás.
        y = drawCalibrationNotice(canvas, y, calibration)
        y += 16f

        canvas.drawText("Resumen", MARGIN, y, heading())
        y += 14f
        val unit = if (summary.weighting == "A") "dBA" else "dBZ"
        val stats = listOf(
            "Nivel continuo equivalente (Leq)" to "${summary.leqDb.formatDb()} $unit",
            "Nivel máximo registrado" to "${summary.maxDb.formatDb()} $unit",
            "Nivel mínimo registrado" to "${summary.minDb.formatDb()} $unit",
            "Umbral de superación" to "${thresholds.exceedanceLimit.roundToInt()} $unit",
            "Superaciones del umbral" to "${summary.exceedances} intervalos",
            "Intervalos registrados" to "${summary.intervals}",
            "Tiempo efectivo de medición" to "%.0f minutos".format(summary.coverageMinutes),
        )
        for ((label, value) in stats) {
            canvas.drawText(label, MARGIN + 4f, y + 9f, body(muted))
            canvas.drawText(value, PAGE_WIDTH - MARGIN - 4f, y + 9f, body(ink, Paint.Align.RIGHT))
            y += 15f
        }
        if (summary.anyClipped) {
            y += 4f
            canvas.drawText(
                "Aviso: algunos intervalos saturaron el micrófono; su nivel real es superior al indicado.",
                MARGIN + 4f, y + 9f, small(warn),
            )
            y += 14f
        }

        // El eje X de cada gráfica se dibuja pegado a su borde inferior, así que el
        // siguiente título necesita aire propio o se le monta encima.
        y += 20f
        canvas.drawText("Nivel a lo largo del día", MARGIN, y, heading())
        y += 12f
        renderDayChart(
            canvas,
            RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + 190f),
            Analysis.dayPoints(rows, calibration),
            thresholds,
            style,
            zone,
        )
        y += 190f + 22f

        canvas.drawText("Promedio por franja horaria", MARGIN, y, heading())
        y += 12f
        renderHourlyChart(
            canvas,
            RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + 170f),
            Analysis.hourly(rows, calibration, zone),
            thresholds,
            style,
        )
        y += 170f + 24f

        val byHour = Analysis.exceedancesByHour(rows, calibration, thresholds, zone)
        canvas.drawText("Superaciones por franja horaria", MARGIN, y, heading())
        y += 16f
        if (byHour.isEmpty()) {
            canvas.drawText("Ninguna superación del umbral en el periodo.", MARGIN + 4f, y + 9f, body(muted))
            y += 15f
        } else {
            // Con datos de las 24 horas esta tabla desbordaría el pie de página.
            val bottomLimit = PAGE_HEIGHT - 52f
            var truncated = 0
            for ((hour, count) in byHour.toSortedMap()) {
                if (y + 14f > bottomLimit) {
                    truncated++
                    continue
                }
                canvas.drawText("%02d:00 – %02d:59".format(hour, hour), MARGIN + 4f, y + 9f, body(muted))
                canvas.drawText(
                    "$count ${if (count == 1) "superación" else "superaciones"}",
                    PAGE_WIDTH - MARGIN - 4f, y + 9f, body(ink, Paint.Align.RIGHT),
                )
                y += 14f
            }
            if (truncated > 0) {
                canvas.drawText(
                    "y $truncated franjas más (ver el detalle en la página siguiente y en el CSV).",
                    MARGIN + 4f, y + 9f, small(muted),
                )
            }
        }

        drawFooter(canvas, 1)
    }

    private fun drawCalibrationNotice(canvas: Canvas, top: Float, calibration: Calibration): Float {
        val height = if (calibration.isCalibrated) 44f else 58f
        val box = RectF(MARGIN, top, PAGE_WIDTH - MARGIN, top + height)
        canvas.drawRoundRect(box, 4f, 4f, fillPaint(warnBg))
        canvas.drawRoundRect(box, 4f, 4f, linePaint(0xFFFDBA74.toInt()))

        var y = top + 15f
        if (calibration.isCalibrated) {
            canvas.drawText("Medición calibrada", MARGIN + 10f, y, heading(warn))
            y += 13f
            canvas.drawText(
                "Calibrada frente a una referencia externa con un offset de " +
                    "%.1f dB. Precisión sujeta a la del instrumento de referencia.".format(calibration.offsetDb),
                MARGIN + 10f, y, small(warn),
            )
        } else {
            canvas.drawText("Estimación relativa · medición SIN CALIBRAR", MARGIN + 10f, y, heading(warn))
            y += 13f
            canvas.drawText(
                "El micrófono de un teléfono no viene calibrado de fábrica. Los valores absolutos en dB",
                MARGIN + 10f, y, small(warn),
            )
            y += 11f
            canvas.drawText(
                "pueden desviarse varios decibelios y no sustituyen a un sonómetro homologado. Las",
                MARGIN + 10f, y, small(warn),
            )
            y += 11f
            canvas.drawText(
                "variaciones relativas entre horas y días sí son significativas.",
                MARGIN + 10f, y, small(warn),
            )
        }
        return top + height
    }

    private fun drawEventsPage(
        canvas: Canvas,
        events: List<io.github.kolossus03.sonometro.core.PeakEvent>,
        weighting: String,
        thresholds: Thresholds,
        zone: ZoneId,
    ) {
        var y = MARGIN
        canvas.drawText("Eventos de pico", MARGIN, y + 16f, title())
        y += 30f
        val unit = if (weighting == "A") "dBA" else "dBZ"
        canvas.drawText(
            "Intervalos cuyo nivel de pico alcanzó o superó ${thresholds.exceedanceLimit.roundToInt()} $unit, " +
                "ordenados de mayor a menor.",
            MARGIN, y + 9f, small(muted),
        )
        y += 22f

        canvas.drawText("Hora", MARGIN + 4f, y + 9f, small(muted))
        canvas.drawText("Pico", MARGIN + 110f, y + 9f, small(muted))
        canvas.drawText("Leq del intervalo", MARGIN + 190f, y + 9f, small(muted))
        canvas.drawText("Ubicación", MARGIN + 320f, y + 9f, small(muted))
        y += 13f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint(rule))
        y += 12f

        // Sin paginación adicional: más de ~50 filas se recortan y el CSV queda como
        // fuente completa. El informe es un resumen, no el registro entero.
        for (event in events.take(50)) {
            val time = Instant.ofEpochMilli(event.timestampMs).atZone(zone).format(TIME)
            canvas.drawText(time, MARGIN + 4f, y + 9f, body(ink))
            canvas.drawText("${event.peakDb.formatDb()} $unit", MARGIN + 110f, y + 9f, body(ink))
            canvas.drawText("${event.leqDb.formatDb()} $unit", MARGIN + 190f, y + 9f, body(muted))
            canvas.drawText(event.label.ifBlank { "—" }, MARGIN + 320f, y + 9f, body(muted))
            y += 14f
            if (y > PAGE_HEIGHT - 60f) break
        }

        if (events.size > 50) {
            y += 6f
            canvas.drawText(
                "Se muestran los 50 picos más altos de ${events.size}. El CSV contiene el registro completo.",
                MARGIN + 4f, y + 9f, small(muted),
            )
        }
        drawFooter(canvas, 2)
    }

    private fun drawFooter(canvas: Canvas, page: Int) {
        canvas.drawLine(MARGIN, PAGE_HEIGHT - 36f, PAGE_WIDTH - MARGIN, PAGE_HEIGHT - 36f, linePaint(rule))
        canvas.drawText(
            "Generado por Sonómetro · el audio no se graba, solo se calcula su nivel",
            MARGIN, PAGE_HEIGHT - 22f, small(muted),
        )
        canvas.drawText(
            "Página $page", PAGE_WIDTH - MARGIN, PAGE_HEIGHT - 22f, small(muted, Paint.Align.RIGHT),
        )
    }

    private fun title() = Paint().apply {
        isAntiAlias = true; color = ink; textSize = 17f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private fun heading(color: Int = ink) = Paint().apply {
        isAntiAlias = true; this.color = color; textSize = 11f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private fun body(color: Int, align: Paint.Align = Paint.Align.LEFT) = Paint().apply {
        isAntiAlias = true; this.color = color; textSize = 10f; textAlign = align
    }

    private fun small(color: Int, align: Paint.Align = Paint.Align.LEFT) = Paint().apply {
        isAntiAlias = true; this.color = color; textSize = 8.5f; textAlign = align
    }

    private fun linePaint(color: Int) = Paint().apply {
        isAntiAlias = true; this.color = color; style = Paint.Style.STROKE; strokeWidth = 0.8f
    }

    private fun fillPaint(color: Int) = Paint().apply {
        isAntiAlias = true; this.color = color; style = Paint.Style.FILL
    }
}
