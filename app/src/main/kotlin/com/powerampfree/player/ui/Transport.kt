package com.powerampfree.player.ui

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import java.io.File
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.powerampfree.audio.dsp.Band
import com.powerampfree.audio.dsp.EqPreset
import com.powerampfree.audio.dsp.FilterType
import com.powerampfree.audio.dsp.ParametricEq
import com.powerampfree.audio.lrc.LrcLine
import com.powerampfree.audio.lrc.LrcParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import com.powerampfree.player.*

    // Unified player snapshot (title / uri / playing / format) with ONE listener
    // so Compose recomposes only while the session actually changes.

internal data class PlayerSnapshot(
        val title: String = "—",
        val artist: String = "",
        val uri: String = "",
        val isPlaying: Boolean = false,
        val format: String = "",
        /** Current-track cues for art lookup (MediaStore albumId or file uri). */
        val albumId: Long = -1L,
        val folder: String = "",
    )


    @Composable
internal fun MainActivity.rememberPlayerSnapshot(): androidx.compose.runtime.State<PlayerSnapshot> {
        val state = androidx.compose.runtime.remember { mutableStateOf(PlayerSnapshot()) }
        androidx.compose.runtime.LaunchedEffect(controller) {
            val c = controller
            if (c == null) {
                state.value = PlayerSnapshot()
            } else {
                val listener = object : Player.Listener {
                    override fun onMediaMetadataChanged(m: MediaMetadata) {
                        state.value = snapshotOf(c)
                    }

                    override fun onMediaItemTransition(
                        m: MediaItem?,
                        reason: Int,
                    ) {
                        state.value = snapshotOf(c)
                    }

                    override fun onIsPlayingChanged(playing: Boolean) {
                        state.value = snapshotOf(c)
                    }
                }
                c.addListener(listener)
                state.value = snapshotOf(c)
                try {
                    awaitCancellation()
                } finally {
                    c.removeListener(listener)
                }
            }
        }
        return state
    }


internal fun MainActivity.snapshotOf(c: Player): PlayerSnapshot {
        // Prefer the format published by the service's own session player
        // (real decoder values); fall back to the controller's track list.
        var fmt = FormatBus.current
        if (fmt.isEmpty()) {
            var sRate = 0
            var bps = 0
            var mime = ""
            outer@ for (group in c.currentTracks.groups) {
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
            fmt = FormatBus.formatString(sRate, bps, mime)
        }
        // Album art lookup cues come from the published queue (same process,
        // QueueBus carries the real MediaTrack list incl. albumId/folder).
        val qt = QueueBus.tracks.getOrNull(QueueBus.index)
        return PlayerSnapshot(
            title = c.mediaMetadata.title?.toString() ?: "—",
            artist = c.mediaMetadata.artist?.toString() ?: "",
            uri = c.currentMediaItem?.mediaId ?: "",
            isPlaying = c.isPlaying,
            format = fmt,
            albumId = qt?.albumId ?: -1L,
            folder = qt?.folder ?: "",
        )
    }


internal fun MainActivity.fmtClock(msRemain: Long): String {
        val s = (msRemain / 1000).coerceAtLeast(0L)
        return "%d:%02d".format(s / 60, s % 60)
    }


internal fun MainActivity.sendRepeat(mode: String) {
        val c = controller ?: return
        val bundle = Bundle().apply { putString(PlaybackService.EXTRA_REPEAT_MODE, mode) }
        c.sendCustomCommand(
            androidx.media3.session.SessionCommand(PlaybackService.CMD_SET_REPEAT, bundle),
            bundle,
        )
    }


internal fun MainActivity.sendShuffle(on: Boolean) {
        val c = controller ?: return
        val bundle = Bundle().apply { putBoolean(PlaybackService.EXTRA_SHUFFLE_ON, on) }
        c.sendCustomCommand(
            androidx.media3.session.SessionCommand(PlaybackService.CMD_SET_SHUFFLE, bundle),
            bundle,
        )
    }


internal fun MainActivity.nudgePosition(deltaMs: Long) {
        val c = controller ?: return
        runCatching { c.seekTo((c.currentPosition + deltaMs).coerceAtLeast(0L)) }
    }


internal fun MainActivity.togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }


internal fun MainActivity.sendSkip(next: Boolean) {
        val c = controller ?: return
        val action = if (next) PlaybackService.CMD_SKIP_NEXT else PlaybackService.CMD_SKIP_PREV
        val bundle = Bundle.EMPTY
        controller?.sendCustomCommand(androidx.media3.session.SessionCommand(action, bundle), bundle)
    }


    // --------------------------------------------------- playback helpers

    /** Insert a track into the queue right after the current one (or at end). */
internal fun MainActivity.sendEnqueueNext(t: MediaTrack, atEnd: Boolean) {
        val c = controller ?: return
        val bundle = Bundle().apply {
            putString(PlaybackService.EXTRA_TRACKS_JSON, MediaTrack.listToJson(listOf(t)))
            putBoolean(PlaybackService.EXTRA_ENQUEUE_END, atEnd)
        }
        android.util.Log.i(TAG, "enqueueNext uri=${t.uri} atEnd=$atEnd")
        c.sendCustomCommand(
            androidx.media3.session.SessionCommand(PlaybackService.CMD_ENQUEUE_NEXT, bundle),
            bundle,
        )
    }


internal fun MainActivity.sendSeekQueue(index: Int) {
        val c = controller ?: return
        val bundle = Bundle().apply { putInt(PlaybackService.EXTRA_START_INDEX, index) }
        c.sendCustomCommand(
            androidx.media3.session.SessionCommand(PlaybackService.CMD_SEEK_QUEUE, bundle),
            bundle,
        )
    }


internal fun MainActivity.sendPreset(preset: EqPreset) {
        PresetStore.storeCurrent(this, preset)
        val bundle = Bundle().apply {
            putString(PlaybackService.EXTRA_PRESET_JSON, preset.toJson())
        }
        controller?.sendCustomCommand(
            androidx.media3.session.SessionCommand(PlaybackService.CMD_SET_PRESET, bundle),
            bundle,
        )
    }


internal fun MainActivity.sendCrossfade(ms: Long) {
        PlaybackSettings.setCrossfadeMs(this, ms)
        val bundle = Bundle().apply {
            putLong(PlaybackService.EXTRA_CROSSFADE_MS, ms)
        }
        controller?.sendCustomCommand(
            androidx.media3.session.SessionCommand(PlaybackService.CMD_SET_CROSSFADE, bundle),
            bundle,
        )
    }


    /**
     * Push the Replay Gain trim live to the playback session. Prefs
     * (audio_settings) are already persisted by the caller; the service just
     * re-reads them and rebuilds the DSP chain trim.
     */
internal fun MainActivity.sendReplayGain() {
        controller?.sendCustomCommand(
            androidx.media3.session.SessionCommand(PlaybackService.CMD_SET_REPLAY_GAIN, Bundle.EMPTY),
            Bundle.EMPTY,
        )
    }
