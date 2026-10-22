# Verification plan — PowerampFree DSP core tests

The analyzed reference cannot run here (it is an Android binary), so
equivalence claims are *behavioral properties* traced from REA findings to
tests that any environment can execute:

## Sprint 1 (2026-10-22): player screen completo — verificación

Bloque 1 del catálogo (`POWERAMP_FEATURES.md`). Implementación original
(observaciones de la referencia solo como comportamiento objetivo).

### Implementado
- **Repeat modes** (`off/one/all`): ruteados por sesión propia
  `com.powerampfree.player.SET_REPEAT` (extra `repeat_mode`). Nativo:
  `ExoPlayer.repeatMode`. Dual: ONE → el beam del item único hace loop
  nativo (plan de crossfade reseteado); ALL → el orquestador hace wrap a la
  primera pista en `advanceOrchestrator`. Persistido en
  `PlaybackSettings` (`repeat_mode`).
- **Shuffle**: `SET_SHUFFLE` (extra `shuffle_on`). Nativo:
  `shuffleModeEnabled`. Dual: la pista actual/elegida queda primera y el
  resto se baraja Fisher-Yates completo — también aplicado al encolar
  (`setQueue`) con shuffle activo (`ShuffleOps` en `:core:audio`, 6 tests).
- **±10 s**: botones de nudge por `controller.seekTo` (verificado 40.7 s →
  51.3 s con +10 s; 43.5 s con −10 s).
- **Chip de formato técnico**: el servicio publica el formato REAL del
  decoder (`Tracks` del session player) en `FormatBus` (mismo proceso;
  fallback `controller.currentTracks` para otros procesos). En el teléfono:
  `48 kHz · 64 kbps · MP3` sobre el título (estos archivos van por el decoder
  MP3 de c2.android).
- **Rating like/dislike**: `RatingsStore` (prefs `track_ratings`, uri → 1/0/-1,
  toggle al repetir el mismo estado). Verificado persistido para la pista
  reproducida.
- **Sleep timer**: ⏰ con diálogo (5/15/30/60/90 min, cancelar), chip con
  cuenta atrás en vivo (`⏰14:42`), pausa automática al expirar.
- **Marquee** del título largo (deja de truncar a 40 chars).

### Defectos corregidos durante esta fase
- **Semántica de shuffle**: la primera versión barajaba con la pista elegida
  fija en su índice original; arrancaba la pista 0 (no la tocada) que resultó
  corrupta (`Source error`). Fix: elegida primero + resto barajado (the tail
  includes the remainder), `queueIndex = 0`.

### Verificación en dispositivo (evidencia logcat/UI)
- `PafService: setShuffle on=true dual=true`, `setRepeat mode=all dual=true`.
- `playTracks n=288 index=1` → `setQueue n=288 index=1 dual=true` — PLAYING
  con metadata = pista tocada (elegida primera), `error=null`.
- Chip visible en dump: `48 kHz · 64 kbps · MP3` (fila bajo el título).
- `+10s`/`−10s` movieron la posición (dumpsys position).
- Sleep timer: diálogo renderizado, chip `⏰14:42`.
- Rating: `shared_prefs/track_ratings.xml` con la pista a `value="1"`.
- No FATAL/ANR durante toda la sesión; las pistas corruptas de la biblioteca
  real pueden generar `Source error` sin crashear la app (estado de error
  manejado por Media3).

### Cobertura
- Tests nuevos: `ShuffleOpsTest` (6) → `:core:audio` 46/46 verde.
- Pendiente de este bloque: carátula y waveform-en-seekbar (sprint
  carátulas, orden 8 del roadmap).

---

## Sprint 5 (2026-10-22): Settings + tema + Export/Import — verificación

Bloques 6 + 7 (+ acceso del menú overlay, bloque 15). Implementación original;
persistence propia (`AppearanceStore` en prefs `appearance`).

### Implementado
- **Pantalla raíz de Ajustes** como overlay opaco sobre el tab activo: 10
  categorías (Look and Feel / Audio / Visualization / Background / Album Art /
  Biblioteca / Headset / Lock Screen / Misc / About); las pendientes se pintan
  atenuadas con su sprint objetivo; BACK cierra sub-pantalla y luego el overlay.
