package com.powerampfree.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioProcessor as M3AudioProcessor
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.powerampfree.audio.engine.AutoAdvanceEngine
import com.powerampfree.audio.input.HeadsetGestures
import com.powerampfree.player.service.abandonAudioFocus
import com.powerampfree.player.service.advanceOrchestrator
import com.powerampfree.player.service.buildSessionCallback
import com.powerampfree.player.service.cancelPendingFadeFinish
import com.powerampfree.player.service.dispatchGesture
import com.powerampfree.player.service.enginePause
import com.powerampfree.player.service.enginePlay
import com.powerampfree.player.service.fadeWatcher
import com.powerampfree.player.service.finishFade
import com.powerampfree.player.service.handleFocusChange
import com.powerampfree.player.service.makeListener
import com.powerampfree.player.service.registerHeadsetReceivers
import com.powerampfree.player.service.restoreQueue
import com.powerampfree.player.service.saveListeningProgress
import com.powerampfree.player.service.saveQueueSnapshot
import com.powerampfree.player.service.stopSaveTicker
import com.powerampfree.player.service.TAG
import com.powerampfree.player.service.unregisterHeadsetReceivers
import com.powerampfree.player.service.WATCHER_PERIOD_MS

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
 *
 * The behavior lives in extension functions under `player/service/`:
 * `Orchestrator` (dual-beam advancement), `QueuePersistence` (snapshot +
 * restore), `SessionCallback` (Media3 command surface), `FocusHeadset`
 * (audio focus + headset routing) and `PlayerListener` (ExoPlayer events).
 */
class PlaybackService : MediaSessionService() {

    internal var mediaSession: MediaSession? = null
    internal var primary: ExoBeam = ExoBeam.empty()
    internal var overlay: ExoBeam? = null
    internal val mainHandler = Handler(Looper.getMainLooper())

    // ---- dual-decoder orchestrator state -----------------------------------
    internal var queueTracks: List<MediaTrack> = emptyList()
    internal var queueIndex = -1
    internal var fadeActive = false
    internal var plannedRealDurationMs = -1L

    internal val crossfadeMs: Long
        get() = PlaybackSettings.crossfadeMs(applicationContext)
    internal val dualMode: Boolean
        get() = crossfadeMs > 0

    // ---- repeat / shuffle (player screen) ----------------------------------
    internal val repeatMode: String
        get() = PlaybackSettings.repeatMode(applicationContext)
    internal val isRepeatOne: Boolean
        get() = repeatMode == PlaybackSettings.REPEAT_ONE
    internal val isRepeatAll: Boolean
        get() = repeatMode == PlaybackSettings.REPEAT_ALL
    internal val shuffleOn: Boolean
        get() = PlaybackSettings.shuffleEnabled(applicationContext)

