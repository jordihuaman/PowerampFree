# Verification plan — PowerampFree DSP core tests

The analyzed reference cannot run here (it is an Android binary), so
equivalence claims are *behavioral properties* traced from REA findings to
tests that any environment can execute:

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

Known NOT verified:
- Bit-exact audio equivalence with the reference native DSP (its .so remains black-box).
- Device-level behavior (Hi-Res routing, USB DAC) — out of scope of this scaffold.
