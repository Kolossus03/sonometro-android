package io.github.kolossus03.sonometro.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Filtro de muestra a muestra, con estado. No es thread-safe. */
interface SampleFilter {
    fun process(x: Double): Double
    fun reset()
}

/** Biquad en forma directa II transpuesta. */
class Biquad(
    private val b0: Double,
    private val b1: Double,
    private val b2: Double,
    private val a1: Double,
    private val a2: Double,
) {
    private var z1 = 0.0
    private var z2 = 0.0

    fun process(x: Double): Double {
        val y = b0 * x + z1
        z1 = b1 * x - a1 * y + z2
        z2 = b2 * x - a2 * y
        return y
    }

    fun reset() {
        z1 = 0.0
        z2 = 0.0
    }

    /** |H(e^{jw})| a la frecuencia dada, para normalizar la ganancia de la cascada. */
    fun magnitudeAt(freqHz: Double, sampleRate: Double): Double {
        val w = 2.0 * PI * freqHz / sampleRate
        // e^{-jw} = cos w - j sin w
        val numRe = b0 + b1 * cos(w) + b2 * cos(2 * w)
        val numIm = -(b1 * sin(w) + b2 * sin(2 * w))
        val denRe = 1.0 + a1 * cos(w) + a2 * cos(2 * w)
        val denIm = -(a1 * sin(w) + a2 * sin(2 * w))
        return hypot(numRe, numIm) / hypot(denRe, denIm)
    }

    companion object {
        /**
         * Transformada bilineal de un biquad analógico
         *   H(s) = (b2·s² + b1·s + b0) / (a2·s² + a1·s + a0)
         * al dominio z. Sin prewarping: las frecuencias de esquina de la ponderación A
         * están muy por debajo de Nyquist a 44,1 kHz, salvo la de 12194 Hz, cuyo
         * desplazamiento queda dentro de la tolerancia de la clase 2.
         */
        fun fromAnalog(
            b2: Double, b1: Double, b0: Double,
            a2: Double, a1: Double, a0: Double,
            sampleRate: Double,
        ): Biquad {
            val k = 2.0 * sampleRate
            val kk = k * k
            val bb0 = b2 * kk + b1 * k + b0
            val bb1 = 2.0 * (b0 - b2 * kk)
            val bb2 = b2 * kk - b1 * k + b0
            val aa0 = a2 * kk + a1 * k + a0
            val aa1 = 2.0 * (a0 - a2 * kk)
            val aa2 = a2 * kk - a1 * k + a0
            return Biquad(bb0 / aa0, bb1 / aa0, bb2 / aa0, aa1 / aa0, aa2 / aa0)
        }
    }
}

/** Cascada de biquads con una ganancia escalar de normalización. */
class BiquadCascade(private val sections: List<Biquad>, private val gain: Double) : SampleFilter {
    override fun process(x: Double): Double {
        var y = x * gain
        for (s in sections) y = s.process(y)
        return y
    }

    override fun reset() = sections.forEach { it.reset() }
}

private object Identity : SampleFilter {
    override fun process(x: Double) = x
    override fun reset() = Unit
}

/**
 * Ponderación frecuencial.
 *
 * [A] es la curva estándar IEC 61672 clase 2, que aproxima la sensibilidad del oído
 * humano a niveles bajos. Es la que usan las ordenanzas de ruido, así que es el
 * modo por defecto.
 * [Z] es la respuesta plana (sin ponderar), útil para comparar con otras herramientas.
 */
enum class Weighting(val label: String, val unit: String) {
    A("Ponderación A", "dBA"),
    Z("Sin ponderar (Z)", "dBZ");

    fun createFilter(sampleRate: Int): SampleFilter = when (this) {
        Z -> Identity
        A -> buildAWeighting(sampleRate.toDouble())
    }
}

/**
 * Ponderación A analógica:
 *
 *            (2π·f4)² · s⁴
 * H(s) = ───────────────────────────────────────
 *        (s + 2π·f1)² (s + 2π·f2)(s + 2π·f3)(s + 2π·f4)²
 *
 * Se factoriza en tres biquads, cada uno pasa por la transformada bilineal, y la
 * cascada se normaliza a 0 dB en 1 kHz (que es la definición de la curva).
 */
private fun buildAWeighting(sampleRate: Double): BiquadCascade {
    val w1 = 2.0 * PI * 20.598997
    val w2 = 2.0 * PI * 107.65265
    val w3 = 2.0 * PI * 737.86223
    val w4 = 2.0 * PI * 12194.217

    val sections = listOf(
        // s² / (s + w1)²
        Biquad.fromAnalog(1.0, 0.0, 0.0, 1.0, 2.0 * w1, w1 * w1, sampleRate),
        // s² / ((s + w2)(s + w3))
        Biquad.fromAnalog(1.0, 0.0, 0.0, 1.0, w2 + w3, w2 * w3, sampleRate),
        // w4² / (s + w4)²
        Biquad.fromAnalog(0.0, 0.0, w4 * w4, 1.0, 2.0 * w4, w4 * w4, sampleRate),
    )

    val magAt1k = sections.fold(1.0) { acc, s -> acc * s.magnitudeAt(1000.0, sampleRate) }
    return BiquadCascade(sections, gain = 1.0 / magAt1k)
}
