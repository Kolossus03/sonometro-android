package io.github.kolossus03.sonometro.ui.meter

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kolossus03.sonometro.service.LoggerState
import io.github.kolossus03.sonometro.service.NoiseLoggerService

/**
 * Interruptor del registro en segundo plano.
 *
 * El permiso de notificaciones no condiciona que el servicio funcione, solo que su
 * notificación sea visible. Se pide, pero el registro arranca igual si se deniega:
 * bloquearlo sería castigar al usuario por una preferencia cosmética.
 */
@Composable
fun RecordingCard(
    intervalSeconds: Int,
    label: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isRunning by LoggerState.isRunning.collectAsStateWithLifecycle()
    val written by LoggerState.samplesWritten.collectAsStateWithLifecycle()

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { NoiseLoggerService.start(context) }

    fun toggle(enabled: Boolean) {
        if (!enabled) {
            NoiseLoggerService.stop(context)
            return
        }
        if (context.needsNotificationPermission()) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            NoiseLoggerService.start(context)
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isRunning) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Bedtime,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Registrar en segundo plano",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        "Sigue midiendo con la pantalla apagada",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = isRunning, onCheckedChange = ::toggle)
            }

            Spacer(Modifier.height(10.dp))
            val where = label.ifBlank { "sin etiqueta" }
            val status = if (isRunning) {
                "Guardando una muestra cada ${formatInterval(intervalSeconds)} · " +
                    "$written guardadas · $where"
            } else {
                "Una muestra cada ${formatInterval(intervalSeconds)} · $where"
            }
            Text(
                status,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun Context.needsNotificationPermission(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED

private fun formatInterval(seconds: Int): String = when {
    seconds < 60 -> "$seconds s"
    seconds % 60 == 0 -> "${seconds / 60} min"
    else -> "${seconds / 60} min ${seconds % 60} s"
}
