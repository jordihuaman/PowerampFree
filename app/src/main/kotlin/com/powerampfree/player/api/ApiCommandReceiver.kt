package com.powerampfree.player.api

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.powerampfree.audio.dsp.EqPreset
import com.powerampfree.player.PlaybackService

/**
 * Public command surface for this app — own namespace, own semantics.
 *
 * Action:  com.powerampfree.player.API_COMMAND
 * extras:  "cmd"  : one of play|pause|playpause|next|previous|set_preset|$preset_index? no-more
 *          "arg"  : JSON preset text for cmd=set_preset
 *
 * This is an original API designed for our project, not a replica of any
 * reference app's public intents. Commands are forwarded to the MediaSession
 * through a MediaController, so the same path the UI uses is exercised.
 */
class ApiCommandReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val cmd = intent.getStringExtra(EXTRA_CMD) ?: return
        android.util.Log.i("PafApi", "api broadcast: $cmd")
        val controller = MediaController.Builder(
            context,
            SessionToken(context, PlaybackService.sessionComponent(context)),
        ).buildAsync()

        // BroadcastReceivers may end before the async connect finishes: goAsync()
        val pending = goAsync()
        controller.addListener(
            {
                try {
                    val c = controller.get()
                    Handler(Looper.getMainLooper()).post {
                        try {
                            handle(c, cmd, intent.getStringExtra(EXTRA_ARG))
                        } finally {
                            c.release()
                            pending.finish()
                        }
                    }
                } catch (t: Exception) {
                    android.util.Log.e("PafApi", "controller connect failed", t)
                    pending.finish()
                }
            },
            { it.run() },
        )
    }

    private fun handle(c: MediaController, cmd: String, arg: String?) {
        when (cmd) {
            CMD_PLAY -> c.play()
            CMD_PAUSE -> c.pause()
            CMD_PLAY_PAUSE -> if (c.isPlaying) c.pause() else c.play()
            CMD_NEXT -> sendSkip(c, PlaybackService.CMD_SKIP_NEXT)
            CMD_PREVIOUS -> sendSkip(c, PlaybackService.CMD_SKIP_PREV)
            CMD_SEEK -> {
                val ms = arg?.toLongOrNull() ?: return
                c.seekTo(ms)
            }
            CMD_SET_PRESET -> {
                val json = arg ?: return
                runCatching { EqPreset.fromJson(json) }.getOrNull() ?: return
                val extras = Bundle().apply {
                    putString(PlaybackService.EXTRA_PRESET_JSON, json)
                }
                c.sendCustomCommand(
                    SessionCommand(PlaybackService.CMD_SET_PRESET, extras),
                    extras,
                )
            }
        }
    }

    /** Skips route through the session's own commands (valid in both engines). */
    private fun sendSkip(c: MediaController, action: String) {
        android.util.Log.i("PafApi", "sendSkip $action")
        try {
            c.sendCustomCommand(SessionCommand(action, Bundle.EMPTY), Bundle.EMPTY)
        } catch (t: Throwable) {
            android.util.Log.e("PafApi", "sendCustomCommand failed", t)
        }
    }

    companion object {
        const val ACTION = "com.powerampfree.player.API_COMMAND"
        const val EXTRA_CMD = "com.powerampfree.player.cmd"
        const val EXTRA_ARG = "com.powerampfree.player.arg"
        const val CMD_PLAY = "play"
        const val CMD_PAUSE = "pause"
        const val CMD_PLAY_PAUSE = "playpause"
        const val CMD_NEXT = "next"
        const val CMD_PREVIOUS = "previous"
        const val CMD_SEEK = "seek"
        const val CMD_SET_PRESET = "set_preset"
    }
}
