package com.act.geomapper.presentation.screens

import android.content.Context
import android.graphics.Color as AColor
import android.graphics.Paint as APaint
import android.view.MotionEvent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.act.geomapper.data.settings.AppSettings
import com.act.geomapper.data.settings.toDisplayArea
import com.act.geomapper.data.settings.toDisplayDistance
import com.act.geomapper.domain.models.PuntoGps
import com.act.geomapper.presentation.components.*
import com.act.geomapper.presentation.components.toTileSource
import com.act.geomapper.presentation.viewmodels.MapUiState
import com.act.geomapper.presentation.viewmodels.MapViewModel
import com.act.geomapper.presentation.viewmodels.ModoCaptura
import com.act.geomapper.ui.theme.GlassBox
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay
import org.locationtech.jts.io.WKTReader
import com.act.geomapper.data.settings.CoordFormat
import com.act.geomapper.data.settings.formatCoord
import com.act.geomapper.presentation.overlay.DirectionOverlay
import com.act.geomapper.presentation.overlay.VertexEditOverlay
import com.act.geomapper.presentation.overlay.crearPuntoDot
import com.act.geomapper.ui.theme.rememberWindowInfo
import androidx.compose.foundation.layout.navigationBarsPadding

@Composable
fun MapScreen(
    viewModel       : MapViewModel,
    settings        : AppSettings                 = AppSettings(),
    basemapActual   : Basemap                     = Basemap.OSM,
    predios         : List<com.act.geomapper.domain.models.Predio> = emptyList(),
    ocultos         : Set<Long>                   = emptySet(),
    redrawVersion   : Int                         = 0,
    onGuardarEdicion: (Long, org.locationtech.jts.geom.Geometry) -> Unit = { _, _ -> },
    modifier        : Modifier                    = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val azimut  = rememberAzimut()
    val win     = rememberWindowInfo()

    LaunchedEffect(Unit) {
        Configuration.getInstance().apply {
            userAgentValue = context.packageName
            load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        }
        viewModel.iniciarGps()
    }

    LaunchedEffect(azimut) { viewModel.actualizarAzimut(azimut) }

    // Coordenadas del centro del mapa (la diana) — se actualizan en tiempo real al mover
    var centroDiana by remember { mutableStateOf(GeoPoint(4.6097, -74.0817)) }

    // Centrar en ubicación actual la primera vez que llega un fix GPS
    var centradoInicial by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.estadoGps.puntoActual) {
        if (!centradoInicial && uiState.estadoGps.puntoActual != null) {
            centradoInicial = true
            viewModel.centrarEnUbicacion()
        }
    }

    val mapView = remember {
        val initState = viewModel.uiState.value
        MapView(context).apply {
            setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(initState.lastMapZoom)
            controller.setCenter(GeoPoint(initState.lastMapLat, initState.lastMapLon))
            overlays.add(RotationGestureOverlay(this).also { it.isEnabled = true })
            overlays.add(object : org.osmdroid.views.overlay.Overlay() {
                override fun onSingleTapConfirmed(e: MotionEvent, mv: MapView): Boolean {
                    // Captura en el CENTRO del mapa (donde está la diana), no donde tocó el dedo
                    val centro = mv.mapCenter as GeoPoint
                    viewModel.capturarPuntoManual(PuntoGps(centro.latitude, centro.longitude))
                    return true
                }
            })
        }
    }

    LaunchedEffect(basemapActual) {
        mapView.setTileSource(basemapActual.toTileSource())
        mapView.invalidate()
    }

    // Listener del mapa: actualiza coordenadas de la diana en tiempo real
    // Colocado DESPUÉS de mapView y centroDiana para evitar "Unresolved reference"
    DisposableEffect(mapView) {
        val listener = object : MapListener {
            override fun onScroll(event: ScrollEvent): Boolean {
                centroDiana = mapView.mapCenter as GeoPoint
                return false
            }
            override fun onZoom(event: ZoomEvent): Boolean {
                centroDiana = mapView.mapCenter as GeoPoint
                return false
            }
        }
        mapView.addMapListener(listener)
        onDispose {
            mapView.removeMapListener(listener)
            // Guardar posición para restaurarla si MapScreen se recrea (ej. al volver de Parcelación)
            val c = mapView.mapCenter as GeoPoint
            viewModel.guardarPosicion(c.latitude, c.longitude, mapView.zoomLevelDouble)
        }
    }

    // Centrar en GPS al pedirlo
    LaunchedEffect(uiState.centrarEnGps) {
        if (uiState.centrarEnGps) {
            uiState.estadoGps.puntoActual?.let {
                mapView.controller.animateTo(GeoPoint(it.latitud, it.longitud))
                viewModel.centradoConsumido()
            }
        }
    }

    // P4 FIX: overlays de borrador (captura en curso) — no toca entidadesWkt
    LaunchedEffect(uiState.puntosCapturados, uiState.modoCaptura) {
        dibujarBorrador(mapView, uiState)
    }

    val wktWriter = remember { org.locationtech.jts.io.WKTWriter() }

    // redrawVersion es un Int (primitivo, siempre estable para Compose). Cada vez que
    // predios cambia en MapaApp se incrementa, garantizando que este LaunchedEffect se
    // reinicia sin depender de si Compose decide recomponer MapScreen o no.
    // NOTA: no usamos entidadesWkt como fuente — las entidades se auto-guardan a DB
    // inmediatamente, así que predios es siempre la fuente de verdad (incluso vacío).
    LaunchedEffect(redrawVersion, ocultos,
                   settings.rellenoPoligonos, settings.areaUnit, settings.distanceUnit) {
        val pares = predios.filter { it.id !in ocultos }.map { p -> p to wktWriter.write(p.geometry) }
        dibujarEntidadesGuardadas(mapView, pares, settings.rellenoPoligonos, settings.areaUnit, settings.distanceUnit)
    }

    // ── Overlay de edición de vértices ───────────────────────────────────────
    val vertexOverlay = remember { VertexEditOverlay() }
    val factory = remember { org.locationtech.jts.geom.GeometryFactory(org.locationtech.jts.geom.PrecisionModel(), 4326) }

    LaunchedEffect(uiState.editingPredio) {
        val predio = uiState.editingPredio
        if (predio == null) {
            limpiarPreviewEdicion(mapView)
            vertexOverlay.limpiar(mapView)
        } else {
            val coords = predio.geometry.coordinates
                .map { org.osmdroid.util.GeoPoint(it.y, it.x) }
            // Activar con callback de preview en tiempo real
            vertexOverlay.activar(mapView, coords) {
                dibujarPreviewEdicion(mapView, vertexOverlay.obtenerVertices(),
                    predio.geometry.geometryType)
            }
            val centroid = predio.geometry.centroid.coordinate
            mapView.controller.animateTo(org.osmdroid.util.GeoPoint(centroid.y, centroid.x))
        }
    }

    // Overlay de navegación: línea recta GPS→destino, se actualiza con cada fix GPS
    LaunchedEffect(uiState.estadoGps.puntoActual, uiState.navegacionDestino) {
        dibujarNavegacion(mapView, uiState.estadoGps.puntoActual, uiState.navegacionDestino)
    }

    // DirectionOverlay: punto azul con flecha de heading
    val dirOverlay = remember { DirectionOverlay() }
    LaunchedEffect(Unit) {
        if (!mapView.overlays.contains(dirOverlay))
            mapView.overlays.add(dirOverlay)
    }
    // Actualizar posición y azimut en el overlay
    LaunchedEffect(uiState.estadoGps.puntoActual, azimut) {
        uiState.estadoGps.puntoActual?.let {
            dirOverlay.geoPoint  = org.osmdroid.util.GeoPoint(it.latitud, it.longitud)
            dirOverlay.azimutDeg = azimut
            mapView.invalidate()
        }
    }
    LaunchedEffect(uiState.centrarEnGps) {
        if (uiState.centrarEnGps) {
            uiState.estadoGps.puntoActual?.let {
                mapView.controller.animateTo(org.osmdroid.util.GeoPoint(it.latitud, it.longitud))
            }
            viewModel.centradoConsumido()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {

        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

        // ── Crosshair full-screen (siempre visible) ───────────────────────
        Crosshair(modifier = Modifier.fillMaxSize())

        // ── Brújula — debajo del header, superior derecha ────────────────
        // statusBarsPadding + offset fijo relativo al header → funciona en todos los tamaños
        NorthArrow(
            azimut   = azimut,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = win.northArrowPad, end = 10.dp)
        )

        // ── Botón "ir a mi ubicación" ─────────────────────────────────────
        FloatingActionButton(
            onClick        = { viewModel.centrarEnUbicacion() },
            modifier       = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 16.dp, bottom = win.fabBottomPad)
                .size(win.fabSize),
            containerColor = Color(0xCC0D47A1),
            elevation      = FloatingActionButtonDefaults.elevation(2.dp, 2.dp)
        ) {
            Icon(Icons.Default.MyLocation, "Mi ubicación", tint = Color.White, modifier = Modifier.size(win.iconSize))
        }

        // ── Barra de captura ─────────────────────────────────────────────
        AnimatedVisibility(
            visible  = uiState.modoCaptura != ModoCaptura.NINGUNO,
            enter    = slideInVertically { it } + fadeIn(),
            exit     = slideOutVertically { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = win.captureBarBot, start = 12.dp, end = 80.dp)
        ) {
            BarraCaptura(
                modo              = uiState.modoCaptura,
                numPuntos         = uiState.puntosCapturados.size,
                onAgregarPuntoGps = viewModel::capturarPuntoGps,
                onUndo            = viewModel::undoUltimoPunto,
                onFinalizar       = viewModel::finalizarCaptura,
                onCancelar        = viewModel::cancelarCaptura
            )
        }

        // ── Coordenadas de la diana ───────────────────────────────────────
        val coordenadasVisibles = uiState.modoCaptura == ModoCaptura.NINGUNO
                               && uiState.editingPredio == null
        if (coordenadasVisibles) {
            GlassBox(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = win.coordBarBot),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier              = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.GpsFixed, null, tint = Color(0xFF81C784), modifier = Modifier.size(12.dp))
                    Text(
                        formatCoord(centroDiana.latitude, settings.coordFormat, isLat = true),
                        color = Color.White, fontSize = win.textMd.sp, fontWeight = FontWeight.Medium
                    )
                    Text("·", color = Color.White.copy(0.4f), fontSize = win.textMd.sp)
                    Text(
                        formatCoord(centroDiana.longitude, settings.coordFormat, isLat = false),
                        color = Color(0xFF90CAF9), fontSize = win.textMd.sp, fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // ── Chip de navegación: distancia en tiempo real al punto destino ────
        uiState.navegacionDestino?.let { destino ->
            val origen = uiState.estadoGps.puntoActual
            val distanciaTexto = if (origen != null) {
                val result = FloatArray(1)
                android.location.Location.distanceBetween(
                    origen.latitud, origen.longitud,
                    destino.latitud, destino.longitud,
                    result
                )
                result[0].toDouble().toDisplayDistance(settings.distanceUnit)
            } else "—"
            NavegacionChip(
                distancia = distanciaTexto,
                onDetener = viewModel::detenerNavegacion,
                modifier  = Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(start = 16.dp, bottom = win.fabBottomPad + 64.dp)
            )
        }

        // ── Chip de área resultado ────────────────────────────────────────
        AnimatedVisibility(
            visible  = uiState.wktResultado != null && uiState.areaHa > 0,
            enter    = fadeIn() + slideInVertically { it },
            exit     = fadeOut() + slideOutVertically { it },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(bottom = 56.dp, start = 8.dp)
        ) {
            AreaChip(uiState.areaHa.toDisplayArea(settings.areaUnit), viewModel::limpiarResultado)
        }

        // ── Barra de edición de geometría ─────────────────────────────────
        uiState.editingPredio?.let { predio ->
            BarraEdicion(
                nombreEntidad = predio.nombre.ifBlank { "Sin nombre" },
                onGuardar     = {
                    val vertices = vertexOverlay.obtenerVertices()
                    if (vertices.isNotEmpty()) {
                        val geom = reconstruirGeometria(factory, predio.geometry.geometryType, vertices)
                        if (geom != null) onGuardarEdicion(predio.id, geom)
                    }
                    limpiarPreviewEdicion(mapView)
                    viewModel.finalizarEdicion()
                },
                onCancelar    = { limpiarPreviewEdicion(mapView); viewModel.finalizarEdicion() },
                modifier      = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = win.captureBarBot, start = 12.dp, end = 12.dp)
            )
        }

        // ── Snackbar de recuperación de captura ────────────────────────────
        if (uiState.capturaRecuperada > 0) {
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 100.dp, start = 12.dp, end = 12.dp),
                action = {
                    Row {
                        TextButton(onClick = viewModel::descartarRecuperacion) {
                            Text("Descartar", color = Color(0xFFEF9A9A))
                        }
                        TextButton(onClick = viewModel::confirmarRecuperacion) {
                            Text("Continuar", color = Color(0xFF81C784))
                        }
                    }
                },
                containerColor = Color(0xFF1A3A5C)
            ) {
                Text(
                    "📍 ${uiState.capturaRecuperada} puntos de ${uiState.modoCaptura.name.lowercase()} recuperados",
                    fontSize = 12.sp, color = Color.White
                )
            }
        }

        // ── Error ─────────────────────────────────────────────────────────
        uiState.error?.let { err ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 100.dp, start = 16.dp, end = 80.dp),
                action = { TextButton(onClick = viewModel::limpiarError) { Text("OK") } }
            ) { Text(err, fontSize = 13.sp) }
        }
    }
}

