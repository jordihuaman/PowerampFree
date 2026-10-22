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
        val preset = EqPreset(
            "Hot", preampDb = 12f,
            bands = (1..10).map { Band(FilterType.PEAKING, 100 * it, 12f, 0.7f) },
            limitOn = true,
        )
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
    fun limiter_disabled_is_passthrough() {
        val preset = EqPreset(
            "Hot", preampDb = 12f,
            bands = (1..10).map { Band(FilterType.PEAKING, 100 * it, 12f, 0.7f) },
            limitOn = false,
        )
        val chain = DspChain.buildDefault(44100, 2, preset)
        val buf = FloatArray(4410 * 2) { 0.9f }
        chain.process(buf, 4410, 2)
        // Without the limiter module the hot preset MUST be able to exceed the ceiling.
        assertTrue(buf.any { it > 1.0f }, "disabled limiter clipped anyway")
    }

    @Test
    fun resolved_bands_layers_compose() {
        val preset = EqPreset(
            name = "Layers",
            bands = listOf(Band(FilterType.PEAKING, 1000, 2f, 1f)),
            graphicGainsDb = List(EqPreset.GRAPHIC_FREQS_HZ.size) { 3f },
            graphicOn = true,
            toneBassPct = 25f,
            toneTreblePct = 50f,
            toneOn = true,
        )
        val r = preset.resolvedBands()
        assertEquals(13, r.size)
        // Graphic layer: fixed freqs, 3 dB each.
        r[1].let { assertEquals(31, it.freqHz); assertEquals(3f, it.gainDb); assertEquals(1.41f, it.q) }
        r[10].let { assertEquals(16000, it.freqHz) }
        // Tone shelves last, mapped % → dB.
        r[11].let { assertEquals(FilterType.LOW_SHELF, it.type); assertEquals(250, it.freqHz); assertEquals(3.0f, it.gainDb) }
        r[12].let { assertEquals(FilterType.HIGH_SHELF, it.type); assertEquals(4000, it.freqHz); assertEquals(6.0f, it.gainDb) }
        // Layers off → only manual bands.
        val off = preset.copy(graphicOn = false, toneOn = false)
        assertEquals(listOf(Band(FilterType.PEAKING, 1000, 2f, 1f)), off.resolvedBands())
        assertEquals(0, off.graphicBandCount)
    }

    @Test
    fun resolved_bands_chain_accepts_full_layering() {
        val preset = EqPreset(
            name = "Full",
            bands = (1..12).map { Band(FilterType.PEAKING, 100 * it, 0.5f, 1f) },
            graphicOn = true,
            toneOn = true,
            limitOn = true,
        )
        val chain = DspChain.buildDefault(44100, 2, preset)
        val buf = FloatArray(2205 * 2) { 0.5f }
        chain.process(buf, 2205, 2)
        buf.forEach { assertTrue(it.isFinite()) }
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
