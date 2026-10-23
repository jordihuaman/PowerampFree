package com.powerampfree.player.service

import androidx.media3.common.C
import com.powerampfree.player.PlaybackService
import com.powerampfree.player.QueueBus

/** Starts playback of [queueIndex] from orchestrator state (dual/queued restore). */
internal fun PlaybackService.playCurrentItem(positionMs: Long) {
    if (queueIndex !in queueTracks.indices) return
    cancelPendingFadeFinish()
    fadeActive = false
    val oldOverlay = overlay
    overlay = null
    oldOverlay?.release()
    advanceEngine.currentTrackOpened(sidOf(queueIndex))

    val track = queueTracks[queueIndex]
    // Per-track progress (block 11): an open without an explicit position
    // resumes the remembered playhead for qualifying long tracks only.
    val pos = if (positionMs > 0L) positionMs else restorePositionFor(track)
    val item = track.toMediaItem()
    // Focus is managed by the service (block 8 toggles), not by ExoPlayer.
    val beam = buildBeam(withFocus = false)
    beam.player?.setMediaItem(item, pos)
    beam.player?.prepare()
    beam.player?.play()
    // Give the session the live beam: the audible engine changes, so the
    // session player must follow (single decoder identity for the UI).
    applyDualBeamFlags(beam.player)
    mediaSession?.setPlayer(beam.player!!)
    plannedRealDurationMs = -1L
    primary.release()
    primary = beam
    planPrimaryCrossfade()
    saveQueueSnapshot()
    armOverlay()
}

/** Arms the paused overlay decoder with the NEXT track + forced fade-in plan. */
internal fun PlaybackService.armOverlay() {
    if (!dualMode || fadeActive) return
    val nextIdx = queueIndex + 1
    if (nextIdx !in queueTracks.indices) return
    val oldOverlay = overlay
    overlay = null
    oldOverlay?.release()
    val beam = buildBeam(withFocus = false)
    beam.player?.setMediaItem(queueTracks[nextIdx].toMediaItem())
    beam.dsp?.setCrossfadePlan(
        durationsMs = queueTracks.drop(nextIdx).map { it.durationMs },
        fadeMs = crossfadeMs,
        itemIndex = 0,
        positionMs = 0,
        forceFadeIn = true,
    )
    beam.player?.prepare()
    overlay = beam
}

/** Starts the second decoder so both sound together (real overlap begins). */
internal fun PlaybackService.startOverlap() {
    if (fadeActive) return
    val ov = overlay?.player ?: return
    fadeActive = true
    advanceEngine.pendingTrackOpened(sidOf(queueIndex + 1))
    ov.play()
    mainHandler.postDelayed(finishFadeRunnable, crossfadeMs)
}

/** Promotes the overlay once the fade completed (pending → current promotion). */
internal fun PlaybackService.finishFade() {
    if (!fadeActive) return
    val ov = overlay ?: run {
        fadeActive = false
        cancelPendingFadeFinish()
        return
    }
    commitTrackState() // outgoing track finished (still the session player)
    advanceEngine.dspTrackStarted(sidOf(queueIndex + 1)) // engine promotion
    fadeActive = false
    cancelPendingFadeFinish()
    queueIndex += 1
    plannedRealDurationMs = -1L
    advanceEngine.currentTrackOpened(sidOf(queueIndex))
    QueueBus.publish(queueTracks, queueIndex)
    val old = primary
    primary = ov
    overlay = null
    mediaSession?.setPlayer(ov.player!!)
    planPrimaryCrossfade()
    old.release()
    saveQueueSnapshot()
    armOverlay()
}

internal fun PlaybackService.cancelPendingFadeFinish() {
    mainHandler.removeCallbacks(finishFadeRunnable)
}

/** Instant advance (skip / next-media-button / end-without-overlay). */
internal fun PlaybackService.advanceOrchestrator() {
    if (!dualMode) return
    android.util.Log.i(
        TAG, "advanceOrchestrator qi=$queueIndex n=${queueTracks.size} repAll=$isRepeatAll",
    )
    commitTrackState() // outgoing track: ended (pos≈dur) or user skip
    var next = queueIndex + 1
    if ((next !in queueTracks.indices) && isRepeatAll && queueTracks.isNotEmpty()) {
        next = 0 // repeat all: continuous replay of the whole queue
    }
    if (next !in queueTracks.indices) {
        advanceEngine.playbackTerminated()
        cancelPendingFadeFinish()
        return
    }
    cancelPendingFadeFinish()
    fadeActive = false
    val index = next
    queueIndex = index
    plannedRealDurationMs = -1L
    QueueBus.publish(queueTracks, index)
    val beam = buildBeam(withFocus = false).also {
        it.player?.setMediaItem(queueTracks[index].toMediaItem())
        it.dsp?.setCrossfadePlan(
            durationsMs = queueTracks.drop(index).map { tr -> tr.durationMs },
            fadeMs = crossfadeMs,
            itemIndex = 0,
            positionMs = 0,
            forceFadeIn = true,
        )
        it.player?.prepare()
    }
    applyDualBeamFlags(beam.player)
    advanceEngine.currentTrackOpened(sidOf(index))
    mediaSession?.setPlayer(beam.player!!)
    primary.release()
    primary = beam
    beam.player?.play()
    planPrimaryCrossfade()
    saveQueueSnapshot()
    armOverlay()
}

