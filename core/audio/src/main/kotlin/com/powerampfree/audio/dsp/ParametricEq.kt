package com.powerampfree.audio.dsp

import kotlin.math.ln
import kotlin.math.pow

/**
 * Parametric EQ chain: band list → per-channel biquads.
 *
 * Behavior mirrors the observed native engine family:
 *  - coefficients are designed per band and per channel,
 *  - magnitude response can be evaluated at arbitrary frequencies (used for UI curves),
 *  - when only a band's gain changes, only that band's coefficients are
 *    recomputed (equivalent of the observed `native_modify_coefs_for_gain`
 *    incremental-update path).
 */
class ParametricEq private constructor(
    override val sampleRateHz: Int,
    val channels: Int,
    private val bandState: MutableList<BandState>,
) : AudioProcessor {

    private class BandState(val band: Band, val perChannel: Array<Biquad>)

    val bandCount: Int get() = bandState.size

    /**
     * @param bands initial bands; must contain at most 31 bands (matches the
     *              "large parametric sizes" typical UI limits; enforced as guard)
     */
    class Config private constructor(val bands: List<Band>) {
        companion object {
            // Manual (≤31) + graphic (10) + tone (2) layers resolve on one engine,
            // so the hard cap is 63, matching the store chain layout.
            fun of(bands: List<Band>): Config {
                require(bands.size in 0..63) { "bands size must be 0..63, got ${bands.size}" }
                return Config(bands)
            }
        }
    }

    companion object Factory {
        fun build(config: Config, sampleRateHz: Int, channels: Int): ParametricEq {
            require(sampleRateHz in 8000..1_000_000) { "unsupported sample rate $sampleRateHz" }
            require(channels in 1..8) { "channels must be 1..8" }
            val state = config.bands.mapTo(mutableListOf()) { b ->
                BandState(b, Array(channels) { Biquad.design(b, sampleRateHz.toDouble()) })
            }
            return ParametricEq(sampleRateHz, channels, state)
        }
    }

    /** In-place processing; every enabled band filters every channel independently. */
    override fun process(buffer: FloatArray, frames: Int, channels: Int) {
        require(channels == this.channels) { "configured for $this.channels channels, got $channels" }
        for (band in bandState) {
            if (!band.band.enabled) continue
            for (ch in 0 until channels) {
                val bq = band.perChannel[ch]
                for (i in ch until frames * channels step channels) {
                    buffer[i] = bq.processChannel(buffer[i].toDouble(), ch).toFloat()
                }
            }
        }
    }

    override fun reset() {
        for (st in bandState) for (bq in st.perChannel) bq.reset()
    }

    /** Observed-equivalent: replace all bands (full reconfigure path). */
    fun setBands(newBands: List<Band>) {
        require(newBands.size in 0..63)
        bandState.clear()
        for (b in newBands) {
            bandState += BandState(b, Array(channels) { Biquad.design(b, sampleRateHz.toDouble()) })
        }
    }

    /**
     * Observed-equivalent incremental path: when a band's gain (only) changes,
     * recompute just that band's biquads. Returns true if applied.
     */
    fun modifyBandGain(index: Int, newGainDb: Float): Boolean {
        if (index !in bandState.indices) return false
        val old = bandState[index].band
        if (old.gainDb == newGainDb) return false
        val changed = old.copy(gainDb = newGainDb)
        bandState[index] = BandState(changed, Array(channels) { Biquad.design(changed, sampleRateHz.toDouble()) })
        // NOTE: like the reference native path, per-band state is isolated; a coef
        // change in one band cannot contaminate other bands.
        return true
    }

    fun bandAt(index: Int): Band? = bandState.getOrNull(index)?.band

    /** Total magnitude response (dB) of all enabled bands at [freqHz]. */
    fun magnitudeDb(freqHz: Double): Double {
        var sum = 0.0
        for (st in bandState) {
            if (!st.band.enabled) continue
            // All channels are designed identically → evaluate channel 0.
            sum += st.perChannel[0].magnitudeDb(freqHz, sampleRateHz.toDouble())
        }
        return sum
    }

    /** Response curve for the UI at logarithmically spaced points (min..max Hz). */
    fun responseCurve(totalPoints: Int, minFreqHz: Double = 20.0, maxFreqHz: Double = 20_000.0): FloatArray {
        val out = FloatArray(totalPoints)
        val totalDecades = kotlin.math.ln(maxFreqHz / minFreqHz) / kotlin.math.ln(2.0)
        for (i in 0 until totalPoints) {
            val f = minFreqHz * 2.0.pow(totalDecades * i / (totalPoints - 1.0))
            out[i] = magnitudeDb(f.coerceAtMost(maxFreqHz)).toFloat()
        }
        return out
    }
}

