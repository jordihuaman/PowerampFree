package com.powerampfree.player

import android.content.Context

/**
 * Album art + background settings (blocks 12/13, sprint 8).
 *
 * Own prefs file so Export/Import carries it; plain booleans/ints survive the
 * org.json round-trip (no numeric-narrowing class involved). Read live by the
 * composables — display-only settings, no session push needed.
 */
object ArtSettings {
    private const val PREFS = "art_settings"

    // --- block 12: album art ----------------------------------------------
    const val HIGH_RES_ART = "high_res_art"
    const val KEEP_ASPECT = "keep_aspect"

    // --- block 13: art-derived background -----------------------------------
    const val BG_ENABLED = "bg_enabled"
    const val BG_BLUR = "bg_blur"
    const val BG_GRADIENT = "bg_gradient"

    const val BLUR_MAX = 10
    const val GRADIENT_MAX = 10

    private fun sp(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // --- block 12 -----------------------------------------------------------

    /** Send the full-resolution art to the session/notification consumers. */
    fun highResArt(context: Context): Boolean = sp(context).getBoolean(HIGH_RES_ART, true)
    fun setHighResArt(context: Context, on: Boolean) = sp(context).edit().putBoolean(HIGH_RES_ART, on).apply()

    /** Display fill/crop when false (square tiles), aspect-fit when true. */
    fun keepAspect(context: Context): Boolean = sp(context).getBoolean(KEEP_ASPECT, true)
    fun setKeepAspect(context: Context, on: Boolean) = sp(context).edit().putBoolean(KEEP_ASPECT, on).apply()

    // --- block 13 -----------------------------------------------------------

    fun bgEnabled(context: Context): Boolean = sp(context).getBoolean(BG_ENABLED, true)
    fun setBgEnabled(context: Context, on: Boolean) = sp(context).edit().putBoolean(BG_ENABLED, on).apply()

    /** Background blur strength 0..10 (0 = plain art). */
    fun bgBlur(context: Context): Int = sp(context).getInt(BG_BLUR, 5)
    fun setBgBlur(context: Context, v: Int) = sp(context).edit().putInt(BG_BLUR, v.coerceIn(0, BLUR_MAX)).apply()

    /** Bottom darkening 0..10 (gradient overlay of the background). */
    fun bgGradient(context: Context): Int = sp(context).getInt(BG_GRADIENT, 4)
    fun setBgGradient(context: Context, v: Int) =
        sp(context).edit().putInt(BG_GRADIENT, v.coerceIn(0, GRADIENT_MAX)).apply()
}
