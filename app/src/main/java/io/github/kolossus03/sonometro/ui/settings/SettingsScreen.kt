@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package io.github.kolossus03.sonometro.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.kolossus03.sonometro.audio.Weighting
import io.github.kolossus03.sonometro.core.Calibration
import io.github.kolossus03.sonometro.core.Thresholds
import io.github.kolossus03.sonometro.core.formatDb
import io.github.kolossus03.sonometro.data.Settings
import io.github.kolossus03.sonometro.ui.AppViewModelProvider
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LifecycleResumeEffect(Unit) {
        viewModel.startListening()
        onPauseOrDispose { viewModel.stopListening() }
    }

    state.message?.let { text ->
        LaunchedEffect(text) {
            snackbarHostState.showSnackbar(text)
            viewModel.consumeMessage()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(4.dp))

        CalibrationSection(
            currentDb = state.currentDb,
            unit = state.unit,
            calibration = state.calibration,
            onCalibrate = viewModel::calibrateTo,
            onOffsetChange = viewModel::setOffset,
            onReset = viewModel::resetCalibration,
        )

        RecordingSection(
            settings = state.settings,
            labelText = state.labelText,
            onIntervalChange = viewModel::setInterval,
            onLabelChange = viewModel::setLabel,
            onWeightingChange = viewModel::setWeighting,
        )

        ThresholdsSection(
            thresholds = state.settings.thresholds,
            unit = state.unit,
            onChange = viewModel::setThresholds,
        )

        DataSection(
            count = state.measurementCount,
            exporting = state.exporting,
            onExportCsv = viewModel::exportCsv,
            onExportPdf = { viewModel.exportPdf() },
            onDeleteAll = viewModel::deleteAll,
        )

        SnackbarHost(snackbarHostState)
        Spacer(Modifier.height(24.dp))
    }

    // El chooser se lanza como efecto y se consume, para que no reaparezca al girar.
    state.pendingShare?.let { result ->
        LaunchedEffect(result.uri) {
            context.startActivity(viewModel.shareIntent(result))
            viewModel.consumeShare()
        }
    }
}

