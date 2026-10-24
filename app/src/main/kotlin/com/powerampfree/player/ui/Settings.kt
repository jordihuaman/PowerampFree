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

    // ------------------------------------------------------- settings screen

internal enum class SettingsSub { LOOKFEEL, LIBRARY, ABOUT, AUDIO, HEADSET, ART, VIZ }


    /** Prefs included in data export/import (block 6). */
internal val BACKUP_PREFS = listOf(
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
internal fun MainActivity.SettingsScreen(
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
                    }) { Text("\u2190") }
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

internal data class SettingsRow(
        val icon: PafIconKind,
        val title: String,
        val summary: String,
        val sub: SettingsSub?,
    )


    @Composable
internal fun MainActivity.SettingsRoot(onOpen: (SettingsSub) -> Unit) {
        val rows = listOf(
            SettingsRow(PafIconKind.Gear, "Look and Feel", "Tema, acento, tipografía", SettingsSub.LOOKFEEL),
            SettingsRow(PafIconKind.Bars, "Audio", "Focus/Replay Gain · bloque 8", SettingsSub.AUDIO),
            SettingsRow(
                PafIconKind.Bars, "Visualización",
                "Espectro player/EQ, estilo, opacidad · bloque 14", SettingsSub.VIZ,
            ),
            SettingsRow(PafIconKind.Note, "Background", "Fondo · en Album Art (bl. 13)", null),
            SettingsRow(PafIconKind.Note, "Album Art", "Carátulas + fondo · bloque 12/13", SettingsSub.ART),
            SettingsRow(PafIconKind.QueueList, "Biblioteca", "Rescan, progreso, stats, filtros", SettingsSub.LIBRARY),
            SettingsRow(PafIconKind.Headphones, "Headset/Bluetooth", "Botones y conexión · bloque 10", SettingsSub.HEADSET),
            SettingsRow(PafIconKind.Lock, "Lock Screen", "Opciones de lockscreen", null),
            SettingsRow(PafIconKind.Info, "Misc", "Scrobbling/Android Auto", null),
            SettingsRow(PafIconKind.Info, "About", "Versión y datos", SettingsSub.ABOUT),
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
        ) {
            itemsIndexed(rows) { _, r ->
                val enabled = r.sub != null
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(if (enabled) 1f else 0.45f)
                        .clip(RoundedCornerShape(50))
                        .clickable(enabled = enabled) { r.sub?.let(onOpen) }
                        .padding(vertical = 8.dp, horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Paf.Surf3, androidx.compose.foundation.shape.CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        PafIcon(r.icon, Paf.Text2, 18.dp)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            r.title,
                            fontFamily = PafFamilyBold,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = Paf.Text,
                        )
                        Text(
                            r.summary,
                            fontFamily = PafFamily,
                            fontSize = 12.sp,
                            color = Paf.Text3,
                        )
                    }
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
                    }) { Text("Export") }
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
                    }) { Text("Import") }
                }
                OutlinedButton(onClick = {
                    RootNote.note = runCatching { importLatestBackup() }
                        .getOrElse { "Import falló: ${it.message}" }
                }) { Text("Último backup") }
                Text(
                    RootNote.note,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }


    // ------------------------------------------------- export / import data

    /** Preferences + EQ preset files ? JSON in Downloads (block 6). */
internal fun MainActivity.exportData(): String {
        val root = org.json.JSONObject()
        root.put("app", "powerampfree-recreation")
        root.put("version", 1)
        root.put("when", System.currentTimeMillis())
        val prefsObj = org.json.JSONObject()
        for (name in BACKUP_PREFS) {
            val sp = getSharedPreferences(name, Context.MODE_PRIVATE)
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
        // Playlists: each file is self-contained JSON.
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
            return "Export OK ? Documents/PowerampFree/$displayName"
        } else {
            // Pre-Q scoped storage: app-specific public dir (still user-visible).
            val out = java.io.File(getExternalFilesDir(null), displayName)
            out.writeText(root.toString(2))
            return "Export OK ? ${out.absolutePath}"
        }
    }


    /** Newest export in Documents/PowerampFree via MediaStore (no SAF UI). */
internal fun MainActivity.importLatestBackup(): String {
        if (Build.VERSION.SDK_INT < 29) return "Import Último: requiere Android 10+"
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
        return "Import Último: no hay backups en Documents/PowerampFree"
    }


    /** Restore prefs + preset files from an exported JSON (SAF picker uri). */
internal fun MainActivity.importData(uri: Uri): String {
        val text = contentResolver.openInputStream(uri)?.use {
            it.readBytes().toString(Charsets.UTF_8)
        } ?: return "Import: no se pudo leer el archivo"
        val root = org.json.JSONObject(text)
        val prefsObj = root.optJSONObject("prefs")
            ?: return "Import: JSON sin sección prefs"
        for (name in prefsObj.keys()) {
            val sp = getSharedPreferences(name, Context.MODE_PRIVATE)
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
        // Playlists: restore each saved list file.
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
        // audio_settings (replay gain) was restored above · re-apply the trim.
        runCatching { sendReplayGain() }
        return "Import OK (${BACKUP_PREFS.size} prefs · " +
            "${root.optJSONArray("presets")?.length() ?: 0} presets · " +
            "${root.optJSONArray("playlists")?.length() ?: 0} playlists · " +
            "${root.optJSONObject("trackState")?.length() ?: 0} pistas estado)"
    }
