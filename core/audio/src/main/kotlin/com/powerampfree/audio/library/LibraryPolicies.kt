package com.powerampfree.audio.library

/**
 * Pure library policies (block 11 of the feature catalog), free of Android
 * types so the thresholds are unit-testable. See docs/POWERAMP_FEATURES.md §11.
 */

/**
 * Per-track progress ("Store/Restore Per Track Progress"): only tracks at
 * least [thresholdMin] minutes long keep a remembered position (podcasts,
 * DJ sets, audiobooks). Short tracks always restart at 0, mirroring the
 * original "Track Duration For Per Track Progress — At least: 45 min.".
 */
object TrackProgressPolicy {

    /** Minimum track duration (minutes) that keeps per-track progress. */
    const val DEFAULT_THRESHOLD_MIN = 45

    /** Progress remembered for this track on the next opening? */
    fun keepProgress(trackDurationMs: Long, thresholdMin: Int, toggledOn: Boolean): Boolean =
        toggledOn && trackDurationMs >= thresholdMin * 60_000L
}

/**
 * "Count As Played — after playing at least: 50%": a listen counts once the
 * playhead passed [pct]% of the track. A natural end always counts (the
 * remaining part does not exist any more), even with a 100% threshold.
 */
object CountAsPlayedPolicy {

    const val DEFAULT_PCT = 50

    fun counts(listenedMs: Long, trackDurationMs: Long, pct: Int): Boolean {
        if (trackDurationMs <= 0L) return false
        if (listenedMs >= trackDurationMs) return true
        return listenedMs * 100 >= trackDurationMs * pct.coerceIn(1, 100)
    }
}

/**
 * Library filters ("Ignore Short Tracks (Notifications, etc.)" < 6 s).
 * Video is excluded by source here: the library queries
 * `MediaStore.Audio.Media`, an audio-only collection — that original toggle
 * therefore maps to a no-op in this codebase and is not modelled.
 */
object LibraryFilterPolicy {

    /** Original scanner: ignores ≤ 6-second sounds (notifications, etc.). */
    const val SHORT_TRACK_MAX_S = 6

    /** True when the track belongs in the library under the user's filters. */
    fun admits(trackDurationMs: Long, ignoreShortOn: Boolean): Boolean {
        if (!ignoreShortOn) return true
        return trackDurationMs > SHORT_TRACK_MAX_S * 1000L
    }
}