@Composable
private fun SectionCard(title: String, subtitle: String? = null, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun CalibrationSection(
    currentDb: Double?,
    unit: String,
    calibration: Calibration,
    onCalibrate: (Double) -> Unit,
    onOffsetChange: (Double) -> Unit,
    onReset: () -> Unit,
) {
    var reference by remember { mutableStateOf("") }

    SectionCard(
        title = "Calibración",
        subtitle = "El micrófono de un móvil no viene calibrado. Sin un sonómetro de " +
            "referencia, el valor absoluto en dB es una estimación; las variaciones " +
            "relativas entre horas y días sí son fiables.",
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Science, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                calibration.statusLabel,
                style = MaterialTheme.typography.labelLarge,
                color = if (calibration.isCalibrated) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }

        Spacer(Modifier.height(12.dp))
        Text("Lectura actual", style = MaterialTheme.typography.labelSmall)
        Text(
            "${currentDb.formatDb()} $unit",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )

        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = reference,
                onValueChange = { reference = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("dB del sonómetro de referencia") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            Button(
                onClick = {
                    reference.toDoubleOrNull()?.let(onCalibrate)
                    reference = ""
                },
                enabled = reference.toDoubleOrNull() != null && currentDb != null,
            ) { Text("Calibrar") }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "Ajuste manual del offset: ${"%.1f".format(calibration.offsetDb)} dB",
            style = MaterialTheme.typography.labelMedium,
        )
        Slider(
            value = calibration.offsetDb.toFloat(),
            onValueChange = { onOffsetChange(it.toDouble()) },
            valueRange = Calibration.MIN_OFFSET_DB.toFloat()..Calibration.MAX_OFFSET_DB.toFloat(),
        )
        Text(
            "Corresponde al nivel en dB que representa el fondo de escala del micrófono.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(6.dp))
        TextButton(onClick = onReset) { Text("Restablecer a sin calibrar") }
    }
}

@Composable
private fun RecordingSection(
    settings: Settings,
    labelText: String,
    onIntervalChange: (Int) -> Unit,
    onLabelChange: (String) -> Unit,
    onWeightingChange: (Weighting) -> Unit,
) {
    SectionCard(title = "Registro", subtitle = "Cada cuánto se guarda una muestra y de dónde.") {
        Text("Intervalo de muestreo", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Settings.INTERVAL_CHOICES.forEach { seconds ->
                FilterChip(
                    selected = settings.samplingIntervalSeconds == seconds,
                    onClick = { onIntervalChange(seconds) },
                    label = { Text(formatInterval(seconds)) },
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = labelText,
            onValueChange = onLabelChange,
            label = { Text("Etiqueta de ubicación") },
            placeholder = { Text("salón, oficina, dormitorio…") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(16.dp))
        Text("Ponderación frecuencial", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Weighting.entries.forEach { weighting ->
                FilterChip(
                    selected = settings.weighting == weighting,
                    onClick = { onWeightingChange(weighting) },
                    label = { Text(weighting.label) },
                )
            }
        }
        Text(
            "La ponderación A imita la sensibilidad del oído y es la que usan las " +
                "ordenanzas de ruido. Cambiarla invalida la comparación con lo ya registrado.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun ThresholdsSection(
    thresholds: Thresholds,
    unit: String,
    onChange: (Thresholds) -> Unit,
) {
    SectionCard(
        title = "Umbrales",
        subtitle = "Definen la clasificación y qué cuenta como superación en el informe.",
    ) {
        ThresholdSlider("Silencioso por debajo de", thresholds.quietBelow, unit, 25f..95f) {
            onChange(thresholds.copy(quietBelow = it))
        }
        ThresholdSlider("Moderado por debajo de", thresholds.moderateBelow, unit, 30f..100f) {
            onChange(thresholds.copy(moderateBelow = it))
        }
        ThresholdSlider("Ruidoso por debajo de", thresholds.noisyBelow, unit, 40f..110f) {
            onChange(thresholds.copy(noisyBelow = it))
        }
        Text(
            "Superación del umbral: pico ≥ ${thresholds.exceedanceLimit.roundToInt()} $unit",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ThresholdSlider(
    label: String,
    value: Double,
    unit: String,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Double) -> Unit,
) {
    Column(Modifier.padding(bottom = 4.dp)) {
        Text(
            "$label ${value.roundToInt()} $unit",
            style = MaterialTheme.typography.labelMedium,
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toDouble()) },
            valueRange = range,
        )
    }
}

@Composable
private fun DataSection(
    count: Int,
    exporting: Boolean,
    onExportCsv: () -> Unit,
    onExportPdf: () -> Unit,
    onDeleteAll: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }

    SectionCard(
        title = "Datos",
        subtitle = "$count muestras guardadas. Todo se queda en este teléfono.",
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onExportCsv, enabled = !exporting, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.Description, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("CSV")
            }
            Button(onClick = onExportPdf, enabled = !exporting, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.PictureAsPdf, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Informe PDF")
            }
        }
        if (exporting) {
            Spacer(Modifier.height(10.dp))
            CircularProgressIndicator(Modifier.size(20.dp))
        }

        Spacer(Modifier.height(10.dp))
        TextButton(onClick = { confirmDelete = true }) {
            Icon(Icons.Rounded.DeleteForever, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Borrar todo el histórico")
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("¿Borrar el histórico?") },
            text = { Text("Se eliminarán las $count muestras. No se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteAll()
                    confirmDelete = false
                }) { Text("Borrar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") }
            },
        )
    }
}

private fun formatInterval(seconds: Int): String = when {
    seconds < 60 -> "$seconds s"
    seconds % 60 == 0 -> "${seconds / 60} min"
    else -> "${seconds / 60} min ${seconds % 60} s"
}
