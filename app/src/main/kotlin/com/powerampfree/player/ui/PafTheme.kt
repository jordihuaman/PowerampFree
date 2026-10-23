package com.powerampfree.player.ui

import android.graphics.Typeface
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

/**
 * Tokens de diseño medidos de la referencia visual (docs/UI_SPEC.md, pasada 1):
 * fondo negro puro con superficies grises, acento verde neón solo en el EQ y
 * sin elevaciones (la jerarquía se hace por contraste). Tipografía sans
 * condensada, siempre bold. 100 % original: solo constantes de color/tamaño
 * observadas, sin recursos de la app de referencia.
 */
internal object Paf {
    val Bg = Color(0xFF000000)          // fondo global
    val Surf1 = Color(0xFF0E0E0E)       // pastillas de título/artista, chips
    val Surf2 = Color(0xFF181818)       // nav inferior, mini-player, tarjetas
    val Surf3 = Color(0xFF222222)       // rieles, separadores, chips de acción
    val Surf4 = Color(0xFF313131)       // riel del EQ, fondo de preset
    val Stroke = Color(0xFF3F3F3F)      // contornos
    val Text = Color(0xFFFFFFFF)        // títulos, iconos activos
    val Text2 = Color(0xFFD1D1D1)       // secundario fuerte
    val Text3 = Color(0xFF999999)       // iconos inactivos, tiempos
    val EqGreen = Color(0xFF00FF00)     // barras del EQ / línea de respuesta
    val EqGlow = Color(0xFF00A000)      // halo del verde

    // Acentos de categoría de la biblioteca (círculos con glifo blanco)
    val CatAllSongs = Color(0xFF5066D8)
    val CatFolders = Color(0xFF4D7AC5)
    val CatGenres = Color(0xFF5392A2)
    val CatYears = Color(0xFF663E63)
    val CatComposer = Color(0xFF235E3D)
    val CatPlaylist = Color(0xFF3E5C8F) // tono propio (sin análogo medido)
    val CatQueue = Color(0xFF313131)
}

/** Esquema oscuro con los tokens medidos (no hay modo claro observado). */
internal fun pafScheme(accentColor: Int?) = darkColorScheme(
    background = Paf.Bg,
    onBackground = Paf.Text,
    surface = Paf.Bg,
    onSurface = Paf.Text,
    surfaceVariant = Paf.Surf2,
    onSurfaceVariant = Paf.Text3,
    surfaceContainer = Paf.Surf2,
    surfaceContainerHighest = Paf.Surf3,
    primary = if (accentColor != null) Color(accentColor) else Paf.Text,
    onPrimary = Paf.Bg,
    secondary = Paf.Text2,
    onSecondary = Paf.Bg,
    outline = Paf.Stroke,
    outlineVariant = Paf.Surf3,
)

/** Familia condensada compartida (medida en la referencia; bold de fábrica). */
internal val PafFamilyBold = FontFamily(Typeface.create("sans-serif-condensed", Typeface.BOLD))
internal val PafFamily = FontFamily(Typeface.create("sans-serif-condensed", Typeface.NORMAL))

/**
 * Tipografía: familia condensada para TODOS los estilos, bold de fábrica
 * (extensible con el toggle de la pantalla Look and Feel). Tamaños según
 * UI_SPEC §1.2: títulos ≈32 sp, filas ≈20 sp, secundarios 16 sp, meta 14 sp.
 */
internal fun pafTypography(base: Typography, bold: Boolean): Typography {
    val family = if (bold) PafFamilyBold else PafFamily
    fun st(s: TextStyle, szIsh: androidx.compose.ui.unit.TextUnit) =
        s.copy(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = szIsh)
    return base.copy(
        displayLarge = st(base.displayLarge, 57.sp),
        displayMedium = st(base.displayMedium, 44.sp),
        displaySmall = st(base.displaySmall, 34.sp),
        headlineLarge = st(base.headlineLarge, 32.sp),   // títulos de pantalla
        headlineMedium = st(base.headlineMedium, 28.sp),
        headlineSmall = st(base.headlineSmall, 24.sp),
        titleLarge = st(base.titleLarge, 22.sp),
        titleMedium = st(base.titleMedium, 20.sp),       // títulos de fila
        titleSmall = st(base.titleSmall, 16.sp),
        bodyLarge = base.bodyLarge.copy(fontFamily = family, fontSize = 16.sp),
        bodyMedium = base.bodyMedium.copy(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 16.sp),
        bodySmall = base.bodySmall.copy(fontFamily = family, fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 15.sp),
        labelMedium = base.labelMedium.copy(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 13.sp),
        labelSmall = base.labelSmall.copy(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 11.sp),
    )
}
