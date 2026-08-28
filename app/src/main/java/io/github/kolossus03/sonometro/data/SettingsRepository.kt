package io.github.kolossus03.sonometro.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.kolossus03.sonometro.audio.Weighting
import io.github.kolossus03.sonometro.core.Calibration
import io.github.kolossus03.sonometro.core.Thresholds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "sonometro_settings")

/** Ajustes del usuario. Un solo objeto para que la UI observe un único flujo. */
data class Settings(
    val calibration: Calibration = Calibration.Uncalibrated,
    val thresholds: Thresholds = Thresholds.Default,
    val weighting: Weighting = Weighting.A,
    val samplingIntervalSeconds: Int = DEFAULT_INTERVAL_SECONDS,
    val locationLabel: String = "",
) {
    companion object {
        const val DEFAULT_INTERVAL_SECONDS = 60
        val INTERVAL_CHOICES = listOf(10, 30, 60, 300, 600)
    }
}

class SettingsRepository(private val context: Context) {

    val settings: Flow<Settings> = context.dataStore.data.map { prefs ->
        Settings(
            calibration = Calibration(
                offsetDb = prefs[OFFSET_DB] ?: Calibration.DEFAULT_OFFSET_DB,
                isCalibrated = prefs[IS_CALIBRATED] ?: false,
            ),
            thresholds = Thresholds(
                quietBelow = prefs[QUIET_BELOW] ?: Thresholds.Default.quietBelow,
                moderateBelow = prefs[MODERATE_BELOW] ?: Thresholds.Default.moderateBelow,
                noisyBelow = prefs[NOISY_BELOW] ?: Thresholds.Default.noisyBelow,
            ),
            weighting = prefs[WEIGHTING]?.let { name ->
                Weighting.entries.firstOrNull { it.name == name }
            } ?: Weighting.A,
            samplingIntervalSeconds = prefs[INTERVAL_SECONDS] ?: Settings.DEFAULT_INTERVAL_SECONDS,
            locationLabel = prefs[LOCATION_LABEL] ?: "",
        )
    }

    /**
     * Fijar el offset marca la app como calibrada. Volver al valor de fábrica la
     * devuelve a "estimación relativa": no queremos que un reset silencioso deje
     * la etiqueta "Calibrado" mintiendo en un informe.
     */
    suspend fun setCalibrationOffset(offsetDb: Double) {
        val clamped = offsetDb.coerceIn(Calibration.MIN_OFFSET_DB, Calibration.MAX_OFFSET_DB)
        context.dataStore.edit { prefs ->
            prefs[OFFSET_DB] = clamped
            prefs[IS_CALIBRATED] = true
        }
    }

    suspend fun resetCalibration() {
        context.dataStore.edit { prefs ->
            prefs[OFFSET_DB] = Calibration.DEFAULT_OFFSET_DB
            prefs[IS_CALIBRATED] = false
        }
    }

    suspend fun setThresholds(thresholds: Thresholds) {
        context.dataStore.edit { prefs ->
            prefs[QUIET_BELOW] = thresholds.quietBelow
            prefs[MODERATE_BELOW] = thresholds.moderateBelow
            prefs[NOISY_BELOW] = thresholds.noisyBelow
        }
    }

    suspend fun setWeighting(weighting: Weighting) {
        context.dataStore.edit { it[WEIGHTING] = weighting.name }
    }

    suspend fun setSamplingInterval(seconds: Int) {
        context.dataStore.edit { it[INTERVAL_SECONDS] = seconds }
    }

    suspend fun setLocationLabel(label: String) {
        context.dataStore.edit { it[LOCATION_LABEL] = label.trim() }
    }

    private companion object {
        val OFFSET_DB = doublePreferencesKey("offset_db")
        val IS_CALIBRATED = booleanPreferencesKey("is_calibrated")
        val QUIET_BELOW = doublePreferencesKey("quiet_below")
        val MODERATE_BELOW = doublePreferencesKey("moderate_below")
        val NOISY_BELOW = doublePreferencesKey("noisy_below")
        val WEIGHTING = stringPreferencesKey("weighting")
        val INTERVAL_SECONDS = intPreferencesKey("interval_seconds")
        val LOCATION_LABEL = stringPreferencesKey("location_label")
    }
}
