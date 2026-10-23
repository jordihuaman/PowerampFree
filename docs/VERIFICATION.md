# Verification plan — PowerampFree DSP core tests

The analyzed reference cannot run here (it is an Android binary), so
equivalence claims are *behavioral properties* traced from REA findings to
tests that any environment can execute:

## Sprint 10 (2026-10-22): playlists + visualización del player — verificación

Bloques 4/6 (Add to Playlist + pantallas de playlists) y 14 núcleo (espectro
en el player y en el EQ). `PlaylistNames` (puro, `:core:audio`,
`PlaylistNamesTest` 5 tests), `PlaylistStore` (app, un JSON por lista en
`filesDir/playlists/` con snapshot del MediaTrack + `at`), `VizSettings`
(prefs `viz_settings`), UI en `MainActivity`.

### Implementado
- **Add to Playlist** (ctx menu): fila 🔖 → `AddToPlaylistSheet` (filas de
  listas con conteo + dedupe por uri + "＋ Nueva playlist…"). Rework sobre la
  marcha: el botón "Crear y añadir" inline bajo el IME no procesaba MIUI taps
  (4+ taps verificados inertes); se movió a un **AlertDialog M3** con
  TextField (patrón probado del sort dialog).
- **Playlists UI**: tarjeta 🔖 en la grilla de Biblioteca (junto a Carpetas;
  Ajustes/Importar reordenadas en su propia fila), `PlaylistsScreen`
  (contado "N listas guardadas" + "＋ Nueva" + filas "name · N pistas") y
  `PlaylistScreen` (▶ Reproducir / ✏ Renombrar / 🗑 Eliminar + filas
  tap=playTracks / long-press=ctx menu / 🗑 trailing quita la pista).
- **Viz (bloque 14 núcleo)**: toggle 📊 en la cabecera del player
  (`player_spectrum`), estilo Classic/Rounded, opacidad del panel (valor =
  alfa de las barras; default 60), `Equalizer Screen Spectrum`
  Disabled/Classic/Rounded bajo la curva del EQ. `SpectrumBars` relee las
  prefs en el poll de 50 ms → efecto inmediato sin recomposición forzada ni
  push de sesión. Sub-pantalla nueva **Ajustes → Visualización** (row que
  antes estaba atenuada); `viz_settings` añadido a `BACKUP_PREFS` y
  export/import de `playlists` en el backup JSON.
- **Fix del sprint (descubierto durante el análisis del sprint 9)**: los
  botones ⏮/⏭ de la cabecera del player y del mini-player tenían las
  acciones cruzadas (⏮ → `CMD_SKIP_NEXT`, ⏭ → `CMD_SKIP_PREV`) desde el
  sprint 1. Intercambiados: ⏮→`sendSkip(false)`, ⏭→`sendSkip(true)`.

