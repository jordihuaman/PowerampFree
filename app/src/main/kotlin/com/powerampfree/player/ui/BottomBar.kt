package com.powerampfree.player.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.powerampfree.player.MainActivity
import kotlinx.coroutines.delay

/**
 * Barra inferior al estilo de la referencia visual (docs/UI_SPEC §2): pastilla
 * #181818 con 4 tabs vectoriales (Library / Equ-Effects / Search / Main Menu)
 * dibujados con Canvas — sin recursos ni iconos de la app de referencia.
 * En pantallas con mini-player la pastilla crece: seekbar fina + fila de
 * miniatura/título/play encima de los tabs. El tab del reproductor NO existe:
 * se abre tocando el mini-player (idéntico a la referencia).
 */
internal enum class PafTab(val desc: String) {
    LIBRARY("Library"),
    EQ("Equ/Effects"),
    SEARCH("Search"),
    MENU("Main Menu"),
    PLAYER("Player"),
    QUEUE("Queue"),
}

internal enum class PafIconKind {
    Grid, Bars, Magnifier, Burger, Play, Pause, QueueList, Gear, Info, Folder, Person,
    Shuffle, Down, Note, Repeat, Prev, Next, ThumbUp, ThumbDown, TextLines, Dots, Clock,
    Tone, Limit, Headphones, Lock, Trash, Plus,
}

private val NAV_TABS = listOf(PafTab.LIBRARY, PafTab.EQ, PafTab.SEARCH, PafTab.MENU)

/**
 * Iconos vectoriales originales (trazo fino, esquinas redondeadas — el look
 * de la referencia viene de esta estética, no de assets copiados).
 */
