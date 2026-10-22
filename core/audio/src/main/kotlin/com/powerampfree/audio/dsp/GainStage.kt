package com.powerampfree.audio.dsp

import kotlin.math.pow

/**
 * Volume/preamp model — original design.
 *
 * The analyzed player decouples *content gain* (EQ/preamp, digital) from
 * *playback volume* (device stream). We reproduce that split:
 *
 *  - [contentGainDb] applies a fixed digital gain in the DSP chain (preamp).
 *  - [volumeLinear] is the user's 0..1 volume; mapped with a power curve so the
 *    low end is usable (curve 2.0 = perceptual-ish standard mapping).
 *  - [SoftLimiter] sits after gain stages so +dB content never clips.
 */
class GainStage(
    override val sampleRateHz: Int,
    var contentGainDb: Float = 0f,
    var volumeLinear: Float = 1f,
    val volumeCurvePower: Double = 2.0,
) : AudioProcessor {

    private var lastGain = Float.MIN_VALUE

    private fun currentGainLinear(): Float {
        val v = volumeLinear.coerceIn(0f, 1f).toDouble()
        val mapped = v.pow(volumeCurvePower)
        val totalDb = contentGainDb + 20.0 * kotlin.math.log10(mapped + 1e-20)
        return 10.0.pow(totalDb / 20.0).toFloat().also { lastGain = it }
    }

    override fun process(buffer: FloatArray, frames: Int, channels: Int) {
        val g = currentGainLinear()
        for (i in 0 until frames * channels) buffer[i] = buffer[i] * g
    }

    override fun reset() { lastGain = Float.MIN_VALUE }

    /** Current effective linear gain (exposed for tests + UI). */
    fun effectiveGainLinear(): Float = currentGainLinear()
}

