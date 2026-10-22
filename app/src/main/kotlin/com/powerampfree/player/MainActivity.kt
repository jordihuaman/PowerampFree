package com.powerampfree.player

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.powerampfree.audio.dsp.Band
import com.powerampfree.audio.dsp.EqPreset
import com.powerampfree.audio.dsp.FilterType
import com.powerampfree.audio.dsp.ParametricEq
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.currentCoroutineContext
/**
 * UI del reproductor — dos secciones (original, sin recursos de la app de referencia):
 *  1. Biblioteca (MediaStore) + importación de carpeta SAF; tocar una pista la
 *     reproduce y encola el resto de la lista (playback continuo).
 *  2. EQ paramétrico: sliders por banda ligados al estado EqPreset, curva de
 *     respuesta dibujada con la misma matemática que aplica el DSP real,
 *     presets con nombre guardados en filesDir/eq/.
 *
 * EQ realtime: el estado se envía al servicio por MediaSession custom command
 * (com.powerampfree.player.SET_PRESET), que aplica DspAudioProcessor.applyPreset.
 */
class MainActivity : ComponentActivity() {

    private val TAG = "PafMainActivity"

    private var controller: MediaController? by mutableStateOf(null)

    /** Selection queued while the MediaController is still connecting. */
    private var pendingPlay: Pair<List<MediaTrack>, Int>? = null

    /** Shared library state so callbacks and composables recompute together. */
    private var libraryOrEmpty by mutableStateOf<List<MediaTrack>>(emptyList())