// ── P3: Diana/crosshair ───────────────────────────────────────────────────────

@Composable
private fun Crosshair(modifier: Modifier = Modifier) {
    // Diana negra: círculo externo + 4 brazos + punto central — tamaño 48dp fijo centrado
    Canvas(modifier = modifier) {
        val cx   = size.width  / 2f
        val cy   = size.height / 2f
        val r    = 18.dp.toPx()   // radio del círculo externo
        val arm  = 10.dp.toPx()   // largo de cada brazo
        val s    = 2.dp.toPx()
        val col  = Color(0xDD000000)
        val stroke = Stroke(s)

        // Círculo externo (sin relleno)
        drawCircle(col, r, Offset(cx, cy), style = stroke)

        // 4 brazos saliendo del círculo
        drawLine(col, Offset(cx, cy - r),       Offset(cx, cy - r - arm), s)
        drawLine(col, Offset(cx, cy + r),       Offset(cx, cy + r + arm), s)
        drawLine(col, Offset(cx - r, cy),       Offset(cx - r - arm, cy), s)
        drawLine(col, Offset(cx + r, cy),       Offset(cx + r + arm, cy), s)

        // Punto central
        drawCircle(col, 3.dp.toPx(), Offset(cx, cy))
    }
}

// ── Overlays osmdroid ─────────────────────────────────────────────────────────

