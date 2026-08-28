# Sonómetro

> *English README: [`README.md`](README.md).*

Medidor de ruido ambiental con histórico, para Android. Registra el nivel de sonido a lo
largo del día, dice a qué horas es ruidoso tu barrio o tu oficina, y exporta los datos a
CSV y a un informe PDF que puedes adjuntar a una reclamación.

El audio **no se graba nunca**. Cada bloque de 100 ms se convierte en un número de
decibelios y la señal se descarta. Todo el histórico vive en el teléfono.

---

## Build e instalación

**Requisitos**

| | Versión usada |
|---|---|
| JDK | 17 (Temurin 17.0.19) |
| Android SDK | `compileSdk` 35, `build-tools` 35.0.0 |
| Gradle | 8.11.1 (vía wrapper, no hace falta instalarlo) |
| Dispositivo | Android 7.0+ (`minSdk` 24) |

Crea `local.properties` en la raíz apuntando a tu SDK:

```properties
sdk.dir=C\:\\Users\\TuUsuario\\Android
```

**Compilar e instalar**

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

**Tests** (verifican el filtro de ponderación A contra la tabla de la IEC 61672):

```bash
./gradlew :app:testDebugUnitTest
```

---

## Decisiones técnicas

### Los niveles se guardan en dBFS, no en dB SPL

Es la decisión de la que cuelga todo lo demás. El offset de calibración es una propiedad
del micrófono de *este* teléfono, no del instante en que se tomó la medida. Guardando el
nivel crudo relativo al fondo de escala (dBFS) y aplicando el offset solo al presentarlo,
calibrar la app **corrige el histórico entero de forma retroactiva**. Si se guardara dB SPL,
cada fila congelaría el offset vigente al escribirse y el archivo acabaría con dos escalas
mezcladas, imposible de arreglar después.

Verificado en el dispositivo: tras calibrar, los picos del histórico pasaron de 75 a
91 dBA y las superaciones del umbral de 2 a 35, sin reescribir una sola fila.

### Ponderación A implementada de verdad

La curva de ponderación A (IEC 61672) se construye como una cascada de tres biquads,
transformando cada sección analógica al dominio z con la bilineal a la frecuencia de
muestreo real, y normalizando a 0 dB en 1 kHz. `WeightingTest` mide la ganancia del filtro
pasando senos puros y la compara con la tabla nominal de la norma:

```
    Hz  | IEC nominal | medido | error
   31,5 |       -39,4 | -39,53 | -0,13
   63,0 |       -26,2 | -26,22 | -0,02
  125,0 |       -16,1 | -16,19 | -0,09
  250,0 |        -8,6 |  -8,68 | -0,08
  500,0 |        -3,2 |  -3,25 | -0,05
 1000,0 |         0,0 |   0,00 | +0,00
 2000,0 |         1,2 |   1,20 | +0,00
 4000,0 |         1,0 |   0,92 | -0,08
 8000,0 |        -1,1 |  -1,81 | -0,71
```

Dentro de la tolerancia de clase 1 en toda la banda. La desviación de 8 kHz es el warping
de la bilineal cerca de Nyquist, esperada y documentada en el código.

### Captura de audio

`AudioRecord` a 44,1 kHz mono PCM16, pidiendo la fuente `UNPROCESSED` si el fabricante la
expone y cayendo a `VOICE_RECOGNITION` si no. Ambas evitan el control automático de
ganancia y la supresión de ruido que el `MIC` normal aplica, y que harían la medida
inservible. Además se desactivan explícitamente los efectos `AutomaticGainControl` y
`NoiseSuppressor` sobre la sesión.

Sobre cada bloque de 100 ms se calcula el RMS ponderado. Lo que se muestra lleva
ponderación temporal *Fast* (τ = 125 ms); lo que alimenta el Leq es el valor del bloque sin
suavizar.

### El promedio es un Leq, no una media de decibelios

