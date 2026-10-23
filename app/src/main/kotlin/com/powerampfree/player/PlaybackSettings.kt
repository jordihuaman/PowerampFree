package com.powerampfree.player

import android.content.Context

/**
 * Playback settings (non-DSP, service-side): crossfade length in ms etc.
 * Persisted so the plan survives process death like the queue does.
 */
object PlaybackSettings {
    private const val PREFS = "playback_settings"
    private const val KEY_CROSSFADE_MS = "crossfade_ms"
    private const val KEY_REPEAT_MODE = "repeat_mode"
    private const val KEY_SHUFFLE_ON = "shuffle_on"

    fun crossfadeMs(context: Context): Long {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // Tolerant reader: an imported backup may have stored Int (org.json
        // parses any number fitting Int as Integer).
        return when (val v = sp.all[KEY_CROSSFADE_MS]) {
            is Long -> v
            is Int -> v.toLong()
            else -> 0L
        }
    }

    fun setCrossfadeMs(context: Context, ms: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(KEY_CROSSFADE_MS, ms.coerceIn(0L, 12_000L)).apply()
    }

    // --- repeat / shuffle (player screen; survives process death) -----------

    const val REPEAT_OFF = "off"
    const val REPEAT_ONE = "one"
    const val REPEAT_ALL = "all"

    fun repeatMode(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_REPEAT_MODE, REPEAT_OFF) ?: REPEAT_OFF

    fun setRepeatMode(context: Context, mode: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_REPEAT_MODE, mode).apply()
    }

    fun shuffleEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_SHUFFLE_ON, false)

    fun setShuffleEnabled(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SHUFFLE_ON, on).apply()
    }
}