private const val TAG_CAPTURA    = "captura"
private const val TAG_BORRADOR   = "borrador"
private const val TAG_GUARDADA   = "guardada"
private const val TAG_AREA_LBL   = "guardada_a"
private const val TAG_NAVEGACION = "navegacion"

// P4: solo borra los overlays de BORRADOR, nunca las entidades guardadas
private fun dibujarBorrador(mapView: MapView, state: MapUiState) {
    mapView.overlays.removeAll { it is Marker && (it as Marker).id == TAG_CAPTURA }
    mapView.overlays.removeAll { it is Polyline && it.title == TAG_BORRADOR }
    mapView.overlays.removeAll { it is Polygon  && it.title == TAG_BORRADOR }

    val puntos = state.puntosCapturados
    if (puntos.isEmpty()) { mapView.invalidate(); return }

    // Marcadores profesionales para puntos en curso
    puntos.forEachIndexed { i, p ->
        Marker(mapView).apply {
            id          = TAG_CAPTURA
            position    = GeoPoint(p.latitud, p.longitud)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            icon        = crearPuntoDot(mapView.context)
            infoWindow  = null
            mapView.overlays.add(this)
        }
    }

    val geos = puntos.map { GeoPoint(it.latitud, it.longitud) }

    when (state.modoCaptura) {
        ModoCaptura.LINEA, ModoCaptura.POLIGONO -> {
            val cerrado = state.modoCaptura == ModoCaptura.POLIGONO && geos.size >= 2
            Polyline(mapView).apply {
                title            = TAG_BORRADOR
                infoWindow       = null
                setPoints(if (cerrado) geos + geos.first() else geos)
                outlinePaint.color       = if (state.modoCaptura == ModoCaptura.POLIGONO)
                    AColor.parseColor("#2E7D32") else AColor.parseColor("#1565C0")
                outlinePaint.strokeWidth = 3f
                outlinePaint.pathEffect  = android.graphics.DashPathEffect(floatArrayOf(12f, 6f), 0f)
                mapView.overlays.add(this)
            }
            if (state.modoCaptura == ModoCaptura.POLIGONO && geos.size >= 3) {
                Polygon(mapView).apply {
                    title                  = TAG_BORRADOR
                    infoWindow             = null
                    points                 = geos + geos.first()
                    fillPaint.color        = AColor.argb(50, 255, 193, 7)
                    outlinePaint.color     = AColor.TRANSPARENT
                    mapView.overlays.add(this)
                }
            }
        }
        else -> Unit
    }
    mapView.invalidate()
}