@Composable
internal fun PafIcon(kind: PafIconKind, tint: Color, size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        val sw = s * 0.085f
        when (kind) {
            PafIconKind.Folder -> {
                drawRoundRect(
                    tint,
                    topLeft = Offset(s * 0.10f, s * 0.24f),
                    size = Size(s * 0.36f, s * 0.18f),
                    cornerRadius = CornerRadius(s * 0.05f),
                )
                drawRoundRect(
                    tint,
                    topLeft = Offset(s * 0.10f, s * 0.36f),
                    size = Size(s * 0.80f, s * 0.44f),
                    cornerRadius = CornerRadius(s * 0.06f),
                )
            }
            PafIconKind.Person -> {
                drawCircle(tint, radius = s * 0.17f, center = Offset(s * 0.5f, s * 0.32f))
                drawRoundRect(
                    tint,
                    topLeft = Offset(s * 0.24f, s * 0.55f),
                    size = Size(s * 0.52f, s * 0.34f),
                    cornerRadius = CornerRadius(s * 0.17f),
                )
            }
            PafIconKind.Shuffle -> {
                drawLine(
                    tint, start = Offset(s * 0.14f, s * 0.30f), end = Offset(s * 0.55f, s * 0.70f),
                    strokeWidth = sw, cap = StrokeCap.Round,
                )
                drawLine(
                    tint, start = Offset(s * 0.14f, s * 0.70f), end = Offset(s * 0.55f, s * 0.30f),
                    strokeWidth = sw, cap = StrokeCap.Round,
                )
                // puntas de flecha
                val head = 0.16f
                drawRoundRect(tint, topLeft = Offset(s * 0.68f, s * 0.60f), size = Size(s * 0.16f, s * 0.22f), cornerRadius = CornerRadius(s * 0.04f))
                drawRoundRect(tint, topLeft = Offset(s * 0.68f, s * 0.18f), size = Size(s * 0.16f, s * 0.22f), cornerRadius = CornerRadius(s * 0.04f))
            }
            PafIconKind.Down -> {
                drawLine(tint, Offset(s * 0.5f, s * 0.18f), Offset(s * 0.5f, s * 0.62f), sw * 1.2f, StrokeCap.Round)
                drawLine(tint, Offset(s * 0.32f, s * 0.46f), Offset(s * 0.5f, s * 0.64f), sw * 1.2f, StrokeCap.Round)
                drawLine(tint, Offset(s * 0.68f, s * 0.46f), Offset(s * 0.5f, s * 0.64f), sw * 1.2f, StrokeCap.Round)
                drawLine(tint, Offset(s * 0.26f, s * 0.82f), Offset(s * 0.74f, s * 0.82f), sw, StrokeCap.Round)
            }
            PafIconKind.Note -> {
                drawCircle(tint, radius = s * 0.15f, center = Offset(s * 0.44f, s * 0.68f))
                drawLine(tint, Offset(s * 0.57f, s * 0.66f), Offset(s * 0.57f, s * 0.18f), sw * 1.2f, StrokeCap.Round)
                drawRoundRect(tint, topLeft = Offset(s * 0.55f, s * 0.16f), size = Size(s * 0.22f, s * 0.12f), cornerRadius = CornerRadius(s * 0.03f))
            }
            PafIconKind.Grid -> {
                val cell = s * 0.36f
                for (i in 0..1) for (j in 0..1) {
                    drawRoundRect(
                        tint,
                        topLeft = Offset(i * (s - cell), j * (s - cell)),
                        size = Size(cell, cell),
                        cornerRadius = CornerRadius(s * 0.07f),
                    )
                }
            }
            PafIconKind.Bars -> {
                val w = s * 0.16f
                val xs = listOf(0.06f, 0.42f, 0.78f)
                val hs = listOf(0.42f, 1.0f, 0.66f)
                xs.forEachIndexed { i, x ->
                    val h = s * hs[i]
                    drawRoundRect(
                        tint,
                        topLeft = Offset(s * x, s - h),
                        size = Size(w, h),
                        cornerRadius = CornerRadius(w / 2),
                    )
                }
            }
            PafIconKind.Magnifier -> {
                drawCircle(tint, radius = s * 0.26f, center = Offset(s * 0.40f, s * 0.40f), style = Stroke(sw))
                drawLine(
                    tint,
                    start = Offset(s * 0.60f, s * 0.60f),
                    end = Offset(s * 0.86f, s * 0.86f),
                    strokeWidth = sw,
                    cap = StrokeCap.Round,
                )
            }
            PafIconKind.Burger -> {
                listOf(0.28f, 0.50f, 0.72f).forEach { y ->
                    drawLine(
                        tint,
                        start = Offset(s * 0.14f, s * y),
                        end = Offset(s * 0.86f, s * y),
                        strokeWidth = sw * 1.1f,
                        cap = StrokeCap.Round,
                    )
                }
            }
            PafIconKind.Play -> {
                val p = androidx.compose.ui.graphics.Path().apply {
                    moveTo(s * 0.26f, s * 0.14f)
                    lineTo(s * 0.84f, s * 0.50f)
                    lineTo(s * 0.26f, s * 0.86f)
                    close()
                }
                drawPath(p, tint)
            }
            PafIconKind.Pause -> {
                listOf(0.20f, 0.58f).forEach { x ->
                    drawRoundRect(
                        tint,
                        topLeft = Offset(s * x, s * 0.16f),
                        size = Size(s * 0.22f, s * 0.68f),
                        cornerRadius = CornerRadius(s * 0.06f),
                    )
                }
            }
            PafIconKind.QueueList -> {
                listOf(0.55f, 0.75f, 0.40f).forEachIndexed { i, w ->
                    val y = s * (0.26f + i * 0.24f)
                    drawLine(tint, Offset(s * 0.14f, y), Offset(s * (0.14f + w), y), sw, StrokeCap.Round)
                }
            }
            PafIconKind.Gear -> {
                drawCircle(tint, radius = s * 0.22f, style = Stroke(sw))
                for (a in 0 until 8) {
                    val ang = a * Math.PI / 4
                    drawLine(
                        tint,
                        start = Offset(
                            (s * 0.5 + s * 0.30 * Math.cos(ang)).toFloat(),
                            (s * 0.5 + s * 0.30 * Math.sin(ang)).toFloat(),
                        ),
                        end = Offset(
                            (s * 0.5 + s * 0.44 * Math.cos(ang)).toFloat(),
                            (s * 0.5 + s * 0.44 * Math.sin(ang)).toFloat(),
                        ),
                        strokeWidth = sw,
                        cap = StrokeCap.Round,
                    )
                }
            }
            PafIconKind.Info -> {
                drawCircle(tint, radius = s * 0.40f, style = Stroke(sw))
                drawCircle(tint, radius = sw * 0.7f, center = Offset(s * 0.5f, s * 0.32f))
                drawLine(tint, Offset(s * 0.5f, s * 0.48f), Offset(s * 0.5f, s * 0.72f), sw, StrokeCap.Round)
            }
            PafIconKind.Repeat -> {
                drawLine(tint, Offset(s * 0.18f, s * 0.34f), Offset(s * 0.78f, s * 0.34f), sw, StrokeCap.Round)
                drawLine(tint, Offset(s * 0.22f, s * 0.66f), Offset(s * 0.82f, s * 0.66f), sw, StrokeCap.Round)
                // puntas
                drawLine(tint, Offset(s * 0.66f, s * 0.20f), Offset(s * 0.82f, s * 0.34f), sw, StrokeCap.Round)
                drawLine(tint, Offset(s * 0.66f, s * 0.48f), Offset(s * 0.82f, s * 0.34f), sw, StrokeCap.Round)
                drawLine(tint, Offset(s * 0.34f, s * 0.80f), Offset(s * 0.18f, s * 0.66f), sw, StrokeCap.Round)
                drawLine(tint, Offset(s * 0.34f, s * 0.52f), Offset(s * 0.18f, s * 0.66f), sw, StrokeCap.Round)
            }
            PafIconKind.Prev, PafIconKind.Next -> {
                val next = kind == PafIconKind.Next
                val barX = if (next) s * 0.80f else s * 0.20f
                drawLine(tint, Offset(barX, s * 0.20f), Offset(barX, s * 0.80f), sw * 1.4f, StrokeCap.Round)
                val p = androidx.compose.ui.graphics.Path().apply {
                    if (next) {
                        moveTo(s * 0.20f, s * 0.20f); lineTo(s * 0.68f, s * 0.50f); lineTo(s * 0.20f, s * 0.80f)
                    } else {
                        moveTo(s * 0.80f, s * 0.20f); lineTo(s * 0.32f, s * 0.50f); lineTo(s * 0.80f, s * 0.80f)
                    }
                    close()
                }
                drawPath(p, tint)
            }
            PafIconKind.ThumbUp, PafIconKind.ThumbDown -> {
                val down = kind == PafIconKind.ThumbDown
                fun y(v: Float) = if (down) s * (1f - v) else s * v
                val p = androidx.compose.ui.graphics.Path().apply {
                    moveTo(s * 0.18f, y(0.44f))
                    lineTo(s * 0.34f, y(0.44f))
                    lineTo(s * 0.44f, y(0.20f))
                    lineTo(s * 0.60f, y(0.26f))
                    lineTo(s * 0.56f, y(0.44f))
                    lineTo(s * 0.84f, y(0.44f))
                    lineTo(s * 0.78f, y(0.80f))
                    lineTo(s * 0.18f, y(0.80f))
                    close()
                }
                drawPath(p, tint, style = Stroke(sw))
            }
            PafIconKind.TextLines -> {
                val ws = listOf(0.72f, 0.52f, 0.62f)
                ws.forEachIndexed { i, w ->
                    val yy = s * (0.30f + i * 0.20f)
                    drawLine(tint, Offset(s * 0.16f, yy), Offset(s * (0.16f + w), yy), sw, StrokeCap.Round)
                }
            }
            PafIconKind.Dots -> {
                listOf(0.30f, 0.50f, 0.70f).forEach { yy ->
                    drawCircle(tint, radius = sw * 0.72f, center = Offset(s * 0.5f, s * yy))
                }
            }
            PafIconKind.Clock -> {
                drawCircle(tint, radius = s * 0.38f, style = Stroke(sw))
                drawLine(tint, Offset(s * 0.5f, s * 0.28f), Offset(s * 0.5f, s * 0.52f), sw, StrokeCap.Round)
                drawLine(tint, Offset(s * 0.5f, s * 0.52f), Offset(s * 0.66f, s * 0.62f), sw, StrokeCap.Round)
            }
            PafIconKind.Tone -> {
                drawCircle(tint, radius = s * 0.36f, style = Stroke(sw))
                drawCircle(tint, radius = s * 0.10f, center = Offset(s * 0.5f, s * 0.5f))
            }
            PafIconKind.Limit -> {
                drawCircle(tint, radius = s * 0.38f, style = Stroke(sw))
                drawCircle(tint, radius = s * 0.20f, style = Stroke(sw))
            }
            PafIconKind.Headphones -> {
                drawArc(
                    tint, startAngle = 180f, sweepAngle = 180f, useCenter = false,
                    topLeft = Offset(s * 0.16f, s * 0.22f),
                    size = Size(s * 0.68f, s * 0.56f),
                    style = Stroke(sw, cap = StrokeCap.Round),
                )
                drawRoundRect(
                    tint, topLeft = Offset(s * 0.14f, s * 0.50f),
                    size = Size(s * 0.16f, s * 0.30f), cornerRadius = CornerRadius(s * 0.07f),
                )
                drawRoundRect(
                    tint, topLeft = Offset(s * 0.70f, s * 0.50f),
                    size = Size(s * 0.16f, s * 0.30f), cornerRadius = CornerRadius(s * 0.07f),
                )
            }
            PafIconKind.Lock -> {
                drawArc(
                    tint, startAngle = 180f, sweepAngle = 180f, useCenter = false,
                    topLeft = Offset(s * 0.30f, s * 0.16f),
                    size = Size(s * 0.40f, s * 0.40f),
                    style = Stroke(sw, cap = StrokeCap.Round),
                )
                drawRoundRect(
                    tint, topLeft = Offset(s * 0.20f, s * 0.42f),
                    size = Size(s * 0.60f, s * 0.40f), cornerRadius = CornerRadius(s * 0.08f),
                )
            }
            PafIconKind.Trash -> {
                drawLine(tint, Offset(s * 0.16f, s * 0.28f), Offset(s * 0.84f, s * 0.28f), sw, StrokeCap.Round)
                drawLine(tint, Offset(s * 0.38f, s * 0.18f), Offset(s * 0.62f, s * 0.18f), sw, StrokeCap.Round)
                drawRoundRect(
                    tint, topLeft = Offset(s * 0.24f, s * 0.34f),
                    size = Size(s * 0.52f, s * 0.48f), cornerRadius = CornerRadius(s * 0.08f),
                    style = Stroke(sw),
                )
            }
            PafIconKind.Plus -> {
                drawLine(tint, Offset(s * 0.5f, s * 0.20f), Offset(s * 0.5f, s * 0.80f), sw * 1.2f, StrokeCap.Round)
                drawLine(tint, Offset(s * 0.20f, s * 0.5f), Offset(s * 0.80f, s * 0.5f), sw * 1.2f, StrokeCap.Round)
            }
        }
    }
}

