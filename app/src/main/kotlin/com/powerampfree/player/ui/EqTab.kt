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

    // ------------------------------------------------------------- eq tab

    /**
     * EQ screen (block 16): EQ / Tone / Limit tabs over the same resolved
     * preset. The graphic layer is 10 fixed bands of the parametric engine;
     * Tone knobs are two real shelf bands; the limiter flag toggles the
     * SoftLimiter module. Everything lives in [EqPreset] and is pushed live
     * via the session command.
     */
    @Composable
internal fun MainActivity.EqTab() {
        var updatingPreset by remember { mutableStateOf(PresetStore.loadCurrent(this)) }
        var crossfadeS by remember { mutableStateOf(PlaybackSettings.crossfadeMs(this).toInt() / 1000) }
        var savedPresets by remember { mutableStateOf(PresetStore.listNamed(this)) }
        var presetName by remember { mutableStateOf("MyPreset") }
        var page by remember { mutableStateOf(0) }

        fun update(p: EqPreset) {
            updatingPreset = p
            sendPreset(p)
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            EqStatusChip(updatingPreset)
            Spacer(Modifier.height(6.dp))
            TabRow(selectedTabIndex = page) {
                Tab(selected = page == 0, onClick = { page = 0 }, text = { Text("🎛 EQ") })
                Tab(selected = page == 1, onClick = { page = 1 }, text = { Text("💿 Tone") })
                Tab(selected = page == 2, onClick = { page = 2 }, text = { Text("🔊 Limit") })
            }
            when (page) {
                0 -> {
                    // --- faders: preamp + 10 graphic bands -----------------
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            VSlider(
                                value = updatingPreset.preampDb,
                                onValueChange = { v -> update(updatingPreset.copy(preampDb = v)) },
                                valueRange = -12f..12f,
                                modifier = Modifier
                                    .width(46.dp)
                                    .height(240.dp)
                                    .padding(3.dp),
                            )
                            Text("Pre", style = MaterialTheme.typography.labelSmall)
                            Text(
                                "%.1f".format(updatingPreset.preampDb),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        updatingPreset.graphicGainsDb.forEachIndexed { i, g ->
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                VSlider(
                                    value = g,
                                    onValueChange = { v ->
                                        val q = (v * 2).toInt() / 2f // 0.5 dB steps
                                        val gains = updatingPreset.graphicGainsDb
                                            .toMutableList()
                                            .apply { set(i, q) }
                                        update(updatingPreset.copy(graphicGainsDb = gains, graphicOn = true))
                                    },
                                    valueRange = -12f..12f,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(240.dp)
                                        .padding(2.dp),
                                )
                                Text(
                                    EqPreset.GRAPHIC_LABELS.getOrElse(i) { "$i" },
                                    style = MaterialTheme.typography.labelSmall,
                                )
                                Text(
                                    "%.1f".format(g.coerceIn(-12f, 12f)),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    EqCurve(updatingPreset)
                    // Block 14: "Equalizer Screen Spectrum" (Disabled/Classic/Rounded).
                    if (VizSettings.eqSpectrum(this@EqTab) != VizSettings.EQ_DISABLED) {
                        SpectrumBars(isPlaying = controller?.isPlaying == true)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = updatingPreset.graphicOn,
                            onCheckedChange = { on -> update(updatingPreset.copy(graphicOn = on)) },
                        )
                        Text("  EQ activo", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                1 -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = updatingPreset.toneOn,
                            onCheckedChange = { on -> update(updatingPreset.copy(toneOn = on)) },
                        )
                        Text("  Tone activo", style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Knob(
                            label = "Bass",
                            value = updatingPreset.toneBassPct,
                            onValueChange = { pct ->
                                update(updatingPreset.copy(toneBassPct = pct, toneOn = true))
                            },
                        )
                        Knob(
                            label = "Treble",
                            value = updatingPreset.toneTreblePct,
                            onValueChange = { pct ->
                                update(updatingPreset.copy(toneTreblePct = pct, toneOn = true))
                            },
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Bass: shelf 250 Hz · Treble: shelf 4 kHz · máx +12 dB",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    EqCurve(updatingPreset)
                }
                else -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = updatingPreset.limitOn,
                            onCheckedChange = { on -> update(updatingPreset.copy(limitOn = on)) },
                        )
                        Text("  Limitador activo", style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Limitador suave: garantiza picos ≤ techo (0.98 FS) con " +
                            "release ~80 ms; sin coloración cuando el contenido está bien recortado.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            // --- parametric editor / crossfade / save (EQ ground layer) ------
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item {
                    Text(
                        "Crossfade: ${crossfadeS} s" + if (crossfadeS == 0) " (off)" else "",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Slider(
                        value = crossfadeS.toFloat(),
                        onValueChange = { v ->
                            crossfadeS = v.toInt()
                            sendCrossfade(crossfadeS * 1000L)
                        },
                        valueRange = 0f..12f,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item { Text("Bandas paramétricas:  ${updatingPreset.bands.size}", style = MaterialTheme.typography.bodySmall) }
                itemsIndexed(updatingPreset.bands) { i, band ->
                    BandRow(
                        index = i,
                        band = band,
                        onGain = { g ->
                            update(
                                updatingPreset.copy(
                                    bands = updatingPreset.bands.toMutableList()
                                        .apply { set(i, band.copy(gainDb = g)) },
                                ),
                            )
                        },
                        onRemove = {
                            update(
                                updatingPreset.copy(
                                    bands = updatingPreset.bands.toMutableList().apply { removeAt(i) },
                                ),
                            )
                        },
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            enabled = updatingPreset.bands.size < 31,
                            onClick = {
                                update(
                                    updatingPreset.copy(
                                        bands = updatingPreset.bands +
                                            Band(FilterType.PEAKING, 1000, 0f, 1.0f),
                                    ),
                                )
                            },
                        ) { Text("+ banda") }
                        OutlinedButton(onClick = {
                            update(PresetStore.defaultPreset())
                        }) { Text("Reset") }
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = presetName,
                            onValueChange = { presetName = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                        Button(onClick = {
                            PresetStore.saveNamed(this@EqTab, updatingPreset.copy(name = presetName))
                            savedPresets = PresetStore.listNamed(this@EqTab)
                        }) { Text("Guardar") }
                    }
                    if (savedPresets.isNotEmpty()) {
                        Text("Presets guardados:", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            savedPresets.take(5).forEach { sp ->
                                OutlinedButton(onClick = {
                                    presetName = sp.name
                                    update(sp)
                                }) { Text(sp.name, style = MaterialTheme.typography.labelSmall) }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }


    /** Status chip like the observed one: `NO DVC · EQ n · TON · LMT`. */
    @Composable
internal fun MainActivity.EqStatusChip(preset: EqPreset) {
        val eqCount = preset.bands.size + preset.graphicBandCount
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
                Text("ⓘ ", style = MaterialTheme.typography.labelMedium)
                Text(
                    "NO DVC · EQ ${if (eqCount > 0) "$eqCount" else "OFF"} · " +
                        "TON ${if (preset.toneOn) "ON" else "OFF"} · " +
                        "LMT ${if (preset.limitOn) "ON" else "OFF"}",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }


    @Composable
internal fun MainActivity.EqCurve(preset: EqPreset) {
        // Off-screen engine instance just for the curve (same math as the DSP
        // chain): resolves ALL layers (parametric + graphic + tone shelves).
        val curve = remember(preset) {
            try {
                val eq = ParametricEq.build(ParametricEq.Config.of(preset.resolvedBands()), 48_000, 2)
                eq.responseCurve(120)
            } catch (_: IllegalArgumentException) {
                FloatArray(120)
            }
        }
        Canvas(modifier = Modifier.fillMaxWidth().height(110.dp)) {
            val dbMin = -18f
            val dbMax = +18f
            fun yForDb(db: Float): Float =
                size.height - ((db - dbMin) / (dbMax - dbMin)) * size.height
            drawLine(
                color = Color(0x33888888),
                start = Offset(0f, yForDb(0f)),
                end = Offset(size.width, yForDb(0f)),
            )
            val path = Path()
            curve.forEachIndexed { i, db ->
                val x = size.width * i / (curve.size - 1).coerceAtLeast(1)
                val y = yForDb(db)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, Color(0xFF39A7E4), style = Stroke(4f))
        }
    }


    /**
     * Vertical fader for the graphic EQ (no native vertical Slider in
     * Compose): pointer-driven, tap or drag anywhere on the track, fill grows
     * bottom-up like the reference's lit tracks.
     */
    @Composable
internal fun MainActivity.VSlider(
        value: Float,
        valueRange: ClosedFloatingPointRange<Float>,
        onValueChange: (Float) -> Unit,
        modifier: Modifier = Modifier,
        fillColor: Color = Color(0xFF4CD56A),
    ) {
        val frac =
            ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start))
                .coerceIn(0f, 1f)
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF18251F))
                .pointerInput(valueRange) {
                    fun setAt(y: Float) {
                        val f = (1f - y / size.height.toFloat()).coerceIn(0f, 1f)
                        onValueChange(
                            valueRange.start + f * (valueRange.endInclusive - valueRange.start),
                        )
                    }
                    detectDragGestures { change, _ ->
                        change.consume()
                        setAt(change.position.y)
                    }
                }
                .pointerInput(valueRange) {
                    detectTapGestures { pos ->
                        val f = (1f - pos.y / size.height.toFloat()).coerceIn(0f, 1f)
                        onValueChange(
                            valueRange.start + f * (valueRange.endInclusive - valueRange.start),
                        )
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val fh = size.height * frac
                drawRect(
                    color = fillColor,
                    topLeft = Offset(0f, size.height - fh),
                    size = Size(size.width, fh),
                )
                // 0 dB center marker
                drawLine(
                    color = Color(0x22000000),
                    start = Offset(0f, size.height / 2f),
                    end = Offset(size.width, size.height / 2f),
                    strokeWidth = 2f,
                )
                // handle
                drawLine(
                    color = Color.White,
                    start = Offset(0f, size.height - fh),
                    end = Offset(size.width, size.height - fh),
                    strokeWidth = 3f,
                )
            }
        }
    }


    /**
     * Rotary knob for Tone (Bass/Treble): drag anywhere on the dial; position
     * maps to the 270° sweep (135°→45°, clockwise, matching the arc below).
     */
    @Composable
internal fun MainActivity.Knob(
        label: String,
        value: Float,
        onValueChange: (Float) -> Unit,
        modifier: Modifier = Modifier,
        trackColor: Color = Color(0xFF303030),
        fillColor: Color = Color(0xFF4CD56A),
    ) {
        Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            val frac = (value.coerceIn(0f, 100f)) / 100f
            Canvas(
                modifier = Modifier
                    .size(96.dp)
                    .pointerInput(Unit) {
                        fun onPoint(x: Float, y: Float) {
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            var a = atan2((y - cy).toDouble(), (x - cx).toDouble()) * 180.0 / Math.PI
                            if (a < 0) a += 360.0
                            var rel = a - 135.0
                            if (rel < 0) rel += 360.0
                            if (rel > 270.0) {
                                rel = if (rel < 315.0) 270.0 else 0.0
                            }
                            onValueChange((rel / 270.0).toFloat() * 100f)
                        }
                        detectDragGestures { change, _ ->
                            change.consume()
                            onPoint(change.position.x, change.position.y)
                        }
                    }
                    .pointerInput(Unit) {
                        // Taps need their own detector: drag requires touch slop.
                        fun setAt(x: Float, y: Float) {
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            var a = atan2((y - cy).toDouble(), (x - cx).toDouble()) * 180.0 / Math.PI
                            if (a < 0) a += 360.0
                            var rel = a - 135.0
                            if (rel < 0) rel += 360.0
                            if (rel > 270.0) {
                                rel = if (rel < 315.0) 270.0 else 0.0
                            }
                            onValueChange((rel / 270.0).toFloat() * 100f)
                        }
                        detectTapGestures { pos -> setAt(pos.x, pos.y) }
                    },
            ) {
                val sw = 34f
                val inset = sw
                drawArc(
                    color = trackColor,
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    style = Stroke(width = sw, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - 2 * inset, size.height - 2 * inset),
                )
                drawArc(
                    color = fillColor,
                    startAngle = 135f,
                    sweepAngle = 270f * frac,
                    useCenter = false,
                    style = Stroke(width = sw, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - 2 * inset, size.height - 2 * inset),
                )
                // pointer from center ring to the knob edge
                val ang = Math.toRadians(135.0 + 270.0 * frac)
                val rOuter = size.minDimension / 2f - sw / 2f - 2f
                val rInner = rOuter * 0.6f
                val cx = size.width / 2f
                val cy = size.height / 2f
                drawLine(
                    color = Color.White,
                    start = Offset(cx + rInner * cos(ang).toFloat(), cy + rInner * sin(ang).toFloat()),
                    end = Offset(cx + rOuter * cos(ang).toFloat(), cy + rOuter * sin(ang).toFloat()),
                    strokeWidth = 5f,
                )
            }
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(
                "${value.coerceIn(0f, 100f).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }


    @Composable
internal fun MainActivity.BandRow(
        index: Int,
        band: Band,
        onGain: (Float) -> Unit,
        onRemove: () -> Unit,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "#${index + 1}  ${band.type.name.lowercase().replace('_', ' ')} · ${band.freqHz} Hz · Q${"%.2f".format(band.q)}" +
                        if (!band.enabled) " (off)" else "",
                    style = MaterialTheme.typography.bodySmall,
                )
                Slider(
                    value = band.gainDb.coerceIn(-15f, 15f),
                    onValueChange = onGain,
                    valueRange = -15f..15f,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                "${"%+.1f".format(band.gainDb)} dB",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(end = 4.dp),
            )
            androidx.compose.material3.TextButton(onClick = onRemove) { Text("✕") }
        }
    }