// Pares (Predio?, wkt) — Predio puede ser null para entidades de sesión sin área
private fun dibujarEntidadesGuardadas(
    mapView      : MapView,
    entidades    : List<Pair<com.act.geomapper.domain.models.Predio?, String>>,
    conRelleno   : Boolean,
    areaUnit     : com.act.geomapper.data.settings.AreaUnit,
    distanceUnit : com.act.geomapper.data.settings.DistanceUnit = com.act.geomapper.data.settings.DistanceUnit.METERS
) {
    // Snapshot primero para evitar ConcurrentModificationException con osmdroid
    mapView.overlays.toList().forEach { overlay ->
        when {
            overlay is Polyline && overlay.title == TAG_GUARDADA -> mapView.overlays.remove(overlay)
            overlay is Polygon  && overlay.title == TAG_GUARDADA -> mapView.overlays.remove(overlay)
            overlay is Marker   && overlay.id == TAG_GUARDADA    -> mapView.overlays.remove(overlay)
            overlay is Marker   && overlay.id == TAG_AREA_LBL    -> mapView.overlays.remove(overlay)
        }
    }

    val reader = WKTReader()
    entidades.forEach { (predio, wkt) ->
        runCatching {
            val geom = reader.read(wkt)
            when (geom.geometryType) {
                "Point" -> {
                    Marker(mapView).apply {
                        id         = TAG_GUARDADA
                        position   = GeoPoint(geom.coordinate.y, geom.coordinate.x)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon       = crearPuntoDot(mapView.context, AColor.parseColor("#1A1A1A"), 16)
                        infoWindow = null
                        mapView.overlays.add(this)
                    }
                }
                "LineString" -> {
                    Polyline(mapView).apply {
                        title            = TAG_GUARDADA
                        infoWindow       = null
                        setPoints(geom.coordinates.map { GeoPoint(it.y, it.x) })
                        outlinePaint.color       = AColor.parseColor("#1565C0")
                        outlinePaint.strokeWidth = 3f
                        mapView.overlays.add(this)
                    }
                    // Etiqueta de longitud en el punto medio de la línea
                    val longM = predio?.perimetro ?: 0.0
                    if (longM > 0) {
                        val textoLong = "%.2f".format(
                            when (distanceUnit) {
                                com.act.geomapper.data.settings.DistanceUnit.KILOMETERS -> longM / 1000.0
                                else -> longM
                            }
                        ) + when (distanceUnit) {
                            com.act.geomapper.data.settings.DistanceUnit.KILOMETERS -> " km"
                            else -> " m"
                        }
                        val coords = geom.coordinates
                        val mid    = coords[coords.size / 2]
                        Marker(mapView).apply {
                            id         = TAG_GUARDADA
                            position   = GeoPoint(mid.y, mid.x)
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            icon       = crearEtiquetaTexto(mapView.context, textoLong)
                            infoWindow = null
                            mapView.overlays.add(this)
                        }
                    }
                }
                else -> {
                    val coords   = geom.coordinates.map { GeoPoint(it.y, it.x) }
                    val centroid = geom.centroid.coordinate

                    Polygon(mapView).apply {
                        title                    = TAG_GUARDADA
                        infoWindow               = null
                        points                   = coords
                        fillPaint.color          = if (conRelleno) AColor.argb(70, 255, 193, 7) else AColor.TRANSPARENT
                        outlinePaint.color       = AColor.parseColor("#E65100")
                        outlinePaint.strokeWidth = 3f
                        mapView.overlays.add(this)
                    }

                    // ── Etiqueta de área (zoom ≥ 13) ─────────────────────────
                    val areaHa = predio?.area ?: 0.0
                    if (areaHa > 0) {
                        val textoArea = "%.2f".format(
                            when (areaUnit) {
                                com.act.geomapper.data.settings.AreaUnit.SQUARE_METERS     -> areaHa * 10_000
                                com.act.geomapper.data.settings.AreaUnit.SQUARE_KILOMETERS -> areaHa / 100
                                com.act.geomapper.data.settings.AreaUnit.HECTARES          -> areaHa
                            }
                        ) + when (areaUnit) {
                            com.act.geomapper.data.settings.AreaUnit.SQUARE_METERS     -> " m²"
                            com.act.geomapper.data.settings.AreaUnit.SQUARE_KILOMETERS -> " km²"
                            com.act.geomapper.data.settings.AreaUnit.HECTARES          -> " ha"
                        }
                        MinZoomMarker(mapView, 13.0).apply {
                            id       = TAG_AREA_LBL
                            position = GeoPoint(centroid.y, centroid.x)
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            icon     = crearEtiquetaTexto(mapView.context, textoArea)
                            mapView.overlays.add(this)
                        }
                    }

                }
            }
        }
    }
    mapView.invalidate()
    mapView.postInvalidate()   // fuerza redibujado aunque el hilo de render esté ocupado
}

