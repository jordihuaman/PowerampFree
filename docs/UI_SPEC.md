# Poweramp → PowerampFree · Especificación visual (pasada 1)

Referencia observada: **Poweramp build-981-uni**, `com.maxmpz.audioplayer`,
dispositivo `24117RN76L` (Android 16, **1080×2400 px @ 450 dpi** → 384×853 dp).

Método: `adb` + `screencap` + `uiautomator dump`, sin modificar ajustes. Todas las
medidas en **px de dispositivo** salvo que se indique `dp` (px ÷ 2.8125).
Evidencia: `docs/ui-capture/` (ver `INDEX.md`).

---

## 1. Sistema de diseño

### 1.1 Paleta (muestreada de las capturas)

| Token | Hex | Uso observado |
|---|---|---|
| `bg` | `#000000` | Fondo global, player, biblioteca, EQ, búsqueda |
| `surface-1` | `#0E0E0E` | Pastillas de título/artista, chips |
| `surface-2` | `#181818` | Nav inferior, mini-player, tarjetas, teclas del EQ |
| `surface-3` | `#222222` | Rieles, separadores, botones de acción |
| `surface-4` | `#313131` | Riel del ecualizador, fondo del preset |
| `stroke` | `#3F3F3F` | Contorno de chips/botones de acción |
| `text` | `#FFFFFF` | Títulos, iconos activos |
| `text-2` | `#D1D1D1` | Texto secundario fuerte |
| `text-3` | `#999999` / `#A8A8A8` | Artista, iconos inactivos, tiempos |
| `accent-eq` | `#00FF00` | Barras del EQ, línea de respuesta |
| `accent-eq-glow` | `#00A000` | Halo/desvanecido del verde |
| `like` | `#FFFFFF` sobre `#0E0E0E` | Thumb up/down |
| Iconos de categoría | `#5066D8` `#4D7AC5` `#5392A2` `#663E63` `#235E3D` | Círculos de All Songs, Folders, Years, Genres, Composer |

No hay modo claro en lo observado: **todo negro puro** con grises de superficie.
El acento cromático solo aparece en los iconos de biblioteca y en el EQ (verde neón).

### 1.2 Tipografía

- Familia: sans **condensada** (aspecto Roboto Condensed / gothic), siempre **bold**.
- Títulos de pantalla: caja de texto de 141 px de alto (≈50 dp) → ≈32–34 sp.
- Títulos de fila: 72 px (≈26 dp) → ≈20 sp, una línea con recorte por elipsis.
- Secundarios: 64 px (≈23 dp) → ≈16 sp; metadatos (duración/formato): ≈14 sp.
- Sin mayúsculas forzadas; el énfasis es por peso y tamaño, no por color.

### 1.3 Formas y espaciado

- **Todo es pastilla**: chips y botones con radio = altura/2.
  - Chip estándar: **191×101 px** (68×36 dp), radio 50 %.
  - Botón de acción de cabecera: **152×101** (54×36 dp).
- Márgenes laterales: **34–39 px** (≈12–14 dp) para arte; **23 px** (8 dp) para la barra inferior.
- Gaps entre chips: **11 px** (4 dp).
- Elevación: nula (sin sombras); la jerarquía se hace por contraste de superficie.
- Separadores: líneas de 1 px `#222222` solo en listas de ajustes; las listas de
  música no llevan separador, usan aire.

---

## 2. Navegación global

### 2.1 Barra inferior (4 tabs)

Contenedor: **1034×146 px** (368×52 dp) en `(23,2231)-(1057,2377)`, radio pastilla,
fondo `#181818`.

| Tab | Centro | Icono observado |
|---|---|---|
| Library | x=157 | rejilla 2×2 |
| Equ/Effects | x=413 | 3 barras verticales |
| Search | x=669 | lupa |
| Main Menu | x=925 | hamburguesa 3 líneas |

