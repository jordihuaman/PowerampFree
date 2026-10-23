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

    // ------------------------------ album art + background

    /**
     * Art tile resolved from [PlayerSnapshot] cues (MediaStore albumart
     * provider → sibling cover files); generic glyph when nothing found.
     */
    @Composable
internal fun MainActivity.AlbumArtImage(snap: PlayerSnapshot, px: Int, modifier: Modifier) {
        var bmp by remember(snap.albumId, snap.uri) {
            mutableStateOf<android.graphics.Bitmap?>(null)
        }
        androidx.compose.runtime.LaunchedEffect(snap.albumId, snap.uri) {
            val cap = if (ArtSettings.highResArt(this@AlbumArtImage)) 1024 else 512
            bmp = withContext(Dispatchers.IO) {
                AlbumArtLoader.load(
                    this@AlbumArtImage, snap.albumId, snap.uri, snap.folder, minOf(px, cap),
                )
            }
        }
        val b = bmp
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (b != null) {
                androidx.compose.foundation.Image(
                    bitmap = b.asImageBitmap(),
                    contentDescription = "Carátula",
                    contentScale = if (ArtSettings.keepAspect(this@AlbumArtImage)) {
                        ContentScale.Fit
                    } else {
                        ContentScale.Crop
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text("🎵", style = MaterialTheme.typography.titleLarge)
            }
        }
    }


    /**
     * Block 13: art-derived player background — blurred art + bottom gradient.
     * Plain surface when disabled or no art (no decorative fake gradients).
     */
    @Composable
internal fun MainActivity.PlayerBackground(snap: PlayerSnapshot, modifier: Modifier) {
        if (!ArtSettings.bgEnabled(this@PlayerBackground)) {
            Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface))
            return
        }
        var bmp by remember(snap.albumId, snap.uri) {
            mutableStateOf<android.graphics.Bitmap?>(null)
        }
        androidx.compose.runtime.LaunchedEffect(snap.albumId, snap.uri) {
            // Sharp art when the blur slider is 0 (256px upscale would look
            // blurry anyway); small decode for the blurred path.
            val b = ArtSettings.bgBlur(this@PlayerBackground)
            bmp = withContext(Dispatchers.IO) {
                AlbumArtLoader.load(
                    this@PlayerBackground, snap.albumId, snap.uri, snap.folder,
                    if (b == 0) 1024 else 256,
                )
            }
        }
        val gradient = ArtSettings.bgGradient(this@PlayerBackground) / 10f
        if (bmp == null) {
            Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface))
            return
        }
        val scrim = Modifier.fillMaxSize().background(
            androidx.compose.ui.graphics.Brush.verticalGradient(
                0f to androidx.compose.ui.graphics.Color.Transparent,
                1f to androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.25f + 0.6f * gradient),
            ),
        )
        val blur = ArtSettings.bgBlur(this@PlayerBackground)
        val base = Modifier.fillMaxSize()
        Box(modifier.fillMaxSize()) {
            androidx.compose.foundation.Image(
                bitmap = bmp!!.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = if (android.os.Build.VERSION.SDK_INT >= 31 && blur > 0) {
                    base.blur((blur * 6).dp)
                } else {
                    base
                },
            )
            Box(scrim)
        }
    }


    // -------------------------------------------------------- mini player

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
internal fun MainActivity.MiniPlayerBar(snap: PlayerSnapshot, onOpen: () -> Unit) {
        Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpen)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AlbumArtImage(
                    snap = snap,
                    px = 48,
                    modifier = Modifier.size(44.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        snap.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(),
                    )
                    if (snap.artist.isNotEmpty()) {
                        Text(
                            snap.artist,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 1,
                        )
                    }
                }
                androidx.compose.material3.TextButton(onClick = { togglePlayPause() }) {
                    Text(if (snap.isPlaying) "⏸" else "▶")
                }
                androidx.compose.material3.TextButton(onClick = { sendSkip(true) }) { Text("⏭") }
            }
        }
    }


    // -------------------------------------------------------- player tab

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
internal fun MainActivity.PlayerTab(snap: PlayerSnapshot, onSettings: () -> Unit, onLyrics: () -> Unit) {
        val isPlaying = snap.isPlaying

        // player-screen controls state
        var repeatS by remember { mutableStateOf(PlaybackSettings.repeatMode(this)) }
        var shuffleS by remember { mutableStateOf(PlaybackSettings.shuffleEnabled(this)) }
        var ratTick by remember { mutableStateOf(0) }
        var sleepEndMs by remember { mutableStateOf(0L) }
        var nowTick by remember { mutableStateOf(System.currentTimeMillis()) }
        var showSleep by remember { mutableStateOf(false) }
        var vizOn by remember { mutableStateOf(VizSettings.playerSpectrum(this)) }

        // sleep timer countdown → auto pause
        androidx.compose.runtime.LaunchedEffect(sleepEndMs) {
            while (sleepEndMs > 0L && currentCoroutineContext().isActive) {
                nowTick = System.currentTimeMillis()
                if (nowTick >= sleepEndMs) {
                    controller?.pause()
                    sleepEndMs = 0L
                } else {
                    delay(1000)
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            PlayerBackground(snap, Modifier)
            Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        snap.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(),
                    )
                    if (snap.format.isNotEmpty()) {
                        Text(
                            snap.format,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    androidx.compose.material3.TextButton(onClick = onSettings) { Text("⚙") }
                    androidx.compose.material3.TextButton(onClick = onLyrics) { Text("📝") }
                    // Block 14: bars button toggles the on-player visualization.
                    androidx.compose.material3.TextButton(onClick = {
                        val on = !vizOn
                        vizOn = on
                        VizSettings.setPlayerSpectrum(this@PlayerTab, on)
                    }) {
                        Text(
                            "📊",
                            color = if (vizOn) Color(0xFF39A7E4) else LocalContentColor.current,
                        )
                    }
                    androidx.compose.material3.TextButton(onClick = { sendSkip(false) }) { Text("⏮") }
                    Button(onClick = { togglePlayPause() }) { Text(if (isPlaying) "⏸" else "▶") }
                    androidx.compose.material3.TextButton(onClick = { sendSkip(true) }) { Text("⏭") }
                }
            }
            // art tile row: own row, no nested weight chains
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                AlbumArtImage(snap = snap, px = 512, modifier = Modifier.size(160.dp))
            }
            // ---- controls row: shuffle / repeat / sleep / ±10 s / rating
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val accent = Color(0xFF39A7E4)
                androidx.compose.material3.TextButton(onClick = {
                    shuffleS = !shuffleS
                    sendShuffle(shuffleS)
                }) {
                    Text(
                        "🔀", color = if (shuffleS) accent else LocalContentColor.current,
                    )
                }
                androidx.compose.material3.TextButton(onClick = {
                    repeatS = when (repeatS) {
                        PlaybackSettings.REPEAT_OFF -> PlaybackSettings.REPEAT_ALL
                        PlaybackSettings.REPEAT_ALL -> PlaybackSettings.REPEAT_ONE
                        else -> PlaybackSettings.REPEAT_OFF
                    }
                    sendRepeat(repeatS)
                }) {
                    val label = when (repeatS) {
                        PlaybackSettings.REPEAT_ONE -> "🔁1"
                        PlaybackSettings.REPEAT_ALL -> "🔁∞"
                        else -> "🔁"
                    }
                    Text(label, color = if (repeatS != PlaybackSettings.REPEAT_OFF) accent else LocalContentColor.current)
                }
                androidx.compose.material3.TextButton(onClick = { showSleep = true }) {
                    Text(
                        if (sleepEndMs > 0L) "⏰${fmtClock(sleepEndMs - nowTick)}" else "⏰",
                        color = if (sleepEndMs > 0L) accent else LocalContentColor.current,
                    )
                }
                androidx.compose.material3.TextButton(onClick = { nudgePosition(-10_000L) }) { Text("−10s") }
                androidx.compose.material3.TextButton(onClick = { nudgePosition(10_000L) }) { Text("+10s") }
                val rated = if (ratTick >= 0) RatingsStore.rating(this@PlayerTab, snap.uri) else 0
                androidx.compose.material3.TextButton(onClick = {
                    RatingsStore.toggle(this@PlayerTab, snap.uri, 1)
                    ratTick++
                }) {
                    Text("👍", color = if (rated == 1) accent else LocalContentColor.current)
                }
                androidx.compose.material3.TextButton(onClick = {
                    RatingsStore.toggle(this@PlayerTab, snap.uri, -1)
                    ratTick++
                }) {
                    Text("👎", color = if (rated == -1) Color(0xFFE4394E) else LocalContentColor.current)
                }
            }
            if (vizOn) SpectrumBars(isPlaying = isPlaying)
            }
        }

        if (showSleep) {
            AlertDialog(
                onDismissRequest = { showSleep = false },
                title = { Text("Temporizador de sueño") },
                text = {
                    Column {
                        Text(
                            if (sleepEndMs > 0L) {
                                "Activo · pausa en ${fmtClock(sleepEndMs - nowTick)}"
                            } else {
                                "Pausa la reproducción automáticamente cuando acabe la cuenta."
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(5, 15, 30, 60, 90).forEach { min ->
                                androidx.compose.material3.OutlinedButton(onClick = {
                                    sleepEndMs = System.currentTimeMillis() + min * 60_000L
                                    nowTick = System.currentTimeMillis()
                                    showSleep = false
                                }) { Text("${min}m") }
                            }
                        }
                    }
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = { showSleep = false }) {
                        Text("Cerrar")
                    }
                },
                dismissButton = {
                    if (sleepEndMs > 0L) {
                        androidx.compose.material3.TextButton(onClick = {
                            sleepEndMs = 0L
                        }) { Text("Cancelar temporizador") }
                    }
                },
            )
        }
    }
