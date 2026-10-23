package com.powerampfree.audio.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Headset hook gesture resolution (sprint 7). */
class HeadsetGesturesTest {

    @Test
    fun `single click resolves to SINGLE`() {
        val g = HeadsetGestures()
        assertNull(g.onDown(repeatCount = 0))
        assertNull(g.onUp())
        assertEquals(HeadsetAction.SINGLE, g.onResolveWindow())
    }

    @Test
    fun `double click resolves to DOUBLE_NEXT`() {
        val g = HeadsetGestures()
        g.onDown(0); g.onUp()
        g.onDown(0); g.onUp()
        assertEquals(HeadsetAction.DOUBLE_NEXT, g.onResolveWindow())
    }

    @Test
    fun `triple click resolves to TRIPLE_PREV`() {
        val g = HeadsetGestures()
        repeat(3) { g.onDown(0); g.onUp() }
        assertEquals(HeadsetAction.TRIPLE_PREV, g.onResolveWindow())
    }

    @Test
    fun `four clicks are not mapped`() {
        val g = HeadsetGestures()
        repeat(4) { g.onDown(0); g.onUp() }
        assertNull(g.onResolveWindow())
    }

    @Test
    fun `long press while held yields LONG_NEXT and consumes the click`() {
        val g = HeadsetGestures()
        assertNull(g.onDown(0))
        assertEquals(HeadsetAction.LONG_NEXT, g.onLongElapsed())
        // The later double-window must not also fire a single action.
        assertNull(g.onUp())
        assertNull(g.onResolveWindow())
    }

    @Test
    fun `long press with no click does nothing`() {
        val g = HeadsetGestures()
        assertNull(g.onLongElapsed())
    }

    @Test
    fun `auto-repeat downs do not add clicks`() {
        val g = HeadsetGestures()
        g.onDown(0)
        repeat(5) { g.onDown(it + 1) }
        g.onUp()
        assertEquals(HeadsetAction.SINGLE, g.onResolveWindow())
    }

    @Test
    fun `resolve with no clicks does nothing`() {
        val g = HeadsetGestures()
        assertNull(g.onResolveWindow())
    }

    @Test
    fun `state resets after resolve so the next gesture starts fresh`() {
        val g = HeadsetGestures()
        g.onDown(0); g.onUp()
        assertEquals(HeadsetAction.SINGLE, g.onResolveWindow())
        g.onDown(0); g.onUp()
        assertEquals(HeadsetAction.SINGLE, g.onResolveWindow())
    }

    @Test
    fun `reset clears a pending click`() {
        val g = HeadsetGestures()
        g.onDown(0)
        g.reset()
        assertNull(g.onResolveWindow())
    }
}
