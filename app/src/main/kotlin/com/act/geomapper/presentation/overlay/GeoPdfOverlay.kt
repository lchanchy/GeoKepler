package com.act.geomapper.presentation.overlay

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.graphics.Rect
import android.graphics.RectF
import com.act.geomapper.data.geopdf.GeoPdfData
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

class GeoPdfOverlay(private val data: GeoPdfData) : Overlay() {

    private val paint = Paint().apply { alpha = 210; isFilterBitmap = true }
    private val src   = Rect(0, 0, data.bitmap.width, data.bitmap.height)
    private val ptNW  = Point()
    private val ptSE  = Point()

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val proj = mapView.projection
        proj.toPixels(GeoPoint(data.norte, data.oeste), ptNW)
        proj.toPixels(GeoPoint(data.sur,   data.este),  ptSE)
        val dst = RectF(
            ptNW.x.toFloat(), ptNW.y.toFloat(),
            ptSE.x.toFloat(), ptSE.y.toFloat()
        )
        canvas.drawBitmap(data.bitmap, src, dst, paint)
    }
}
