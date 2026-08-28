package io.github.kolossus03.sonometro.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Verifica la curva de ponderación A midiendo la ganancia real del filtro sobre
 * tonos puros, no inspeccionando sus coeficientes. Si la implementación fuese
 * incorrecta, estos números se irían.
 */
class WeightingTest {

    private val sampleRate = 44_100

    /** Ganancia del filtro en dB para un seno de [freq] Hz, descartando el transitorio. */
    private fun gainDb(filter: SampleFilter, freq: Double): Double {
        filter.reset()
        val settleSamples = sampleRate / 2
        val measureSamples = sampleRate * 2
        val omega = 2.0 * PI * freq / sampleRate

        for (n in 0 until settleSamples) {
            filter.process(sin(omega * n))
        }
        var sumSquares = 0.0
        for (n in settleSamples until settleSamples + measureSamples) {
            val y = filter.process(sin(omega * n))
            sumSquares += y * y
        }
        val rmsOut = sqrt(sumSquares / measureSamples)
        val rmsIn = 1.0 / sqrt(2.0) // RMS de un seno de amplitud 1
        return 20.0 * log10(rmsOut / rmsIn)
    }

    /** Tabla de la IEC 61672-1, ponderación A, respuesta nominal en dB. */
    private val iecTable = listOf(
        31.5 to -39.4,
        63.0 to -26.2,
        125.0 to -16.1,
        250.0 to -8.6,
        500.0 to -3.2,
        1000.0 to 0.0,
        2000.0 to 1.2,
        4000.0 to 1.0,
        8000.0 to -1.1,
    )

    @Test
    fun `la ponderacion A sigue la curva nominal de la IEC 61672`() {
        val filter = Weighting.A.createFilter(sampleRate)
        println("  Hz  | IEC nominal | medido | error")
        for ((freq, expected) in iecTable) {
            val actual = gainDb(filter, freq)
            println(
                "%7.1f | %11.1f | %6.2f | %+.2f".format(freq, expected, actual, actual - expected)
            )
            assertEquals(
                "A-weighting a $freq Hz: esperado $expected dB, obtenido ${"%.2f".format(actual)} dB",
                expected,
                actual,
                1.0, // holgado para 8 kHz, donde la bilineal sin prewarping ya desvía
            )
        }
    }

    @Test
    fun `la ponderacion A es exactamente 0 dB en 1 kHz`() {
        val filter = Weighting.A.createFilter(sampleRate)
        assertEquals(0.0, gainDb(filter, 1000.0), 0.05)
    }

    @Test
    fun `la ponderacion A atenua fuertemente los graves`() {
        val filter = Weighting.A.createFilter(sampleRate)
        assertTrue("20 Hz debe quedar por debajo de -45 dB", gainDb(filter, 20.0) < -45.0)
    }

    @Test
    fun `la ponderacion Z no altera la senal`() {
        val filter = Weighting.Z.createFilter(sampleRate)
        for (freq in listOf(50.0, 1000.0, 10_000.0)) {
            assertEquals(0.0, gainDb(filter, freq), 1e-9)
        }
    }

    @Test
    fun `el suelo de dBFS esta acotado`() {
        assertEquals(NoiseMeter.FLOOR_DBFS, NoiseMeter.meanSquareToDbfs(0.0), 1e-9)
        assertEquals(NoiseMeter.FLOOR_DBFS, NoiseMeter.meanSquareToDbfs(-1.0), 1e-9)
        // Fondo de escala: media cuadrática 1.0 => 0 dBFS
        assertEquals(0.0, NoiseMeter.meanSquareToDbfs(1.0), 1e-9)
    }
}
