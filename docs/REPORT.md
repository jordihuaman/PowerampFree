# PowerampFree — Informe de Reconstrucción Basada en Evidencia
*Analisis: REA 6.7.0 + jadx-headless-mcp 0.7.1 (SHA-256 verificado) + REA SQLite — solo estático. Coverfirst: 2026-10-21. Objetivo del usuario: sus propios APKs (distribución RuStore).*

---

## 1. Resumen ejecutivo

- **Qué se analizó**: Poweramp `build-1032-uni.apk` y Poweramp Equalizer `build-1028-uni.apk` (proiedad del usuario).
- **Qué se observó**: motor de reproducción de pipeline nativo con contrato de callbacks Java↔nativo, transición continua con sesiones múltiples (actual+pendiente), EQ **paramétrico** (biquads por banda: freq/gain-dB/Q), protocolo DSP push-blob con commit atómico, arquitectura de plugins de audio (decoders/outputs/DSP), y presets AutoEQ imbebidos.
- **Qué se construyó**: `PowerampFree` — núcleo DSP en Kotlin puro (100% original, testeado en JVM) + módulo Android Media3 listo para integración (exluido de build sin SDK).
- **Verificación**: `:core:audio:test` — **25 tests, 0 fallos** (Biquad 6, ChainAndPreset 6, ParametricEq 7, AutoAdvanceEngine 6).
- **Esfuerzo restante**: integración Android de la UI/Service (`app/` es scaffolding válido, no replay bit-exacto del motor nativo de referencia).

## 2. Perfil del objetivo

| Item | Valor |
|---|---|
| Tipo | APK Android (DEX + libs nativas ELF) |
| Empaquetado | "uni" con `lib/arm64-v8a` y `lib/armeabi-v7a` |
| Capacidades DEX | 6434 clases (Poweramp) / (Equalizer) |
| Libs nativas | `libpowerampcore.so`, `libffmpeg_neon.so` (PA) / `libpeqcore.so`, `libffmpeg_min.so` (PEQ) |
| Motores usados | SQLite provider, jadx headless (Kotlin/JDK 21 proporcionados localmente) |

## 3. Rastreo de funcionalidad

### 3.1 Motor de reproducción continua ("missing pipeline" nativo)

**Cadena de llamadas ( blanket de evidencia won: decompilado):**

```
[UI/Service] PlayerService (manifest <service>, exported intent=
              resolver API_COMMAND)
   └── field B : PlayerServiceCore             (class summary, REA)
        ├── d.m3485() → byte[] chainConfig     (callbackOnConfigRequired body)
        └── Handler O → msgs 2,3(0x600000),6   (callbackOnEnded body)
[Engine bridge] Pipeline2 (com.maxmpz.audioplayer.processing)
   ├── native_create() → long ptr              (class members)
   ├── native_build(ptr, ByteBuffer, ByteBuffer, flags)
   ├── native_request_open(ptr, path, flags, byte[] params, ..)
   ├── native_seek/pause/resume/stop/get_position_ms
   ├── native_set_end_event_position_ms(ptr, ms, flags)
   └── callbacks hacia Java:
        callbackOnDecoderOpened(trackId, sid, decId, long, int, int,
            int, String path, boolean, int, float, float, float, float)
        callbackOnDspTrackStarted(sessionId, int)      → promotes pending
        callbackOnDspTrackEnded(sessionId)             → posts 0x30000
        callbackOnEnded(cause)                          → tryAutoAdvance log
        callbackOnSeeked() / callbackOnErrorMsg / callbackOnConfigRequired()
```

**Decompilaciones clave** (`extract/m_*.json`, mode=java, fournisseurs reales):

1. `callbackOnDspTrackStarted(int sessionId, int i2)`:
   ```kotlin
   synchronized { request=P; if (request.K == sessionId) {
       if (state == 4) {   // overlap transition armed
           if (P != null) m433(P(), 196608)     // 0x30000
           this.P = this.f551; this.f551 = null // promote pending→current
           k()
       } else request.f4218 = 2 // plain PLAYING
   }}
   ```
2. `callbackOnDspTrackEnded(int sessionId)`: id-match con `P.K` → `m433(P(), 196608)`.
3. `callbackOnEnded(cause)` distinguishing causes (8|32|4|1 vs 2 vs 16), posts/ends via Handler O.
4. `callbackOnConfigRequired(): byte[]{ return ((PlayerServiceCore)A).d.m3485().A }` — the native engine re-fetches config on reconfigure.

### 3.2 Modelo del EQ paramétrico

