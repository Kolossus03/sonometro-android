package io.github.kolossus03.sonometro.ui.meter

import android.annotation.SuppressLint
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.kolossus03.sonometro.AppContainer
import io.github.kolossus03.sonometro.audio.Reading
import io.github.kolossus03.sonometro.core.Calibration
import io.github.kolossus03.sonometro.core.LeqAccumulator
import io.github.kolossus03.sonometro.core.Thresholds
import io.github.kolossus03.sonometro.audio.Weighting
import io.github.kolossus03.sonometro.data.Settings
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val HISTORY_CAPACITY = 120 // 12 s a 10 lecturas/s

data class MeterUiState(
    val isRunning: Boolean = false,
    val currentDb: Double? = null,
    val averageDb: Double? = null,
    val peakDb: Double? = null,
    val minDb: Double? = null,
    val historyDb: List<Float> = emptyList(),
    val clipping: Boolean = false,
    val elapsedSamples: Long = 0,
    val settings: Settings = Settings(),
    val error: String? = null,
) {
    val calibration: Calibration get() = settings.calibration
    val thresholds: Thresholds get() = settings.thresholds
    val unit: String get() = settings.weighting.unit
    /** Segundos de medición: cada lectura son 100 ms. */
    val elapsedSeconds: Long get() = elapsedSamples / 10
}

/** Estado en dBFS, antes de aplicar la calibración. */
private data class LiveState(
    val fastDbfs: Double? = null,
    val leqDbfs: Double? = null,
    val peakDbfs: Double? = null,
    val minDbfs: Double? = null,
    val historyDbfs: List<Float> = emptyList(),
    val clipping: Boolean = false,
    val samples: Long = 0,
)

class MeterViewModel(private val container: AppContainer) : ViewModel() {

    private val accumulator = LeqAccumulator()
    private val live = MutableStateFlow(LiveState())
    private val running = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)
    private var job: Job? = null

    /** Si el usuario pausó a mano, volver a la pantalla no debe reanudar la medición. */
    private var pausedByUser = false
    private var lastWeighting: Weighting? = null

    /**
     * El estado vivo se mantiene en dBFS y la calibración se aplica en la
     * proyección a UI. Así, mover el offset repinta el histórico entero al
     * instante en vez de dejar mezcladas lecturas viejas y nuevas.
     */
    val uiState = combine(
        container.settings.settings,
        live,
        running,
        error,
    ) { settings, live, isRunning, err ->
        val cal = settings.calibration
        MeterUiState(
            isRunning = isRunning,
            currentDb = live.fastDbfs?.let(cal::toSpl),
            averageDb = live.leqDbfs?.let(cal::toSpl),
            peakDb = live.peakDbfs?.let(cal::toSpl),
            minDb = live.minDbfs?.let(cal::toSpl),
            historyDb = live.historyDbfs.map { cal.toSpl(it.toDouble()).toFloat() },
            clipping = live.clipping,
            elapsedSamples = live.samples,
            settings = settings,
            error = err,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MeterUiState())

    /** Reanuda al volver a la pantalla, salvo que el usuario hubiera pausado. */
    fun onScreenResumed() {
        if (!pausedByUser) start()
    }

    fun onScreenPaused() {
        // Libera el micrófono; el registro en segundo plano es cosa del servicio.
        job?.cancel()
        job = null
        running.value = false
    }

    @SuppressLint("MissingPermission") // La pantalla no llama a start() sin el permiso.
    fun start() {
        if (job?.isActive == true) return
        pausedByUser = false
        error.value = null
        running.value = true

        job = viewModelScope.launch {
            container.readingSource.readings()
                .catch { e ->
                    error.value = e.message ?: "Error al leer el micrófono."
                    running.value = false
                }
                .collect { (weighting, reading) ->
                    // Cambiar la ponderación invalida las estadísticas acumuladas:
                    // dBA y dBZ no son comparables entre sí.
                    if (lastWeighting != null && lastWeighting != weighting) resetStats()
                    lastWeighting = weighting
                    onReading(reading)
                }
        }
    }

    fun stop() {
        pausedByUser = true
        job?.cancel()
        job = null
        running.value = false
    }

    fun resetStats() {
        accumulator.reset()
        live.value = LiveState()
    }

    fun dismissError() {
        error.value = null
    }

    private fun onReading(reading: Reading) {
        accumulator.add(reading.blockDbfs)
        live.update { previous ->
            val history = (previous.historyDbfs + reading.fastDbfs.toFloat())
                .takeLast(HISTORY_CAPACITY)
            LiveState(
                fastDbfs = reading.fastDbfs,
                leqDbfs = accumulator.leqDbfs(),
                peakDbfs = accumulator.peakDbfs(),
                minDbfs = accumulator.minDbfs(),
                historyDbfs = history,
                clipping = reading.clipped,
                samples = accumulator.samples,
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        stop()
    }
}
