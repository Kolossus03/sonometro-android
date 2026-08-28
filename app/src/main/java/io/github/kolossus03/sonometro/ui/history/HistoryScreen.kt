package io.github.kolossus03.sonometro.ui.history

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.kolossus03.sonometro.chart.renderDayChart
import io.github.kolossus03.sonometro.chart.renderHourlyChart
import io.github.kolossus03.sonometro.chart.renderWeekHeatmap
import io.github.kolossus03.sonometro.core.HourStat
import io.github.kolossus03.sonometro.core.NoiseSummary
import io.github.kolossus03.sonometro.core.PeakEvent
import io.github.kolossus03.sonometro.core.formatDb
import io.github.kolossus03.sonometro.ui.AppViewModelProvider
import io.github.kolossus03.sonometro.ui.components.NativeChart
import io.github.kolossus03.sonometro.ui.components.rememberChartStyle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ES = Locale("es", "ES")
private val DATE_FORMAT = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", ES)
private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss", ES)

@Composable
fun HistoryScreen(
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val style = rememberChartStyle(isSystemInDarkTheme())

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            DateSelector(
                date = state.date,
                isToday = state.isToday,
                onPrevious = viewModel::previousDay,
                onNext = viewModel::nextDay,
                onToday = viewModel::today,
            )
        }

        item {
            state.daySummary?.let { SummaryRow(it, state.unit, state.calibration.isCalibrated) }
        }

        item {
            ChartCard(
                title = "Nivel a lo largo del día",
                subtitle = "Promedio (Leq) y pico de cada intervalo",
                height = 190.dp,
            ) { canvas, bounds ->
                renderDayChart(canvas, bounds, state.dayPoints, state.thresholds, style)
            }
        }

        item {
            ChartCard(
                title = "Franjas horarias",
                subtitle = "Promedio por hora del día",
                height = 180.dp,
            ) { canvas, bounds ->
                renderHourlyChart(canvas, bounds, state.hourly, state.thresholds, style)
            }
        }

        item {
            WindowAdvice(state.quietestHour, state.noisiestHour, state.unit, state.thresholds.exceedanceLimit)
        }

        item {
            ChartCard(
                title = "Semana",
                subtitle = "Promedio por hora y día · celdas vacías = sin medir",
                height = 210.dp,
            ) { canvas, bounds ->
                renderWeekHeatmap(canvas, bounds, state.weekMatrix, style, ES)
            }
        }

        item {
            PeakEventsHeader(state.peaks.size, state.thresholds.exceedanceLimit, state.unit)
        }

        if (state.peaks.isEmpty()) {
            item {
                Text(
                    "Ninguna superación registrada este día.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
        } else {
            items(state.peaks.size) { index ->
                PeakEventRow(state.peaks[index], state.unit)
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun DateSelector(
    date: LocalDate,
    isToday: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.Rounded.ChevronLeft, contentDescription = "Día anterior")
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                date.format(DATE_FORMAT).replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.titleSmall,
            )
            if (!isToday) {
                TextButton(onClick = onToday) { Text("Volver a hoy") }
            }
        }
        IconButton(onClick = onNext, enabled = !isToday) {
            Icon(Icons.Rounded.ChevronRight, contentDescription = "Día siguiente")
        }
    }
}

@Composable
private fun SummaryRow(summary: NoiseSummary, unit: String, calibrated: Boolean) {
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MiniStat("Leq del día", summary.leqDb.formatDb(), unit, Modifier.weight(1f))
            MiniStat("Máximo", summary.maxDb.formatDb(), unit, Modifier.weight(1f))
            MiniStat("Superaciones", summary.exceedances.toString(), "eventos", Modifier.weight(1f))
        }
        Spacer(Modifier.height(6.dp))
        val coverage = "%.0f min medidos".format(summary.coverageMinutes)
        val calibrationNote = if (calibrated) "calibrado" else "estimación relativa, sin calibrar"
        Text(
            "$coverage · $calibrationNote",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MiniStat(title: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(vertical = 10.dp, horizontal = 10.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                unit,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun ChartCard(
    title: String,
    subtitle: String,
    height: androidx.compose.ui.unit.Dp,
    draw: (android.graphics.Canvas, android.graphics.RectF) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            NativeChart(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height),
                draw = draw,
            )
        }
    }
}

/** El consejo que da nombre a la app: cuándo abrir la ventana. */
@Composable
private fun WindowAdvice(quietest: HourStat?, noisiest: HourStat?, unit: String, limit: Double) {
    if (quietest?.leqDb == null || noisiest?.leqDb == null) return
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.NightsStay, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Mejor hora para abrir la ventana: ${"%02d".format(quietest.hour)}:00",
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "${quietest.leqDb.formatDb()} $unit de promedio. La hora más ruidosa fue " +
                    "las ${"%02d".format(noisiest.hour)}:00 con ${noisiest.leqDb.formatDb()} $unit.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun PeakEventsHeader(count: Int, limit: Double, unit: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        Icon(Icons.AutoMirrored.Rounded.VolumeUp, null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Column {
            Text("Eventos de pico", style = MaterialTheme.typography.titleSmall)
            Text(
                "$count intervalos con pico ≥ ${limit.formatDb()} $unit",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PeakEventRow(event: PeakEvent, unit: String) {
    val time = Instant.ofEpochMilli(event.timestampMs)
        .atZone(ZoneId.systemDefault())
        .format(TIME_FORMAT)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(time, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(84.dp))
        Text(
            "${event.peakDb.formatDb()} $unit",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(84.dp),
        )
        Text(
            event.label.ifBlank { "—" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
