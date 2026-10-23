package com.powerampfree.audio.playlist

/**
 * Pure playlist-name handling (sprint 10, block 4/6 support logic), free of
 * Android types: names become playlist file names under the app's files
 * directory, so they must survive a strict filename charset and never collide
 * with entries the user already has.
 */
object PlaylistNames {

    /** Default name when the user submits an empty/invalid title. */
    const val DEFAULT = "Nueva playlist"

    /**
     * Makes [raw] a safe, non-empty playlist name: trimmed, collapsed
     * whitespace, only letters/digits/space/dash/dot/underscore kept, and the
     * default applied when nothing is left.
     */
    fun sanitize(raw: String): String {
        val cleaned = raw
            .map { if (it.isLetterOrDigit() || it == ' ' || it in " .-_") it else '_' }
            .joinToString("")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trim('_')
            .trim()
        return cleaned.ifBlank { DEFAULT }
    }

    /**
     * Returns [name] unchanged when it is free among [existing]; otherwise a
     * suffixed variant ("name (2)", "name (3)", …) that is still safe.
     */
    fun unique(name: String, existing: Set<String>): String {
        if (name !in existing) return name
        var n = 2
        while (true) {
            val candidate = "$name ($n)"
            if (candidate !in existing) return candidate
            n++
        }
    }
}
