package com.powerampfree.audio.library

import com.powerampfree.audio.library.CountAsPlayedPolicy.counts
import com.powerampfree.audio.library.LibraryFilterPolicy.admits
import com.powerampfree.audio.library.TrackProgressPolicy.keepProgress
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryPoliciesTest {

    // ------------------------------------------------- TrackProgressPolicy

    @Test
    fun `progress kept for tracks at or above the threshold`() {
        val d45 = 45L * 60_000L
        assertTrue(keepProgress(d45, 45, toggledOn = true))
        assertTrue(keepProgress(d45 + 1, 45, toggledOn = true))
    }

    @Test
    fun `progress dropped below threshold`() {
        assertFalse(keepProgress(45L * 60_000L - 1, 45, toggledOn = true))
        assertFalse(keepProgress(0L, 45, toggledOn = true))
    }

    @Test
    fun `toggle off disables progress entirely`() {
        assertFalse(keepProgress(3L * 3_600_000L, 45, toggledOn = false))
    }

    @Test
    fun `threshold is configurable in minutes`() {
        assertTrue(keepProgress(2L * 60_000L, 1, toggledOn = true))
    }

    // ------------------------------------------------ CountAsPlayedPolicy

    @Test
    fun `half of the track meets the default 50 percent`() {
        assertTrue(counts(60_000, 120_000, 50))
    }

    @Test
    fun `just under the threshold does not count`() {
        assertFalse(counts(59_999, 120_000, 50))
    }

    @Test
    fun `natural end always counts`() {
        assertTrue(counts(120_000, 120_000, 100))
        assertTrue(counts(1_000, 1_000, 100))
    }

    @Test
    fun `unknown duration never counts`() {
        assertFalse(counts(60_000, 0, 50))
    }

    @Test
    fun `pct is clamped between 1 and 100`() {
        assertTrue(counts(1_200, 120_000, 0))   // 1%
        assertFalse(counts(1_199, 120_000, 0))  // 1%
    }

    // ------------------------------------------------ LibraryFilterPolicy

    @Test
    fun `short tracks ignored when filter on`() {
        assertFalse(admits(600, true))
        assertFalse(admits(6000, true))     // exactly 6 s -> ignored
        assertTrue(admits(6001, true))
    }

    @Test
    fun `short tracks kept when filter off`() {
        assertTrue(admits(600, false))
    }

    @Test
    fun `normal tracks always admitted`() {
        assertTrue(admits(120_000, true))
        assertTrue(admits(120_000, false))
    }
}
