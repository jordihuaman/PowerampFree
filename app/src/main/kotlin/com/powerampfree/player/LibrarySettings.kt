package com.powerampfree.player

import android.content.Context

/**
 * Library settings (block 11).
 *
 * Own prefs file so Export/Import carries it; plain booleans/ints survive the
 * org.json round-trip. Read live by the service (progress/count thresholds)
 * and `LibraryLoader` (filters). List/stat row data itself goes to
 * [TrackStateStore], these only hold the tunables.
 */
object LibrarySettings {
    private const val PREFS = "library_settings"

    // --- per-track progress ---------------------------------------------------
    const val STORE_PROGRESS = "store_progress"
    const val PROGRESS_MIN_MINUTES = "progress_min_minutes"

    /** Minimum track duration (min) that keeps a remembered position. */
    const val PROGRESS_MIN_DEFAULT = 45
    const val PROGRESS_MIN_MAX = 90

    // --- stats -----------------------------------------------------------------
    const val COUNT_PCT = "count_pct"

    /** Count a listen after playing at least this percentage. */
    const val COUNT_PCT_DEFAULT = 50
    const val COUNT_PCT_MAX = 100

    // --- filters ----------------------------------------------------------------
    const val IGNORE_SHORT = "ignore_short"

    private fun sp(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun storeProgress(context: Context): Boolean = sp(context).getBoolean(STORE_PROGRESS, true)
    fun setStoreProgress(context: Context, on: Boolean) =
        sp(context).edit().putBoolean(STORE_PROGRESS, on).apply()

    fun progressMinMinutes(context: Context): Int =
        sp(context).getInt(PROGRESS_MIN_MINUTES, PROGRESS_MIN_DEFAULT)
    fun setProgressMinMinutes(context: Context, v: Int) =
        sp(context).edit().putInt(PROGRESS_MIN_MINUTES, v.coerceIn(1, PROGRESS_MIN_MAX)).apply()

    fun countPct(context: Context): Int = sp(context).getInt(COUNT_PCT, COUNT_PCT_DEFAULT)
    fun setCountPct(context: Context, v: Int) =
        sp(context).edit().putInt(COUNT_PCT, v.coerceIn(1, COUNT_PCT_MAX)).apply()

    fun ignoreShort(context: Context): Boolean = sp(context).getBoolean(IGNORE_SHORT, true)
    fun setIgnoreShort(context: Context, on: Boolean) =
        sp(context).edit().putBoolean(IGNORE_SHORT, on).apply()
}
