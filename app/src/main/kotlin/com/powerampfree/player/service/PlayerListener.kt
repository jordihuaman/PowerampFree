package com.powerampfree.player.service

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.powerampfree.player.FormatBus
import com.powerampfree.player.PlaybackService

/**
 * Listener factory (one per player): only the session player drives
 * engine/queue continuity; the overlay decoder's events are handled by the
 * orchestrator ([startOverlap]/[finishFade]), not here.
 */
internal fun PlaybackService.makeListener(self: ExoPlayer): Player.Listener =
    object : Player.Listener {

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (self !== primary.player) return
            if (fadeActive && dualMode) {
                // Outgoing decoder crossed the seam: hold it silent; also kill
                // its own native auto-advance so duplicate audio never plays.
                self.pause()
                return
            }
            if (!dualMode) {
                val idx = self.currentMediaItemIndex
                advanceEngine.pendingTrackOpened(sidOf(idx))
                if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    advanceEngine.dspTrackStarted(sidOf(idx))
                }
            }
            saveQueueSnapshot()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (self !== primary.player) return
            when {
                playbackState == Player.STATE_ENDED && dualMode -> {
                    // Overlay was not armed in time (unknown durations etc.):
                    // switch without fade so playback never stalls.
                    advanceOrchestrator()
                }
                playbackState == Player.STATE_ENDED && !dualMode -> {
                    commitTrackState() // single mode natural end (pos == dur)
                    advanceEngine.dspTrackEnded(currentSid())
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (self !== primary.player) return
            if (isPlaying) {
                advanceEngine.dspTrackStarted(currentSid())
                startSaveTicker()
                // refresh fade plan against the live clock after stream resume
                planPrimaryCrossfade()
                // Hold audio focus while playing (block 8); clears a focus pause.
                ensureAudioFocus()
                focusPaused = false
            } else {
                stopSaveTicker()
                saveQueueSnapshot()
                saveListeningProgress() // block 11: pause keeps the playhead
                // Freeze the whole mix on user pause: the overlay halts too.
                if (fadeActive) overlay?.player?.pause()
                // Release focus on a user pause/end, but keep it across a
                // focus-induced pause so the later GAIN can resume.
                if (!focusPaused) abandonAudioFocus()
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            if (self !== primary.player) return
            val itemIndex = if (dualMode) 0 else self.currentMediaItemIndex
            primary.dsp?.snapCrossfade(itemIndex, newPosition.positionMs)
        }

        override fun onTimelineChanged(
            timeline: androidx.media3.common.Timeline,
            reason: Int,
        ) {
            if (self !== primary.player) return
            planPrimaryCrossfade()
        }

        /** Publishes the REAL decoder format (sampleRate/bitrate/codec) so the
         *  UI can show the technical chip without metadata guessing. */
        override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
            if (self !== primary.player) return
            var sRate = 0
            var bps = 0
            var mime = ""
            outer@ for (group in tracks.groups) {
                if (group.type != C.TRACK_TYPE_AUDIO) continue
                for (i in 0 until group.length) {
                    val f = group.getTrackFormat(i)
                    if (f.sampleRate > 0) {
                        sRate = f.sampleRate
                        bps = if (f.bitrate > 0) f.bitrate else 0
                        mime = f.sampleMimeType ?: ""
                        break@outer
                    }
                }
            }
            FormatBus.current = FormatBus.formatString(sRate, bps, mime)
        }
    }
