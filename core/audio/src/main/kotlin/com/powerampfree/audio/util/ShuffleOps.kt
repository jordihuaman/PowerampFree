package com.powerampfree.audio.util

import kotlin.random.Random

/**
 * Pure queue-shuffle utilities (no Android deps) — original clean-room logic.
 *
 * Semantics chosen for the player: when a track is selected with shuffle ON,
 * the chosen track stays first and the REST of the queue is reordered
 * (Fisher-Yates over the tail), so continuous playback continues from the
 * selection in randomized order.
 */
object ShuffleOps {

    /**
     * Keeps [fromIndex] elements in place and shuffles everything after.
     * Returns a new list; the original is not mutated.
     */
    fun <T> shuffleTail(list: List<T>, fromIndex: Int, random: Random): List<T> {
        val out = list.toMutableList()
        for (i in (fromIndex + 1) until out.size) {
            // swap position i with a random position in [fromIndex+1, i]
            val j = random.nextInt(fromIndex + 1, i + 1)
            val tmp = out[i]
            out[i] = out[j]
            out[j] = tmp
        }
        return out
    }
}
