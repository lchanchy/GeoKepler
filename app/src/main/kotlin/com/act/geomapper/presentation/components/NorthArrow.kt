package com.act.geomapper.presentation.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun NorthArrow(
    azimut  : Float,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val s    = com.act.geomapper.ui.theme.LocalStrings.current
    val anim by animateFloatAsState(
        targetValue   = azimut,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label         = "north"
    )

    // 80dp: espacio suficiente para flecha + letras N/S/E/O sin recorte
    Canvas(modifier = modifier.size(80.dp)) {
        val cx  = size.width  / 2f
        val cy  = size.height / 2f
        val len = size.minDimension / 2f - 14.dp.toPx()  // flecha más corta → deja margen para letras
        val w   = 5.dp.toPx()                             // ancho de la base

        // ── Flecha que gira con azimut ──────────────────────────────────
        rotate(-anim, Offset(cx, cy)) {
            // Norte (rojo)
            drawPath(Path().apply {
                moveTo(cx, cy - len); lineTo(cx - w, cy); lineTo(cx + w, cy); close()
            }, Color(0xFFE53935))

            // Sur (gris oscuro)
            drawPath(Path().apply {
                moveTo(cx, cy + len); lineTo(cx - w, cy); lineTo(cx + w, cy); close()
            }, Color(0xFF555555))
        }

        // Punto central
        drawCircle(Color(0xFF222222), 3.dp.toPx(), Offset(cx, cy))
        drawCircle(Color.White,       1.5.dp.toPx(), Offset(cx, cy))

        // ── Letras N/S/E/O fijas (no rotan con la flecha) ────────────────
        val cardinals = listOf(
            0f   to s.norte,
            180f to s.sur,
            90f  to s.este,
            270f to s.oeste
        )
        val labelR = len + 8.dp.toPx()   // radio de las letras (fuera de la flecha)

        cardinals.forEach { (ang, label) ->
            val isNorth = label == "N"
            val style = TextStyle(
                color      = if (isNorth) Color(0xFFE53935) else Color(0xFF333333),
                fontSize   = if (isNorth) 11.sp else 9.sp,
                fontWeight = if (isNorth) FontWeight.ExtraBold else FontWeight.SemiBold
            )
            val measured = textMeasurer.measure(label, style)
            val rad = Math.toRadians(ang.toDouble())
            val lx  = cx + labelR * sin(rad).toFloat() - measured.size.width  / 2f
            val ly  = cy - labelR * cos(rad).toFloat() - measured.size.height / 2f
            drawText(textMeasurer, label, topLeft = Offset(lx, ly), style = style)
        }
    }
}
