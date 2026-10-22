package com.powerampfree.audio.dsp

import kotlin.test.*

class ChainAndPresetTest {

    @Test
    fun preset_json_round_trip() {
        val p = EqPreset(
            name = "Test",
            preampDb = -3.5f,
            bands = listOf(Band(FilterType.PEAKING, 1000, 2f, 1f), Band(FilterType.LOW_SHELF, 80, -4f, 0.7f)),
        )
        val p2 = EqPreset.fromJson(p.toJson())
        assertEquals(p, p2)
    }

    @Test
    fun preset_json_extra_keys_tolerated() {
        val json = """{"name":"X","preampDb":0.0,"bands":[{"type":"PEAKING","freqHz":500,"gainDb":1.0,"q":0.7,"enabled":true}],"extra":1}"""
        val p = EqPreset.fromJson(json)
        assertEquals(1, p.bands.size)
    }

    @Test
    fun autoeq_csv_import() {
        val csv = "# AutoEQ preset\n100,4.0,0.7\n400,-2.0,1.41\n2000,6.0,0.71\n"
        val p = AutoEqImporter.parse(csv)
        assertEquals(3, p.bands.size)
        assertEquals(Band(FilterType.PEAKING, 400, -2f, 1.41f), p.bands[1])
    }

    @Test
    fun chain_trims_no_output_clipping() {
        val preset = EqPreset("Hot", preampDb = 12f, bands = (1..10).map { Band(FilterType.PEAKING, 100 * it, 12f, 0.7f) })
        val chain = DspChain.buildDefault(44100, 2, preset)
        // Loud woman-wave input near full scale.
        val frames = 4410
        val buf = FloatArray(frames * 2) { (if (it % 2 == 0) kotlin.math.cos(it / 20.0) else kotlin.math.cos(it / 30.0 + 1f)).toFloat() * 0.95f }
        chain.process(buf, frames, 2)
        for (v in buf) {
            assertTrue(v.isFinite())
            assertTrue(v <= 1.0f, "value above 1.0: $v") // limiter ceiling respects [-1,1]
            assertTrue(v >= -1.0f, "value below -1.0: $v")
        }
    }

    @Test
    fun chain_silent_path_when_flat_preset_volume_100() {
        val preset = EqPreset("Flat", 0f, listOf())
        val chain = DspChain.buildDefault(44100, 2, preset, volume = 1f)
        val buf = FloatArray(1000 * 2) { 0.1f } // small DC content, both channels
        chain.process(buf, 1000, 2)
        // Flat preset + unity volume ⇒ output value essentially equals input value.
        assertEquals(0.1f, buf[0], 1e-3f, "flat path close to unity")
    }

    @Test
    fun gain_stage_volume_mapping_monotonic() {
        val g = GainStage(44100)
        var last = -1f
        for (vol in floatArrayOf(0f, 0.1f, 0.33f, 0.5f, 0.77f, 1f)) {
            g.volumeLinear = vol
            val gain = g.effectiveGainLinear()
            assertTrue(gain > last, "gain must be monotonic in volume: vol=$vol gain=$gain")
            last = gain
        }
        g.volumeLinear = 0f
        assertEquals(0f, g.effectiveGainLinear(), 1e-5f, "volume=0 must give near-silence")
    }
}
