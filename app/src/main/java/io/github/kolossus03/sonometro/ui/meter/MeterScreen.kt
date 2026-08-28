package io.github.kolossus03.sonometro.ui.meter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.kolossus03.sonometro.ui.AppViewModelProvider
import io.github.kolossus03.sonometro.ui.components.GaugeMeter
import io.github.kolossus03.sonometro.ui.components.LevelStrip
import kotlin.math.roundToInt

@Composable
fun MeterScreen(
    modifier: Modifier = Modifier,
    viewModel: MeterViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Un sonómetro que no mide al abrirlo no sirve de nada. Se para al salir para
    // no retener el micrófono: de eso se encarga el servicio en segundo plano.
    LifecycleResumeEffect(Unit) {
        viewModel.onScreenResumed()
        onPauseOrDispose { viewModel.onScreenPaused() }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(8.dp))
        CalibrationBadge(
            label = state.calibration.statusLabel,
            calibrated = state.calibration.isCalibrated,
        )

        Spacer(Modifier.height(8.dp))
        GaugeMeter(
            db = state.currentDb,
            thresholds = state.thresholds,
            unit = state.unit,
            modifier = Modifier.fillMaxWidth(0.92f),
        )

        state.error?.let { message ->
            Spacer(Modifier.height(8.dp))
            ErrorCard(message)
        }

        if (state.clipping) {
            Spacer(Modifier.height(8.dp))
            ClippingWarning()
        }

        Spacer(Modifier.height(16.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
        ) {
            LevelStrip(levels = state.historyDb, thresholds = state.thresholds)
        }

        Spacer(Modifier.height(20.dp))
        StatsRow(state)

        Spacer(Modifier.height(24.dp))
        Controls(
            isRunning = state.isRunning,
            hasData = state.elapsedSamples > 0,
            onStart = viewModel::start,
            onStop = viewModel::stop,
            onReset = viewModel::resetStats,
        )

        Spacer(Modifier.height(20.dp))
        RecordingCard(
            intervalSeconds = state.settings.samplingIntervalSeconds,
            label = state.settings.locationLabel,
        )

        Spacer(Modifier.height(24.dp))
        PrivacyNote()
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun CalibrationBadge(label: String, calibrated: Boolean) {
    val color = if (calibrated) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        leadingIcon = { Icon(Icons.Rounded.Tune, null, Modifier.size(16.dp)) },
        colors = AssistChipDefaults.assistChipColors(
            disabledLabelColor = color,
            disabledLeadingIconContentColor = color,
        ),
    )
}

@Composable
private fun StatsRow(state: MeterUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatCard("Promedio", state.averageDb, state.unit, Modifier.weight(1f), "Leq")
        StatCard("Pico", state.peakDb, state.unit, Modifier.weight(1f))
        StatCard("Mínimo", state.minDb, state.unit, Modifier.weight(1f))
    }
    if (state.elapsedSamples > 0) {
        Spacer(Modifier.height(8.dp))
        Text(
            "Midiendo desde hace ${formatDuration(state.elapsedSeconds)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatCard(
    title: String,
    value: Double?,
    unit: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                caption?.let {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = value?.takeIf { it.isFinite() }?.let { "${it.roundToInt()}" } ?: "––",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                unit,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun Controls(
    isRunning: Boolean,
    hasData: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onReset: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(
            onClick = if (isRunning) onStop else onStart,
            modifier = Modifier
                .weight(1f)
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Icon(
                if (isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(if (isRunning) "Pausar" else "Medir")
        }
        OutlinedButton(
            onClick = onReset,
            enabled = hasData,
            modifier = Modifier.height(52.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Icon(Icons.Rounded.Restore, contentDescription = "Reiniciar", Modifier.size(20.dp))
        }
    }
}

@Composable
private fun ClippingWarning() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.Warning,
                null,
                Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Saturación: el micrófono está al límite. El nivel real es mayor que el mostrado.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(
            message,
            Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@Composable
private fun PrivacyNote() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            Icons.Rounded.Lock,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "El audio no se graba. Cada fragmento se convierte en un nivel en decibelios " +
                "y se descarta. Todo el histórico se queda en este teléfono.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

private fun formatDuration(seconds: Long): String = when {
    seconds < 60 -> "$seconds s"
    seconds < 3600 -> "${seconds / 60} min ${seconds % 60} s"
    else -> "${seconds / 3600} h ${(seconds % 3600) / 60} min"
}
