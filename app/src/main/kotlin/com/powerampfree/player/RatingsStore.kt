package com.powerampfree.player

import android.content.Context

/**
 * Track ratings (like/dislike) — original store, our own prefs namespace.
 * Values: 1 = like, -1 = dislike, 0/absence = unrated.
 */
object RatingsStore {
    private const val PREFS = "track_ratings"

    fun rating(context: Context, uri: String): Int =
        if (uri.isEmpty()) 0
        else context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(uri, 0)

    /** Toggles [value]: clicking the same state again clears it. Returns the new rating. */
    fun toggle(context: Context, uri: String, value: Int): Int {
        if (uri.isEmpty()) return 0
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val next = if (prefs.getInt(uri, 0) == value) 0 else value
        prefs.edit().putInt(uri, next).apply()
        return next
    }
}
