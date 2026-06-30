package com.act.geomapper.presentation.components

import android.content.Context
import android.hardware.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/** Devuelve el azimut en grados [0, 360) usando TYPE_ROTATION_VECTOR. */
@Composable
fun rememberAzimut(): Float {
    val context = LocalContext.current
    var azimut by remember { mutableFloatStateOf(0f) }

    DisposableEffect(Unit) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val rotMatrix = FloatArray(9)
        val orientation = FloatArray(3)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotMatrix, event.values)
                SensorManager.getOrientation(rotMatrix, orientation)
                azimut = Math.toDegrees(orientation[0].toDouble()).toFloat()
                    .let { if (it < 0) it + 360f else it }
            }
            override fun onAccuracyChanged(s: Sensor, a: Int) = Unit
        }

        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm.unregisterListener(listener) }
    }

    return azimut
}

@Composable
fun CompassRose(
    azimut: Float,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val animAzimut by animateFloatAsState(
        targetValue   = azimut,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
        label         = "compass"
    )

    Canvas(modifier = modifier.size(64.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension / 2f - 2.dp.toPx()

        rotate(-animAzimut, center) {
            dibujarRosa(center, r, textMeasurer)
        }
    }
}

private fun DrawScope.dibujarRosa(
    center: Offset,
    radius: Float,
    tm: TextMeasurer
) {
    val cardinals = listOf(
        0f   to "N",
        90f  to "E",
        180f to "S",
        270f to "O"
    )

    cardinals.forEachIndexed { idx, (ang, label) ->
        val rad  = Math.toRadians(ang.toDouble())
        val tipX = center.x + radius * sin(rad).toFloat()
        val tipY = center.y - radius * cos(rad).toFloat()

        // Punta de flecha
        val isNorth = idx == 0
        val color   = if (isNorth) Color(0xFF00C853) else Color(0xFF666666)   // N verde, resto gris
        val halfBase = radius * 0.22f
        val b1 = Offset(
            center.x + halfBase * cos(Math.toRadians(ang + 90.0)).toFloat(),
            center.y - halfBase * sin(Math.toRadians(ang + 90.0)).toFloat()
        )
        val b2 = Offset(
            center.x + halfBase * cos(Math.toRadians(ang - 90.0)).toFloat(),
            center.y - halfBase * sin(Math.toRadians(ang - 90.0)).toFloat()
        )

        drawPath(Path().apply {
            moveTo(tipX, tipY); lineTo(b1.x, b1.y)
            lineTo(center.x, center.y); lineTo(b2.x, b2.y); close()
        }, color)

        // Letras en las 4 puntas principales
        val style  = TextStyle(
            color      = if (isNorth) Color(0xFF00C853) else Color(0xFF444444),
            fontSize   = 9.sp,
            fontWeight = if (isNorth) FontWeight.ExtraBold else FontWeight.Medium
        )
        val measured = tm.measure(label, style)
        val labelOffset = radius * 0.72f
        val lx = center.x + labelOffset * sin(rad).toFloat() - measured.size.width / 2f
        val ly = center.y - labelOffset * cos(rad).toFloat() - measured.size.height / 2f
        drawText(tm, label, topLeft = Offset(lx, ly), style = style)
    }

    // Círculo central
    drawCircle(Color(0xFF0D2B4E), 3.dp.toPx(), center)
    drawCircle(Color.White, 1.5.dp.toPx(), center)
}
