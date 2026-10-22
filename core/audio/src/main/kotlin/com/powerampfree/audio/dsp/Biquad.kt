package com.powerampfree.audio.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan

/**
 * Second-order section (biquad) computed from an [Band] with the RBJ cookbook
 * formulas (Reference: R. Bristow-Johnson, "Cookbook formulae for audio EQ
 * biquad filter coefficients", an industry-standard public formulation — no
 * original-player code is involved).
 *
 * Direct Form II Transposed representation:
 *   y[n] = b0*x[n] + b1*x[n-1] + b2*x[n-2] - a1*y[n-1] - a2*y[n-2]
 */
class Biquad private constructor(
    val b0: Double, val b1: Double, val b2: Double,
    /** Already normalized by a0: caller memory gets exactly 5 doubles. */
    val a1: Double, val a2: Double,
) {
    private var z1 = 0.0
    private var z2 = 0.0
    private var bypass = false
    private var resetPending = false

    fun setBypass(v: Boolean) { bypass = v }
    fun isBypass(): Boolean = bypass

    /** Zero out filter state (used on seek / track change to avoid stale ringing). */
    fun reset() { z1 = 0.0; z2 = 0.0 }

    /** Marks that next process call must first flush state (coefficient swap safety). */
    fun scheduleReset() { resetPending = true }

    /** Process one interleaved frame for [channels] channels; state is shared L/R pair per channel. */
    fun processChannel(x: Double, channelIndex: Int): Double {
        if (resetPending) { reset(); resetPending = false }
        if (bypass) return x
        // NOTE: state arrays are per-channel; for simplicity each Biquad instance holds one channel's
        // state, and the owning Eq chain instantiates one Biquad per band per channel (2..N).
        val y = b0 * x + z1
        z1 = b1 * x + z2 - a1 * y
        z2 = b2 * x - a2 * y
        return y
    }

    /**
     * Magnitude response in dB at [freqHz] given the design [sampleRateHz].
     * This mirrors the reference native evaluation used to draw EQ curves.
     */
    fun magnitudeDb(freqHz: Double, sampleRateHz: Double): Double {
        val w = 2.0 * PI * freqHz / sampleRateHz
        // H(z) = (b0 + b1 z^-1 + b2 z^-2) / (1 + a1 z^-1 + a2 z^-2)
        val c = cos(w); val s = sin(w)
        val c2 = cos(2 * w); val s2 = sin(2 * w)
        val numRe = b0 + b1 * c + b2 * c2
        val numIm = -(b1 * s + b2 * s2)
        val denRe = 1.0 + a1 * c + a2 * c2
        val denIm = -(a1 * s + a2 * s2)
        val num = numRe * numRe + numIm * numIm
        val den = denRe * denRe + denIm * denIm
        return 10.0 * kotlin.math.ln(num / den) / kotlin.math.ln(10.0)
    }

    companion object {
        private val LN2 = kotlin.math.ln(2.0)

        fun design(band: Band, sampleRateHz: Double): Biquad {
            require(band.freqHz < sampleRateHz / 2.0) {
                "fc ${band.freqHz} must be below Nyquist for sr=$sampleRateHz"
            }
            val f0 = band.freqHz.toDouble()
            val A = 10.0.pow(band.gainDb / 40.0) // half-amplitude factor used by cookbook peaking/shelf
            val Q = band.q.coerceAtLeast(1e-4f).toDouble()
            val w0 = 2 * PI * f0 / sampleRateHz
            val alpha = sin(w0) / (2 * Q)

            val cs: DoubleArray = when (band.type) {
                FilterType.PEAKING -> {
                    val b0 = 1.0 + alpha * A
                    val b1 = -2.0 * cos(w0)
                    val b2 = 1.0 - alpha * A
                    val a0 = 1.0 + alpha / A
                    val a1 = -2.0 * cos(w0)
                    val a2 = 1.0 - alpha / A
                    doubleArrayOf(b0, b1, b2, a0, a1, a2)
                }
                FilterType.LOW_SHELF -> {
                    val sqrtA = kotlin.math.sqrt(A)
                    val twoSqrtAalpha = 2.0 * sqrtA * alpha
                    val b0 = A * ((A + 1.0) - (A - 1.0) * cos(w0) + twoSqrtAalpha)
                    val b1 = 2.0 * A * ((A - 1.0) - (A + 1.0) * cos(w0))
                    val b2 = A * ((A + 1.0) - (A - 1.0) * cos(w0) - twoSqrtAalpha)
                    val a0 = (A + 1.0) + (A - 1.0) * cos(w0) + twoSqrtAalpha
                    val a1 = -2.0 * ((A - 1.0) + (A + 1.0) * cos(w0))
                    val a2 = (A + 1.0) + (A - 1.0) * cos(w0) - twoSqrtAalpha
                    doubleArrayOf(b0, b1, b2, a0, a1, a2)
                }
                FilterType.HIGH_SHELF -> {
                    val sqrtA = kotlin.math.sqrt(A)
                    val twoSqrtAalpha = 2.0 * sqrtA * alpha
                    val b0 = A * ((A + 1.0) + (A - 1.0) * cos(w0) + twoSqrtAalpha)
                    val b1 = -2.0 * A * ((A - 1.0) + (A + 1.0) * cos(w0))
                    val b2 = A * ((A + 1.0) + (A - 1.0) * cos(w0) - twoSqrtAalpha)
                    val a0 = (A + 1.0) - (A - 1.0) * cos(w0) + twoSqrtAalpha
                    val a1 = 2.0 * ((A - 1.0) - (A + 1.0) * cos(w0))
                    val a2 = (A + 1.0) - (A - 1.0) * cos(w0) - twoSqrtAalpha
                    doubleArrayOf(b0, b1, b2, a0, a1, a2)
                }
                FilterType.LOW_PASS -> {
                    val b0 = (1.0 - cos(w0)) / 2.0
                    val b1 = 1.0 - cos(w0)
                    val b2 = (1.0 - cos(w0)) / 2.0
                    val a0 = 1.0 + alpha
                    val a1 = -2.0 * cos(w0)
                    val a2 = 1.0 - alpha
                    doubleArrayOf(b0, b1, b2, a0, a1, a2)
                }
                FilterType.HIGH_PASS -> {
                    val b0 = (1.0 + cos(w0)) / 2.0
                    val b1 = -(1.0 + cos(w0))
                    val b2 = (1.0 + cos(w0)) / 2.0
                    val a0 = 1.0 + alpha
                    val a1 = -2.0 * cos(w0)
                    val a2 = 1.0 - alpha
                    doubleArrayOf(b0, b1, b2, a0, a1, a2)
                }
                FilterType.NOTCH -> {
                    val b0 = 1.0
                    val b1 = -2.0 * cos(w0)
                    val b2 = 1.0
                    val a0 = 1.0 + alpha
                    val a1 = -2.0 * cos(w0)
                    val a2 = 1.0 - alpha
                    doubleArrayOf(b0, b1, b2, a0, a1, a2)
                }
            }
            // Normalize by a0, matching normal 5-double biquad memory layout.
            // cs = [b0, b1, b2, a0, a1, a2]
            val a0 = cs[3]
            return Biquad(
                cs[0] / a0, cs[1] / a0, cs[2] / a0,
                cs[4] / a0, cs[5] / a0
            ).also { it.scheduleReset() }
        }

        /** LN2 exposed for tests. */
        val lnOf2 get() = LN2
    }
}
