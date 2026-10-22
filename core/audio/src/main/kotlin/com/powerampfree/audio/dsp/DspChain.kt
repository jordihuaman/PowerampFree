package com.powerampfree.audio.dsp

/**
 * Ordered DSP chain — mirrors the observed engine concept: one pipeline object
 * owns the processing order (preamp/gain → EQ → limiter), configured per track
 * session and rebuilt on format changes.
 */
class DspChain(override val sampleRateHz: Int, val channels: Int, val processors: List<AudioProcessor>) : AudioProcessor {

    override fun process(buffer: FloatArray, frames: Int, channels: Int) {
        for (p in processors) p.process(buffer, frames, channels)
    }

    override fun reset() {
        for (p in processors) p.reset()
    }

    companion object {
        /** Standard musical-truth order: trim/absolute level → tonal → safety. */
        fun buildDefault(sampleRateHz: Int, channels: Int, preset: EqPreset, volume: Float = 1f): DspChain {
            val gain = GainStage(sampleRateHz, contentGainDb = preset.preampDb, volumeLinear = volume)
            val eq = ParametricEq.build(ParametricEq.Config.of(preset.bands), sampleRateHz, channels)
            val limiter = SoftLimiter(sampleRateHz)
            return DspChain(sampleRateHz, channels, listOf(gain, eq, limiter))
        }
        fun processorsByName(chain: DspChain): List<String> = chain.processors.map { it::class.simpleName ?: "?" }
    }
}
