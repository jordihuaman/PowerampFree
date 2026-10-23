package com.powerampfree.player.service

import android.content.Intent
import android.content.IntentFilter
import com.powerampfree.audio.focus.FocusAction
import com.powerampfree.audio.focus.FocusChange
import com.powerampfree.audio.focus.FocusPolicy
import com.powerampfree.audio.focus.FocusToggles
import com.powerampfree.audio.input.HeadsetAction
import com.powerampfree.player.AudioSettings
import com.powerampfree.player.PlaybackService
import com.powerampfree.player.PlaybackService.Companion.BT_A2DP_CONNECTION_CHANGED

internal fun PlaybackService.focusToggles() = FocusToggles(
    pause = AudioSettings.focusPause(applicationContext),
    resume = AudioSettings.focusResume(applicationContext),
    duck = AudioSettings.focusDuck(applicationContext),
)

internal fun PlaybackService.buildFocusRequestIfNeeded() {
    if (focusRequest != null) return
    val am = getSystemService(android.content.Context.AUDIO_SERVICE)
        as? android.media.AudioManager ?: return
    audioManager = am
    focusRequest = android.media.AudioFocusRequest.Builder(
        android.media.AudioManager.AUDIOFOCUS_GAIN,
    )
        .setAudioAttributes(
            android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
        )
        .setOnAudioFocusChangeListener(focusListener, mainHandler)
        .setWillPauseWhenDucked(false)
        .build()
}

internal fun PlaybackService.ensureAudioFocus() {
    buildFocusRequestIfNeeded()
    val am = audioManager ?: return
    val req = focusRequest ?: return
    if (haveFocus) return
    haveFocus = am.requestAudioFocus(req) ==
        android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED
}

internal fun PlaybackService.abandonAudioFocus() {
    val am = audioManager ?: return
    val req = focusRequest ?: return
    if (haveFocus) am.abandonAudioFocusRequest(req)
    haveFocus = false
    focusPaused = false
    if (focusDucked) {
        focusDucked = false
        primary.dsp?.setDuck(false)
        overlay?.dsp?.setDuck(false)
    }
}

internal fun PlaybackService.handleFocusChange(change: Int) {
    val fc = when (change) {
        android.media.AudioManager.AUDIOFOCUS_GAIN -> FocusChange.GAIN
        android.media.AudioManager.AUDIOFOCUS_LOSS -> FocusChange.LOSS
        android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> FocusChange.LOSS_TRANSIENT
        android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK ->
            FocusChange.LOSS_TRANSIENT_CAN_DUCK
        else -> return
    }
    val playing = primary.player?.isPlaying == true
    val action = FocusPolicy.onEvent(fc, focusToggles(), focusPaused, focusDucked)
    when (action) {
        FocusAction.PAUSE -> if (playing) {
            focusPaused = true
            enginePause()
        }
        FocusAction.RESUME -> {
            enginePlay()
            focusPaused = false
        }
        FocusAction.DUCK -> if (playing && !focusDucked) {
            focusDucked = true
            primary.dsp?.setDuck(true)
            overlay?.dsp?.setDuck(true)
        }
        FocusAction.RESTORE -> if (focusDucked) {
            focusDucked = false
            primary.dsp?.setDuck(false)
            overlay?.dsp?.setDuck(false)
        }
        FocusAction.NONE -> {}
    }
    // A permanent loss drops our request; a later play re-requests.
    if (fc == FocusChange.LOSS) haveFocus = false
}

internal fun PlaybackService.enginePlay() {
    primary.player?.play()
    if (fadeActive) overlay?.player?.play()
}

internal fun PlaybackService.enginePause() {
    primary.player?.pause()
    if (fadeActive) overlay?.player?.pause()
}

internal fun PlaybackService.engineToggle() {
    if (primary.player?.isPlaying == true) enginePause() else enginePlay()
}

internal fun PlaybackService.handleHook(event: android.view.KeyEvent) {
    when (event.action) {
        android.view.KeyEvent.ACTION_DOWN -> {
            if (event.repeatCount == 0) {
                mainHandler.removeCallbacks(gestureLong)
                mainHandler.postDelayed(gestureLong, gestures.longPressMs)
            }
            gestures.onDown(event.repeatCount)
        }
        android.view.KeyEvent.ACTION_UP -> {
            mainHandler.removeCallbacks(gestureLong)
            mainHandler.removeCallbacks(gestureResolve)
            gestures.onUp()
            mainHandler.postDelayed(gestureResolve, gestures.doubleWindowMs)
        }
    }
}

internal fun PlaybackService.dispatchGesture(action: HeadsetAction?) {
    when (action) {
        HeadsetAction.SINGLE -> performSinglePress()
        HeadsetAction.DOUBLE_NEXT -> skipNext()
        HeadsetAction.TRIPLE_PREV -> skipPrevious()
        HeadsetAction.LONG_NEXT -> if (primary.player?.isPlaying == true) skipNext()
        else -> {}
    }
}

/**
 * Single press maps by the active connection (wired preferred when both
 * are up). Ambiguity (a BT button while wired is plugged) resolves to the
 * wired action — documented, since a hook KeyEvent carries no source.
 */
internal fun PlaybackService.performSinglePress() {
    val action = if (wiredConnected || !btConnected) {
        AudioSettings.wiredAction(applicationContext)
    } else {
        AudioSettings.btAction(applicationContext)
    }
    when (action) {
        AudioSettings.ACTION_NEXT -> skipNext()
        AudioSettings.ACTION_PREV -> skipPrevious()
        else -> engineToggle()
    }
}

internal fun PlaybackService.applyReplayGain() {
    val db = AudioSettings.rgAppliedDb(applicationContext)
    android.util.Log.i(TAG, "applyReplayGain db=$db")
    primary.dsp?.setReplayGain(db)
    overlay?.dsp?.setReplayGain(db)
}

internal fun PlaybackService.registerHeadsetReceivers() {
    // NOISY + BT A2DP connection state (block 10).
    runCatching {
        registerReceiver(
            noisyReceiver,
            IntentFilter(android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY),
        )
    }
    runCatching {
        registerReceiver(headsetPlugReceiver, IntentFilter(Intent.ACTION_HEADSET_PLUG))
    }
    runCatching {
        registerReceiver(btReceiver, IntentFilter(BT_A2DP_CONNECTION_CHANGED))
    }
}

internal fun PlaybackService.unregisterHeadsetReceivers() {
    runCatching { unregisterReceiver(noisyReceiver) }
    runCatching { unregisterReceiver(headsetPlugReceiver) }
    runCatching { unregisterReceiver(btReceiver) }
}
