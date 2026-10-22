package com.powerampfree.audio.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Practical visualization utility for the player UI: windowed radix-2 FFT
 * magnitude spectrum. Pure Kotlin, deterministic, unit-tested (bin
 * localization, silence floor, DC response, determinism).
 *
 * This is our own original utility — no reference-app code is involved. It is
 * used by an audio "tap" (a narrow copy of the post-EQ output) to draw the
 * live spectrum in the app UI; it never influences the audible path.
 */
class SpectrumAnalyzer(val fftSize: Int = 1024) {

    init {
        require(fftSize >= 16 && (fftSize and (fftSize - 1)) == 0) {
            "fftSize must be a power of two >= 16"
        }
    }

    val bins: Int get() = fftSize / 2

    private val window = FloatArray(fftSize) { i ->
        (0.5 * (1.0 - cos(2.0 * PI * i / (fftSize - 1)))).toFloat()
    }

    private val re = DoubleArray(fftSize)
    private val im = DoubleArray(fftSize)

    /**
     * Magnitudes for [mono] samples (shorter input is zero-padded; longer
     * truncated to one analysis window).
     */
    fun spectrum(mono: FloatArray): FloatArray {
        for (i in 0 until fftSize) {
            re[i] = if (i < mono.size) mono[i].toDouble() * window[i] else 0.0
            im[i] = 0.0
        }
        fft()
        val out = FloatArray(bins)
        for (k in 0 until bins) {
            val r = re[k]
            val m = im[k]
            out[k] = sqrt(r * r + m * m).toFloat()
        }
        return out
    }

    /** Frequency (Hz) of analysis bin [k] for the given sample rate. */
    fun binFreqHz(k: Int, sampleRateHz: Int): Double =
        k.toDouble() / fftSize * sampleRateHz

    /** Iterative in-place radix-2 FFT with bit-reversal permutation. */
    private fun fft() {
        val n = fftSize
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2.0 * PI / len
            val wr = cos(ang)
            val wi = sin(ang)
            val half = len / 2
            var blockIdx = 0
            while (blockIdx < n) {
                var cr = 1.0
                var ci = 0.0
                for (k in 0 until half) {
                    val a = blockIdx + k
                    val b = a + half
                    val ur = re[a]
                    val ui = im[a]
                    val xr = re[b]
                    val xi = im[b]
                    val vr = xr * cr - xi * ci
                    val vi = xr * ci + xi * cr
                    re[a] = ur + vr
                    im[a] = ui + vi
                    re[b] = ur - vr
                    im[b] = ui - vi
                    val ncr = cr * wr - ci * wi
                    ci = cr * wi + ci * wr
                    cr = ncr
                }
                blockIdx += len
            }
            len = len shl 1
        }
    }
}