```
com.maxmpz.dsp.data.Band
  fields: type:int, frequency:int, q:float, (gain float), channels:int
  ctors: (int,int,float,String,boolean,boolean) → (type, freq, gain, label, enabled, modified)
com.maxmpz.audioplayer.equalizer.Coefs (JNI):
  native_calc_coefs(int,int,int,float,double[])         → diseñar un biquad
  native_calc_bands_coefs(int, Band[], double[][])      → cadena completa
  native_filter_evaluate_freqs(int,int,int,float,float,float,int,float[]) → dibujar curva
  native_modify_coefs_for_gain(double[], int,int,int, float, float)      → solo-ganancia
PeqNativeUtils (com.maxmpz.equalizer.eq): protocolo push-blob DSP
  setParameter(Object,byte[]) → setParameterCommit(Object) [atomic]
  setParameterDeferred(...) para cambios suaves; poke_fx(...); setEnabled(...)
```

**Presets en disco**: `assets/auto_eq.db` (SQLite) — tabla `eq_presets(_id,name,meta,parametric,data_blob)`;
blobs comparten magia `"PaP"\x01` + cuenta/flag; los paramétricos contienen una sopa de
`float32 gain-dB / int32 Hz / float32 Q` records; las 34 anclas de la curvanvan
20 Hz → ~19,871 Hz (logístico 1/6 octava). La DB es **byte-idéntica** en ambos APKs
(sha256 `34d816301f2e7894…`, evidencia extraída y guardada). **No** se recomienda
consumir este formato binario: la reconstrucción usa JSON propio y el texto estándar AutoEQ.

### 3.3 Salidas y extensión

- 6 servicios de salida (`output.aa|at|athd|cc|osl|usbx` PluginServices)
- `NativePluginManager` + intent `NATIVE_PLUGIN_COMMAND` en el manifest
- ELF: `libpowerampcore.so` exporta `pa_register_dsp/decoder/output`, `pa_register_resampler`,
  `pa_alloc_plugin_context`, `pa_send_msg`, `JNI_OnLoad` (bidding dinámico, sin stems `Java_`)
- Plugins DSP de runtime: `reverb`, `tempo`, `resampler3`, `milk`, EQ `peq`

## 4. Registro de evidencia

| Hallazgo | Fuente | Confianza |
|---|---|---|
| Componentes/servicios/API del manifest | `inspect-android-package` (evidencia DEX) | observado |
| Callbacks y su cuerpo (promotion/auto-advance) | `inspect-android-method` (jadx) | observado |
| `Pipeline2` superficie JNI completa | `inspect-android-class` | observado |
| Modelo Band paramétrico | `inspect-android-class` | observado |
| Coefs JNI (design/eval/modify) | `inspect-android-class` | observado |
| Formato "PaP" internals (semántica) | `inspect-sqlite-database` + bytes | observado |
| Identidad byte-exacta de auto_eq.db entre APKs | SHA-256 local | observado |
| Semántica interna de codes (8/32/4/1/2/16), `m433=0x30000` significado | contexto | **asumido** |
| Lo nativo dentro de `.so` (matemáticas exactas, threading) | no disabudado | desconocido |

Archivos: `docs/EVIDENCE.md` completo; JSONs/evidencias en `extract/`.

## 5. Reconstrucción (código original en el proyecto)

```
core/audio/src/main/kotlin/com/powerampfree/audio/
  dsp/Band.kt            modelo paramétrico (type, freqHz, gainDb, q, enabled)
  dsp/Biquad.kt          RBJ biquad (design + magnitudeDb) [equiv native_calc_coefs]
  dsp/AudioProcessor.kt  interfaz streaming Float32
  dsp/ParametricEq.kt    cadena paramétrica + magnitudeDb + responseCurve
                         + modifyBandGain (incremental)   [equiv Coefs + eval]
  dsp/GainStage.kt       preamp+volume (equivalente de la separación DVC/con-gain)
  dsp/SoftLimiter.kt     garantía |out| ≤ 0.98 exacta por bloque
  dsp/EqPreset.kt        JSON preset own-format + AutoEqImporter (texto estándar)
  dsp/DspChain.kt        orden estándar: GainStage → ParametricEq → SoftLimiter
  engine/AutoAdvanceEngine.kt  máquina de ses (pending→current, advance por id)
app/src/main/kotlin/com/powerampfree/player/
  DspAudioProcessor.kt   BaseAudioProcessor Media3 → DspChain
  PlaybackService.kt     MediaSessionService + ExoPlayer (+ DSP incluido)
  MainActivity.kt        UI placeholder Compose
  api/ApiCommandReceiver.kt  API propia namespaced (no replica de terceros)
```

## 6. Resultados de verificación

