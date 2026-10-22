package com.powerampfree.player

import android.content.Context
import android.content.Intent
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Foreground playback service.
 *
 * Wires Media3 ExoPlayer (gapless playlist engine through same-format playlist
 * concatenation) with our DSP AudioProcessor and a MediaSession.
 *
 * TODO(integrate):
 *  - playlist loading from MediaStore / SAF
 *  - queue persistence across process deaths
 *  - EQ preset live changes: notify dsp (applyPreset path, via chain setBands)
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink? {
                return DefaultAudioSink.Builder(context)
                    .setAudioProcessors(arrayOf<AudioProcessor>(DspAudioProcessor()))
                    .build()
            }
        }

        val player = ExoPlayer.Builder(this, renderersFactory)
            .build()

        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}