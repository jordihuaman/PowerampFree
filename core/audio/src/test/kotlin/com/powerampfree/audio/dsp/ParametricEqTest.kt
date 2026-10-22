package com.powerampfree.audio.dsp

import kotlin.math.abs
import kotlin.test.*

class ParametricEqTest {

    private fun makeEq(bands: List<Band>, sr: Int = 44100, ch: Int = 2): ParametricEq =
        ParametricEq.build(ParametricEq.Config.of(bands), sr, ch)

    @Test
    fun no_bands_means_identity_processor() {
        val eq = makeEq(emptyList())
        val db = eq.magnitudeDb(1000.0)
        assertEquals(0.0, db, 1e-9)
    }

    @Test
    fun centered_response_equals_band_gain() {
        val eq = makeEq(listOf(Band(FilterType.PEAKING, 1200, 4.5f, 1.0f)))
        assertEquals(4.5, eq.magnitudeDb(1200.0), 1e-6)
    }

    @Test
    fun two_bands_forced_add_on_response() {
        val eq = makeEq(listOf(
            Band(FilterType.PEAKING, 100, 6f, 1.0f),
            Band(FilterType.PEAKING, 10000, -3f, 2f),
        ))
        assertEquals(6.0, eq.magnitudeDb(100.0), 0.25) // neighbor contamination small at 1kHz-distance
        assertEquals(-3.0, eq.magnitudeDb(10000.0), 0.25)
    }

    @Test
    fun modify_band_gain_recomputes_only_that_band() {
        val bands = listOf(
            Band(FilterType.PEAKING, 100, 3f, 1f),
            Band(FilterType.PEAKING, 1000, 2f, 1f),
        )
        val eq = makeEq(bands)
        val full = makeEq(listOf(bands[0].copy(gainDb = 7f), bands[1]))
        eq.modifyBandGain(0, 7f)
        // At the *other* band's center the response must equal the full-rebuild value
        // (band isolation) — neighbor contributions include each other, hence loose tol.
        assertEquals(full.magnitudeDb(1000.0), eq.magnitudeDb(1000.0), 1e-9)
        // Centered exactness: at 100 Hz the modified band dominates; a neighboring
        // Q=1 peaking at 1000 Hz leaks about +0.02 dB at 100 Hz (measured real value).
        assertEquals(7.0, eq.magnitudeDb(100.0), 0.05)
    }

    @Test
    fun disabled_bands_neutralized() {
        val onlyEnabled = listOf(Band(FilterType.PEAKING, 2000, -2f, 1f))
        val withDisabled = onlyEnabled + Band(FilterType.PEAKING, 1000, 6f, 1f, enabled = false)
        assertEquals(
            makeEq(onlyEnabled).magnitudeDb(1000.0),
            makeEq(withDisabled).magnitudeDb(1000.0),
            1e-9,
            "disabled band must contribute exactly 0 dB to the response",
        )
    }

    @Test
    fun processing_is_stable_with_extreme_preset() {
        val eq = makeEq((1..16).map { Band(FilterType.PEAKING, 100 * it, 12f, 0.7f) })
        val buf = FloatArray(4410 * 2) { if (it == 0) 1f else 0f } // impulse
        eq.process(buf, 4410, 2)
        var tailMax = 0f
        for (i in ((4410 - 2205) * 2) until 4410 * 2) { // last quarter: after ring died
            val a = abs(buf[i]); if (a > tailMax) tailMax = a
        }
        assertTrue(tailMax < 0.01f, "impulse resonance must decay to silence; got $tailMax")
        eq.reset()
        val buf2 = FloatArray(2) { if (it == 0) 1f else 0f }
        eq.process(buf2, 1, 2)
        assertTrue(buf2[0].isFinite() && abs(buf2[0]) < 1e4f)
    }

    @Test
    fun response_curve_composes_as_sum_of_bands() {
        // Chain superposition property: total response at f must equal Σ per-band response at f.
        val centers = listOf(20, 40, 80, 160, 320, 640, 1280, 2560, 5120, 10240, 14000, 16000)
        val bands = centers.map { Band(FilterType.PEAKING, it, 8f, 0.7f) }
        val eq = makeEq(bands)
        val single = centers.map { makeEq(listOf(Band(FilterType.PEAKING, it,  8f, 0.7f))) }
        val freqProbe = doubleArrayOf(30.0, 55.0, 100.0, 1000.0, 8000.0, 15000.0)
        for (f in freqProbe) {
            val composite = eq.magnitudeDb(f)
            val expected = single.sumOf { it.magnitudeDb(f) }
            assertEquals(expected, composite, 1e-9, "superposition at $f Hz")
        }
        // Flat preset must be a perfectly flat curve.
        val flat = makeEq(bands.map { it.copy(gainDb = 0f) })
        assertEquals(0.0, flat.magnitudeDb(1000.0), 1e-9)
        val flatCurve = flat.responseCurve(96)
        for (v in flatCurve) assertEquals(0f, v, 1e-6f, "flat curve must be zero")
    }
}
