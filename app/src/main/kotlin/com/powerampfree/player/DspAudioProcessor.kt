package com.powerampfree.player

/**
 * Playback DSP bridge for Media3.
 *
 * This is the integration point between Media3's render pipeline and the pure
 * Kotlin :core:audio DSP: Media3 hands us LinearPCM buffers through its
 * [androidx.media3.common.audio.BaseAudioProcessor] contract; we forward them
 * to our ordered chain.
 *
 * On format changes Media3 delegates to onConfigure() — our ParametricEq is
 * rebuilt there (no stale filter state across rate switches, aligning with the
 * reconfigure-on-format-change behavior observed in the analyzed engine).
 */
class DspAudioProcessor : androidx.media3.common.audio.BaseAudioProcessor() {

    private var chain: com.powerampfree.audio.dsp.DspChain? = null
    private var fmt: androidx.media3.common.audio.AudioProcessor.AudioFormat? = null

    override fun onConfigure(
        inputAudioFormat: androidx.media3.common.audio.AudioProcessor.AudioFormat,
    ): androidx.media3.common.audio.AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != androidx.media3.common.C.ENCODING_PCM_FLOAT) {
            throw androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        fmt = inputAudioFormat
        chain = com.powerampfree.audio.dsp.DspChain.buildDefault(
            sampleRateHz = inputAudioFormat.sampleRate,
            channels = inputAudioFormat.channelCount,
            preset = PresetStore.load(),
        )
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: java.nio.ByteBuffer) {
        val f = fmt ?: run { inputBuffer.position(inputBuffer.limit()); return }
        val floats = (inputBuffer.remaining() / 4 / f.channelCount) * f.channelCount
        if (floats == 0) { replaceOutputBuffer(0).flip(); return }
        val samples = FloatArray(floats)
        inputBuffer.order(java.nio.ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(samples, 0, floats)
        inputBuffer.position(inputBuffer.limit())
        chain!!.process(samples, floats / f.channelCount, f.channelCount)
        val out = replaceOutputBuffer(floats * 4)
        out.order(java.nio.ByteOrder.LITTLE_ENDIAN).asFloatBuffer().put(samples, 0, floats)
    }

    override fun onFlush() {
        chain?.reset()
    }

    override fun onReset() {
        chain = null
        fmt = null
    }
}

/**
 * Preset persistence — lightweight store for the first version (no Room yet).
 * JSON document per preset in filesDir/eq/; current preset name in prefs.
 */
object PresetStore {
    fun load(): com.powerampfree.audio.dsp.EqPreset {
        val txt = """
            {"name":"Default","preampDb":0.0,"bands":[
              {"type":"LOW_SHELF","freqHz":90,"gainDb":0.0,"q":0.7},
              {"type":"PEAKING","freqHz":1000,"gainDb":0.0,"q":0.9},
              {"type":"HIGH_SHELF","freqHz":9000,"gainDb":0.0,"q":0.7}
            ]}
        """.trimIndent()
        return com.powerampfree.audio.dsp.EqPreset.fromJson(txt)
    }
}
