# Poweramp 1032 / Poweramp Equalizer 1028 — Evidence log

Analysis performed with **REA 6.7.0** (CLI, `--format json`, evidence-backed) + jadx-headless-mcp 0.7.1
(verified SHA-256: `6e5eacf5…f75`) + REA SQLite provider. Artifacts belong to the user (RuStore "uni" distribution).
Nothing was executed from these APks; all findings are static observations. RE-compiled code was used
as reference only; no decompiled source ships in this project.

## Surface map

Artifact: `targets/Poweramp-build-1032-uni.apk` (manifest `com.maxmpz.audioplayer`, classes 6434).
Evidence id: `ev_be6fc1a2…9e1385e6cca9e` (search-android-classes run), plus per-command evidence ids above.

| Component | Class (from manifest / DEX) |
|---|---|
| Player service | `com.maxmpz.audioplayer.player.PlayerService` → field B: `PlayerServiceCore` |
| DSP engine (native) | `com.maxmpz.audioplayer.processing.Pipeline2` (JNI: native_create/build/request_open/seek/…) |
| Native plugin manager | `com.maxmpz.audioplayer.plugin.NativePluginManager` (intent `NATIVE_PLUGIN_COMMAND`) |
| Decoder plugins | `decoder.ffmpeg|flac|mod|mpc|wv|amedia` PluginServices |
| Output plugins | `output.aa|at|athd|cc|osl|usbx` PluginServices |
| DSP plugins | `plugin.reverb`, `plugin.tempo`, `plugin.resampler3`, `plugin.milk`, EQ (peq) |
| Public API | `PowerampAPIActivity` / `PowerampAPIReceiver`: action `com.maxmpz.audioplayer.API_COMMAND`, `ACTION_OPEN_EQ`, `ACTION_SCAN_DIRS` |
| EQ UI | dialogs: `EqPresetsActivity`, `AddEqPresetActivity`, `EqPresetAssignActivity`, `AddReverbPresetActivity`, `AudioBufferActivity` |
| Library | data providers (`AAContentProvider`, `GenericFileProvider`, `RestProvider`) |
| Visualizer | `com.maxmpz.milk.*` MilkDrop stack, GLSL + Lua |

## Precise findings

1. **Preset engine is parametric (biquads).**
   `com.maxmpz.dsp.data.Band` fields: `type:int, frequency:int, q:float, gain-float, channels:int`.
   The EQ preset blobs inside `assets/auto_eq.db` (SQLite, byte-identical in both APKs,
   sha256 `34d81630…19`) store per-preset parametric records
   (float32 gain dB / int32 Hz / float32 Q) plus 34 anchor curve points for UI.
   Confidence: observed.

2. **DSP parameter protocol is push-blob with atomic commit.**
   `PeqNativeUtils` natives: `setParameter(Object, byte[])`, `setParameterCommit(Object)`,
   `setParameterDeferred(Object, byte[])`, `getParameter`, `command`, `poke_fx`, `setEnabled`.
   Confidence: observed.

3. **Filter math runs natively; Kotlin receives Band[] and gets double[] coefs.**
   `Coefs`: `native_calc_coefs`, `native_calc_bands_coefs`, `native_filter_evaluate_freqs`
   (curve drawing), `native_modify_coefs_for_gain` (incremental gain change).
   Confidence: observed.

4. **Playback motor is a native pipeline owned through a long handle.**
   `Pipeline2`: `native_create/build/request_open/pause/resume/stop/seek/get_position_ms/
   set_end_event_position_ms/sleep_until_java_cmd/test_mode/route_msg`.
   Java injects callbacks as functions (decoder opened/failed/closed, DSP track started/ended,
   seeked, ended, config required). Confidence: observed; exact DSP internals inside the .so
   remain black-box (arm64 native disassembly pending) — NOT claimed.

5. **Track continuity (gapless/auto-advance) is a two-session dance.**
   Decompiled bodies (evidence files `extract/m_callbackOn*.json`):
   - `callbackOnDspTrackEnded(sessionId)`: publishes internal msg `196608` (**0x30000**) when the
     id matches the current pending (`s21.K == i`).
   - `callbackOnDspTrackStarted(sessionId, param)`: when the id matches the pending track and
     internal state == 4, **promotes pending → current** and posts 0x30000; otherwise sets state 2.
   - `callbackOnEnded(cause)`: distinguishes causes (`8|32|4|1`, `2`, `16`) and logs
     "pendingEnd=… tryAutoAdvance=…" with `m428/m433` event postings (`0x20000/0x50000`).
   - `callbackOnConfigRequired()`: returns fresh byte[] chain config from `PlayerServiceCore`,
     consumed by `native_build`.
   - Unicode-correct internals: state constants were not documented in app docs; the exact
     meaning of cause codes 8/32/4/1/2/16 is ASSUMED from context (end/pause/error/start).
   Confidence: observed for routine code; internals ASSUMED where noted.

6. **Queue/model glue.** `PlayerService` (service in manifest) delegates into `PlayerServiceCore`.
   PlayerServiceCore exposes `m362/m361/m360/...`, event handler `O` (Handler with messages e.g. 2
   seek-finished, 3 pipeline-event 0x600000, 6 pipeline-stopped). Confidence: observed (names are
   obfuscated; roles derived from use sites).

7. **EQ standalone app** targets `com.maxmpz.equalizer.peq` — wait, ID is `com.maxmpz.equalizer`
   (its manifest: `PeqService`, `PeqSessionReceiver`, `PeqNotificationListener`, `PeqAPIActivity` /
   `PeqAPIReceiver` with `com.maxmpz.equalizer.API_COMMAND`, `ACTION_IMPORT_PRESETS`, `EQ_PRESET`
   set command `SET_EQU_PRESET` (int), `START/STOP/TOGGLE_SERVICE`). Confidence: observed.

8. **Not claimed**: real-time audio behavior, native DSP accuracy, DRM/pla details, Hi-Res
   qualifications, USB DAC internals — outside static-scan scope.

## Evidence files

- `extract/inspect_package_poweramp.json` — APK/package overview (evaluated by jadx)
- `extract/m_dspTrackEnded.json` etc. — method bodies (jadx get_method_body)
- `extract/_autoeq_*.db` — extracted `auto_eq.db` snapshots (SQLite; inspected via REA)
- `extract/decode_pap.js` — run-on-purpose helper that located the "PaP" blobs (evidence only)
- `extract/so/…` — extracted native libs for ELF symbol table review (`tools/elf_syms.js`)
