# Poweramp — inventario de funcionalidades observadas

Catálogo de funcionalidades identificadas en capturas reales de la app de
referencia **proporcionadas por el usuario** (`PowerampImages\`, 35 capturas del
2026-10-22). Es un inventario de *comportamientos visibles en UI*, usado como
hoja de ruta para portar funciones **de forma clean-room** a PowerampFree.

Reglas que gobiernan este documento (misma política del proyecto):
- Solo se documenta lo **observable en las capturas del usuario**.
- Nada de este texto se traduce en código copiado: cada función que portemos
  será implementación original (ya validado con REA sobre los APK propios).
- Al final de cada función se indica su estado en nuestro proyecto:
  `HECHA` / `PARCIAL` / `PENDIENTE`.

Leyenda de evidencia: `IMG:<archivo>` = captura donde se observa.

---

## 1. Pantalla de reproducción (Player Screen)

Evidencia: `IMG:00.21.23 (3)`, `IMG:00.21.23 (2)` (mini-player).

- Carátula grande en la parte superior (placeholder con forma de onda cuando no
  hay art; el fondo del player es negro puro con la carátula al centro).
- Fila de "me gusta / no me gusta" (thumbs up / thumbs down) a la izquierda →
  **valoración de pistas con 2 estados** (like/dislike).
- Botones a la derecha de la carátula: **toggle de visualización** (icono de
  barras) y **temporizador de sueño** (icono de reloj).
- Chips/botones de **repeat** y **shuffle** visibles en la fila de controles.
- Título de la pista en un "pill" (texto que puede hacer marquee si es largo);
  artista en un chip debajo.
- Seekbar con **waveform/visualización superpuesta** sobre la zona de
  reproducción.
- Transporte completo: `-10 s` (rewind), anterior, play/pause (botón circular
  grande blanco), siguiente, `+10 s` (forward), y overflow `...`.
- Tiempos: transcurrido a la izquierda, total a la derecha (`0:02` / `2:00:19`).
- Info técnica de audio en un chip inferior: **`48 KHZ 128 KBPS MP3`**
  (frecuencia de muestreo + bitrate + códec detectados del archivo).

Estado en PowerampFree: `HECHA` (sprint 1, 2026-10-22) — repeat modes
(off/one/all; nativo vía `ExoPlayer.repeatMode`, dual con loop del beam en ONE
y wrap del orquestador en ALL), shuffle (nativo directo; dual Fisher-Yates con
la pista elegida/actual primero), ±10 s de nudge de seek, chip de formato
técnico desde el formato real del decoder (`48 kHz · 64 kbps · MP3`), rating
like/dislike persistido (`track_ratings`), sleep timer con cuenta atrás y
pausa automática, título con marquee. La carátula grande y la waveform sobre
el seekbar quedan para el sprint de carátulas (ver roadmap, orden 8).

## 2. Navegación inferior (bottom navigation)

Evidencia: `IMG:00.21.23` (todas).

- 4 tabs fijos: **Biblioteca** (rejilla), **Ecualizador/Visualizador** (barras),
  **Búsqueda** (lupa), **Cola/Menú** (hamburger).
- Mini-player persistente encima de la barra: carátula pequeña, título, artista,
  botón play y barra de progreso.

Estado en PowerampFree: `HECHA` (sprint 2, 2026-10-22) — bottom nav de 5 tabs
(▶ Reproductor, 📚 Biblioteca, 🎚 EQ, 🔍 Buscar, 📋 Cola) y **mini-player
persistente** sobre la barra (título/artista marquee + ⏸/⏭; tap abre el
player). La barra de progreso en el mini-player queda para el sprint de
carátulas/fondo.

## 3. Explorador por carpetas (Folders)

Evidencia: `IMG:00.21.23 (1)`.

- Breadcrumb "Folders" con flecha ↑ para subir de carpeta.
- Cabecera de carpeta con carátula grande, **nombre de carpeta** + **nombre de
  dispositivo/alistado** y resumen **nº de pistas | duración total**
  (`242 | 186:59:48`).
- Fila de acciones de cabecera: **shuffle**, **play**, **búsqueda**,
  **"Select"** (modo selección múltiple) y overflow `⋮`.
- Filas de pistas: carátula/placeholder, título, artista y
  **`duración | formato`** (`2:00:19 | mp3`).

Estado en PowerampFree: `HECHA` el núcleo (sprint 3, 2026-10-22) — explorador
con breadcrumb, navegación por subcarpetas desde `RELATIVE_PATH`, cabecera con
totales del subárbol (`n pistas · duración total`), filas con duración,
acciones ▶ Todo / 🎲 (arranque aleatorio) / ☑ Select, retroceso con BACK y ↑.
Pendiente: carátula/disp. en la cabecera y placeholder de carátula por fila
(sprint carátulas, orden 8).

## 4. Menú contextual de pista (long-press)

Evidencia: `IMG:00.21.23` (popup sobre el player).

Cabecera con carátula, título, artista, `duración | formato` y los botones
like/dislike. Acciones observadas:

- **Delete** (borrar el archivo físico).
- **Add to Playlist**.
- **Album Art** (asignar/ver carátula).
- **Info/Tags** (ver/editar etiquetas ID3).
- **Lyrics** (ver/editar letras).
- Navegación por metadatos: **Artist**, **Album**, **Folder**, **Genre**
  (saltar a la lista de ese artista/álbum/carpeta/género).

Estado en PowerampFree: `PENDIENTE`.

## 5. Letras (Lyrics)

Evidencia: `IMG:00.21.23 (2)`.

- Vista de letras con **línea actual resaltada** (blanca y en negrita) y el
  resto atenuado → letras sincronizadas (o al menos auto-scroll con
  resaltado).
- Cabecera compacta con carátula + título/artista + `duración | formato`.
- Acceso desde el menú contextual de la pista (sección 4).

Estado en PowerampFree: `PENDIENTE`.

## 6. Ajustes — pantalla raíz (Settings)

Evidencia: `IMG:00.21.28 (4)`, `IMG:00.21.28 (3)`.

Categorías con icono + título + resumen:

- **Look and Feel** — Skin, player interface, language, notifications.
- **Audio** — Crossfade, replay gain, volume, output.
- **Visualization** — Faded controls opacity, preset duration.
- **Background** — Blur, details, intensity, saturation.
- **Album Art** — Download, quality, cache cleanup.
- **Library** — Rescan, music folders, list, queue options.
- **Headset/Bluetooth** — Pause/resume on connection, headset buttons.
- **Lock Screen** — Poweramp lock screen options.
- **Misc** — Scrobbling, Android Auto, other tweaks.
- **About** — Version/changelog, translations info.
- Otras acciones: **Get Support**, **Export Settings/Data**,
  **Import Settings/Data**, **Send Errors To Developer** (toggle).

Estado en PowerampFree: `HECHA` el núcleo (sprint 5, 2026-10-22) — pantalla
raíz con las 10 categorías (icono+resumen; pendientes atenuadas con su sprint),
sub-pantalla **Biblioteca** con Rescan real, **Export/Import** de datos
(prefs + presets de EQ → JSON en `Documents/PowerampFree`, importación por
SAF o "Último backup vía MediaStore"), **About**. Entradas: ⚙ en el reproductor
y tarjeta ⚙ en la Biblioteca (overlay sobre el tab activo). Pendiente: las
sub-pantallas de Ajustes propias de cada bloque (sprint 7+).

## 7. Ajustes — Look and Feel

Evidencia: `IMG:00.21.28 (1)`, `IMG:00.21.28 (2)`.

- **Skin**: cambiar el tema visual; opciones por skin (Pro Buttons, Static
  Seekbar).
- **Follow Day/Night Mode** (toggle): seguir el modo día/noche del sistema si
  el skin lo soporta.
- **Settings Theme**: Default / Light / Dark / Follow Day/Night Mode.
- **Settings Font**: Default / Alternative font / **Bold**.
- **Screen Orientation**: Default / Portrait (Vertical) / Landscape (Horizontal).
- **Animations**: Disabled / Fast / Default.
- **Start at Library** (toggle): pantalla de arranque = biblioteca; back desde
  el player vuelve a la lista actual.
- **Stretch Overscroll** (toggle, ON).
- **Hide Status Bar**, **Keep Screen On** (toggles).
- **Settings Shortcuts in Main Menu** (slider Disabled→Max).
- **Language** (Auto), **Icon** (elegir icono de launcher).
- Enlaces a sub-pantallas: **General**, **Player Screen**, **Lyrics**,
  **Notifications**.
- **Notifications** → opciones extra de lista: *Filename As Title*, *Show Track
  Number*, etc.

Estado en PowerampFree: `HECHA` la parte central (sprint 5) — **Settings
Theme** (Automático/Claro/Oscuro aplicado en vivo), **acento** (5 colores +
predeterminado), **fuente negrita**, **Start at Library** (arranque en la
pestaña Biblioteca), Búsqueda/categorías como del sprint 2. Pendiente: skin,
orientación, animaciones, overscroll, hide status bar, idioma e icono.

## 8. Ajustes — Audio (índice de subcategorías)

Evidencia: `IMG:00.21.27 (5)`.

La categoría Audio es un índice con estas sub-pantallas:

- **Audio Info** — info detallada del procesado de audio; también accesible
  con long-press sobre el meta-info pequeño de las pantallas Main y EQ.
- **Crossfade, Fade, and Gapless** — opciones de crossfade, tipo/longitud de
  fade y gapless.
- **Replay Gain (RG)** — habilitar RG, elegir fuente (track/album), valores
  de preamp.
- **Audio Focus** — Pause/Resume/**Duck** en llamadas/notificaciones/arranque.
- **Equalizer** — settings del EQ (nº de bandas, frecuencias).
- **Resampler** — resampling/dither.
- **Direct Volume Control (DVC)** — opciones DVC, DVC para Bluetooth.
- **Output** — AudioTrack, Hi-Res Output, OpenSL options.
- **Advanced Tweaks** — volume levels, MusicFX, reset de presets de
  ecualizador/reverb.

Estado en PowerampFree: `PARCIAL` — crossfade hecho y audio focus básico
viaja en media3; faltan ReplayGain, DVC, resampler, selección de salida y el
resto.

## 9. Ajustes — Misc (scrobbling, Android Auto)

Evidencia: `IMG:00.21.28 (3)` (resumen "Scrobbling, Android Auto, other
tweaks").

Estado en PowerampFree: `PENDIENTE`.

---

## 10. Ajustes — Headset/Bluetooth

Evidencia: `IMG:00.21.25`.

- **Connection**:
  - **Pause On Headset Disconnect** (toggle ON) — pausa al desconectar
    auricular cableado/Bluetooth/USB DAC.
  - **Resume On Wired Headset** (toggle ON) — reanuda al conectar cable.
  - **Resume On Bluetooth** (toggle ON) — reanuda al conectar Bluetooth.
  - Enlace a **Audio Focus** ("few other play/resume options are available in
    Audio Focus settings").
- **Buttons**:
  - **Respond To Buttons** (toggle ON) — habilita controles de auricular/BT.
  - **Wired Headset** — acción seleccionable por pill: *Single press* /
    *Double/triple press for next/prev. track* / *Long press for next track
    (when playing)*.
  - Sección **Bluetooth** equivalente (idénticos pills).

Estado en PowerampFree: `PARCIAL` — media3 ya gestiona audio focus y
media-button básico; faltan estas opciones configurables.

## 11. Ajustes — Library

Evidencia: `IMG:00.21.25 (1)`, `IMG:00.21.25 (2)`.

- **Track Duration For Per Track Progress and -10/+10s buttons** (slider,
  "At least: 45 min.") — umbral de duración para activar progreso por pista y
  botones ±10 s.
- **Count As Played** (slider, "after playing at least: 50%") — umbral para
  contar una pista como reproducida.
- Nota: *individual playlists and folders can be set to keep each track
  progress via their header menu / List Options* → **progreso por pista
  configurable por lista/carpeta**.
- **Scanner**: Rescan (Folders: 1, Playlists: 0, Songs: 242), Full Rescan
  (limpia la BD de tags), **Music Folders** (seleccionar carpetas para
  Folders/Library), **File Access Legacy Mode** (acceso directo a archivos,
  requiere permiso extra; no soportado en Android 13+ edición Play).
- Filtros: **Ignore Short Tracks (Notifications, etc.)** (< 6 s),
  **Ignore Video Tracks** (toggle ON).
- **Advanced**: **Store/Restore Per Track Progress** (útil para podcasts/sets
  largos, toggle ON), **-10/+10s Rewind Pro Buttons** (convierte los botones
  `<<<`/`>>>` de cambio de categoría en ±10 s para pistas largas, requiere
  opción "Pro Buttons" del skin).
- Sub-pantallas: **Lists** (list click action, lists/categories options),
  **Search** (search results playback options), **Queue** (queue options),
  **Playlists** (*user created playlists import/export* and options),
  **Shuffle** (shuffle options), **Scanner** (folders and library scanner
  options).
- **Reset stats** (reset tracks played times, last played, and related stats).
- **Restore Defaults**.

Estado en PowerampFree: `PENDIENTE` — tenemos cola persistente; faltan
listas guardadas, stats de reproducción y opciones de shuffle/scan.

## 12. Ajustes — Album Art

Evidencia: `IMG:00.21.26 (3)`, `IMG:00.21.26 (4)`.

- **Use 24-bit RGB** (ON) — mayor resolución de color, 2× memoria por imagen.
- **Send High Resolution Album Art** (ON) — envía carátula en alta resolución a
  lock screen, widgets y consumidores de media API.
- **Always Send Album Art** (OFF) — para smartwatches/otros dispositivos.
- **API Compatibility** (Experiment, OFF) — cambiar si la carátula no es visible
  en Android Auto / apps de terceros.
- **Also Search By Title/Filename** (ON) — busca carátula aunque no haya tags.
- **Prefer Downloaded Album Art** (OFF) — prefiere la descargada sobre
  `Cover.jpg`/`AlbumArt.jpg` de la carpeta.
- **Aspect Ratio**: Default / **Keep Aspect Ratio** (seleccionado) / Square.
- **Increase Resolution** (Experiment).
- **Download**: Download Album Art (OFF), Download Artist Images (OFF, incluye
  Album Artists y Compositores), High Resolution (OFF), Mobile User-Agent
  (OFF), **Download Only On Wi-Fi** (ON), Reset Negative Status (borra el
  estado "no encontrado"), Delete Cache.
- **Advanced Tweaks**: Force Default Image, Use 24-bit RGB, Send High
  Resolution Album Art, Always Send Album Art, API Compatibility (ver arriba).
- **Restore Defaults**.

Estado en PowerampFree: `PENDIENTE` — no mostramos ni enviamos carátula.

## 13. Ajustes — Background (fondo derivado de la carátula)

Evidencia: `IMG:00.21.26 (6)`.

- **Enable Blurred Backgrounds** (ON), **List Background** (ON),
  **Lyrics Background** (ON) — el fondo borroso se puede aplicar al player,
  a las listas y a la pantalla de lyrics (estos dos se atenúan para
  legibilidad). Nota: *skins may override, change, or completely disable
  background*.
- **Background Gradient** (0–10, actual 4; extremos None/Max) y
  **Background Gradient Color** (hex, actual `#000000`, con muestrador).
- **Background Gradient For Lists** (ON) — aplica el degradado del fondo
  también a las listas (el skin puede anularlo).
- **Background Blur** (0–10, actual 5) con extremos *Less / More*.
- **Background Details** (0–10, actual 5) con extremos *Solid Color /
  Detailed*.
- **Background Intensity** (100%).
- **Background Saturation** (150%).

Estado en PowerampFree: `PENDIENTE` — el player usa fondo plano.

## 14. Ajustes — Visualization (visualizador MilkDrop)

Evidencia: `IMG:00.21.27 (1)`.

- **Visualization Delay** — retardo extra para sincronizar
  audio/visualización/lyrics, **configurable por tipo de salida/dispositivo**.
- **Presets**:
  - **Rescan Presets** — "Built-in: 11"; Poweramp carga presets **`.milk`** y
    zips (hasta 10k presets) desde
    `/Android/data/com.maxmpz.audioplayer/milk_presets`.
  - **Full Presets Rescan**.
  - **Hide Unliked Presets** (OFF) — oculta los presets que no gustan; aviso:
    *presets disabling removes their ratings*.
  - **Spectrum Bars/Built-in Presets** (ON), **Built-in Presets** (ON).
  - **3rd Party Presets → Folder Presets** (ON, misma ruta `milk_presets`).
- **Opciones de render/overlay del visualizador** (evidencia
  `IMG:00.21.27 (2..4)`): *Faded Controls Opacity* (50%), *UI Timeout*
  (1500 ms), *Visible Album Art* (ON), *Ignore Touch* (OFF), *Track Opacity*
  (25%), *Scaled Bars For Faded Controls* (ON), *HD* (ON), *Crop Aspect*
  (OFF), *Force 30 FPS* (OFF), *Strict* (OFF), *Preset Duration*
  ("Change each: 10s"), *Top Visualization Panel Opacity* (60%),
  **Visualization On Player Screen** (OFF, se activa con el botón de barras
  del player), **Equalizer Screen Spectrum**: Disabled / Classic /
  **Rounded** (seleccionado), **Visualization in Library** (OFF).
- **Restore Defaults**.

Estado en PowerampFree: `PARCIAL` — tenemos visualizador propio de spectrum
(FFT/Hann, barras); no hay motor de presets `.milk` (formato de terceros,
fuera del alcance clean-room por ahora).

## 15. Menú principal (overlay) y Búsqueda

Evidencia: `IMG:00.21.28 (5)` (menú), `IMG:00.21.29`, `IMG:00.21.29 (1)`.

**Menú principal** (hoja inferior sobre la UI):
- Marca + "Full Version" (estado de licencia).
- **Settings** con **pills de acceso rápido** a sub-pantallas:
  *Bluetooth*, *AAudio Output*, *Advanced Tweaks*, *Equalizer*, *Resampler*.
- **Changelog** y **Help**.
- Mini-player integrado en el menú (título/artista + play).

**Búsqueda**:
- Campo de búsqueda con botón ✕ (limpiar) y lupa.
- **Filtros por categoría** en pills horizontales: *All*, *Albums*, *Artists*,
  *Album Artists*, *Folders* (y más a la derecha, scroll horizontal).
- Acciones: shuffle, play, **Select**, overflow `⋮`.
- **Historial de búsqueda** persistente: entradas individuales con ✕ para
  borrar una a una + acción **"Clear search history"**.

Estado en PowerampFree: la **búsqueda** es `HECHA` (sprint 2): campo + filtros
por categoría (Todo/Título/Artista/Álbum), resultados reproducibles (encola
los resultados), historial persistente con "Borrar" todo. El **menú principal
overlay** quedó cubierto por la pantalla de Ajustes (sprint 5): acceso vía
⚙ del reproductor y tarjeta ⚙ de la Biblioteca. Pendiente: ✕ por entrada
individual del historial.

## 16. Pantalla de Ecualizador (gráfica)

Evidencia: `IMG:00.21.29 (1)`.

- **3 tabs superiores**: icono de faders (EQ gráfico), disco (Tone),
  altavoz-en-paréntesis (Limit/DVC).
- **EQ gráfico de 10 bandas** con **Preamp** a la izquierda: frecuencias
  `31, 62, 125, 250, 500, 1K, 2K, 4K` (+ más a la derecha con scroll
  horizontal). Sliders verticales con pista iluminada en verde y valor dB
  bajo cada banda (0.0 por defecto); Preamp muestra `0.0`.
- **Curva de respuesta** en vivo (línea verde) bajo los sliders.
- **Chip de estado** resumen: `NO DVC EQ 10 TON LMT` (estado actual: sin DVC,
  EQ activo, 10 bandas, Tone activo, Limiter activo) con icono ⓘ.
- Botones-toggles a la izquierda: **Equ**, **Tone**, **Limit**.
- Botón central **Preset** + overflow `⋮`.
- **Tone**: dos **knobs rotatorios** — **Bass** (0%) y **Treble** (0%).

Estado en PowerampFree: `HECHA` el núcleo (sprint 4, 2026-10-22) — tabs
EQ/Tone/Limit; EQ gráfico de **10 bandas (31–16 kHz)** con faders verticales
propios (toque o arrastre, pasos de 0.5 dB, pista rellena bottom-up) y
**Preamp** a la izquierda; curva de respuesta de las capas resueltas; **chip
de estado** `NO DVC · EQ n · TON · LMT`; **Tone** con dos knobs rotatorios
reales (Bass 250 Hz / Treble 4 kHz, barrido de 270°, máx +12 dB)
implementados como 2 bandas *shelf* reales del motor paramétrico; **Limit**
conmutador del módulo limitador suave. Pendiente: DVC (device monitoring),
scroll horizontal para más de 10 bandas y limiter UI de medidor.

## 17. Biblioteca — pantalla de categorías

Evidencia: `IMG:00.21.29 (2)`.

Pantalla "Library" con título + overflow `⋮` y lista de categorías (cada una
con icono circular de color):

- **All Songs**, **Folders**, **Folders Hierarchy**, **Albums**, **Artists**,
  **Album Artists**, **Genres**, **Years**, **Composers**, (una categoría más
  cortada), y **Queue** (con estado, p. ej. "empty").

Estado en PowerampFree: `HECHA` (sprint 2) en su núcleo — pantalla Biblioteca
con tarjetas de categoría **All Songs / Albums / Artists / Genres / Folders**
(más tarjeta de importación SAF), cada una con navegación a listas de pistas
reproducibles y botón ▶ Todo; también la lista "Todo" plana. Sin categorías
"extra" (Album Artists/Years/Composers/Hierarchy) — quedan para sprint de
Library avanzado. La pantalla de **Cola** es tab propio (ver bloque 8).

## 18. Opciones de lista por contexto (List Options)

Evidencia: `IMG:00.21.29 (3)`.
Diálogo **"List Options: Folder Songs"** (el título cambia según la lista:
folder/playlist) con icono de engranaje:

- **List Memory**: checkboxes **List Position** y **Per Track Progress** —
  *List Position is restored when the list is started via header Play button
  or by navigation with* `<<<` `>>>`; *Per Track Progress overrides the
  Store/Restore Per Track Progress option*.
- **Sort**: radio **By track #** / **By disc and track #** / **By title**.
- Botón **Close**.

Estado en PowerampFree: `HECHA` la parte Sort (sprint 3): botón ⇅ en la
cabecera del explorador de carpetas con diálogo Título / Nº pista / Duración
(✓ sobre la activa). Pendiente: List Memory (posición/progreso por pista).

## 19. Menú de cabecera de carpeta

Evidencia: `IMG:00.21.29 (4)`.

Popup con la cabecera (carátula, nombre de carpeta `Rohn Audio`, dispositivo
`24117RN76L`, resumen `242 | 186:59:48`) y acciones:

- **Select Folders** — selector de carpetas de la biblioteca.
- **Rescan** — reescaneo del scanner.
- **Add to Launcher** — atajo directo en el launcher para esa carpeta.
- **List Options** — abre el diálogo de la sección 18.

Estado en PowerampFree: `PARCIAL` (sprint 3) — la cabecera del explorador ya
lleva las acciones ▶/🎲/☑/⇅ y "Recargar biblioteca" reescanea MediaStore
(equivalente de Rescan). Pendiente: Select Folders por casillas del source y
Add to Launcher (shortcut).

## Resumen de cobertura

| #  | Funcionalidad | Estado |
|----|---------------|--------|
| 1  | Player screen (rating, sleep timer, repeat/shuffle, info de formato) | HECHA (sprint 1; carátula+waveform en sprint 8) |
| 2  | Bottom nav + mini-player persistente | HECHA (sprint 2; progreso en mini → sprint 8) |
| 3  | Explorador de carpetas (breadcrumb, totales, selección) | HECHA núcleo (sprint 3; carátulas en sprint 8) |
| 4  | Menú contextual de pista (Delete/Add to Playlist/Tags/Artist/…) | PENDIENTE |
| 5  | Lyrics (línea sincronizada resaltada) | PENDIENTE |
| 6  | Settings — pantalla raíz + Export/Import settings/data | HECHA núcleo (sprint 5; sub-pantallas: sprint 7+) |
| 7  | Settings — Look and Feel (skin, tema, orientación, animaciones) | HECHA central (sprint 5); rest de opciones pendiente |
| 8  | Settings — Audio (ReplayGain, DVC, resampler, output, focus) | PARCIAL |
| 9  | Settings — Misc (scrobbling, Android Auto) | PENDIENTE |
| 10 | Settings — Headset/Bluetooth (pause/resume en conexión, botones) | PARCIAL |
| 11 | Settings — Library (scanner, filtros, progreso por pista, stats) | PENDIENTE |
| 12 | Settings — Album Art (descarga, calidad, envío a lock screen) | PENDIENTE |
| 13 | Settings — Background (fondo borroso/gradiente de la carátula) | PENDIENTE |
| 14 | Settings — Visualization (presets .milk, ajustes de render) | PARCIAL |
| 15 | Menú principal (overlay) + Búsqueda (filtros, historial) | Búsqueda HECHA + acceso a ajustes (sprint 5); ✕ por entrada pendiente |
| 16 | Pantalla de EQ (10 bandas + curva + tone/bass/treble + chip estado) | HECHA núcleo (sprint 4; DVC + limiter-meter pendiente) |
| 17 | Biblioteca — categorías (All Songs/Albums/Artists/Genres/…) | HECHA núcleo (sprint 2); categorías extra en sprint 9 |
| 18 | Opciones de lista por contexto (List Memory, Sort) | HECHA Sort (sprint 3); List Memory en sprint 9 |
| 19 | Menú de cabecera de carpeta (Select Folders/Rescan/Add to Launcher) | PARCIAL (sprint 3: acciones ▶/🎲/☑/⇅ + Rescan-ish) |

---

## Orden de implementación (roadmap acordado 2026-10-22)

Nota: la carpeta `PowerampImages\` de las capturas ya no existe; las referencias
`IMG:` quedan históricas. La libertad de diseño es total: importa el
**comportamiento**, no el aspecto.

Sprints ejecutados en este orden (cada uno con build verde + tests + docs):

1. **Sprint 1 — Player screen completo (bloques 1, y soporte del 16)** ✅ hecho:
   - repeat modes (off/one/all) en ambos motores (nativo: `ExoPlayer.repeatMode`;
     dual: loop del beam en ONE, wrap del orquestador en ALL), sesión + notificación;
   - shuffle (nativo directo; dual: reordenado Fisher-Yates con la pista actual
     primero, aplicado también al encolar con shuffle activo);
   - **±10 s** de avance/rebobinado en la fila de transporte;
   - **chip de formato** (`48 kHz · 320 kbps · OPUS`) tomado del `Format` real
     del decoder (no de metadatos declarados);
   - **rating like/dislike** persistente por pista (prefs `track_ratings`),
     visible sobre la fila Reproduciendo;
   - **sleep timer** con cuenta atrás y pausa automática;
   - **marquee** del título largo.
2. **Sprint 2 — Navegación (bloques 2 + 15 + 17)** ✅ done (2026-10-22): bottom nav
   de 5 tabs, mini-player persistente, pantalla de cola con tap-para-saltar
   (`SEEK_QUEUE` + `QueueBus`), búsqueda con filtros + historial persistente,
   categorías Biblioteca (All Songs/Albums/Artists/Genres/Folders + SAF) sobre
   MediaStore (`MediaTrack` extendido con albumId/track/folder + `MediaGenres`).
3. **Sprint 3 — Carpetas (bloques 3 + 19 + 18)** ✅ done (2026-10-22): explorador
   con breadcrumb (`RELATIVE_PATH`, subida con ↑ y BACK), cabecera con totales
   del subárbol, ▶/🎲/☑ Select/⇅ sort de List Options, filas con duración,
   selección múltiple reproducible. Aplicado insets de status bar
   (descubierto: MIUI edge-to-edge se tragaba taps y<~100px).
4. **Sprint 4 — EQ gráfico (bloque 16)** ✅ done (2026-10-22): tabs EQ/Tone/Limit,
   10-band graphic (31–16 kHz) con faders verticales + Preamp, curva de las
   capas resueltas, knobs Tone = 2 bandas shelf reales, limitador conmutable,
   chip `NO DVC · EQ n · TON · LMT`; Push y persistencia por `SET_PRESET`.
   `:core:audio` 49 tests (capas + limiter disable).
5. **Sprint 5 — Settings + tema (bloques 6 + 7)** ✅ done (2026-10-22): raíz de
   ajustes con 10 categorías, Look & Feel en vivo (Auto/Claro/Oscuro, acento,
   negrita, Start at Library), sub-pantalla Biblioteca (rescan) y
   Export/Import de datos a JSON. Bug del import (org.json narrows Long→Int)
   fijado con claves de ms como Long + lector tolerante.
6. **Sprint 6 — Pista y letras (bloques 4 + 5)**: menú contextual de pista
   (like/dislike/artist/album/folder navegar; Album Art, Tags-Info; delete con
   confirmación) y letras sincronizadas LRC si existen junto al archivo.
7. **Sprint 7 — Audio/Headset settings (bloques 8 + 10)**: pause/resume en
   conexión de headset/BT, botones de auricular, opciones de focus.
8. **Sprint 8 — Carátulas y fondo (bloques 12 + 13)**: art de MediaStore hacia
   lockscreen/mini-player, fondo derivado de la carátula (sin descarga).
9. **Sprint 9 — Library avanzada (bloque 11)**: scan options, progreso por
   pista, stats/count-as-played, filtros.
10. **Sprint 10 — Visualización avanzada + Misc (bloques 14 + 9)**: opciones de
    render del visualizador propio, scrobbling/Auto si hay tiempo.

