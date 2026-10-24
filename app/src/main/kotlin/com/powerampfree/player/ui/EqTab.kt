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
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.geometry.CornerRadius
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
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

    /** dB con un decimal, sin "-0.0" cuando el valor es cero. */
internal fun fmtDb(v: Float): String =
        "%.1f".format(if (kotlin.math.abs(v) < 0.05f) 0f else v)

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
        var showPresets by remember { mutableStateOf(false) }
        var showOptions by remember { mutableStateOf(false) }

        fun update(p: EqPreset) {
            updatingPreset = p
            sendPreset(p)
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            // Selector segmentado EQ / Tone / Limit (una sola pastilla, §6).
            PafSegmentedControl(
                kinds = listOf(PafIconKind.Bars, PafIconKind.Tone, PafIconKind.Limit),
                selected = page,
                onSelect = { page = it },
            )
            Spacer(Modifier.height(6.dp))
            when (page) {
                0 -> {
                    // --- faders: preamp + 10 graphic bands -----------------
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(Paf.Surf1)
                                .padding(horizontal = 2.dp, vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            VSlider(
                                value = updatingPreset.preampDb,
                                onValueChange = { v -> update(updatingPreset.copy(preampDb = v)) },
                                valueRange = -12f..12f,
                                modifier = Modifier
                                    .width(46.dp)
                                    .height(216.dp)
                                    .padding(3.dp),
                            )
                            Text("Preamp", style = MaterialTheme.typography.labelSmall)
                            Text(
                                fmtDb(updatingPreset.preampDb),
                                style = MaterialTheme.typography.labelSmall,
                                color = Paf.Text3,
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
                                        .height(216.dp)
                                        .padding(2.dp),
                                )
                                Text(
                                    EqPreset.GRAPHIC_LABELS.getOrElse(i) { "$i" },
                                    style = MaterialTheme.typography.labelSmall,
                                )
                                Text(
                                    fmtDb(g.coerceIn(-12f, 12f)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Paf.Text3,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    EqCurvePanel(updatingPreset)
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        EqStatusChip(updatingPreset)
                    }
                    Spacer(Modifier.height(6.dp))
                    // Fila Equ / Preset / ⋯ de la referencia (§6).
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PafActionChip(
                            label = "Equ",
                            icon = PafIconKind.Bars,
                            active = updatingPreset.graphicOn,
                            onClick = {
                                update(updatingPreset.copy(graphicOn = !updatingPreset.graphicOn))
                            },
                        )
                        PafActionChip(
                            label = "Preset",
                            icon = PafIconKind.Note,
                            onClick = { showPresets = true },
                        )
                        Spacer(Modifier.weight(1f))
                        PafRoundButton(
                            kind = PafIconKind.Dots,
                            onClick = { showOptions = true },
                            size = 38.dp,
                            iconSize = 18.dp,
                            background = Paf.Surf3,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    // Tone/Limit + knobs Bass/Treble (siempre visibles, §6).
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            PafActionChip(
                                label = "Tone",
                                icon = PafIconKind.Tone,
                                active = updatingPreset.toneOn,
                                onClick = {
                                    update(updatingPreset.copy(toneOn = !updatingPreset.toneOn))
                                },
                            )
                            PafActionChip(
                                label = "Limit",
                                icon = PafIconKind.Limit,
                                active = updatingPreset.limitOn,
                                onClick = {
                                    update(updatingPreset.copy(limitOn = !updatingPreset.limitOn))
                                },
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Knob(
                            label = "Bass",
                            value = updatingPreset.toneBassPct,
                            onValueChange = { pct ->
                                update(updatingPreset.copy(toneBassPct = pct, toneOn = true))
                            },
                            dialSize = 78.dp,
                        )
                        Spacer(Modifier.width(4.dp))
                        Knob(
                            label = "Treble",
                            value = updatingPreset.toneTreblePct,
                            onValueChange = { pct ->
                                update(updatingPreset.copy(toneTreblePct = pct, toneOn = true))
                            },
                            dialSize = 78.dp,
                        )
                    }
                }
                1 -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        Knob(
                            label = "Bass",
                            value = updatingPreset.toneBassPct,
                            onValueChange = { pct ->
                                update(updatingPreset.copy(toneBassPct = pct, toneOn = true))
                            },
                            dialSize = 132.dp,
                        )
                        Knob(
                            label = "Treble",
                            value = updatingPreset.toneTreblePct,
                            onValueChange = { pct ->
                                update(updatingPreset.copy(toneTreblePct = pct, toneOn = true))
                            },
                            dialSize = 132.dp,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        PafActionChip(
                            label = if (updatingPreset.toneOn) "Tone activo" else "Tone apagado",
                            icon = PafIconKind.Tone,
                            active = updatingPreset.toneOn,
                            onClick = {
                                update(updatingPreset.copy(toneOn = !updatingPreset.toneOn))
                            },
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Bass: shelf 250 Hz · Treble: shelf 4 kHz · máx +12 dB",
                        style = MaterialTheme.typography.bodySmall,
                        color = Paf.Text3,
                    )
                    Spacer(Modifier.height(6.dp))
                    EqCurvePanel(updatingPreset)
                }
                else -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        PafActionChip(
                            label = if (updatingPreset.limitOn) "Limitador activo" else "Limitador apagado",
                            icon = PafIconKind.Limit,
                            active = updatingPreset.limitOn,
                            onClick = {
                                update(updatingPreset.copy(limitOn = !updatingPreset.limitOn))
                            },
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Limitador suave: garantiza picos ≤ techo (0.98 FS) con " +
                            "release ~80 ms; sin coloración cuando el contenido está bien recortado.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Paf.Text3,
                    )
                    Spacer(Modifier.height(6.dp))
                    EqCurvePanel(updatingPreset)
                }
            }
            Spacer(Modifier.height(8.dp))

            // --- parametric editor / crossfade / save (EQ ground layer) ------
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 20.dp),
            ) {
                item {
                    Text(
                        "Crossfade: ${crossfadeS} s" + if (crossfadeS == 0) " (off)" else "",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    PafHSlider(
                        value = crossfadeS.toFloat(),
                        valueRange = 0f..12f,
                        onValueChange = { v ->
                            crossfadeS = v.toInt()
                            sendCrossfade(crossfadeS * 1000L)
                        },
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
                    Spacer(Modifier.height(10.dp))
                }
            }
        }

        if (showPresets) {
            EqPresetsDialog(
                presets = savedPresets,
                name = presetName,
                onName = { presetName = it },
                onLoad = {
                    presetName = it.name
                    update(it)
                },
                onSave = {
                    PresetStore.saveNamed(this@EqTab, updatingPreset.copy(name = presetName))
                    savedPresets = PresetStore.listNamed(this@EqTab)
                },
                onReset = { update(PresetStore.defaultPreset()) },
                onClose = { showPresets = false },
            )
        }
        if (showOptions) {
            EqOptionsDialog(
                onAddBand = {
                    if (updatingPreset.bands.size < 31) {
                        update(
                            updatingPreset.copy(
                                bands = updatingPreset.bands +
                                    Band(FilterType.PEAKING, 1000, 0f, 1.0f),
                            ),
                        )
                    }
                },
                onReset = { update(PresetStore.defaultPreset()) },
                onClose = { showOptions = false },
            )
        }
    }


    /**
     * Selector segmentado de la referencia (§6): una única pastilla #181818
     * con tres zonas iguales; el icono activo se pinta blanco.
     */
    @Composable
internal fun MainActivity.PafSegmentedControl(
        kinds: List<PafIconKind>,
        selected: Int,
        onSelect: (Int) -> Unit,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(RoundedCornerShape(50))
                .background(Paf.Surf2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            kinds.forEachIndexed { i, kind ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    PafIcon(kind, if (i == selected) Paf.Text else Paf.Text3, 22.dp)
                }
            }
        }
    }


    /** Panel de la curva: fondo #181818 con la mitad izquierda algo más clara. */
    @Composable
