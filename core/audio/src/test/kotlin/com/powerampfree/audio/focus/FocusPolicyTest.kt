package com.powerampfree.audio.focus

import org.junit.Assert.assertEquals
import org.junit.Test

/** Audio-focus decision table (sprint 7). */
class FocusPolicyTest {

    private val all = FocusToggles(pause = true, resume = true, duck = true)
    private val none = FocusToggles(pause = false, resume = false, duck = false)

    @Test
    fun `permanent loss pauses when pause enabled`() {
        assertEquals(
            FocusAction.PAUSE,
            FocusPolicy.onEvent(FocusChange.LOSS, all, pausedByFocus = false, ducked = false),
        )
    }

    @Test
    fun `permanent loss does nothing when pause disabled`() {
        assertEquals(
            FocusAction.NONE,
            FocusPolicy.onEvent(FocusChange.LOSS, none, pausedByFocus = false, ducked = false),
        )
    }

    @Test
    fun `transient loss pauses when pause enabled`() {
        assertEquals(
            FocusAction.PAUSE,
            FocusPolicy.onEvent(
                FocusChange.LOSS_TRANSIENT, all, pausedByFocus = false, ducked = false,
            ),
        )
    }

    @Test
    fun `transient loss ducks when pause disabled but duck enabled`() {
        val t = FocusToggles(pause = false, resume = true, duck = true)
        assertEquals(
            FocusAction.DUCK,
            FocusPolicy.onEvent(FocusChange.LOSS_TRANSIENT, t, pausedByFocus = false, ducked = false),
        )
    }

    @Test
    fun `transient loss does nothing when both pause and duck disabled`() {
        val t = FocusToggles(pause = false, resume = true, duck = false)
        assertEquals(
            FocusAction.NONE,
            FocusPolicy.onEvent(FocusChange.LOSS_TRANSIENT, t, pausedByFocus = false, ducked = false),
        )
    }

    @Test
    fun `duckable loss ducks first when duck enabled`() {
        assertEquals(
            FocusAction.DUCK,
            FocusPolicy.onEvent(
                FocusChange.LOSS_TRANSIENT_CAN_DUCK, all, pausedByFocus = false, ducked = false,
            ),
        )
    }

    @Test
    fun `duckable loss falls back to pause when duck disabled`() {
        val t = FocusToggles(pause = true, resume = true, duck = false)
        assertEquals(
            FocusAction.PAUSE,
            FocusPolicy.onEvent(
                FocusChange.LOSS_TRANSIENT_CAN_DUCK, t, pausedByFocus = false, ducked = false,
            ),
        )
    }

    @Test
    fun `duckable loss does nothing when duck and pause disabled`() {
        val t = FocusToggles(pause = false, resume = true, duck = false)
        assertEquals(
            FocusAction.NONE,
            FocusPolicy.onEvent(
                FocusChange.LOSS_TRANSIENT_CAN_DUCK, t, pausedByFocus = false, ducked = false,
            ),
        )
    }

    @Test
    fun `gain after duck restores volume`() {
        assertEquals(
            FocusAction.RESTORE,
            FocusPolicy.onEvent(FocusChange.GAIN, all, pausedByFocus = false, ducked = true),
        )
    }

    @Test
    fun `gain after focus pause resumes when resume enabled`() {
        assertEquals(
            FocusAction.RESUME,
            FocusPolicy.onEvent(FocusChange.GAIN, all, pausedByFocus = true, ducked = false),
        )
    }

    @Test
    fun `gain after focus pause stays paused when resume disabled`() {
        val t = FocusToggles(pause = true, resume = false, duck = true)
        assertEquals(
            FocusAction.NONE,
            FocusPolicy.onEvent(FocusChange.GAIN, t, pausedByFocus = true, ducked = false),
        )
    }

    @Test
    fun `gain with no prior focus action does nothing`() {
        assertEquals(
            FocusAction.NONE,
            FocusPolicy.onEvent(FocusChange.GAIN, all, pausedByFocus = false, ducked = false),
        )
    }
}
