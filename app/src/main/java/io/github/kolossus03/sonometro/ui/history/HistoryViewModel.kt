package io.github.kolossus03.sonometro.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.kolossus03.sonometro.AppContainer
import io.github.kolossus03.sonometro.core.Analysis
import io.github.kolossus03.sonometro.core.Calibration
import io.github.kolossus03.sonometro.core.DayPoint
import io.github.kolossus03.sonometro.core.HourStat
import io.github.kolossus03.sonometro.core.NoiseSummary
import io.github.kolossus03.sonometro.core.PeakEvent
import io.github.kolossus03.sonometro.core.Thresholds
import io.github.kolossus03.sonometro.core.localDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId

private const val WEEK_DAYS = 7L

data class HistoryUiState(
    val date: LocalDate = LocalDate.now(),
    val loading: Boolean = true,
    val dayPoints: List<DayPoint> = emptyList(),
    val hourly: List<HourStat> = emptyList(),
    val weekMatrix: Map<LocalDate, List<Double?>> = emptyMap(),
    val peaks: List<PeakEvent> = emptyList(),
    val daySummary: NoiseSummary? = null,
    val weekSummary: NoiseSummary? = null,
    val quietestHour: HourStat? = null,
    val noisiestHour: HourStat? = null,
    val calibration: Calibration = Calibration.Uncalibrated,
    val thresholds: Thresholds = Thresholds.Default,
    val unit: String = "dBA",
) {
    val isToday: Boolean get() = date == LocalDate.now()
    val hasDayData: Boolean get() = dayPoints.isNotEmpty()
}

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    container: AppContainer,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    private val selectedDate = MutableStateFlow(LocalDate.now())

    /**
     * Una sola consulta por semana: el día seleccionado se recorta de ese rango en
     * memoria. Pedir día y semana por separado dispararía dos observadores de Room
     * que se refrescan a destiempo y hacen parpadear las gráficas.
     */
    val uiState = selectedDate
        .flatMapLatest { date ->
            combine(
                container.measurements.observeRange(date, WEEK_DAYS, zone),
                container.settings.settings,
            ) { rows, settings ->
                val comparable = Analysis.comparable(rows)
                val dayRows = comparable.filter { it.localDate(zone) == date }
                val cal = settings.calibration
                val thresholds = settings.thresholds

                val hourly = Analysis.hourly(dayRows, cal, zone)
                val weekDays = (0 until WEEK_DAYS).map { date.minusDays(WEEK_DAYS - 1 - it) }

                HistoryUiState(
                    date = date,
                    loading = false,
                    dayPoints = Analysis.dayPoints(dayRows, cal),
                    hourly = hourly,
                    weekMatrix = Analysis.weekMatrix(comparable, cal, weekDays, zone),
                    peaks = Analysis.peakEvents(dayRows, cal, thresholds),
                    daySummary = Analysis.summarize(dayRows, cal, thresholds),
                    weekSummary = Analysis.summarize(comparable, cal, thresholds),
                    quietestHour = Analysis.quietestHour(hourly),
                    noisiestHour = Analysis.noisiestHour(hourly),
                    calibration = cal,
                    thresholds = thresholds,
                    unit = settings.weighting.unit,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun previousDay() {
        selectedDate.value = selectedDate.value.minusDays(1)
    }

    fun nextDay() {
        val next = selectedDate.value.plusDays(1)
        if (!next.isAfter(LocalDate.now())) selectedDate.value = next
    }

    fun today() {
        selectedDate.value = LocalDate.now()
    }
}
