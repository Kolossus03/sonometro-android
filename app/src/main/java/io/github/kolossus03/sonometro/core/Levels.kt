package io.github.kolossus03.sonometro.core

/** Clasificación cualitativa de un nivel sonoro. */
enum class NoiseClass(val label: String, val hint: String) {
    QUIET("Silencioso", "Buen momento para abrir la ventana"),
    MODERATE("Moderado", "Ruido de fondo normal"),
    NOISY("Ruidoso", "Molesto de forma sostenida"),
    VERY_NOISY("Muy ruidoso", "Dañino con exposición prolongada"),
}

/**
 * Umbrales en dB que separan las cuatro clases. Configurables porque lo que cuenta
 * como "ruidoso" depende de la ordenanza municipal y del uso del espacio.
 *
 * Los valores por defecto siguen la guía de la OMS para ruido ambiental
 * (~45 dB de noche, ~55-60 dB de día en zona residencial).
 */
data class Thresholds(
    val quietBelow: Double = 45.0,
    val moderateBelow: Double = 60.0,
    val noisyBelow: Double = 75.0,
) {
    fun classify(db: Double): NoiseClass = when {
        db < quietBelow -> NoiseClass.QUIET
        db < moderateBelow -> NoiseClass.MODERATE
        db < noisyBelow -> NoiseClass.NOISY
        else -> NoiseClass.VERY_NOISY
    }

    /** El umbral a partir del cual una lectura cuenta como "superación" en el informe. */
    val exceedanceLimit: Double get() = noisyBelow

    companion object {
        val Default = Thresholds()
    }
}

/**
 * Convierte dBFS (relativo al fondo de escala del ADC) a dB SPL estimado.
 *
 * Un micrófono de móvil no viene calibrado: no existe un mapa conocido entre
 * el fondo de escala digital y una presión sonora en pascales. [offsetDb] es ese
 * mapa, y hasta que el usuario lo fija contra un sonómetro de referencia, la
 * lectura es una *estimación relativa*, no una medida absoluta. [isCalibrated]
 * existe para que la UI y las exportaciones nunca puedan mentir sobre eso.
 */
data class Calibration(
    val offsetDb: Double = DEFAULT_OFFSET_DB,
    val isCalibrated: Boolean = false,
) {
    fun toSpl(dbfs: Double): Double = dbfs + offsetDb

    val statusLabel: String
        get() = if (isCalibrated) "Calibrado" else "Estimación relativa · sin calibrar"

    companion object {
        /**
         * 0 dBFS ≈ 94 dB SPL. 94 dB es el nivel de un calibrador acústico estándar
         * (1 Pa a 1 kHz) y sitúa el fondo de escala cerca de donde saturan los
         * micrófonos MEMS típicos. Es un punto de partida plausible, no una medida.
         */
        const val DEFAULT_OFFSET_DB = 94.0
        const val MIN_OFFSET_DB = 60.0
        const val MAX_OFFSET_DB = 130.0

        val Uncalibrated = Calibration()
    }
}
