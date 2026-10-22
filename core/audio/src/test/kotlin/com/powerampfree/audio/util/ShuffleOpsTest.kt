package com.powerampfree.audio.util

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShuffleOpsTest {

    @Test
    fun `head stays fixed and tail is a permutation of the original tail`() {
        val src = (0 until 8).toList()
        val out = ShuffleOps.shuffleTail(src, 2, Random(1))
        assertEquals(src.take(3), out.take(3))
        assertEquals(src.drop(3).sorted(), out.drop(3).sorted())
    }

    @Test
    fun `fromIndex covers whole list`() {
        val out = ShuffleOps.shuffleTail(listOf("a", "b", "c"), -1, Random(2))
        assertEquals(listOf("a", "b", "c").sorted(), out.sorted())
    }

    @Test
    fun single_element_tail_is_unchanged() {
        val out = ShuffleOps.shuffleTail(listOf(1, 2, 3), 2, Random(3))
        assertEquals(listOf(1, 2, 3), out)
    }

    @Test
    fun empty_and_short_lists_are_safe() {
        assertEquals(emptyList<Int>(), ShuffleOps.shuffleTail(emptyList<Int>(), 0, Random(4)))
        assertEquals(listOf(9), ShuffleOps.shuffleTail(listOf(9), 0, Random(5)))
    }

    @Test
    fun deterministic_with_same_seed() {
        val a = ShuffleOps.shuffleTail((0 until 20).toList(), 1, Random(42))
        val b = ShuffleOps.shuffleTail((0 until 20).toList(), 1, Random(42))
        assertEquals(a, b)
    }

    @Test
    fun almost_always_changes_something_for_long_tails() {
        val src = (0 until 30).toList()
        var changed = 0
        for (s in 0 until 5) {
            val out = ShuffleOps.shuffleTail(src, 0, Random(s))
            if (out != src) changed++
        }
        assertTrue(changed >= 4)
    }
}
