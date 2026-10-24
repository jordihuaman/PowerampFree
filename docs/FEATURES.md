# Features — status & roadmap

Inventario de comportamientos observados en las capturas del usuario (referencia:
`docs/archive/features-full.md`, con evidencia `IMG:` por feature). Regla del
proyecto: se importa el **comportamiento**, nunca recursos ni código; todo lo
portado es implementación original.

Estado del port: `✅ HECHA` / `🟡 PARCIAL` / `⬜ PENDIENTE`.

| # | Feature (bloque de la referencia) | Estado | Implementación |
|---|---|---|---|
| 1 | Player screen (transporte, repeat, shuffle, ±10s, rating, sleep timer, marquee, chip de formato real del decoder) | ✅ S1 | `PlayerTab`, `FormatBus`, `RatingsStore` |
| 2 | Bottom nav (4 tabs en la implementación: Library/EQ/Search/Menu) + mini-player persistente + cola en vivo | ✅ S2/S11 | `PafBottomBar`, `QueueBus` |
| 3 | Explorador por carpetas (breadcrumb, totales de subárbol, selección, sort por auditoría) | ✅ S3 | `MainActivity.FolderScreen`, `ListSort` |
| 4 | Menú contextual de pista (play/encolar/ir-a ×3/letras/playlist) | ✅ S6 | `TrackContextMenu`, `CMD_ENQUEUE_NEXT` |
| 5 | Letras sincronizadas LRC (línea activa, follow, tap-seek) | ✅ S6 | `LrcParser` + `LyricsScreen` |
| 6/7 | Settings raíz + Look & Feel en vivo (tema/estanque/negrita/start-at-library) + Export/Import JSON | ✅ S5 | `Settings*`, `AppearanceStore` |
| 8 | Settings Audio (preamp crossfade, Replay Gain) | ✅ S7 | `AudioSettings`, `GainStage.replayGainDb` |
| 9 | Settings Misc (scrobbling, Android Auto) | ⬜ | — |
| 10 | Headset/Bluetooth (gestos hook, foco duck/pause, cuerda actions) | ✅ S7 | `HeadsetGestures`+`FocusPolicy` (puros, testeado), `AudioSettings` |
| 11 | Settings Library (stats plays, umbral de counts, filtro cortas, restore por-pista) | ✅ S9 | `TrackProgressPolicy`+`LibraryPolicies`, `TrackStateStore` |
| 12 | Settings Album Art (provider del sistema + cover por carpeta, cache, fondo) | ✅ S8 | `AlbumArtLoader`, `CoverFileNames`, `ArtSettings` |
| 13 | Background derivado de carátula (blur + scrim) | ✅ S8 | `PlayerBackground` |
| 14 | Visualizador: espectro en player y bajo la curva del EQ (estilo/opacidad en vivo) | ✅ S10 | `SpectrumAnalyzer`+`VisualizerTap`, `VizSettings` |
| 15 | Menú principal overlay + Búsqueda (filtros + historial) | ✅ S2/S12 | `MainMenuSheet`, `SearchTab`, `SearchHistory` |
| 16 | EQ gráfico: tabs EQ/Tone/Limit, 10 bandas + preamp, curva por capas, presets y bandas paramétricas | ✅ S4/S12 | `EqTab`, `ParametricEq`, `EqPreset` |
| 17 | Biblioteca por categorías (categorías/albumes/categorías-generos + SAF) | ✅ S2 | `LibraryTab`, `LibraryLoader` |
| 18 | List Options (sort por audiencia, cabecera con totales) | 🟡 | sort ✅ (S3), resto ⬜ |
| 19 | Menú de cabecera de carpeta | 🟡 | acciones: ▶/🎲/☑/⇅ ✅ |

Playlists end-to-end (crear/añadir-dedupe/play/renombrar/eliminar + incluir en backup): ✅ S10.

## Candidatos siguientes (Sprint 13, propuesto)

- **Navegación fina**: scroller A-Z en listas largas, gestos de biblioteca y
  estados vacíos.
- **Visualizador a pantalla completa** (estilos Classic/Rounded, transición
  desde el player).
- **Fase funcional**: preferencias reales de biblioteca (carpetas
  incluidas/excluidas, orden persistente, temas) y atajos de reproducción.

## Cobertura histórica de sprints

S1 player · S2 navegación/cola/búsqueda/categorías · S3 carpetas · S4 EQ gráfico ·
S5 settings+tema+backup · S6 menú contextual+letras · S7 audio/headset+foco ·
S8 carátulas+fondo · S9 stats/restore de biblioteca · S10 playlists+visualización ·
S11 shell visual (tokens, 4 tabs, filas, biblioteca) · S12 player §3 + EQ neón +
cola/búsqueda/playlists/ajustes.
Verificación en dispositivo (Xiaomi MIUI Android 16) en
`docs/archive/VERIFICATION-history.md`.
