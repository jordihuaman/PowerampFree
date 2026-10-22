package com.powerampfree.player

import android.content.Context

/**
 * Look & Feel persistence (block 7): theme mode, accent color, bold font,
 * start-at-library. Own prefs, no reference-app resources.
 */
object AppearanceStore {

    private const val PREFS = "appearance"
    private const val KEY_MODE = "theme_mode"
    private const val KEY_ACCENT = "accent_color" // -1 = default scheme color
    private const val KEY_BOLD = "bold_font"
    private const val KEY_START_LIB = "start_at_library"

    const val MODE_AUTO = "auto"
    const val MODE_LIGHT = "light"
    const val MODE_DARK = "dark"

    /** Named accent swatches offered on the Look & Feel screen. */
    val ACCENTS: List<Pair<String, Int>> = listOf(
        "Azul" to 0xFF39A7E4.toInt(),
        "Verde" to 0xFF4CD56A.toInt(),
        "Violeta" to 0xFF9C6ADE.toInt(),
        "Ámbar" to 0xFFFFB74D.toInt(),
        "Rosa" to 0xFFF06292.toInt(),
    )

    fun themeMode(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_MODE, MODE_AUTO) ?: MODE_AUTO

    fun setThemeMode(context: Context, mode: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_MODE, mode).apply()
    }

    /** null → scheme default. */
    fun accent(context: Context): Int? {
        val v = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_ACCENT, -1)
        return if (v == -1) null else v
    }

    fun setAccent(context: Context, argb: Int?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_ACCENT, argb ?: -1).apply()
    }

    fun accentName(context: Context): String {
        val a = accent(context) ?: return "Predeterminado"
        return ACCENTS.firstOrNull { it.second == a }?.first ?: "Personalizado"
    }

    fun boldFont(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_BOLD, false)

    fun setBoldFont(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_BOLD, on).apply()
    }

    fun startAtLibrary(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_START_LIB, false)

    fun setStartAtLibrary(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_START_LIB, on).apply()
    }
}
