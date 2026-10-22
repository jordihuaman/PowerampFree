package com.powerampfree.audio.dsp

import kotlin.math.abs
import kotlin.math.exp

/**
 * Safety limiter with an exact per-block guarantee:
 *
 *  - Scans the block to find the true peak.
 *  - Below [ceiling]: identity (no coloration on well-trimmed content).
 *  - Above it: one static gain ceiling/peak per block, so |out| <= ceiling
 *    holds exactly; smoothing happens BETWEEN blocks (release), a standard
 *    union of certainty and musicality first described in classic literature.
 *    here attack is immediate, release is gradual (~80 ms).
 */
class SoftLimiter(override val sampleRateHz: Int, val ceiling: Float = 0.98f) : AudioProcessor {

    /** Module toggle (Limit button on the EQ screen); disabled = pure passthrough. */
    var enabled: Boolean = true

    private val releaseCoeff = exp(-1.0 / (0.080 * sampleRateHz))
    private var env = 0f

    override fun process(buffer: FloatArray, frames: Int, channels: Int) {
        if (!enabled) return
        var peak = 0f
        for (i in 0 until frames * channels) {
            val a = abs(buffer[i])
            if (a > peak) peak = a
        }
        // Release envelope between blocks.
        env = if (peak > env) peak else (env * releaseCoeff.toFloat())
        val gr = if (env <= ceiling) 1f else ceiling / env
        if (gr == 1f) return
        for (i in 0 until frames * channels) buffer[i] *= gr
    }

    override fun reset() { env = 0f }

    /** Current smoothed envelope (for UI meter). */
    fun envelope(): Float = env
}