/** Etiqueta de área centrada en el polígono — texto blanco sobre fondo oscuro semitransparente */
private fun crearEtiquetaTexto(context: android.content.Context, texto: String): android.graphics.drawable.BitmapDrawable {
    val density = context.resources.displayMetrics.density
    val textPx  = 13f * density          // 13sp en píxeles — legible sin ser intrusivo
    val padH    = 10f * density
    val padV    = 5f  * density

    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        textSize  = textPx
        color     = android.graphics.Color.WHITE
        typeface  = android.graphics.Typeface.DEFAULT_BOLD
        textAlign = android.graphics.Paint.Align.CENTER
    }
    val tw = paint.measureText(texto)
    val w  = (tw + padH * 2).toInt().coerceAtLeast(1)
    val h  = (textPx + padV * 2).toInt().coerceAtLeast(1)

    val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
    val c   = android.graphics.Canvas(bmp)

    // Fondo redondeado oscuro semitransparente
    val bgP = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(160, 20, 20, 20)
    }
    c.drawRoundRect(0f, 0f, w.toFloat(), h.toFloat(), h / 2f, h / 2f, bgP)

    // Texto centrado verticalmente
    val baseline = padV + textPx * 0.85f   // 0.85 = compensar descenders
    c.drawText(texto, w / 2f, baseline, paint)

    return android.graphics.drawable.BitmapDrawable(context.resources, bmp)
}

