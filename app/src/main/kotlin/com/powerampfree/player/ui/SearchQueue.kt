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

    // -------------------------------------------------------- search tab

internal enum class SearchFilter(val label: String) {
        ALL("Todo"), TITLE("Título"), ARTIST("Artista"), ALBUM("Álbum"),
    }


    @OptIn(ExperimentalFoundationApi::class)
    @Composable
internal fun MainActivity.SearchTab(onOpenCtx: OpenCtx = { _, _, _ -> }) {
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
                    SearchHistory.add(this@SearchTab, query)
                    history = SearchHistory.load(this@SearchTab)
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
                            SearchHistory.clear(this@SearchTab)
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
                                    SearchHistory.add(this@SearchTab, q)
                                    history = SearchHistory.load(this@SearchTab)
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
internal fun MainActivity.QueueTab(snap: PlayerSnapshot, onOpenCtx: OpenCtx = { _, _, _ -> }) {
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
