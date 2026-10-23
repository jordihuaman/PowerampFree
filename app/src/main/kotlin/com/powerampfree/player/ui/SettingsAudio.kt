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

    // -------------------------------------------- audio / headset screens (7)

    /**
     * Block 8 audio screen: focus behaviour toggles + Replay Gain. RG changes
     * persist to audio_settings and push the trim live to the session.
     */
    @Composable
internal fun MainActivity.AudioSettingsScreen() {
        val ctx = this@AudioSettingsScreen
        var focusPause by remember { mutableStateOf(AudioSettings.focusPause(ctx)) }
        var focusResume by remember { mutableStateOf(AudioSettings.focusResume(ctx)) }
        var focusDuck by remember { mutableStateOf(AudioSettings.focusDuck(ctx)) }
        var rgOn by remember { mutableStateOf(AudioSettings.rgEnabled(ctx)) }
        var rgSource by remember { mutableStateOf(AudioSettings.rgSource(ctx)) }
        var rgDb by remember { mutableStateOf(AudioSettings.rgPregainDb(ctx)) }

        Column(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
            Text("Audio focus", style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = focusPause, onCheckedChange = { on ->
                    AudioSettings.setFocusPause(ctx, on)
                    focusPause = on
                })
                Text("  Pausar al perder foco", style = MaterialTheme.typography.bodyLarge)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = focusResume, onCheckedChange = { on ->
                    AudioSettings.setFocusResume(ctx, on)
                    focusResume = on
                })
                Text("  Reanudar al recuperar foco", style = MaterialTheme.typography.bodyLarge)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = focusDuck, onCheckedChange = { on ->
                    AudioSettings.setFocusDuck(ctx, on)
                    focusDuck = on
                })
                Text("  Duck (bajar volumen)", style = MaterialTheme.typography.bodyLarge)
            }
            Text(
                "Foco: pausa/reanudación por Events de otras apps " +
                    "(llamadas, navegación). Duck baja el volumen en vez de pausar.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )

            Spacer(Modifier.height(12.dp))
            Text("Replay Gain", style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = rgOn, onCheckedChange = { on ->
                    AudioSettings.setRgEnabled(ctx, on)
                    rgOn = on
                    sendReplayGain()
                })
                Text("  RG activo", style = MaterialTheme.typography.bodyLarge)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = {
                    AudioSettings.setRgSource(ctx, AudioSettings.RG_SOURCE_TRACK)
                    rgSource = AudioSettings.RG_SOURCE_TRACK
                    sendReplayGain()
                }) {
                    val mark = if (rgSource == AudioSettings.RG_SOURCE_TRACK) " ✓" else ""
                    Text("Pista$mark", style = MaterialTheme.typography.bodyLarge)
                }
                TextButton(onClick = {
                    AudioSettings.setRgSource(ctx, AudioSettings.RG_SOURCE_ALBUM)
                    rgSource = AudioSettings.RG_SOURCE_ALBUM
                    sendReplayGain()
                }) {
                    val mark = if (rgSource == AudioSettings.RG_SOURCE_ALBUM) " ✓" else ""
                    Text("Álbum$mark", style = MaterialTheme.typography.bodyLarge)
                }
            }
            Text(
                "Pregain: ${"%+.1f".format(rgDb)} dB",
                style = MaterialTheme.typography.bodySmall,
            )
            Slider(
                value = rgDb,
                onValueChange = { v ->
                    AudioSettings.setRgPregainDb(ctx, v)
                    rgDb = AudioSettings.rgPregainDb(ctx)
                    sendReplayGain()
                },
                valueRange = -12f..12f,
                steps = 48,
                enabled = rgOn,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Trim global aplicado en la cadena DSP (preamp); los tags " +
                    "por pista llegan en sprints posteriores.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }


    /**
     * Block 10 headset screen: connection behaviour + button mapping. Values
     * are read by the service at event time (plug/BT broadcasts, media keys).
     */
    @Composable
internal fun MainActivity.HeadsetSettingsScreen() {
        val ctx = this@HeadsetSettingsScreen
        var pauseOnDisconnect by remember { mutableStateOf(AudioSettings.pauseOnDisconnect(ctx)) }
        var resumeWired by remember { mutableStateOf(AudioSettings.resumeOnWired(ctx)) }
        var resumeBt by remember { mutableStateOf(AudioSettings.resumeOnBluetooth(ctx)) }
        var respond by remember { mutableStateOf(AudioSettings.respondToButtons(ctx)) }
        var wired by remember { mutableStateOf(AudioSettings.wiredAction(ctx)) }
        var bt by remember { mutableStateOf(AudioSettings.btAction(ctx)) }

        Column(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
            Text("Conexión", style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = pauseOnDisconnect, onCheckedChange = { on ->
                    AudioSettings.setPauseOnDisconnect(ctx, on)
                    pauseOnDisconnect = on
                })
                Text("  Pausar al desconectar", style = MaterialTheme.typography.bodyLarge)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = resumeWired, onCheckedChange = { on ->
                    AudioSettings.setResumeOnWired(ctx, on)
                    resumeWired = on
                })
                Text("  Reanudar al enchufar Jack", style = MaterialTheme.typography.bodyLarge)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = resumeBt, onCheckedChange = { on ->
                    AudioSettings.setResumeOnBluetooth(ctx, on)
                    resumeBt = on
                })
                Text("  Reanudar al conectar Bluetooth", style = MaterialTheme.typography.bodyLarge)
            }

            Spacer(Modifier.height(12.dp))
            Text("Botones", style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = respond, onCheckedChange = { on ->
                    AudioSettings.setRespondToButtons(ctx, on)
                    respond = on
                })
                Text("  Responder a botones", style = MaterialTheme.typography.bodyLarge)
            }
            if (!respond) {
                Text(
                    "Las pulsaciones del auricular se ignoran por completo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            Text(
                "Pulsación simple (Jack):",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 6.dp),
            )
            Row {
                listOf(
                    "play_pause" to "⏯", "next" to "⏭", "prev" to "⏮",
                ).forEach { (action, glyph) ->
                    TextButton(onClick = {
                        AudioSettings.setWiredAction(ctx, action)
                        wired = action
                    }) {
                        Text(glyph + if (wired == action) " ✓" else "")
                    }
                }
            }
            Text(
                "Pulsación simple (Bluetooth):",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 6.dp),
            )
            Row {
                listOf(
                    "play_pause" to "⏯", "next" to "⏭", "prev" to "⏮",
                ).forEach { (action, glyph) ->
                    TextButton(onClick = {
                        AudioSettings.setBtAction(ctx, action)
                        bt = action
                    }) {
                        Text(glyph + if (bt == action) " ✓" else "")
                    }
                }
            }
            Text(
                "Doble: siguiente · Triple: anterior · Mantener: siguiente.\n" +
                    "Si ambos están conectados manda la acción del Jack.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
