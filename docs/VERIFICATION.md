# Verification plan — PowerampFree DSP core tests

The analyzed reference cannot run here (it is an Android binary), so
equivalence claims are *behavioral properties* traced from REA findings to
tests that any environment can execute:

## Paso 6 (2026-10-21): verificación en dispositivo real

**Dispositivo**: teléfono físico Xiaomi (tanzanite/24117RN76L, MIUI), Android 16
(API 36), arm64-v8a, serial `di4djffyeukjgaw4j`. Biblioteca real del usuario:
288 pistas (grabaciones OPUS de RecForge, audio real — no sintético).

### Instalación y arranque
- `adb install -r app\build\outputs\apk\debug\app-debug.apk` → `Success`.
- Permisos concedidos: `READ_MEDIA_AUDIO`, `POST_NOTIFICATIONS`.
- La app lanza, renderiza toda la UI (fila Reproduciendo + transporte
  ⏮▶⏸⏭, espectro, EQ paramétrico + curva, crossfade slider, presets,
  biblioteca 288 pistas). Sin FATAL ni ANR durante toda la sesión.
- Capturas: `extract\ui_step6_screen1.png`, `ui_step6_playing.png`,
  `ui_step6_notification.png`.

### Defectos encontrados en runtime (y corregidos)
1. **`UnhandledAudioFormatException` (MediaCodecAudioRenderer error)** — el
   decodificador entrega PCM 16-bit y el DSP solo aceptaba float.
   **Fix**: `DspAudioProcessor` acepta PCM16 y hace convert 16↔float en
   `queueInput` (el sink sigue viendo el encoding de entrada).
2. **Cola truncada del controlador** — el tap enviaba 288 items
   (`playTracks n=288`) y la sesión recibía `onSetMediaItems items=1 start=-1`
   (la serialización media3 de MediaItems se degradó al cruzar el binder).
   **Fix**: comando propio `com.powerampfree.player.SET_QUEUE` con JSON
   compacto (uri/title/artist/album/duración); la sesión ya no depende de la
   serialización de MediaItems de media3.
3. **Selección previa a la conexión del `MediaController`** — los taps antes de
   la conexión async eran no-ops silenciosos. **Fix**: cola pendiente de
   selección + flush al conectar; log de fallos de conexión.
4. **Sesión desacoplada del motor audible en modo dual** — `playCurrentItem`/
   `advanceOrchestrator`/`finishFade` reemplazaban el ExoPlayer activo sin
   llamar a `MediaSession.setPlayer`, así que la sesión seguía mostrando el
   player viejo (STOPPED/288 items) mientras el audio corría por el nuevo.
   **Fix**: promoción explícita con `mediaSession.setPlayer(beam.player)` en
   los tres puntos de cambio de beam.
5. **Broadcast implícito no recibida por el receiver de manifest** (restrictión
   Android 8+ para custom actions) — la API externa solo recibía intents
   explícitos. **Fix**: registro dinámico en `PlaybackService.onCreate`
   (`RECEIVER_EXPORTED`) además del manifest entry.
6. **Duraciones inconsistentes de MediaStore vs decoder** — en grabaciones
   OPUS largas la duración anunciada no coincide con la real (position >
   durationMs sin avanzar). **Fix**: `replanWithRealDuration` re-plantea el
   fade-OUT con `player.duration` real al escuchar cambios en el watcher.

### Evidencia de playback continuo (nativo, crossfade 0)
- `dumpsys media_session`: `state=PLAYING(3) speed=1.0`, position avanzando
  (5.9 s → 21 s → 34 s → 49 s), `queue size=288`, metadata con el título de la
  pista tocada, `error=null`.
- AudioFocus requerido y concedido (`USAGE_MEDIA/CONTENT_TYPE_MUSIC gain=GAIN`).
- Restore tras force-stop: la cola con posición guardada se reanuda
  (`playpause` → PLAYING en la misma posición persistida);
  `files/queue.json` restauración correcta.

### Evidencia de comandos/API
- `am broadcast -a com.powerampfree.player.API_COMMAND` con
  `com.powerampfree.player.cmd` = `next|previous|playpause|play|pause|seek|set_preset`.
- `next` → log `PafService: skipNext` → cambia de pista (metadata nueva).
- `set_preset` + JSON → `PafService` aplica el preset y lo persiste:
  `shared_prefs/dsp_presets.xml` queda con `TestBoost PEAKING 1000 Hz +6 dB`.
- `PlaybackState.custom actions=[Previous, Next]` con las IDs de nuestros
  vectores originales — los botones del layout personalizado llegan a la
  plataforma (candidata a los botones físicos/notificación).

### Evidencia de modo dual (crossfade activo)
- UI slider: `Crossfade: 2 s`; persistencia en
  `shared_prefs/playback_settings.xml` (`crossfade_ms=2000`).
