package io.github.kolossus03.sonometro

import android.app.Application
import android.content.Context
import io.github.kolossus03.sonometro.audio.NoiseMeter
import io.github.kolossus03.sonometro.audio.ReadingSource
import io.github.kolossus03.sonometro.data.MeasurementRepository
import io.github.kolossus03.sonometro.data.SettingsRepository
import io.github.kolossus03.sonometro.data.db.AppDatabase
import io.github.kolossus03.sonometro.export.ExportManager

/**
 * Contenedor de dependencias.
 *
 * Sin Hilt a propósito: con este número de dependencias, un localizador explícito
 * se lee mejor que un grafo generado, y evita añadir un segundo procesador de
 * anotaciones sobre el que ya trae Room.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val settings: SettingsRepository by lazy { SettingsRepository(appContext) }
    val noiseMeter: NoiseMeter by lazy { NoiseMeter(appContext) }
    val readingSource: ReadingSource by lazy { ReadingSource(settings, noiseMeter) }

    private val database: AppDatabase by lazy { AppDatabase.get(appContext) }
    val measurements: MeasurementRepository by lazy {
        MeasurementRepository(database.measurementDao())
    }
    val exports: ExportManager by lazy { ExportManager(appContext) }
}

class SonometroApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
