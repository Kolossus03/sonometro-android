# Sonómetro

An Android sound level meter with history. It logs ambient noise through the day, tells you
which hours your street or your office is loud, and exports the data to CSV and to a PDF
report you can attach to a noise complaint.

**Audio is never recorded.** Each 100 ms block is turned into a decibel number and the
signal is discarded. The whole history stays on the phone — no accounts, no cloud, no
telemetry.

> *Spanish README: [`README.es.md`](README.es.md).*

---

## Why another sound meter

Because most of them get the acoustics wrong, quietly. This one:

- **implements A-weighting for real** — a cascade of three biquads, each analog section
  mapped to the z-domain with the bilinear transform at the actual sample rate, normalized
  to 0 dB at 1 kHz — and verifies it against the IEC 61672 table by *measuring the filter*,
  not by asserting on coefficients;
- **averages energy, not decibels**, everywhere, because the arithmetic mean of dB values is
  meaningless;
- **stores raw dBFS and applies the calibration offset at presentation time**, so calibrating
  the app fixes the entire history retroactively;
- **is honest about what it cannot do**: an uncalibrated phone microphone cannot give you an
  absolute SPL, and the UI says so where you cannot miss it.

If you only want the DSP, `audio/Weighting.kt` and `core/Leq.kt` are self-contained and MIT
licensed. Take them.

---

## Build and install

**Requirements**

| | Version used |
|---|---|
| JDK | 17 (Temurin 17.0.19) |
| Android SDK | `compileSdk` 35, `build-tools` 35.0.0 |
| Gradle | 8.11.1 (via the wrapper — no separate install needed) |
| Device | Android 7.0+ (`minSdk` 24) |

Create `local.properties` at the repo root pointing at your SDK:

```properties
sdk.dir=C\:\\Users\\YourUser\\Android
```

**Build and install**

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

**Tests** (they check the A-weighting filter against the IEC 61672 table):

```bash
./gradlew :app:testDebugUnitTest
```

---

## Design decisions

### Levels are stored in dBFS, not dB SPL

This is the decision everything else hangs from. The calibration offset is a property of
*this phone's* microphone, not of the moment a measurement was taken. By storing the raw
level relative to full scale (dBFS) and applying the offset only when presenting it,
calibrating the app **corrects the entire history retroactively**. If dB SPL were stored,
every row would freeze the offset in force when it was written, and the file would end up
with two scales mixed together — unfixable after the fact.

Verified on device: after calibrating, history peaks went from 75 to 91 dBA and threshold
exceedances from 2 to 35, without rewriting a single row.

### A-weighting implemented properly

The A-weighting curve (IEC 61672) is built as a cascade of three biquads, transforming each
analog section to the z-domain with the bilinear transform at the real sample rate, and
normalizing to 0 dB at 1 kHz. `WeightingTest` measures the filter's gain by pushing pure
sines through it and compares against the standard's nominal table:

```
    Hz  | IEC nominal | measured | error
   31.5 |       -39.4 |   -39.53 | -0.13
   63.0 |       -26.2 |   -26.22 | -0.02
  125.0 |       -16.1 |   -16.19 | -0.09
  250.0 |        -8.6 |    -8.68 | -0.08
  500.0 |        -3.2 |    -3.25 | -0.05
 1000.0 |         0.0 |     0.00 | +0.00
 2000.0 |         1.2 |     1.20 | +0.00
 4000.0 |         1.0 |     0.92 | -0.08
 8000.0 |        -1.1 |    -1.81 | -0.71
```

Within class 1 tolerance across the band. The 8 kHz deviation is bilinear warping near
Nyquist — expected, and documented in the code.

### Audio capture

`AudioRecord` at 44.1 kHz mono PCM16, requesting the `UNPROCESSED` source if the vendor
exposes it and falling back to `VOICE_RECOGNITION` if not. Both avoid the automatic gain
control and noise suppression that the ordinary `MIC` source applies, which would make the
measurement worthless. `AutomaticGainControl` and `NoiseSuppressor` effects are also
explicitly disabled on the session.

Weighted RMS is computed over each 100 ms block. What is displayed carries *Fast* time
weighting (τ = 125 ms); what feeds the Leq is the unsmoothed block value.

### The average is an Leq, not a mean of decibels

Averaging decibels arithmetically means nothing: you have to average energy and convert back
to dB. A 90 dB peak lasting a minute dominates the whole hour even if the rest is silence,
and an arithmetic mean would hide exactly the event the user is trying to document. Every
aggregation (hourly, daily, in the report) goes through `LeqAccumulator.ofWeighted`,
weighting each interval by its sample count. There is deliberately no SQL query anywhere
that does `AVG(leqDbfs)`.

### Foreground service, not WorkManager

WorkManager cannot go below 15 minutes for periodic work and guarantees no punctuality. This
needs the microphone held open continuously and sampled every few seconds — exactly the use
case for a `microphone`-type foreground service, with its persistent notification and a
`PARTIAL_WAKE_LOCK` so the CPU does not sleep with the screen off.

### A single audio source

The microphone is an exclusive resource. If the service is logging and the user opens the
meter screen, a second `AudioRecord` on the same mic would return silence. `ReadingSource`
centralizes that decision: with the service active, the UI subscribes to the stream the
service publishes instead of opening its own capture.

### Compose Canvas instead of MPAndroidChart

MPAndroidChart is a View-based library with no releases since 2019 and requires adding
JitPack as a repository. The chart renderers (`chart/Charts.kt`) draw against
`android.graphics.Canvas`, so **the same code produces the on-screen chart and the one in
the PDF report**. A chart drawn twice is a chart that eventually diverges.

