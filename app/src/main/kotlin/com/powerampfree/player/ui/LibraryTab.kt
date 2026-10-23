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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.sp
import com.powerampfree.player.QueueBus

    // ------------------------------------------------------ library tab

internal data class GroupRow(
        val title: String,
        val subtitle: String,
        val tracks: List<MediaTrack>,
    )


internal enum class LibTop { ROOT, SONGS, ALBUMS, ARTISTS, GENRES, FOLDERS, PLAYLISTS }


    /** Sort options for the folder explorer (List Options). */
internal enum class ListSort(val label: String) {
        TITLE("T�tulo"), TRACK("N� pista"), DURATION("Duraci�n"),
    }


    /** Parent of a RELATIVE_PATH-like folder string ("Music/RecForge/" ? "Music/"; "Music/" ? ""). */
internal fun MainActivity.parentFolder(p: String): String {
        if (p.isEmpty()) return ""
        val parent = p.trimEnd('/').substringBeforeLast('/', "")
        return if (parent.isEmpty()) "" else "$parent/"
    }


    /** Direct-child folder paths under [path] ("" = device root). */
internal fun MainActivity.subFoldersOf(path: String): List<String> =
        libraryOrEmpty
            .map { it.folder }
            .filter { it.startsWith(path) && it != path }
            .map { path + it.removePrefix(path).substringBefore('/') + '/' }
            .distinct()
            .sorted()


internal fun MainActivity.folderTracksSorted(path: String, sort: ListSort): List<MediaTrack> =
        libraryOrEmpty.filter { it.folder == path }.let { list ->
            when (sort) {
                ListSort.TITLE -> list.sortedBy { it.title.lowercase() }
                ListSort.TRACK -> list.sortedBy { it.trackNo }
                ListSort.DURATION -> list.sortedBy { it.durationMs }
            }
        }


    @Composable
internal fun MainActivity.LibraryTab(
        onSettings: () -> Unit = {},
        onOpenCtx: OpenCtx = { _, _, _ -> },
        onQueue: () -> Unit = {},
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
            detail != null -> TrackListView(detail!!.title, detail!!.tracks, onOpenCtx, onUp = { detail = null })
            folderPath != null -> FolderScreen(
                path = folderPath!!,
                onNavigate = { folderPath = it },
                onOpenCtx = onOpenCtx,
            )
            top == LibTop.SONGS -> TrackListView("Todas las canciones", libraryOrEmpty, onOpenCtx, onUp = { top = LibTop.ROOT })
            top == LibTop.ALBUMS -> GroupListView(
                "�lbumes",
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
                onOpen = { g -> folderPath = if (g.subtitle == "(ra�z)") "" else g.subtitle },
            )
            plName != null -> PlaylistScreen(
                name = plName!!,
                onOpenCtx = onOpenCtx,
                onChange = { plName = it },
            )
            top == LibTop.PLAYLISTS -> PlaylistsScreen(onOpen = { plName = it })
            top == LibTop.GENRES -> GenresScreen(onOpen = { detail = it })
            else -> LibraryRoot(onOpen = { top = it }, onSettings = onSettings, onQueue = onQueue)
        }
    }


internal fun MainActivity.albumRows(): List<GroupRow> =
        libraryOrEmpty.groupBy { it.albumId }
            .values
            .map { list ->
                GroupRow(
                    list.first().album.ifBlank { "Sin �lbum" },
                    list.first().artist.ifBlank { "" } + " � ${list.size} pistas",
                    list.sortedBy { it.trackNo },
                )
            }
            .sortedBy { it.title.lowercase() }


internal fun MainActivity.artistRows(): List<GroupRow> =
        libraryOrEmpty.groupBy { it.artist.ifBlank { "Sin artista" } }
            .map { (name, list) -> GroupRow(name, "${list.size} pistas", list) }
            .sortedBy { it.title.lowercase() }


internal fun MainActivity.folderRows(): List<GroupRow> =
        libraryOrEmpty.groupBy { it.folder }
            .map { (f, list) ->
                val display = f.trimEnd('/').substringAfterLast('/').ifBlank { "Ra�z" }
                GroupRow(display, f.ifBlank { "(ra�z)" }, list)
            }
            .sortedBy { it.title.lowercase() }


    @Composable
