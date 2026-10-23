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

    @Composable
internal fun MainActivity.LookAndFeelScreen(
        themeMode: String,
        onThemeMode: (String) -> Unit,
        accent: Int?,
        onAccent: (Int?) -> Unit,
        bold: Boolean,
        onBold: (Boolean) -> Unit,
        startAtLibrary: Boolean,
        onStartAtLibrary: (Boolean) -> Unit,
    ) {
        var showThemeDialog by remember { mutableStateOf(false) }
        var showAccentDialog by remember { mutableStateOf(false) }

        Column(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = bold, onCheckedChange = onBold)
                Text("  Fuente negrita", style = MaterialTheme.typography.bodyLarge)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = startAtLibrary, onCheckedChange = onStartAtLibrary)
                Text("  Arrancar en Biblioteca", style = MaterialTheme.typography.bodyLarge)
            }
            OutlinedButton(onClick = { showThemeDialog = true }) {
                val label = when (themeMode) {
                    AppearanceStore.MODE_LIGHT -> "Claro"
                    AppearanceStore.MODE_DARK -> "Oscuro"
                    else -> "Automático"
                }
                Text("Tema: $label ▸")
            }
            Spacer(Modifier.height(4.dp))
            OutlinedButton(onClick = { showAccentDialog = true }) {
                Text("Acento: ${AppearanceStore.accentName(this@LookAndFeelScreen)} ▸")
            }
        }

        if (showThemeDialog) {
            AlertDialog(
                onDismissRequest = { showThemeDialog = false },
                title = { Text("Settings Theme") },
                text = {
                    Column {
                        listOf(
                            "Automático" to AppearanceStore.MODE_AUTO,
                            "Claro" to AppearanceStore.MODE_LIGHT,
                            "Oscuro" to AppearanceStore.MODE_DARK,
                        ).forEach { (label, mode) ->
                            TextButton({
                                onThemeMode(mode)
                                showThemeDialog = false
                            }) {
                                Text(
                                    label + if (themeMode == mode) "  ✓" else "",
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton({ showThemeDialog = false }) { Text("Cerrar") }
                },
            )
        }
        if (showAccentDialog) {
            AlertDialog(
                onDismissRequest = { showAccentDialog = false },
                title = { Text("Acento") },
                text = {
                    Column {
                        TextButton({
                            onAccent(null)
                            showAccentDialog = false
                        }) { Text("Predeterminado" + if (accent == null) "  ✓" else "") }
                        AppearanceStore.ACCENTS.forEach { (name, argb) ->
                            TextButton({
                                onAccent(argb)
                                showAccentDialog = false
                            }) {
                                Text(
                                    name + if (accent == argb) "  ✓" else "",
                                    color = Color(argb),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton({ showAccentDialog = false }) { Text("Cerrar") }
                },
            )
        }
    }


    @Composable
internal fun MainActivity.SettingsLibraryScreen(onNote: (String) -> Unit) {
        val ctx = this@SettingsLibraryScreen
        var storeProgress by remember { mutableStateOf(LibrarySettings.storeProgress(ctx)) }
        var progressMin by remember { mutableStateOf(LibrarySettings.progressMinMinutes(ctx)) }
        var countPct by remember { mutableStateOf(LibrarySettings.countPct(ctx)) }
        var ignoreShort by remember { mutableStateOf(LibrarySettings.ignoreShort(ctx)) }
        var stats by remember { mutableStateOf(TrackStateStore.statsSummary(ctx)) }

        Column(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
            Text(
                "${libraryOrEmpty.size} pistas en MediaStore · " +
                    "${libraryOrEmpty.count { it.uri.startsWith("file:") || it.uri.startsWith("/") }} importadas (SAF)",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = {
                libraryOrEmpty = MediaStoreLibrary.query(this@SettingsLibraryScreen)
                onNote("Rescan completado: ${libraryOrEmpty.size} pistas")
            }) { Text("⭮ Rescan") }

            Spacer(Modifier.height(12.dp))
            Text("Per Track Progress", style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = storeProgress, onCheckedChange = { on ->
                    LibrarySettings.setStoreProgress(ctx, on)
                    storeProgress = on
                })
                Text("  Almacenar progreso por pista", style = MaterialTheme.typography.bodyLarge)
            }
            Text(
                "Solo pistas de al menos $progressMin min guardan posición " +
                    "(podcasts/sets); al reabrirlas continúan donde quedaron.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Text("Umbral: $progressMin min", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = progressMin.toFloat(),
                onValueChange = { v ->
                    LibrarySettings.setProgressMinMinutes(ctx, v.roundToInt())
                    progressMin = LibrarySettings.progressMinMinutes(ctx)
                },
                valueRange = 1f..90f,
                steps = 88,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))
            Text("Count as played", style = MaterialTheme.typography.titleSmall)
            Text(
                "Una escucha cuenta tras reproducir al menos el $countPct % " +
                    "(avance natural cuenta siempre; pausar no cuenta).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Text("Umbral: $countPct %", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = countPct.toFloat(),
                onValueChange = { v ->
                    LibrarySettings.setCountPct(ctx, v.roundToInt())
                    countPct = LibrarySettings.countPct(ctx)
                },
                valueRange = 1f..100f,
                steps = 98,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))
            Text("Filtros de biblioteca", style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = ignoreShort, onCheckedChange = { on ->
                    LibrarySettings.setIgnoreShort(ctx, on)
                    ignoreShort = on
                })
                Text("  Ignorar pistas cortas (< 6 s)", style = MaterialTheme.typography.bodyLarge)
            }
            Text(
                "Notificaciones/melodías excluidas del rescan. Video ya queda " +
                    "fuente de los datos (solo MediaStore.Audio). Requiere rescan.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )

            Spacer(Modifier.height(12.dp))
            Text("Estadísticas", style = MaterialTheme.typography.titleSmall)
            Text(
                "${stats.first} pistas reproducidas · ${stats.second} escuchas totales",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(onClick = {
                TrackStateStore.resetStats(ctx)
                stats = TrackStateStore.statsSummary(ctx)
                onNote("Estadísticas reiniciadas (posiciones por pista conservadas)")
            }) { Text("🗑 Reset stats") }
            Text(
                "Progreso/stats viven en filesDir/track_state.json, no se borran " +
                    "en el rescan. Reset solo limpia conteos, NO las posiciones.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }


    /**
     * Visualization settings (block 14 núcleo). Live effect:
     * `SpectrumBars` re-reads `viz_settings` every 50 ms, so these controls act
     * immediately on the player and the EQ screen. Display-only settings:
     * no session push needed.
     */
    @Composable
internal fun MainActivity.VizSettingsScreen(onNote: (String) -> Unit) {
        val ctx = this@VizSettingsScreen
        var playerOn by remember { mutableStateOf(VizSettings.playerSpectrum(ctx)) }
        var style by remember { mutableStateOf(VizSettings.spectrumStyle(ctx)) }
        var opacity by remember { mutableStateOf(VizSettings.panelOpacity(ctx)) }
        var eqMode by remember { mutableStateOf(VizSettings.eqSpectrum(ctx)) }

        Column(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
            Text(
                "Espectro del player (barra en vivo sobre el fondo)",
                style = MaterialTheme.typography.titleSmall,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = playerOn, onCheckedChange = { on ->
                    VizSettings.setPlayerSpectrum(ctx, on)
                    playerOn = on
                    onNote(if (on) "Espectro del player ON" else "Espectro del player OFF")
                })
                Text("  Visualization On Player Screen", style = MaterialTheme.typography.bodyLarge)
            }
            Text(
                "Visible en el player cuando se reproduce (también con el botón 📊 " +
                    "de la cabecera). Estilo y opacidad aplican a player y EQ.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )

            Spacer(Modifier.height(12.dp))
            Text("Estilo de las barras", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = {
                    VizSettings.setSpectrumStyle(ctx, VizSettings.STYLE_CLASSIC)
                    style = VizSettings.spectrumStyle(ctx)
                }) {
                    Text(
                        "▦ Classic",
                        color = if (style == VizSettings.STYLE_CLASSIC)
                            MaterialTheme.colorScheme.primary else LocalContentColor.current,
                    )
                }
                OutlinedButton(onClick = {
                    VizSettings.setSpectrumStyle(ctx, VizSettings.STYLE_ROUNDED)
                    style = VizSettings.spectrumStyle(ctx)
                }) {
                    Text(
                        "● Rounded",
                        color = if (style == VizSettings.STYLE_ROUNDED)
                            MaterialTheme.colorScheme.primary else LocalContentColor.current,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Text("Opacidad del panel", style = MaterialTheme.typography.titleSmall)
            Text(
                "Top Visualization Panel Opacity — $opacity %",
                style = MaterialTheme.typography.bodySmall,
            )
            Slider(
                value = opacity.toFloat(),
                onValueChange = { v ->
                    VizSettings.setPanelOpacity(ctx, v.roundToInt())
                    opacity = VizSettings.panelOpacity(ctx)
                },
                valueRange = 0f..100f,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))
            Text("Equalizer Screen Spectrum", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    "Disabled" to VizSettings.EQ_DISABLED,
                    "Classic" to VizSettings.STYLE_CLASSIC,
                    "Rounded" to VizSettings.STYLE_ROUNDED,
                ).forEach { (label, mode) ->
                    OutlinedButton(onClick = {
                        VizSettings.setEqSpectrum(ctx, mode)
                        eqMode = VizSettings.eqSpectrum(ctx)
                    }) {
                        Text(
                            label,
                            color = if (eqMode == mode)
                                MaterialTheme.colorScheme.primary else LocalContentColor.current,
                        )
                    }
                }
            }
            Text(
                "Referencia del catálogo: Disabled / Classic / Rounded. Big Spectrum " +
                    "MilkDrop (presets .milk), Visualization Delay y opciones de render " +
                    "(HD/Crop/FPS) siguen pendientes: Poweramp carga presets de terceros.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }


    /**
     * Blocks 12/13: carátulas + fondo derivado. Display-only
     * settings (no session push); read live by the composables.
     */
    @Composable
internal fun MainActivity.ArtSettingsScreen(currentSnapshot: PlayerSnapshot) {
        val ctx = this@ArtSettingsScreen
        var highRes by remember { mutableStateOf(ArtSettings.highResArt(ctx)) }
        var keepAspect by remember { mutableStateOf(ArtSettings.keepAspect(ctx)) }
        var bgOn by remember { mutableStateOf(ArtSettings.bgEnabled(ctx)) }
        var blur by remember { mutableStateOf(ArtSettings.bgBlur(ctx)) }
        var gradient by remember { mutableStateOf(ArtSettings.bgGradient(ctx)) }

        Column(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
            Text("Carátula", style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = highRes, onCheckedChange = { on ->
                    ArtSettings.setHighResArt(ctx, on)
                    highRes = on
                })
                Text("  Enviar carátula en alta resolución", style = MaterialTheme.typography.bodyLarge)
            }
            Text(
                "Alta resolución: bitmap completo hacia la sesión/notificación; " +
                    "OFF limita a 512 px.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = keepAspect, onCheckedChange = { on ->
                    ArtSettings.setKeepAspect(ctx, on)
                    keepAspect = on
                })
                Text("  Mantener proporción", style = MaterialTheme.typography.bodyLarge)
            }
            Text(
                "Fuentes: carátula de álbum del sistema (MediaStore por albumId) " +
                    "y Cover.jpg/… junto al archivo para pistas con ruta directa. " +
                    "Pistas por SAF árbol sin ruta local muestran glifo genérico.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            // Diagnóstico block 12: what the art lookup currently sees.
            Text(
                "Pista actual: albumId=${currentSnapshot.albumId} · " +
                    "carpeta=${currentSnapshot.folder.ifEmpty { "—" }} " +
                    "(bus n=${QueueBus.tracks.size} i=${QueueBus.index})",
                style = MaterialTheme.typography.bodySmall,
            )

            Spacer(Modifier.height(12.dp))
            Text("Fondo derivado (bloque 13)", style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = bgOn, onCheckedChange = { on ->
                    ArtSettings.setBgEnabled(ctx, on)
                    bgOn = on
                })
                Text("  Fondo borroso del player", style = MaterialTheme.typography.bodyLarge)
            }
            Text(
                "Desenfoque de fondo: $blur",
                style = MaterialTheme.typography.bodySmall,
            )
            Slider(
                value = blur.toFloat(),
                onValueChange = { v ->
                    blur = v.toInt().coerceIn(0, ArtSettings.BLUR_MAX)
                    ArtSettings.setBgBlur(ctx, blur)
                },
                valueRange = 0f..ArtSettings.BLUR_MAX.toFloat(),
                steps = ArtSettings.BLUR_MAX - 1,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Gradiente del fondo: $gradient",
                style = MaterialTheme.typography.bodySmall,
            )
            Slider(
                value = gradient.toFloat(),
                onValueChange = { v ->
                    gradient = v.toInt().coerceIn(0, ArtSettings.GRADIENT_MAX)
                    ArtSettings.setBgGradient(ctx, gradient)
                },
                valueRange = 0f..ArtSettings.GRADIENT_MAX.toFloat(),
                steps = ArtSettings.GRADIENT_MAX - 1,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(onClick = {
                AlbumArtLoader.clear()
                RootNote.note = "Cache de carátulas limpiada"
            }) { Text("🗑 Borrar cache de carátulas") }
        }
    }


    @Composable
internal fun MainActivity.AboutScreen() {
        Column(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
            val ver = runCatching {
                packageManager.getPackageInfo(packageName, 0).versionName
            }.getOrNull() ?: "?"
            Text("PowerampFree — reconstrucción original", style = MaterialTheme.typography.titleMedium)
            Text(
                "paquete: $packageName\nversión: $ver\n" +
                    "motor: :core:audio (paramEQ/crossfade/limiter) · MediaStore + SAF",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }


    /** Shared mutable note so import results reach the UI (simple app scope). */
internal object RootNote {
        var note: String by androidx.compose.runtime.mutableStateOf("")
    }
