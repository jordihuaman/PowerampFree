package com.powerampfree.player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powerampfree.player.MainActivity

/**
 * Hoja del Menú principal (tab 4 — UI_SPEC §7): sube desde abajo sobre scrim,
 * esquinas superiores redondeadas, fondo #181818. Logo propio (PowerampFree,
 * nunca la marca de la referencia), luego filas: Ajustes, Cola, Acerca de.
 * Mini-player + tabs siguen visibles al pie (vive DENTRO del content Box).
 */
@Composable
internal fun MainActivity.MainMenuSheet(
    onClose: () -> Unit,
    onSettings: () -> Unit,
    onQueue: () -> Unit,
    onAbout: () -> Unit,
) {
    BackHandler { onClose() }
    val scrim = remember { MutableInteractionSource() }
    val bodyClick = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x88000000))
            .clickable(
                interactionSource = scrim,
                indication = null,
                onClick = onClose,
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            color = Paf.Surf2,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            // El cuerpo traga los taps para que no cierren vía scrim.
            Column(
                modifier = Modifier
                    .clickable(
                        interactionSource = bodyClick,
                        indication = null,
                        onClick = {},
                    )
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.height(22.dp))
                Text(
                    "POWERAMPFREE",
                    fontFamily = MaterialTheme.typography.headlineLarge.fontFamily,
                    fontWeight = FontWeight.Black,
                    fontSize = 30.sp,
                    letterSpacing = 1.5.sp,
                    color = Paf.Text,
                )
                Text(
                    "Reproductor libre",
                    fontFamily = MaterialTheme.typography.labelMedium.fontFamily,
                    fontSize = 13.sp,
                    color = Paf.Text3,
                )
                Spacer(modifier = Modifier.height(16.dp))

                MenuRow(icon = PafIconKind.Gear, label = "Ajustes", onClick = onSettings)
                MenuRow(icon = PafIconKind.QueueList, label = "Cola de reproducción", onClick = onQueue)
                MenuRow(icon = PafIconKind.Info, label = "Acerca de", onClick = onAbout)
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun MenuRow(icon: PafIconKind, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PafIcon(icon, Paf.Text, 24.dp)
        Spacer(Modifier.width(14.dp))
        Text(label, fontFamily = PafFamilyBold, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Paf.Text)
    }
}
