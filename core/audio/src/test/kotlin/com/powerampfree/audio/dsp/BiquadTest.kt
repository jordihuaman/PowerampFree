package com.powerampfree.audio.dsp

import kotlin.math.abs
import kotlin.test.*

/**
 * Biquad property tests derived from the observed native behavior:
 * peaking filters must realize EXACTLY the configured gain at the center
 * frequency (the whole point of a parametric preset), shelves saturate at
 * +gain (low) / +gain (high), and pass/notch types don't use gain.
 */
class BiquadTest {

    @Test
    fun peaking_at_center_has_exact_gain() {
        for (q in listOf(0.7f, 1.0f, 2.5f, 10f)) for (g in listOf(-8f, -3f, 0f, 3f, 8f)) {
            val bq = Biquad.design(Band(FilterType.PEAKING, 1000, g, q), 44100.0)
            val db = bq.magnitudeDb(1000.0, 44100.0)
            assertEquals(g.toDouble(), db, 1e-6, "q=$q g=$g")
        }
    }

    @Test
    fun peaking_eq_classic_defaults() {
        // fc=1000, g=+6, q=1.0 → gain at center must be exactly +6 dB.
        val bq = Biquad.design(Band(FilterType.PEAKING, 1000, 6f, 1f), 48000.0)
        assertEquals(6.0, bq.magnitudeDb(1000.0, 48000.0), 1e-6)
    }

    @Test
    fun bypass_is_identity_when_gain_is_zero() {
        val bq = Biquad.design(Band(FilterType.PEAKING, 1000, 0f, 1f), 44100.0)
        val db = bq.magnitudeDb(1000.0, 44100.0)
        assertEquals(0.0, db, 1e-9)
    }

    @Test
    fun low_shell_frequencies_attracted_to_gain() {
        val bq = Biquad.design(Band(FilterType.LOW_SHELF, 100, 5f, 0.7f), 44100.0)
        assertEquals(5.0, bq.magnitudeDb(10.0, 44100.0), 5e-3)
        assertEquals(0.0, bq.magnitudeDb(10000.0, 44100.0), 0.05)
    }

    @Test
    fun low_pass_attenuates_above_fc() {
        val bq = Biquad.design(Band(FilterType.LOW_PASS, 500, 0.707f), 44100.0)
        assertTrue(bq.magnitudeDb(5000.0, 44100.0) < -18.0)
    }

    @Test
    fun coefficient_stability_bounded() {
        // Impulse through 31 chained maximum-gain bands: any legitimate transient may be
        // large, but the response must DECAY once the input stops (no self-oscillation).
        val bands = (0..30).map { Biquad.design(Band(FilterType.PEAKING, 60 + it * 100, 12f, 0.7f), 44100.0) }
        var x = bands.fold(1.0) { acc, bq -> bq.processChannel(acc, 0) } // impulse through chain
        assertTrue(x.isFinite(), "first output must be finite")
        var last = x
        for (i in 0 until 3000) {
            x = bands.fold(0.0) { acc, bq -> bq.processChannel(acc, 0) } // feed silence
            last = x
            if (i >= 2000 && abs(x) < 1e-2) return // decays — stable
        }
        throw AssertionError("impulse response does not decay: last sample $last")
    }
}