### Verificación en device (Xiaomi tanzanite, Android 16, `di4djffyeukjgaw4`)
- **Playlists end-to-end**:
  - ctx menu → "Añadir a Playlist" → sheet auto-nueva → dialog →
    **Test10.json creado con 1 pista** (`run-as cat`: esquema completo
    uri/title/artist/album/durationMs/albId/trk/dir/at).
  - Segunda pista añadida tocando la fila "Test10 · 1 pistas" del sheet →
    **2 entries** en el JSON (RecForge + Download).
  - **Dedupe**: re-añadir la misma pista → sigue en **2** (toast "Ya estaba
    en…"). *(toast no capturable — verificado por conteo del JSON).*
  - Biblio → tarjeta Playlists ("1 listas guardadas") → fila "Test10 ·
    2 pistas" → `PlaylistScreen` (screencap `paf_pl.png`: header ▶/✏/🗑 + 2
    filas con 🗑 trailing + mini-player).
  - 🗑 trailing de la fila 1 → JSON queda en **1 entrada** ✓ (header "1
    pistas").
  - **▶ Reproducir** → dumpsys `PLAYING(3)`, `description=2026-08-03
    12h50m03s, <unknown>, RecForge` = primera pista restante ✓ (SET_QUEUE
    desde la lista).
  - **⏭/⏮ fix**: con la cola completa de la biblioteca (286 pistas, pista
    actual = fila 1), header ⏭ → `description` cambió a la pista
    siguiente ("2026-10-13-02h05m39s (1)"); ⏮ → volvió a la pista anterior
    ("2026-10-13-02h05m39s") ✓. Ambos verificados por dumpsys.
  - **✏ Renombrar** → dialog "Test10b" → `Test10.json` → **`Test10b.json`**
    en disco con la pista intacta y `name:"Test10b"` ✓.
  - **🗑 Eliminar** → confirm "Borrar" → PlaylistsScreen "**0 listas
    guardadas**" + `files/playlists/` vacío ✓.
- **Visualización**:
  - Player por defecto: barras **rounded** al 60% visibles en screencap
    (`paf_viz_on.png`).
  - 📊 OFF: barras desaparecen (`paf_viz_off.png`) + `viz_settings.xml`
    `player_spectrum=false` persistido ✓; re-toggle ON ✓.
  - **Classic** (`paf_viz_classic.png`): barras rectangulares vs rounded +
    pref `spectrum_style=classic` ✓. Restaurado a rounded.
  - **Opacidad**: slider a 17% (`paf_viz_op17.png`: barras casi
    translúcidas, pref `panel_opacity=17`) → restore a 60 vía slider (quedó
    62; se dejó en el default exacto con force-stop+XML en la limpieza).
  - **EQ spectrum** (`paf_eq_viz.png`): barras rounded en vivo bajo la curva
    del tab EQ con mini-player. El modo **Disabled** (ocultar el espectro)
    se verificó por build/código-path (un `if != disabled`), sin screencap
    dedicada.
- **Limpieza (estado de handback)**: force-stop → XML defaults
  (`player_spectrum=true`, `spectrum_style=rounded`, `panel_opacity=60`) +
  cola restaurada a `[A,B]` (Greatness + 7 Powerful, index 0) → relaunch.
  Sanity final: sesión viva `PAUSED(2)`, `description=Greatness is Quiet
  Until It Isn't…`, playlists dir `0` ✓. Nada de Test10 sobrevive. La
  (abundante) cola de biblioteca reproducida durante las pruebas limpió los
  contadores/stats normales (`track_state.json` conserva progreso real de
  esas escuchas — dato de uso, no basura de test).

### Notas MIUI nuevas (para próximos sprints)
- El **AlertDialog M3** es el patrón robusto para inputs junto al IME; el
  TextField de Compose bajo un bottom sheet también funciona pero los taps
  sobre botones Material del sheet resultaron inertes durante todo el
  sprint (Causa probada: texto visible en el campo era draft del IME; el
  estado Compose no lo recibía sin foco exacto del campo).
- Las coordenadas del propio sheet con IME abierto cambian (escribir
  "Test10" movió el dialog hacia arriba); re-dumpear tras cada cambio de
  teclado.
- `input text` repetido acaba **acumulando** texto ("Test10 que no me
  Test100"…): limpiar con N×keyevent 67 antes de reintentar (y el propio
  campo de diálogo se autocorrigió al commit).
- Llegó una llamada WhatsApp durante el test del EQ: overlay del sistema
  interrumpió un screencap (no tocado; se re-encaminó el test).

### Build
`:app:assembleDebug` verde; `:core:audio:test` **102/102** (97 previos + 5
`PlaylistNamesTest`: sanitize/colapso de espacios/blank→default/unique sin
colisión/sufijo libre mínimo).

## Sprint 9 (2026-10-22): library avanzada (progreso, stats, filtros) — verificación

Bloque 11. Políticas puras `com.powerampfree.audio.library` en `:core:audio`,
store Android `app/.../TrackStateStore.kt`, prefs `LibrarySettings.kt`
(`library_settings`), cableado en `PlaybackService`/`LibraryLoader`, UI en
Settings → Biblioteca.

### Implementado
- **`TrackProgressPolicy`** ("Track Duration For Per Track Progress — At
  least: 45 min."): solo pistas ≥ umbral (slider 1–90 min, def. 45) guardan y
  reabren en su posición recordada; toggle Store/Restore ON.
- **`CountAsPlayedPolicy`** ("Count As Played — after playing at least: 50%"):
  escucha contada cuando el playhead ≥ N % de la pista (def. 50 %, slider
  1–100 %); avance natural cuenta siempre (listened == duration), pausar no
  cuenta.
- **`LibraryFilterPolicy`** ("Ignore Short Tracks (Notifications, etc.)" < 6
  s): pistas ≤ 6 s fuera del rescan; video ya excluido de fuente
  (`MediaStore.Audio`).
- **`TrackStateStore`**: `{tracks:{uri:{pos,plays,last}}}` en
  `filesDir/track_state.json`, cache + rewrite atómico tmp→rename; API
  `positionFor/saveProgress/commitCount/statsSummary/resetStats/snapshotJson/
  importAll`.
- **`PlaybackService`**: `commitTrackState()` en skip/dual-seam
  (finishFade)/END/jump; snapshot de progreso en pausa y en el ticker de 15 s;
  `restorePositionFor()` en `playCurrentItem` (⏮/jump/SET_QUEUE — el arranque
  sin posición explícita reanuda si la política lo pide) y en el arranque de
  SET_QUEUE single-mode. Stats solo en avances; la pausa guarda posición sin
  contar.
- **`LibraryLoader`**: filtro de cortas aplicado en el query con
  `LibrarySettings.ignoreShort`.
- **UI Settings → Biblioteca**: rescan + Per Track Progress (toggle, umbral
  1–90), Count as played (umbral 1–100 %), Filtros (ignorar cortas), stats
  (N pistas · N escuchas totales) y **Reset stats** (conteos a 0, posiciones
  conservadas). `library_settings` en `BACKUP_PREFS`; export/import incluye
  sección `trackState` (solo entradas vivas).

### Defectos corregidos durante esta fase
- **`queue.json` con `index:-1` crasheaba el servicio en el restore**
  (`IndexOutOfBoundsException: index: -1, size: 2` en `restoreQueue`): el
  flujo preexistente de ⏮ en la primera pista hacía `queueIndex=-1` y un
  snapshot posterior persistía ese -1. Guardas en los tres puntos:
  `restoreQueue` (índice fuera de rango → aborta sin crash),
  `QueueStore.save` (no persiste índices corruptos) y `saveQueueSnapshot`
  dual. Semántica nueva para ⏮@0 en dual: reinicia la pista actual (nunca
  -1). Evidencia: el boot con el queue corrupto ya no crashea y el guard
  deja la sesión viva (state NONE → rescan/SET_QUEUE re-arma).
- Lectura del `positionMs` del track saliente en pausa/crossfade: el commit
  usa `primary.player.currentPosition` DE QUE el track siga siendo el item
  actual (llamadas ubicadas antes de cualquier cambio de beam).

### Verificación en dispositivo (evidencia)
- **Count-as-played**: SET_QUEUE de búsqueda y avances → cada transición con
  playhead ≥ umbral sumó plays (21 escuchas totales visibles en settings =
  la suma del file). Pausa: posición guardada exacta (184 669 ms = posición
  de pausa) **sin** incrementar plays.
- **Restore per-track**: (1) SET_QUEUE reabrió B en su posición recordada
  **exacta 29 864 ms**; (2) tap en Cola (queueJump) reabrió A en **99 921
  ms** (dumpsys 105.9 s = 99.9 + tiempo transcurrido). Con la app
  detenida/relanzada el arranque restauró la cola en pausa (sin autoplay).
- **Filtros**: con "Ignorar pistas cortas" ON el rescan devuelve **286
  pistas** (antes 288) — las 2 pistas < 6 s del sistema quedaron fuera.
- **Settings**: línea "2 pistas reproducidas · 21 escuchas totales" →
  **Reset stats** → nota "Estadísticas reiniciadas (posiciones por pista
  conservadas)" + "0 pistas · 0 escuchas" + file con plays=0/last=0 y
  posiciones intactas (paf_libset.png).
- **Tests finales**: `:core:audio` **97/97** (85 + 12 `LibraryPoliciesTest`),
  `:app:assembleDebug` verde; prefs de prueba (1 min / 1 %) restauradas a
  defaults (45 min / 50 %) y `track_state.json` de prueba eliminada tras la
  verificación.

### Limitaciones honestas
- Music Folders (selección de carpetas), Full Rescan con limpieza de tags,
  File Access Legacy Mode y las sub-pantallas Lists/Search/Queue/Playlists/
  Shuffle/Scanner del catálogo no implementadas; progreso configurable por
  lista/carpeta individual pendiente; los botones ±10 s permanecen siempre
  visibles (sin el modo "Pro Buttons" de pistas largas); restore aplicado en
  aperturas (SET_QUEUE/jump/⏮), el avance natural dentro de la cola entra
  desde 0 (semántica de reproducción secuencial).
- La verificación de skip se hizo con los propios botones ⏭/⏮/Cola y
  teclas de sistema; los taps de MIUI resultaron intermitentes (patrón
  conocido) y se registró el desorden de eventos observado vía file
  (plays/plays duplicados) sin afectar la corrección del commit.

### Cobertura
- `core.audio.library.LibraryPoliciesTest` — 12 tests (progreso umbral,
  toggle, count porcentual con límites, extremo natural, filtros cortas y
  bordes). Total `:core:audio`: **97/97**.

## Sprint 8 (2026-10-22): carátulas + fondo derivado — verificación

Bloques 12 + 13. Lógica pura `com.powerampfree.audio.art.CoverFileNames`,
loader Android `app/.../AlbumArtLoader.kt`, settings `ArtSettings.kt`
(`art_settings`), tile/fondo en `MainActivity`, `artworkUri` en `LibraryLoader`.

### Implementado
- **`CoverFileNames`**: candidatos canónicos en orden de prioridad
  (`cover > albumart > album_art > folder > front` × `jpg > jpeg > png`),
  `pick(lowercase)` y `isCoverLower`. 6 tests `:core:audio`.
- **`AlbumArtLoader`**: (1) proveedor albumart del sistema por `albumId`
  (`content://media/external/audio/albumart/<id>`, uri que también viaja en la
  metadata); (2) fallback scan de carátulas en la carpeta de la pista — padre
  derivado del file: uri, o `DATA`/`RELATIVE_PATH` consultado por `ALBUM_ID`;
  (3) SAF árbol sin ruta local → null (glifo honesto). Decode dos pasadas con
  `inSampleSize`; cache `LruCache` **por bytes (32 MiB)** con `sizeOf =
  allocationByteCount` (los tiles de 1024 px ≈ 4 MiB → el cap de entradas era
  insuficiente). `high_res_art` (def. ON) limita el decode del tile a 1024 px
  vs 512 px.
- **Metadata con art**: `toMediaItem()` pone `setArtworkUri(artUri(albumId))`
  cuando `albumId >= 0` → la tarjeta de medios del sistema (lock screen,
  Bluetooth, casting) recibe la carátula; `high_res_art` en este diseño limita
  el decode propio de la UI (el bitmap grande se envía vía uri canónica del
  sistema, no se serializa a mano).
- **UI**: fila propia de carátula 160 dp bajo la cabecera del player
  (`AlbumArtImage` con claves remember albumId+uri y decode en IO),
  mini-player 44 dp; fondo del player (bloque 13) = carátula Crop + scrim
  gradiente vertical (alpha = 0.25 + 0.6×gradiente/10) con `Modifier.blur`
  (radio valor×6 dp, API 31+); blur=0 → decode **1024 px** para nitidez
  (el upscale de 256 px se veía borroso igualmente); Settings → **Album Art**
  con toggle alta resolución, mantener proporción, diagnóstico de pista
  (albumId/carpeta + n/i del bus) y Borrar cache. `art_settings` (plain
  boolean/int) en `BACKUP_PREFS`.

### Defectos corregidos durante esta fase
- **`QueueStore` no persistía `albId`/`dir`/`trk`** (advertencia crítica
  `.doc` y descubierta en runtime): tras un restore, TODAS las pistas del
  queue tenían `albumId=-1` → art lookup imposible y diagnóstico de settings
  mostraba `albumId=-1 carpeta=—` con el bus poblado (`n=288 i=7 id-1`, sonda
  temporal en el campo artist). Round-trip save/load corregido (mismas claves
  que `MediaTrack`: `albId`, `dir`, `trk`); el queue re-se-guarda con albId ya
  (evidencia: `queue.json` con `"albId":7441874997196160190,"dir":"Music/` +
  `Rohn Audio/"` tras un `SET_QUEUE` nuevo).
- **Layout roto del header del player**: como la carátula se anidó en una
  Row(weight) dentro de la Row del header DENTRO del nuevo Box del fondo, el
  dump mostraba dispersión vertical imposible (título @144, botones @574,
  controles @1138) y no había tile pintado. Lección: NO anidar cadenas de
  weight en Rows dentro de la reestructuración Box; carátula en **fila
  propia** centrada y header restaurado al baseline exacto → dump normal
  (header y206, tile centrado 505-671, controles y827).
- **Cache por entradas**: `LruCache(48)` con tiles de hasta 4 MiB podía
  exceder 190 MiB; cambiado a límite por **bytes** (32 MiB).

### Verificación en dispositivo (evidencia)
- **Tile + fondo con art real**: cover de prueba generada (gradiente PA,
  512×512 jpeg) y `push` a `Music/Rohn Audio/cover.jpg` → tile 160 dp con la
  art + **fondo con blur** (fantasma PA desenfocado moderno vía blur) y
  scrim; portada también en el **mini-player** (44 dp). Con `blur=0` el fondo
  es nítido (decode 1024 px) — comparativa de capas paf_shot10/11/12.png.
- **Slider operativos**: swipe en el slider Desenfoque 5→0 cambia el valor en
  pantalla (`Desenfoque de fondo: 0`) y persiste (`art_settings.xml`
  `bg_blur=0` vía `run-as`); restaurado a 5 (default).
- **Tarjeta de medios del sistema**: `cmd statusbar expand-notifications` →
  tarjeta con miniatura PA + título + controles + badge cast (la metadata de
  la sesión incluye artwork); `dumpsys media_session`
  `metadata: size=11` para el track.
- **Fallbacks honestos**: con las carpetas SIN carátula (único contenido real
  del dispositivo: mp3+lrc y grabaciones opus) el tile muestra el glifo 🎵 —
  confirmación de que el sistema no sirve art para esos álbumes y de que el
  glifo es el estado legítimo. La carátula de prueba se **eliminó** tras la
  verificación (`rm cover.jpg`; el art vuelve al glifo).
- **Tests finales**: `:core:audio` **85/85** (79 + 6 `CoverFileNamesTest`),
  `:app:assembleDebug` verde; APK final instalada
  (`lastUpdateTime=20:55` serie de reinstalas de verificación).

### Limitaciones honestas
- Sin descarga por red (no faucets); sin imágenes de artista; carátulas de
  fila en listas/cabeceras pendientes; List/Lyrics background pendiente
  (bloque 13 parcial); API Compatibility/24-bit/Increase Resolution no
  implementados; el toggle alta resolución solo controla el decode propio de
  la UI (el resto de consumidores usa la uri canónica del sistema).
- Verificación de lock screen card propia no posible (MIUI no la muestra); se
  validó la vía equivalente (tarjeta de medios del sistema en la shade).

### Cobertura
- `core.audio.art.CoverFileNames` — 6 tests (prioridad, dedupe, case, null,
  isCover). Total `:core:audio`: **85/85**.

## Sprint 7 (2026-10-22): ajustes de audio y headset — verificación

Bloques 8 + 10 del catálogo. Lógica pura en `:core:audio`
(`com.powerampfree.audio.input.HeadsetGestures`,
`com.powerampfree.audio.focus.FocusPolicy`), cableado en `PlaybackService`,
UI en `MainActivity`.

### Implementado
- **`HeadsetGestures`** (máquina de estados sin reloj; el caller arma timers y
  les alimenta expiraciones): down→cuenta click (solo `repeatCount==0`), up→
  ventana doble 350 ms, long 700 ms consume el click y emite `LONG_NEXT`,
  resolve 1/2/3 clicks → `SINGLE`/`DOUBLE_NEXT`/`TRIPLE_PREV`. 10 tests con
  timestamps sintéticos.
- **`FocusPolicy`**: tabla de decisión pura evento→acción respetando
  `FocusToggles(pause, resume, duck)`; GAIN restaura duck antes que resume;
  LOSS_TRANSIENT_CAN_DUCK prefiere duck. 12 tests.
- **`PlaybackService`**: `AudioFocusRequest` (USAGE_MEDIA/CONTENT_TYPE_MUSIC,
  `setWillPauseWhenDucked(false)`) gestionado por el servicio;
  `ExoBeam(withFocus=false)` para que ExoPlayer NO pida foco por su cuenta
  (`handleAudioFocus` de media3 ya no interfiere). `ensureAudioFocus()` al
  pasar a playing, `abandonAudioFocus()` al pausar solo si `!focusPaused`;
  LOSS permanente baja `haveFocus` (re-request al siguiente play). Duck =
  `GainStage.volumeLinear` absoluto 0.3/1.0 (baseline siempre 1.0).
- **Media buttons**: `onMediaButtonEvent` devuelve true (swallow) cuando
  `respond_to_buttons=false`; HEADSETHOOK alimenta `HeadsetGestures`
  (timers `gestureResolve`/`gestureLong`); MEDIA_PLAY/PAUSE/PLAY_PAUSE y
  NEXT/PREV mapean a engine/skip. Pulsación simple = acción configurada por
  conexión (Jack preferido si ambos conectados, documentado porque el
  KeyEvent no trae source).
- **Receptores**: ACTION_AUDIO_BECOMING_NOISY → pausa si
  `pause_on_disconnect`; ACTION_HEADSET_PLUG sticky sembrado con guard
  `plugInitialized` (la primera entrega NO dispara resume); A2DP
  connection state → `btConnected` + resume si `resume_on_bluetooth`.
- **Replay Gain**: `GainStage` + param `replayGainDb` (última posición del
  constructor para no romper llamadas posicionales), `DspChain.buildDefault`
  lo pasa al preamp como trim aditivo; `CMD_SET_REPLAY_GAIN` en `onConnect`
  + handler → `applyReplayGain()` (primary + overlay). `AudioSettings`
  (prefs `audio_settings`; lector numérico tolerante Int/Long/Double/Number
  por la lección org.json del import). UI: Settings → Audio y
  Headset/Bluetooth; `sendReplayGain()` persiste y empuja el trim en vivo;
  `audio_settings` añadido a `BACKUP_PREFS` e import re-aplica el RG.
- **RG aplicado a la cadena**: `DspAudioProcessor.onConfigure` pasa
  `AudioSettings.rgAppliedDb()` a `buildDefault` (+3 tests: trims, stack con
  preamp, wiring de buildDefault).

### Defectos corregidos durante esta fase
- **Compile**: `IntentFilter` sin import en `PlaybackService` (únicos
  errores de la pasada Kotlin; resto compiló a la primera).
- **org.json narrowing** (lección del sprint 5, precargada en diseño):
  `rg_pregain_db` se lee con `num()` tolerante y el import mapea
  Double→`putFloat`.

### Verificación en dispositivo (evidencia)
- **Foco (dumpsys audio)**: mientras reproduce, el stack de foco tiene arriba
  `pack: com.powerampfree.player -- client:
  ...PlaybackService$$ExternalSyntheticLambda1 -- gain: GAIN -- loss: none`
  con `usage=USAGE_MEDIA content=CONTENT_TYPE_MUSIC` — el listener propio del
  servicio (no el de media3) sostiene el foco ✓. Los logs de
  `requestAudioFocus` con `clientId=...x media3.exoplayer.AudioFocusManager`
  son de builds anteriores (hoy `buildBeam(withFocus=false)`).
- **Settings**: filas Audio y Headset/Bluetooth habilitadas; ambas
  sub-pantallas renderizan (dumps `uiautomator`): 3 switches de focus,
  sección Replay Gain con Pista ✓/Álbum y slider Pregain ±12 dB; Headset con
  3 toggles de conexión, Responder a botones, pills ⏯/⏭/⏮ por conexión.
- **Persistencia** (`run-as ... cat shared_prefs/audio_settings.xml`):
  toggles de focus y RG escriben al instante (`rg_enabled=true`,
  `focus_resume=false`…); `wired_action` cambia con la pill (`next` →
  `play_pause`); reactivado todo a default al cierre (solo
  `wired_action=play_pause` explícito).
- **Pulsación simple** (`input keyevent 79`): con `wired_action=next` el
  track saltó 2026-…-02h05m39s → Greatness is Quiet… ✓; con
  `wired_action=play_pause` pausa/reanuda — dumps intermedios captaron
  `BUFFERING(6)` del re-buffer tras reanudar; PLAYING final en la MISMA
  posición (18 142 → 18 667 ms) ✓.
- **Toggle directo** (`input keyevent 85`): PLAYING → PAUSED(2) speed=0.0 →
  PLAYING reanudando en la posición exacta (18 142 → 18 160 ms) ✓.
- **Swallow**: con `respond_to_buttons=false`, keyevent 85 y 79 no cambian el
  estado (PLAYING→PLAYING, posición avanzando 39.7→42.7 s normal) ✓;
  reactivado después.
- **Persistencia de foco**: al cerrar sesiones previas el stack mostró
  `focus owner ... event: died` limpio (sin foco huérfano).

### Limitaciones honestas
- Pérdida de foco real (llamada, A2DP por otra app, nav prompt) no simulable
  aquí: cubierto por 12 tests de la tabla de decisión + código de
  `handleFocusChange()`; verificación en hardware real queda pendiente.
- Ratios de simultáneos (wired+BT) no generados con dongles reales:
  `wiredConnected`/`btConnected` se prueban por receptores y lógica.
- Pregain actúa como trim global (tags por pista pendientes); el trim se
  persiste y se re-aplica en vivo — sin medición audible hecha (unit-tests
  del stack de gain sí cubren la aritmética).

### Cobertura
- `:core:audio` **79/79** verde (FocusPolicyTest 12, HeadsetGesturesTest 10,
  ChainAndPresetTest +3 → 12; Biquad 6, Crossfade 9, ParamEq 7, Spectrum 6,
  AutoAdvance 6, Lrc 5, ShuffleOps 6); `:app:assembleDebug` verde; APK
  instalado + verificado en `di4djffyeukjgaw4`.

---

## Sprint 6 (2026-10-22): menú contextual de pista + letras LRC — verificación

Bloques 4 + 5 del catálogo. Implementación original (comportamiento de la
referencia solo como objetivo). Evidencia por dumps `uiautomator`, `dumpsys
media_session` y análisis de píxeles (MIUI suprime nuestros `Log.i`).

### Implementado
- **`TrackContextMenu`** (hoja inferior: scrim + `Column` alineada abajo,
  renderizada DENTRO del content Box de `PafApp` — ver defecto estructural
  abajo). Cabecera: título/artista/álbum, `dur · ext` (extLabel) y 👍/👎 de
  rating rápido. Acciones: ▶ Reproducir ahora / ⏭ Ecolar siguiente / ⤓
  Ecolar al final de la cola / 🎤 Ir a: \<artista\> / 💿 Ir a: \<álbum\> /
  📁 Ir a: carpeta / 📝 Letras. Filas artista/álbum solo si el campo no está
  en blanco (etiqueta = nombre real). Long-press (`combinedClickable`) en
  QueueTab, TrackListView, FolderScreen y SearchTab.
- **`CMD_ENQUEUE_NEXT`** (`com.powerampfree.player.ENQUEUE_NEXT`, extra
  `EXTRA_ENQUEUE_END`): `enqueueTracks()` inserta tras el índice actual (dual)
  o tras `QueueBus.index` (nativo) SIN interrumpir la reproducción; registrado
  en `onConnect` como session command.
- **Navegación cross-tab**: `NavRequest(kind 0/1/2 = artista/álbum/carpeta,
  key, albumId)` viaja en el estado `gotoNav` de `PafApp`; `LibraryTab` lo
  consume con `LaunchedEffect` (pone `GroupRow` de detalle o `folderPath`) y
  salta al tab Biblioteca.
- **Letras**: `LrcParser` puro Kotlin en `:core:audio`
  (`com.powerampfree.audio.lrc`, `LrcLine`/`activeIndex`, 5 tests). Resolución
  `findLyrics()` (app): 1) ruta `file:`/`DATA` de MediaStore → hermano `.lrc`
  con lectura `File`; 2) query MediaStore Files (`DISPLAY_NAME` +
  `RELATIVE_PATH`) → `openInputStream` vía `ContentUris` (árboles SAF no
  siguen). `LyricsScreen` overlay opaco: cabecera (marquee título, artista ·
  dur · ext, sufijo "(no es la pista actual)"), pos muestreada 200 ms si es la
  pista actual, línea activa = última `atMs <= posMs` (negrita), tap →
  `seekTo(line.atMs)`, follow con botón ⏯ Seguir (flag `pendingAuto` protege
  el auto-scroll programático del watcher `isScrollInProgress`), estado vacío
  "Sin letras / No hay archivo .lrc junto a esta pista."