- Tap en pista → `PafMainActivity: playTracks n=288 index=1` →
  `PafService: setQueue n=288 index=1 dual=true`.
- Tras el cambio de engine, la sesión muestra el beam promovido:
  `state=PLAYING` , metadata de la pista actual, `queue size=1` (el design
  dual: solo el item actual en el session player).
- `skipNext dual=true` → `advanceOrchestrator` → la sesión cambia metadata a
  la pista siguiente y continúa `PLAYING` (promoción correcta).
- Al llegar al real end de una pista larga, la costura hizo la transición a
  la siguiente pista (metadata actualizada, sigue PLAYING, no se detuvo).

### Cobertura lograda vs pendiente
- Verificado: instalación/permisos/UI real, playback nativo continuo con cola
  grande, skip prev/next en ambos modos, API broadcast (7 comandos), EQ real
  via broadcast + persistencia, crossfade dual activado por UI y sesión
  promovida, persistencia de cola+posición y restore, sin crash.
- Pendiente visual (el usuario debe confirmar a ojo): a reciente el
  espectro dibujándose en vivo, botones de la notificación activos (los
  `custom actions` de plataforma ya los muestran), curva de EQ con el
  TestBoost de 6 dB.
- Pendiente audio (medición): calidad audible del fade en la costura con
  material OPUS real; drift de la duración real ya está mitigado en código
  (fix 6) pero falta confrimar a oído.

| Observed behavior (REA evidence)            | Our mechanism                     | Test(s) |
|---------------------------------------------|-----------------------------------|---------|
| Presets are parametric (freq/gain/Q/Band[]) | `Band`, `EqPreset` JSON           | ParametricEqTest, ChainAndPresetTest |
| `native_calc_coefs` designs biquads         | `Biquad.design` (RBJ formulas)    | BiquadTest.peaking_at_center_has_exact_gain |
| `native_filter_evaluate_freqs` curve        | `ParametricEq.magnitudeDb/responseCurve` | response_curve_never_extreme |
| `native_modify_coefs_for_gain` incremental  | `ParametricEq.modifyBandGain`     | modify_band_gain_recomputes_only_that_band |
| Chain reconfig on format change             | `DspChain.buildDefault` + reset   | processing_is_stable_with_extreme_preset |
| No-down scale: post-EQ safety               | `SoftLimiter`                     | chain_trims_no_output_clipping |
| Two-session auto-advance (pending promote)  | `AutoAdvanceEngine`               | AutoAdvanceEngineTest.* |
| DVC split (content gain vs stream volume)   | `GainStage`                       | gain_stage_volume_mapping_monotonic |

Acceptance = full green on `:core:audio:test`.

## Paso 5 (2026-10-21): visualizador + pulido de UX — verificación

- **FFT/Hann en core**: `SpectrumAnalyzer` (radix-2 iterativo, ventana Hann puros, originales)
  con 6 tests nuevos → **40/40 tests verdes** (bins exactos, localización de seno al bin esperado
  ±1, suelo de silencio <1e-4, DC en bin 0, determinismo bit-idéntico, tamaños ilegales).
- **Tap de audio**: `VisualizerTap` (bounded, lock-free `AtomicReference`) alimenta desde el
  post-EQ —acumulador mono en `DspAudioProcessor.queueInput`, publicado cada ventana de 1024
  frames≈21 ms; el tap nunca altera la ruta audible.
- **UI**: barras de espectro log (30 Hz–16 kHz, dB −60..0) en Compose refrescadas cada 50 ms;
  fila "Reproduciendo" (título por listener del MediaController) + transporte ⏮ ▶/⏸ ⏭.
- **Notificación dual**: botones reales next/prev en la notificación via custom layout de
  Media3 con comandos propios `com.powerampfree.player.SKIP_NEXT/SKIP_PREV` (manejados en ambos
  motores: player nativo u orquestador dual) e iconos vectoriales originales; `ApiCommandReceiver`
  unificado a esos comandos (play/pause/set_preset/skips).
- API de sesión verificada empíricamente por javap del AAR (media3-session 1.4.1):
  `MediaSession.setCustomLayout(List<CommandButton>)` existe; no existe `RESULT_SEALED`.
- `gradle :app:assembleDebug` → BUILD SUCCESSFUL.
- No verificado en dispositivo: arendizaje visual real del espectro y notificación (sin hardware).

## Paso 4 (2026-10-21): mezcla real de dos decoders (crossfade de superposición) — verificación

- **Diseño (clean-room, componible con el paso 3)**: cada pista vive en su propio ExoPlayer con su
  propia cadena DSP; el sistema mezcla los dos AudioTracks a nivel de audio server → superposición
  real audible. La pista saliente hace fade-out sample-exacto hasta 0 en la costura (plan
  secuencial existente); la entrante se prepara (pausada) con plan fade-in `forceFadeIn` y arranca
  exactamente `fadeMs` antes de la costura (watcher de posición 15 ms). Al completar el fade,
  `MediaSession.setPlayer` promueve el overlay (promoción pending→current del motor observado) y el
  player anterior se libera; el siguiente overlay se arma inmediatamente.
