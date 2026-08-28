package io.github.kolossus03.sonometro.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.github.kolossus03.sonometro.MainActivity
import io.github.kolossus03.sonometro.R
import io.github.kolossus03.sonometro.SonometroApp
import io.github.kolossus03.sonometro.audio.Reading
import io.github.kolossus03.sonometro.core.Calibration
import io.github.kolossus03.sonometro.core.LeqAccumulator
import io.github.kolossus03.sonometro.core.Thresholds
import io.github.kolossus03.sonometro.data.db.MeasurementEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Estado observable del servicio.
 *
 * [lastReading] existe porque el micrófono es un recurso exclusivo: si el servicio
 * está registrando y el usuario abre la pantalla del medidor, un segundo
 * AudioRecord sobre el mismo micro devolvería silencio o fallaría. Con el servicio
 * activo la UI se cuelga de este flujo en vez de abrir su propia captura.
 */
object LoggerState {
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _samplesWritten = MutableStateFlow(0)
    val samplesWritten: StateFlow<Int> = _samplesWritten.asStateFlow()

    private val _lastReading = MutableStateFlow<Reading?>(null)
    val lastReading: StateFlow<Reading?> = _lastReading.asStateFlow()

    internal fun setRunning(running: Boolean) {
        _isRunning.value = running
        if (!running) {
            _samplesWritten.value = 0
            _lastReading.value = null
        }
    }

    internal fun incrementWritten() {
        _samplesWritten.value += 1
    }

    internal fun publish(reading: Reading) {
        _lastReading.value = reading
    }
}

/**
 * Registra el nivel de ruido en segundo plano.
 *
 * Es un foreground service y no WorkManager porque WorkManager no baja de 15 min
 * en trabajo periódico y no garantiza puntualidad; aquí se muestrea cada pocos
 * segundos y hay que sostener el micrófono abierto de forma continua.
 */
class NoiseLoggerService : Service() {

    private val scope = CoroutineScope(SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
        startLogging()
        return START_STICKY
    }

    @SuppressLint("MissingPermission") // La UI no inicia el servicio sin RECORD_AUDIO.
    private fun startLogging() {
        if (LoggerState.isRunning.value) return
        LoggerState.setRunning(true)

        startForegroundCompat(buildNotification(currentDb = null, calibration = Calibration.Uncalibrated))
        acquireWakeLock()

        val container = (application as SonometroApp).container

        scope.launch {
            // Lectura real de DataStore antes de nada: un stateIn con valor inicial
            // por defecto devolvería los ajustes de fábrica en el primer first().
            val initial = container.settings.settings.first()
            // A partir de ahí, un único observador. Leer DataStore en cada bloque de
            // 100 ms sería absurdo; la etiqueta y la calibración salen del snapshot.
            val settingsState = container.settings.settings
                .stateIn(scope, SharingStarted.Eagerly, initial)

            val intervalMs = initial.samplingIntervalSeconds * 1000L
            val weighting = initial.weighting

            val interval = LeqAccumulator()
            var intervalStartMs = System.currentTimeMillis()
            var intervalClipped = false
            var lastNotificationMs = 0L

            runCatching {
                container.noiseMeter.readings(weighting).collect { reading: Reading ->
                    LoggerState.publish(reading)
                    interval.add(reading.blockDbfs)
                    if (reading.clipped) intervalClipped = true

                    val now = reading.timestampMs
                    val current = settingsState.value

                    // La notificación se refresca despacio: cada 100 ms sería un
                    // derroche de batería y el sistema la limita igualmente.
                    if (now - lastNotificationMs >= NOTIFICATION_REFRESH_MS) {
                        lastNotificationMs = now
                        updateNotification(
                            currentDb = current.calibration.toSpl(reading.fastDbfs),
                            calibration = current.calibration,
                            thresholds = current.thresholds,
                        )
                    }

                    if (now - intervalStartMs >= intervalMs && !interval.isEmpty) {
                        container.measurements.insert(
                            MeasurementEntity(
                                timestampMs = now,
                                leqDbfs = interval.leqDbfs(),
                                peakDbfs = interval.peakDbfs(),
                                minDbfs = interval.minDbfs(),
                                samples = interval.samples.toInt(),
                                weighting = weighting.name,
                                label = current.locationLabel,
                                clipped = intervalClipped,
                            )
                        )
                        LoggerState.incrementWritten()
                        interval.reset()
                        intervalClipped = false
                        intervalStartMs = now
                    }
                }
            }.onFailure {
                stopSelf()
            }
        }
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    /**
     * Con la pantalla apagada, AudioRecord no garantiza que la CPU siga despierta.
     * Sin este wakelock el muestreo se vuelve intermitente en cuanto el teléfono
     * entra en suspensión profunda.
     */
    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "sonometro:logger").apply {
            setReferenceCounted(false)
            acquire(WAKELOCK_TIMEOUT_MS)
        }
    }

    private fun updateNotification(
        currentDb: Double,
        calibration: Calibration,
        thresholds: Thresholds,
    ) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(currentDb, calibration, thresholds))
    }

    private fun buildNotification(
        currentDb: Double?,
        calibration: Calibration,
        thresholds: Thresholds = Thresholds.Default,
    ): Notification {
        createChannel()

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, NoiseLoggerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )

        val title = currentDb?.let {
            "${it.roundToInt()} dB · ${thresholds.classify(it).label}"
        } ?: "Iniciando medición…"

        val suffix = if (calibration.isCalibrated) "" else " (sin calibrar)"
        val written = LoggerState.samplesWritten.value

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText("Registrando ruido ambiental$suffix · $written muestras")
            .setSmallIcon(R.drawable.ic_stat_meter)
            .setContentIntent(openApp)
            .addAction(0, "Detener", stop)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Registro de ruido",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Notificación persistente mientras se registra el nivel de ruido."
                setShowBadge(false)
            }
        )
    }

    override fun onDestroy() {
        LoggerState.setRunning(false)
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "noise_logger"
        private const val NOTIFICATION_ID = 1001
        /**
         * Cada notify() hace que el sistema tome un wakelock propio. A 2 s eran 30
         * por minuto durante toda la noche; a 5 s la notificación sigue viva y el
         * coste baja a la mitad larga.
         */
        private const val NOTIFICATION_REFRESH_MS = 5_000L

        /** 8 horas: una noche de registro. Más allá, Android lo revoca igualmente. */
        private const val WAKELOCK_TIMEOUT_MS = 8 * 60 * 60 * 1000L

        const val ACTION_STOP = "io.github.kolossus03.sonometro.STOP_LOGGING"

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, NoiseLoggerService::class.java),
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, NoiseLoggerService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
