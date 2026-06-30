package com.act.geomapper.presentation.overlay

import android.graphics.Color
import android.graphics.Point
import android.view.MotionEvent
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay

private const val TAG_EDIT_VERTEX = "edit_vertex"

/**
 * Gestiona la edición de vértices arrastrables sobre el mapa.
 *
 * NO usa Marker.isDraggable (que requiere long-press y pierde el evento ante el scroll del mapa).
 * En su lugar, un Overlay propio intercepta ACTION_DOWN/MOVE/UP y mueve el Marker más cercano.
 */
class VertexEditOverlay {

    private val markers    = mutableListOf<Marker>()
    private var dragOverlay: VertexDragOverlay? = null

    /**
     * @param onChange se llama en cada ACTION_MOVE — úsalo para redibuj ar el preview en tiempo real.
     */
    fun activar(mapView: MapView, vertices: List<GeoPoint>, onChange: (() -> Unit)? = null) {
        limpiar(mapView)

        vertices.forEach { gp ->
            Marker(mapView).apply {
                id       = TAG_EDIT_VERTEX
                position = GeoPoint(gp.latitude, gp.longitude)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                isDraggable = false
                icon = crearPuntoDot(
                    context   = mapView.context,
                    colorFill = Color.parseColor("#FF9800"),
                    sizeDp    = 24
                )
                mapView.overlays.add(this)
                markers.add(this)
            }
        }

        dragOverlay = VertexDragOverlay(mapView, markers, onChange).also {
            mapView.overlays.add(it)
        }

        mapView.invalidate()
    }

    fun obtenerVertices(): List<GeoPoint> = markers.map { it.position }

    fun estaActivo(): Boolean = markers.isNotEmpty()

    fun limpiar(mapView: MapView) {
        markers.forEach { mapView.overlays.remove(it) }
        markers.clear()
        dragOverlay?.let { mapView.overlays.remove(it) }
        dragOverlay = null
        mapView.invalidate()
    }
}

/**
 * Overlay que intercepta touch events y mueve el Marker más cercano al dedo.
 *
 * Radio de captura generoso (56px ≈ 14dp @ 4x) para facilitar uso en campo con guantes.
 */
private class VertexDragOverlay(
    private val mapView  : MapView,
    private val markers  : List<Marker>,
    private val onChange : (() -> Unit)? = null
) : Overlay() {

    private var dragging: Marker? = null
    private val pt               = Point()
    private val touchRadiusPx    = 56f * mapView.resources.displayMetrics.density

    override fun onTouchEvent(event: MotionEvent, mapView: MapView): Boolean {
        val x = event.x
        val y = event.y

        return when (event.actionMasked) {

            MotionEvent.ACTION_DOWN -> {
                dragging = nearestMarker(x, y)
                dragging != null   // consumir solo si tocamos un vértice
            }

            MotionEvent.ACTION_MOVE -> {
                dragging?.let { m ->
                    val gp = mapView.projection.fromPixels(x.toInt(), y.toInt()) as GeoPoint
                    m.position = gp
                    onChange?.invoke()   // preview en tiempo real
                    mapView.invalidate()
                    true
                } ?: false
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = null
                false
            }

            else -> false
        }
    }

    private fun nearestMarker(x: Float, y: Float): Marker? {
        var best: Marker? = null
        var bestDist = touchRadiusPx * touchRadiusPx   // comparar con dist²

        markers.forEach { m ->
            mapView.projection.toPixels(m.position, pt)
            val dx = x - pt.x
            val dy = y - pt.y
            val dist2 = dx * dx + dy * dy
            if (dist2 < bestDist) {
                bestDist = dist2
                best = m
            }
        }
        return best
    }
}
