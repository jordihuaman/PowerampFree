# PowerampFree — integration doc (original code, informed by REA findings)

## External API (broadcast commands)

Action: `com.powerampfree.player.API_COMMAND` — extra keys are namespaced:

| key                            | value                                        |
|--------------------------------|----------------------------------------------|
| `com.powerampfree.player.cmd`  | `play` `pause` `playpause` `next` `previous` `seek` `set_preset` |
| `com.powerampfree.player.arg`  | for `seek`: milliseconds; for `set_preset`: EqPreset JSON |

The receiver forwards everything through a MediaController into the session,
so the exact same code path the app UI uses gets exercised. Because manifest
receivers do not receive implicit custom actions (Android 8+), the service also
registers the same receiver dynamically at startup — implicit broadcasts work
while the app process is alive, and explicit component broadcasts always work:

```
adb shell am broadcast -a com.powerampfree.player.API_COMMAND \
  --es com.powerampfree.player.cmd set_preset \
  --es com.powerampfree.player.arg '{"name":"Boost","preampDb":0.0,"bands":[{"type":"PEAKING","freqHz":1000,"gainDb":6.0,"q":0.9}]}'
```

Queue placement uses the session command `com.powerampfree.player.SET_QUEUE`
(compact JSON track list) rather than relying on `setMediaItems` from a
controller: during on-device verification media3's controller-side MediaItem
serialization degraded a 288-item queue to 1 item when it crossed the binder
(observed: `onSetMediaItems items=1 start=-1`).

## Architecture

```
┌────────────────────────── Android app (:app) ──────────────────────────┐
│  Media3/@Service PlaybackService                       using MediaSession │
│        │  (own API surface: broadcast RECEIVER, EQ commands,             │
│        │   NOT a clone of the reference app's API intents)                │
│        ▼                                                                  │
│  ExoPlayer (Media3) — gapless playlist concatenation, AutoAdvance         │
│        │                                                                  │
│        ▼                                                                  │
│  DSP chain from :core:audio via Media3 AudioProcessor:                   │
│    GainStage (preamp+volume) → ParametricEq → SoftLimiter                 │
└───────────────────────────────────────────────────────────────────────────┘
                        ▲
Pure JVM (:core:audio) — Biquad/Band/EqPreset/AutoEQLoader/ParametricEq/
GainStage/SoftLimiter/DspChain/AutoAdvanceEngine — fully unit-tested.
```

## That mapping, and honest naming

Also shipped (paso 5): visualizador de espectro sobre tap post-EQ (FFT propia, jamás invasiva
a la señal audible) y botones next/prev en notificación para modo dual (custom layout +
`com.powerampfree.player.SKIP_NEXT/SKIP_PREV`).

## Playback service checklist

1. Foreground `Service` with Media3 `MediaSession` (media button + framework integration).
2. Playlist = ExoPlayer `MediaItem`s with `mediaId = trackId` and metadata.
3. **Continuous playback**: rely on ExoPlayer seamless concatenation (same-format tracks) and then
   bind `AutoAdvanceEngine` states to `PLAYER_EVENTS` (`isPlaying`, `discontinuity` reasons).
4. EQ state: DataStore or `SharedPreferences`; serialize `EqPreset` to JSON, apply through
   `ParametricEq.setBands()` — because the DSP `AudioProcessor` keeps its own lifecycle, no manual
   sample-rate rebinding needed (Media3 notifies format change).
5. Volume: Media3 `volume` (0..1) + optional preamp. `GainStage` already has this split.

## That mapping, and honest naming

Reference-app behavior            Our original mechanism
---------------------------------  ----------------------------------------
`Pipeline2` native pipeline        Media3 ExoPlayer + our `DspChain`
`callbackOnDspTrackEnded`          `Player.Listener.onPlaybackStateChanged(STATE_ENDED)` / AutoAdvance
`callbackOnDspTrackStarted`        `ExoPlayer` `isPlaying` + subsequent `MediaItem`
`Band(type,freq,gain,Q,channels)`  `Band(type,freqHz,gainDb,q,enabled)`
`native_calc_coefs`                `Biquad.design` (RBJ formulas in Kotlin)
`native_filter_evaluate_freqs`     `ParametricEq.magnitudeDb` / `responseCurve`
`native_modify_coefs_for_gain`     `ParametricEq.modifyBandGain`
`api_command` external intents     own actions namespaced `com.powerampfree.player.*`
"PaP" binary preset format         JSON `EqPreset` (own format)

## Received known-good places to integrate

Estado (paso 2 completado, 2026-10-21): todos los TODO(integrate) del servicio están implementados.

- ~~`app/PlaybackService.kt` — created, marked `TODO(integrate)`~~ → **implementado**: cola desde
  MediaStore (`MediaStoreLibrary`) y SAF (`SafLibrary`), restauración de cola pausada al crear el
  servicio, ticker de posición (persistencia cada 15 s + en transiciones), EQ en vivo por
  MediaSession custom command `com.powerampfree.player.SET_PRESET` aplicado en
  `DspAudioProcessor.applyPreset` (ruta reconfigure completa o incremental por ganancia según si
  cambia la estructura de bandas), `AutoAdvanceEngine` conectado a los eventos de ExoPlayer
  (`onMediaItemTransition`, `onPlaybackStateChanged(ENDED)`, `onIsPlayingChanged`) como observador
  de continuidad con guards de sesión.
- `app/MainActivity.kt` — **implementado**: biblioteca MediaStore + importación SAF, EQ con sliders
  por banda (/preamp) ligados a `EqPreset`, curva de respuesta dibujada con la misma matemática
  (`ParametricEq.responseCurve` sobre 48 kHz off-screen), presets con nombre en `filesDir/eq/`
  (`PresetStore.saveNamed/listNamed`) y preset actual en prefs.
- Crossfade real: **dos caminos implementados**. (a) Secuencial (audio en una sola tubería):
  `CrossfadeProcessor` con fade-out de A + fade-in de B sample-exactos. (b) **Mezcla real de dos
  decoders (paso 4)**: doble ExoPlayer con DSP propio, fade por plan en cada lado, mezcla a nivel
  audio server (AudioTracks simultáneos), cola por orquestador + promoción de sesión
  (`MediaSession.setPlayer`); activo cuando el crossfade > 0 (`dualMode`), exacto en el lado DSP
  y aproximado inter-decoder en fase (documentado). `AutoAdvanceEngine.OVERLAP` rige el estado
  lógico de la superposición (pendingTrackOpened→dspTrackStarted→promoción).
