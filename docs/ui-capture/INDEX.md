# Índice de capturas — Poweramp build-981-uni

Capturas reales tomadas por `adb` (screenshot + `uiautomator dump`) el 2026-10-22/23
sobre el dispositivo `24117RN76L` (Android 16, 1080×2400, 450 dpi).

Por cada pantalla hay tres artefactos con el mismo nombre base:

- `<nombre>.png` — captura de pantalla (fidelidad visual completa)
- `<nombre>.xml` — jerarquía `uiautomator` cruda (ids, bounds, estados)
- `<nombre>.txt` — resumen legible: `id | desc | text | pkg | cls | tamaño @ centro | flags`

La bitácora de acciones (taps, swipes, back, foco) está en `actions.log`.

| Captura | Qué muestra |
|---|---|
| `player-main` | Pantalla de reproducción completa: carátula/placeholder, like/unlike, lyrics, overflow, chips de visualización/temporizador/repeat/shuffle, waveform, transporte, chip técnico, nav inferior. |
| `player-lyrics` | Resultado de pulsar **Lyrics** sin letra disponible: la pantalla no cambia (no hay vista de letras sin datos). |
| `player-vis-toast` | Toast inferior **"Visualization – Full Screen / Long press for more options"** tras pulsar el chip de visualización. |
| `player-vis-menu` | Mismo estado con el toast ya atenuado; sirve para medir el toast (radio, color, icono). |
| `tab-player` | Player limpio (referencia de medidas). |
| `tab-library` | Biblioteca: cabecera "Library", rejilla de categorías con iconos circulares de color y scroller A-Z. |
| `lib-top` | Biblioteca sin mini-player (lista completa desde arriba). |
| `lib-scrolled` | Biblioteca desplazada: aparecen cola/streams y los últimos ítems. |
| `lib-all-songs` | Lista completa **All Songs**: breadcrumb "↑ Library", icono+título, fila de acciones (shuffle/play/search/Select/⋮), filas con carátula placeholder de onda, título, artista, `duración | formato`, A-Z lateral. |
| `lib-folders` | **Folders**: carpeta de primer nivel "Rohn Audio" con nombre de dispositivo y arte grande. |
| `lib-folder-tracks` | **Contenido de carpeta**: cabecera con arte de esquina, nombre, dispositivo, `242 | 186:59:48`, fila de acciones y pista actual resaltada en pastilla. |
| `tab-eq` | **EQ/Efectos**: 3 iconos de sección (EQ activo), 10 bandas + preamp con verde neón, preset pill, Tone (Bass/Treble), Limit, badge de estado, mini-player. |
| `tab-search` | **Búsqueda** con teclado abierto: campo, chips de filtro, historial. |
| `search-no-keyboard` | Búsqueda sin teclado: se ve la estructura completa (campo, chips, acciones, historial). |
| `main-menu` | **Main Menu** como bottom sheet: logo POWERAMP, "Full Version", fila Settings, chips (Album Art, Library, Headset/Bluetooth, Lock Screen, About), Changelog, Help. |

## Pendiente de capturar (siguiente pasada)

- Vista de letras con una pista **con letras** (la pista actual no tiene).
- Visualizador a pantalla completa (el tap simple solo mostró el toast; requiere
  long-press → opciones, o activarlo en ajustes).
- Cola de reproducción con pistas, ecualizador de presets, gestor de listas.
- Árbol completo de Ajustes (fase 2, cuando se aborden opciones).
- Animaciones: transición player↔biblioteca, apertura del sheet, glow del EQ.