/** Marker que solo se dibuja cuando el zoom del mapa es ≥ minZoom */
private class MinZoomMarker(mapView: MapView, private val minZoom: Double) : Marker(mapView) {
    init { infoWindow = null }
    override fun draw(c: android.graphics.Canvas, osmv: MapView, shadow: Boolean) {
        if (osmv.zoomLevelDouble >= minZoom) super.draw(c, osmv, shadow)
    }
}

// ── Sub-componentes de UI ─────────────────────────────────────────────────────

@Composable
private fun BarraCaptura(
    modo             : ModoCaptura,
    numPuntos        : Int,
    onAgregarPuntoGps: () -> Unit,
    onUndo           : () -> Unit,
    onFinalizar      : () -> Unit,
    onCancelar       : () -> Unit
) {
    val s      = com.act.geomapper.ui.theme.LocalStrings.current
    val label  = when (modo) {
        ModoCaptura.PUNTO    -> s.puntoGps
        ModoCaptura.LINEA    -> "${s.linea}  ·  $numPuntos pts"
        ModoCaptura.POLIGONO -> "${s.poligono}  ·  $numPuntos pts"
        ModoCaptura.NINGUNO  -> ""
    }
    val ptsMin = if (modo == ModoCaptura.LINEA) 2 else 3

    GlassBox(shape = RoundedCornerShape(20.dp)) {
        Row(
            modifier              = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(onClick = onCancelar, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Close, s.cancelar, tint = Color(0xFFEF5350), modifier = Modifier.size(18.dp))
            }

            Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))

            if (numPuntos > 0) {
                IconButton(onClick = onUndo, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Undo, s.deshacer, tint = Color(0xFFFFC107), modifier = Modifier.size(18.dp))
                }
            }

            Button(
                onClick        = onAgregarPuntoGps,
                colors         = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D47A1)),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier       = Modifier.height(34.dp)
            ) {
                Icon(Icons.Default.GpsFixed, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(s.gps, fontSize = 12.sp)
            }

            if (modo != ModoCaptura.PUNTO && numPuntos >= ptsMin) {
                Button(
                    onClick        = onFinalizar,
                    colors         = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier       = Modifier.height(34.dp)
                ) {
                    Icon(Icons.Default.Check, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(s.finalizar, fontSize = 12.sp)
                }
            }
        }
    }
}

