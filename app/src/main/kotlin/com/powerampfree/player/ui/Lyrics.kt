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

    // ------------------------------------------------ lyrics
    /**
     * Reads the synced .lrc sitting next to an audio file (block 5). Tries,
     * in order: file-system path (file:/absolute uris), MediaStore DATA of
     * the audio row, then a MediaStore Files row with the same basename.
     */
internal fun MainActivity.findLyrics(context: Context, uriStr: String): List<LrcLine>? {
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
internal fun MainActivity.LyricsScreen(track: MediaTrack, isCurrent: Boolean, onClose: () -> Unit) {
        // BACK handled inside the overlay: inner-most BackHandler wins.
        BackHandler { onClose() }
        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
        var lines by remember(track.uri) { mutableStateOf<List<LrcLine>?>(null) }
        androidx.compose.runtime.LaunchedEffect(track.uri) {
            // null lookup result maps to an empty list so the UI leaves the
            // "Buscando letras…" state and renders the "Sin letras" note.
            val found = withContext(Dispatchers.IO) {
                findLyrics(this@LyricsScreen, track.uri) ?: emptyList()
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