internal fun PlaybackService.planPrimaryCrossfade() {
    val dsp = primary.dsp ?: return
    if (!dualMode) { resetCrossfadePlan(dsp); return }
    // Repeat-one loops the single item natively: no seam, no fades.
    if (isRepeatOne) { resetCrossfadePlan(dsp); return }
    if (queueIndex !in queueTracks.indices) return
    // Primary owns exactly one item; its plan uses the remaining queue's
    // durations so the fade-OUT is aligned to the item's own end (seam).
    val durations = queueTracks.drop(queueIndex).map { it.durationMs }
    dsp.setCrossfadePlan(
        durationsMs = durations,
        fadeMs = crossfadeMs,
        itemIndex = 0,
        positionMs = primary.player?.currentPosition ?: 0,
    )
}

/**
 * MediaStore durations can disagree with the decoder's real duration
 * (observed on long OPUS recordings). The fade-OUT must align to the
 * real end, so re-plan whenever the player reports a new duration.
 */
internal fun PlaybackService.replanWithRealDuration(player: androidx.media3.common.Player) {
    val dur = player.duration
    if (dur <= 0 || dur == androidx.media3.common.C.TIME_UNSET) return
    if (dur == plannedRealDurationMs) return
    plannedRealDurationMs = dur
    primary.dsp?.setCrossfadePlan(
        durationsMs = listOf(dur),
        fadeMs = crossfadeMs,
        itemIndex = 0,
        positionMs = player.currentPosition,
    )
}

internal fun PlaybackService.resetCrossfadePlan(dsp: com.powerampfree.player.DspAudioProcessor) {
    dsp.setCrossfadePlan(emptyList(), 0L, 0, 0)
}

internal fun PlaybackService.replanAfterCrossfadeChange() {
    planPrimaryCrossfade()
    if (!dualMode) { overlay?.release(); overlay = null; return }
    armOverlay()
}

/** Watches the session player to launch the overlap exactly at seam−fadeMs. */
internal val PlaybackService.fadeWatcher: Runnable
    get() = object : Runnable {
        override fun run() {
            val p = primary.player
            if (!fadeActive && p != null && dualMode) {
                replanWithRealDuration(p)
            }
            if (!fadeActive && p != null && p.isPlaying && dualMode && !isRepeatOne &&
                queueIndex in queueTracks.indices && queueIndex + 1 < queueTracks.size
            ) {
                val dur = p.duration
                if (dur > 0 && dur < C.TIME_UNSET &&
                    dur >= 2 * crossfadeMs &&
                    p.currentPosition >= dur - crossfadeMs
                ) {
                    startOverlap()
                }
            }
            mainHandler.postDelayed(this, WATCHER_PERIOD_MS)
        }
    }

internal fun PlaybackService.skipNext() {
    android.util.Log.i(TAG, "skipNext dual=$dualMode")
    if (dualMode) advanceOrchestrator() else {
        commitTrackState() // single mode: outgoing track (player-based)
        primary.player?.seekToNextMediaItem()
    }
}

internal fun PlaybackService.skipPrevious() {
    commitTrackState() // outgoing position, before any index change
    android.util.Log.i(TAG, "skipPrevious qi=$queueIndex n=${queueTracks.size} dual=$dualMode")
    if (!dualMode && queueIndex - 1 !in queueTracks.indices) {
        primary.player?.seekToPreviousMediaItem()
        return
    }
    if (dualMode && queueIndex == 0) {
        // ⏮ at the first track: restart it (never queueIndex=-1).
        QueueBus.publish(queueTracks, 0)
        dualModeOrchestratorPlayPrevious()
        return
    }
    queueIndex -= 1
    if (dualMode && queueIndex in queueTracks.indices) {
        QueueBus.publish(queueTracks, queueIndex)
    }
    dualModeOrchestratorPlayPrevious()
}

/** Queue screen: jump directly to [idx] WITHOUT re-shuffling/re-queueing. */
internal fun PlaybackService.queueJump(idx: Int) {
    cancelPendingFadeFinish()
    fadeActive = false
    commitTrackState() // outgoing track, before index change
    queueIndex = idx
    plannedRealDurationMs = -1L
    playCurrentItem(0L)
    QueueBus.publish(queueTracks, idx)
}

internal fun PlaybackService.dualModeOrchestratorPlayPrevious() {
    if (queueIndex !in queueTracks.indices) return
    playCurrentItem(0L)
}
