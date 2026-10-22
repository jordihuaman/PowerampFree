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
 */
@Serializable
data class EqPreset(
    val name: String,
    val preampDb: Float = 0f,
    val bands: List<Band> = emptyList(),
) {
    fun toJson(): String = json.encodeToString(this)
    fun toGainStageConfig(): Float = preampDb

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        fun fromJson(text: String): EqPreset = json.decodeFromString(text)
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
