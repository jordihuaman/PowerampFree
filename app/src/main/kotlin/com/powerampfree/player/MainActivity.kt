package com.powerampfree.player

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

/**
 * UI del reproductor — navegación de 5 tabs con mini-player persistente
 * (original, sin recursos de la app de referencia):
 *
 *  ▶ Reproductor: título/formato real del decoder, transporte, shuffle/repeat,
 *    sleep timer, rating y espectro.
 *  📚 Biblioteca: tarjetas de categoría (All Songs / Álbumes / Artistas /
 *    Géneros / Carpetas, sobre MediaStore) + importación SAF; tocar una pista
 *    la reproduce y encola la lista visible (playback continuo).
 *  🎚 EQ: paramétrico con curva, presets, crossfade.
 *  🔍 Búsqueda: filtro por categoría + historial persistente.
 *  📋 Cola: lista en vivo del orquestador; tocar una pista salta a ella.
 *
 * Realtime: cada cambio (EQ, crossfade, cola, repeat...) viaja por session
 * commands de nuestra propia API (com.powerampfree.player.*); en modo dual la
 * cola exacta se comparte en el mismo proceso vía [QueueBus].
 */
/** Signature of the long-press opener shared by every track list (sprint 6). */
private typealias OpenCtx = (track: MediaTrack, list: List<MediaTrack>, index: Int) -> Unit

