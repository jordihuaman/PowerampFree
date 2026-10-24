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
import androidx.compose.ui.draw.clipToBounds
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

        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Buscar",
                fontFamily = PafFamilyBold,
                fontWeight = FontWeight.Bold,
                fontSize = 30.sp,
                color = Paf.Text,
            )
            Spacer(Modifier.height(10.dp))
            androidx.compose.material3.OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(50),
                placeholder = { Text("Buscar…", fontFamily = PafFamily, color = Paf.Text3) },
                leadingIcon = {
                    Box(modifier = Modifier.padding(start = 6.dp)) {
                        PafIcon(PafIconKind.Magnifier, Paf.Text3, 18.dp)
                    }
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .padding(end = 10.dp)
                                .clickable { query = "" },
                        ) {
                            Text(
                                "×",
                                fontFamily = PafFamilyBold,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                color = Paf.Text2,
                            )
                        }
                    }
                },
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Paf.Surf2,
                    unfocusedContainerColor = Paf.Surf2,
                    focusedBorderColor = Paf.Stroke,
                    unfocusedBorderColor = Color.Transparent,
                    cursorColor = Paf.Text,
                    focusedTextColor = Paf.Text,
                    unfocusedTextColor = Paf.Text,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    SearchHistory.add(this@SearchTab, query)
                    history = SearchHistory.load(this@SearchTab)
                }),
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SearchFilter.values().forEach { f ->
                    PafActionChip(f.label, active = mode == f) { mode = f }
                }
            }
            Spacer(Modifier.height(8.dp))

            if (query.isBlank()) {
                if (history.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Historial",
                            fontFamily = PafFamilyBold,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Paf.Text2,
                        )
                        Spacer(Modifier.weight(1f))
                        PafActionChip("Borrar", icon = PafIconKind.Trash) {
                            SearchHistory.clear(this@SearchTab)
                            history = emptyList()
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    history.forEach { h ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .clickable { query = h },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PafIcon(PafIconKind.Clock, Paf.Text3, 17.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                h,
                                fontFamily = PafFamily,
                                fontSize = 16.sp,
                                color = Paf.Text2,
                                maxLines = 1,
                                modifier = Modifier.weight(1f).clipToBounds(),
                            )
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clickable {
                                        SearchHistory.remove(this@SearchTab, h)
                                        history = SearchHistory.load(this@SearchTab)
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "×",
                                    fontFamily = PafFamilyBold,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = Paf.Text3,
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        "Busca por título, artista o álbum.",
                        fontFamily = PafFamily,
                        fontSize = 14.sp,
                        color = Paf.Text3,
                    )
                }
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
                fontFamily = PafFamily,
                fontSize = 13.sp,
                color = Paf.Text3,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
            )
            LazyColumn(modifier = Modifier.weight(1f)) {
                itemsIndexed(results) { i, t ->
                    PafTrackRow(
                        track = t,
                        tracks = results,
                        index = i,
                        onOpenCtx = onOpenCtx,
                        onRowClick = {
                            SearchHistory.add(this@SearchTab, q)
                            history = SearchHistory.load(this@SearchTab)
                            playTracks(results, i)
                        },
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }


    // ---------------------------------------------------------- queue tab

    @Composable
internal fun MainActivity.QueueTab(
        snap: PlayerSnapshot,
        onOpenCtx: OpenCtx = { _, _, _ -> },
        onBack: () -> Unit = {},
    ) {
        // Subscribe to orchestrator publishes (same-process queue channel).
        QueueBus.version.value
        val tracks = QueueBus.tracks

        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            item {
                Spacer(Modifier.height(8.dp))
                PafBackPill("↑ Biblioteca", onClick = onBack)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Cola",
                    fontFamily = PafFamilyBold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 30.sp,
                    color = Paf.Text,
                )
                Text(
                    if (tracks.isEmpty()) "vacía"
                    else "${tracks.size} pistas · toca una para saltar a ella",
                    fontFamily = PafFamily,
                    fontSize = 13.sp,
                    color = Paf.Text3,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            if (tracks.isEmpty()) {
                item {
                    Text(
                        "La cola está vacía: reproduce algo desde la Biblioteca.",
                        fontFamily = PafFamily,
                        fontSize = 14.sp,
                        color = Paf.Text3,
                    )
                }
            }
            itemsIndexed(tracks) { i, t ->
                PafTrackRow(
                    track = t,
                    tracks = tracks,
                    index = i,
                    onOpenCtx = onOpenCtx,
                    number = i + 1,
                    onRowClick = {
                        val isCurrent = t.uri == snap.uri && snap.uri.isNotEmpty()
                        if (!isCurrent) sendSeekQueue(i)
                    },
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