// ── Barra de edición de geometría ────────────────────────────────────────────

@Composable
private fun BarraEdicion(
    nombreEntidad: String,
    onGuardar    : () -> Unit,
    onCancelar   : () -> Unit,
    modifier     : Modifier = Modifier
) {
    GlassBox(shape = RoundedCornerShape(20.dp), modifier = modifier) {
        Row(
            modifier              = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.EditLocation, null, tint = Color(0xFFFF9800), modifier = Modifier.size(16.dp))
            Text(
                "Editando: $nombreEntidad",
                color    = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            // Cancelar
            IconButton(onClick = onCancelar, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Default.Close, "Cancelar edición", tint = Color(0xFFEF5350), modifier = Modifier.size(16.dp))
            }
            // Guardar
            Button(
                onClick        = onGuardar,
                colors         = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20)),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier       = Modifier.height(34.dp)
            ) {
                Icon(Icons.Default.Check, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Guardar", fontSize = 12.sp)
            }
        }
    }
}

// Reconstruye la geometría JTS a partir de los vértices editados
// ── Preview de edición en tiempo real ────────────────────────────────────────

private const val TAG_EDIT_PREVIEW = "edit_preview"

/** Dibuja la forma actualizada mientras el usuario arrastra vértices. */
private fun dibujarPreviewEdicion(
    mapView : MapView,
    vertices: List<org.osmdroid.util.GeoPoint>,
    tipo    : String
) {
    // Eliminar preview anterior
    mapView.overlays.removeAll { it is org.osmdroid.views.overlay.Polyline && it.title == TAG_EDIT_PREVIEW }
    mapView.overlays.removeAll { it is org.osmdroid.views.overlay.Polygon  && it.title == TAG_EDIT_PREVIEW }

    if (vertices.size < 2) { mapView.invalidate(); return }

    when (tipo) {
        "LineString" -> {
            org.osmdroid.views.overlay.Polyline(mapView).apply {
                title            = TAG_EDIT_PREVIEW
                infoWindow       = null
                setPoints(vertices)
                outlinePaint.color       = android.graphics.Color.parseColor("#FF9800")
                outlinePaint.strokeWidth = 4f
                outlinePaint.pathEffect  = android.graphics.DashPathEffect(floatArrayOf(14f, 7f), 0f)
                mapView.overlays.add(this)
            }
        }
        else -> {   // Polygon
            if (vertices.size >= 3) {
                org.osmdroid.views.overlay.Polygon(mapView).apply {
                    title                    = TAG_EDIT_PREVIEW
                    infoWindow               = null
                    points                   = vertices + vertices.first()
                    fillPaint.color          = android.graphics.Color.argb(45, 255, 152, 0)
                    outlinePaint.color       = android.graphics.Color.parseColor("#FF9800")
                    outlinePaint.strokeWidth = 4f
                    outlinePaint.pathEffect  = android.graphics.DashPathEffect(floatArrayOf(14f, 7f), 0f)
                    mapView.overlays.add(this)
                }
            }
        }
    }
    mapView.invalidate()
}

