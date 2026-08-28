package io.github.kolossus03.sonometro.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max

/**
 * Una lectura del micrófono, en dBFS (decibelios relativos a fondo de escala).
 *
 * El offset de calibración NO se aplica aquí a propósito: el medidor entrega la
 * magnitud física que puede medir sin ambigüedad, y la conversión a dB SPL
 * ocurre en un único sitio ([io.github.kolossus03.sonometro.data.Calibration]). Así no hay
 * dos caminos por los que el offset pueda desincronizarse.
 */
data class Reading(
    val timestampMs: Long,
    /** Nivel con ponderación temporal Fast (τ = 125 ms). Es lo que se muestra. */
    val fastDbfs: Double,
    /** Nivel del bloque sin suavizar. Es lo que alimenta el Leq. */
    val blockDbfs: Double,
    /** true si el ADC saturó: la lectura es un límite inferior, no una medida. */
    val clipped: Boolean,
)

class MicrophoneUnavailableException(message: String) : Exception(message)

/**
 * Lee el micrófono y produce niveles. Nunca escribe la señal a disco ni la
 * retiene: cada bloque se convierte en un escalar y se descarta.
 */
class NoiseMeter(private val context: Context) {

    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun readings(weighting: Weighting = Weighting.A): Flow<Reading> = flow {
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, ENCODING)
        if (minBuffer <= 0) {
            throw MicrophoneUnavailableException("El dispositivo no soporta 44,1 kHz mono PCM16.")
        }
        val bufferBytes = max(minBuffer, WINDOW_SAMPLES * BYTES_PER_SAMPLE * 4)

        val record = AudioRecord(pickAudioSource(), SAMPLE_RATE, CHANNEL_CONFIG, ENCODING, bufferBytes)
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            throw MicrophoneUnavailableException(
                "No se pudo abrir el micrófono. ¿Lo está usando otra app?"
            )
        }

        disableSignalProcessing(record.audioSessionId)

        val filter = weighting.createFilter(SAMPLE_RATE)
        val buffer = ShortArray(WINDOW_SAMPLES)
        // Media cuadrática con ponderación temporal Fast, en unidades de fondo de escala.
        var fastMeanSquare = 0.0
        var primed = false

        record.startRecording()
        try {
            while (currentCoroutineContext().isActive) {
                var offset = 0
                while (offset < WINDOW_SAMPLES) {
                    val n = record.read(buffer, offset, WINDOW_SAMPLES - offset)
                    if (n <= 0) throw MicrophoneUnavailableException("Lectura de audio fallida (código $n).")
                    offset += n
                }

                var sumSquares = 0.0
                var clipped = false
                for (sample in buffer) {
                    if (abs(sample.toInt()) >= CLIP_THRESHOLD) clipped = true
                    val y = filter.process(sample.toDouble() / FULL_SCALE)
                    sumSquares += y * y
                }
                val blockMeanSquare = sumSquares / WINDOW_SAMPLES

                fastMeanSquare = if (!primed) {
                    primed = true
                    blockMeanSquare
                } else {
                    fastMeanSquare + (blockMeanSquare - fastMeanSquare) * FAST_ALPHA
                }

                emit(
                    Reading(
                        timestampMs = System.currentTimeMillis(),
                        fastDbfs = meanSquareToDbfs(fastMeanSquare),
                        blockDbfs = meanSquareToDbfs(blockMeanSquare),
                        clipped = clipped,
                    )
                )
            }
        } finally {
            runCatching { record.stop() }
            record.release()
        }
    }.flowOn(Dispatchers.Default)

    /**
     * UNPROCESSED entrega la señal sin AGC ni supresión de ruido, que es lo único
     * medible. Si el fabricante no la expone, VOICE_RECOGNITION es el siguiente
     * mejor: por contrato no aplica AGC. MIC es el último recurso y sus lecturas
     * están comprimidas.
     */
    private fun pickAudioSource(): Int {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val supportsUnprocessed =
            am.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
        return if (supportsUnprocessed) {
            MediaRecorder.AudioSource.UNPROCESSED
        } else {
            MediaRecorder.AudioSource.VOICE_RECOGNITION
        }
    }

    /** Cinturón y tirantes: algunos equipos activan estos efectos igualmente. */
    private fun disableSignalProcessing(sessionId: Int) {
        runCatching {
            if (AutomaticGainControl.isAvailable()) {
                AutomaticGainControl.create(sessionId)?.enabled = false
            }
        }
        runCatching {
            if (NoiseSuppressor.isAvailable()) {
                NoiseSuppressor.create(sessionId)?.enabled = false
            }
        }
    }

    companion object {
        const val SAMPLE_RATE = 44_100
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val BYTES_PER_SAMPLE = 2
        private const val FULL_SCALE = 32_768.0
        private const val CLIP_THRESHOLD = 32_700

        /** 100 ms por bloque: 10 lecturas por segundo. */
        const val WINDOW_SAMPLES = SAMPLE_RATE / 10

        /** Suelo de la escala. Por debajo, el ruido propio del ADC domina. */
        const val FLOOR_DBFS = -120.0

        /** Coeficiente del filtro exponencial Fast: 1 - e^(-Δt/τ), Δt=100 ms, τ=125 ms. */
        private val FAST_ALPHA = 1.0 - exp(-0.100 / 0.125)

        fun meanSquareToDbfs(meanSquare: Double): Double {
            if (meanSquare <= 0.0) return FLOOR_DBFS
            val db = 10.0 * log10(meanSquare)
            return if (db < FLOOR_DBFS) FLOOR_DBFS else db
        }
    }
}
