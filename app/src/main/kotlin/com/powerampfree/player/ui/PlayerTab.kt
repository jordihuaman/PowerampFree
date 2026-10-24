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
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
     * Placeholder de carátula propio: tile oscuro con una silueta de waveform
     * centrada. Se dibuja con Canvas (sin recursos gráficos de terceros) y
     * escala a cualquier tamaño (fila, mini-player, portada grande).
     */
    @Composable
internal fun PafArtPlaceholder(modifier: Modifier = Modifier) {
        Canvas(modifier = modifier.background(Paf.Surf2)) {
            val n = 15
            val left = size.width * 0.18f
            val right = size.width * 0.82f
            val gap = (right - left) / n
            val cy = size.height / 2f
            val maxH = size.height * 0.46f
            for (i in 0 until n) {
                val k = kotlin.math.abs(kotlin.math.sin(i * 1.15 + 0.4)).toFloat()
                val h = maxH * (0.22f + 0.78f * k)
                val w = gap * 0.42f
                drawRoundRect(
                    color = Paf.Surf4,
                    topLeft = Offset(left + i * gap + (gap - w) / 2f, cy - h / 2f),
                    size = Size(w, h),
                    cornerRadius = CornerRadius(w / 2f),
                )
            }
        }
    }


    /**
     * Art tile resolved from [PlayerSnapshot] cues (MediaStore albumart
     * provider → sibling cover files); generic glyph when nothing found.
     */
    @Composable
internal fun MainActivity.AlbumArtImage(
        snap: PlayerSnapshot,
        px: Int,
        modifier: Modifier,
        crop: Boolean = false,
    ) {
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
                    contentScale = if (crop || !ArtSettings.keepAspect(this@AlbumArtImage)) {
                        ContentScale.Crop
                    } else {
                        ContentScale.Fit
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                // Placeholder propio: tile oscuro con silueta de waveform, sin
                // recursos de la app de referencia (clean-room).
                PafArtPlaceholder(Modifier.fillMaxSize())
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


    // -------------------------------------------------------- player tab
    //
    // Restyle §3: arte cuadrado con like/dislike y letras/menú superpuestos,
    // título y artista en pastillas con marquee, chips de modo, waveform con
    // transporte integrado y chip técnico. Todo dibujado con Canvas.

    /** Chip redondo de icono (68×36) con borde #3F3F3F (§3, chips de modo). */
    @Composable
internal fun PafIconChip(kind: PafIconKind, active: Boolean, onClick: () -> Unit) {
        Box(
            modifier = Modifier
                .width(68.dp)
                .height(36.dp)
                .clip(RoundedCornerShape(50))
                .border(1.dp, Paf.Stroke, RoundedCornerShape(50))
                .background(if (active) Paf.Surf2 else Color.Transparent)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            PafIcon(kind, if (active) Paf.Text else Paf.Text3, 18.dp)
        }
    }

    /** Botón circular de icono (like/dislike, letras, menú, transporte). */
    @Composable
internal fun PafRoundButton(
        kind: PafIconKind,
        onClick: () -> Unit,
        size: Dp = 40.dp,
        iconSize: Dp = 18.dp,
        tint: Color = Paf.Text,
        background: Color = Paf.Surf2,
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(background)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            PafIcon(kind, tint, iconSize)
        }
    }

    /** ±10 s: arco con flecha (dibujado) y "10" dentro. */
    @Composable
internal fun PafNudgeButton(forward: Boolean, onClick: () -> Unit) {
        Box(
            modifier = Modifier.size(48.dp).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(42.dp)) {
                val s = this.size.minDimension
                val sw = s * 0.07f
                val r = s * 0.33f
                val cx = s / 2f
                val cy = s * 0.44f
                scale(if (forward) -1f else 1f, 1f, Offset(cx, cy)) {
                    drawArc(
                        Paf.Text,
                        startAngle = 115f,
                        sweepAngle = 245f,
                        useCenter = false,
                        topLeft = Offset(cx - r, cy - r),
                        size = Size(r * 2, r * 2),
                        style = Stroke(sw, cap = StrokeCap.Round),
                    )
                    val ax = cx + r * cos(Math.toRadians(115.0)).toFloat()
                    val ay = cy + r * sin(Math.toRadians(115.0)).toFloat()
                    val head = Path().apply {
                        moveTo(ax - s * 0.02f, ay - s * 0.11f)
                        lineTo(ax + s * 0.09f, ay + s * 0.01f)
                        lineTo(ax - s * 0.06f, ay + s * 0.05f)
                        close()
                    }
                    drawPath(head, Paf.Text)
                }
            }
            Text(
                "10",
                fontFamily = PafFamilyBold,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                color = Paf.Text,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp),
            )
        }
    }

    /** Pastilla de rating (like / dislike, §3). */
    @Composable
