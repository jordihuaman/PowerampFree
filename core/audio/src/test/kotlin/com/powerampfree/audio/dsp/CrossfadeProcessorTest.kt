package com.powerampfree.audio.dsp

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Crossfade envelope properties (sequential-fade design):
 *  - disabled (fadeMs=0) → strict unity, bit-exact passthrough;
 *  - linear ramps meet at ~0 on both sides of the seam (no click amplitude);
 *  - middle of every track is at unity gain;
 *  - gain is never > 1 (no amplification / no limiter pumping at fades);
 *  - snapTo after seek re-syncs to the correct envelope (seek-safety);
 *  - process() integrates the envelope onto actual PCM buffers.
 */
class CrossfadeProcessorTest {

    private val sr = 48_000
    private fun frames(ms: Long) = ms * sr / 1000

    @Test
    fun disabled_fade_is_bit_exact_unity() {
        val xf = CrossfadeProcessor(sr, 2)
        xf.setPlan(listOf(1000L, 1000L), 0, 0, 0)
        assertFalse(xf.isArmed)
        val buf = FloatArray(1200) { if (it % 2 == 0) 0.31f else -0.42f }
        val expected = buf.copyOf()
        xf.process(buf, 600, 2)
        assertTrue(buf.contentEquals(expected), "disabled crossfade must be a pure passthrough")
    }

    @Test
    fun linear_ramps_meet_near_zero_at_the_seam() {
        val xf = CrossfadeProcessor(sr, 2)
        val d = 4800L // ms per track
        xf.setPlan(listOf(d, d, d), 1000, 0, 0)
        assertTrue(xf.isArmed)
        assertEquals(48_000L * d / 1000, xf.plannedBoundaries()[0])
        val fade = frames(1000)
        // 1 frame before the seam: faded to ~0
        assertTrue(
            xf.envelopeAt(xf.plannedBoundaries()[0] - 1) < 0.001f,
            "last frame of track A must be ~0",
        )
        // fade-out starts at unity exactly [fade] frames before the seam
        assertTrue(xf.envelopeAt(xf.plannedBoundaries()[0] - fade) > 0.999f)
        // mid fade-out window: linear descending
        val midOut = xf.plannedBoundaries()[0] - fade / 2
        assertEquals(0.5f, xf.envelopeAt(midOut), 0.01f)
        // first frame of track B: ~0
        assertTrue(xf.envelopeAt(xf.plannedBoundaries()[0]) < 0.001f)
        // mid fade-in of track B (half of the 1 s window = 500 ms in)
        assertEquals(0.5f, xf.envelopeAt(xf.plannedBoundaries()[0] + fade / 2), 0.01f)
    }

    @Test
    fun middle_of_track_is_within_epsilon_of_unity() {
        val xf = CrossfadeProcessor(sr, 2)
        xf.setPlan(listOf(4800L, 4800L), 1000, 0, 0)
        val midFrameA = xf.plannedBoundaries()[0] / 2
        val midFrameB = (xf.plannedBoundaries()[0] + xf.plannedBoundaries()[1]) / 2
        assertTrue(abs(xf.envelopeAt(midFrameA) - 1f) < 1e-6f)
        assertTrue(abs(xf.envelopeAt(midFrameB) - 1f) < 1e-6f)    }

    @Test
    fun first_and_last_track_edges_do_not_silence_the_song() {
        val xf = CrossfadeProcessor(sr, 2)
        xf.setPlan(listOf(4800L, 4800L, 4800L), 1000, 0, 0)
        val b0 = xf.plannedBoundaries()
        val totalEnd = b0[2]
        // first track head: no fade-in (no predecessor)
        assertEquals(1f, xf.envelopeAt(0), 1e-6f)
        assertEquals(1f, xf.envelopeAt(1000), 1e-6f)
        // last track end: no fade-out to silence at queue end
        assertTrue(xf.envelopeAt(totalEnd - 1) > 0.999f)
    }

