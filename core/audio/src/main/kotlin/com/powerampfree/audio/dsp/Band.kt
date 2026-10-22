package com.powerampfree.audio.dsp

/**
 * Biquad filter types for the parametric EQ.
 *
 * Reconstruction note: the reference implementation computes per-band biquad
 * coefficients natively (`native_calc_coefs` / `native_calc_bands_coefs` in the
 * analyzed player). We implement the same *mathematical family* — RBJ Audio EQ
 * Cookbook biquads — directly in Kotlin so behavior is inspectable and testable.
 */
enum class FilterType { PEAKING, LOW_SHELF, HIGH_SHELF, LOW_PASS, HIGH_PASS, NOTCH }

/**
 * One parametric EQ band.
 *
 * @property type    filter shape
 * @property freqHz  center/corner frequency in Hz (must be inside (0, sampleRate/2))
 * @property gainDb  peak/shelf gain in dB (ignored for pass/notch/all-pass types)
 * @property q       resonance (peaking: gain at fc is exactly +gainDb when q is finite)
 * @property enabled UI toggle; disabled bands are skipped in the chain
 */
@kotlinx.serialization.Serializable
data class Band(
    val type: FilterType = FilterType.PEAKING,
    val freqHz: Int,
    val gainDb: Float,
    val q: Float = 0.7f,
    val enabled: Boolean = true,
) {
    init {
        require(freqHz > 0) { "frequency must be > 0" }
    }
}
