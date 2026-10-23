package com.powerampfree.player.service

import android.content.Intent
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.ListenableFuture
import com.powerampfree.audio.util.ShuffleOps
import com.powerampfree.player.AudioSettings
import com.powerampfree.player.MediaTrack
import com.powerampfree.player.PlaybackService
import com.powerampfree.player.PlaybackService.Companion.CMD_ENQUEUE_NEXT
import com.powerampfree.player.PlaybackService.Companion.CMD_SEEK_QUEUE
import com.powerampfree.player.PlaybackService.Companion.CMD_SET_CROSSFADE
import com.powerampfree.player.PlaybackService.Companion.CMD_SET_PRESET
import com.powerampfree.player.PlaybackService.Companion.CMD_SET_QUEUE
import com.powerampfree.player.PlaybackService.Companion.CMD_SET_REPLAY_GAIN
import com.powerampfree.player.PlaybackService.Companion.CMD_SET_REPEAT
import com.powerampfree.player.PlaybackService.Companion.CMD_SET_SHUFFLE
import com.powerampfree.player.PlaybackService.Companion.CMD_SKIP_NEXT
import com.powerampfree.player.PlaybackService.Companion.CMD_SKIP_PREV
import com.powerampfree.player.PlaybackService.Companion.EXTRA_CROSSFADE_MS
import com.powerampfree.player.PlaybackService.Companion.EXTRA_DURATION_MS
import com.powerampfree.player.PlaybackService.Companion.EXTRA_ENQUEUE_END
import com.powerampfree.player.PlaybackService.Companion.EXTRA_PRESET_JSON
import com.powerampfree.player.PlaybackService.Companion.EXTRA_REPEAT_MODE
import com.powerampfree.player.PlaybackService.Companion.EXTRA_SHUFFLE_ON
import com.powerampfree.player.PlaybackService.Companion.EXTRA_START_INDEX
import com.powerampfree.player.PlaybackService.Companion.EXTRA_TRACKS_JSON
import com.powerampfree.player.PlaybackSettings
import com.powerampfree.player.PresetStore
import com.powerampfree.player.QueueBus
import com.powerampfree.player.R

/** Notification/custom-layout buttons backed by our own session commands. */
internal fun PlaybackService.skipLayout(): List<androidx.media3.session.CommandButton> =
    listOf(
        androidx.media3.session.CommandButton.Builder()
            .setDisplayName("Previous")
            .setSessionCommand(SessionCommand(CMD_SKIP_PREV, Bundle.EMPTY))
            .setIconResId(R.drawable.ic_skip_prev)
            .build(),
        androidx.media3.session.CommandButton.Builder()
            .setDisplayName("Next")
            .setSessionCommand(SessionCommand(CMD_SKIP_NEXT, Bundle.EMPTY))
            .setIconResId(R.drawable.ic_skip_next)
            .build(),
    )

