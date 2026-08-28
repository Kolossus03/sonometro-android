package io.github.kolossus03.sonometro.core

import kotlin.math.log10
import kotlin.math.pow

/**
 * Acumulador de nivel continuo equivalente (Leq).
 *
 * El promedio de un nivel en decibelios NO es la media aritmética de los
 * decibelios: hay que promediar la energía (la presión al cuadrado) y volver a
 * pasar a dB. Un pico de 90 dB durante un segundo pesa mucho más que 60 dB
 * durante diez, y la media aritmética lo escondería.
 *
 * Trabaja en dBFS; la conversión a SPL es un desplazamiento y conmuta con el Leq.
 */
class LeqAccumulator {
    private var sumOfPowers = 0.0
    private var count = 0L
    private var peak = Double.NEGATIVE_INFINITY
    private var min = Double.POSITIVE_INFINITY

    val samples: Long get() = count
    val isEmpty: Boolean get() = count == 0L

    fun add(dbfs: Double) {
        sumOfPowers += 10.0.pow(dbfs / 10.0)
        count++
        if (dbfs > peak) peak = dbfs
        if (dbfs < min) min = dbfs
    }

    /** Nivel continuo equivalente, en dBFS. */
    fun leqDbfs(): Double =
        if (count == 0L) Double.NaN else 10.0 * log10(sumOfPowers / count)

    fun peakDbfs(): Double = if (count == 0L) Double.NaN else peak

    fun minDbfs(): Double = if (count == 0L) Double.NaN else min

    fun reset() {
        sumOfPowers = 0.0
        count = 0L
        peak = Double.NEGATIVE_INFINITY
        min = Double.POSITIVE_INFINITY
    }

    companion object {
        /** Leq de una lista de niveles ya en dB (mismo razonamiento energético). */
        fun of(levelsDb: List<Double>): Double {
            if (levelsDb.isEmpty()) return Double.NaN
            val sum = levelsDb.sumOf { 10.0.pow(it / 10.0) }
            return 10.0 * log10(sum / levelsDb.size)
        }

        /** Leq ponderado por el número de muestras que representa cada nivel. */
        fun ofWeighted(levels: List<Pair<Double, Long>>): Double {
            val totalWeight = levels.sumOf { it.second }
            if (totalWeight == 0L) return Double.NaN
            val sum = levels.sumOf { (db, w) -> 10.0.pow(db / 10.0) * w }
            return 10.0 * log10(sum / totalWeight)
        }
    }
}