Promediar decibelios aritméticamente no significa nada: hay que promediar la energía y
volver a pasar a dB. Un pico de 90 dB durante un minuto domina la hora entera aunque el
resto sea silencio, y la media aritmética escondería justo el evento que el usuario quiere
documentar. Todas las agregaciones (por hora, por día, del informe) pasan por
`LeqAccumulator.ofWeighted`, ponderando cada intervalo por su número de muestras. No existe
ninguna consulta SQL que haga `AVG(leqDbfs)`, a propósito.

### Foreground service, no WorkManager

WorkManager no baja de 15 minutos en trabajo periódico y no garantiza puntualidad. Aquí hay
que sostener el micrófono abierto de forma continua y muestrear cada pocos segundos. Es
exactamente el caso de uso de un foreground service de tipo `microphone`, con su
notificación persistente y un `PARTIAL_WAKE_LOCK` para que la CPU no se duerma con la
pantalla apagada.

### Una sola fuente de audio

El micrófono es un recurso exclusivo. Si el servicio está registrando y el usuario abre la
pantalla del medidor, un segundo `AudioRecord` sobre el mismo micro devolvería silencio.
`ReadingSource` centraliza esa decisión: con el servicio activo, la UI se cuelga del flujo
que este publica en lugar de abrir su propia captura.

### Compose Canvas en lugar de MPAndroidChart

MPAndroidChart es una librería de Views sin releases desde 2019 y obliga a añadir JitPack
como repositorio. Los renderizadores de gráficas (`chart/Charts.kt`) dibujan contra
`android.graphics.Canvas`, de modo que **el mismo código produce la gráfica de la pantalla y
la del informe PDF**. Una gráfica dibujada dos veces es una gráfica que acaba divergiendo.

### Sin Hilt

Un `AppContainer` explícito en la clase `Application` cubre las cinco dependencias que hay.
Hilt añadiría un segundo procesador de anotaciones sobre el que ya trae Room, a cambio de
nada.

### Colores validados, no elegidos a ojo

La rampa verde→ámbar→rojo es una paleta de **estado**, y se validó con el comprobador de
contraste y daltonismo de la skill `dataviz`. El primer intento (`#A16207` ámbar vs
`#C2410C` naranja) daba ΔE 2,6 bajo deuteranopía: **indistinguibles** para quien tiene
daltonismo rojo-verde. La paleta final da ΔE 14,4 en tema claro y 21,0 en oscuro, con
contraste ≥3:1 sobre su superficie. Aun así, el color nunca es la única señal: siempre va
acompañado de su etiqueta textual.

El mapa de calor semanal usa una rampa secuencial de un solo tono (magnitud, no identidad),
con escala numérica. Las celdas sin datos se pintan como hueco, nunca como "silencioso":
no haber medido no es lo mismo que no haber ruido.

---

## Arquitectura

```
audio/      AudioRecord, ponderación A (biquads + bilineal), ReadingSource
core/       Leq, umbrales, calibración, análisis (horas, picos, resumen)
data/       Room (entidad/DAO/DB), repositorios, DataStore de ajustes
service/    NoiseLoggerService (foreground, tipo microphone)
chart/      Renderizadores sobre android.graphics.Canvas (pantalla + PDF)
export/     CsvExporter, PdfReportGenerator, ExportManager (FileProvider)
ui/         Compose: medidor, histórico, ajustes, tema, permisos
```

MVVM con `StateFlow`, módulo único. Room + KSP, DataStore Preferences,
Navigation Compose, Material 3.

---

## Verificado en dispositivo real

Todo lo siguiente se comprobó en un **Xiaomi 2407FPN8EG con Android 16 (SDK 36)**:

- El medidor reacciona al sonido: rango real observado de 26 a 91 dBA, con la clasificación
  y el color cambiando en consecuencia.
- Registro en segundo plano durante 29 minutos con la app en primer plano, y **3 minutos con
  la pantalla realmente dormida** (`mWakefulness=Asleep`). En ambos casos cada intervalo de
  60 s guardó `samples` entre 598 y 603, es decir ~600 bloques de 100 ms: **no se perdió ni
  un bloque de audio**.