internal fun MainActivity.LibraryRoot(
        onOpen: (LibTop) -> Unit,
        onSettings: () -> Unit,
        onQueue: () -> Unit = {},
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Biblioteca",
                            fontFamily = PafFamilyBold,
                            fontWeight = FontWeight.Bold,
                            fontSize = 30.sp,
                            color = Paf.Text,
                        )
                        Text(
                            "${libraryOrEmpty.size} pistas en el sistema",
                            fontFamily = PafFamily,
                            fontSize = 12.sp,
                            color = Paf.Text3,
                        )
                    }
                    // overflow en círculo (abre ajustes, UI_SPEC §4.1)
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(Paf.Surf3, CircleShape)
                            .clickable(onClick = onSettings),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("⋮", color = Paf.Text, fontSize = 22.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (libraryOrEmpty.isEmpty() &&
                    ContextCompat.checkSelfPermission(
                        this@LibraryRoot, Manifest.permission.READ_MEDIA_AUDIO,
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    Row {
                        PafActionChip("Conceder permiso de audio") {
                            askAudioPermission.launch(Manifest.permission.READ_MEDIA_AUDIO)
                        }
                    }
                }
            }
            item {
                PafCateRow(
                    Paf.CatAllSongs, PafIconKind.QueueList, "Canciones",
                    if (libraryOrEmpty.isEmpty()) "" else "${libraryOrEmpty.size}",
                ) { onOpen(LibTop.SONGS) }
            }
            item {
                PafCateRow(
                    Paf.CatFolders, PafIconKind.Folder, "Carpetas",
                    if (folderRows().isEmpty()) "" else "${folderRows().size}",
                ) { onOpen(LibTop.FOLDERS) }
            }
            item {
                PafCateRow(
                    Paf.CatYears, PafIconKind.Grid, "Álbumes",
                    if (albumRows().isEmpty()) "" else "${albumRows().size}",
                ) { onOpen(LibTop.ALBUMS) }
            }
            item {
                PafCateRow(
                    Paf.CatComposer, PafIconKind.Person, "Artistas",
                    if (artistRows().isEmpty()) "" else "${artistRows().size}",
                ) { onOpen(LibTop.ARTISTS) }
            }
            item {
                PafCateRow(
                    Paf.CatGenres, PafIconKind.Bars, "Géneros", "",
                ) { onOpen(LibTop.GENRES) }
            }
            item {
                val plCount = PlaylistStore.list(this@LibraryRoot).size
                PafCateRow(
                    Paf.CatPlaylist, PafIconKind.QueueList, "Playlists",
                    if (plCount == 0) "" else "$plCount",
                ) { onOpen(LibTop.PLAYLISTS) }
            }
            item {
                val qCount = QueueBus.tracks.size
                PafCateRow(
                    Paf.CatQueue, PafIconKind.QueueList, "Cola",
                    if (qCount == 0) "vacía" else "$qCount pistas",
                ) { onQueue() }
            }
            item {
                PafCateRow(
                    Paf.CatQueue, PafIconKind.Gear, "Ajustes", "",
                ) { onSettings() }
            }
            item {
                PafCateRow(
                    Paf.CatQueue, PafIconKind.Down, "Importar (SAF)", "",
                ) { pickFolder.launch(null) }
            }
            item {
                if (libraryOrEmpty.isNotEmpty()) {
                    Row(modifier = Modifier.padding(vertical = 10.dp)) {
                        PafActionChip("Recargar biblioteca") {
                            libraryOrEmpty = MediaStoreLibrary.query(this@LibraryRoot)
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }


    @Composable
internal fun MainActivity.GroupListView(title: String, rows: List<GroupRow>, onOpen: (GroupRow) -> Unit) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            title,
                            fontFamily = PafFamilyBold,
                            fontWeight = FontWeight.Bold,
                            fontSize = 26.sp,
                            color = Paf.Text,
                            maxLines = 1,
                        )
                        Text(
                            "${rows.size}",
                            fontFamily = PafFamily,
                            fontSize = 13.sp,
                            color = Paf.Text3,
                        )
                    }
                }
            }
            itemsIndexed(rows) { _, g ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onOpen(g) }
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(Paf.Surf3, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        PafIcon(
                            if (title.contains("Artist")) PafIconKind.Person else PafIconKind.Grid,
                            Paf.Text2,
                            22.dp,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            g.title,
                            fontFamily = PafFamilyBold,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = Paf.Text,
                            maxLines = 1,
                        )
                        if (g.subtitle.isNotBlank()) {
                            Text(
                                g.subtitle,
                                fontFamily = PafFamily,
                                fontSize = 13.sp,
                                color = Paf.Text3,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
    @Composable
internal fun MainActivity.GenresScreen(onOpen: (GroupRow) -> Unit) {
        var rows by remember(libraryOrEmpty) { mutableStateOf<List<GroupRow>?>(null) }
        androidx.compose.runtime.LaunchedEffect(libraryOrEmpty) {
            rows = MediaGenres.load(this@GenresScreen)
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
                Text("G�neros", style = MaterialTheme.typography.titleMedium)
                Text("Cargando�", style = MaterialTheme.typography.bodySmall)
            }
        } else if (current.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("G�neros", style = MaterialTheme.typography.titleMedium)
                Text(
                    "No hay g�neros en la biblioteca del sistema.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        } else {
            GroupListView("G�neros", current, onOpen)
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
internal fun MainActivity.FolderScreen(
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

        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            // ---- breadcrumb + header (UI_SPEC §4.2)
            Row(verticalAlignment = Alignment.CenterVertically) {
                PafBackPill(
                    "↑ " + (parentFolder(path).trimEnd('/').substringAfterLast('/').ifEmpty { "Carpetas" }),
                ) { onNavigate(parentFolder(path)) }
            }
            Row(
                modifier = Modifier.padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(Paf.CatFolders, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    PafIcon(PafIconKind.Folder, Paf.Text, 24.dp)
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        path.trimEnd('/').ifEmpty { "Dispositivo" },
                        fontFamily = PafFamilyBold,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = Paf.Text,
                        maxLines = 1,
                    )
                    Text(
                        "${subtree.size} | ${fmtDur(totalMs)}",
                        fontFamily = PafFamily,
                        fontSize = 13.sp,
                        color = Paf.Text3,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            // ---- chips de acción
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PafActionChip("Aleatorio", PafIconKind.Shuffle) {
                    if (tracks.isNotEmpty()) {
                        playTracks(tracks, kotlin.random.Random.nextInt(tracks.size))
                    }
                }
                PafActionChip("Todo", PafIconKind.Play) {
                    if (tracks.isNotEmpty()) playTracks(tracks, 0)
                }
                PafActionChip(
                    if (selecting) "Salir" else "Select",
                    active = selecting,
                ) {
                    selecting = !selecting
                    if (!selecting) selected.clear()
                }
                PafActionChip("Ordenar") { showSort = true }
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier.weight(1f),
            ) {
                itemsIndexed(subs) { _, sub ->
                    val direct = libraryOrEmpty.count { it.folder == sub }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onNavigate(sub) }
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(Paf.Surf3, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            PafIcon(PafIconKind.Folder, Paf.Text2, 20.dp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                sub.trimEnd('/').substringAfterLast('/'),
                                fontFamily = PafFamilyBold,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Paf.Text,
                                maxLines = 1,
                            )
                            Text(
                                "$direct pistas",
                                fontFamily = PafFamily,
                                fontSize = 12.sp,
                                color = Paf.Text3,
                            )
                        }
                    }
                }
                itemsIndexed(tracks) { i, t ->
                    val isSel = t.uri in selected
                    PafTrackRow(
                        track = t,
                        tracks = tracks,
                        index = i,
                        onOpenCtx = onOpenCtx,
                        selecting = selecting,
                        selected = isSel,
                        onSelectToggle = {
                            if (isSel) selected.remove(t.uri) else selected.add(t.uri)
                        },
                    )
                }
                item { Spacer(Modifier.height(12.dp)) }
            }
            if (selecting) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PafActionChip("Reproducir (${selected.size})") {
                        val selTracks = tracks.filter { it.uri in selected }
                        if (selTracks.isNotEmpty()) playTracks(selTracks, 0)
                    }
                    PafActionChip(
                        "Todos ${tracks.size}",
                    ) {
                        selected.clear()
                        tracks.forEach { selected.add(it.uri) }
                    }
                    PafActionChip(
                        "Ninguno",
                    ) { selected.clear() }
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