/** Library navigation from the track context menu: 0=artist 1=album 2=folder. */
private data class NavRequest(val kind: Int, val key: String, val albumId: Long = -1L)

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
                PafApp()
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

    private enum class PafTab(val glyph: String, val label: String) {
        PLAYER("▶", "Reprod."),
        LIBRARY("📚", "Biblio."),
        EQ("🎚", "EQ"),
        SEARCH("🔍", "Buscar"),
        QUEUE("📋", "Cola"),
    }

    @Composable
    private fun PafApp() {
        val snap by rememberPlayerSnapshot()

        // Look & Feel live state (write-through to prefs from Settings).
        var themeMode by remember { mutableStateOf(AppearanceStore.themeMode(this)) }
        var accentColor by remember { mutableStateOf(AppearanceStore.accent(this)) }
        var boldFont by remember { mutableStateOf(AppearanceStore.boldFont(this)) }
        var startAtLibrary by remember { mutableStateOf(AppearanceStore.startAtLibrary(this)) }
        var settingsOpen by remember { mutableStateOf(false) }

        // Track context menu + lyrics overlay (sprint 6).
        var ctxTrack by remember { mutableStateOf<MediaTrack?>(null) }
        var ctxList by remember { mutableStateOf<List<MediaTrack>>(emptyList()) }
        var ctxIndex by remember { mutableStateOf(0) }
        var gotoNav by remember { mutableStateOf<NavRequest?>(null) }
        var lyricsTrack by remember { mutableStateOf<MediaTrack?>(null) }
        var plTrack by remember { mutableStateOf<MediaTrack?>(null) }
        val openCtx: OpenCtx = { t, l, i ->
            ctxTrack = t
            ctxList = l
            ctxIndex = i
        }

        var tab by remember {
            mutableStateOf(if (startAtLibrary) PafTab.LIBRARY else PafTab.PLAYER)
        }

        val darkTheme = when (themeMode) {
            AppearanceStore.MODE_LIGHT -> false
            AppearanceStore.MODE_DARK -> true
            else -> isSystemInDarkTheme()
        }
        val openLyrics: (PlayerSnapshot) -> Unit = { s ->
            val dur = controller?.duration ?: 0L
            lyricsTrack = MediaTrack(
                uri = s.uri,
                title = s.title,
                artist = s.artist,
                album = "",
                durationMs = if (dur > 0) dur else 0L,
            )
        }
        val baseScheme = if (darkTheme) darkColorScheme() else lightColorScheme()
        val scheme =
            if (accentColor != null) baseScheme.copy(primary = Color(accentColor!!)) else baseScheme
        val typography = if (boldFont) {
            val t = MaterialTheme.typography
            t.copy(
                displayLarge = t.displayLarge.copy(fontWeight = FontWeight.Bold),
                displayMedium = t.displayMedium.copy(fontWeight = FontWeight.Bold),
                displaySmall = t.displaySmall.copy(fontWeight = FontWeight.Bold),
                headlineLarge = t.headlineLarge.copy(fontWeight = FontWeight.Bold),
                headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold),
                headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Bold),
                titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Bold),
                titleMedium = t.titleMedium.copy(fontWeight = FontWeight.Bold),
                titleSmall = t.titleSmall.copy(fontWeight = FontWeight.Bold),
                bodyLarge = t.bodyLarge.copy(fontWeight = FontWeight.Bold),
                bodyMedium = t.bodyMedium.copy(fontWeight = FontWeight.Bold),
                bodySmall = t.bodySmall.copy(fontWeight = FontWeight.Bold),
                labelLarge = t.labelLarge.copy(fontWeight = FontWeight.Bold),
                labelMedium = t.labelMedium.copy(fontWeight = FontWeight.Bold),
                labelSmall = t.labelSmall.copy(fontWeight = FontWeight.Bold),
            )
        } else {
            MaterialTheme.typography
        }

        MaterialTheme(colorScheme = scheme, typography = typography) {
            // Edge-to-edge on MIUI: the content must clear the status bar or the
            // system UI swallows taps in the top band (observed).
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars),
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    when (tab) {
                        PafTab.PLAYER -> PlayerTab(
                            snap,
                            onSettings = { settingsOpen = true },
                            onLyrics = { openLyrics(snap) },
                        )
                        PafTab.LIBRARY -> LibraryTab(
                            onSettings = { settingsOpen = true },
                            onOpenCtx = openCtx,
                            goto = gotoNav,
                            onGotoDone = { gotoNav = null },
                        )
                        PafTab.EQ -> EqTab()
                        PafTab.SEARCH -> SearchTab(onOpenCtx = openCtx)
                        PafTab.QUEUE -> QueueTab(snap, onOpenCtx = openCtx)
                    }

                    // Overlays INSIDE the content box (proven pattern: sibling
                    // compose calls outside a layout node behaved inert on the
                    // device). Z-order: context menu → lyrics → settings.
                    ctxTrack?.let { t ->
                        TrackContextMenu(
                            track = t,
                            onDismiss = { ctxTrack = null },
                            onPlay = {
                                playTracks(ctxList.ifEmpty { listOf(t) }, ctxIndex)
                                ctxTrack = null
                            },
                            onEnqueueNext = { sendEnqueueNext(t, false); ctxTrack = null },
                            onEnqueueEnd = { sendEnqueueNext(t, true); ctxTrack = null },
                            onArtist = {
                                gotoNav = NavRequest(
                                    0, t.artist.ifBlank { "Sin artista" },
                                )
                                tab = PafTab.LIBRARY
                                ctxTrack = null
                            },
                            onAlbum = {
                                gotoNav = NavRequest(
                                    1, t.album.ifBlank { "Sin álbum" }, t.albumId,
                                )
                                tab = PafTab.LIBRARY
                                ctxTrack = null
                            },
                            onFolder = {
                                gotoNav = NavRequest(2, if (t.folder.isBlank()) "" else t.folder.trimEnd('/'))
                                tab = PafTab.LIBRARY
                                ctxTrack = null
                            },
                            onLyrics = {
                                lyricsTrack = t
                                ctxTrack = null
                            },
                            onAddToPlaylist = {
                                plTrack = t
                                ctxTrack = null
                            },
                        )
                    }
                    plTrack?.let { t ->
                        AddToPlaylistSheet(
                            track = t,
                            onClose = { plTrack = null },
                        )
                    }
                    lyricsTrack?.let { t ->
                        LyricsScreen(
                            track = t,
                            isCurrent = t.uri == snap.uri && snap.uri.isNotEmpty(),
                            onClose = { lyricsTrack = null },
                        )
                    }
                    if (settingsOpen) {
                        SettingsScreen(
                    themeMode = themeMode,
                    onThemeMode = {
                        themeMode = it
                        AppearanceStore.setThemeMode(this@MainActivity, it)
                    },
                    accent = accentColor,
                    onAccent = {
                        accentColor = it
                        AppearanceStore.setAccent(this@MainActivity, it)
                    },
                    bold = boldFont,
                    onBold = {
                        boldFont = it
                        AppearanceStore.setBoldFont(this@MainActivity, it)
                    },
                    startAtLibrary = startAtLibrary,
                    onStartAtLibrary = {
                        startAtLibrary = it
                        AppearanceStore.setStartAtLibrary(this@MainActivity, it)
                    },
                    onClose = { settingsOpen = false },
                    currentSnapshot = snap,
                    )
                    }
                    }
                if (tab != PafTab.PLAYER && !settingsOpen) {
                    MiniPlayerBar(snap, onOpen = { tab = PafTab.PLAYER })
                }
                NavigationBar {
                    PafTab.values().forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { tab = t },
                            icon = { Text(t.glyph, style = MaterialTheme.typography.titleMedium) },
                            label = { Text(t.label, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
            }
        }
    }

    // ------------------------------ album art + background (sprint 8)

    /**
     * Art tile resolved from [PlayerSnapshot] cues (MediaStore albumart
     * provider → sibling cover files); generic glyph when nothing found.
     */
    @Composable
    private fun AlbumArtImage(snap: PlayerSnapshot, px: Int, modifier: Modifier) {
        var bmp by remember(snap.albumId, snap.uri) {
            mutableStateOf<android.graphics.Bitmap?>(null)
        }
        androidx.compose.runtime.LaunchedEffect(snap.albumId, snap.uri) {
            val cap = if (ArtSettings.highResArt(this@MainActivity)) 1024 else 512
            bmp = withContext(Dispatchers.IO) {
                AlbumArtLoader.load(
                    this@MainActivity, snap.albumId, snap.uri, snap.folder, minOf(px, cap),
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
                    contentScale = if (ArtSettings.keepAspect(this@MainActivity)) {
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
    private fun PlayerBackground(snap: PlayerSnapshot, modifier: Modifier) {
        if (!ArtSettings.bgEnabled(this@MainActivity)) {
            Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface))
            return
        }
        var bmp by remember(snap.albumId, snap.uri) {
            mutableStateOf<android.graphics.Bitmap?>(null)
        }
        androidx.compose.runtime.LaunchedEffect(snap.albumId, snap.uri) {
            // Sharp art when the blur slider is 0 (256px upscale would look
            // blurry anyway); small decode for the blurred path.
            val b = ArtSettings.bgBlur(this@MainActivity)
            bmp = withContext(Dispatchers.IO) {
                AlbumArtLoader.load(
                    this@MainActivity, snap.albumId, snap.uri, snap.folder,
                    if (b == 0) 1024 else 256,
                )
            }
        }
        val gradient = ArtSettings.bgGradient(this@MainActivity) / 10f
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
        val blur = ArtSettings.bgBlur(this@MainActivity)
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
    private fun MiniPlayerBar(snap: PlayerSnapshot, onOpen: () -> Unit) {
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
    private fun PlayerTab(snap: PlayerSnapshot, onSettings: () -> Unit, onLyrics: () -> Unit) {
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
                        VizSettings.setPlayerSpectrum(this@MainActivity, on)
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
            // art tile row (sprint 8): own row, no nested weight chains
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
                val rated = if (ratTick >= 0) RatingsStore.rating(this@MainActivity, snap.uri) else 0
                androidx.compose.material3.TextButton(onClick = {
                    RatingsStore.toggle(this@MainActivity, snap.uri, 1)
                    ratTick++
                }) {
                    Text("👍", color = if (rated == 1) accent else LocalContentColor.current)
                }
                androidx.compose.material3.TextButton(onClick = {
                    RatingsStore.toggle(this@MainActivity, snap.uri, -1)
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

    // ------------------------------------------------------------- eq tab

    /**
     * EQ screen (block 16): EQ / Tone / Limit tabs over the same resolved
     * preset. The graphic layer is 10 fixed bands of the parametric engine;
     * Tone knobs are two real shelf bands; the limiter flag toggles the
     * SoftLimiter module. Everything lives in [EqPreset] and is pushed live
     * via the session command.
     */
    @Composable
    private fun EqTab() {
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
                    if (VizSettings.eqSpectrum(this@MainActivity) != VizSettings.EQ_DISABLED) {
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
    private fun EqStatusChip(preset: EqPreset) {
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

    /** Placeholder used to key tab pages cleanly. */
    @Composable
    private fun TabContent(name: String) {
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier) {}
    }

    // ------------------------------------------------------ library tab

    private data class GroupRow(
        val title: String,
        val subtitle: String,
        val tracks: List<MediaTrack>,
    )

    private enum class LibTop { ROOT, SONGS, ALBUMS, ARTISTS, GENRES, FOLDERS, PLAYLISTS }

    /** Sort options for the folder explorer (List Options). */
    private enum class ListSort(val label: String) {
        TITLE("Título"), TRACK("Nº pista"), DURATION("Duración"),
    }

    /** Parent of a RELATIVE_PATH-like folder string ("Music/RecForge/" → "Music/"; "Music/" → ""). */
    private fun parentFolder(p: String): String {
        if (p.isEmpty()) return ""
        val parent = p.trimEnd('/').substringBeforeLast('/', "")
        return if (parent.isEmpty()) "" else "$parent/"
    }

    /** Direct-child folder paths under [path] ("" = device root). */
    private fun subFoldersOf(path: String): List<String> =
        libraryOrEmpty
            .map { it.folder }
            .filter { it.startsWith(path) && it != path }
            .map { path + it.removePrefix(path).substringBefore('/') + '/' }
            .distinct()
            .sorted()

    private fun folderTracksSorted(path: String, sort: ListSort): List<MediaTrack> =
        libraryOrEmpty.filter { it.folder == path }.let { list ->
            when (sort) {
                ListSort.TITLE -> list.sortedBy { it.title.lowercase() }
                ListSort.TRACK -> list.sortedBy { it.trackNo }
                ListSort.DURATION -> list.sortedBy { it.durationMs }
            }
        }

    private fun fmtDur(ms: Long): String {
        if (ms <= 0) return "—"
        val s = ms / 1000
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
    }

    @Composable
    private fun LibraryTab(
        onSettings: () -> Unit = {},
        onOpenCtx: OpenCtx = { _, _, _ -> },
        goto: NavRequest? = null,
        onGotoDone: () -> Unit = {},
    ) {
        var top by remember { mutableStateOf(LibTop.ROOT) }
        var detail by remember { mutableStateOf<GroupRow?>(null) }
        var folderPath by remember { mutableStateOf<String?>(null) }
        var plName by remember { mutableStateOf<String?>(null) }

        BackHandler(
            enabled = detail != null || folderPath != null || plName != null ||
                top != LibTop.ROOT,
        ) {
            when {
                detail != null -> detail = null
                plName != null -> plName = null
                folderPath != null -> folderPath =
                    if (folderPath!!.isEmpty()) null else parentFolder(folderPath!!)
                else -> top = LibTop.ROOT
            }
        }

        // Context-menu "go to artist/album/folder": jump inside the library.
        androidx.compose.runtime.LaunchedEffect(goto) {
            val r = goto ?: return@LaunchedEffect
            when (r.kind) {
                0 -> {
                    val tracks = libraryOrEmpty.filter {
                        it.artist.ifBlank { "Sin artista" } == r.key
                    }
                    if (tracks.isNotEmpty()) {
                        detail = GroupRow(r.key, "${tracks.size} pistas", tracks)
                    }
                }
                1 -> {
                    val tracks = libraryOrEmpty.filter {
                        (r.albumId >= 0 && it.albumId == r.albumId) ||
                            (r.albumId < 0 && it.album == r.key)
                    }.sortedBy { it.trackNo }
                    if (tracks.isNotEmpty()) {
                        detail = GroupRow(r.key, "${tracks.size} pistas", tracks)
                    }
                }
                2 -> {
                    detail = null
                    folderPath = r.key
                }
            }
            onGotoDone()
        }

        when {
            detail != null -> TrackListView(detail!!.title, detail!!.tracks, onOpenCtx)
            folderPath != null -> FolderScreen(
                path = folderPath!!,
                onNavigate = { folderPath = it },
                onOpenCtx = onOpenCtx,
            )
            top == LibTop.SONGS -> TrackListView("Todas las canciones", libraryOrEmpty, onOpenCtx)
            top == LibTop.ALBUMS -> GroupListView(
                "Álbumes",
                albumRows(),
                onOpen = { detail = it },
            )
            top == LibTop.ARTISTS -> GroupListView(
                "Artistas",
                artistRows(),
                onOpen = { detail = it },
            )
            top == LibTop.FOLDERS -> GroupListView(
                "Carpetas",
                folderRows(),
                onOpen = { g -> folderPath = if (g.subtitle == "(raíz)") "" else g.subtitle },
            )
            plName != null -> PlaylistScreen(
                name = plName!!,
                onOpenCtx = onOpenCtx,
                onChange = { plName = it },
            )
            top == LibTop.PLAYLISTS -> PlaylistsScreen(onOpen = { plName = it })
            top == LibTop.GENRES -> GenresScreen(onOpen = { detail = it })
            else -> LibraryRoot(onOpen = { top = it }, onSettings = onSettings)
        }
    }

    private fun albumRows(): List<GroupRow> =
        libraryOrEmpty.groupBy { it.albumId }
            .values
            .map { list ->
                GroupRow(
                    list.first().album.ifBlank { "Sin álbum" },
                    list.first().artist.ifBlank { "" } + " · ${list.size} pistas",
                    list.sortedBy { it.trackNo },
                )
            }
            .sortedBy { it.title.lowercase() }

    private fun artistRows(): List<GroupRow> =
        libraryOrEmpty.groupBy { it.artist.ifBlank { "Sin artista" } }
            .map { (name, list) -> GroupRow(name, "${list.size} pistas", list) }
            .sortedBy { it.title.lowercase() }

    private fun folderRows(): List<GroupRow> =
        libraryOrEmpty.groupBy { it.folder }
            .map { (f, list) ->
                val display = f.trimEnd('/').substringAfterLast('/').ifBlank { "Raíz" }
                GroupRow(display, f.ifBlank { "(raíz)" }, list)
            }
            .sortedBy { it.title.lowercase() }

    @Composable
    private fun LibraryRoot(onOpen: (LibTop) -> Unit, onSettings: () -> Unit) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text("Biblioteca", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${libraryOrEmpty.size} pistas en el sistema",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CategoryCard("🎵", "Canciones", libraryOrEmpty.size) { onOpen(LibTop.SONGS) }
                    CategoryCard("💿", "Álbumes", albumRows().size) { onOpen(LibTop.ALBUMS) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CategoryCard("🎤", "Artistas", artistRows().size) { onOpen(LibTop.ARTISTS) }
                    CategoryCard("🎼", "Géneros", 0) { onOpen(LibTop.GENRES) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CategoryCard("📁", "Carpetas", folderRows().size) { onOpen(LibTop.FOLDERS) }
                    CategoryCard(
                        "🔖", "Playlists",
                        com.powerampfree.player.PlaylistStore.list(this@MainActivity).size,
                    ) { onOpen(LibTop.PLAYLISTS) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CategoryCard("⚙", "Ajustes", 0) { onSettings() }
                    CategoryCard("📥", "Importar (SAF)", 0) { pickFolder.launch(null) }
                }
            }
            item {
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
                        }) { Text("Recargar biblioteca") }
                    }
                }
            }
        }
    }

    @Composable
    private fun RowScope.CategoryCard(glyph: String, title: String, count: Int, onClick: () -> Unit) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(86.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onClick)
                .padding(10.dp),
        ) {
            Column {
                Text(glyph, style = MaterialTheme.typography.titleMedium)
                Text(title, style = MaterialTheme.typography.bodyMedium)
                if (count > 0) {
                    Text(
                        "$count",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }

    @Composable
    private fun GroupListView(title: String, rows: List<GroupRow>, onOpen: (GroupRow) -> Unit) {  LazyColumn(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            itemsIndexed(rows) { _, g ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onOpen(g) }
                        .padding(10.dp),
                ) {
                    Text(g.title, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        g.subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }

    // -------------------------------------------------- playlists (sprint 10)

    /**
     * Saved playlists hub (block 6): "name · N pistas" rows + create row.
     * Lists live in `PlaylistStore` (filesDir/playlists); adding tracks happens
     * from any track's long-press context menu ("Añadir a Playlist").
     */
    @Composable
    private fun PlaylistsScreen(onOpen: (String) -> Unit) {
        var names by remember { mutableStateOf(PlaylistStore.list(this)) }
        var showNew by remember { mutableStateOf(false) }
        var newName by remember { mutableStateOf("") }
        androidx.compose.runtime.LaunchedEffect(Unit) {
            names = PlaylistStore.list(this@MainActivity)
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Playlists", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${names.size} listas guardadas",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    OutlinedButton(onClick = { showNew = true }) { Text("＋ Nueva") }
                }
            }
            if (names.isEmpty()) {
                item {
                    Text(
                        "Sin playlists todavía. Pulsa largo sobre una pista de " +
                            "cualquier lista y usa \"Añadir a Playlist\".",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
            itemsIndexed(names) { _, name ->
                val n = PlaylistStore.tracks(this@MainActivity, name).size
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onOpen(name) }
                        .padding(vertical = 8.dp, horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("🔖", modifier = Modifier.width(30.dp))
                    Text(name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "$n pistas",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
        if (showNew) {
            AlertDialog(
                onDismissRequest = { showNew = false },
                title = { Text("Nueva playlist") },
                text = {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Nombre") },
                    )
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(
                        enabled = newName.isNotBlank(),
                        onClick = {
                            val stored = PlaylistStore.create(this@MainActivity, newName)
                            newName = ""
                            showNew = false
                            onOpen(stored)
                        },
                    ) { Text("Crear") }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { showNew = false }) {
                        Text("Cancelar")
                    }
                },
            )
        }
    }

    /**
     * One saved playlist: header actions (▶ Reproducir / ✏ Renombrar / 🗑
     * Eliminar) + track rows. A row tap starts the queue at that track
     * (`playTracks`), long-press opens the shared track context menu, and the
     * trailing 🗑 removes the track from THIS list only.
     */
    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun PlaylistScreen(
        name: String,
        onOpenCtx: OpenCtx,
        onChange: (String?) -> Unit,
    ) {
        var list by remember { mutableStateOf<List<MediaTrack>>(emptyList()) }
        var tick by remember { mutableStateOf(0) }
        var showRename by remember { mutableStateOf(false) }
        var showDelete by remember { mutableStateOf(false) }
        var newTitle by remember { mutableStateOf(name) }

        androidx.compose.runtime.LaunchedEffect(name, tick) {
            list = PlaylistStore.tracks(this@MainActivity, name)
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${list.size} pistas",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    OutlinedButton(onClick = { playTracks(list, 0) }) { Text("▶ Reproducir") }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    OutlinedButton(onClick = { newTitle = name; showRename = true }) {
                        Text("✏ Renombrar")
                    }
                    OutlinedButton(onClick = { showDelete = true }) { Text("🗑 Eliminar") }
                }
                Spacer(Modifier.height(4.dp))
            }
            if (list.isEmpty()) {
                item {
                    Text(
                        "Lista vacía. Pulsa largo sobre una pista y elige " +
                            "\"Añadir a Playlist\".",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
            itemsIndexed(list) { i, t ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .combinedClickable(
                            onClick = { playTracks(list, i) },
                            onLongClick = { onOpenCtx(t, list, i) },
                        )
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(vertical = 6.dp, horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(t.title, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${t.artist} — ${t.album}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    Text(
                        "🗑",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier
                            .clickable {
                                if (PlaylistStore.removeTrack(this@MainActivity, name, t.uri)) {
                                    tick++
                                }
                            },
                    )
                }
            }
        }
        if (showRename) {
            AlertDialog(
                onDismissRequest = { showRename = false },
                title = { Text("Renombrar playlist") },
                text = {
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        label = { Text("Nuevo nombre") },
                    )
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(
                        enabled = newTitle.isNotBlank(),
                        onClick = {
                            val stored = PlaylistStore.rename(this@MainActivity, name, newTitle)
                            showRename = false
                            onChange(stored)
                        },
                    ) { Text("Guardar") }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { showRename = false }) {
                        Text("Cancelar")
                    }
                },
            )
        }
        if (showDelete) {
            AlertDialog(
                onDismissRequest = { showDelete = false },
                title = { Text("Eliminar playlist") },
                text = { Text("¿Borrar \"$name\" y sus ${list.size} pistas guardadas?") },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        PlaylistStore.delete(this@MainActivity, name)
                        showDelete = false
                        onChange(null)
                    }) { Text("Borrar") }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { showDelete = false }) {
                        Text("Cancelar")
                    }
                },
            )
        }
    }

    @Composable
    private fun GenresScreen(onOpen: (GroupRow) -> Unit) {
        var rows by remember(libraryOrEmpty) { mutableStateOf<List<GroupRow>?>(null) }
        androidx.compose.runtime.LaunchedEffect(libraryOrEmpty) {
            rows = MediaGenres.load(this@MainActivity)
                .mapNotNull { g ->
                    val tracks = libraryOrEmpty.filter { it.uri in g.uris }
                    if (tracks.isEmpty()) null
                    else GroupRow(g.name, "${tracks.size} pistas", tracks)
                }
        }
        val current = rows
        if (current == null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Géneros", style = MaterialTheme.typography.titleMedium)
                Text("Cargando…", style = MaterialTheme.typography.bodySmall)
            }
        } else if (current.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Géneros", style = MaterialTheme.typography.titleMedium)
                Text(
                    "No hay géneros en la biblioteca del sistema.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        } else {
            GroupListView("Géneros", current, onOpen)
        }
    }

    // ------------------------------------------------------- folder explorer

    /**
     * Folder explorer: breadcrumb to go back up, folder list to go down,
     * header with subtree totals (Playamp-style), shuffle/selected/sort actions
     * (List Options). Navigation data comes from MediaStore RELATIVE_PATH.
     */
    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun FolderScreen(
        path: String,
        onNavigate: (String) -> Unit,
        onOpenCtx: OpenCtx = { _, _, _ -> },
    ) {
        var sort by remember(path) { mutableStateOf(ListSort.TITLE) }
        var selecting by remember(path) { mutableStateOf(false) }
        val selected = remember { androidx.compose.runtime.mutableStateListOf<String>() }
        var showSort by remember { mutableStateOf(false) }

        val subs = remember(path, libraryOrEmpty) { subFoldersOf(path) }
        val tracks = remember(path, libraryOrEmpty, sort) { folderTracksSorted(path, sort) }
        val subtree = remember(path, libraryOrEmpty) {
            libraryOrEmpty.filter { it.folder.startsWith(path) }
        }
        val totalMs = subtree.sumOf { it.durationMs }

        Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            // ---- breadcrumb + header
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.TextButton(onClick = { onNavigate(parentFolder(path)) }) {
                    Text("↑")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        path.trimEnd('/').ifEmpty { "Dispositivo" },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                    )
                    Text(
                        "${subtree.size} pistas · ${fmtDur(totalMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                androidx.compose.material3.TextButton(onClick = { showSort = true }) { Text("⇅") }
            }
            // ---- actions row (header options)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { if (tracks.isNotEmpty()) playTracks(tracks, 0) }) {
                    Text("▶ Todo")
                }
                OutlinedButton(onClick = {
                    if (tracks.isNotEmpty()) {
                        playTracks(tracks, kotlin.random.Random.nextInt(tracks.size))
                    }
                }) { Text("🎲") }
                OutlinedButton(onClick = {
                    selecting = !selecting
                    if (!selecting) selected.clear()
                }) { Text(if (selecting) "✕ Salir" else "☑ Select") }
            }
            Spacer(Modifier.height(6.dp))
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (path.isNotEmpty()) {
                    itemsIndexed(listOf(parentFolder(path))) { _, parent ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigate(parent) }
                                .padding(vertical = 4.dp),
                        ) {
                            Text("📁 …", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                itemsIndexed(subs) { _, sub ->
                    val direct = libraryOrEmpty.count { it.folder == sub }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigate(sub) }
                            .padding(vertical = 4.dp),
                    ) {
                        Column {
                            Text(
                                sub.trimEnd('/').substringAfterLast('/'),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                "$direct pistas",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                }
                itemsIndexed(tracks) { i, t ->
                    val isSel = t.uri in selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    if (selecting) {
                                        if (isSel) selected.remove(t.uri) else selected.add(t.uri)
                                    } else {
                                        playTracks(tracks, i)
                                    }
                                },
                                onLongClick = { onOpenCtx(t, tracks, i) },
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (selecting) {
                            Text(
                                if (isSel) "☑" else "☐",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(end = 6.dp),
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(t.title, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                t.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                        Text(
                            fmtDur(t.durationMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
                item { Spacer(Modifier.height(12.dp)) }
            }
            if (selecting) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = {
                        val selTracks = tracks.filter { it.uri in selected }
                        if (selTracks.isNotEmpty()) playTracks(selTracks, 0)
                    }) { Text("▶ ${selected.size}") }
                    OutlinedButton(onClick = {
                        selected.clear()
                        tracks.forEach { selected.add(it.uri) }
                    }) { Text("Todos") }
                    OutlinedButton(onClick = { selected.clear() }) { Text("Ninguno") }
                }
            }
        }

        if (showSort) {
            AlertDialog(
                onDismissRequest = { showSort = false },
                title = { Text("Ordenar por") },
                text = {
                    Column {
                        ListSort.values().forEach { s ->
                            androidx.compose.material3.TextButton(onClick = {
                                sort = s
                                showSort = false
                            }) {
                                Text(
                                    s.label + if (sort == s) "  ✓" else "",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = { showSort = false }) {
                        Text("Cerrar")
                    }
                },
            )
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun TrackListView(title: String, tracks: List<MediaTrack>, onOpenCtx: OpenCtx) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${tracks.size} pistas",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    OutlinedButton(onClick = { playTracks(tracks, 0) }) { Text("▶ Todo") }
                }
            }
            itemsIndexed(tracks) { i, t ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { playTracks(tracks, i) },
                            onLongClick = { onOpenCtx(t, tracks, i) },
                        )
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
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    // -------------------------------------------------------- search tab

    private enum class SearchFilter(val label: String) {
        ALL("Todo"), TITLE("Título"), ARTIST("Artista"), ALBUM("Álbum"),
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun SearchTab(onOpenCtx: OpenCtx = { _, _, _ -> }) {
        var query by remember { mutableStateOf("") }
        var mode by remember { mutableStateOf(SearchFilter.ALL) }
        var history by remember { mutableStateOf(SearchHistory.load(this)) }

        Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SearchFilter.values().forEach { f ->
                    OutlinedButton(onClick = { mode = f }) {
                        Text(
                            f.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (mode == f) MaterialTheme.colorScheme.primary
                            else LocalContentColor.current,
                        )
                    }
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Buscar…") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    SearchHistory.add(this@MainActivity, query)
                    history = SearchHistory.load(this@MainActivity)
                }),
            )
            Spacer(Modifier.height(8.dp))

            if (query.isBlank()) {
                if (history.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Historial", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.weight(1f))
                        androidx.compose.material3.TextButton(onClick = {
                            SearchHistory.clear(this@MainActivity)
                            history = emptyList()
                        }) { Text("🗑 Borrar") }
                    }
                    history.forEach { h ->
                        androidx.compose.material3.TextButton(onClick = { query = h }) {
                            Text("🕘 $h", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                } else {
                    Text(
                        "Busca por título, artista o álbum.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                Spacer(Modifier.weight(1f))
                return@Column
            }

            val q = query.trim()
            val results = libraryOrEmpty.filter { t ->
                when (mode) {
                    SearchFilter.TITLE -> t.title.contains(q, ignoreCase = true)
                    SearchFilter.ARTIST -> t.artist.contains(q, ignoreCase = true)
                    SearchFilter.ALBUM -> t.album.contains(q, ignoreCase = true)
                    SearchFilter.ALL ->
                        t.title.contains(q, ignoreCase = true) ||
                            t.artist.contains(q, ignoreCase = true) ||
                            t.album.contains(q, ignoreCase = true)
                }
            }
            Text(
                "${results.size} resultados",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                itemsIndexed(results) { i, t ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    SearchHistory.add(this@MainActivity, q)
                                    history = SearchHistory.load(this@MainActivity)
                                    playTracks(results, i)
                                },
                                onLongClick = { onOpenCtx(t, results, i) },
                            )
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
    }

    // ---------------------------------------------------------- queue tab

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun QueueTab(snap: PlayerSnapshot, onOpenCtx: OpenCtx = { _, _, _ -> }) {
        // Subscribe to orchestrator publishes (same-process queue channel).
        val ver = QueueBus.version.value
        val tracks = QueueBus.tracks

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item {
                Text("Cola", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${tracks.size} pistas · toca una para saltar a ella",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            if (tracks.isEmpty()) {
                item {
                    Text(
                        "La cola está vacía: reproduce algo desde la Biblioteca.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            itemsIndexed(tracks) { i, t ->
                val isCurrent = t.uri == snap.uri && snap.uri.isNotEmpty()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .let { m ->
                            if (isCurrent) {
                                m.background(Color(0x2239A7E4))
                            } else m
                        }
                        .clip(RoundedCornerShape(8.dp))
                        .combinedClickable(
                            onClick = { if (!isCurrent) sendSeekQueue(i) },
                            onLongClick = { onOpenCtx(t, tracks, i) },
                        )
                        .padding(vertical = 4.dp, horizontal = 6.dp),
                ) {
                    Text(
                        "${i + 1}. ${t.title}" + if (isCurrent) "  ▶" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isCurrent) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "${t.artist} — ${t.album}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    // --------------------------------------------------- playback helpers

    /** Insert a track into the queue right after the current one (or at end). */
    private fun sendEnqueueNext(t: MediaTrack, atEnd: Boolean) {
        val c = controller ?: return
        val bundle = Bundle().apply {
            putString(PlaybackService.EXTRA_TRACKS_JSON, MediaTrack.listToJson(listOf(t)))
            putBoolean(PlaybackService.EXTRA_ENQUEUE_END, atEnd)
        }
        android.util.Log.i(TAG, "enqueueNext uri=${t.uri} atEnd=$atEnd")
        c.sendCustomCommand(
            androidx.media3.session.SessionCommand(PlaybackService.CMD_ENQUEUE_NEXT, bundle),
            bundle,
        )
    }

    private fun sendSeekQueue(index: Int) {
        val c = controller ?: return
        val bundle = Bundle().apply { putInt(PlaybackService.EXTRA_START_INDEX, index) }
        c.sendCustomCommand(
            androidx.media3.session.SessionCommand(PlaybackService.CMD_SEEK_QUEUE, bundle),
            bundle,
        )
    }

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

    // -------------------------------------- track context menu (sprint 6)

    private fun extLabel(uriStr: String): String =
        uriStr.substringBefore('?').substringAfterLast('.', "").uppercase().ifBlank { "?" }

    /**
     * Bottom-sheet context menu for a track (block 4). Long-press opens it
     * from queue/list/search/folder rows.
     */
    @Composable
    private fun TrackContextMenu(
        track: MediaTrack,
        onDismiss: () -> Unit,
        onPlay: () -> Unit,
        onEnqueueNext: () -> Unit,
        onEnqueueEnd: () -> Unit,
        onArtist: () -> Unit,
        onAlbum: () -> Unit,
        onFolder: () -> Unit,
        onLyrics: () -> Unit,
        onAddToPlaylist: () -> Unit = {},
    ) {
        // BACK handled inside the overlay: inner-most BackHandler wins.
        BackHandler { onDismiss() }
        var ratTick by remember { mutableStateOf(0) }
        val scrim = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x88000000))
                .clickable(
                    interactionSource = scrim,
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(
                    topStart = 18.dp, topEnd = 18.dp, bottomStart = 0.dp, bottomEnd = 0.dp,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                    // ---- header: title / artist / dur|formato + like/dislike
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                track.title,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                            )
                            Text(
                                "${track.artist} — ${track.album}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                                maxLines = 1,
                            )
                            Text(
                                "${fmtDur(track.durationMs)} · ${extLabel(track.uri)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                        val rated = if (ratTick >= 0) {
                            RatingsStore.rating(this@MainActivity, track.uri)
                        } else 0
                        androidx.compose.material3.TextButton(onClick = {
                            RatingsStore.toggle(this@MainActivity, track.uri, 1)
                            ratTick++
                        }) {
                            Text(
                                "👍",
                                color = if (rated == 1) Color(0xFF39A7E4)
                                else LocalContentColor.current,
                            )
                        }
                        androidx.compose.material3.TextButton(onClick = {
                            RatingsStore.toggle(this@MainActivity, track.uri, -1)
                            ratTick++
                        }) {
                            Text(
                                "👎",
                                color = if (rated == -1) Color(0xFFE4394E)
                                else LocalContentColor.current,
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    // ---- actions
                    val actions = buildList {
                        add("▶" to ("Reproducir ahora" to onPlay))
                        add("⏭" to ("Encolar siguiente" to onEnqueueNext))
                        add("⤓" to ("Encolar al final de la cola" to onEnqueueEnd))
                        if (track.artist.isNotBlank()) {
                            add("🎤" to ("Ir a: ${track.artist}" to onArtist))
                        }
                        if (track.album.isNotBlank()) {
                            add("💿" to ("Ir a: ${track.album}" to onAlbum))
                        }
                        add("📁" to ("Ir a: carpeta" to onFolder))
                        add("🔖" to ("Añadir a Playlist" to onAddToPlaylist))
                        add("📝" to ("Letras" to onLyrics))
                    }
                    actions.forEach { (glyph, pair) ->
                        val (label, action) = pair
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { action() }
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(glyph, modifier = Modifier.width(40.dp))
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }

    // --------------------------------------------- add to playlist (sprint 10)

    /**
     * Bottom sheet behind "Añadir a Playlist": pick an existing list (tap) or
     * create a new one inline. Feedback via toast; duplicates are reported and
     * re-added silently keeps the list unchanged (`PlaylistStore.addTrack`).
     */
    @Composable
    private fun AddToPlaylistSheet(
        track: MediaTrack,
        onClose: () -> Unit,
    ) {
        BackHandler { onClose() }
        var names by remember { mutableStateOf(PlaylistStore.list(this)) }
        var showNew by remember { mutableStateOf(names.isEmpty()) }
        var newName by remember { mutableStateOf("") }
        val scrim = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }

        fun toast(text: String) =
            android.widget.Toast.makeText(
                this, text, android.widget.Toast.LENGTH_SHORT,
            ).show()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x88000000))
                .clickable(interactionSource = scrim, indication = null, onClick = onClose),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
                tonalElevation = 8.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                        .clickable(enabled = false) {},
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        track.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                    )
                    if (!showNew) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 320.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { showNew = true }
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .padding(vertical = 8.dp, horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("＋", modifier = Modifier.width(30.dp))
                                    Text(
                                        "Nueva playlist…",
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                            itemsIndexed(names) { _, name ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            val added =
                                                PlaylistStore.addTrack(this@MainActivity, name, track)
                                            toast(
                                                if (added) "Añadida a $name"
                                                else "Ya estaba en $name",
                                            )
                                            onClose()
                                        }
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .padding(vertical = 8.dp, horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("🔖", modifier = Modifier.width(30.dp))
                                    Text(
                                        name,
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        "${PlaylistStore.tracks(this@MainActivity, name).size} pistas",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline,
                                    )
                                }
                            }
                        }
                    }
                    if (showNew) {
                        AlertDialog(
                            onDismissRequest = { showNew = false },
                            title = { Text("Nueva playlist") },
                            text = {
                                OutlinedTextField(
                                    value = newName,
                                    onValueChange = { newName = it },
                                    label = { Text("Nombre") },
                                )
                            },
                            confirmButton = {
                                androidx.compose.material3.TextButton(
                                    enabled = newName.isNotBlank(),
                                    onClick = {
                                        val stored =
                                            PlaylistStore.create(this@MainActivity, newName)
                                        PlaylistStore.addTrack(
                                            this@MainActivity, stored, track,
                                        )
                                        toast("Añadida a $stored")
                                        showNew = false
                                        onClose()
                                    },
                                ) { Text("Crear y añadir") }
                            },
                            dismissButton = {
                                androidx.compose.material3.TextButton(
                                    onClick = { showNew = false },
                                ) { Text("Cancelar") }
                            },
                        )
                    }
                }
            }
        }
    }

    // ------------------------------------------------ lyrics (sprint 6)
    /**
     * Reads the synced .lrc sitting next to an audio file (block 5). Tries,
     * in order: file-system path (file:/absolute uris), MediaStore DATA of
     * the audio row, then a MediaStore Files row with the same basename.
     */
    private fun findLyrics(context: Context, uriStr: String): List<LrcLine>? {
        // 1) Resolve an underlying file path (best effort).
        val audioPath: String? = when {
            uriStr.startsWith("file://") -> Uri.parse(uriStr).path
            uriStr.startsWith("/") -> uriStr
            uriStr.startsWith("content://media") -> {
                runCatching {
                    context.contentResolver.query(
                        Uri.parse(uriStr),
                        arrayOf(android.provider.MediaStore.MediaColumns.DATA),
                        null, null, null,
                    )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                }.getOrNull()
            }
            else -> null
        }
        if (audioPath.isNullOrEmpty()) return null
        val base = audioPath.substringBeforeLast('.')

        // 2) Direct file read (works for app dirs / pre-R / permissive roms).
        runCatching {
            val f = File("$base.lrc")
            if (f.exists()) f.readText() else null
        }.getOrNull()?.let { txt ->
            val lines = LrcParser.parse(txt)
            if (lines.isNotEmpty()) {
                android.util.Log.i(TAG, "lyrics source=file base=$base n=${lines.size}")
                return lines
            }
        }

        // 3) MediaStore Files row with the same basename in the same folder.
        if (Build.VERSION.SDK_INT >= 29) {
            val name = base.substringAfterLast('/') + ".lrc"
            val relDir = audioPath
                .removePrefix("/storage/emulated/0/")
                .substringBeforeLast('/')
                .trimEnd('/') + "/"
            runCatching {
                val files = MediaStore.Files.getContentUri("external")
                val id = context.contentResolver.query(
                    files,
                    arrayOf(MediaStore.MediaColumns._ID),
                    "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND " +
                        "${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
                    arrayOf(name, relDir),
                    null,
                )?.use { c -> if (c.moveToFirst()) c.getLong(0) else null }
                if (id != null) {
                    val uri = android.content.ContentUris.withAppendedId(files, id)
                    context.contentResolver.openInputStream(uri)?.use {
                        it.readBytes().toString(Charsets.UTF_8)
                    }
                } else null
            }.getOrNull()?.let { txt ->
                val lines = LrcParser.parse(txt)
                if (lines.isNotEmpty()) {
                    android.util.Log.i(
                        TAG, "lyrics source=mediastore rel=$relDir n=${lines.size}",
                    )
                    return lines
                }
            }
        }
        // SAF documents (imported trees) are not followed this sprint.
        android.util.Log.i(TAG, "lyrics: no .lrc found for $uriStr")
        return null
    }

    @Composable
    private fun LyricsScreen(track: MediaTrack, isCurrent: Boolean, onClose: () -> Unit) {
        // BACK handled inside the overlay: inner-most BackHandler wins.
        BackHandler { onClose() }
        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
        var lines by remember(track.uri) { mutableStateOf<List<LrcLine>?>(null) }
        androidx.compose.runtime.LaunchedEffect(track.uri) {
            // null lookup result maps to an empty list so the UI leaves the
            // "Buscando letras…" state and renders the "Sin letras" note.
            val found = withContext(Dispatchers.IO) {
                findLyrics(this@MainActivity, track.uri) ?: emptyList()
            }
            lines = found
        }
        var posMs by remember { mutableStateOf(0L) }
        if (isCurrent) {
            androidx.compose.runtime.LaunchedEffect(track.uri) {
                while (currentCoroutineContext().isActive) {
                    posMs = controller?.currentPosition ?: 0L
                    delay(200)
                }
            }
        }
        val parsed = lines
        val active = if (isCurrent && parsed != null) {
            LrcParser.activeIndex(parsed, posMs)
        } else -1
        var follow by remember { mutableStateOf(true) }
        // True while our own auto-scroll animation runs, so the user-scroll
        // watcher below does not treat it as a manual scroll.
        var pendingAuto by remember { mutableStateOf(false) }
        androidx.compose.runtime.LaunchedEffect(listState.isScrollInProgress) {
            if (listState.isScrollInProgress && !pendingAuto) follow = false
        }
        androidx.compose.runtime.LaunchedEffect(active, follow) {
            if (follow && active >= 0 && !listState.isScrollInProgress) {
                pendingAuto = true
                listState.animateScrollToItem(active.coerceIn(0, (parsed?.size ?: 1) - 1))
                pendingAuto = false
            }
        }

        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.TextButton(onClick = onClose) { Text("←") }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            track.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee(),
                        )
                        Text(
                            "${track.artist} · ${fmtDur(track.durationMs)} · " +
                                "${extLabel(track.uri)}" +
                                if (!isCurrent) " (no es la pista actual)" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    if (!follow && active >= 0) {
                        androidx.compose.material3.TextButton(onClick = {
                            follow = true
                        }) { Text("⏯ Seguir") }
                    }
                }
                Spacer(Modifier.height(6.dp))
                when (val l = parsed) {
                    null -> Text(
                        "Buscando letras…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    emptyList<LrcLine>() -> Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text("Sin letras", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "No hay archivo .lrc junto a esta pista.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    else -> LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        item { Spacer(Modifier.height(40.dp)) }
                        itemsIndexed(l) { i, line ->
                            val isActive = i == active
                            Text(
                                line.text,
                                style = if (isActive) {
                                    MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                    )
                                } else {
                                    MaterialTheme.typography.bodyMedium
                                },
                                color = if (isActive) LocalContentColor.current
                                else MaterialTheme.colorScheme.outline,
                                modifier = Modifier
                                    .clickable(enabled = isCurrent) {
                                        controller?.seekTo(line.atMs)
                                        follow = true
                                    }
                                    .padding(horizontal = 4.dp),
                            )
                        }
                        item { Spacer(Modifier.height(40.dp)) }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------- settings screen

    private enum class SettingsSub { LOOKFEEL, LIBRARY, ABOUT, AUDIO, HEADSET, ART, VIZ }

    /** Prefs included in data export/import (block 6). */
    private val BACKUP_PREFS = listOf(
        "dsp_presets", "playback_settings", "appearance",
        "search_history", "track_ratings", "audio_settings", "art_settings",
        "library_settings", "viz_settings",
    )

    /**
     * Settings root + sub-screens (Look & Feel live, Library actions, About).
     * Rendered as an opaque overlay above the current tab; BACK handled by
     * the overlay (registered AFTER the tab-level handler so it wins).
     */
    @Composable
    private fun SettingsScreen(
        themeMode: String,
        onThemeMode: (String) -> Unit,
        accent: Int?,
        onAccent: (Int?) -> Unit,
        bold: Boolean,
        onBold: (Boolean) -> Unit,
        startAtLibrary: Boolean,
        onStartAtLibrary: (Boolean) -> Unit,
        onClose: () -> Unit,
        currentSnapshot: PlayerSnapshot,
    ) {
        var sub by remember { mutableStateOf<SettingsSub?>(null) }
        var note by remember { mutableStateOf("") }

        BackHandler(enabled = sub != null) { sub = null }

        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                // header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.TextButton(onClick = {
                        if (sub != null) sub = null else onClose()
                    }) { Text(if (sub == null) "✕" else "←") }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (sub == null) "Ajustes" else when (sub) {
                                SettingsSub.LOOKFEEL -> "Look and Feel"
                                SettingsSub.LIBRARY -> "Biblioteca"
                                SettingsSub.AUDIO -> "Audio"
                                SettingsSub.HEADSET -> "Headset/Bluetooth"
                                SettingsSub.ART -> "Album Art"
                                SettingsSub.VIZ -> "Visualización"
                                else -> "Acerca de"
                            },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (note.isNotEmpty()) {
                            Text(
                                note,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    androidx.compose.material3.TextButton(onClick = onClose) { Text("Cerrar") }
                }
                when (sub) {
                    SettingsSub.LOOKFEEL -> LookAndFeelScreen(
                        themeMode, onThemeMode, accent, onAccent, bold, onBold,
                        startAtLibrary, onStartAtLibrary,
                    )
                    SettingsSub.LIBRARY -> SettingsLibraryScreen { note = it }
                    SettingsSub.AUDIO -> AudioSettingsScreen()
                    SettingsSub.HEADSET -> HeadsetSettingsScreen()
                    SettingsSub.ART -> ArtSettingsScreen(currentSnapshot)
                    SettingsSub.VIZ -> VizSettingsScreen { note = it }
                    SettingsSub.ABOUT -> AboutScreen()
                    null -> SettingsRoot { s ->
                        note = ""
                        sub = s
                    }
                }
            }
        }
    }

    // -------------------------------------------------------- settings root

    private data class SettingsRow(
        val glyph: String,
        val title: String,
        val summary: String,
        val sub: SettingsSub?,
    )

    @Composable
    private fun SettingsRoot(onOpen: (SettingsSub) -> Unit) {
        val rows = listOf(
            SettingsRow("🎨", "Look and Feel", "Tema, acento, tipografía", SettingsSub.LOOKFEEL),
            SettingsRow("🎵", "Audio", "Focus/Replay Gain — bloque 8", SettingsSub.AUDIO),
            SettingsRow(
                "📈", "Visualización",
                "Espectro player/EQ, estilo, opacidad — bloque 14", SettingsSub.VIZ,
            ),
            SettingsRow("🖼", "Background", "Fondo — en Album Art (bl. 13)", null),
            SettingsRow("💿", "Album Art", "Carátulas + fondo — bloque 12/13", SettingsSub.ART),
            SettingsRow("📚", "Biblioteca", "Rescan, progreso, stats, filtros", SettingsSub.LIBRARY),
            SettingsRow("🎧", "Headset/Bluetooth", "Botones y conexión — bloque 10", SettingsSub.HEADSET),
            SettingsRow("🔒", "Lock Screen", "Opciones de lockscreen", null),
            SettingsRow("⚗", "Misc", "Scrobbling/Android Auto — sprint 9", null),
            SettingsRow("ℹ", "About", "Versión y datos", SettingsSub.ABOUT),
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
        ) {
            itemsIndexed(rows) { _, r ->
                val enabled = r.sub != null
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(if (enabled) 1f else 0.45f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(enabled = enabled) { r.sub?.let(onOpen) }
                        .padding(vertical = 10.dp, horizontal = 6.dp),
                ) {
                    Text(
                        "${r.glyph}  ${r.title}",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        r.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
            item {
                Spacer(Modifier.height(6.dp))
                Text("Datos", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        val msg = runCatching { exportData() }
                            .getOrElse { "Export falló: ${it.message}" }
                        RootNote.note = msg
                    }) { Text("⬆ Export") }
                    val importPicker = rememberLauncherForActivityResult(
                        ActivityResultContracts.OpenDocument(),
                    ) { uri ->
                        if (uri != null) {
                            RootNote.note = runCatching { importData(uri) }
                                .getOrElse { "Import falló: ${it.message}" }
                        }
                    }
                    OutlinedButton(onClick = {
                        importPicker.launch(arrayOf("application/json", "text/*", "*/*"))
                    }) { Text("⬇ Import") }
                }
                OutlinedButton(onClick = {
                    RootNote.note = runCatching { importLatestBackup() }
                        .getOrElse { "Import falló: ${it.message}" }
                }) { Text("⏰ Último backup") }
                Text(
                    RootNote.note,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }

    /** Shared mutable note so import results reach the UI (simple app scope). */
    private object RootNote {
        var note: String by androidx.compose.runtime.mutableStateOf("")
    }

    @Composable
    private fun LookAndFeelScreen(
        themeMode: String,
        onThemeMode: (String) -> Unit,
        accent: Int?,
        onAccent: (Int?) -> Unit,
        bold: Boolean,
        onBold: (Boolean) -> Unit,
        startAtLibrary: Boolean,
        onStartAtLibrary: (Boolean) -> Unit,
    ) {
        var showThemeDialog by remember { mutableStateOf(false) }
        var showAccentDialog by remember { mutableStateOf(false) }

        Column(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = bold, onCheckedChange = onBold)
                Text("  Fuente negrita", style = MaterialTheme.typography.bodyLarge)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = startAtLibrary, onCheckedChange = onStartAtLibrary)
                Text("  Arrancar en Biblioteca", style = MaterialTheme.typography.bodyLarge)
            }
            OutlinedButton(onClick = { showThemeDialog = true }) {
                val label = when (themeMode) {
                    AppearanceStore.MODE_LIGHT -> "Claro"
                    AppearanceStore.MODE_DARK -> "Oscuro"
                    else -> "Automático"
                }
                Text("Tema: $label ▸")
            }
            Spacer(Modifier.height(4.dp))
            OutlinedButton(onClick = { showAccentDialog = true }) {
                Text("Acento: ${AppearanceStore.accentName(this@MainActivity)} ▸")
            }
        }

        if (showThemeDialog) {
            AlertDialog(
                onDismissRequest = { showThemeDialog = false },
                title = { Text("Settings Theme") },
                text = {
                    Column {
                        listOf(
                            "Automático" to AppearanceStore.MODE_AUTO,
                            "Claro" to AppearanceStore.MODE_LIGHT,
                            "Oscuro" to AppearanceStore.MODE_DARK,
                        ).forEach { (label, mode) ->
                            TextButton({
                                onThemeMode(mode)
                                showThemeDialog = false
                            }) {
                                Text(
                                    label + if (themeMode == mode) "  ✓" else "",
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton({ showThemeDialog = false }) { Text("Cerrar") }
                },
            )
        }
        if (showAccentDialog) {
            AlertDialog(
                onDismissRequest = { showAccentDialog = false },
                title = { Text("Acento") },
                text = {
                    Column {
                        TextButton({
                            onAccent(null)
                            showAccentDialog = false
                        }) { Text("Predeterminado" + if (accent == null) "  ✓" else "") }
                        AppearanceStore.ACCENTS.forEach { (name, argb) ->
                            TextButton({
                                onAccent(argb)
                                showAccentDialog = false
                            }) {
                                Text(
                                    name + if (accent == argb) "  ✓" else "",
                                    color = Color(argb),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton({ showAccentDialog = false }) { Text("Cerrar") }
                },
            )
        }
    }

    @Composable
    private fun SettingsLibraryScreen(onNote: (String) -> Unit) {
        val ctx = this@MainActivity
        var storeProgress by remember { mutableStateOf(LibrarySettings.storeProgress(ctx)) }
        var progressMin by remember { mutableStateOf(LibrarySettings.progressMinMinutes(ctx)) }
        var countPct by remember { mutableStateOf(LibrarySettings.countPct(ctx)) }
        var ignoreShort by remember { mutableStateOf(LibrarySettings.ignoreShort(ctx)) }
        var stats by remember { mutableStateOf(TrackStateStore.statsSummary(ctx)) }

        Column(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
            Text(
                "${libraryOrEmpty.size} pistas en MediaStore · " +
                    "${libraryOrEmpty.count { it.uri.startsWith("file:") || it.uri.startsWith("/") }} importadas (SAF)",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = {
                libraryOrEmpty = MediaStoreLibrary.query(this@MainActivity)
                onNote("Rescan completado: ${libraryOrEmpty.size} pistas")
            }) { Text("⭮ Rescan") }

            Spacer(Modifier.height(12.dp))
            Text("Per Track Progress", style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = storeProgress, onCheckedChange = { on ->
                    LibrarySettings.setStoreProgress(ctx, on)
                    storeProgress = on
                })
                Text("  Almacenar progreso por pista", style = MaterialTheme.typography.bodyLarge)
            }
            Text(
                "Solo pistas de al menos $progressMin min guardan posición " +
                    "(podcasts/sets); al reabrirlas continúan donde quedaron.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Text("Umbral: $progressMin min", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = progressMin.toFloat(),
                onValueChange = { v ->
                    LibrarySettings.setProgressMinMinutes(ctx, v.roundToInt())
                    progressMin = LibrarySettings.progressMinMinutes(ctx)
                },
                valueRange = 1f..90f,
                steps = 88,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))
            Text("Count as played", style = MaterialTheme.typography.titleSmall)
            Text(
                "Una escucha cuenta tras reproducir al menos el $countPct % " +
                    "(avance natural cuenta siempre; pausar no cuenta).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Text("Umbral: $countPct %", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = countPct.toFloat(),
                onValueChange = { v ->
                    LibrarySettings.setCountPct(ctx, v.roundToInt())
                    countPct = LibrarySettings.countPct(ctx)
                },
                valueRange = 1f..100f,
                steps = 98,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))
            Text("Filtros de biblioteca", style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = ignoreShort, onCheckedChange = { on ->
                    LibrarySettings.setIgnoreShort(ctx, on)
                    ignoreShort = on
                })
                Text("  Ignorar pistas cortas (< 6 s)", style = MaterialTheme.typography.bodyLarge)
            }
            Text(
                "Notificaciones/melodías excluidas del rescan. Video ya queda " +
                    "fuente de los datos (solo MediaStore.Audio). Requiere rescan.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )

            Spacer(Modifier.height(12.dp))
            Text("Estadísticas", style = MaterialTheme.typography.titleSmall)
            Text(
                "${stats.first} pistas reproducidas · ${stats.second} escuchas totales",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(onClick = {
                TrackStateStore.resetStats(ctx)
                stats = TrackStateStore.statsSummary(ctx)
                onNote("Estadísticas reiniciadas (posiciones por pista conservadas)")
            }) { Text("🗑 Reset stats") }
            Text(
                "Progreso/stats viven en filesDir/track_state.json, no se borran " +
                    "en el rescan. Reset solo limpia conteos, NO las posiciones.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }

    /**
     * Visualization settings (sprint 10 — block 14 núcleo). Live effect:
     * `SpectrumBars` re-reads `viz_settings` every 50 ms, so these controls act
     * immediately on the player and the EQ screen. Display-only settings:
     * no session push needed.
     */
    @Composable
    private fun VizSettingsScreen(onNote: (String) -> Unit) {
        val ctx = this@MainActivity
        var playerOn by remember { mutableStateOf(VizSettings.playerSpectrum(ctx)) }
        var style by remember { mutableStateOf(VizSettings.spectrumStyle(ctx)) }
        var opacity by remember { mutableStateOf(VizSettings.panelOpacity(ctx)) }
        var eqMode by remember { mutableStateOf(VizSettings.eqSpectrum(ctx)) }

        Column(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
            Text(
                "Espectro del player (barra en vivo sobre el fondo)",
                style = MaterialTheme.typography.titleSmall,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = playerOn, onCheckedChange = { on ->
                    VizSettings.setPlayerSpectrum(ctx, on)
                    playerOn = on
                    onNote(if (on) "Espectro del player ON" else "Espectro del player OFF")
                })
                Text("  Visualization On Player Screen", style = MaterialTheme.typography.bodyLarge)
            }
            Text(
                "Visible en el player cuando se reproduce (también con el botón 📊 " +
                    "de la cabecera). Estilo y opacidad aplican a player y EQ.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )

            Spacer(Modifier.height(12.dp))
            Text("Estilo de las barras", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = {
                    VizSettings.setSpectrumStyle(ctx, VizSettings.STYLE_CLASSIC)
                    style = VizSettings.spectrumStyle(ctx)
                }) {
                    Text(
                        "▦ Classic",
                        color = if (style == VizSettings.STYLE_CLASSIC)
                            MaterialTheme.colorScheme.primary else LocalContentColor.current,
                    )
                }
                OutlinedButton(onClick = {
                    VizSettings.setSpectrumStyle(ctx, VizSettings.STYLE_ROUNDED)
                    style = VizSettings.spectrumStyle(ctx)
                }) {
                    Text(
                        "● Rounded",
                        color = if (style == VizSettings.STYLE_ROUNDED)
                            MaterialTheme.colorScheme.primary else LocalContentColor.current,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Text("Opacidad del panel", style = MaterialTheme.typography.titleSmall)
            Text(
                "Top Visualization Panel Opacity — $opacity %",
                style = MaterialTheme.typography.bodySmall,
            )
            Slider(
                value = opacity.toFloat(),
                onValueChange = { v ->
                    VizSettings.setPanelOpacity(ctx, v.roundToInt())
                    opacity = VizSettings.panelOpacity(ctx)
                },
                valueRange = 0f..100f,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))
            Text("Equalizer Screen Spectrum", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    "Disabled" to VizSettings.EQ_DISABLED,
                    "Classic" to VizSettings.STYLE_CLASSIC,
                    "Rounded" to VizSettings.STYLE_ROUNDED,
                ).forEach { (label, mode) ->
                    OutlinedButton(onClick = {
                        VizSettings.setEqSpectrum(ctx, mode)
                        eqMode = VizSettings.eqSpectrum(ctx)
                    }) {
                        Text(
                            label,
                            color = if (eqMode == mode)
                                MaterialTheme.colorScheme.primary else LocalContentColor.current,
                        )
                    }
                }
            }
            Text(
                "Referencia del catálogo: Disabled / Classic / Rounded. Big Spectrum " +
                    "MilkDrop (presets .milk), Visualization Delay y opciones de render " +
                    "(HD/Crop/FPS) siguen pendientes: Poweramp carga presets de terceros.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }

    @Composable
    private fun AboutScreen() {
        Column(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
            val ver = runCatching {
                packageManager.getPackageInfo(packageName, 0).versionName
            }.getOrNull() ?: "?"
            Text("PowerampFree — reconstrucción original", style = MaterialTheme.typography.titleMedium)
            Text(
                "paquete: $packageName\nversión: $ver\n" +
                    "motor: :core:audio (paramEQ/crossfade/limiter) · MediaStore + SAF",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }

    // -------------------------------------------- audio / headset screens (7)

    /**
     * Block 8 audio screen: focus behaviour toggles + Replay Gain. RG changes
     * persist to audio_settings and push the trim live to the session.
     */
    @Composable
    private fun AudioSettingsScreen() {
        val ctx = this@MainActivity
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
    private fun HeadsetSettingsScreen() {
        val ctx = this@MainActivity
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

    /**
     * Blocks 12/13 (sprint 8): carátulas + fondo derivado. Display-only
     * settings (no session push); read live by the composables.
     */
    @Composable
    private fun ArtSettingsScreen(currentSnapshot: PlayerSnapshot) {
        val ctx = this@MainActivity
        var highRes by remember { mutableStateOf(ArtSettings.highResArt(ctx)) }
        var keepAspect by remember { mutableStateOf(ArtSettings.keepAspect(ctx)) }
        var bgOn by remember { mutableStateOf(ArtSettings.bgEnabled(ctx)) }
        var blur by remember { mutableStateOf(ArtSettings.bgBlur(ctx)) }
        var gradient by remember { mutableStateOf(ArtSettings.bgGradient(ctx)) }

        Column(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
            Text("Carátula", style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = highRes, onCheckedChange = { on ->
                    ArtSettings.setHighResArt(ctx, on)
                    highRes = on
                })
                Text("  Enviar carátula en alta resolución", style = MaterialTheme.typography.bodyLarge)
            }
            Text(
                "Alta resolución: bitmap completo hacia la sesión/notificación; " +
                    "OFF limita a 512 px.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = keepAspect, onCheckedChange = { on ->
                    ArtSettings.setKeepAspect(ctx, on)
                    keepAspect = on
                })
                Text("  Mantener proporción", style = MaterialTheme.typography.bodyLarge)
            }
            Text(
                "Fuentes: carátula de álbum del sistema (MediaStore por albumId) " +
                    "y Cover.jpg/… junto al archivo para pistas con ruta directa. " +
                    "Pistas por SAF árbol sin ruta local muestran glifo genérico.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            // Diagnóstico block 12: what the art lookup currently sees.
            Text(
                "Pista actual: albumId=${currentSnapshot.albumId} · " +
                    "carpeta=${currentSnapshot.folder.ifEmpty { "—" }} " +
                    "(bus n=${QueueBus.tracks.size} i=${QueueBus.index})",
                style = MaterialTheme.typography.bodySmall,
            )

            Spacer(Modifier.height(12.dp))
            Text("Fondo derivado (bloque 13)", style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = bgOn, onCheckedChange = { on ->
                    ArtSettings.setBgEnabled(ctx, on)
                    bgOn = on
                })
                Text("  Fondo borroso del player", style = MaterialTheme.typography.bodyLarge)
            }
            Text(
                "Desenfoque de fondo: $blur",
                style = MaterialTheme.typography.bodySmall,
            )
            Slider(
                value = blur.toFloat(),
                onValueChange = { v ->
                    blur = v.toInt().coerceIn(0, ArtSettings.BLUR_MAX)
                    ArtSettings.setBgBlur(ctx, blur)
                },
                valueRange = 0f..ArtSettings.BLUR_MAX.toFloat(),
                steps = ArtSettings.BLUR_MAX - 1,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Gradiente del fondo: $gradient",
                style = MaterialTheme.typography.bodySmall,
            )
            Slider(
                value = gradient.toFloat(),
                onValueChange = { v ->
                    gradient = v.toInt().coerceIn(0, ArtSettings.GRADIENT_MAX)
                    ArtSettings.setBgGradient(ctx, gradient)
                },
                valueRange = 0f..ArtSettings.GRADIENT_MAX.toFloat(),
                steps = ArtSettings.GRADIENT_MAX - 1,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(onClick = {
                AlbumArtLoader.clear()
                RootNote.note = "Cache de carátulas limpiada"
            }) { Text("🗑 Borrar cache de carátulas") }
        }
    }

    // ------------------------------------------------- export / import data

    /** Preferences + EQ preset files → JSON in Downloads (block 6). */
    private fun exportData(): String {
        val root = org.json.JSONObject()
        root.put("app", "powerampfree-recreation")
        root.put("version", 1)
        root.put("when", System.currentTimeMillis())
        val prefsObj = org.json.JSONObject()
        for (name in BACKUP_PREFS) {
            val sp = getSharedPreferences(name, MODE_PRIVATE)
            val o = org.json.JSONObject()
            for ((k, v) in sp.all) o.put(k, org.json.JSONObject.wrap(v))
            prefsObj.put(name, o)
        }
        root.put("prefs", prefsObj)
        // Per-track state (block 11): progress + counts, only live entries.
        val trackState = org.json.JSONObject()
        for ((uri, s) in TrackStateStore.snapshotJson(this)) {
            if (s.positionMs <= 0L && s.playedCount <= 0) continue
            trackState.put(
                uri,
                org.json.JSONObject()
                    .put("pos", s.positionMs)
                    .put("plays", s.playedCount)
                    .put("last", s.lastPlayedMs),
            )
        }
        root.put("trackState", trackState)
        val presets = org.json.JSONArray()
        val dir = File(filesDir, "eq")
        dir.listFiles { f -> f.isFile && f.name.endsWith(".json") }?.forEach { f ->
            runCatching {
                presets.put(
                    org.json.JSONObject().put("file", f.name).put("json", f.readText()),
                )
            }
        }
        root.put("presets", presets)
        // Playlists (sprint 10): each file is self-contained JSON.
        val playlists = org.json.JSONArray()
        File(filesDir, "playlists")
            .listFiles { f -> f.isFile && f.name.endsWith(".json") }?.forEach { f ->
                runCatching {
                    playlists.put(
                        org.json.JSONObject().put("file", f.name).put("json", f.readText()),
                    )
                }
            }
        root.put("playlists", playlists)

        val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
            .format(java.util.Date())
        val displayName = "PowerampFree-backup-$stamp.json"
        if (Build.VERSION.SDK_INT >= 29) {
            val cv = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Documents/PowerampFree")
            }
            val uri = contentResolver.insert(
                MediaStore.Files.getContentUri("external"),
                cv,
            ) ?: return "Export: no se creó el archivo"
            contentResolver.openOutputStream(uri)?.use { os ->
                os.write(root.toString(2).toByteArray(Charsets.UTF_8))
            } ?: return "Export: no se pudo abrir el stream"
            return "Export OK → Documents/PowerampFree/$displayName"
        } else {
            // Pre-Q scoped storage: app-specific public dir (still user-visible).
            val out = java.io.File(getExternalFilesDir(null), displayName)
            out.writeText(root.toString(2))
            return "Export OK → ${out.absolutePath}"
        }
    }

    /** Newest export in Documents/PowerampFree via MediaStore (no SAF UI). */
    private fun importLatestBackup(): String {
        if (Build.VERSION.SDK_INT < 29) return "Import último: requiere Android 10+"
        val sel =
            "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND " +
            "${MediaStore.MediaColumns.MIME_TYPE} = ?"
        val args = arrayOf("Documents/PowerampFree/", "application/json")
        contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME),
            sel, args, "${MediaStore.MediaColumns._ID} DESC",
        )?.use { c ->
            if (c.moveToFirst()) {
                val id = c.getLong(0)
                val uri = android.content.ContentUris.withAppendedId(
                    MediaStore.Files.getContentUri("external"), id,
                )
                return importData(uri)
            }
        }
        return "Import último: no hay backups en Documents/PowerampFree"
    }

    /** Restore prefs + preset files from an exported JSON (SAF picker uri). */
    private fun importData(uri: Uri): String {
        val text = contentResolver.openInputStream(uri)?.use {
            it.readBytes().toString(Charsets.UTF_8)
        } ?: return "Import: no se pudo leer el archivo"
        val root = org.json.JSONObject(text)
        val prefsObj = root.optJSONObject("prefs")
            ?: return "Import: JSON sin sección prefs"
        for (name in prefsObj.keys()) {
            val sp = getSharedPreferences(name, MODE_PRIVATE)
            val values = prefsObj.getJSONObject(name)
            val ed = sp.edit()
            for (k in values.keys()) {
                when (val v = values.get(k)) {
                    is Int ->
                        // org.json silently narrows Longs to Int on parse:
                        // milisecond-style keys MUST go back as Long.
                        if (k.endsWith("_ms") || k == "crossfade_ms") {
                            ed.putLong(k, v.toLong())
                        } else {
                            ed.putInt(k, v)
                        }
                    is Long -> ed.putLong(k, v)
                    is Boolean -> ed.putBoolean(k, v)
                    is String -> ed.putString(k, v)
                    is Double -> ed.putFloat(k, v.toFloat())
                }
            }
            ed.apply()
        }
        root.optJSONArray("presets")?.let { arr ->
            val dir = File(filesDir, "eq").apply { mkdirs() }
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val name = o.optString("file").ifBlank { "import$i.json" }
                val safe = name.map {
                    if (it.isLetterOrDigit() || it in " .-_") it else '_'
                }.joinToString("")
                runCatching { File(dir, safe).writeText(o.getString("json")) }
            }
        }
        // Per-track state (block 11): merge progress + counts back in.
        root.optJSONObject("trackState")?.let { ts ->
            val entries = HashMap<String, TrackStateStore.TrackState>()
            for (uri in ts.keys()) {
                val o = ts.optJSONObject(uri) ?: continue
                entries[uri] = TrackStateStore.TrackState(
                    positionMs = o.optLong("pos"),
                    playedCount = o.optInt("plays"),
                    lastPlayedMs = o.optLong("last"),
                )
            }
            if (entries.isNotEmpty()) TrackStateStore.importAll(this, entries)
        }
        // Playlists (sprint 10): restore each saved list file.
        root.optJSONArray("playlists")?.let { arr ->
            val dir = File(filesDir, "playlists").apply { mkdirs() }
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val name = o.optString("file").ifBlank { "import$i.json" }
                val safe = name.map {
                    if (it.isLetterOrDigit() || it in " .-_") it else '_'
                }.joinToString("")
                runCatching { File(dir, safe).writeText(o.getString("json")) }
            }
        }
        // Apply the restored EQ immediately (session may be connected).
        runCatching { sendPreset(PresetStore.loadCurrent(this)) }
        // audio_settings (replay gain) was restored above — re-apply the trim.
        runCatching { sendReplayGain() }
        return "Import OK (${BACKUP_PREFS.size} prefs · " +
            "${root.optJSONArray("presets")?.length() ?: 0} presets · " +
            "${root.optJSONArray("playlists")?.length() ?: 0} playlists · " +
            "${root.optJSONObject("trackState")?.length() ?: 0} pistas estado)"
    }

    @Composable
    private fun TextButton(onClick: () -> Unit, content: @Composable () -> Unit) =
        androidx.compose.material3.TextButton(onClick = onClick) { content() }

    // Unified player snapshot (title / uri / playing / format) with ONE listener
    // so Compose recomposes only while the session actually changes.

    private data class PlayerSnapshot(
        val title: String = "—",
        val artist: String = "",
        val uri: String = "",
        val isPlaying: Boolean = false,
        val format: String = "",
        /** Current-track cues for art lookup (MediaStore albumId or file uri). */
        val albumId: Long = -1L,
        val folder: String = "",
    )

    @Composable
    private fun rememberPlayerSnapshot(): androidx.compose.runtime.State<PlayerSnapshot> {
        val state = androidx.compose.runtime.remember { mutableStateOf(PlayerSnapshot()) }
        androidx.compose.runtime.LaunchedEffect(controller) {
            val c = controller
            if (c == null) {
                state.value = PlayerSnapshot()
            } else {
                val listener = object : Player.Listener {
                    override fun onMediaMetadataChanged(m: MediaMetadata) {
                        state.value = snapshotOf(c)
                    }

                    override fun onMediaItemTransition(
                        m: MediaItem?,
                        reason: Int,
                    ) {
                        state.value = snapshotOf(c)
                    }

                    override fun onIsPlayingChanged(playing: Boolean) {
                        state.value = snapshotOf(c)
                    }
                }
                c.addListener(listener)
                state.value = snapshotOf(c)
                try {
                    awaitCancellation()
                } finally {
                    c.removeListener(listener)
                }
            }
        }
        return state
    }

    private fun snapshotOf(c: Player): PlayerSnapshot {
        // Prefer the format published by the service's own session player
        // (real decoder values); fall back to the controller's track list.
        var fmt = FormatBus.current
        if (fmt.isEmpty()) {
            var sRate = 0
            var bps = 0
            var mime = ""
            outer@ for (group in c.currentTracks.groups) {
                if (group.type != C.TRACK_TYPE_AUDIO) continue
                for (i in 0 until group.length) {
                    val f = group.getTrackFormat(i)
                    if (f.sampleRate > 0) {
                        sRate = f.sampleRate
                        bps = if (f.bitrate > 0) f.bitrate else 0
                        mime = f.sampleMimeType ?: ""
                        break@outer
                    }
                }
            }
            fmt = FormatBus.formatString(sRate, bps, mime)
        }
        // Album art lookup cues come from the published queue (same process,
        // QueueBus carries the real MediaTrack list incl. albumId/folder).
        val qt = QueueBus.tracks.getOrNull(QueueBus.index)
        return PlayerSnapshot(
            title = c.mediaMetadata.title?.toString() ?: "—",
            artist = c.mediaMetadata.artist?.toString() ?: "",
            uri = c.currentMediaItem?.mediaId ?: "",
            isPlaying = c.isPlaying,
            format = fmt,
            albumId = qt?.albumId ?: -1L,
            folder = qt?.folder ?: "",
        )
    }

    private fun fmtClock(msRemain: Long): String {
        val s = (msRemain / 1000).coerceAtLeast(0L)
        return "%d:%02d".format(s / 60, s % 60)
    }

    private fun sendRepeat(mode: String) {
        val c = controller ?: return
        val bundle = Bundle().apply { putString(PlaybackService.EXTRA_REPEAT_MODE, mode) }
        c.sendCustomCommand(
            androidx.media3.session.SessionCommand(PlaybackService.CMD_SET_REPEAT, bundle),
            bundle,
        )
    }

    private fun sendShuffle(on: Boolean) {
        val c = controller ?: return
        val bundle = Bundle().apply { putBoolean(PlaybackService.EXTRA_SHUFFLE_ON, on) }
        c.sendCustomCommand(
            androidx.media3.session.SessionCommand(PlaybackService.CMD_SET_SHUFFLE, bundle),
            bundle,
        )
    }

    private fun nudgePosition(deltaMs: Long) {
        val c = controller ?: return
        runCatching { c.seekTo((c.currentPosition + deltaMs).coerceAtLeast(0L)) }
    }

    @Composable
    /**
     * Live spectrum bars (sprint 10 — block 14 núcleo). Style/opacity come
     * from `viz_settings`, re-read inside the 50 ms poll (cached prefs, cheap)
     * so the header 📊 toggle and the Visualization screen act instantly
     * without any session push. Opacity maps 1:1 onto the bars alpha
     * (default 60 == the previous hard-coded 0x99 alpha).
     */
    private fun SpectrumBars(isPlaying: Boolean) {
        var spectrum by androidx.compose.runtime.remember {
            mutableStateOf(VisualizerTap.spectrum())
        }
        var style by androidx.compose.runtime.remember {
            mutableStateOf(VizSettings.spectrumStyle(this@MainActivity))
        }
        var alphaPct by androidx.compose.runtime.remember {
            mutableStateOf(VizSettings.panelOpacity(this@MainActivity))
        }
        androidx.compose.runtime.LaunchedEffect(isPlaying) {
            while (kotlinx.coroutines.currentCoroutineContext().isActive) {
                if (isPlaying) spectrum = VisualizerTap.spectrum()
                style = VizSettings.spectrumStyle(this@MainActivity)
                alphaPct = VizSettings.panelOpacity(this@MainActivity)
                kotlinx.coroutines.delay(50)
            }
        }
        Canvas(modifier = Modifier.fillMaxWidth().height(84.dp)) {
            val barCount = 48
            val fLow = 30.0
            val fHigh = 16_000.0
            val logSpan = kotlin.math.ln(fHigh / fLow)
            val barW = size.width / barCount
            val color = Color(0xFF39A7E4).copy(alpha = alphaPct / 100f)
            val rounded = style == VizSettings.STYLE_ROUNDED
            val corner = androidx.compose.ui.geometry.CornerRadius(barW * 0.45f, barW * 0.45f)
            for (b in 0 until barCount) {
                val freq = fLow * Math.exp(logSpan * b / (barCount - 1.0))
                val k = (freq / 48_000.0 * VisualizerTap.bins).toInt()
                    .coerceIn(0, VisualizerTap.bins - 1)
                val mag = spectrum.getOrNull(k) ?: 0f
                val db = (20.0 * Math.log10(mag + 1e-9))
                val level = ((db + 60.0) / 60.0).coerceIn(0.0, 1.0).toFloat()
                val topLeft = Offset(b * barW + 1f, size.height * (1f - level))
                val barSize = androidx.compose.ui.geometry.Size(barW - 2f, size.height * level)
                if (rounded) {
                    drawRoundRect(color = color, topLeft = topLeft, size = barSize, cornerRadius = corner)
                } else {
                    drawRect(color = color, topLeft = topLeft, size = barSize)
                }
            }
        }
    }

    @Composable
    private fun EqCurve(preset: EqPreset) {
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
    private fun VSlider(
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
    private fun Knob(
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
    private fun BandRow(
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

    /**
     * Push the Replay Gain trim live to the playback session. Prefs
     * (audio_settings) are already persisted by the caller; the service just
     * re-reads them and rebuilds the DSP chain trim.
     */
    private fun sendReplayGain() {
        controller?.sendCustomCommand(
            androidx.media3.session.SessionCommand(PlaybackService.CMD_SET_REPLAY_GAIN, Bundle.EMPTY),
            Bundle.EMPTY,
        )
    }
}
