# Poweramp 1032 / Poweramp Equalizer 1028 — Evidence log

Clean-room analysis of the user's own APKs (RuStore "uni" distribution), made with
**REA 6.7.0** (`--format json`, evidence-backed) + **jadx-headless-mcp 0.7.1**
(SHA-256 verified) + REA SQLite provider. Static only: nothing was executed from
the APKs; decompiled code was used as behavioral reference and does **not** ship
in this project. Original artifacts live in (untracked) `extract/` and `targets/`.

Confidence legend: `observed` = seen directly in DEX/sqlite bytes; `assumed` =
inferred from context; `unknown` = not investigated.

## Artifact profile

| Item | Value |
|---|---|
| Target | `Poweramp-build-1032-uni.apk` (manifest `com.maxmpz.audioplayer`, 6434 classes), `PowerampEqualizer-build-1028-uni.apk` (`com.maxmpz.equalizer`) |
| Packaging | "uni" with `lib/arm64-v8a` + `lib/armeabi-v7a` |
| Native libs | `libpowerampcore.so`, `libffmpeg_neon.so` (PA) / `libpeqcore.so`, `libffmpeg_min.so` (PEQ) |

## Surface map

| Component | Class |
|---|---|
| Player service | `com.maxmpz.audioplayer.player.PlayerService` → core field `PlayerServiceCore` |
| DSP engine | `com.maxmpz.audioplayer.processing.Pipeline2` (JNI: `native_create/build/request_open/seek/…`) |
| Plugin manager | `com.maxmpz.audioplayer.plugin.NativePluginManager` (intent `NATIVE_PLUGIN_COMMAND`) |
| Plugins | decoders `decoder.ffmpeg|flac|mod|mpc|wv|amedia`, outputs `output.aa|at|athd|cc|osl|usbx`, DSP `plugin.reverb|tempo|resampler3|milk|peq` |
| Public API | `PowerampAPIActivity` / `PowerampAPIReceiver`: `API_COMMAND`, `ACTION_OPEN_EQ`, `ACTION_SCAN_DIRS` |
| EQ app | `PeqService`, `PeqAPIReceiver` (`SET_EQU_PRESET`, `START/STOP/TOGGLE_SERVICE`) |

## Core findings

1. **EQ is parametric (biquads).** `com.maxmpz.dsp.data.Band {type:int, frequency:int, q:float, gain:float, channels:int}`.
   Presets live in `assets/auto_eq.db` (table `eq_presets(_id,name,meta,parametric,data_blob)`;
   blob magic `"PaP"\x01`, parametric records are float32 gain dB / int32 Hz / float32 Q,
   plus 34 anchor points 20 Hz→19.87 kHz at 1/6 oct for UI). The DB is **byte-identical
   in both APKs** (sha256 `34d816301f2e7894…`). *observed* — We deliberately do not
   reimplement the "PaP" binary format; our presets are JSON + standard AutoEQ text.

2. **DSP parameter protocol = push-blob with atomic commit.** `PeqNativeUtils`:
   `setParameter(Object, byte[])` → `setParameterCommit(Object)` (also deferred/poke).
   *observed*

3. **Filter math native, Kotlin hands in Band[] / receives double[] coefs.** `Coefs`
   natives: `calc_coefs`, `calc_bands_coefs`, `filter_evaluate_freqs` (curve draw),
   `modify_coefs_for_gain` (incremental gain). *observed*

4. **Playback is a native pipeline owned via a long handle.** `Pipeline2`
   `native_create/build/request_open/pause/resume/stop/seek/get_position_ms/…`;
   Java injects callbacks (decoder opened/failed, DSP track started/ended, seeked,
   ended, config required). *observed* — DSP internals inside the `.so` remain
   black-box: *unknown* (behavior-equivalence only, never bit-exact).

5. **Gapless/auto-advance is a two-session dance.** Current + pending sessions;
   on `callbackOnDspTrackEnded(sessionId)` (id match) → msg `0x30000`;
   `callbackOnDspTrackStarted` promotes pending→current when state==4 (overlap armed);
   `callbackOnEnded(cause)` distinguishes end causes `8|32|4|1|2|16` (*assumed* semantics).

6. **EQ native libs export a bidding plugin API** (`libpowerampcore.so` ELF:
   `pa_register_dsp/decoder/output`, `pa_register_resampler`, `pa_alloc_plugin_context`,
   `pa_send_msg`, `JNI_OnLoad` — dynamic bidding, no `Java_` stems). *observed*

7. **Queue/service glue is obfuscated** (`m362/m360`, handler msgs `2/3/6`): roles
   derived from use sites. *observed (named assumed)*

**Not claimed / unknown:** real-time audio behavior, exact native DSP math,
DRM/Hi-Res qualifications, USB DAC internals, visualizer (MilkDrop) internals.

## Artifacts

`extract/inspect_package_*.json` (package overviews), `extract/m_callback*.json`
(method bodies), `extract/*autoeq.db` (extracted `auto_eq.db` snapshot),
`extract/decode_pap.js` (blob-format locator), `extract/so/…` (ELF symbols).
All untracked — see `.gitignore`.