internal fun MainActivity.EqCurvePanel(preset: EqPreset) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Paf.Surf2),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.78f)
                    .fillMaxHeight()
                    .background(Paf.Surf1),
            )
            EqCurve(preset, modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp))
        }
    }


    /** Slider horizontal fino (crossfade), coherente con el look del EQ. */
    @Composable
internal fun MainActivity.PafHSlider(
        value: Float,
        valueRange: ClosedFloatingPointRange<Float>,
        onValueChange: (Float) -> Unit,
        modifier: Modifier = Modifier,
    ) {
        val span = (valueRange.endInclusive - valueRange.start).takeIf { it > 0f } ?: 1f
        val frac = ((value - valueRange.start) / span).coerceIn(0f, 1f)
        Box(
            modifier = modifier
                .height(30.dp)
                .pointerInput(valueRange) {
                    fun setAt(x: Float) {
                        val f = (x / size.width.toFloat()).coerceIn(0f, 1f)
                        onValueChange(valueRange.start + f * span)
                    }
                    detectTapGestures { pos -> setAt(pos.x) }
                }
                .pointerInput(valueRange) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val f = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                        onValueChange(valueRange.start + f * span)
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val cy = size.height / 2f
                val h = 6.dp.toPx()
                val r = CornerRadius(h / 2f)
                drawRoundRect(
                    Paf.Surf4,
                    topLeft = Offset(0f, cy - h / 2f),
                    size = Size(size.width, h),
                    cornerRadius = r,
                )
                drawRoundRect(
                    Paf.Text2,
                    topLeft = Offset(0f, cy - h / 2f),
                    size = Size(size.width * frac, h),
                    cornerRadius = r,
                )
                drawCircle(Paf.Text, radius = 8.dp.toPx(), center = Offset(size.width * frac, cy))
            }
        }
    }


    /** Diálogo de presets (§6): cargar guardados, guardar con nombre, reset. */
    @Composable
