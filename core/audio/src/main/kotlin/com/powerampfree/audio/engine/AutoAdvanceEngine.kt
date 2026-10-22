package com.powerampfree.audio.engine

/**
 * Playback queue auto-advance state machine.
 *
 * Design evidence (behavior observed in the analyzed reference pipeline):
 *  - the engine distinguishes a *current* track session from a *pending* one;
 *  - when a DSP-layer "track started" arrives for the pending session, the
 *    pending track is promoted to current (transition/overlap active);
 *  - a DSP-layer "track ended" for the current session posts an auto-advance;
 *  - out-of-order session ids must be ignored (id equality guard).
 *
 * Here: pure, deterministic, testable core that a service binds to real
 * decode/DSP callbacks. No scheduling here, no I/O: Android service only
 * notifies it and enqueues actions (next track / crossfade promotion).
 */
class AutoAdvanceEngine(private val listener: Listener) {

    interface Listener {
        fun onAdvanceNeeded(currentSession: Long)
        fun onPendingPromoted(newCurrentSession: Long)
        fun onPipelineIdle()
    }

    /** Crossfade overlap states observed in the reference performance state code. */
    private enum class EngineState { IDLE, PLAYING, OVERLAP }

    private var state: EngineState = EngineState.IDLE
    var currentSession: Long = NO_SESSION
        private set
    var pendingSession: Long = NO_SESSION
        private set

    /** Marks queue position for advance *only after* DSP really ended the track. */
    private var advanceArmed = false

    fun currentTrackOpened(sessionId: Long) {
        currentSession = sessionId
        pendingSession = NO_SESSION
        state = EngineState.PLAYING
        advanceArmed = false
    }

    /** User interacts: a next-track is prepared while current is still playing (overlap phase). */
    fun pendingTrackOpened(sessionId: Long) {
        pendingSession = sessionId
        state = EngineState.OVERLAP
    }

    /** DSP-layer: samples of [sessionId] hit the filter chain. */
    fun dspTrackStarted(sessionId: Long, sampleRateHz: Int = 0) {
        when {
            sessionId == currentSession && state == EngineState.OVERLAP -> {
                // Same id can't promote anything: it's already current.
            }
            sessionId == pendingSession && state == EngineState.OVERLAP -> {
                // Reference behavior: promote pending → current, post overlap event.
                val advanced = pendingSession
                currentSession = advanced
                pendingSession = NO_SESSION
                listener.onPendingPromoted(advanced)
            }
            sessionId == currentSession && state == EngineState.PLAYING -> Unit // (re)start of current
            else -> {
                // Unknown session: ignore (guard against out-of-order callbacks).
            }
        }
    }

    /** DSP-layer: the EQ/output chain saw the last samples of [sessionId]. */
    fun dspTrackEnded(sessionId: Long) {
        if (sessionId != currentSession) return // id equality guard (observed behavior)
        if (advanceArmed) return                // duplicate callback tolerance (observed duplicates)
        advanceArmed = true
        listener.onAdvanceNeeded(currentSession)
    }

    /**
     * Hard "playback truly over" event (decoder done, no queue next).
     * Under the reference engine: end-of-pipeline event, service goes to idle/notification.
     */
    fun playbackTerminated() {
        if (state == EngineState.OVERLAP && pendingSession != NO_SESSION) {
            currentSession = NO_SESSION // current outlived? overlap must end cleanly
        }
        state = EngineState.IDLE
        currentSession = NO_SESSION
        pendingSession = NO_SESSION
        listener.onPipelineIdle()
    }

    fun isOverlapActive(): Boolean = state == EngineState.OVERLAP

    companion object {
        const val NO_SESSION = -1L
    }
}