- Notificación persistente (`ONGOING|NO_CLEAR|SILENT`) con el nivel actual, la clasificación
  y el aviso "(sin calibrar)". Servicio con `foregroundServiceType=microphone` (`types=0x80`).
- Calibración: introducir la lectura de una referencia recalcula el offset y marca la app
  como calibrada; el histórico completo se reescala al instante.
- Exportación de CSV (52 filas, con columnas `estado_calibracion` y `offset_db`) y de informe
  PDF de dos páginas, ambos abiertos y revisados.
- Modo claro y oscuro, incluidas las gráficas.

---

## Limitaciones

**La calibración es la limitación importante, y no se puede resolver desde el software.**

El micrófono de un teléfono no viene calibrado de fábrica: no existe un mapa conocido entre
el fondo de escala del ADC y una presión sonora en pascales. La app arranca con un offset de
94 dB (0 dBFS ≈ 94 dB SPL, el nivel de un calibrador acústico estándar), que es un punto de
partida plausible, **no una medida**. Hasta que fijes ese offset contra un sonómetro de
referencia, el valor absoluto puede desviarse varios decibelios.

Lo que sí es fiable sin calibrar son las **variaciones relativas**: qué hora es más ruidosa
que otra, qué día fue peor, cuántas veces se superó un umbral que tú mismo definiste. Para
eso sirve la app, y por eso la UI y las exportaciones dicen "estimación relativa · sin
calibrar" en un sitio donde no se puede ignorar.

Otras limitaciones conocidas:

- **No sustituye a un sonómetro homologado** en un procedimiento legal. El informe PDF sirve
  para documentar un patrón con datos, no para acreditar un nivel ante un juzgado.
- El micrófono satura en torno a los 100–110 dB SPL. La app detecta la saturación y lo avisa
  tanto en la UI como en el informe, pero en ese régimen la lectura es un límite inferior.
- **Xiaomi / HyperOS** mata agresivamente los procesos en segundo plano. Para registrar toda
  la noche, ve a Ajustes → Aplicaciones → Sonómetro y activa *Inicio automático*, y en la
  pantalla de tareas recientes bloquea la app (icono del candado). Además, desactiva las
  restricciones de batería para la app.
- Los eventos de pico se detectan a nivel de intervalo, no de evento acústico individual: si
  el intervalo es de 1 minuto, dos motos que pasen en ese minuto cuentan como una superación.
- La gráfica del día dibuja un hueco donde no hubo registro, en vez de interpolar. Es
  deliberado, pero con intervalos largos la curva se ve fragmentada.

### Nota sobre ADB en Xiaomi

HyperOS bloquea `adb shell input` y `adb shell pm grant` salvo que actives
**Ajustes → Ajustes adicionales → Opciones de desarrollador → "Depuración USB (Ajustes de
seguridad)"**, que exige cuenta Xiaomi, SIM y conexión a internet. También ignora
`KEYCODE_POWER` y `KEYCODE_SLEEP` por ADB: para apagar la pantalla hay que usar el botón
físico.

---

## Próximos pasos

1. **Calibración asistida por tono de referencia.** Reproducir un tono conocido por el
   altavoz del propio teléfono y usar la respuesta para estimar el offset sin necesidad de un
   sonómetro externo. No da precisión absoluta, pero acota mucho mejor que el 94 dB por
   defecto.
2. **Percentiles estadísticos (L10, L50, L90).** Son lo que citan de verdad las ordenanzas de
   ruido, más que el Leq a secas. Requiere guardar un histograma por intervalo, no solo
   Leq/pico/mínimo.
3. **Detección de eventos acústicos**, en lugar de superaciones por intervalo: agrupar picos
   contiguos en un solo evento con su duración.
4. **Múltiples ubicaciones a la vez**, con la etiqueta como dimensión de primera clase en el
   histórico y comparativas entre ellas (salón vs. dormitorio).
5. **Retención y purga automática** del histórico; ahora mismo crece sin límite.
6. **Reanudar el registro tras un reinicio** con un `BOOT_COMPLETED` receiver, opcional.
7. Tests instrumentados de la capa Room y de los renderizadores de gráficas (comparación de
   bitmaps).