internal fun MainActivity.EqPresetsDialog(
        presets: List<EqPreset>,
        name: String,
        onName: (String) -> Unit,
        onLoad: (EqPreset) -> Unit,
        onSave: () -> Unit,
        onReset: () -> Unit,
        onClose: () -> Unit,
    ) {
        AlertDialog(
            onDismissRequest = onClose,
            containerColor = Paf.Surf2,
            titleContentColor = Paf.Text,
            textContentColor = Paf.Text2,
            title = {
                Text(
                    "Presets",
                    fontFamily = PafFamilyBold,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (presets.isEmpty()) {
                        Text(
                            "Sin presets guardados todavía.",
                            fontFamily = PafFamily,
                            color = Paf.Text3,
                            fontSize = 14.sp,
                        )
                    }
                    presets.take(6).forEach { sp ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(50))
                                .background(Paf.Surf3)
                                .clickable { onLoad(sp) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PafIcon(PafIconKind.Bars, Paf.EqGreen, 16.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                sp.name,
                                fontFamily = PafFamilyBold,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Paf.Text,
                            )
                        }
                    }
                    OutlinedTextField(
                        value = name,
                        onValueChange = onName,
                        singleLine = true,
                        label = { Text("Nombre") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onSave) { Text("Guardar") }
                        OutlinedButton(onClick = onReset) { Text("Reset") }
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = onClose,
                    colors = ButtonDefaults.textButtonColors(contentColor = Paf.Text),
                ) { Text("Cerrar") }
            },
        )
    }


    /** Menú ⋯ del EQ: añadir banda o restablecer el preset. */
    @Composable
internal fun MainActivity.EqOptionsDialog(
        onAddBand: () -> Unit,
        onReset: () -> Unit,
        onClose: () -> Unit,
    ) {
        AlertDialog(
            onDismissRequest = onClose,
            containerColor = Paf.Surf2,
            titleContentColor = Paf.Text,
            textContentColor = Paf.Text2,
            title = {
                Text(
                    "Opciones",
                    fontFamily = PafFamilyBold,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        "Añadir banda paramétrica" to onAddBand,
                        "Restablecer EQ" to onReset,
                    ).forEach { (label, action) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(50))
                                .clickable {
                                    action()
                                    onClose()
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PafIcon(PafIconKind.Dots, Paf.Text2, 16.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                label,
                                fontFamily = PafFamilyBold,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Paf.Text,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = onClose,
                    colors = ButtonDefaults.textButtonColors(contentColor = Paf.Text),
                ) { Text("Cerrar") }
            },
        )
    }


    /** Status chip like the observed one: `NO DVC · EQ n · TON · LMT`. */
    @Composable
internal fun MainActivity.EqStatusChip(preset: EqPreset) {
        val eqCount = preset.bands.size + preset.graphicBandCount
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Paf.Surf2)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PafIcon(PafIconKind.Info, Paf.Text3, 13.dp)
            Spacer(Modifier.width(6.dp))
            Text(
                "NO DVC · EQ ${if (eqCount > 0) "$eqCount" else "OFF"} · " +
                    "TON ${if (preset.toneOn) "ON" else "OFF"} · " +
                    "LMT ${if (preset.limitOn) "ON" else "OFF"}",
                fontFamily = PafFamilyBold,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = Paf.Text2,
            )
        }
    }


    @Composable
