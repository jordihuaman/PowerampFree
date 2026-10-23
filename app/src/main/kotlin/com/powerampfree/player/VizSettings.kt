package com.powerampfree.player

import android.content.Context

/**
 * Visualization settings (block 14 núcleo).
 *
 * Own prefs file so Export/Import carries it. Read live (cheap cached-prefs
 * reads inside the 50 ms spectrum poll) so the player/EQ screens react
 * without session pushes:
 *  - `player_spectrum` — "Visualization On Player Screen" (the reference
 *    turns it on from the bars button on the player; here: header 📊 toggle),
 *  - `spectrum_style` — "classic" rects / "rounded" rounded bars,
 *  - `panel_opacity` — bars panel opacity 0–100 (reference "Top Visualization
 *    Panel Opacity" 50/60 % family),
 *  - `eq_spectrum` — "Equalizer Screen Spectrum": "disabled"/"classic"/
 *    "rounded" (reference: Disabled/Classic/Rounded).
 *
 * Out of scope (documented honestly in POWERAMP_FEATURES.md §14): `.milk`
 * 3rd-party presets/rescans, Visualization Delay, render toggles (HD/Crop),
 * preset rotation.
 */
object VizSettings {
    private const val PREFS = "viz_settings"

    const val PLAYER_SPECTRUM = "player_spectrum"
    const val SPECTRUM_STYLE = "spectrum_style"
    const val PANEL_OPACITY = "panel_opacity"
    const val EQ_SPECTRUM = "eq_spectrum"

    const val STYLE_CLASSIC = "classic"
    const val STYLE_ROUNDED = "rounded"

    const val EQ_DISABLED = "disabled"

    /** Reference default style of the built-in bars. */
    const val OPACITY_DEFAULT = 60
    const val OPACITY_MAX = 100

    private fun sp(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun playerSpectrum(context: Context): Boolean =
        sp(context).getBoolean(PLAYER_SPECTRUM, true)

    fun setPlayerSpectrum(context: Context, on: Boolean) =
        sp(context).edit().putBoolean(PLAYER_SPECTRUM, on).apply()

    fun spectrumStyle(context: Context): String =
        sp(context).getString(SPECTRUM_STYLE, STYLE_ROUNDED)?.ifBlank { STYLE_ROUNDED }
            ?: STYLE_ROUNDED

    fun setSpectrumStyle(context: Context, style: String) =
        sp(context).edit()
            .putString(SPECTRUM_STYLE, if (style == STYLE_CLASSIC) STYLE_CLASSIC else STYLE_ROUNDED)
            .apply()

    fun panelOpacity(context: Context): Int =
        sp(context).getInt(PANEL_OPACITY, OPACITY_DEFAULT)

    fun setPanelOpacity(context: Context, v: Int) =
        sp(context).edit().putInt(PANEL_OPACITY, v.coerceIn(0, OPACITY_MAX)).apply()

    fun eqSpectrum(context: Context): String =
        sp(context).getString(EQ_SPECTRUM, STYLE_ROUNDED)?.let {
            if (it == EQ_DISABLED) EQ_DISABLED else
                if (it == STYLE_CLASSIC) STYLE_CLASSIC else STYLE_ROUNDED
        } ?: STYLE_ROUNDED

    fun setEqSpectrum(context: Context, mode: String) =
        sp(context).edit().putString(
            EQ_SPECTRUM,
            when (mode) {
                EQ_DISABLED -> EQ_DISABLED
                STYLE_CLASSIC -> STYLE_CLASSIC
                else -> STYLE_ROUNDED
            },
        ).apply()
}
