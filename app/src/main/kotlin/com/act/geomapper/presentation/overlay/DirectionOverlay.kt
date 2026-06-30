package com.act.geomapper.presentation.overlay

import android.graphics.*
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

/**
 * Dibuja un punto azul con flecha de heading en la posición GPS actual.
 * Actualizar [geoPoint] y [azimutDeg] desde Compose y llamar mapView.invalidate().
 */
class DirectionOverlay : Overlay() {

    @Volatile var geoPoint  : GeoPoint? = null
    @Volatile var azimutDeg : Float     = 0f

    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(60, 21, 101, 192)
        style = Paint.Style.FILL
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1565C0")
        style = Paint.Style.FILL
    }
    private val dotBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color       = Color.WHITE
        style       = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val screenPt = Point()

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val gp = geoPoint ?: return
        mapView.projection.toPixels(gp, screenPt)
        val x = screenPt.x.toFloat()
        val y = screenPt.y.toFloat()

        // Halo pulsante (tamaño fijo — la animación real se haría con invalidate en loop,
        // ponytail: tamaño estático suficiente para campo)
        canvas.drawCircle(x, y, 38f, haloPaint)

        // Punto azul con borde blanco
        canvas.drawCircle(x, y, 18f, dotPaint)
        canvas.drawCircle(x, y, 18f, dotBorderPaint)

        // Flecha de heading (triángulo blanco apuntando al norte rotado por azimut)
        canvas.save()
        canvas.rotate(azimutDeg, x, y)
        val arrow = Path().apply {
            moveTo(x,        y - 30f)   // punta
            lineTo(x - 8f,   y - 14f)   // base izquierda
            lineTo(x + 8f,   y - 14f)   // base derecha
            close()
        }
        canvas.drawPath(arrow, arrowPaint)
        canvas.restore()
    }
}