- **Entradas**: ⚙ en la fila de transporte del reproductor y tarjeta ⚙ en la raíz
  de Biblioteca.
- **Look & Feel (en vivo)**:
  - *Settings Theme*: Automático (isSystemInDarkTheme) / Claro / Oscuro.
  - *Acento*: 5 muescas (azul/verde/violeta/ámbar/rosa) → `colorScheme.primary`.
  - *Fuente negrita* (typography con FontWeight.Bold en 15 estilos).
  - *Start at Library*: la app arranca en la pestaña Biblioteca.
- **Biblioteca**: contador MediaStore/SAF + **Rescan** real.
- **Export/Import de datos** (bloque 6): dump JSON de 5 prefs
  (`dsp_presets/playback_settings/appearance/search_history/track_ratings`)
  + presets de `files/eq` → archivo en `Documents/PowerampFree/` vía
  MediaStore (API 29+); importación por SAF (OpenDocument) o "
  ⏰ Último backup" (query MediaStore del más reciente).
- **About**: paquete/versión/nota de reconstrucción.

### Verificación en dispositivo (evidencia)
- Raíz pinta con filas y pendientes atenuadas ✓; ✕/←/Cerrar navegación ✓.
- **Tema Oscuro**: fondo medido por pixel `R20 G18 B24` (oscuro) ✓;
  `theme_mode=dark` persistido ✓.
- **Acento Violeta**: `accent_color=-6526242` (= `0xFF9C6ADE`) ✓ y
  presencia del color en pantalla (50 píxeles del matiz violeta al capturar) ✓.
- **Export**: `PowerampFree-backup-20261022_023306.json` creado en
  `Documents/PowerampFree/` y verificado `cat` (prefs + playback_settings
  dentro) ✓.
- **Import round-trip**: acento cambiado a Ámbar → import → **violeta
  restaurado** (`accent_color=-6526242`) ✓.
- **Bug encontrado y corregido**: org.json re-parsea `Long` como `Int`
  (2000 → Integer) y el import lo escribió como Int en `crossfade_ms`;
  `PlaybackSettings.crossfadeMs` (getLong) → **ClassCastException al crear el
  servicio** ("PowerampFree keeps stopping"). Fix doble: (1) el import escribe
  `-ms` como Long (registro/claves), (2) el lector es tolerante (`is Int →
  toLong`). Verificado: prefs de nuevo `long`, servicio arranca, reproducción
  `PLAYING, error=null`, 0 crashes nuevos ✓.
- **Start at Library**: activado → force-stop → relanzamiento abre
  **Biblioteca** directamente ✓; devuelto a off.
- ⚙ del reproductor presente en la fila de transporte ✓.

### Cobertura
- `:core:audio` 49/49 verde; `:app:assembleDebug` verde; APK final instalado;
  playback pausado y app force-stopped.

---

## Sprint 4 (2026-10-22): EQ gráfico (10 bandas + Tone knobs + Limit) — verificación

Bloque 16 del catálogo. Implementación original (faders/knobs propios de
Compose; motor paramétrico `:core:audio` resuelve por capas).

### Implementado
- **Extensión de `EqPreset`** (JSON compatible hacia atrás): `graphicGainsDb`
  (10 ganancias fijas 31/62/125/250/500/1K/2K/4K/8K/16K), `graphicOn`,
  `toneBassPct`/`toneTreblePct`/`toneOn`, `limitOn`; `resolvedBands()` une
  capas (paramétricas + gráfico + 2 shelves de tone). Límite del motor sube a
  63 bandas para 31+10+2.
- **`SoftLimiter.enabled`** (módulo passthrough cuando off) y
  `DspChain.buildDefault`/`applyPreset` resuelven capas y conmutan el módulo.
- **Pantalla EQ con 3 tabs** (🎛 EQ / 💿 Tone / 🔊 Limit):
  - EQ: **Preamp + 10 faders verticales** propios (toque o arrastre, cuantía
    de 0.5 dB, marcador de 0 dB, relleno bottom-up), curva de respuesta de
    capas resueltas, Switch "EQ activo", editor paramétrico + crossfade +
    guardar presets debajo (App Layer).
  - Tone: **Bass / Treble knobs rotatorios** (barrido 270°, tap y drag),
    mapeados a bandas LOW_SHELF 250 Hz / HIGH_SHELF 4 kHz (0..100% → 0..+12 dB).
  - Limit: Switch del limitador + descripción.
