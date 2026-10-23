package com.powerampfree.player

import android.content.Context

/**
 * Audio + headset settings (blocks 8 & 10, sprint 7).
 *
 * Persisted in their own prefs file so the existing export/import (which walks
 * a list of prefs names) carries them along. Values are read live by the
 * service at event time (focus changes, headset events, media buttons); only
 * Replay Gain needs a live re-apply because it feeds the DSP chain.
 */
object AudioSettings {
    private const val PREFS = "audio_settings"

    // --- headset connection (block 10) ------------------------------------
    const val PAUSE_ON_DISCONNECT = "pause_on_disconnect"
    const val RESUME_ON_WIRED = "resume_on_wired"
    const val RESUME_ON_BLUETOOTH = "resume_on_bluetooth"

    // --- headset buttons (block 10) ---------------------------------------
    const val RESPOND_TO_BUTTONS = "respond_to_buttons"
    const val WIRED_ACTION = "wired_action"
    const val BT_ACTION = "bt_action"

    /** What a single headset press does (multi-press gestures stay fixed). */
    const val ACTION_PLAY_PAUSE = "play_pause"
    const val ACTION_NEXT = "next"
    const val ACTION_PREV = "prev"

    // --- audio focus (block 8) --------------------------------------------
    const val FOCUS_PAUSE = "focus_pause"
    const val FOCUS_RESUME = "focus_resume"
    const val FOCUS_DUCK = "focus_duck"

    // --- replay gain (block 8) --------------------------------------------
    const val RG_ENABLED = "rg_enabled"
    const val RG_SOURCE = "rg_source"
    const val RG_PREGAIN_DB = "rg_pregain_db"
    const val RG_SOURCE_TRACK = "track"
    const val RG_SOURCE_ALBUM = "album"

    private fun sp(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun bool(context: Context, key: String, def: Boolean): Boolean =
        sp(context).getBoolean(key, def)

    private fun setBool(context: Context, key: String, on: Boolean) {
        sp(context).edit().putBoolean(key, on).apply()
    }

    private fun str(context: Context, key: String, def: String): String =
        sp(context).getString(key, def) ?: def

    private fun setStr(context: Context, key: String, value: String) {
        sp(context).edit().putString(key, value).apply()
    }

    /**
     * Tolerant numeric reader: an imported backup may have stored the value as
     * Int/Long/Double (org.json re-parses numbers by magnitude), not the
     * original Float — same class of bug as the `_ms` Long narrowing.
     */
    private fun num(context: Context, key: String, def: Float): Float =
        when (val v = sp(context).all[key]) {
            is Float -> v
            is Double -> v.toFloat()
            is Int -> v.toFloat()
            is Long -> v.toFloat()
            is Number -> v.toFloat()
            else -> def
        }

    // --- headset connection ------------------------------------------------

    fun pauseOnDisconnect(context: Context): Boolean =
        bool(context, PAUSE_ON_DISCONNECT, true)

    fun setPauseOnDisconnect(context: Context, on: Boolean) =
        setBool(context, PAUSE_ON_DISCONNECT, on)

    fun resumeOnWired(context: Context): Boolean =
        bool(context, RESUME_ON_WIRED, true)

    fun setResumeOnWired(context: Context, on: Boolean) =
        setBool(context, RESUME_ON_WIRED, on)

    fun resumeOnBluetooth(context: Context): Boolean =
        bool(context, RESUME_ON_BLUETOOTH, true)

    fun setResumeOnBluetooth(context: Context, on: Boolean) =
        setBool(context, RESUME_ON_BLUETOOTH, on)

    // --- headset buttons ---------------------------------------------------

    fun respondToButtons(context: Context): Boolean =
        bool(context, RESPOND_TO_BUTTONS, true)

    fun setRespondToButtons(context: Context, on: Boolean) =
        setBool(context, RESPOND_TO_BUTTONS, on)

    fun wiredAction(context: Context): String =
        str(context, WIRED_ACTION, ACTION_PLAY_PAUSE)

    fun setWiredAction(context: Context, action: String) =
        setStr(context, WIRED_ACTION, action)

    fun btAction(context: Context): String =
        str(context, BT_ACTION, ACTION_PLAY_PAUSE)

    fun setBtAction(context: Context, action: String) =
        setStr(context, BT_ACTION, action)

    // --- audio focus -------------------------------------------------------

    fun focusPause(context: Context): Boolean = bool(context, FOCUS_PAUSE, true)
    fun setFocusPause(context: Context, on: Boolean) = setBool(context, FOCUS_PAUSE, on)

    fun focusResume(context: Context): Boolean = bool(context, FOCUS_RESUME, true)
    fun setFocusResume(context: Context, on: Boolean) = setBool(context, FOCUS_RESUME, on)

    fun focusDuck(context: Context): Boolean = bool(context, FOCUS_DUCK, true)
    fun setFocusDuck(context: Context, on: Boolean) = setBool(context, FOCUS_DUCK, on)

    // --- replay gain -------------------------------------------------------

    fun rgEnabled(context: Context): Boolean = bool(context, RG_ENABLED, false)
    fun setRgEnabled(context: Context, on: Boolean) = setBool(context, RG_ENABLED, on)

    fun rgSource(context: Context): String =
        str(context, RG_SOURCE, RG_SOURCE_TRACK)

    fun setRgSource(context: Context, source: String) =
        setStr(context, RG_SOURCE, source)

    fun rgPregainDb(context: Context): Float =
        num(context, RG_PREGAIN_DB, 0f)

    fun setRgPregainDb(context: Context, db: Float) {
        sp(context).edit().putFloat(RG_PREGAIN_DB, db.coerceIn(-20f, 20f)).apply()
    }

    /**
     * Net Replay Gain trim currently applied in the DSP chain: the configured
     * preamp when enabled, 0 dB otherwise. Per-track tag values are not parsed
     * yet (pending), so this is a global trim driven by the preamp slider.
     */
    fun rgAppliedDb(context: Context): Float =
        if (rgEnabled(context)) rgPregainDb(context) else 0f
}
