package com.powerampfree.audio.dsp

/**
 * Streaming stereo/mono DSP processor used by the player pipeline. Callers feed
 * interleaved 32-bit float PCM (format Float32, interleaved per frame).
 */
interface AudioProcessor {
    /** Sample rate the processor was configured at. */
    val sampleRateHz: Int

    /**
     * Process [frames] frames of interleaved PCM in-place buffer [buffer].
     * @param channels 1=mono, 2=stereo etc.
     */
    fun process(buffer: FloatArray, frames: Int, channels: Int)

    /** Reset internal state (samples, filters, envelopes). */
    fun reset()
}
