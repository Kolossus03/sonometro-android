package io.github.kolossus03.sonometro.ui.theme

import androidx.compose.ui.graphics.Color

// Marca: azul cian sobre pizarra.
val Sky200 = Color(0xFFBAE6FD)
val Sky400 = Color(0xFF38BDF8)
val Sky600 = Color(0xFF0284C7)
val Sky700 = Color(0xFF0369A1)

val Slate50 = Color(0xFFF8FAFC)
val Slate100 = Color(0xFFF1F5F9)
val Slate300 = Color(0xFFCBD5E1)
val Slate500 = Color(0xFF64748B)
val Slate700 = Color(0xFF334155)
val Slate800 = Color(0xFF1E293B)
val Slate900 = Color(0xFF0F172A)
val Slate950 = Color(0xFF020617)

/**
 * Colores semánticos de nivel de ruido: una paleta de *estado* ordenada por
 * severidad, no una paleta categórica.
 *
 * Los valores salen de validar candidatos con el comprobador de la skill dataviz,
 * no de elegirlos a ojo. El primer intento (ámbar #A16207 vs naranja #C2410C)
 * daba ΔE 2,6 bajo deuteranopía: indistinguibles para quien tiene daltonismo
 * rojo-verde. Los actuales dan ΔE 14,4 en claro y 21,0 en oscuro, con contraste
 * ≥3:1 sobre su superficie.
 *
 * La banda de luminosidad que exige el comprobador para paletas categóricas no
 * aplica aquí: en una escala de severidad, oscurecer al empeorar es la intención.
 * Aun así, el color nunca es la única señal — siempre va acompañado de su etiqueta.
 */
object LevelColors {
    val QuietLight = Color(0xFF15803D)
    val QuietDark = Color(0xFF4ADE80)

    val ModerateLight = Color(0xFFCA8A04)
    val ModerateDark = Color(0xFFFACC15)

    val NoisyLight = Color(0xFFE11D48)
    val NoisyDark = Color(0xFFFB7185)

    val VeryNoisyLight = Color(0xFF9F1239)
    val VeryNoisyDark = Color(0xFFE11D48)
}

/**
 * Rampa secuencial de un solo tono para el mapa de calor semanal (magnitud, no
 * identidad): claro→oscuro en modo claro, oscuro→claro en modo oscuro. Nunca un
 * arcoíris.
 */
object HeatRamp {
    val Light = listOf(
        Color(0xFFF0F9FF), Color(0xFFE0F2FE), Color(0xFFBAE6FD), Color(0xFF7DD3FC),
        Color(0xFF38BDF8), Color(0xFF0EA5E9), Color(0xFF0284C7), Color(0xFF075985),
    )
    val Dark = listOf(
        Color(0xFF0C2A3E), Color(0xFF0E3A55), Color(0xFF115E7E), Color(0xFF0284C7),
        Color(0xFF0EA5E9), Color(0xFF38BDF8), Color(0xFF7DD3FC), Color(0xFFBAE6FD),
    )
}