### Defectos corregidos durante esta fase
- **Overlays touch-inert por estructura hermana**: `TrackContextMenu`,
  `LyricsScreen` y `SettingsScreen` se llamaban como composable HERMANOS tras
  el `Column` raíz de `PafApp` (fuera de un nodo de layout) — se dibujaban
  pero los taps caían muertos (los clicks ejecutaban según prefs pero sin
  recomposición; ← y BACK no cerraban). Fix: moverlos DENTRO del content Box
  (`weight(1f)`, patrón ya probado). Cada overlay además lleva su propio
  `BackHandler` interno (el más interno gana): `TrackContextMenu { onDismiss() }`,
  `LyricsScreen { onClose() }`. ← y BACK cierran la letra correctamente.
- **Estado vacío de letras**: `findLyrics(...) ?: emptyList()` para que la UI
  salga de "Buscando letras…" y pinte "Sin letras" cuando no hay `.lrc`.
- **Follow de auto-scroll**: dos iteraciones previas rechazadas — (a) check
  por rango de visibilidad (dispara ANTES del auto-scroll y entra en deadlock
  vía follow=false) y (b) watcher de scroll plano (cancela el scroll
  programático). Solución final: flag `pendingAuto` activa mientras corre
  `animateScrollToItem`.

### Verificación en dispositivo (evidencia)
- **Menú contextual** abre con long-press en las 4 listas; todas las filas
  renderizan. Tras el fix intra-Box las coordenadas se recolocaron (filas
  y≈1203-1905 según nº de acciones).
