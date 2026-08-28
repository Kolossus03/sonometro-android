package io.github.kolossus03.sonometro.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Una muestra agregada del intervalo de registro.
 *
 * Los niveles se guardan en **dBFS**, no en dB SPL. El offset de calibración es una
 * propiedad del micrófono de este teléfono, no del momento de la medida: si el
 * usuario calibra la semana que viene contra un sonómetro de referencia, todo el
 * histórico debe corregirse solo. Guardar SPL congelaría un offset equivocado en
 * cada fila y dejaría el archivo con dos escalas mezcladas, imposible de arreglar.
 *
 * La ponderación sí se guarda por fila: dBA y dBZ no son comparables entre sí, y
 * mezclarlos en una media sería un error silencioso.
 */
@Entity(
    tableName = "measurements",
    indices = [Index("timestampMs")],
)
data class MeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Instante de cierre del intervalo, epoch millis. */
    val timestampMs: Long,
    /** Nivel continuo equivalente del intervalo, en dBFS. */
    val leqDbfs: Double,
    /** Máximo nivel Fast del intervalo, en dBFS. */
    val peakDbfs: Double,
    /** Mínimo nivel Fast del intervalo, en dBFS. */
    val minDbfs: Double,
    /** Lecturas de 100 ms agregadas. Pondera el Leq al promediar intervalos. */
    val samples: Int,
    /** "A" o "Z". */
    val weighting: String,
    /** Etiqueta manual: "salón", "oficina"… Cadena vacía si no hay. */
    val label: String,
    /** true si algún bloque del intervalo saturó el ADC. */
    val clipped: Boolean,
)
