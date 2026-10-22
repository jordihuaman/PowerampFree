package com.powerampfree.audio.dsp

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * EQ preset — our own original, JSON-based format (deliberately NOT the binary
 * format found in the analyzed reference app; interoperability happens through
 * [AutoEqImporter] for published AutoEQ text presets).
 *
 * Example:
 * ```json
 * {"name":"Headphones","preampDb":-3.5,"bands":[{"type":"PEAKING","freqHz":1234,"gainDb":-3.2,"q":1.41}]}
 * ```
 *
 * Layers (matching the observed EQ screen concept: graphic EQ + Tone knobs +
 * limiter are independent modules over the same parametric engine):
 *  - [bands]            manual parametric bands
 *  - [graphicGainsDb]   the 10 fixed-band graphic EQ gains at [GRAPHIC_FREQS_HZ]
 *  - [toneBassPct]      0..100% knob → low shelf at [TONE_BASS_HZ] (max [TONE_MAX_DB])
 *  - [toneTreblePct]    0..100% knob → high shelf at [TONE_TREBLE_HZ]
 *  - [limitOn]          soft limiter module toggle
 * All layers are resolved together by [resolvedBands] before reaching the DSP.
 */
@Serializable
data class EqPreset(
    val name: String,
    val preampDb: Float = 0f,
    val bands: List<Band> = emptyList(),
    val graphicGainsDb: List<Float> = List(GRAPHIC_FREQS_HZ.size) { 0f },
    val graphicOn: Boolean = false,
    val toneBassPct: Float = 0f,
    val toneTreblePct: Float = 0f,
    val toneOn: Boolean = false,
    val limitOn: Boolean = false,
) {
    fun toJson(): String = json.encodeToString(this)
    fun toGainStageConfig(): Float = preampDb

    /** Number of the graphic layer present in [resolvedBands] (10 or 0). */
    val graphicBandCount: Int get() = if (graphicOn) GRAPHIC_FREQS_HZ.size else 0

    /**
     * All bands that must be fed to the parametric engine: manual bands first,
     * then the graphic layer, then the Tone shelves (order = observable layering).
     */
    fun resolvedBands(): List<Band> = buildList {
        addAll(bands)
        if (graphicOn) {
            GRAPHIC_FREQS_HZ.forEachIndexed { i, f ->
                add(Band(FilterType.PEAKING, f, graphicGainsDb.getOrElse(i) { 0f }, GRAPHIC_Q))
            }
        }
        if (toneOn) {
            add(
                Band(
                    FilterType.LOW_SHELF,
                    TONE_BASS_HZ,
                    pctToDb(toneBassPct),
                    TONE_Q,
                ),
            )
            add(
                Band(
                    FilterType.HIGH_SHELF,
                    TONE_TREBLE_HZ,
                    pctToDb(toneTreblePct),
                    TONE_Q,
                ),
            )
        }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        fun fromJson(text: String): EqPreset = json.decodeFromString(text)

        /** Fixed center frequencies of the graphic EQ layer (10 bands). */
        val GRAPHIC_FREQS_HZ = intArrayOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)

        /** Short labels shown under the graphic faders. */
        val GRAPHIC_LABELS = arrayOf(
            "31", "62", "125", "250", "500", "1K", "2K", "4K", "8K", "16K",
        )

        const val GRAPHIC_Q = 1.41f
        const val TONE_BASS_HZ = 250
        const val TONE_TREBLE_HZ = 4000
        const val TONE_Q = 0.7f

        /** Tone knobs are 0..100% mapped onto 0..+[TONE_MAX_DB] dB. */
        const val TONE_MAX_DB = 12f

        fun pctToDb(pct: Float): Float = (pct.coerceIn(0f, 100f) / 100f) * TONE_MAX_DB
    }
}

/**
 * Importer for the public AutoEQ parametric preset format (community standard:
 * CSV rows of `freq,gainDb,q` per filter). Allowed by our legal constraints:
 * we consume the *published user data files*, not any proprietary binary.
 */
object AutoEqImporter {
    fun parse(text: String): EqPreset {
        val bands = buildList {
            for (line in text.lineSequence()) {
                val t = line.trim()
                if (t.isEmpty() || t.startsWith("#")) continue
                val parts = t.split(Regex("[,;\t]+")).map { it.trim().removeSuffix("Hz").trim() }
                if (parts.size < 3) continue
                val f = parts[0].toFloatOrNull() ?: continue
                val g = parts[1].toFloatOrNull() ?: continue
                val q = parts[2].toFloatOrNull() ?: continue
                if (f <= 0f) continue
                add(Band(FilterType.PEAKING, f.toInt(), g, q))
            }
        }
        return EqPreset(name = "AutoEQ", bands = bands)
    }
}
