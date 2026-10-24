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

    // -------------------------------------- track context menu

internal fun MainActivity.extLabel(uriStr: String): String =
        uriStr.substringBefore('?').substringAfterLast('.', "").uppercase().ifBlank { "?" }


/** Etiqueta de artista: MediaStore usa "<unknown>" para las pistas sin tag. */
internal fun artistLabel(raw: String): String =
    raw.takeIf { it.isNotBlank() && !it.startsWith("<unknown>") } ?: "Artista desconocido"


internal fun MainActivity.fmtDur(ms: Long): String {
        if (ms <= 0) return "—"
        val s = ms / 1000
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
    }


    @Composable
    /**
     * Live spectrum bars (block 14 núcleo). Style/opacity come
     * from `viz_settings`, re-read inside the 50 ms poll (cached prefs, cheap)
     * so the header 📊 toggle and the Visualization screen act instantly
     * without any session push. Opacity maps 1:1 onto the bars alpha
     * (default 60 == the previous hard-coded 0x99 alpha).
     */
internal fun MainActivity.SpectrumBars(isPlaying: Boolean) {
        var spectrum by androidx.compose.runtime.remember {
            mutableStateOf(VisualizerTap.spectrum())
        }
        var style by androidx.compose.runtime.remember {
            mutableStateOf(VizSettings.spectrumStyle(this@SpectrumBars))
        }
        var alphaPct by androidx.compose.runtime.remember {
            mutableStateOf(VizSettings.panelOpacity(this@SpectrumBars))
        }
        androidx.compose.runtime.LaunchedEffect(isPlaying) {
            while (kotlinx.coroutines.currentCoroutineContext().isActive) {
                if (isPlaying) spectrum = VisualizerTap.spectrum()
                style = VizSettings.spectrumStyle(this@SpectrumBars)
                alphaPct = VizSettings.panelOpacity(this@SpectrumBars)
                kotlinx.coroutines.delay(50)
            }
        }
        Canvas(modifier = Modifier.fillMaxWidth().height(84.dp)) {
            val barCount = 48
            val fLow = 30.0
            val fHigh = 16_000.0
            val logSpan = kotlin.math.ln(fHigh / fLow)
            val barW = size.width / barCount
            val color = Paf.EqGreen.copy(alpha = alphaPct / 100f)
            val rounded = style == VizSettings.STYLE_ROUNDED
            val corner = androidx.compose.ui.geometry.CornerRadius(barW * 0.45f, barW * 0.45f)
            for (b in 0 until barCount) {
                val freq = fLow * Math.exp(logSpan * b / (barCount - 1.0))
                val k = (freq / 48_000.0 * VisualizerTap.bins).toInt()
                    .coerceIn(0, VisualizerTap.bins - 1)
                val mag = spectrum.getOrNull(k) ?: 0f
                val db = (20.0 * Math.log10(mag + 1e-9))
                val level = ((db + 60.0) / 60.0).coerceIn(0.0, 1.0).toFloat()
                val topLeft = Offset(b * barW + 1f, size.height * (1f - level))
                val barSize = androidx.compose.ui.geometry.Size(barW - 2f, size.height * level)
                if (rounded) {
                    drawRoundRect(color = color, topLeft = topLeft, size = barSize, cornerRadius = corner)
                } else {
                    drawRect(color = color, topLeft = topLeft, size = barSize)
                }
            }
        }
    }


    @Composable
internal fun MainActivity.TextButton(onClick: () -> Unit, content: @Composable () -> Unit) =
        androidx.compose.material3.TextButton(onClick = onClick) { content() }
