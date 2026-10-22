package com.powerampfree.audio.engine

import kotlin.test.*

class AutoAdvanceEngineTest {

    private class RecordingListener : AutoAdvanceEngine.Listener {
        val events = mutableListOf<String>()
        override fun onAdvanceNeeded(currentSession: Long) { events += "advance($currentSession)" }
        override fun onPendingPromoted(newCurrentSession: Long) { events += "promote($newCurrentSession)" }
        override fun onPipelineIdle() { events += "idle()" }
    }

    @Test
    fun dspTrackEnded_for_current_posts_advance_exactly_once() {
        val l = RecordingListener()
        val engine = AutoAdvanceEngine(l)
        engine.currentTrackOpened(1001)
        engine.dspTrackEnded(1001)
        assertEquals(listOf("advance(1001)"), l.events)
    }

    @Test
    fun dspTrackEnded_for_wrong_session_is_ignored() {
        // Observed reference guard: id equality decides whether the event is processed.
        val l = RecordingListener()
        val engine = AutoAdvanceEngine(l)
        engine.currentTrackOpened(1001)
        engine.dspTrackEnded(999)   // stale/unknown session
        assertEquals(emptyList(), l.events)
        engine.dspTrackEnded(1001)  // proper end still fires afterward
        assertEquals(listOf("advance(1001)"), l.events)
    }

    @Test
    fun overlap_pending_promoted_on_dsp_start_of_pending_id() {
        val l = RecordingListener()
        val engine = AutoAdvanceEngine(l)
        engine.currentTrackOpened(10)
        engine.pendingTrackOpened(20)
        assertTrue(engine.isOverlapActive())
        engine.dspTrackStarted(10)               // current keeps running during overlap
        assertEquals(emptyList(), l.events.filter { it.startsWith("promote") })
        engine.dspTrackStarted(20)               // pending becomes current
        assertEquals(listOf("promote(20)"), l.events.filter { it.startsWith("promote") })
        assertEquals(20, engine.currentSession)
        assertEquals(AutoAdvanceEngine.NO_SESSION, engine.pendingSession)
    }

    @Test
    fun unknown_start_id_cannot_promote() {
        val l = RecordingListener()
        val engine = AutoAdvanceEngine(l)
        engine.currentTrackOpened(10)
        engine.pendingTrackOpened(20)
        engine.dspTrackStarted(55)
        assertEquals(20, engine.pendingSession)   // unknown session can't promote or clear pending
        assertEquals(10, engine.currentSession)   // ...nor steal the current role
        assertEquals(emptyList(), l.events.filter { it.startsWith("promote") || it.startsWith("advance") })
    }

    @Test
    fun termination_flows_to_idle_and_clears_sessions() {
        val l = RecordingListener()
        val engine = AutoAdvanceEngine(l)
        engine.currentTrackOpened(1)
        engine.pendingTrackOpened(2)
        engine.currentTrackEnded_soft()
        engine.playbackTerminated()
        assertEquals(1, l.events.count { it == "idle()" })
        assertEquals(AutoAdvanceEngine.NO_SESSION, engine.currentSession)
        assertEquals(AutoAdvanceEngine.NO_SESSION, engine.pendingSession)
    }

    private fun AutoAdvanceEngine.currentTrackEnded_soft() {
        // Actual soft advance ordering that the service would do after a successful advance:
        dspTrackEnded(currentSession)
    }

    @Test
    fun ended_only_when_not_yet_armed_doesnt_double_advance() {
        val l = RecordingListener()
        val engine = AutoAdvanceEngine(l)
        engine.currentTrackOpened(7)
        engine.dspTrackEnded(7)
        engine.dspTrackEnded(7) // duplicate event (observed duplicates possible in native callbacks)
        assertEquals(1, l.events.count { it == "advance(7)" })
    }
}
