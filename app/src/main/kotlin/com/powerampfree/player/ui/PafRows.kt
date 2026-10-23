package com.powerampfree.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powerampfree.player.MainActivity
import com.powerampfree.player.MediaTrack
import com.powerampfree.player.OpenCtx
import com.powerampfree.player.QueueBus

/**
 * Filas y chips compartidos al estilo de la referencia visual (docs/UI_SPEC
 * §4): fila de pista de 96 dp con miniatura de 80 dp esquinas 12, tres líneas
 * (título 20 sp / artista 16 sp / dur|ext 14 sp) y la pista en reproducción
 * resaltada con pastilla #181818. Componentes originales.
 */

/** ¿La fila es la pista en reproducción? Lee el estado de cola compartido. */
private fun currentUri(): String? = QueueBus.tracks.getOrNull(QueueBus.index)?.uri

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MainActivity.PafTrackRow(
    track: MediaTrack,
    tracks: List<MediaTrack>,
    index: Int,
    onOpenCtx: OpenCtx,
    selecting: Boolean = false,
    selected: Boolean = false,
    onSelectToggle: (() -> Unit)? = null,
) {
    val density = LocalDensity.current
    QueueBus.version.value // suscripción: invalida la fila al cambiar la cola
    val isCurrent = currentUri() == track.uri && track.uri.isNotEmpty()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isCurrent) Paf.Surf2 else Color.Transparent),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .combinedClickable(
                    onClick = {
                        if (selecting) onSelectToggle?.invoke() else playTracks(tracks, index)
                    },
                    onLongClick = { onOpenCtx(track, tracks, index) },
                )
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selecting) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) Paf.Text else Color.Transparent,
                        ),
                )
                Spacer(Modifier.width(10.dp))
            }
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(Paf.Surf3, RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp)),
            ) {
                AlbumArtImage(
                    snap = PlayerSnapshot(
                        title = track.title,
                        artist = track.artist,
                        uri = track.uri,
                        isPlaying = false,
                        format = "",
                        albumId = track.albumId,
                        folder = track.folder,
                    ),
                    px = with(density) { 80.dp.toPx().toInt() },
                    modifier = Modifier.size(80.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    track.title,
                    fontFamily = PafFamilyBold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp,
                    color = Paf.Text,
                    maxLines = 1,
                    modifier = Modifier.clipToBounds(),
                )
                if (track.artist.isNotBlank()) {
                    Text(
                        track.artist,
                        fontFamily = PafFamily,
                        fontSize = 15.sp,
                        color = Paf.Text3,
                        maxLines = 1,
                    )
                }
                Text(
                    run {
                        val alb = track.album
                        val albLabel = if (alb.isNotBlank() && !alb.startsWith("<unknown>")) alb else
                            track.artist.takeIf { it.isNotBlank() && !it.startsWith("<unknown>") } ?: ""
                        if (albLabel.isEmpty()) {
                            "${fmtDur(track.durationMs)}"
                        } else {
                            "${fmtDur(track.durationMs)} | ${albLabel}"
                        }
                    },
                    fontFamily = PafFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = Paf.Text3,
                )
            }
        }
    }
}

/** Chip de acción en pastilla (#222; activo = inversión como en la referencia). */
@Composable
internal fun PafActionChip(
    label: String,
    icon: PafIconKind? = null,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (active) Paf.Text else Paf.Surf3)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            PafIcon(icon, if (active) Paf.Bg else Paf.Text, 16.dp)
            Spacer(Modifier.width(6.dp))
        }
        Text(
            label,
            fontFamily = PafFamilyBold,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = if (active) Paf.Bg else Paf.Text,
        )
    }
}

/** Pastilla breadcrumb con borde (#3F3F3F) y fondo transparente (§4.2). */
@Composable
internal fun PafBackPill(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, Paf.Stroke, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            fontFamily = PafFamilyBold,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = Paf.Text2,
        )
    }
}

/** Círculo de categoría con glifo blanco (§4.1). */
@Composable
internal fun PafCateRow(
    color: Color,
    icon: PafIconKind,
    label: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(color, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            PafIcon(icon, Paf.Text, 18.dp)
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(
                label,
                fontFamily = PafFamilyBold,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = Paf.Text,
                maxLines = 1,
            )
            if (subtitle.isNotEmpty()) {
                Text(subtitle, fontFamily = PafFamily, fontSize = 12.sp, color = Paf.Text3)
            }
        }
    }
}