internal fun PafRatingPill(kind: PafIconKind, active: Boolean, onClick: () -> Unit) {
        Box(
            modifier = Modifier
                .width(54.dp)
                .height(36.dp)
                .clip(RoundedCornerShape(50))
                .background(Paf.Surf2)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            PafIcon(kind, if (active) Paf.Text else Paf.Text3, 18.dp)
        }
    }

    /**
     * Waveform/seekbar protagonista (§3): barras espejadas, mitad reproducida
     * en blanco y mitad restante en gris; tocar busca. El transporte se pinta
     * encima con fondos negros para que los iconos respiren.
     */
    @Composable
internal fun MainActivity.PafWaveform(
        isPlaying: Boolean,
        posMs: Long,
        durMs: Long,
        onSeek: (Long) -> Unit,
        controls: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
    ) {
        var spectrum by remember { mutableStateOf(FloatArray(0)) }
        androidx.compose.runtime.LaunchedEffect(isPlaying) {
            while (currentCoroutineContext().isActive) {
                spectrum = if (isPlaying) VisualizerTap.spectrum() else FloatArray(0)
                delay(80)
            }
        }
        val frac = if (durMs > 0) (posMs.toFloat() / durMs).coerceIn(0f, 1f) else 0f
        Box(modifier = Modifier.fillMaxWidth().height(170.dp)) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(durMs) {
                        detectTapGestures { off ->
                            if (durMs > 0) {
                                onSeek((off.x / size.width * durMs).toLong())
                            }
                        }
                    },
            ) {
                val n = 56
                val left = size.width * 0.14f
                val right = size.width * 0.86f
                val gap = (right - left) / n
                val cy = size.height / 2f
                val maxH = size.height * 0.88f
                for (i in 0 until n) {
                    val base = 0.22f + 0.62f * kotlin.math.abs(
                        sin(i * 1.71 + 0.35).toFloat(),
                    )
                    val live = if (spectrum.isEmpty()) 0f else {
                        val k = (i * spectrum.size / n).coerceIn(0, spectrum.size - 1)
                        spectrum[k].coerceIn(0f, 1f)
                    }
                    val level = (base * (0.6f + 0.7f * live)).coerceIn(0.08f, 1f)
                    val h = maxH * level
                    val played = (i + 0.5f) / n <= frac
                    drawRoundRect(
                        color = if (played) Paf.Text else Color(0xFF4A4A4A),
                        topLeft = Offset(left + i * gap + gap * 0.22f, cy - h / 2f),
                        size = Size(gap * 0.56f, h),
                        cornerRadius = CornerRadius(gap * 0.28f),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
                content = controls,
            )
        }
    }

    /** Etiqueta condensada de tiempo: `0:08` / `2:00:19` (§3). */
    @Composable