    private val askAudioPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            libraryOrEmpty = if (granted) MediaStoreLibrary.query(this) else emptyList()
        }

    private val pickFolder =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            if (uri != null) {
                runCatching {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
                val tracks = SafLibrary.collectAudio(this, uri)
                playTracks(tracks, 0)
            }
        }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val initialLibrary: List<MediaTrack> =
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
                == PackageManager.PERMISSION_GRANTED
            ) {
                libraryOrEmpty = MediaStoreLibrary.query(this)
                libraryOrEmpty
            } else emptyList()

        setContent {
            MaterialTheme {
                PlayerScreen()
            }
        }

        connectController()
    }

    private fun connectController() {
        val token = SessionToken(this, PlaybackService.sessionComponent(this))
        val future = MediaController.Builder(this, token).buildAsync()
        future.addListener(
            {
                try {
                    controller = future.get()
                    pendingPlay?.let { (tracks, idx) ->
                        pendingPlay = null
                        playTracks(tracks, idx)
                    }
                } catch (t: Throwable) {
                    android.util.Log.e(TAG, "MediaController connect failed", t)
                }
            },
            ContextCompat.getMainExecutor(this),
        )
    }

    /** Queues the whole track list starting at [index] (continuous autoplay after). */
    private fun playTracks(tracks: List<MediaTrack>, index: Int) {
        val c = controller
        if (c == null) {
            android.util.Log.w(
                TAG,
                "controller not ready; queueing selection (${tracks.size} tracks)",
            )
            pendingPlay = tracks to index
            return
        }
        android.util.Log.i(TAG, "playTracks n=${tracks.size} index=$index first=${tracks.getOrNull(index)?.uri}")
        // Queue delivery via our OWN session command: media3's controller-side
        // MediaItem serialization degraded large queues when reaching the
        // service (observed items=1 start=-1), so we carry the list ourselves.
        val extras = Bundle().apply {
            putString(PlaybackService.EXTRA_TRACKS_JSON, MediaTrack.listToJson(tracks))
            putInt(PlaybackService.EXTRA_START_INDEX, index)
        }
        c.sendCustomCommand(
            androidx.media3.session.SessionCommand(PlaybackService.CMD_SET_QUEUE, extras),
            extras,
        )
    }

    override fun onDestroy() {
        controller?.release()
        controller = null
        super.onDestroy()
    }

    // ------------------------------------------------------------------ UI

    @Composable
    private fun PlayerScreen() {
        var updatingPreset by remember { mutableStateOf(PresetStore.loadCurrent(this)) }
        var crossfadeS by remember { mutableStateOf(PlaybackSettings.crossfadeMs(this).toInt() / 1000) }
        var savedPresets by remember { mutableStateOf(PresetStore.listNamed(this)) }
        var presetName by remember { mutableStateOf("MyPreset") }

        val nowTitle by rememberPlayingState()
        val isPlaying by rememberPlayingFlag()

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // ------------------------------------------------- now playing row
            item {
                Text("Reproduciendo", style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(nowTitle.take(40), style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        androidx.compose.material3.TextButton(onClick = { sendSkip(true) }) { Text("⏮") }
                        Button(onClick = { togglePlayPause() }) { Text(if (isPlaying) "⏸" else "▶") }
                        androidx.compose.material3.TextButton(onClick = { sendSkip(false) }) { Text("⏭") }
                    }
                }
                SpectrumBars(isPlaying = isPlaying)
            }

            // ------------------------------------------------------ EQ section
            item {
                Text("EQ paramétrico", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Preamp: ${"%.1f".format(updatingPreset.preampDb)} dB",
                    style = MaterialTheme.typography.bodySmall,
                )
                Slider(
                    value = updatingPreset.preampDb,
                    onValueChange = { v ->
                        updatingPreset = updatingPreset.copy(preampDb = v)
                        sendPreset(updatingPreset)
                    },
                    valueRange = -12f..12f,
                    modifier = Modifier.fillMaxWidth(),
                )
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
                EqCurve(updatingPreset)
            }
            itemsIndexed(updatingPreset.bands) { i, band ->
                BandRow(
                    index = i,
                    band = band,
                    onGain = { g ->
                        updatingPreset = updatingPreset.copy(
                            bands = updatingPreset.bands.toMutableList()
                                .apply { set(i, band.copy(gainDb = g)) },
                        )
                        sendPreset(updatingPreset)
                    },
                    onRemove = {
                        updatingPreset = updatingPreset.copy(
                            bands = updatingPreset.bands.toMutableList().apply { removeAt(i) },
                        )
                        sendPreset(updatingPreset)
                    },
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        enabled = updatingPreset.bands.size < 31,
                        onClick = {
                            updatingPreset = updatingPreset.copy(
                                bands = updatingPreset.bands +
                                    Band(FilterType.PEAKING, 1000, 0f, 1.0f),
                            )
                            sendPreset(updatingPreset)
                        },
                    ) { Text("+ banda") }
                    OutlinedButton(onClick = {
                        updatingPreset = PresetStore.defaultPreset()
                        sendPreset(updatingPreset)
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
                        PresetStore.saveNamed(this@MainActivity, updatingPreset.copy(name = presetName))
                        savedPresets = PresetStore.listNamed(this@MainActivity)
                    }) { Text("Guardar") }
                }
                if (savedPresets.isNotEmpty()) {
                    Text("Presets guardados:", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        savedPresets.take(5).forEach { sp ->
                            OutlinedButton(onClick = {
                                presetName = sp.name
                                updatingPreset = sp
                                sendPreset(sp)
                            }) { Text(sp.name, style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                }
            }

            // -------------------------------------------------- Library section
            item {
                Spacer(Modifier.height(10.dp))
                Text("Biblioteca (${libraryOrEmpty.size} pistas)", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (libraryOrEmpty.isEmpty() &&
                        ContextCompat.checkSelfPermission(
                            this@MainActivity, Manifest.permission.READ_MEDIA_AUDIO,
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        Button(onClick = {
                            askAudioPermission.launch(Manifest.permission.READ_MEDIA_AUDIO)
                        }) { Text("Conceder permiso de audio") }
                    } else {
                        OutlinedButton(onClick = {
                            libraryOrEmpty = MediaStoreLibrary.query(this@MainActivity)
                        }) { Text("Recargar") }
                    }
                    OutlinedButton(onClick = { pickFolder.launch(null) }) {
                        Text("Importar carpeta (SAF)")
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            itemsIndexed(libraryOrEmpty) { i, t ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { playTracks(libraryOrEmpty, i) }
                        .padding(vertical = 4.dp),
                ) {
                    Text(t.title, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${t.artist} — ${t.album}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }

    // --------------------------------------------------- playback helpers

    private fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    private fun sendSkip(next: Boolean) {
        val c = controller ?: return
        val action = if (next) PlaybackService.CMD_SKIP_NEXT else PlaybackService.CMD_SKIP_PREV
        val bundle = Bundle.EMPTY
        controller?.sendCustomCommand(androidx.media3.session.SessionCommand(action, bundle), bundle)
    }

    @Composable
    private fun rememberPlayingState(): androidx.compose.runtime.State<String> {
        val state = androidx.compose.runtime.remember { mutableStateOf("—") }
        androidx.compose.runtime.LaunchedEffect(controller) {
            val c = controller
            if (c == null) {
                state.value = "—"
            } else {
                val listener = object : androidx.media3.common.Player.Listener {
                    override fun onMediaMetadataChanged(m: androidx.media3.common.MediaMetadata) {
                        state.value = c.mediaMetadata.title?.toString() ?: "—"
                    }

                    override fun onMediaItemTransition(
                        m: androidx.media3.common.MediaItem?,
                        reason: Int,
                    ) {
                        state.value = c.mediaMetadata.title?.toString() ?: "—"
                    }
                }
                c.addListener(listener)
                state.value = c.mediaMetadata.title?.toString() ?: "—"
                try {
                    awaitCancellation()
                } finally {
                    c.removeListener(listener)
                }
            }
        }
        return state
    }

    @Composable
    private fun rememberPlayingFlag(): androidx.compose.runtime.State<Boolean> {
        val state = androidx.compose.runtime.remember { mutableStateOf(false) }
        androidx.compose.runtime.LaunchedEffect(controller) {
            val c = controller
            if (c == null) {
                state.value = false
            } else {
                val listener = object : androidx.media3.common.Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) {
                        state.value = playing
                    }
                }
                c.addListener(listener)
                state.value = c.isPlaying
                try {
                    awaitCancellation()
                } finally {
                    c.removeListener(listener)
                }
            }
        }
        return state
    }

    @Composable
    private fun SpectrumBars(isPlaying: Boolean) {
        var spectrum by androidx.compose.runtime.remember {
            mutableStateOf(VisualizerTap.spectrum())
        }
        androidx.compose.runtime.LaunchedEffect(isPlaying) {
            while (kotlinx.coroutines.currentCoroutineContext().isActive) {
                if (isPlaying) spectrum = VisualizerTap.spectrum()
                kotlinx.coroutines.delay(50)
            }        }
        Canvas(modifier = Modifier.fillMaxWidth().height(84.dp)) {
            val barCount = 48
            val fLow = 30.0
            val fHigh = 16_000.0
            val logSpan = kotlin.math.ln(fHigh / fLow)
            val barW = size.width / barCount
            for (b in 0 until barCount) {
                val freq = fLow * Math.exp(logSpan * b / (barCount - 1.0))
                val k = (freq / 48_000.0 * VisualizerTap.bins).toInt()
                    .coerceIn(0, VisualizerTap.bins - 1)
                val mag = spectrum.getOrNull(k) ?: 0f
                val db = (20.0 * Math.log10(mag + 1e-9))
                val level = ((db + 60.0) / 60.0).coerceIn(0.0, 1.0).toFloat()
                drawRect(
                    color = Color(0x9939A7E4),
                    topLeft = Offset(b * barW + 1f, size.height * (1f - level)),
                    size = androidx.compose.ui.geometry.Size(barW - 2f, size.height * level),
                )
            }
        }
    }

    @Composable
    private fun EqCurve(preset: EqPreset) {
        // Off-screen engine instance just for the curve (same math as the DSP chain).
        val curve = remember(preset) {
            try {
                val eq = ParametricEq.build(ParametricEq.Config.of(preset.bands), 48_000, 2)
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

    @Composable
    private fun BandRow(
        index: Int,
        band: Band,
        onGain: (Float) -> Unit,
        onRemove: () -> Unit,
    ) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
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

    private fun sendPreset(preset: EqPreset) {
        PresetStore.storeCurrent(this, preset)
        val bundle = Bundle().apply {
            putString(PlaybackService.EXTRA_PRESET_JSON, preset.toJson())
        }
        controller?.sendCustomCommand(
            androidx.media3.session.SessionCommand(PlaybackService.CMD_SET_PRESET, bundle),
            bundle,
        )
    }

    private fun sendCrossfade(ms: Long) {
        PlaybackSettings.setCrossfadeMs(this, ms)
        val bundle = Bundle().apply {
            putLong(PlaybackService.EXTRA_CROSSFADE_MS, ms)
        }
        controller?.sendCustomCommand(
            androidx.media3.session.SessionCommand(PlaybackService.CMD_SET_CROSSFADE, bundle),
            bundle,
        )
    }
}