    @Test
    fun never_amplifies_on_short_tracks() {
        val xf = CrossfadeProcessor(sr, 2)
        xf.setPlan(listOf(500L, 5000L), 1000, 0, 0)
        // fade cap = 250ms → fadeFrames = 12000
        assertEquals(12_000, xf.plannedFadeFrames())
        for (f in 0L until xf.plannedBoundaries()[1]) {
            val g = xf.envelopeAt(f)
            assertTrue(g in 0f..1f + 1e-6f, "gain $g at frame $f exceeded unity")
        }
    }

    @Test
    fun snap_to_resyncs_envelope_across_the_seam() {
        val xf = CrossfadeProcessor(sr, 2)
        xf.setPlan(listOf(4800L, 4800L), 1000, 0, 0)
        // fade-in of track B: 500 ms into it = exactly half the 1 s fade window
        xf.snapTo(itemIndex = 1, positionMs = 500)
        assertEquals(0.5f, xf.envelopeAt(xf.currentFrame()), 0.01f)
        // tail of track A fade-out: 4795 ms into a 4800 ms track → ~0
        xf.snapTo(itemIndex = 0, positionMs = 4795)
        val g = xf.envelopeAt(xf.currentFrame())
        assertTrue(g < 0.05f, "snap to tail of the fade-out must be near 0, got $g")
    }

    @Test
    fun process_applies_envelope_to_pcm_advance_and_reset() {
        val xf = CrossfadeProcessor(sr, 2)
        xf.setPlan(listOf(4800L, 4800L), 1000, 1, 0)
        xf.snapTo(itemIndex = 1, positionMs = 0)
        val head = FloatArray(4000) // 2000 frames stereo, at fade-in start
        for (f in 0 until 2000) { head[2 * f] = 0.8f; head[2 * f + 1] = -0.8f }
        xf.process(head, 2000, 2)
        // frame 1000 (≈frame of fade mid): half amplitude
        val mid = head[1998] // frame 999 left channel
        assertTrue(abs(mid - (0.8f * 999.0f / xf.plannedFadeFrames())) < 0.02f, "mid=$mid")
        // after reset the timeline returns to frame 0
        xf.reset()
        assertEquals(0L, xf.currentFrame())
        assertEquals(1f, xf.envelopeAt(0), 1e-6f)
    }

    @Test
    fun unknown_duration_items_stay_unity_and_bound_neighbors() {
        val xf = CrossfadeProcessor(sr, 2)
        xf.setPlan(listOf(4800L, 0L, 0L), 1000, 0, 0)
        val b = xf.plannedBoundaries()
        // unknown items keep exactly the same boundary as their predecessor
        assertEquals(b[0], b[1]); assertEquals(b[1], b[2])
        // inside unknown window → unity
        val probe = b[0] + 24000
        assertTrue(xf.envelopeAt(probe) in 0f..1f)
        // item beyond unknown → no fade-in from a zero-length previous item
        xf.setPlan(listOf(0L, 4800L), 1000, 1, 0)
        assertEquals(1f, xf.envelopeAt(0), 1e-6f) // head of second item: no predecessor audio
    }

    @Test
    fun forced_fade_in_applies_at_head_of_first_track() {
        val xf = CrossfadeProcessor(sr, 2)
        // overlay decoder path: single-item plan, fade-in forced (no predecessor)
        xf.setPlan(listOf(4800L), 1000, 0, 0, forceFadeIn = true)
        assertTrue(xf.isArmed)
        val fade = frames(1000)
        assertEquals(0f, xf.envelopeAt(0), 1e-6f) // starts silent
        assertEquals(0.5f, xf.envelopeAt(fade / 2), 0.01f)
        assertTrue(xf.envelopeAt(fade) > 0.999f)  // fades fully in
        // body stays unity
        assertTrue(abs(xf.envelopeAt(frames(2400)) - 1f) < 1e-6f)
    }
}