internal fun MainActivity.PafTimeText(ms: Long, modifier: Modifier = Modifier, alignEnd: Boolean = false) {
        Text(
            if (ms <= 0L) "0:00" else fmtDur(ms),
            fontFamily = PafFamilyBold,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = Paf.Text2,
            modifier = modifier,
            textAlign = if (alignEnd) androidx.compose.ui.text.style.TextAlign.End
            else androidx.compose.ui.text.style.TextAlign.Start,
        )
    }

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
        var posMs by remember { mutableStateOf(0L) }
        var durMs by remember { mutableStateOf(0L) }

        androidx.compose.runtime.LaunchedEffect(Unit) {
            while (currentCoroutineContext().isActive) {
                controller?.let { c ->
                    posMs = c.currentPosition.coerceAtLeast(0L)
                    durMs = c.duration.coerceAtLeast(0L)
                }
                delay(250)
            }
        }

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

        val rated = if (ratTick >= 0) RatingsStore.rating(this, snap.uri) else 0

        Box(modifier = Modifier.fillMaxSize().background(Paf.Bg)) {
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                Spacer(Modifier.height(8.dp))
                // ---- arte + acciones superpuestas -------------------------
                Box(modifier = Modifier.fillMaxWidth().height(308.dp)) {
                    AlbumArtImage(snap = snap, px = 1024, modifier = Modifier.fillMaxSize(), crop = true)
                    Row(
                        modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        PafRatingPill(PafIconKind.ThumbUp, rated == 1) {
                            RatingsStore.toggle(this@PlayerTab, snap.uri, 1)
                            ratTick++
                        }
                        PafRatingPill(PafIconKind.ThumbDown, rated == -1) {
                            RatingsStore.toggle(this@PlayerTab, snap.uri, -1)
                            ratTick++
                        }
                    }
                    Row(
                        modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        PafRoundButton(PafIconKind.TextLines, onClick = onLyrics)
                        PafRoundButton(PafIconKind.Dots, onClick = onSettings)
                    }
                }
                Spacer(Modifier.height(8.dp))

                // ---- título / artista en pastillas ------------------------
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(50))
                        .background(Paf.Surf1)
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                ) {
                    Text(
                        snap.title,
                        fontFamily = PafFamilyBold,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Paf.Text,
                        maxLines = 1,
                        modifier = Modifier.clipToBounds().basicMarquee(),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Paf.Surf1)
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text(
                        artistLabel(snap.artist),
                        fontFamily = PafFamily,
                        fontSize = 14.sp,
                        color = Paf.Text3,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.height(10.dp))

                // ---- chips de modo ---------------------------------------
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PafIconChip(PafIconKind.Bars, vizOn) {
                            vizOn = !vizOn
                            VizSettings.setPlayerSpectrum(this@PlayerTab, vizOn)
                        }
                        PafIconChip(PafIconKind.Clock, sleepEndMs > 0L) { showSleep = true }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PafIconChip(
                            PafIconKind.Repeat,
                            repeatS != PlaybackSettings.REPEAT_OFF,
                        ) {
                            repeatS = when (repeatS) {
                                PlaybackSettings.REPEAT_OFF -> PlaybackSettings.REPEAT_ALL
                                PlaybackSettings.REPEAT_ALL -> PlaybackSettings.REPEAT_ONE
                                else -> PlaybackSettings.REPEAT_OFF
                            }
                            sendRepeat(repeatS)
                        }
                        PafIconChip(PafIconKind.Shuffle, shuffleS) {
                            shuffleS = !shuffleS
                            sendShuffle(shuffleS)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))

                // Aire flexible: el transporte baja hacia la zona baja como en
                // la referencia, dejando un margen corto bajo el chip técnico.
                Spacer(Modifier.weight(1.1f))

                // ---- waveform + transporte -------------------------------
                PafWaveform(
                    isPlaying = isPlaying && vizOn,
                    posMs = posMs,
                    durMs = durMs,
                    onSeek = { controller?.seekTo(it) },
                ) {
                    PafNudgeButton(forward = false) { nudgePosition(-10_000L) }
                    PafRoundButton(
                        PafIconKind.Prev,
                        onClick = { sendSkip(false) },
                        size = 48.dp,
                        iconSize = 22.dp,
                        background = Paf.Bg.copy(alpha = 0.86f),
                    )
                    PafRoundButton(
                        if (isPlaying) PafIconKind.Pause else PafIconKind.Play,
                        onClick = { togglePlayPause() },
                        size = 76.dp,
                        iconSize = 34.dp,
                        background = Paf.Bg.copy(alpha = 0.92f),
                    )
                    PafRoundButton(
                        PafIconKind.Next,
                        onClick = { sendSkip(true) },
                        size = 48.dp,
                        iconSize = 22.dp,
                        background = Paf.Bg.copy(alpha = 0.86f),
                    )
                    PafNudgeButton(forward = true) { nudgePosition(10_000L) }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    PafTimeText(posMs, Modifier.weight(1f))
                    PafTimeText(durMs, Modifier.weight(1f), alignEnd = true)
                }
                Spacer(Modifier.height(6.dp))
                if (snap.format.isNotEmpty()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        Text(
                            snap.format.uppercase(),
                            fontFamily = PafFamilyBold,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = Paf.Text3,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Paf.Surf1)
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                }
                Spacer(Modifier.weight(0.45f))
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