- **No hay quinto tab**: el reproductor se abre tocando el mini-player.
- En pantallas con mini-player la barra crece a **354 px**: mini-player (146) +
  seekbar (90) + tabs (146).
- Cada tab mide 256×134 px y tiene `content-desc` accesible
  (`Library`, `Equ/Effects`, `Search`, `Main Menu`).

### 2.2 Mini-player persistente

- Fila de **1034×146 px** pegada encima de los tabs.
- Miniatura de carátula **101×101** (36 dp) a la izquierda (x≈118).
- Título (718×52) + artista (248×54) en dos líneas, recortados.
- Botón play a la derecha: **169×135**.
- Seekbar **944×90** justo encima de los tabs, línea fina con thumb circular.
- Fondo `#181818`, sin borde; separado del contenido por cambio de superficie.

### 2.3 Sofisticaciones que sí están

- **Scroller A-Z** vertical de 67 px de ancho, letras `#999999`, alfabeto completo
  y `#` al final; no es una barra, son letras tocables.
- **Toasts** anclados abajo (encima de la barra): pastilla oscura, icono a la
  izquierda, título bold y subtítulo gris. Ej.: "Visualization – Full Screen /
  Long press for more options".
- **Bottom sheet** para el menú principal: sube desde abajo, esquinas superiores
  redondeadas (~28 dp), fondo `#181818`, tirador ausente, se cierra tocando fuera.

---

## 3. Pantalla de reproducción (Player)

Distribución vertical (px):

| Zona | Bounds | Notas |
|---|---|---|
| Barra de estado | 0–110 | fondo negro, iconos del sistema |
| Carátula / arte | `[36,146]-[1044,1154]` (1008×1008) | placeholder de onda si no hay art |
| Like / Unlike | `[39,1048]-[185,1150]`, `[188,1048]-[334,1150]` | flotan sobre la esquina inferior del arte |
| Lyrics / Menu | `832–935`, `938–1041` @ y=1047–1150 | iconos sueltos, sin pastilla propia |
| Título | `[39,1214]-[1041,1294]` | pastilla `#0E0E0E`, texto 20 sp, **marquee** si desborda |
| Artista | `[39,1296]-[1041,1367]` | pastilla separada, 16 sp, tocable |
| Chips de modo | y `1426–1527` | Visualización `39–230`, Temporizador `241–432`, Repeat `648–839`, Shuffle `850–1041` |
| Waveform + transporte | y `1527–2164` | ver abajo |
| Chip técnico | `[375,2154]-[704,2207]` | `48 KHZ 128 KBPS MP3`, 14 sp, pastilla |
| Nav inferior | y `2231–2377` | 4 tabs |

Transporte (centros, y=1846):

| Control | Tamaño | Centro x |
|---|---|---|
| −10 s | 113×113 | 162 |
| Anterior | 169×169 | 308 |
| Play/Pause | **281×281** | 540 |
| Siguiente | 169×169 | 770 |
| +10 s | 113×113 | 918 |

- Tiempos: transcurrido `[47,2041]-[147,2097]`, total `[899,2041]-[1030,2097]`,
  formato `0 07` / `2 00 19` (espacios en vez de dos puntos).
- La waveform ocupa **1080×518** (y 1586–2104) y es un `SurfaceView`
  (`id/milk`): dibuja barras verticales grises alrededor del botón play, con la
  barra de progreso integrada. No es un seekbar fino: es el protagonista visual.
- El arte sin carátula usa un **placeholder de onda** estático en gris muy oscuro
  (`#181818`) sobre negro.
- Iconos: trazo fino (~1.5–2 dp), esquinas redondeadas, tamaño 24–28 dp dentro de
  chips de 36 dp.

### Estados observados

- Pulsar **Lyrics** sin letra: no cambia de pantalla (queda en el player).
- Pulsar **Visualization**: muestra toast y alterna el modo visualización; el menú
  de opciones está en **long-press**.
