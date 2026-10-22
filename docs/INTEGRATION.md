# PowerampFree — integration doc (original code, informed by REA findings)

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

- `app/PlaybackService.kt` — created, marked `TODO(integrate)`: audio session id (`audioSessionId`),
  MediaSession connection, playlist from MediaStore or manual SAF import.
- EQ UI task: sliders per band (Compose or classic Views) reading/writing `EqPreset` JSON, feeding
  `ParametricEq.responseCurve` for the drawn curve.
- Optional crossfade: `AutoAdvanceEngine` overlap state can drive a two-decoder mix in future work.
