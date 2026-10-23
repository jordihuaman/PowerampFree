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

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
internal fun MainActivity.TrackListView(title: String, tracks: List<MediaTrack>, onOpenCtx: OpenCtx) {
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


    /**
     * Bottom-sheet context menu for a track (block 4). Long-press opens it
     * from queue/list/search/folder rows.
     */
    @Composable
internal fun MainActivity.TrackContextMenu(
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
                            RatingsStore.rating(this@TrackContextMenu, track.uri)
                        } else 0
                        androidx.compose.material3.TextButton(onClick = {
                            RatingsStore.toggle(this@TrackContextMenu, track.uri, 1)
                            ratTick++
                        }) {
                            Text(
                                "👍",
                                color = if (rated == 1) Color(0xFF39A7E4)
                                else LocalContentColor.current,
                            )
                        }
                        androidx.compose.material3.TextButton(onClick = {
                            RatingsStore.toggle(this@TrackContextMenu, track.uri, -1)
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
