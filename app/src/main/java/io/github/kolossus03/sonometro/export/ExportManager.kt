package io.github.kolossus03.sonometro.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import io.github.kolossus03.sonometro.core.Calibration
import io.github.kolossus03.sonometro.core.Thresholds
import io.github.kolossus03.sonometro.data.db.MeasurementEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Resultado de una exportación, listo para compartir. */
data class ExportResult(val file: File, val uri: Uri, val mimeType: String)

class ExportManager(private val context: Context) {

    private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    private fun exportsDir(): File = File(context.cacheDir, "exports").apply { mkdirs() }

    suspend fun exportCsv(
        rows: List<MeasurementEntity>,
        calibration: Calibration,
        label: String,
    ): ExportResult = withContext(Dispatchers.IO) {
        val name = buildString {
            append("sonometro")
            if (label.isNotBlank()) append("_").append(label.slug())
            append("_").append(LocalDate.now().format(stamp)).append(".csv")
        }
        val file = CsvExporter.write(File(exportsDir(), name), rows, calibration)
        ExportResult(file, file.toUri(), "text/csv")
    }

    suspend fun exportPdf(
        date: LocalDate,
        rows: List<MeasurementEntity>,
        calibration: Calibration,
        thresholds: Thresholds,
        label: String,
    ): ExportResult = withContext(Dispatchers.IO) {
        val name = buildString {
            append("informe_ruido")
            if (label.isNotBlank()) append("_").append(label.slug())
            append("_").append(date.format(stamp)).append(".pdf")
        }
        val file = PdfReportGenerator.write(
            target = File(exportsDir(), name),
            date = date,
            rows = rows,
            calibration = calibration,
            thresholds = thresholds,
            locationLabel = label,
        )
        ExportResult(file, file.toUri(), "application/pdf")
    }

    /** Intent de compartir con permiso de lectura temporal para el receptor. */
    fun shareIntent(result: ExportResult): Intent {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = result.mimeType
            putExtra(Intent.EXTRA_STREAM, result.uri)
            putExtra(Intent.EXTRA_SUBJECT, result.file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "Compartir ${result.file.name}").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /** Intent para abrir el fichero con un visor del propio teléfono. */
    fun viewIntent(result: ExportResult): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(result.uri, result.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    private fun File.toUri(): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", this)

    private fun String.slug(): String =
        trim().lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(24)
}