### No Hilt

An explicit `AppContainer` on the `Application` class covers the five dependencies that
exist. Hilt would add a second annotation processor on top of Room's, in exchange for
nothing.

### Colors validated, not eyeballed

The green→amber→red ramp is a *status* palette, and it was validated with a contrast and
color-blindness checker. The first attempt (`#A16207` amber vs `#C2410C` orange) gave ΔE 2.6
under deuteranopia: **indistinguishable** for someone with red-green color blindness. The
final palette gives ΔE 14.4 in light theme and 21.0 in dark, with ≥3:1 contrast against its
surface. Even so, color is never the only signal — it always comes with its text label.

The weekly heatmap uses a single-hue sequential ramp (magnitude, not identity) with a numeric
scale. Cells with no data are drawn as a gap, never as "quiet": not having measured is not
the same as there having been no noise.

---

## Architecture

```
audio/      AudioRecord, A-weighting (biquads + bilinear), ReadingSource
core/       Leq, thresholds, calibration, analysis (hours, peaks, summary)
data/       Room (entity/DAO/DB), repositories, settings DataStore
service/    NoiseLoggerService (foreground, microphone type)
chart/      Renderers over android.graphics.Canvas (screen + PDF)
export/     CsvExporter, PdfReportGenerator, ExportManager (FileProvider)
ui/         Compose: meter, history, settings, theme, permissions
```

MVVM with `StateFlow`, single module. Room + KSP, DataStore Preferences, Navigation Compose,
Material 3.

---

## Verified on a real device

All of the following was checked on a **Xiaomi 2407FPN8EG running Android 16 (SDK 36)**:

- The meter responds to sound: observed range 26 to 91 dBA, with the classification and color
  changing accordingly.
- Background logging for 29 minutes with the app in the foreground, and **3 minutes with the
  screen genuinely asleep** (`mWakefulness=Asleep`). In both cases every 60 s interval stored
  `samples` between 598 and 603 — about 600 blocks of 100 ms: **not a single audio block was
  dropped**.
- Persistent notification (`ONGOING|NO_CLEAR|SILENT`) showing the current level, the
  classification and the "(uncalibrated)" warning. Service with
  `foregroundServiceType=microphone` (`types=0x80`).
- Calibration: entering a reference reading recalculates the offset and marks the app as
  calibrated; the whole history rescales instantly.
- CSV export (52 rows, with `estado_calibracion` and `offset_db` columns) and a two-page PDF
  report, both opened and reviewed.
- Light and dark themes, charts included.

---

## Limitations

**Calibration is the important limitation, and it cannot be solved in software.**

A phone microphone does not ship calibrated: there is no known mapping between the ADC's full
scale and a sound pressure in pascals. The app starts with a 94 dB offset (0 dBFS ≈ 94 dB
SPL, the level of a standard acoustic calibrator), which is a plausible starting point, **not
a measurement**. Until you fix that offset against a reference sound level meter, the
absolute value may be off by several decibels.

What *is* reliable without calibration are the **relative variations**: which hour is louder
than another, which day was worst, how many times a threshold you set yourself was exceeded.
That is what the app is for, and it is why the UI and the exports say "relative estimate ·
uncalibrated" somewhere you cannot ignore.

Other known limitations:

- **It is not a substitute for a certified sound level meter** in a legal procedure. The PDF
  report is for documenting a pattern with data, not for certifying a level in court.
- The microphone saturates around 100–110 dB SPL. The app detects saturation and warns about
  it in the UI and in the report, but in that regime the reading is a lower bound.
- **Xiaomi / HyperOS** kills background processes aggressively. To log through the night, go
  to Settings → Apps → Sonómetro and enable *Autostart*, and lock the app in the recents
  screen (padlock icon). Also disable battery restrictions for the app.
- Peak events are detected per interval, not per individual acoustic event: with a 1-minute
  interval, two motorbikes passing in that minute count as one exceedance.
- The daily chart draws a gap where there was no logging instead of interpolating. That is
  deliberate, but with long intervals the curve looks fragmented.

### Note on ADB with Xiaomi

HyperOS blocks `adb shell input` and `adb shell pm grant` unless you enable **Settings →
Additional settings → Developer options → "USB debugging (Security settings)"**, which
requires a Xiaomi account, a SIM and an internet connection. It also ignores `KEYCODE_POWER`
and `KEYCODE_SLEEP` over ADB: to turn the screen off you need the physical button.

---

## Roadmap

1. **Calibration assisted by a reference tone.** Play a known tone through the phone's own
   speaker and use the response to estimate the offset without an external meter. It does not
   give absolute accuracy, but it bounds things much better than the default 94 dB.
2. **Statistical percentiles (L10, L50, L90).** These are what noise ordinances actually cite,
   more than plain Leq. Requires storing a histogram per interval, not just Leq/peak/min.
3. **Acoustic event detection** instead of per-interval exceedances: group contiguous peaks
   into a single event with its duration.
4. **Multiple locations at once**, with the label as a first-class dimension in the history
   and comparisons between them (living room vs. bedroom).
5. **Retention and automatic purging** of the history; right now it grows without bound.
6. **Resume logging after a reboot** with a `BOOT_COMPLETED` receiver, optional.
7. Instrumented tests for the Room layer and the chart renderers (bitmap comparison).

---

## License

MIT — see [`LICENSE`](LICENSE).
