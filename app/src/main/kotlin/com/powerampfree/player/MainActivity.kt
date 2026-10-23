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

    // PafTab + iconos viven en ui/BottomBar.kt: enum interno compartido
    // (LIBRARY/EQ/SEARCH/MENU en la barra; PLAYER/QUEUE se abren por
    // mini-player y hoja del menú, igual que la referencia).

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
        var menuAbout by remember { mutableStateOf(false) }
        // Atrás desde player/cola/menú → biblioteca (como la referencia: el
        // mini-player y la cola son vistas, no pestañas raíz).
        BackHandler(
            enabled = (tab == PafTab.PLAYER || tab == PafTab.QUEUE || tab == PafTab.MENU) &&
                !settingsOpen && ctxTrack == null && plTrack == null &&
                lyricsTrack == null && !menuAbout,
        ) {
            tab = PafTab.LIBRARY
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
        // Look Poweramp (UI_SPEC §1): negro puro + superficies grises + familia
        // condensada. Los ajustes L&F siguen cableados (accent, bold); la
        // referencia observada no tiene modo claro, así que el esquema es
        // siempre el oscuro medido.
        val scheme = pafScheme(accentColor)
        val typography = pafTypography(MaterialTheme.typography, boldFont)

        MaterialTheme(colorScheme = scheme, typography = typography) {
            // Edge-to-edge on MIUI: the content must clear the status bar or the
            // system UI swallows taps in the top band (observed).
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Paf.Bg)
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
                            onQueue = { tab = PafTab.QUEUE },
                            goto = gotoNav,
                            onGotoDone = { gotoNav = null },
                        )
                        PafTab.EQ -> EqTab()
                        PafTab.SEARCH -> SearchTab(onOpenCtx = openCtx)
                        PafTab.QUEUE -> QueueTab(snap, onOpenCtx = openCtx)
                        PafTab.MENU -> {
                            if (menuAbout) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Paf.Bg)
                                        .clickable { menuAbout = false }
                                        .padding(16.dp),
                                ) {
                                    BackHandler { menuAbout = false }
                                    AboutScreen()
                                }
                            } else {
                                MainMenuSheet(
                                    onClose = { tab = PafTab.LIBRARY },
                                    onSettings = {
                                        tab = PafTab.LIBRARY
                                        settingsOpen = true
                                    },
                                    onQueue = { tab = PafTab.QUEUE },
                                    onAbout = { menuAbout = true },
                                )
                            }
                        }
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
                // Barra pastilla de 4 tabs (UI_SPEC §2). PLAYER no es tab:
                // se abre tocando el mini-player; Cola desde el menú.
                PafBottomBar(
                    tab = if (menuAbout) PafTab.MENU else tab,
                    onTab = { t ->
                        tab = if (tab == PafTab.MENU && t == PafTab.MENU) PafTab.LIBRARY else t
                    },
                    tabOnly = tab == PafTab.PLAYER || settingsOpen,
                    snap = snap,
                    onOpenPlayer = { tab = PafTab.PLAYER },
                )
            }
        }
    }
}