- **Chip de estado** `ⓘ NO DVC · EQ n · TON ON/OFF · LMT ON/OFF`.
- Log nuevo de evidencia en el servicio: `applyPreset … resolved=n`.

### Verificación en dispositivo (evidencia logcat/UI)
- Banda **2K**: tap → +5.5 dB, chip `EQ 11`, `applyPreset … graphic=true
  resolved=11` (incremental a través de `modifyBandGain`).
- **Knob Bass**: tap en el arco superior → **50%**, `tone=true b=50%,
  resolved=13` (1 paramétrica + 10 gráfico + 2 shelves).
- **Knob Treble**: arrastre de barrido completo → **100%**.
- **Preamp**: tap → +6.5 dB (valor visible bajo el fader).
- **Limit**: switch → chip `LMT ON`, `limit=true`; página Limit con
  descripción del módulo.
- **Reproducción real con la cadena completa** (comando
  `cmd media_session dispatch play`): `state=PLAYING, error=null`, sin
  `FATAL`/Source error — DSP (preamp + gráfico + tone + limitador) estable.
- **Reset**: botón restaura default (3 bandas paramétricas, todo off,
  `resolved=3`) — estado limpio al final de la sesión.

### Notas técnicas / hallazgos
- `detectDragGestures` no dispara con tap simple (requiere touch slop):
  los knobs necesitan `detectTapGestures` adicional — corregido.
  VSlider ya lo llevaba separado.
- El LV de registros paramétricos quedaron debajo con `weight(1f)` para que
  el área fija de faders no compita con el scroll (aprendizaje sprint 3).

### Cobertura
- `:core:audio` **49/49** (+3: capas compuestas, limitador disabled =
  passthrough, cadena acepta layering completo); `:app:assembleDebug` verde;
  APK final instalado, reproducción pausada y app force-stopped.

---

## Sprint 3 (2026-10-22): explorador de carpetas + selección + sort — verificación

Bloques 3 + 18 + 19 del catálogo. Implementación original.

### Implementado
- **Explorador de carpetas** (`FolderScreen`): breadcrumb + ↑, navegación por
  subcarpetas derivadas de `RELATIVE_PATH` (root = listo, `Music/` →
  `Music/RecForge/`), BACK sube nivel y al raíz sale de la categoría.
- **Cabecera con totales del subárbol** (`n pistas · duración total`,
  estilo de la referencia: 284 pistas · 296:28:50 para `Music/`).
- **Acciones de cabecera**: ▶ Todo (encola la carpeta), 🎲 (arranque
  aleatorio — `setQueue n=41 index=22`), ☑ Select (modo selección),
  ⇅ sort (List Options: Título / Nº pista / Duración con diálogo y ✓).
- **Filas con duración** formateada (`4:22:05`).
- **Selección múltiple**: casillas en filas, barra inferior
  `▶ n` / `Todos` / `Ninguno`; reproducir la selección
  (`setQueue n=2` verificado).
- **DEFECTO DE PLATAFORMA corregido**: la app se dibuja edge-to-edge y MIUI
  se tragaba todos los taps con `y < ~100px` (los iba a la barra de estado;
  por eso ni ↑ ni ⇅ respondían). Fix: `windowInsetsPadding(statusBars)` en el
  root — verificado con taps posteriores en la banda superior.

### Verificación en dispositivo (evidencia logcat/UI)
- Lista de carpetas raíz de `RELATIVE_PATH` con conteos directos ✓.
- `Music/` → cabecera `284 pistas · 296:28:50`; subcarpetas `RecForge(41)`,
  `Rohn Audio(242)` ✓.
- Sort dialog abierto tras fix insets; "Duración" reordenó (1.ª fila cambió
  de `2026-08-01…` a `2026-09-02…`).
- ☑ Select: 2 pistas marcadas → `▶ 2` → `setQueue n=2 index=0 dual=true` y
  reproduciendo la primera seleccionada ✓.
