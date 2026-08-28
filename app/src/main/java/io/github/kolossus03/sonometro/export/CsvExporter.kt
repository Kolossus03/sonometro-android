package io.github.kolossus03.sonometro.export

import io.github.kolossus03.sonometro.core.Calibration
import io.github.kolossus03.sonometro.data.db.MeasurementEntity
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Exporta el histórico a CSV.
 *
 * Cada fila lleva su propio `estado_calibracion` y `offset_db`. No es redundancia:
 * un CSV se abre meses después, se recorta y se pega en un correo, y en ese momento
 * ya no hay ninguna cabecera que diga si esos decibelios eran una medida o una
 * estimación. La fila tiene que poder defenderse sola.
 */
object CsvExporter {

    private val ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    private val COLUMNS = listOf(
        "fecha_hora_local",
        "epoch_ms",
        "etiqueta",
        "ponderacion",
        "leq_db",
        "pico_db",
        "minimo_db",
        "muestras_100ms",
        "saturado",
        "estado_calibracion",
        "offset_db",
    )

    fun write(
        target: File,
        rows: List<MeasurementEntity>,
        calibration: Calibration,
        zone: ZoneId = ZoneId.systemDefault(),
    ): File {
        target.parentFile?.mkdirs()
        target.bufferedWriter().use { out ->
            out.write(COLUMNS.joinToString(","))
            out.newLine()

            val status = if (calibration.isCalibrated) "calibrado" else "estimacion_relativa"
            for (row in rows) {
                val localTime = Instant.ofEpochMilli(row.timestampMs).atZone(zone).toLocalDateTime()
                val fields = listOf(
                    ISO.format(localTime),
                    row.timestampMs.toString(),
                    row.label.csvEscape(),
                    row.weighting,
                    format1(calibration.toSpl(row.leqDbfs)),
                    format1(calibration.toSpl(row.peakDbfs)),
                    format1(calibration.toSpl(row.minDbfs)),
                    row.samples.toString(),
                    if (row.clipped) "si" else "no",
                    status,
                    format1(calibration.offsetDb),
                )
                out.write(fields.joinToString(","))
                out.newLine()
            }
        }
        return target
    }

    /** Punto decimal, no coma: una coma decimal rompería el propio separador. */
    private fun format1(value: Double): String =
        if (value.isFinite()) String.format(java.util.Locale.US, "%.1f", value) else ""

    private fun String.csvEscape(): String =
        if (any { it == ',' || it == '"' || it == '\n' }) {
            "\"" + replace("\"", "\"\"") + "\""
        } else {
            this
        }
}
