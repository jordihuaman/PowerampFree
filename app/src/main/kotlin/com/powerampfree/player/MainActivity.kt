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
import com.powerampfree.player.ui.*

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
/** Signature of the long-press opener shared by every track list. */
internal typealias OpenCtx = (track: MediaTrack, list: List<MediaTrack>, index: Int) -> Unit

/** Library navigation from the track context menu: 0=artist 1=album 2=folder. */
internal data class NavRequest(val kind: Int, val key: String, val albumId: Long = -1L)

class MainActivity : ComponentActivity() {
    internal val TAG = "PafMainActivity"

    internal var controller: MediaController? by mutableStateOf(null)

    /** Selection queued while the MediaController is still connecting. */
    internal var pendingPlay: Pair<List<MediaTrack>, Int>? = null

    /** Shared library state so callbacks and composables recompute together. */
    internal var libraryOrEmpty by mutableStateOf<List<MediaTrack>>(emptyList())

    internal val askAudioPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            libraryOrEmpty = if (granted) MediaStoreLibrary.query(this) else emptyList()
        }

    internal val pickFolder =
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
    internal fun playTracks(tracks: List<MediaTrack>, index: Int) {
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

        // Track context menu + lyrics overlay.
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
}