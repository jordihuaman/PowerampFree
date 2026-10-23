package com.powerampfree.player.service

import com.powerampfree.audio.library.CountAsPlayedPolicy
import com.powerampfree.audio.library.TrackProgressPolicy
import com.powerampfree.player.LibrarySettings
import com.powerampfree.player.MediaTrack
import com.powerampfree.player.PlaybackService
import com.powerampfree.player.PlaybackService.Companion.EXTRA_DURATION_MS
import com.powerampfree.player.PlaybackService.Companion.SAVE_PERIOD_MS
import com.powerampfree.player.QueueBus
import com.powerampfree.player.QueueStore
import com.powerampfree.player.TrackStateStore

/** Active track descriptor off the live player (works in both modes). */
internal fun PlaybackService.activeTrackFromPlayer(): Pair<String, Long>? {
    val p = primary.player ?: return null
    val mi = p.currentMediaItem ?: return null
    val uri = mi.localConfiguration?.uri?.toString() ?: return null
    val dur = mi.mediaMetadata.extras?.getLong(EXTRA_DURATION_MS) ?: 0L
    return uri to dur
}

/**
 * Block 11 bookkeeping for the track leaving the playhead: stats commit
 * under the "count as played" threshold, progress snapshot for tracks the
 * per-track progress policy keeps. Call when the OUTGOING track is still
 * the player's current item (skip/ended/seam), never after switching.
 */
internal fun PlaybackService.commitTrackState() {
    val (uri, dur) = activeTrackFromPlayer() ?: return
    val pos = primary.player?.currentPosition ?: 0L
    val keep = TrackProgressPolicy.keepProgress(
        dur,
        LibrarySettings.progressMinMinutes(this),
        LibrarySettings.storeProgress(this),
    )
    val count = CountAsPlayedPolicy.counts(pos, dur, LibrarySettings.countPct(this))
    android.util.Log.i(
        TAG, "commitTrackState uri=$uri pos=$pos dur=$dur keep=$keep count=$count",
    )
    when {
        count -> TrackStateStore.commitCount(this, uri, if (keep) pos else 0L)
        keep -> TrackStateStore.saveProgress(this, uri, pos)
    }
}

/** Progress-only snapshot on pause (stats are counted on advances only). */
internal fun PlaybackService.saveListeningProgress() {
    if (!LibrarySettings.storeProgress(this)) return
    val (uri, dur) = activeTrackFromPlayer() ?: return
    if (!TrackProgressPolicy.keepProgress(
            dur, LibrarySettings.progressMinMinutes(this),
            toggledOn = true,
        )
    ) return
    TrackStateStore.saveProgress(this, uri, primary.player?.currentPosition ?: 0L)
}

/** Resolved opening position for [track] (0 = start from the beginning). */
internal fun PlaybackService.restorePositionFor(track: MediaTrack): Long {
    if (!TrackProgressPolicy.keepProgress(
            track.durationMs, LibrarySettings.progressMinMinutes(this),
            LibrarySettings.storeProgress(this),
        )
    ) return 0L
    return TrackStateStore.positionFor(this, track.uri)
}

internal fun PlaybackService.saveQueueSnapshot() {
    val p = primary.player ?: return
    val snapshot: List<MediaTrack>
    val index: Int
    if (dualMode) {
        if (queueIndex !in queueTracks.indices) return // corrupt index guard
        snapshot = queueTracks
        index = queueIndex
    } else {
        if (p.mediaItemCount == 0) return
        snapshot = (0 until p.mediaItemCount).mapNotNull { i ->
            val mi = p.getMediaItemAt(i)
            val uri = mi.localConfiguration?.uri?.toString() ?: return@mapNotNull null
            MediaTrack(
                uri = uri,
                title = mi.mediaMetadata.title?.toString() ?: "?",
                artist = mi.mediaMetadata.artist?.toString() ?: "",
                album = mi.mediaMetadata.albumTitle?.toString() ?: "",
                durationMs = mi.mediaMetadata.extras?.getLong(EXTRA_DURATION_MS) ?: 0L,
            )
        }
        index = p.currentMediaItemIndex
    }
    if (snapshot.isEmpty()) return
    QueueStore.save(this, snapshot, index, p.currentPosition)
}

internal fun PlaybackService.startSaveTicker() {
    mainHandler.postDelayed(saveTicker, SAVE_PERIOD_MS)
}

internal fun PlaybackService.stopSaveTicker() {
    mainHandler.removeCallbacks(saveTicker)
}

/** Queue restore: once, paused, at the saved position (no autoplay on boot). */
internal fun PlaybackService.restoreQueue() {
    val saved = QueueStore.load(this) ?: return
    if (saved.startIndex !in saved.items.indices) return // corrupt snapshot
    val beam = primary
    if (dualMode) {
        queueTracks = saved.items
        queueIndex = saved.startIndex
        QueueBus.publish(queueTracks, queueIndex)
        applyDualBeamFlags(beam.player)
        beam.player?.setMediaItem(saved.items[saved.startIndex].toMediaItem(), saved.positionMs)
        beam.player?.prepare()
        planPrimaryCrossfade()
        armOverlay()
    } else {
        beam.player?.setMediaItems(
            saved.items.map { it.toMediaItem() }, saved.startIndex, saved.positionMs,
        )
        beam.player?.prepare()
        QueueBus.publish(saved.items, saved.startIndex)
    }
}
