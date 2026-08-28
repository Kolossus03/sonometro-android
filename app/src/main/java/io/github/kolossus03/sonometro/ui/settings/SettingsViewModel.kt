package io.github.kolossus03.sonometro.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.kolossus03.sonometro.AppContainer
import io.github.kolossus03.sonometro.audio.Weighting
import io.github.kolossus03.sonometro.core.Calibration
import io.github.kolossus03.sonometro.core.Thresholds
import io.github.kolossus03.sonometro.data.Settings
import io.github.kolossus03.sonometro.export.ExportResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class SettingsUiState(
    val settings: Settings = Settings(),
    /** Lectura viva, ya calibrada. Nula si el micrófono aún no ha entregado nada. */
    val currentDb: Double? = null,
    /** Texto del campo de etiqueta. Lo posee el ViewModel, no el composable. */
    val labelText: String = "",
    val measurementCount: Int = 0,
    val exporting: Boolean = false,
    val message: String? = null,
    val pendingShare: ExportResult? = null,
) {
    val calibration: Calibration get() = settings.calibration
    val unit: String get() = settings.weighting.unit
}

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    /** dBFS crudo de la última lectura: la base para calcular un offset nuevo. */
    private val currentDbfs = MutableStateFlow<Double?>(null)
    private val exporting = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private val pendingShare = MutableStateFlow<ExportResult?>(null)
    private var meterJob: Job? = null

    /**
     * Borrador del campo de etiqueta.
     *
     * No puede vivir en un `remember` cuya clave sea el valor persistido: cada
     * pulsación escribe en DataStore, DataStore reemite, la clave cambia y el estado
     * del campo se reinicia al valor que aún no ha llegado. Escribiendo rápido se
     * pierden letras ("salon" acababa como "soa"). El ViewModel es el dueño.
     */
    private val labelDraft = MutableStateFlow<String?>(null)

    val uiState = combine(
        container.settings.settings,
        currentDbfs,
        container.measurements.observeCount(),
        labelDraft,
        combine(exporting, message, pendingShare, ::Triple),
    ) { settings, dbfs, count, draft, (isExporting, msg, share) ->
        SettingsUiState(
            settings = settings,
            currentDb = dbfs?.let(settings.calibration::toSpl),
            labelText = draft ?: settings.locationLabel,
            measurementCount = count,
            exporting = isExporting,
            message = msg,
            pendingShare = share,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun startListening() {
        if (meterJob?.isActive == true) return
        meterJob = viewModelScope.launch {
            container.readingSource.readings()
                .catch { message.value = it.message ?: "No se pudo leer el micrófono." }
                .collect { (_, reading) -> currentDbfs.value = reading.fastDbfs }
        }
    }

    fun stopListening() {
        meterJob?.cancel()
        meterJob = null
        currentDbfs.value = null
    }

    /**
     * Calibra contra un sonómetro de referencia.
     *
     * El offset se recalcula desde el dBFS crudo, no sumando una corrección al
     * valor mostrado. Así, calibrar dos veces seguidas con la misma referencia da
     * el mismo resultado, en vez de acumular desviación.
     */
    fun calibrateTo(referenceDb: Double) {
        val dbfs = currentDbfs.value
        if (dbfs == null) {
            message.value = "Espera a que haya una lectura del micrófono."
            return
        }
        if (referenceDb !in 20.0..140.0) {
            message.value = "Introduce un valor entre 20 y 140 dB."
            return
        }
        viewModelScope.launch {
            container.settings.setCalibrationOffset(referenceDb - dbfs)
            message.value = "Calibrado: offset %.1f dB.".format(referenceDb - dbfs)
        }
    }

    fun setOffset(offsetDb: Double) = viewModelScope.launch {
        container.settings.setCalibrationOffset(offsetDb)
    }

    fun resetCalibration() = viewModelScope.launch {
        container.settings.resetCalibration()
        message.value = "Calibración restablecida. Las lecturas vuelven a ser una estimación."
    }

    fun setInterval(seconds: Int) = viewModelScope.launch {
        container.settings.setSamplingInterval(seconds)
    }

    fun setWeighting(weighting: Weighting) = viewModelScope.launch {
        container.settings.setWeighting(weighting)
    }

    fun setLabel(label: String) {
        labelDraft.value = label
        viewModelScope.launch { container.settings.setLocationLabel(label) }
    }

    /** Los umbrales deben quedar estrictamente crecientes o la clasificación se rompe. */
    fun setThresholds(thresholds: Thresholds) = viewModelScope.launch {
        val quiet = thresholds.quietBelow.coerceIn(25.0, 95.0)
        val moderate = thresholds.moderateBelow.coerceIn(quiet + 1, 100.0)
        val noisy = thresholds.noisyBelow.coerceIn(moderate + 1, 110.0)
        container.settings.setThresholds(Thresholds(quiet, moderate, noisy))
    }

    fun exportCsv() = export { settings ->
        val rows = container.measurements.getAll()
        if (rows.isEmpty()) error("No hay datos que exportar todavía.")
        container.exports.exportCsv(rows, settings.calibration, settings.locationLabel)
    }

    fun exportPdf(date: LocalDate = LocalDate.now()) = export { settings ->
        val rows = container.measurements.getDay(date)
        if (rows.isEmpty()) error("No hay datos de ese día para el informe.")
        container.exports.exportPdf(
            date = date,
            rows = rows,
            calibration = settings.calibration,
            thresholds = settings.thresholds,
            label = settings.locationLabel,
        )
    }

    private fun export(block: suspend (Settings) -> ExportResult) = viewModelScope.launch {
        exporting.value = true
        val settings = uiState.value.settings
        runCatching { block(settings) }
            .onSuccess {
                pendingShare.value = it
                message.value = "Generado ${it.file.name}"
            }
            .onFailure { message.value = it.message ?: "Falló la exportación." }
        exporting.value = false
    }

    fun deleteAll() = viewModelScope.launch {
        container.measurements.deleteAll()
        message.value = "Histórico borrado."
    }

    fun shareIntent(result: ExportResult) = container.exports.shareIntent(result)

    fun consumeShare() {
        pendingShare.value = null
    }

    fun consumeMessage() {
        message.value = null
    }

    override fun onCleared() {
        super.onCleared()
        stopListening()
    }
}