    internal fun applyNativePlayerFlags(p: androidx.media3.common.Player?) {
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
    internal fun applyDualBeamFlags(p: androidx.media3.common.Player?) {
        p ?: return
        p.repeatMode = if (isRepeatOne) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        p.shuffleModeEnabled = false
    }

    /** One decodable audio slot: its own player + DSP processor + listener. */
    internal class ExoBeam(val player: ExoPlayer?, val dsp: DspAudioProcessor?) {
        fun release() {
            player?.release()
        }
        companion object {
            fun empty() = ExoBeam(null, null)
        }
    }

    // session ids for the engine: 1-based queue index (dual) / player index (native)
    internal fun currentSid(): Long = sidOf(currentIndex())
    internal fun currentIndex(): Int =
        if (dualMode) queueIndex else (primary.player?.currentMediaItemIndex ?: 0)
    internal fun sidOf(i: Int): Long = i.toLong() + 1L

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
    internal lateinit var advanceEngine: AutoAdvanceEngine

    // -------------------------------------------------------- player slots

    internal fun buildBeam(withFocus: Boolean): ExoBeam {
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

    // ----------------------------------------------------- focus/headset state

    internal var audioManager: android.media.AudioManager? = null
    internal var focusRequest: android.media.AudioFocusRequest? = null
    internal var haveFocus = false
    /** True only when WE paused due to a focus loss (a user pause never sets it). */
    internal var focusPaused = false
    internal var focusDucked = false

    internal val focusListener =
        android.media.AudioManager.OnAudioFocusChangeListener { change ->
            handleFocusChange(change)
        }

    internal val gestures = HeadsetGestures()
    internal var wiredConnected = false
    internal var btConnected = false
    internal var plugInitialized = false

    internal val gestureResolve = Runnable { dispatchGesture(gestures.onResolveWindow()) }
    internal val gestureLong = Runnable { dispatchGesture(gestures.onLongElapsed()) }

    /** Narrow identity for `finishFade`: [cancelPendingFadeFinish] removes the
     *  exact instance posted by [startOverlap], so it must be a stable object. */
    internal val finishFadeRunnable = Runnable { finishFade() }

    internal val saveTicker = object : Runnable {
        override fun run() {
            if (primary.player?.isPlaying == true) {
                saveQueueSnapshot()
                saveListeningProgress() // block 11: periodic playhead backup
                mainHandler.postDelayed(this, SAVE_PERIOD_MS)
            }
        }
    }

    internal val noisyReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY &&
                AudioSettings.pauseOnDisconnect(applicationContext)
            ) {
                enginePause()
            }
        }
    }

    internal val headsetPlugReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != Intent.ACTION_HEADSET_PLUG) return
            val state = intent.getIntExtra("state", -1)
            // ACTION_HEADSET_PLUG is sticky: the first delivery is the current
            // state, which we seed without triggering a resume-on-plug.
            if (!plugInitialized) {
                plugInitialized = true
                wiredConnected = state == 1
                return
            }
            val was = wiredConnected
            wiredConnected = state == 1
            if (wiredConnected && !was && AudioSettings.resumeOnWired(applicationContext)) {
                enginePlay()
            }
        }
    }

    internal val btReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != BT_A2DP_CONNECTION_CHANGED) return
            val state = intent.getIntExtra(BT_EXTRA_STATE, -1)
            val was = btConnected
            btConnected = state == BT_STATE_CONNECTED
            if (btConnected && !was && AudioSettings.resumeOnBluetooth(applicationContext)) {
                enginePlay()
            }
        }
    }

    // -------------------------------------------------------- service hooks

    override fun onCreate() {
        super.onCreate()
        android.util.Log.i(TAG, "service onCreate: building players + session")
        advanceEngine = AutoAdvanceEngine(advanceListener)
        primary = buildBeam(withFocus = false)
        mediaSession = MediaSession.Builder(this, primary.player!!)
            .setCallback(buildSessionCallback())
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
        audioManager = getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
        registerHeadsetReceivers()
        restoreQueue()
        mainHandler.postDelayed(fadeWatcher, WATCHER_PERIOD_MS)
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
        mainHandler.removeCallbacks(gestureResolve)
        mainHandler.removeCallbacks(gestureLong)
        unregisterHeadsetReceivers()
        abandonAudioFocus()
        overlay?.release()
        overlay = null
        primary.release()
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    companion object {
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
        const val CMD_SET_REPLAY_GAIN = "com.powerampfree.player.SET_REPLAY_GAIN"

        // Bluetooth A2DP connection state (block 10).
        internal const val BT_A2DP_CONNECTION_CHANGED =
            "android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED"
        internal const val BT_EXTRA_STATE = "android.bluetooth.profile.extra.STATE"
        internal const val BT_STATE_CONNECTED = 2 // BluetoothProfile.STATE_CONNECTED
        const val EXTRA_PRESET_JSON = "com.powerampfree.player.preset_json"
        const val EXTRA_CROSSFADE_MS = "crossfade_ms"
        const val EXTRA_DURATION_MS = "durationMs"
        const val EXTRA_TRACKS_JSON = "tracks_json"
        const val EXTRA_START_INDEX = "start_index"
        const val EXTRA_ENQUEUE_END = "enqueue_end"
        const val EXTRA_REPEAT_MODE = "repeat_mode"
        const val EXTRA_SHUFFLE_ON = "shuffle_on"
        internal const val SAVE_PERIOD_MS = 15_000L

        fun sessionComponent(context: Context) =
            ComponentName(context, PlaybackService::class.java)
    }
}
