package io.github.kolossus03.sonometro.audio

import android.annotation.SuppressLint
import io.github.kolossus03.sonometro.data.SettingsRepository
import io.github.kolossus03.sonometro.service.LoggerState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/**
 * Única puerta de entrada a las lecturas del micrófono para la UI.
 *
 * El micrófono es exclusivo: si el servicio de registro ya lo tiene abierto, una
 * segunda captura devolvería silencio. Aquí se decide de dónde vienen las lecturas
 * y toda la UI comparte esa decisión, en vez de que cada pantalla la reimplemente
 * (y una de ellas se olvide).
 */
class ReadingSource(
    private val settings: SettingsRepository,
    private val meter: NoiseMeter,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    @SuppressLint("MissingPermission") // Las pantallas se abren tras conceder RECORD_AUDIO.
    fun readings(): Flow<Pair<Weighting, Reading>> {
        val weightingFlow = settings.settings.map { it.weighting }.distinctUntilChanged()
        return combine(weightingFlow, LoggerState.isRunning, ::Pair)
            .flatMapLatest { (weighting, serviceRunning) ->
                val source = if (serviceRunning) {
                    LoggerState.lastReading.filterNotNull()
                } else {
                    meter.readings(weighting)
                }
                source.map { weighting to it }
            }
    }
}