- **Reproducir ahora**: en la pista stale `u7LzddOa….flac` (entrada de
  MediaStore cuyo archivo YA NO existe en disco, `owner=com.maxmpz.equalizer`)
  → `state=ERROR(7) error=Source error` y `metadata description=u7LzddOa…` —
  el comando SÍ cambió a la pista tocada y ExoPlayer falló solo porque el
  archivo falta (comportamiento correcto, no un bug). En un archivo real
  (`2026-10-13-02h05m39s.mp3` de Download) → **`PLAYING(3)`** con
  `metadata = 2026-10-13-02h05m39s, <unknown>, Download`, `error=null`.
- **Ir a: artista** (pista Conversation, artista "Jordi") → saltó al detalle
  de artista: cabecera `Jordi · 34 pistas` y la lista completa de las 34
  pistas Conversation ✓.
- **Encolar siguiente**: cola 41→42, insertada tras el índice actual (sin
  interrumpir). **Encolar al final**: 42→43.
- **Letras**: renderiza el `.lrc` real de "The Honest Truth About Motivation"
  (`/sdcard/Music/Rohn Audio/`, primera línea `[00:02.080]There is a
  difference between desire and…`); auto-scroll sigue la línea activa hasta
  hondo del archivo; resaltado verificado por análisis de píxel (fila activa
  max=227 + 300-863 px brillantes >200 vs max=145/0 en las demás; tool
  `tools\analyze-rows.ps1`). **Tap-seek** re-centra y mueve el resaltado
  (verificado en dos capturas). Estado "Sin letras" renderiza para una pista
  sin `.lrc`. BACK y ← cierran la letra (tras el fix estructural).
- **Rating** 👍 persiste en `track_ratings.xml` (uri `…media/63331`
  `value="1"`).
- La pista stale en el menú contextual NO muestra filas de artista/álbum (su
  artist/album están en blanco) — solo Ir a: carpeta + Letras; las pistas con
  metadata muestran Ir a: \<artista\> e Ir a: \<álbum\> con el nombre real.

### Cobertura
- `:core:audio` **54/54** verde (+5: `LrcParserTest`); `:app:assembleDebug`
  verde; APK instalado y verificado en el dispositivo (serial
  `di4djffyeukjgaw4`); playback pausado y app force-stopped al cierre.

---

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
