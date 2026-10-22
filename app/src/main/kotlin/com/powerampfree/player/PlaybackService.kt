package com.powerampfree.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.audio.AudioProcessor as M3AudioProcessor
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.ListenableFuture
import com.powerampfree.audio.engine.AutoAdvanceEngine
import com.powerampfree.audio.util.ShuffleOps
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

/**
 * Foreground playback service with TWO advance engines:
 *
 *  1) **Native gapless** (crossfade off): one ExoPlayer owns the whole queue;
 *     same-format items concatenate seamlessly in one audio-sink session and
 *     ExoPlayer events map into [AutoAdvanceEngine] (id guards, end tolerance).
 *
 *  2) **Real overlapping mix** (crossfade > 0, dual-decoder, clean-room):
 *     - a queue orchestrator ([queueTracks]/[queueIndex]) owns advancement;
 *     - the session player plays only the CURRENT track, faded to 0 at the seam
 *       by its own crossfade plan (fade-out window at the end of the item);
 *     - an overlay ExoPlayer is prepared (paused) with the NEXT track and a
 *       plan that fades IN from 0 at its audio start (forceFadeIn);
 *     - when the session player reaches seam−fadeMs the overlay starts: both
 *       decoders sound simultaneously (Android mixes the two AudioTracks at
 *       the audio server) → real overlapping crossfade;
 *     - when the fade completes, [MediaSession.setPlayer] promotes the overlay
 *       (observed pending→current promotion) and the old player releases; the
 *       next overlay is armed immediately for the following seam.
 *
 * Queue lifecycle: persisted via [QueueStore]; restore is paused. EQ applies
 * live via `SET_PRESET`; crossfade length via `SET_CROSSFADE` (own API).
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var primary: ExoBeam = ExoBeam.empty()
    private var overlay: ExoBeam? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    // ---- dual-decoder orchestrator state -----------------------------------
    private var queueTracks: List<MediaTrack> = emptyList()
    private var queueIndex = -1
    private var fadeActive = false

    private val crossfadeMs: Long
        get() = PlaybackSettings.crossfadeMs(applicationContext)
    private val dualMode: Boolean
        get() = crossfadeMs > 0

    // ---- repeat / shuffle (player screen) ----------------------------------
    private val repeatMode: String
        get() = PlaybackSettings.repeatMode(applicationContext)
    private val isRepeatOne: Boolean
        get() = repeatMode == PlaybackSettings.REPEAT_ONE
    private val isRepeatAll: Boolean
        get() = repeatMode == PlaybackSettings.REPEAT_ALL
    private val shuffleOn: Boolean
        get() = PlaybackSettings.shuffleEnabled(applicationContext)

    private fun applyNativePlayerFlags(p: androidx.media3.common.Player?) {
        p ?: return
        p.repeatMode = when (repeatMode) {
            PlaybackSettings.REPEAT_ONE -> Player.REPEAT_MODE_ONE
            PlaybackSettings.REPEAT_ALL -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
        }
        p.shuffleModeEnabled = shuffleOn
    }

    /** Dual beams carry a single item: replay-one loops natively, ALL wraps in
     *  the orchestrator ([advanceOrchestrator]), so the beam must not loop. */
    private fun applyDualBeamFlags(p: androidx.media3.common.Player?) {
        p ?: return
        p.repeatMode = if (isRepeatOne) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        p.shuffleModeEnabled = false
    }

    /** One decodable audio slot: its own player + DSP processor + listener. */
    private class ExoBeam(val player: ExoPlayer?, val dsp: DspAudioProcessor?) {
        fun release() {
            player?.release()
        }
        companion object {
            fun empty() = ExoBeam(null, null)
        }
    }

    // session ids for the engine: 1-based queue index (dual) / player index (native)
    private fun currentSid(): Long = sidOf(currentIndex())
    private fun currentIndex(): Int =
        if (dualMode) queueIndex else (primary.player?.currentMediaItemIndex ?: 0)
    private fun sidOf(i: Int): Long = i.toLong() + 1L

    private val advanceListener = object : AutoAdvanceEngine.Listener {
        override fun onAdvanceNeeded(currentSession: Long) {
            if (dualMode) advanceOrchestrator()
            else {
                val p = primary.player
                if (p?.hasNextMediaItem() == true) p.seekToNextMediaItem()
                else advanceEngine.playbackTerminated()
            }
        }
        override fun onPendingPromoted(newCurrentSession: Long) = Unit
        override fun onPipelineIdle() = stopSaveTicker()
    }
    private lateinit var advanceEngine: AutoAdvanceEngine

    // -------------------------------------------------------- player slots

    private fun buildBeam(withFocus: Boolean): ExoBeam {
        val dsp = DspAudioProcessor(applicationContext)
        val factory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink? {
                return DefaultAudioSink.Builder(context)
                    .setAudioProcessors(arrayOf<M3AudioProcessor>(dsp))
                    .build()
            }
        }
        val player = ExoPlayer.Builder(this, factory)
            .setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ withFocus,
            )
            .build()
        player.addListener(makeListener(player))
        applyNativePlayerFlags(player)
        return ExoBeam(player, dsp)
    }

    /**
     * Listener factory (one per player): only the session player drives
     * engine/queue continuity; the overlay decoder's events are handled by the
     * orchestrator ([startOverlap]/[finishFade]), not here.
     */
    private fun makeListener(self: ExoPlayer): Player.Listener = object : Player.Listener {

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
            } else {
                stopSaveTicker()
                saveQueueSnapshot()
                // Freeze the whole mix on user pause: the overlay halts too.
                if (fadeActive) overlay?.player?.pause()
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

    // --------------------------------------------------------- orchestrator

    /** Starts playback of [queueIndex] from orchestrator state (dual/queued restore). */
    private fun playCurrentItem(positionMs: Long) {
        if (queueIndex !in queueTracks.indices) return
        cancelPendingFadeFinish()
        fadeActive = false
        val oldOverlay = overlay
        overlay = null
        oldOverlay?.release()
        advanceEngine.currentTrackOpened(sidOf(queueIndex))

        val item = queueTracks[queueIndex].toMediaItem()
        val beam = buildBeam(withFocus = true)
        if (positionMs > 0L) {
            beam.player?.setMediaItem(item, positionMs)
        } else {
            beam.player?.setMediaItem(item)
        }
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
    private fun armOverlay() {
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
    private fun startOverlap() {
        if (fadeActive) return
        val ov = overlay?.player ?: return
        fadeActive = true
        advanceEngine.pendingTrackOpened(sidOf(queueIndex + 1))
        ov.play()
        mainHandler.postDelayed(finishFadeRunnable, crossfadeMs)
    }

    /** Promotes the overlay once the fade completed (pending → current promotion). */
    private fun finishFade() {
        if (!fadeActive) return
        val ov = overlay ?: run {
            fadeActive = false
            cancelPendingFadeFinish()
            return
        }
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

    private val finishFadeRunnable = Runnable { finishFade() }
    private fun cancelPendingFadeFinish() { mainHandler.removeCallbacks(finishFadeRunnable) }

    /** Instant advance (skip / next-media-button / end-without-overlay). */
    private fun advanceOrchestrator() {
        if (!dualMode) return
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

    private fun planPrimaryCrossfade() {
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
    private var plannedRealDurationMs = -1L

    private fun replanWithRealDuration(player: androidx.media3.common.Player) {
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

    private fun resetCrossfadePlan(dsp: DspAudioProcessor) {
        dsp.setCrossfadePlan(emptyList(), 0L, 0, 0)
    }

    private fun replanAfterCrossfadeChange() {
        planPrimaryCrossfade()
        if (!dualMode) { overlay?.release(); overlay = null; return }
        armOverlay()
    }

    /** Watches the session player to launch the overlap exactly at seam−fadeMs. */
    private val fadeWatcher = object : Runnable {
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

    private fun skipNext() {
        android.util.Log.i(TAG, "skipNext dual=$dualMode")
        if (dualMode) advanceOrchestrator() else primary.player?.seekToNextMediaItem()
    }

    private fun skipPrevious() {
        if (queueIndex - 1 !in queueTracks.indices && !dualMode) {
            primary.player?.seekToPreviousMediaItem()
            return
        }
        queueIndex -= 1
        if (dualMode && queueIndex in queueTracks.indices) {
            QueueBus.publish(queueTracks, queueIndex)
        }
        dualModeOrchestratorPlayPrevious()
    }

    /** Queue screen: jump directly to [idx] WITHOUT re-shuffling/re-queueing. */
    private fun queueJump(idx: Int) {
        cancelPendingFadeFinish()
        fadeActive = false
        queueIndex = idx
        plannedRealDurationMs = -1L
        playCurrentItem(0L)
        QueueBus.publish(queueTracks, idx)
    }

    private fun dualModeOrchestratorPlayPrevious() {
        if (queueIndex !in queueTracks.indices) return
        playCurrentItem(0L)
    }

    // ---------------------------------------------------- queue persistence

    private fun saveQueueSnapshot() {
        val p = primary.player ?: return
        val snapshot: List<MediaTrack>
        val index: Int
        if (dualMode) {
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

    private fun startSaveTicker() { mainHandler.postDelayed(saveTicker, SAVE_PERIOD_MS) }
    private fun stopSaveTicker() { mainHandler.removeCallbacks(saveTicker) }

    private val saveTicker = object : Runnable {
        override fun run() {
            if (primary.player?.isPlaying == true) {
                saveQueueSnapshot()
                mainHandler.postDelayed(this, SAVE_PERIOD_MS)
            }
        }
    }

    // ----------------------------------------------------- session plumbing

    private val sessionCallback = object : MediaSession.Callback {

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
                p.setMediaItems(tracks.map { it.toMediaItem() }, start, 0L)
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

        // Dual-mode trade-off (documented): the session player holds exactly one
        // item, so player-level next/previous have no visible item to seek.
        // Advancement is driven by the orchestrator on natural track ends and
        // through the app UI; native mode keeps Media3's full queue behavior.
    }
    private fun skipNextUnused() = Unit

    // -------------------------------------------------------- service hooks

    override fun onCreate() {
        super.onCreate()
        android.util.Log.i(TAG, "service onCreate: building players + session")
        advanceEngine = AutoAdvanceEngine(advanceListener)
        primary = buildBeam(withFocus = true)
        mediaSession = MediaSession.Builder(this, primary.player!!)
            .setCallback(sessionCallback)
            .build()
        // Manifest receivers don't receive implicit custom actions (Android 8+);
        // register our API receiver dynamically so shell/automation broadcasts work.
        runCatching {
            androidx.core.content.ContextCompat.registerReceiver(
                this,
                com.powerampfree.player.api.ApiCommandReceiver(),
                android.content.IntentFilter(com.powerampfree.player.api.ApiCommandReceiver.ACTION),
                androidx.core.content.ContextCompat.RECEIVER_EXPORTED,
            )
        }
        restoreQueue()
        mainHandler.postDelayed(fadeWatcher, WATCHER_PERIOD_MS)
    }

    /** Queue restore: once, paused, at the saved position (no autoplay on boot). */
    private fun restoreQueue() {
        val saved = QueueStore.load(this) ?: return
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

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = primary.player
        if (player == null || !player.playWhenReady ||
            (player.mediaItemCount == 0 && queueTracks.isEmpty())
        ) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        saveQueueSnapshot()
        stopSaveTicker()
        cancelPendingFadeFinish()
        overlay?.release()
        overlay = null
        primary.release()
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    private fun <T> completed(value: T): ListenableFuture<T> =
        object : ListenableFuture<T> {            override fun cancel(mayInterruptIfRunning: Boolean): Boolean = false
            override fun isCancelled(): Boolean = false
            override fun isDone(): Boolean = true
            override fun get(): T = value
            override fun get(timeout: Long, unit: TimeUnit): T = value
            override fun addListener(listener: Runnable, executor: Executor) {
                executor.execute(listener)
            }
        }

    /** Notification/custom-layout buttons backed by our own session commands. */
    private fun skipLayout(): List<androidx.media3.session.CommandButton> = listOf(
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

    companion object {
        private const val TAG = "PafService"

        /** Own namespaced API — original commands, not the reference app's intents. */
        const val CMD_SET_PRESET = "com.powerampfree.player.SET_PRESET"
        const val CMD_SET_CROSSFADE = "com.powerampfree.player.SET_CROSSFADE"
        const val CMD_SKIP_NEXT = "com.powerampfree.player.SKIP_NEXT"
        const val CMD_SKIP_PREV = "com.powerampfree.player.SKIP_PREV"
        const val CMD_SET_QUEUE = "com.powerampfree.player.SET_QUEUE"
        const val CMD_SEEK_QUEUE = "com.powerampfree.player.SEEK_QUEUE"
        const val CMD_ENQUEUE_NEXT = "com.powerampfree.player.ENQUEUE_NEXT"
        const val CMD_SET_REPEAT = "com.powerampfree.player.SET_REPEAT"
        const val CMD_SET_SHUFFLE = "com.powerampfree.player.SET_SHUFFLE"
        const val EXTRA_PRESET_JSON = "com.powerampfree.player.preset_json"
        const val EXTRA_CROSSFADE_MS = "crossfade_ms"
        const val EXTRA_DURATION_MS = "durationMs"
        const val EXTRA_TRACKS_JSON = "tracks_json"
        const val EXTRA_START_INDEX = "start_index"
        const val EXTRA_ENQUEUE_END = "enqueue_end"
        const val EXTRA_REPEAT_MODE = "repeat_mode"
        const val EXTRA_SHUFFLE_ON = "shuffle_on"
        private const val SAVE_PERIOD_MS = 15_000L
        private const val WATCHER_PERIOD_MS = 15L

        fun sessionComponent(context: Context) =
            ComponentName(context, PlaybackService::class.java)
    }
}