- Repeat/Shuffle alternan entre pastilla apagada (icono `#999999`) y encendida
  (icono blanco), con texto `Repeat Off` / `Shuffle Off`.

---

## 4. Biblioteca

### 4.1 Categorías (raíz)

- Título "Library": caja 346×141 en `(196,216)`; margen izquierdo 23 px.
- Overflow `⋮` en círculo de 101 px, arriba a la derecha.
- Filas de **180 px de alto** (64 dp): icono circular **101×101** (36 dp) en
  x=118, texto a x≈193–324 según longitud.
- Categorías en orden: **All Songs, Folders, Folders Hierarchy, Albums, Artists,
  Album Artists, Genres, Years, Composers, …** y al final **Queue** (con subtítulo
  "empty" cuando no hay cola).
- Cada icono es un círculo de color distinto con glifo blanco; no hay chevrons.

### 4.2 Cabecera de listas (All Songs / Folders / carpeta)

- **Breadcrumb**: pastilla "↑ Library" / "↑ Folders" de **245×99** en y=182,
  borde `#3F3F3F`, fondo transparente, texto + flecha.
- Icono de sección **135×135** (48 dp) a x≈102 con el mismo color de la categoría.
- Título **421×141** (≈32 sp) a continuación.
- Fila de acciones a y=548:
  `shuffle` 152×101 · `play` 152×101 · `search` 152×101 · `Select` 188×101 · `⋮` 114×114.
- En **carpeta** el encabezado es más rico: arte de fondo 1080×774, nombre
  (995×72) + dispositivo (1012×64) + contador `242 | 186:59:48`, y la fila de
  acciones debajo, sin breadcrumb duplicado.

### 4.3 Filas de pista

- Altura de fila: **270 px** (96 dp).
- Miniatura **224×224** (80 dp) en x=34–258, radio ~12 dp, placeholder de onda.
- Texto desde x=283 hasta 1055 (772 px):
  - título 72 px (20 sp),
  - artista 64 px (16 sp),
  - tercera línea `41:19 | mp3` (14 sp) con icono de nota/onda delante.
- Pista en reproducción: **fila resaltada con pastilla** `#181818` de esquinas
  redondeadas que abarca miniatura + textos, como se ve en `lib-folder-tracks`.
- Sin separadores; el ritmo lo da el alto de fila.

---

## 5. Búsqueda

- Campo de búsqueda: pastilla de ancho completo (márgenes 23 px), fondo `#181818`,
  lupa a la izquierda, **×** a la derecha, placeholder "Search".
- Fila de chips de filtro: `All` (activo, fondo claro), `Albums`, `Artists`,
  `Album Artists`, `Folders`, … scroll horizontal; el chip activo invierte colores.
- Segunda fila de acciones: shuffle, play, `Select`, `⋮`.
- Historial: filas de 101 px con la consulta a la izquierda y **×** a la derecha;
  acción centrada "Clear search history" en gris.
- Al entrar, el teclado se abre automáticamente y **tapa la nav inferior**.

---

## 6. EQ / Efectos

- Tres iconos de sección arriba, en una barra tipo segmento: EQ (3 barras), Tone
  (círculo), Limit (`(o)`); el activo va en blanco, el resto gris.
- **10 bandas** + Preamp. Etiquetas: `Preamp, 31, 62, 125, 250, 500, 1K, 2K, 4K, 8K, 16K`
  con valor `0.0` debajo.
- Riel vertical `#313131`; el segmento activo es **verde neón `#00FF00`** con halo
  `#00A000`; el thumb es una cápsula blanca redondeada de ≈34×100 px.
- Barra de respuesta (fondo `#313131`) atravesada por una línea verde de 2–3 px.
- Badge central `ⓘ NO DVC EQ 10 TON LMT` en pastilla.
- Fila inferior: `Equ` (checkbox 193×101), `Preset` (450×101 centrado), `⋮` (101×101).
- Columna izquierda: `Tone` y `Limit` como checkboxes; panel Tone con dos mandos
  circulares (Bass / Treble) de 394×248 cada uno, con punto blanco.
