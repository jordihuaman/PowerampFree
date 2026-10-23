package com.powerampfree.audio.input

/**
 * Actions a headset/BT remote gesture can trigger. [SINGLE] is a placeholder
 * the caller maps to its configured single-press action (play/pause, next or
 * previous); the multi-press and long-press gestures have fixed meanings.
 */
enum class HeadsetAction {
    NONE,
    SINGLE,
    DOUBLE_NEXT,
    TRIPLE_PREV,
    LONG_NEXT,
    PLAY,
    PAUSE,
    PLAY_PAUSE,
    NEXT,
    PREV,
}

/**
 * Pure gesture mapper for a single headset hook button (wired or BT remote).
 *
 * The hook button reports only down/up of one physical key; a "click" is a
 * down+up pair, and a burst of clicks within [doubleWindowMs] of each other
 * forms a multi-press. Holding past [longPressMs] is a long press. The class
 * is a clock-free state machine: the caller (Android media-button handler)
 * feeds events and arms timers, then feeds timer expiries back in — which makes
 * every timing branch unit-testable with synthetic timestamps.
 *
 * Contract:
 *  - [onDown] returns no action (the click is only meaningful once released or
 *    resolved); the caller arms the long-press timer on the first down.
 *  - [onUp] returns no action; the caller cancels the long-press timer and arms
 *    the double-window timer.
 *  - [onLongElapsed] fires while held → consumes the pending click (so the
 *    later double-window resolves to nothing) and yields [LONG_NEXT].
 *  - [onResolveWindow] fires after the double-window with no further click and
 *    resolves the click count: 1 → SINGLE, 2 → DOUBLE_NEXT, 3 → TRIPLE_PREV.
 */
class HeadsetGestures(
    val doubleWindowMs: Long = 350,
    val longPressMs: Long = 700,
) {
    private var clicks = 0

    /** First down of a new click ([repeatCount] == 0 for the initial press). */
    fun onDown(repeatCount: Int): HeadsetAction? {
        if (repeatCount != 0) return null
        clicks++
        return null
    }

    /** Release of the hook; caller re-arms the resolve window from here. */
    fun onUp(): HeadsetAction? = null

    /** Long-press timer fired while still held: consume the click, emit next. */
    fun onLongElapsed(): HeadsetAction? {
        if (clicks == 0) return null
        clicks = 0
        return HeadsetAction.LONG_NEXT
    }

    /** Double-window elapsed with no new click: resolve the click count. */
    fun onResolveWindow(): HeadsetAction? {
        val c = clicks
        clicks = 0
        return when (c) {
            1 -> HeadsetAction.SINGLE
            2 -> HeadsetAction.DOUBLE_NEXT
            3 -> HeadsetAction.TRIPLE_PREV
            else -> null
        }
    }

    fun reset() {
        clicks = 0
    }
}