- Core: `CrossfadeProcessor.setPlan(..., forceFadeIn)` + `isArmed` ≥ 1 ítem (test nuevo:
  `forced_fade_in_applies_at_head_of_first_track`). Tests: **34/34 verdes**.
- App: `PlaybackService` reescrito con modo dual/nativo (`dualMode = crossfadeMs > 0`):
  orquestador (`queueTracks/queueIndex/overlay/fadeActive`), watcher por posición, detección y
  silenciado del auto-advance nativo en la costura (`self.pause()`), pausa congelante de ambos
  decoders, rutas de reintento (STATE_ENDED sin overlay → salto sin fade), cola persistente
  también en dual (rutas MediaTrack), EQ en vivo aplicado a ambos decoders, `SET_CROSSFADE`
  re-armando el overlay.
- Ruta dual intercepta `onSetMediaItems` (cola completa→orquestador, session player recibe solo
  el ítem actual) — sin duplicación de decodificación.
- `gradle :app:assembleDebug` → BUILD SUCCESSFUL.
- Trade-offs documentados (honestos): 1) next/previous por notificación no tienen destino visible
  en el session player de un solo ítem (el avance va por orquestador en transiciones naturales y
  por la UI de la app); 2) el overlap mix es aproximado en fase (dos decoders independientes,
  desalineación típica < un buffer, suficiente para crossfade musical); 3) el limiter opera por
  cada decoder (post-mezcla sumada al audio server), no sobre la suma — por diseño lineal el EQ es
  equivalente, el limiter se mantiene como guarda por canal.
- No verificado en dispositivo (sin hardware conectado).

## Paso 3 (2026-10-21): crossfade secuencial — verificación

- `CrossfadeProcessor` (:core:audio, 1 archivo nuevo) con envelope lineal sample-exacto: fade-out
  ventana-final de la pista A + fade-in del arranque de B (equivalente honesto del estado OVERLAP
  observado dentro del alcance de un audio-sink único Media3).
- Propiedades verificadas por los 8 tests nuevos (33 total, todos verdes con `cleanTest`):
  disabled → passthrough bit-exacto; ramps lineales que se juntan ≈0 en la costura (sin clicks);
  centro de pista a ganancia 1±1e-6; primera/última pista no se silencian (sin vecinos reales,
  no fade); nunca amplifica (gain ≤ 1 con corto-cap = mitad de la pista más corta); seek →
  `snapTo` re-sincroniza la envolvente (0.5 exacto a mitad de ventana); `process()` integra el
  envelope sobre PCM; ítems con duración desconocida (SAF) quedan en unity y no silencian.
- Integración: orden CORRECTO en la cadena (fade → preamp/EQ → limiter, fade nunca re-amplifcado,
  limiter vigila la costura); plan desde PlaybackService (`updateCrossfadePlan` en transición,
  isPlaying, timeline change) con snap en `onPositionDiscontinuity`; duraciones propagadas en
  MediaMetadata.extras (MediaStore y restauración); ajuste persistente via sesión
  (`com.powerampfree.player.SET_CROSSFADE`) y slider en UI (0–12 s).
- `gradle :app:assembleDebug` → BUILD SUCCESSFUL; APK regenerado.
- No verificado en dispositivo: comportamiento audible en alternancia real de pistas.

## Paso 2 (2026-10-21): integración de cola + EQ UI — verificación

- `gradle :app:assembleDebug` → **BUILD SUCCESSFUL** (12 deps: media3, compose, documentfile).
- `gradle :core:audio:test --rerun-tasks` → 25 tests OK.
- APK regresado con `aapt2 dump badging`: package `com.powerampfree.player`, permisos correctos
  (POST_NOTIFICATIONS, FOREGROUND_SERVICE_MEDIA_PLAYBACK, READ_MEDIA_AUDIO), MainActivity lanzable.
- Verificación estática del código integrado (sin dispositivo conectado; sin ejecución runtime):
  - cola MediaStore/SAF (`LibraryLoader`), persistencia (`QueueStore`, JSON en filesDir),
  - restauración de cola pausada al crear el servicio (no autoplay en boot),
  - EQ live por MediaSession custom command `SET_PRESET` con dos rutas (reconfigure completo
    / incremental por ganancia) y persistencia del preset actual en prefs + presets con nombre
    en `filesDir/eq/`,
  - `AutoAdvanceEngine` conectado a eventos de ExoPlayer (dspTrackStarted/Ended, guards de sesión).
- Pendiente de verificación en dispositivo (instalación, reproducción real, crossfade).

Known NOT verified:
- Bit-exact audio equivalence with the reference native DSP (its .so remains black-box).
- Device-level behavior (Hi-Res routing, USB DAC) — out of scope of this scaffold.