- Mini-player y nav se mantienen visibles durante todo el EQ.

---

## 7. Menú principal (sheet)

De abajo arriba, sobre un scrim negro translúcido:

1. Logo **POWERAMP** centrado + "Full Version" debajo (gris).
2. Fila **Settings** con icono de engranaje (1034×124, tocable).
3. Chips en dos líneas: `Album Art`, `Library`, `Headset/Bluetooth` /
   `Lock Screen`, `About` (radios ~50 %, fondo `#222222`).
4. Filas **Changelog** (icono de onda) y **Help** (icono `?`), 101 px de alto.
5. Mini-player y nav siguen visibles al pie.

---

## 8. Animaciones y comportamiento (observado)

| Elemento | Comportamiento |
|---|---|
| Título del player | **Marquee** continuo cuando el texto desborda la pastilla |
| Waveform del player | `SurfaceView` animado; las barras reaccionan al audio y rodean el play |
| Toasts | Aparecen sobre la nav y se desvanecen solos (~2 s) |
| Mini-player | Barra de progreso se actualiza en vivo; el botón cambia play/pausa |
| Cambio de tab | Instantáneo, sin slide; el contenido se sustituye |
| Sheet del menú | Desliza desde abajo con scrim |
| Pista actual | Resaltado persistente en pastilla dentro de la lista |
| A-Z | Salto alfabético con letras tocables, sin animación especial |

No confirmado aún (requiere captura en vídeo): duración exacta de transiciones,
curvas de easing, parallax del arte y comportamiento del visualizador a pantalla
completa.

---

## 9. Brechas frente a PowerampFree (prioridad visual)

| # | Brecha | Estado actual del proyecto | Prioridad |
|---|---|---|---|
| 1 | Nav de **4 tabs** con iconos vectoriales (no 5 con emoji) | 5 tabs con emoji | P0 |
| 2 | Mini-player con **miniatura + seekbar + botón** integrado | sin barra de progreso ni miniatura | P0 |
| 3 | Paleta exacta `#000/#0E0E0E/#181818/#222` y pastillas radio 50 % | sin tokens cerrados | P0 |
| 4 | Tipografía condensada bold (títulos 32–34 sp, filas 20 sp) | tipografía por defecto | P0 |
| 5 | Cabecera de lista con breadcrumb + icono de sección + acciones | cabecera simple | P0 |
| 6 | Filas de 96 dp con miniatura 80 dp y 3 líneas | filas más densas | P0 |
| 7 | Placeholder de onda para carátula ausente | placeholder distinto | P1 |
| 8 | Waveform/seeker protagonista (518 px de alto) | seekbar fino | P1 |
| 9 | Scroller A-Z lateral | ausente | P1 |
| 10 | Resaltado de pista actual en pastilla | ausente | P1 |
| 11 | Hoja de menú principal con logo/chips | menú distinto | P1 |
| 12 | EQ de 10 bandas con verde neón y mandos Tone | EQ básico | P1 |
| 13 | Chips de búsqueda con estado activo invertido | sin chips | P2 |
| 14 | Botón Lyrics + vista de letras centrada y grande | pendiente | P2 |
| 15 | Visualizador a pantalla completa | pendiente | P2 |

---

## 10. Cómo reproducir la captura

```powershell
. .\tools\ui-capture.ps1
Ensure-Poweramp
Cap "mi-pantalla"          # PNG + XML + TXT
Show-Nodes                 # tabla de nodos del último dump
Tap -Desc "Library"        # tap por content-desc
Tap -Text "All Songs"      # tap por texto
Back
```

Requisitos: `ANDROID_HOME` definido y dispositivo autorizado (`adb devices`).
El arnés no escribe ajustes: solo screenshots, dumps y eventos de entrada.
