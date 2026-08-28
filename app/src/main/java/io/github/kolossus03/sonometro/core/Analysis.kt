package io.github.kolossus03.sonometro.core

import io.github.kolossus03.sonometro.data.db.MeasurementEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/** Nivel agregado de una hora del día (0..23). Nulo si no hubo medidas. */
data class HourStat(
    val hour: Int,
    val leqDb: Double?,
    val peakDb: Double?,
    val samples: Long,
)

/** Un punto de la curva del día. */
data class DayPoint(
    val timestampMs: Long,
    val leqDb: Double,
    val peakDb: Double,
)

/** Una superación del umbral, con su marca temporal. */
data class PeakEvent(
    val timestampMs: Long,
    val peakDb: Double,
    val leqDb: Double,
    val label: String,
)

/** Resumen estadístico de un conjunto de medidas, listo para el informe. */
data class NoiseSummary(
    val leqDb: Double?,
    val maxDb: Double?,
    val minDb: Double?,
    val exceedances: Int,
    val exceedanceLimitDb: Double,
    val intervals: Int,
    val coverageMinutes: Double,
    val weighting: String,
    val anyClipped: Boolean,
) {
    val isEmpty: Boolean get() = intervals == 0
}

/**
 * Todas las agregaciones pasan por [LeqAccumulator.ofWeighted] y nunca por una
 * media aritmética de decibelios. Un intervalo de 60 s con 600 lecturas pesa el
 * doble que uno truncado de 300, y un pico de 90 dB durante un minuto domina la
 * hora entera aunque el resto sea silencio. Promediar los dB "a mano" borraría
 * exactamente el evento que el usuario quiere documentar.
 */
object Analysis {

    /** Cuando hay medidas con distinta ponderación, gana la mayoritaria. */
    fun dominantWeighting(rows: List<MeasurementEntity>): String =
        rows.groupingBy { it.weighting }.eachCount().maxByOrNull { it.value }?.key ?: "A"

    /** Descarta las filas cuya ponderación no sea comparable con el resto. */
    fun comparable(rows: List<MeasurementEntity>): List<MeasurementEntity> {
        if (rows.isEmpty()) return rows
        val dominant = dominantWeighting(rows)
        return rows.filter { it.weighting == dominant }
    }

    fun dayPoints(rows: List<MeasurementEntity>, calibration: Calibration): List<DayPoint> =
        rows.map {
            DayPoint(
                timestampMs = it.timestampMs,
                leqDb = calibration.toSpl(it.leqDbfs),
                peakDb = calibration.toSpl(it.peakDbfs),
            )
        }

    /**
     * Promedio por hora del día. Responde a "¿a qué horas es más ruidoso mi
     * barrio?" y, por complemento, a "¿cuándo conviene abrir la ventana?".
     */
    fun hourly(
        rows: List<MeasurementEntity>,
        calibration: Calibration,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<HourStat> {
        val byHour = rows.groupBy { it.hourOfDay(zone) }
        return (0..23).map { hour ->
            val bucket = byHour[hour].orEmpty()
            if (bucket.isEmpty()) {
                HourStat(hour, null, null, 0)
            } else {
                val leqDbfs = LeqAccumulator.ofWeighted(
                    bucket.map { it.leqDbfs to it.samples.toLong() }
                )
                HourStat(
                    hour = hour,
                    leqDb = calibration.toSpl(leqDbfs),
                    peakDb = calibration.toSpl(bucket.maxOf { it.peakDbfs }),
                    samples = bucket.sumOf { it.samples.toLong() },
                )
            }
        }
    }

    /** La hora más silenciosa con datos: el mejor momento para abrir la ventana. */
    fun quietestHour(hourly: List<HourStat>): HourStat? =
        hourly.filter { it.leqDb != null }.minByOrNull { it.leqDb!! }

    fun noisiestHour(hourly: List<HourStat>): HourStat? =
        hourly.filter { it.leqDb != null }.maxByOrNull { it.leqDb!! }

    /**
     * Matriz día × hora para el mapa de calor semanal. `null` = sin datos, que se
     * pinta distinto de "silencioso": ausencia de medida no es ausencia de ruido.
     */
    fun weekMatrix(
        rows: List<MeasurementEntity>,
        calibration: Calibration,
        days: List<LocalDate>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Map<LocalDate, List<Double?>> {
        val byDay = rows.groupBy { it.localDate(zone) }
        return days.associateWith { day ->
            val dayRows = byDay[day].orEmpty()
            hourly(dayRows, calibration, zone).map { it.leqDb }
        }
    }

    /**
     * Superaciones del umbral. Se usa el **pico** del intervalo, no su Leq: una
     * moto que pasa a 92 dB durante tres segundos es una superación aunque el
     * promedio del minuto se quede en 55.
     */
    fun peakEvents(
        rows: List<MeasurementEntity>,
        calibration: Calibration,
        thresholds: Thresholds,
    ): List<PeakEvent> = rows
        .filter { calibration.toSpl(it.peakDbfs) >= thresholds.exceedanceLimit }
        .map {
            PeakEvent(
                timestampMs = it.timestampMs,
                peakDb = calibration.toSpl(it.peakDbfs),
                leqDb = calibration.toSpl(it.leqDbfs),
                label = it.label,
            )
        }
        .sortedByDescending { it.peakDb }

    fun summarize(
        rows: List<MeasurementEntity>,
        calibration: Calibration,
        thresholds: Thresholds,
    ): NoiseSummary {
        if (rows.isEmpty()) {
            return NoiseSummary(
                leqDb = null, maxDb = null, minDb = null,
                exceedances = 0, exceedanceLimitDb = thresholds.exceedanceLimit,
                intervals = 0, coverageMinutes = 0.0, weighting = "A", anyClipped = false,
            )
        }
        val leqDbfs = LeqAccumulator.ofWeighted(rows.map { it.leqDbfs to it.samples.toLong() })
        val totalSamples = rows.sumOf { it.samples.toLong() }
        return NoiseSummary(
            leqDb = calibration.toSpl(leqDbfs),
            maxDb = calibration.toSpl(rows.maxOf { it.peakDbfs }),
            minDb = calibration.toSpl(rows.minOf { it.minDbfs }),
            exceedances = peakEvents(rows, calibration, thresholds).size,
            exceedanceLimitDb = thresholds.exceedanceLimit,
            intervals = rows.size,
            // Cada lectura son 100 ms.
            coverageMinutes = totalSamples * 0.1 / 60.0,
            weighting = dominantWeighting(rows),
            anyClipped = rows.any { it.clipped },
        )
    }

    /** Superaciones agrupadas por franja horaria, para la tabla del informe. */
    fun exceedancesByHour(
        rows: List<MeasurementEntity>,
        calibration: Calibration,
        thresholds: Thresholds,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Map<Int, Int> = rows
        .filter { calibration.toSpl(it.peakDbfs) >= thresholds.exceedanceLimit }
        .groupingBy { it.hourOfDay(zone) }
        .eachCount()
}

fun MeasurementEntity.hourOfDay(zone: ZoneId = ZoneId.systemDefault()): Int =
    Instant.ofEpochMilli(timestampMs).atZone(zone).hour

fun MeasurementEntity.localDate(zone: ZoneId = ZoneId.systemDefault()): LocalDate =
    Instant.ofEpochMilli(timestampMs).atZone(zone).toLocalDate()

fun Double?.formatDb(): String = this?.takeIf { it.isFinite() }?.roundToInt()?.toString() ?: "––"