- 🎲 → `setQueue n=41 index=22` con pista aleatoria ✓.
- BACK: `Music/RecForge` → `Music` → lista de Carpetas ✓ (y la app no cierra
  más allá de la raíz de la categoría).
- Regresión: player, EQ tab y mini-player siguen funcionando; sesión con
  `error=null`.

### Cobertura
- `:core:audio` 46/46 verde; `:app:assembleDebug` verde; APK final instalado.

---

## Sprint 2 (2026-10-22): navegación 5 tabs + mini-player + cola + búsqueda + categorías — verificación

Bloques 2 + 15 + 17 del catálogo. Implementación original.

### Implementado
- **Bottom nav de 5 tabs** (`NavigationBar`): ▶ Reproductor / 📚 Biblio. / 🎚 EQ /
  🔍 Buscar / 📋 Cola. El contenido de la app se repartió: EQ (sliders/presets/
  crossfade) vive en su tab; Reproductor quedó con transporte+controles+espectro.
- **Mini-player persistente** sobre la barra (excepto en el tab del player):
  título+artista marquee, ⏸/⏭, **tap → abre el player**. Alimenta
  `PlayerSnapshot` (ahora también artista).
- **Pantalla de Cola**: lista completa del orquestador vía `QueueBus`
  (mismo proceso: en dual el session player solo lleva 1 item y el timeline
  del controller no sirve — consistente con la limitación ya documentada de
  `currentTracks`). Pista actual resaltada por URI común (válido en ambos
  motores); **tap-para-saltar** con comando propio
  `com.powerampfree.player.SEEK_QUEUE` (dual → `queueJump` sin re-barajar;
  nativo → `seekTo(idx)`).
- **Búsqueda** con filtros Todo/Título/Artista/Álbum (in-memory sobre los 288
  tracks), resultados reproducibles (encola el subconjunto), **historial
  persistente** (prefs `search_history`, máx. 10, dedupe, "Borrar").
- **Categorías de Biblioteca**: tarjetas Canciones / Álbumes / Artistas /
  Géneros / Carpetas / Importar-SAF. `MediaTrack` extendido (albumId, trackNo,
  folder por `RELATIVE_PATH` API 29+; serialización opcional en el propio
  JSON de cola). Géneros vía `MediaStore.Audio.Genres` + members. Retroceso
  con `BackHandler` (detalle → categoría → raíz).
- `QueueBus` publicado por el servicio en TODO cambio estructural:
  `setQueue` (ambos motores), `advanceOrchestrator`, `finishFade`,
  `skipPrevious`, shuffle-dual, `onSetMediaItems`, `restoreQueue`, `queueJump`.

### Verificación en dispositivo (evidencia logcat/UI)
- Nav 5 labels visibles; mini-player oculto solo en tab Player ✓.
- Biblioteca: `288 pistas`; Álbumes=7, Artistas=3, tarjetas funcionales.
- Álbum "Conversation" (34 pistas) → tap en pista → `setQueue n=34 index=1
  dual=true` y metadata = pista tocada.
- Cola: lista 34 pistas, actual resaltada `▶`; tap en la 3.ª → `seekQueue
  idx=2 dual=true` y resaltado se movió ✓.
- Búsqueda "rec" → 10 resultados (modo Todo), tap → `setQueue n=10 index=0`
  y `shared_prefs/search_history.xml` con `queries=rec` ✓; al vaciar el campo
  reaparece "Historial" + chip `🕘 rec` + "🗑 Borrar" ✓.
- Carpetas: `Download/`, `Music/`, `Music/RecForge/`… (RELATIVE_PATH) ✓.
  Géneros: el dispositivo no tiene géneros en MediaStore → estado vacío
  ("No hay géneros…"), sin cuelgues.
- EQ tab intacto (preamp/crossfade/bandas). Mini-player tap → tab Player ✓.
- Sin FATAL/ANR (aparte del `Source error` del archivo corrupto preexistente).

### Cobertura
- `:core:audio` 46/46 verde; `:app:assembleDebug` verde.
- Serie con teclado: detectado que taps con IME abierto teclean; el flujo de
  mini-player se re-verificó con el teclado cerrado ✓.

---

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
