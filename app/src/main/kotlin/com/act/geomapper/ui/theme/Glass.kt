package com.act.geomapper.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

// ── Detección de tema ─────────────────────────────────────────────────────────
val Color.isDark get() = luminance() < 0.5f

// ── Modo oscuro (paneles sobre mapa) ─────────────────────────────────────────
val GlassDark   = Color(0xCC0A0A0A)
val GlassBorder = Color(0x40FFFFFF)

@Composable
fun GlassBox(
    modifier    : Modifier   = Modifier,
    shape       : Shape      = RoundedCornerShape(16.dp),
    alpha       : Float      = 0.82f,
    borderWidth : Dp         = 1.dp,
    content     : @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(listOf(
                    Color(0xFF1A1A1A).copy(alpha = alpha),
                    Color(0xFF0D0D0D).copy(alpha = alpha + 0.05f)
                )), shape
            )
            .border(borderWidth,
                Brush.verticalGradient(listOf(GlassBorder, Color(0x15FFFFFF))),
                shape),
        content = content
    )
}

// ── Modo adaptable (claro/oscuro según MaterialTheme) ────────────────────────

val GlassLightBg     = Color(0xD9FFFFFF)
val GlassLightBorder = Color(0x30000000)
val GlassDarkBg      = Color(0xF0111111)   // superfice oscura para dark mode

@Composable
fun GlassLightBox(
    modifier  : Modifier = Modifier,
    shape     : Shape    = RoundedCornerShape(16.dp),
    elevation : Dp       = 4.dp,
    content   : @Composable BoxScope.() -> Unit
) {
    val isDark   = MaterialTheme.colorScheme.background.isDark
    val bgColor  = if (isDark) GlassDarkBg      else GlassLightBg
    val border   = if (isDark) Color(0x25FFFFFF) else GlassLightBorder
    val ambient  = if (isDark) Color.Transparent else Color(0x200D47A1)

    Box(
        modifier = modifier
            .shadow(elevation, shape, ambientColor = ambient, spotColor = ambient)
            .clip(shape)
            .background(bgColor, shape)
            .border(0.5.dp, border, shape),
        content = content
    )
}

// Variante verde ACT oscuro
@Composable
fun GlassGreenBox(
    modifier : Modifier = Modifier,
    shape    : Shape    = RoundedCornerShape(16.dp),
    content  : @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(listOf(
                    Color(0xFF1B5E20).copy(alpha = 0.85f),
                    Color(0xFF0D3312).copy(alpha = 0.90f)
                )), shape
            )
            .border(1.dp, Color(0x502E7D32), shape),
        content = content
    )
}
