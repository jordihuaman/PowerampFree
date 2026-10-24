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
import androidx.compose.ui.unit.sp
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
internal fun MainActivity.TrackListView(
        title: String,
        tracks: List<MediaTrack>,
        onOpenCtx: OpenCtx,
        onUp: (() -> Unit)? = null,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        ) {
            item {
                if (onUp != null) {
                    PafBackPill("↑ Biblioteca", onUp)
                }
                Text(
                    title,
                    fontFamily = PafFamilyBold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 26.sp,
                    color = Paf.Text,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${tracks.size} pistas",
                        fontFamily = PafFamily,
                        fontSize = 13.sp,
                        color = Paf.Text3,
                        modifier = Modifier.weight(1f),
                    )
                    PafActionChip("Aleatorio", PafIconKind.Shuffle) {
                        if (tracks.isNotEmpty()) {
                            playTracks(tracks, kotlin.random.Random.nextInt(tracks.size))
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    PafActionChip("Todo", PafIconKind.Play) { playTracks(tracks, 0) }
                }
            }
            itemsIndexed(tracks) { i, t ->
                PafTrackRow(t, tracks, i, onOpenCtx)
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
                color = Paf.Surf2,
                shape = RoundedCornerShape(
                    topStart = 28.dp, topEnd = 28.dp, bottomStart = 0.dp, bottomEnd = 0.dp,
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
                                fontFamily = PafFamilyBold,
                                fontWeight = FontWeight.Bold,
                                fontSize = 19.sp,
                                color = Paf.Text,
                                maxLines = 1,
                            )
                            Text(
                                artistLabel(track.artist),
                                fontFamily = PafFamily,
                                fontSize = 14.sp,
                                color = Paf.Text3,
                                maxLines = 1,
                            )
                            Text(
                                run {
                                    val alb = track.album
                                        .takeIf { it.isNotBlank() && !it.startsWith("<unknown>") }
                                    val ext = extLabel(track.uri).takeIf { it != "?" && it.length <= 5 }
                                    listOfNotNull(fmtDur(track.durationMs), alb ?: ext)
                                        .joinToString(" · ")
                                },
                                fontFamily = PafFamily,
                                fontSize = 12.sp,
                                color = Paf.Text3,
                            )
                        }
                        val rated = if (ratTick >= 0) {
                            RatingsStore.rating(this@TrackContextMenu, track.uri)
                        } else 0
                        PafRatingPill(PafIconKind.ThumbUp, rated == 1) {
                            RatingsStore.toggle(this@TrackContextMenu, track.uri, 1)
                            ratTick++
                        }
                        Spacer(Modifier.width(6.dp))
                        PafRatingPill(PafIconKind.ThumbDown, rated == -1) {
                            RatingsStore.toggle(this@TrackContextMenu, track.uri, -1)
                            ratTick++
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    // ---- actions
                    val actions = buildList {
                        add(PafIconKind.Play to ("Reproducir ahora" to onPlay))
                        add(PafIconKind.Next to ("Encolar siguiente" to onEnqueueNext))
                        add(PafIconKind.Down to ("Encolar al final de la cola" to onEnqueueEnd))
                        val artistName = track.artist
                            .takeIf { it.isNotBlank() && !it.startsWith("<unknown>") }
                        if (artistName != null) {
                            add(PafIconKind.Person to ("Ir a: $artistName" to onArtist))
                        }
                        val albumName = track.album
                            .takeIf { it.isNotBlank() && !it.startsWith("<unknown>") }
                        if (albumName != null) {
                            add(PafIconKind.Note to ("Ir a: $albumName" to onAlbum))
                        }
                        add(PafIconKind.Folder to ("Ir a: carpeta" to onFolder))
                        add(PafIconKind.QueueList to ("Añadir a Playlist" to onAddToPlaylist))
                        add(PafIconKind.TextLines to ("Letras" to onLyrics))
                    }
                    actions.forEach { (icon, pair) ->
                        val (label, action) = pair
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(50))
                                .clickable { action() }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PafIcon(icon, Paf.Text2, 18.dp)
                            Spacer(Modifier.width(18.dp))
                            Text(
                                label,
                                fontFamily = PafFamilyBold,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Paf.Text,
                            )
                        }
                    }
                }
            }
        }
    }