internal fun PlaybackService.buildSessionCallback(): MediaSession.Callback =
    object : MediaSession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            android.util.Log.i(TAG, "onConnect uid=${controller.uid} pkg=${controller.packageName}")
            val cmds = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand(CMD_SET_PRESET, Bundle.EMPTY))
                .add(SessionCommand(CMD_SET_CROSSFADE, Bundle.EMPTY))
                .add(SessionCommand(CMD_SKIP_NEXT, Bundle.EMPTY))
                .add(SessionCommand(CMD_SKIP_PREV, Bundle.EMPTY))
                .add(SessionCommand(CMD_SET_QUEUE, Bundle.EMPTY))
                .add(SessionCommand(CMD_SEEK_QUEUE, Bundle.EMPTY))
                .add(SessionCommand(CMD_ENQUEUE_NEXT, Bundle.EMPTY))
                .add(SessionCommand(CMD_SET_REPEAT, Bundle.EMPTY))
                .add(SessionCommand(CMD_SET_SHUFFLE, Bundle.EMPTY))
                .add(SessionCommand(CMD_SET_REPLAY_GAIN, Bundle.EMPTY))
                .build()
            // Custom layout: real skip buttons live in the notification/UI even
            // in dual mode, where the single-item session player cannot serve
            // player-level next/previous. Applies to the session itself (self).
            runCatching { session.setCustomLayout(skipLayout()) }
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(cmds)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> = when (customCommand.customAction) {
            CMD_SET_PRESET -> {
                val json = args.getString(EXTRA_PRESET_JSON)
                if (json == null) {
                    completed(SessionResult(SessionResult.RESULT_ERROR_BAD_VALUE))
                } else {
                    runCatching {
                        val p = com.powerampfree.audio.dsp.EqPreset.fromJson(json)
                        PresetStore.storeCurrent(applicationContext, p)
                        primary.dsp?.applyPreset(p)
                        overlay?.dsp?.applyPreset(p)
                        android.util.Log.i(
                            TAG,
                            "applyPreset bands=${p.bands.size} graphic=${p.graphicOn} " +
                                "gains=${p.graphicGainsDb.joinToString(prefix="(", postfix=")") { "%.1f".format(it) }} " +
                                "tone=${p.toneOn} b=${p.toneBassPct.toInt()}% t=${p.toneTreblePct.toInt()}% " +
                                "limit=${p.limitOn} resolved=${p.resolvedBands().size}",
                        )
                    }
                    completed(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            }
            CMD_SET_CROSSFADE -> {
                PlaybackSettings.setCrossfadeMs(applicationContext, args.getLong(EXTRA_CROSSFADE_MS, 0L))
                replanAfterCrossfadeChange()
                completed(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            CMD_SKIP_NEXT -> { skipNext(); completed(SessionResult(SessionResult.RESULT_SUCCESS)) }
            CMD_SKIP_PREV -> { skipPrevious(); completed(SessionResult(SessionResult.RESULT_SUCCESS)) }
            CMD_SET_REPEAT -> {
                val mode = args.getString(EXTRA_REPEAT_MODE) ?: ""
                if (mode !in listOf(
                        PlaybackSettings.REPEAT_OFF,
                        PlaybackSettings.REPEAT_ONE,
                        PlaybackSettings.REPEAT_ALL,
                    )
                ) {
                    completed(SessionResult(SessionResult.RESULT_ERROR_BAD_VALUE))
                } else {
                    android.util.Log.i(TAG, "setRepeat mode=$mode dual=$dualMode")
                    PlaybackSettings.setRepeatMode(applicationContext, mode)
                    if (dualMode) {
                        applyDualBeamFlags(primary.player)
                        // one: loop natively, so re-clear the seam plan; all/one
                        // switch changes whether the overlay must be armed.
                        planPrimaryCrossfade()
                    } else {
                        applyNativePlayerFlags(primary.player)
                    }
                    completed(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            }
            CMD_SET_SHUFFLE -> {
                val on = args.getBoolean(EXTRA_SHUFFLE_ON, false)
                android.util.Log.i(TAG, "setShuffle on=$on dual=$dualMode")
                PlaybackSettings.setShuffleEnabled(applicationContext, on)
                if (dualMode) {
                    if (on && queueIndex in queueTracks.indices) {
                        // Current track first, everything after it randomized.
                        val current = queueTracks[queueIndex]
                        val rest = queueTracks.filterIndexed { i, _ -> i != queueIndex }
                        queueTracks = listOf(current) + ShuffleOps.shuffleTail(
                            rest, -1, kotlin.random.Random(System.nanoTime()),
                        )
                        queueIndex = 0
                        QueueBus.publish(queueTracks, 0)
                        saveQueueSnapshot()
                        armOverlay()
                    }
                } else {
                    primary.player?.shuffleModeEnabled = on
                }
                completed(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            CMD_SET_QUEUE -> {
                val tracks = args.getString(EXTRA_TRACKS_JSON)?.let { json ->
                    runCatching { MediaTrack.listFromJson(json) }.getOrNull()
                }
                if (tracks.isNullOrEmpty()) {
                    completed(SessionResult(SessionResult.RESULT_ERROR_BAD_VALUE))
                } else {
                    setQueue(tracks, args.getInt(EXTRA_START_INDEX, 0))
                    completed(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            }
            CMD_SEEK_QUEUE -> {
                val idx = args.getInt(EXTRA_START_INDEX, -1)
                android.util.Log.i(TAG, "seekQueue idx=$idx dual=$dualMode")
                when {
                    !dualMode && idx >= 0 -> {
                        primary.player?.seekTo(idx, 0L)
                        completed(SessionResult(SessionResult.RESULT_SUCCESS))
                    }
                    dualMode && idx in queueTracks.indices -> {
                        queueJump(idx)
                        completed(SessionResult(SessionResult.RESULT_SUCCESS))
                    }
                    else -> completed(SessionResult(SessionResult.RESULT_ERROR_BAD_VALUE))
                }
            }
            CMD_ENQUEUE_NEXT -> {
                val tracks = args.getString(EXTRA_TRACKS_JSON)?.let { json ->
                    runCatching { MediaTrack.listFromJson(json) }.getOrNull()
                }
                if (tracks.isNullOrEmpty()) {
                    completed(SessionResult(SessionResult.RESULT_ERROR_BAD_VALUE))
                } else {
                    enqueueTracks(tracks, args.getBoolean(EXTRA_ENQUEUE_END, false))
                    completed(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            }
            CMD_SET_REPLAY_GAIN -> {
                // Prefs already persisted by the UI; re-apply the trim live.
                applyReplayGain()
                completed(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            else -> completed(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
        }

        /** Installs a full queue; both engines are served from the same entry point. */
        private fun setQueue(tracks: List<MediaTrack>, startIndex: Int) {
            android.util.Log.i(TAG, "setQueue n=${tracks.size} index=$startIndex dual=$dualMode")
            if (tracks.isEmpty()) return
            val start = startIndex.coerceIn(0, tracks.size - 1)
            if (dualMode) {
                // With shuffle on, the chosen track leads and the rest of the
                // queue is randomized (behavioral family with the reference).
                val ordered = if (shuffleOn && tracks.size > 1) {
                    val rest = tracks.filterIndexed { i, _ -> i != start }
                    listOf(tracks[start]) + ShuffleOps.shuffleTail(
                        rest, -1, kotlin.random.Random(System.nanoTime()),
                    )
                } else tracks
                queueTracks = ordered
                queueIndex = 0
                QueueBus.publish(ordered, 0)
                playCurrentItem(0L)
            } else {
                val p = primary.player ?: return
                applyNativePlayerFlags(p)
                val startTrack = tracks[start]
                val startPos = restorePositionFor(startTrack)
                p.setMediaItems(tracks.map { it.toMediaItem() }, start, startPos)
                p.prepare()
                p.play()
                QueueBus.publish(tracks, start)
            }
        }

        // Insert tracks after the current item (or at the end) without
        // interrupting ongoing playback (context-menu "enqueue next").
        private fun enqueueTracks(tracks: List<MediaTrack>, atEnd: Boolean) {
            if (dualMode) {
                if (queueTracks.isEmpty()) {
                    setQueue(tracks, 0)
                    return
                }
                val at = if (atEnd) queueTracks.size else queueIndex + 1
                val newList = queueTracks.take(at) + tracks + queueTracks.drop(at)
                queueTracks = newList
                QueueBus.publish(newList, queueIndex)
                android.util.Log.i(
                    TAG, "enqueue n=${tracks.size} at=$at dual=true total=${newList.size}",
                )
            } else {
                val p = primary.player ?: return
                val base = QueueBus.tracks
                val at = when {
                    base.isEmpty() || QueueBus.index < 0 -> p.mediaItemCount
                    atEnd -> p.mediaItemCount
                    else -> (QueueBus.index + 1).coerceIn(0, p.mediaItemCount)
                }
                android.util.Log.i(TAG, "enqueue n=${tracks.size} at=$at dual=false")
                p.addMediaItems(at, tracks.map { it.toMediaItem() })
                // Publish the new structure for the queue screen.
                if (base.isEmpty()) {
                    QueueBus.publish(tracks, 0)
                } else {
                    val atList = at.coerceIn(0, base.size)
                    QueueBus.publish(
                        base.take(atList) + tracks + base.drop(atList),
                        atList,
                    )
                }
            }
        }

        // Dual-mode queue changes from controllers (UI) route through the
        // orchestrator; the session player receives only the current item so
        // no second decoder duplicates audio.
        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            android.util.Log.i(
                TAG,
                "onSetMediaItems items=${mediaItems.size} start=$startIndex dual=$dualMode",
            )
            if (!dualMode || mediaItems.isEmpty()) {
                return super.onSetMediaItems(
                    mediaSession, controller, mediaItems, startIndex, startPositionMs,
                )
            }
            queueTracks = mediaItems.map { mi ->
                MediaTrack(
                    uri = mi.localConfiguration?.uri?.toString() ?: "?",
                    title = mi.mediaMetadata.title?.toString() ?: "?",
                    artist = mi.mediaMetadata.artist?.toString() ?: "",
                    album = mi.mediaMetadata.albumTitle?.toString() ?: "",
                    durationMs = mi.mediaMetadata.extras?.getLong(EXTRA_DURATION_MS) ?: 0L,
                )
            }
            queueIndex = startIndex.coerceIn(0, queueTracks.size - 1)
            QueueBus.publish(queueTracks, queueIndex)
            playCurrentItem(startPositionMs)
            return completed(
                MediaSession.MediaItemsWithStartPosition(
                    mutableListOf(queueTracks[queueIndex].toMediaItem()), 0, startPositionMs,
                ),
            )
        }

        /**
         * Headset/media buttons (block 10). Honours the respond-to-buttons
         * toggle (off ⇒ swallow everything); a hook button resolves through
         * [gestures] (single/double/triple/long press), while standard media
         * keys map straight to our engine so next/previous work in dual mode.
         */
        override fun onMediaButtonEvent(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            intent: Intent,
        ): Boolean {
            if (!AudioSettings.respondToButtons(applicationContext)) return true // swallow
            @Suppress("DEPRECATION")
            val event = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
                as? android.view.KeyEvent
                ?: return super.onMediaButtonEvent(session, controller, intent)
            return when (event.keyCode) {
                android.view.KeyEvent.KEYCODE_HEADSETHOOK -> {
                    handleHook(event); true
                }
                android.view.KeyEvent.KEYCODE_MEDIA_PLAY -> {
                    enginePlay(); true
                }
                android.view.KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                    enginePause(); true
                }
                android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                    engineToggle(); true
                }
                android.view.KeyEvent.KEYCODE_MEDIA_NEXT -> {
                    skipNext(); true
                }
                android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                    skipPrevious(); true
                }
                else -> super.onMediaButtonEvent(session, controller, intent)
            }
        }

        // Dual-mode trade-off (documented): the session player holds exactly one
        // item, so player-level next/previous have no visible item to seek.
        // Advancement is driven by the orchestrator on natural track ends and
        // through the app UI; native mode keeps Media3's full queue behavior.
    }
