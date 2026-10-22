package com.powerampfree.player

import android.content.Context

/**
 * Persistent search history (own prefs namespace, newest-first, deduplicated).
 */
object SearchHistory {
    private const val PREFS = "search_history"
    private const val KEY = "queries"
    private const val MAX = 10

    fun load(context: Context): List<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null)
            ?.split("\u0001")
            ?.filter { it.isNotBlank() }
            ?: emptyList()

    fun add(context: Context, query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        val next = buildList {
            add(q)
            load(context).forEach { if (it != q) add(it) }
        }.take(MAX)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, next.joinToString("\u0001")).apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY).apply()
    }
}
