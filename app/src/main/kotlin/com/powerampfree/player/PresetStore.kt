package com.powerampfree.player

import android.content.Context
import java.io.File

/**
 * Preset persistence:
 *  - current live preset: serialized JSON in SharedPreferences;
 *  - named saved presets: one JSON file per preset in `filesDir/eq/`.
 * All in our own original JSON format (no "PaP" binary compat by design).
 */
object PresetStore {

    private const val PREFS = "dsp_presets"
    private const val KEY_CURRENT_JSON = "current_preset_json"
    private const val DIR = "eq"

    fun loadCurrent(context: Context): com.powerampfree.audio.dsp.EqPreset {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val txt = prefs.getString(KEY_CURRENT_JSON, null)
        if (txt != null) {
            runCatching { return com.powerampfree.audio.dsp.EqPreset.fromJson(txt) }
        }
        return defaultPreset()
    }

    fun storeCurrent(context: Context, preset: com.powerampfree.audio.dsp.EqPreset) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CURRENT_JSON, preset.toJson())
            .apply()
    }

    fun saveNamed(context: Context, preset: com.powerampfree.audio.dsp.EqPreset) {
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        val safe = preset.name.ifBlank { "preset" }
            .map { if (it.isLetterOrDigit() || it in " _-") it else '_' }
            .joinToString("")
        File(dir, "$safe.json").writeText(preset.toJson())
    }

    fun listNamed(context: Context): List<com.powerampfree.audio.dsp.EqPreset> {
        val dir = File(context.filesDir, DIR)
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles { f -> f.isFile && f.name.endsWith(".json") }
            ?.sortedBy { it.name }
            ?.mapNotNull { f ->
                runCatching { com.powerampfree.audio.dsp.EqPreset.fromJson(f.readText()) }.getOrNull()
            }
            ?: emptyList()
    }

    fun defaultPreset(): com.powerampfree.audio.dsp.EqPreset =
        com.powerampfree.audio.dsp.EqPreset(
            name = "Default",
            preampDb = 0.0f,
            bands = listOf(
                com.powerampfree.audio.dsp.Band(
                    com.powerampfree.audio.dsp.FilterType.LOW_SHELF, 90, 0.0f, 0.7f,
                ),
                com.powerampfree.audio.dsp.Band(
                    com.powerampfree.audio.dsp.FilterType.PEAKING, 1000, 0.0f, 0.9f,
                ),
                com.powerampfree.audio.dsp.Band(
                    com.powerampfree.audio.dsp.FilterType.HIGH_SHELF, 9000, 0.0f, 0.7f,
                ),
            ),
        )
}
