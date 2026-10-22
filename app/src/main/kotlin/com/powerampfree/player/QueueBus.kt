package com.powerampfree.player

import androidx.compose.runtime.mutableStateOf

/**
 * Same-process publication of the orchestrator queue for the Queue screen.
 *
 * In dual-crossfade mode the session player carries exactly one item, so the
 * controller timeline cannot show the full queue (observed limitation). The
 * service publishes the real queue here after every structural change
 * (setQueue / advance / fade / shuffle / jump / restore), and the Compose UI
 * subscribes to [version] for invalidation.
 */
object QueueBus {
    val version = mutableStateOf(0)

    @Volatile
    var tracks: List<MediaTrack> = emptyList()

    @Volatile
    var index: Int = -1

    fun publish(t: List<MediaTrack>, i: Int) {
        tracks = t
        index = i
        version.value = version.value + 1
    }
}