/**
 * Pastilla inferior completa. Los overlays (menú principal) muestran mini+tabs
 * como la referencia (§7); en el player solo los tabs.
 */
@Composable
internal fun MainActivity.PafBottomBar(
    tab: PafTab,
    onTab: (PafTab) -> Unit,
    tabOnly: Boolean,
    snap: PlayerSnapshot? = null,
    onOpenPlayer: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .navigationBarsPadding()
            .clip(RoundedCornerShape(50))
            .background(Paf.Surf2),
    ) {
        if (!tabOnly && snap != null) {
            PafSeekBand()
            PafMiniRow(snap, onOpenPlayer)
        }
        Row(modifier = Modifier.fillMaxWidth().height(52.dp)) {
            NAV_TABS.forEach { t ->
                val selected = tab == t
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onTab(t) }
                        .semantics { contentDescription = t.desc },
                    contentAlignment = Alignment.Center,
                ) {
                    PafIcon(
                        kind = when (t) {
                            PafTab.LIBRARY -> PafIconKind.Grid
                            PafTab.EQ -> PafIconKind.Bars
                            PafTab.SEARCH -> PafIconKind.Magnifier
                            PafTab.MENU -> PafIconKind.Burger
                            else -> PafIconKind.Grid
                        },
                        tint = if (selected) Paf.Text else Paf.Text3,
                        size = 26.dp,
                    )
                }
            }
        }
    }
}

