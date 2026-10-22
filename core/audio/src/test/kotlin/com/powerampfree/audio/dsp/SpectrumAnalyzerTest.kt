package com.powerampfree.audio.dsp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Spectrum analyzer properties:
 *  - bins = fftSize/2;
 *  - a full-scale sine localizes its energy at the expected bin (±1, Hann
 *    leakage tolerated);
 *  - digital silence produces a near-zero spectrum (window-summation floor);
 *  - DC produces its peak exactly at bin 0;
 *  - repeated analysis of identical input is bit-identical (determinism);
 *  - illegal sizes rejected.
 */
class SpectrumAnalyzerTest {

    private val sr = 48_000

    private fun sine(freqHz: Double, size: Int = 1024): FloatArray =
        FloatArray(size) { i -> (Math.sin(2 * Math.PI * freqHz * i / sr)).toFloat() }

    private fun argmax(spec: FloatArray): Int = spec.indices.maxByOrNull { spec[it] } ?: -1

    @Test
    fun sine_localizes_at_expected_bin() {
        val a = SpectrumAnalyzer(1024)
        val spec = a.spectrum(sine(1000.0))
        val expectedBin = (1000.0 / sr * 1024).toInt() // 21
        val p = argmax(spec)
        assertTrue(
            p in expectedBin - 1..expectedBin + 1,
            "peak bin $p, expected ±1 of $expectedBin",
        )
    }

    @Test
    fun silence_flux_is_negligible() {
        val a = SpectrumAnalyzer(1024)
        val spec = a.spectrum(FloatArray(1024))
        val max = spec.max()
        assertTrue(max < 1e-4, "silence spectrum max=$max")
    }

    @Test
    fun dc_has_peak_at_bin_zero() {
        val a = SpectrumAnalyzer(1024)
        val spec = a.spectrum(FloatArray(1024) { 1.0f })
        assertEquals(0, argmax(spec))
    }

    @Test
    fun deterministic_and_stable() {
        val a = SpectrumAnalyzer(1024)
        val s1 = a.spectrum(sine(1000.0))
        val s2 = a.spectrum(sine(1000.0))
        assertTrue(s1.contentEquals(s2), "same input must give identical magnitudes")
        // separate-input locality: changing later samples alters bins locally
        val s3 = a.spectrum(FloatArray(1024) { i -> if (i < 256) 0.9f else 0f }) // impulse-ish block
        val s4 = a.spectrum(FloatArray(1024) { i -> if (i < 256) 0f else 0f })
        assertTrue(s1.contentEquals(a.spectrum(sine(1000.0))))
        assertTrue(!s3.contentEquals(s4), "signal difference must show up in spectrum")
    }

    @Test
    fun bins_shape_and_sizes() {
        val a = SpectrumAnalyzer(512)
        assertEquals(256, a.bins)
        assertEquals(256, a.spectrum(FloatArray(100)).size) // zero padded input
        val b = SpectrumAnalyzer(2048)
        assertEquals(1024, b.bins)
        // bin k Hz: k/fftSize*sr → 4/512*48000 = 375 Hz
        assertEquals(375.0, a.binFreqHz(4, 48_000), 0.1)
    }

    @Test
    fun illegal_sizes_rejected() {
        for (bad in intArrayOf(0, 8, 12, 100, 1000)) {
            try {
                SpectrumAnalyzer(bad)
                kotlin.test.fail("fftSize $bad must be rejected")
            } catch (_: IllegalArgumentException) {
            }
        }
    }
}