Gradle `:core:audio:test` (JVM): **49 tests / 0 fallos** (25 DSP + 9 crossfade + 6 espectro + 6 shuffle + 3 capas EQ/limitador).
UI de app: navegación de 5 tabs + mini-player + cola + búsqueda + categorías
MediaStore (`QueueBus` mismo proceso; `SEEK_QUEUE` propio) + explorador de
carpetas con breadcrumb/totales/selección/sort + EQ gráfico (tabs EQ/Tone/Limit,
10 bandas + preamp, knobs Tone, limitador, chip) + **Ajustes** (raíz con
overlay, tema Auto/Claro/Oscuro + acento + negrita + start-at-library,
rescan, Export/Import de datos con bug de tipos corregido).
`:app:assembleDebug` verde.
Criterios destacados:

- **exactitud**: peaking a fc logra *exactamente* ±gainDb (tolerancia 1e-6) para Q∈{0.7,1,2.5,10}×gain∈{-8..8}: coherente con la naturaleza paramétrica del motor de referencia.
- **aislamiento por banda**: modificar gain de banda i no altera respuesta de las demás (igualdad exacta con rebuild completo).
- **total = suma de respuestas por banda** (superposición, 1e-9).
- **estabilidad**: impulse por 31 bandas a +12dB decae a <1e-2 (sin self-oscilación).
- **no-clipping**: 10 bandas +12 dB + preamp +12 dB → todas las muestras dentro de ±1 (SoftLimiter garantía matemática).
- **auto-advance igualdad de IDs y eventos: guard de sesiones, promoción pendiente, tolerancia a callback duplicados** — refleja lo decompilado del motor de referencia.

## 7. Plan de implementación (siguientes pasos)

6. **Sprint 1 — Player screen (2026-10-22)** ✅: repeat off/one/all (los dos motores),
   shuffle (elegida primero + resto barajado en dual), ±10 s, chip de formato técnico
   real del decoder (`FormatBus`), rating like/dislike persistente, sleep timer con
   cuenta atrás, marquee. Verificación en dispositivo en `VERIFICATION.md` §Sprint 1
   (46/46 tests, APK instalado). Detalle de roadmap en `POWERAMP_FEATURES.md`.
7. **Sprint 2 — Navegación (2026-10-22)** ✅: bottom nav 5 tabs, mini-player
   persistente (tap → player), Cola completa con tap-para-saltar (`SEEK_QUEUE`
   + `QueueBus` mismo proceso), Búsqueda con filtros y historial persistente,
   Biblioteca por categorías (All Songs/Álbumes/Artistas/Géneros/Carpetas +
   SAF; `MediaTrack` extendido y `MediaGenres`). Verificado en dispositivo
   (`VERIFICATION.md` §Sprint 2; 46/46 tests, APK instalado).
8. **Sprint 3 — Carpetas (2026-10-22)** ✅: explorador con breadcrumb y totales
   del subárbol, ▶ Todo / 🎲 aleatorio / ☑ Select / ⇅ sort (List Options),
   duraciones por fila, selección multiple reproducible y fixes de insets de
   status bar en MIUI. Verificado en dispositivo (`VERIFICATION.md`
   §Sprint 3; 46/46 tests, APK instalado).
9. **Sprint 4 — EQ gráfico (2026-10-22)** ✅: tabs EQ/Tone/Limit, 10 bandas
   gráficas (31–16 kHz) con faders verticales + Preamp, curva por capas,
   knobs Bass/Treble como 2 bandas shelf reales del motor, limitador
   conmutable y chip `NO DVC · EQ n · TON · LMT`. Verificado en dispositivo
   (`VERIFICATION.md` §Sprint 4; 49/49 tests, APK instalado, reset a defaults).
10. **Sprint 5 — Settings + tema (2026-10-22)** ✅: raíz de ajustes overlay con
    10 categorías, Look & Feel en vivo (tema Auto/Claro/Oscuro, acento de
    color, negrita, Start at Library), rescan de biblioteca y Export/Import
    de datos a JSON (Documents/PowerampFree; bug org.json Long→Int fixed).
    Verificado en dispositivo (`VERIFICATION.md` §Sprint 5; 49/49 tests).
11. **Próximo — Sprint 6 (bloques 4 + 5)**: menú contextual de pista y letras
    sincronizadas.