/** Limpia el preview al cancelar o guardar la edición. */
private fun limpiarPreviewEdicion(mapView: MapView) {
    mapView.overlays.removeAll { it is org.osmdroid.views.overlay.Polyline && it.title == TAG_EDIT_PREVIEW }
    mapView.overlays.removeAll { it is org.osmdroid.views.overlay.Polygon  && it.title == TAG_EDIT_PREVIEW }
    mapView.invalidate()
}

private fun reconstruirGeometria(
    factory : org.locationtech.jts.geom.GeometryFactory,
    tipo    : String,
    vertices: List<org.osmdroid.util.GeoPoint>
): org.locationtech.jts.geom.Geometry? {
    if (vertices.isEmpty()) return null
    return runCatching {
        val coords = vertices.map { org.locationtech.jts.geom.Coordinate(it.longitude, it.latitude) }.toTypedArray()
        when (tipo) {
            "Point"      -> factory.createPoint(coords[0])
            "LineString" -> factory.createLineString(coords)
            else         -> {
                // Polígono: cerrar el anillo si no está cerrado
                val ring = if (coords.first() != coords.last()) coords + coords.first() else coords
                factory.createPolygon(ring)
            }
        }
    }.getOrNull()
}

private fun dibujarNavegacion(mapView: MapView, origen: com.act.geomapper.domain.models.PuntoGps?, destino: com.act.geomapper.domain.models.PuntoGps?) {
    mapView.overlays.removeAll { it is Polyline && it.title == TAG_NAVEGACION }
    if (origen == null || destino == null) { mapView.invalidate(); return }

    Polyline(mapView).apply {
        title            = TAG_NAVEGACION
        infoWindow       = null
        setPoints(listOf(
            GeoPoint(origen.latitud,  origen.longitud),
            GeoPoint(destino.latitud, destino.longitud)
        ))
        outlinePaint.color       = AColor.parseColor("#2196F3")
        outlinePaint.strokeWidth = 5f
        outlinePaint.pathEffect  = android.graphics.DashPathEffect(floatArrayOf(24f, 12f), 0f)
        mapView.overlays.add(0, this)   // detrás de todo lo demás
    }
    mapView.invalidate()
}

@Composable
private fun NavegacionChip(distancia: String, onDetener: () -> Unit, modifier: Modifier = Modifier) {
    GlassBox(shape = RoundedCornerShape(12.dp), modifier = modifier) {
        Row(
            modifier              = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(Icons.Default.Navigation, null, tint = Color(0xFF2196F3), modifier = Modifier.size(14.dp))
            Text(distancia, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            IconButton(onClick = onDetener, modifier = Modifier.size(18.dp)) {
                Icon(Icons.Default.Close, null, tint = Color.White.copy(0.4f), modifier = Modifier.size(10.dp))
            }
        }
    }
}

@Composable
private fun AreaChip(texto: String, onDismiss: () -> Unit) {
    GlassBox(shape = RoundedCornerShape(12.dp)) {
        Row(
            modifier              = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(Icons.Default.SquareFoot, null, tint = Color(0xFF81C784), modifier = Modifier.size(14.dp))
            Text(texto, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            IconButton(onClick = onDismiss, modifier = Modifier.size(18.dp)) {
                Icon(Icons.Default.Close, null, tint = Color.White.copy(0.4f), modifier = Modifier.size(10.dp))
            }
        }
    }
}
