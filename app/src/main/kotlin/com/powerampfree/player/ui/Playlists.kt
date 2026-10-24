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

    // -------------------------------------------------- playlists

    /**
     * Saved playlists hub (block 6): "name · N pistas" rows + create row.
     * Lists live in `PlaylistStore` (filesDir/playlists); adding tracks happens
     * from any track's long-press context menu ("Añadir a Playlist").
     */
    @Composable
internal fun MainActivity.PlaylistsScreen(onOpen: (String) -> Unit, onBack: () -> Unit = {}) {
        var names by remember { mutableStateOf(PlaylistStore.list(this)) }
        var showNew by remember { mutableStateOf(false) }
        var newName by remember { mutableStateOf("") }
        androidx.compose.runtime.LaunchedEffect(Unit) {
            names = PlaylistStore.list(this@PlaylistsScreen)
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                Spacer(Modifier.height(8.dp))
                PafBackPill("↑ Biblioteca", onClick = onBack)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Playlists",
                            fontFamily = PafFamilyBold,
                            fontWeight = FontWeight.Bold,
                            fontSize = 30.sp,
                            color = Paf.Text,
                        )
                        Text(
                            "${names.size} listas guardadas",
                            fontFamily = PafFamily,
                            fontSize = 13.sp,
                            color = Paf.Text3,
                        )
                    }
                    PafActionChip("Nueva", icon = PafIconKind.Plus) { showNew = true }
                }
                Spacer(Modifier.height(4.dp))
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
                val n = PlaylistStore.tracks(this@PlaylistsScreen, name).size
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clickable { onOpen(name) }
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Paf.CatPlaylist, androidx.compose.foundation.shape.CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        PafIcon(PafIconKind.QueueList, Paf.Text, 18.dp)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            name,
                            fontFamily = PafFamilyBold,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = Paf.Text,
                            maxLines = 1,
                        )
                        Text(
                            "$n pistas",
                            fontFamily = PafFamily,
                            fontSize = 12.sp,
                            color = Paf.Text3,
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
                            val stored = PlaylistStore.create(this@PlaylistsScreen, newName)
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
internal fun MainActivity.PlaylistScreen(
        name: String,
        onOpenCtx: OpenCtx,
        onChange: (String?) -> Unit,
        onBack: () -> Unit = {},
    ) {
        var list by remember { mutableStateOf<List<MediaTrack>>(emptyList()) }
        var tick by remember { mutableStateOf(0) }
        var showRename by remember { mutableStateOf(false) }
        var showDelete by remember { mutableStateOf(false) }
        var newTitle by remember { mutableStateOf(name) }

        androidx.compose.runtime.LaunchedEffect(name, tick) {
            list = PlaylistStore.tracks(this@PlaylistScreen, name)
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item {
                Spacer(Modifier.height(8.dp))
                PafBackPill("↑ Playlists", onClick = onBack)
                Spacer(Modifier.height(8.dp))
                Text(
                    name,
                    fontFamily = PafFamilyBold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 30.sp,
                    color = Paf.Text,
                    maxLines = 1,
                )
                Text(
                    "${list.size} pistas",
                    fontFamily = PafFamily,
                    fontSize = 13.sp,
                    color = Paf.Text3,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 4.dp),
                ) {
                    PafActionChip("Todo", icon = PafIconKind.Play) { playTracks(list, 0) }
                    PafActionChip("Renombrar") { newTitle = name; showRename = true }
                    PafActionChip("Eliminar", icon = PafIconKind.Trash) { showDelete = true }
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
                PafTrackRow(
                    track = t,
                    tracks = list,
                    index = i,
                    onOpenCtx = onOpenCtx,
                    trailing = {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(50))
                                .clickable {
                                    if (PlaylistStore.removeTrack(this@PlaylistScreen, name, t.uri)) {
                                        tick++
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            PafIcon(PafIconKind.Trash, Paf.Text3, 17.dp)
                        }
                    },
                )
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
                            val stored = PlaylistStore.rename(this@PlaylistScreen, name, newTitle)
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
                        PlaylistStore.delete(this@PlaylistScreen, name)
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


    // --------------------------------------------- add to playlist

    /**
     * Bottom sheet behind "Añadir a Playlist": pick an existing list (tap) or
     * create a new one inline. Feedback via toast; duplicates are reported and
     * re-added silently keeps the list unchanged (`PlaylistStore.addTrack`).
     */
    @Composable
internal fun MainActivity.AddToPlaylistSheet(
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
                                                PlaylistStore.addTrack(this@AddToPlaylistSheet, name, track)
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
                                        "${PlaylistStore.tracks(this@AddToPlaylistSheet, name).size} pistas",
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
                                            PlaylistStore.create(this@AddToPlaylistSheet, newName)
                                        PlaylistStore.addTrack(
                                            this@AddToPlaylistSheet, stored, track,
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