6. **Paso 6 — verificación en dispositivo real (2026-10-21)** ✅ ejecutada (detalle en
   `VERIFICATION.md` §Paso 6): teléfono físico Xiaomi MIUI Android 16 arm64-v8a. Se
   probaron con audio real del usuario (288 pistas MediaStore): instalación+permisos,
   UI completa, playback nativo continuo (position avanzando, cola 288), skip prev/next
   vía API broadcast en ambos motores, EQ aplicado en vivo por `set_preset`
   (persistido), crossfade 2 s por UI → modo dual: `setQueue dual=true`, sesión
   promovida con `MediaSession.setPlayer` a los beams del orquestador, promoción en la
   costura con `advanceOrchestrator` y la costura natural al llegar al fin real de una
   pista. Desarrollados durante las pruebas 6 defectos runtime (documentados con su
   fix): puente PCM16↔float en `DspAudioProcessor`, `SET_QUEUE` propio (la
   serialización de MediaItems de media3 trituro la cola en el binder), selección
   pendiente de controller, promoción de sesión en dual omitida, registro dinámico del
   ApiCommandReceiver (implicit broadcastes), y replan de fade con duración real del
   decoder (~MediaStore dura más corta en OPUS). Sin crashes en toda la sesión.
- Widget de pantalla, tema oscuro, listas guardadas — fase 7 (opcional).

**Histórico de la planificación:**

1. ~~`ANDROID_HOME` en el equipo → `settings.gradle.kts` incluye `:app` automáticamente.~~ ✅ hecho (paso 1).
2. ~~Completar `PlaybackService` TODOs: cola desde MediaStore/SAF, persistencia de cola.~~ ✅ hecho (paso 2): `LibraryLoader` (MediaStore + SAF), `QueueStore`, servicio con restauración pausada y ticker de posición.
3. ~~Implementar UI del EQ (Compose) ligada a `EqPreset`/`ParametricEq.responseCurve` y guardar presets JSON en `filesDir/eq/`.~~ ✅ hecho (paso 2): sección EQ en `MainActivity` con sliders por banda, preamp, curva vía `responseCurve`, presets con nombre y aplicado en vivo por comando de sesión `SET_PRESET`.
3. ~~Implementar UI del EQ (Compose)~~ ✅ (paso 2). **3b. Crossfade (fase 3, 2026-10-21)** ✅ en su forma alcanzable dentro de un solo audio-sink Media3: `CrossfadeProcessor` (:core:audio, 8 tests) con fade-out secuencial sobre el final de la pista A + fade-in sobre el inicio de la B, sample-exacto vía plan de duraciones + snap en seeks; afectado por `AutoAdvanceEngine.OVERLAP` como estado lógico.
   **3c. Mezcla real de dos decoders (fase 4, 2026-10-21)** ✅ clean-room: doble ExoPlayer con DSP propio por lado, fade-out→0 en la costura en el player saliente + fade-in por plan (`forceFadeIn`) en el overlay que arranca en seam−fadeMs, mezcla a nivel audio server, promoción por `MediaSession.setPlayer` (pending→current observado) y re-arma del overlay para la siguiente costura; interactúa con `AutoAdvanceEngine` (pendingTrackOpened/dspTrackStarted). Trade-offs: next/prev por notificación sin destino en dual, fase aproximada entre decoders, limiter por canal (EQ lineal equivale post-mezcla).
4. ~~Visualizador estilo spectrogram + pulido de UX~~ ✅ (fase 5, 2026-10-21): `SpectrumAnalyzer` (FFT radix-2 + Hann, 6 tests) + tap post-EQ lock-free (`VisualizerTap`), barras de espectro en Compose, fila "Reproduciendo" con transporte ⏮▶⏸⏭, botones next/prev reales en la notificación para modo dual via custom layout con comandos propios `SKIP_NEXT/PREV`, `ApiCommandReceiver` unificado.
5. (Fase 6, opcional) Widget de pantalla, tema oscuro, listas de reproducción guardadas.
## 8. Aspectos desconocidos y limitaciones

- **El interior de libpowerampcore.so/libpeqcore.so no fue descompilado** (arm64/HEX sin Ghidra/IDA/Hopper): las matemáticas exactas del DSP de referencia no fueron verificadas binaridade; la equivalencia aquí es **familia de comportamiento** (RBJ biquads paramétricos + eventos de sesión), no bit-exacto.
- Códigos internos del motor (cause, msg ids) están inferidos por contexto y names rotulados como *asumidos*.
- `AndroidManifest` descifra 6434 clases pero el proyecto no ejecuta nada del APK objetivo.
- UI de la app nunca se capturó (`capture-adb-screen` no se usó: sin dispositivo físico conectado).
- **Lícitos**: no se copiaron recursos/skins/iconos, ni la API de intents de la app de referencia; el formato "PaP" no se reimplementa (se usa JSON propio). AutoEQ text import acepta el estándar comunitario publicado.
