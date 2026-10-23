# PowerampFree — architecture & integration

Pure-Kotlin DSP core (`:core:audio`, JVM, 102 tests green) + Android app
(`:app`, Media3/MediaSessionService) implementing Poweramp-like behavior
clean-room. How the reference engine (see `EVIDENCE.md`) maps to our code.

## Architecture

```
┌────────────────────────── :app ──────────────────────────────┐
│ MainActivity (Compose) — 5 tabs + overlays                   │
│   └── MediaController (session commands named API + QueueBus)│
│ PlaybackService (MediaSessionService)                        │
│   ├── dualMode OFF: ExoPlayer playwright list (native queue) │
│   ├── dualMode ON : orchestrator beams primary+overlay on    │
│   │                 `MediaSession.setPlayer`                 │
│   └── DspAudioProcessor (Media3 AudioProcessor → :core DspChain)
│ Stores: QueueStore (queue.json) · TrackStateStore · Playlist  │
│         dirs · AudioSettings/Art/Viz/Appearance prefs        │
│ Receivers: ApiCommandReceiver (implicit via dynamic register)│
└───────────────────────────────────────────────────────────────┘
        :core:audio (pure JVM, fully unit-tested)
        GainStage → ParametricEq → SoftLimiter (`DspChain`)
        + Biquad / Band / EqPreset(+AutoEQ import) / Crossfade
        + AutoAdvanceEngine / FocusPolicy / HeadsetGestures
        + SpectrumAnalyzer / LrcParser / policies / ShuffleOps
```

## Code map (app: `com.powerampfree.player`)

| Area | Files |
|---|---|
| Activity glue | `MainActivity.kt` – controller wiring, permission, routing |
| UI (extension `MainActivity.X()` composables) | `ui/` – `Transport` (snapshot+commands), `Common`, `PlayerTab`, `EqTab`, `LibraryTab`, `Playlists`, `TrackLists`, `SearchQueue`, `Lyrics`, `Settings`, `SettingsAudio`, `SettingsMedia` |
| Service state + hooks | `PlaybackService.kt` – beams, orchestrator state, service lifecycle |
| Service behavior (extensions) | `service/` – `Orchestrator` (dual-beam advance/skip/fades), `QueuePersistence` (snapshot/restore), `SessionCallback` (Media3 API), `FocusHeadset` (focus + wired/BT routing), `PlayerListener` (ExoPlayer events), `Common` (tags/futures) |
| DSP bridge | `DspAudioProcessor.kt` → `:core:audio DspChain` |
| Stores / settings | `PresetStore.kt`, `PlaybackSettings.kt`, `QueueStore.kt`, `TrackStateStore.kt`, `AudioSettings.kt`, `LibrarySettings.kt` |
| Loader / buses | `LibraryLoader.kt` (MediaStore+SAF), `QueueBus.kt`, `FormatBus.kt`, `VisualizerTap` |

## External API (broadcast commands)

Action: `com.powerampfree.player.API_COMMAND`, own namespaced extras:

| key | values |
|---|---|
| `com.powerampfree.player.cmd` | `play` `pause` `playpause` `next` `previous` `seek` `set_preset` |
| `com.powerampfree.player.arg` | `seek`: ms; `set_preset`: EqPreset JSON |

```
adb shell am broadcast -a com.powerampfree.player.API_COMMAND \
  --es com.powerampfree.player.cmd set_preset \
  --es com.powerampfree.player.arg '{"name":"Boost","preampDb":0.0,"bands":[{"type":"PEAKING","freqHz":1000,"gainDb":6.0,"q":0.9}]}'
```

In-session custom commands (`PlaybackService`): `SET_PRESET`, `SET_CROSSFADE`,
`SKIP_NEXT/PREV`, `SET_QUEUE`, `SEEK_QUEUE`, `ENQUEUE_NEXT`, `SET_REPEAT`,
`SET_SHUFFLE`, `SET_REPLAY_GAIN`. NOTE: queue placement uses `SET_QUEUE`
(compact JSON) and **not** controller `setMediaItems`: media3 controller-side
serialization degraded a 288-item queue to 1 item crossing the binder (observed).

## Reference mechanism → our implementation

| Reference (observed in EVIDENCE.md) | Original mechanism |
|---|---|
| `Pipeline2` native pipeline | Media3 ExoPlayer + `DspChain` |
| `callbackOnDspTrackEnded/Started` | `AutoAdvanceEngine` (pending→current guard) + ExoPlayer listeners |
| `Band(type,freq,gain,Q,channels)` | `Band(type,freqHz,gainDb,q,enabled)` |
| `native_calc_coefs` | `Biquad.design` (RBJ cookbook, Kotlin) |
| `native_filter_evaluate_freqs` | `ParametricEq.magnitudeDb/responseCurve` |
| `native_modify_coefs_for_gain` | `ParametricEq.modifyBandGain` |
| External `API_COMMAND` intents | own actions namespaced `com.powerampfree.player.*` |
| "PaP" binary preset blobs | JSON `EqPreset` (+ standard AutoEQ text import) |

## Known limitations (already documented, kept honest)

- Dual-mode crossfade is exact DSP-side, phase-approximate inter-decoder.
- In dual mode the session player holds one item; advancement is orchestrator-driven.
- Never bit-exact with the reference native engine — behavior-equivalence only.

## Docs map

- `EVIDENCE.md` — findings from the reference APKs (what we reproduced).
- `FEATURES.md` — feature port status + roadmap; full catalog in `archive/`.
- `archive/` — full history: `verification-history.md` (device-test log),
  `features-full.md`, `REPORT-history.md`.