/** Seekbar fina con progreso en vivo y drag para buscar (UI_SPEC §2.2). */
@Composable
internal fun MainActivity.PafSeekBand() {
    var posMs by remember { mutableStateOf(0L) }
    var durMs by remember { mutableStateOf(0L) }
    var drag by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            val c = controller
            if (c != null) {
                posMs = c.currentPosition.coerceAtLeast(0L)
                durMs = c.duration.coerceAtLeast(0L)
            }
            delay(300)
        }
    }
    val frac = if (drag != null) drag!! else {
        if (durMs > 0) (posMs.toDouble() / durMs).toFloat().coerceIn(0f, 1f) else 0f
    }
    Slider(
        value = frac.coerceIn(0f, 1f),
        onValueChange = { drag = it },
        onValueChangeFinished = {
            if (durMs > 0 && drag != null) {
                controller?.seekTo((drag!! * durMs).toLong())
            }
            drag = null
        },
        colors = SliderDefaults.colors(
            thumbColor = Paf.Text,
            activeTrackColor = Paf.Text,
            inactiveTrackColor = Paf.Surf4,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(22.dp),
    )
}

/** Fila del mini-player: miniatura 36 dp + título/artista + play (§2.2). */
@Composable
internal fun MainActivity.PafMiniRow(snap: PlayerSnapshot, onOpen: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clickable(onClick = onOpen)
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(Paf.Surf3, RoundedCornerShape(6.dp))
                .clip(RoundedCornerShape(6.dp)),
        ) {
            AlbumArtImage(
                snap = snap,
                px = with(density) { 36.dp.toPx().toInt() },
                modifier = Modifier.size(36.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                snap.title,
                style = MaterialTheme.typography.labelLarge,
                color = Paf.Text,
                maxLines = 1,
                modifier = Modifier.clipToBounds().basicMarquee(),
            )
            if (snap.artist.isNotEmpty()) {
                Text(
                    artistLabel(snap.artist),
                    style = MaterialTheme.typography.labelMedium,
                    color = Paf.Text3,
                    maxLines = 1,
                )
            }
        }
        Box(
            modifier = Modifier
                .size(46.dp)
                .clickable { togglePlayPause() },
            contentAlignment = Alignment.Center,
        ) {
            PafIcon(
                kind = if (snap.isPlaying) PafIconKind.Pause else PafIconKind.Play,
                tint = Paf.Text,
                size = 26.dp,
            )
        }
    }
}