internal fun MainActivity.EqCurve(
        preset: EqPreset,
        modifier: Modifier = Modifier.fillMaxWidth().height(110.dp),
    ) {
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
        Canvas(modifier = modifier) {
            val dbMin = -18f
            val dbMax = +18f
            fun yForDb(db: Float): Float =
                size.height - ((db - dbMin) / (dbMax - dbMin)) * size.height
            // riel de respuesta (#313131) y línea de 0 dB tenue
            drawRoundRect(
                color = Paf.Surf4,
                topLeft = Offset(0f, size.height / 2f - 1.5f),
                size = Size(size.width, 3f),
                cornerRadius = CornerRadius(2f),
            )
            drawLine(
                color = Color(0x22FFFFFF),
                start = Offset(0f, yForDb(0f)),
                end = Offset(size.width, yForDb(0f)),
            )
            val path = Path()
            curve.forEachIndexed { i, db ->
                val x = size.width * i / (curve.size - 1).coerceAtLeast(1)
                val y = yForDb(db)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            // halo verde + línea neón (§6)
            drawPath(path, Paf.EqGlow.copy(alpha = 0.45f), style = Stroke(12f))
            drawPath(path, Paf.EqGreen, style = Stroke(4f))
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
                val railW = size.width * 0.16f
                val railX = (size.width - railW) / 2f
                // riel #313131
                drawRoundRect(
                    color = Paf.Surf4,
                    topLeft = Offset(railX, 0f),
                    size = Size(railW, size.height),
                    cornerRadius = CornerRadius(railW / 2f),
                )
                // segmento activo verde neón con halo (§6)
                val glowW = railW * 2.1f
                drawRoundRect(
                    color = Paf.EqGlow.copy(alpha = 0.35f),
                    topLeft = Offset((size.width - glowW) / 2f, size.height - fh),
                    size = Size(glowW, fh),
                    cornerRadius = CornerRadius(glowW / 2f),
                )
                drawRoundRect(
                    color = Paf.EqGreen,
                    topLeft = Offset(railX, size.height - fh),
                    size = Size(railW, fh),
                    cornerRadius = CornerRadius(railW / 2f),
                )
                // 0 dB center marker
                drawLine(
                    color = Color(0x33FFFFFF),
                    start = Offset(0f, size.height / 2f),
                    end = Offset(size.width, size.height / 2f),
                    strokeWidth = 2f,
                )
                // thumb cápsula blanca
                val thumbW = size.width * 0.62f
                val thumbH = size.width * 0.22f
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(
                        (size.width - thumbW) / 2f,
                        (size.height - fh - thumbH / 2f).coerceIn(0f, size.height - thumbH),
                    ),
                    size = Size(thumbW, thumbH),
                    cornerRadius = CornerRadius(thumbH / 2f),
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
        dialSize: Dp = 96.dp,
        trackColor: Color = Paf.Surf4,
        fillColor: Color = Paf.EqGreen,
    ) {
        Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            val frac = (value.coerceIn(0f, 100f)) / 100f
            Canvas(
                modifier = Modifier
                    .size(dialSize)
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
                val sw = 30f
                val inset = sw
                val r = (size.minDimension - 2 * inset) / 2f
                val cx0 = size.width / 2f
                val cy0 = size.height / 2f
                // cuerpo + anillo completo (look de la referencia)
                drawCircle(Paf.Surf1, radius = r + sw / 2f, center = Offset(cx0, cy0))
                drawCircle(
                    color = trackColor,
                    radius = r,
                    center = Offset(cx0, cy0),
                    style = Stroke(width = sw),
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
                PafHSlider(
                    value = band.gainDb.coerceIn(-15f, 15f),
                    valueRange = -15f..15f,
                    onValueChange = onGain,
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
