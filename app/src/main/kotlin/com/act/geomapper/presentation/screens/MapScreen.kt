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
import com.act.geomapper.data.geopdf.GeoPdfData
import com.act.geomapper.data.gnss.GnssFix
import com.act.geomapper.presentation.overlay.DirectionOverlay
import com.act.geomapper.presentation.overlay.GeoPdfOverlay
import com.act.geomapper.presentation.overlay.VertexEditOverlay
import com.act.geomapper.presentation.overlay.crearPuntoDot
import com.act.geomapper.ui.theme.rememberWindowInfo
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.layout.navigationBarsPadding
import kotlinx.coroutines.launch

@Composable
fun MapScreen(
    viewModel       : MapViewModel,
    settings        : AppSettings                 = AppSettings(),
    basemapActual   : Basemap                     = Basemap.OSM,
    predios         : List<com.act.geomapper.domain.models.Predio> = emptyList(),
    ocultos         : Set<Long>                   = emptySet(),
    redrawVersion   : Int                         = 0,
    geoPdfData      : GeoPdfData?                 = null,
    geoPdfVisible   : Boolean                     = true,
    gnssFixMerged   : GnssFix?                    = null,
    btConectado     : Boolean                     = false,
    wifiConectado   : Boolean                     = false,
    onGuardarEdicion        : (Long, org.locationtech.jts.geom.Geometry) -> Unit = { _, _ -> },
    descargasOffline        : List<com.act.geomapper.data.offline.DescargaOffline> = emptyList(),
    dibujandoAreaDescarga   : Boolean                                            = false,
    onCancelarAreaDescarga  : () -> Unit                                         = {},
    onDescargaCompletada    : (com.act.geomapper.data.offline.DescargaOffline) -> Unit = {},
    modifier                : Modifier                                           = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val azimut  = rememberAzimut()
    val win     = rememberWindowInfo()
    val s       = com.act.geomapper.ui.theme.LocalStrings.current

    var progresoDescarga      by remember { mutableStateOf(-1) }
    var descargaBbox          by remember { mutableStateOf<org.osmdroid.util.BoundingBox?>(null) }
    var mostrarDialogoDescarga by remember { mutableStateOf(false) }
    val mainHandler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }
    val scope       = rememberCoroutineScope()

    // Rastrea los TilesOverlay de descargas offline para poder eliminarlos al actualizar
    val offlineTileData = remember {
        mutableListOf<Pair<org.osmdroid.tileprovider.MapTileProviderBase,
                          org.osmdroid.views.overlay.TilesOverlay>>()
    }

    // Vértices del polígono que el usuario dibuja para definir el área de descarga
    val verticesDescarga = remember { androidx.compose.runtime.snapshots.SnapshotStateList<GeoPoint>() }
    // Ref mutable para leer el estado desde dentro del overlay (que se crea una sola vez)
    val dibujandoRef = androidx.compose.runtime.rememberUpdatedState(dibujandoAreaDescarga)

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
                    val centro = mv.mapCenter as GeoPoint
                    if (dibujandoRef.value) {
                        // Modo dibujo: agregar vértice al polígono de descarga
                        verticesDescarga.add(centro)
                    } else {
                        // Modo normal: capturar entidad
                        viewModel.capturarPuntoManual(PuntoGps(centro.latitude, centro.longitude))
                    }
                    return true
                }
            })
        }
    }

    // Limpiar vértices y estado al salir del modo dibujo
    LaunchedEffect(dibujandoAreaDescarga) {
        if (!dibujandoAreaDescarga) {
            verticesDescarga.clear()
            mostrarDialogoDescarga = false
            progresoDescarga       = -1
            mapView.overlays.removeAll { it is Polyline && it.title == TAG_DESCARGA_BORRADOR }
            mapView.overlays.removeAll { it is Marker  && it.id   == TAG_DESCARGA_BORRADOR }
            mapView.invalidate()
        }
    }

    // Dibujar polígono de área en progreso — igual que dibujarBorrador en modo POLIGONO
    LaunchedEffect(verticesDescarga.size) {
        mapView.overlays.removeAll { it is Polyline && it.title == TAG_DESCARGA_BORRADOR }
        mapView.overlays.removeAll { it is Marker  && it.id   == TAG_DESCARGA_BORRADOR }
        val pts = verticesDescarga.toList()
        if (pts.size >= 2) {
            Polyline(mapView).apply {
                title                    = TAG_DESCARGA_BORRADOR
                infoWindow               = null
                setPoints(if (pts.size >= 3) pts + pts.first() else pts)
                outlinePaint.color       = AColor.WHITE
                outlinePaint.strokeWidth = 3f
                outlinePaint.pathEffect  = android.graphics.DashPathEffect(floatArrayOf(12f, 6f), 0f)
                mapView.overlays.add(this)
            }
        }
        pts.forEach { gp ->
            Marker(mapView).apply {
                id         = TAG_DESCARGA_BORRADOR
                position   = gp
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon       = crearPuntoDot(mapView.context)
                infoWindow = null
                mapView.overlays.add(this)
            }
        }
        mapView.invalidate()
    }

    LaunchedEffect(basemapActual) {
        mapView.setTileSource(basemapActual.toTileSource())
        mapView.invalidate()
    }

    LaunchedEffect(geoPdfData, geoPdfVisible) {
        mapView.overlays.removeAll { it is GeoPdfOverlay }
        if (geoPdfData != null && geoPdfVisible) mapView.overlays.add(0, GeoPdfOverlay(geoPdfData))
        mapView.invalidate()
    }

    // Al importar un ráster (GeoPDF / GeoTIFF), centrar y ajustar el zoom a su extensión
    // para que sea visible aunque esté lejos de la vista actual. Clave: geoPdfData (no la
    // visibilidad) — solo se dispara cuando llega un ráster nuevo, no al mostrar/ocultar.
    LaunchedEffect(geoPdfData) {
        geoPdfData?.let { d ->
            runCatching {
                val bbox = org.osmdroid.util.BoundingBox(d.norte, d.este, d.sur, d.oeste)
                mapView.zoomToBoundingBox(bbox, true, 48)
            }.onFailure {
                mapView.controller.animateTo(GeoPoint((d.norte + d.sur) / 2, (d.este + d.oeste) / 2))
            }
        }
    }

    LaunchedEffect(descargasOffline) {
        // Eliminar overlays de teselas anteriores
        offlineTileData.forEach { (provider, overlay) ->
            mapView.overlays.remove(overlay)
            provider.detach()
        }
        offlineTileData.clear()
        mapView.overlays.removeAll { it is Polyline && it.title?.startsWith("offline_bbox_") == true }

        descargasOffline.filter { it.visible }.forEach { d ->
            runCatching {
                val tileSource = basemapDesdeEtiqueta(d.basemapEtiqueta).toTileSource()
                val provider   = org.osmdroid.tileprovider.MapTileProviderBasic(context, tileSource)
                val dBbox      = org.osmdroid.util.BoundingBox(d.norte, d.este, d.sur, d.oeste)
                val tilesOverlay = BboxTilesOverlay(provider, context, dBbox)
                mapView.overlays.add(0, tilesOverlay)
                offlineTileData.add(provider to tilesOverlay)
            }
            runCatching {
                Polyline(mapView).apply {
                    title = "offline_bbox_${d.id}"
                    infoWindow = null
                    setPoints(listOf(
                        GeoPoint(d.norte, d.oeste),
                        GeoPoint(d.norte, d.este),
                        GeoPoint(d.sur,   d.este),
                        GeoPoint(d.sur,   d.oeste),
                        GeoPoint(d.norte, d.oeste)
                    ))
                    outlinePaint.color       = AColor.argb(230, 255, 255, 255)
                    outlinePaint.strokeWidth = 3f
                    outlinePaint.pathEffect  = android.graphics.DashPathEffect(floatArrayOf(14f, 7f), 0f)
                    mapView.overlays.add(this)
                }
            }
        }
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
        // Las entidades usan add(0,...) y empujan el TilesOverlay a índices altos (encima).
        // Lo reposicionamos al fondo (índice 0) para que quede debajo de las entidades.
        mapView.overlays.filterIsInstance<org.osmdroid.views.overlay.TilesOverlay>().forEach { to ->
            mapView.overlays.remove(to)
            mapView.overlays.add(0, to)
        }
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

    // Posición de alta precisión: GNSS externo RTK si conectado, GPS interno si no
    val externalGnssOk = (btConectado || wifiConectado) && gnssFixMerged != null
    val origenNavegacion = if (externalGnssOk && gnssFixMerged != null)
        com.act.geomapper.domain.models.PuntoGps(gnssFixMerged.latitud, gnssFixMerged.longitud)
    else uiState.estadoGps.puntoActual

    // Overlay de navegación: línea recta posición→destino, usa RTK si disponible
    LaunchedEffect(origenNavegacion, uiState.navegacionDestino) {
        dibujarNavegacion(mapView, origenNavegacion, uiState.navegacionDestino)
    }

    // DirectionOverlay: punto azul con flecha de heading
    val dirOverlay = remember { DirectionOverlay() }
    LaunchedEffect(Unit) {
        if (!mapView.overlays.contains(dirOverlay))
            mapView.overlays.add(dirOverlay)
    }
    // Actualizar posición y azimut — usa posición RTK cuando está disponible
    LaunchedEffect(origenNavegacion, azimut) {
        origenNavegacion?.let {
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
            Icon(Icons.Default.MyLocation, s.miUbicacion, tint = Color.White, modifier = Modifier.size(win.iconSize))
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

        // ── Navegación / Replanteo ────────────────────────────────────────────
        uiState.navegacionDestino?.let { destino ->

            val posLat = origenNavegacion?.latitud
            val posLon = origenNavegacion?.longitud

            // Distancia y rumbo al destino
            val navResult = if (posLat != null && posLon != null) {
                FloatArray(2).also { r ->
                    android.location.Location.distanceBetween(posLat, posLon, destino.latitud, destino.longitud, r)
                }
            } else null
            val distMetros  = navResult?.get(0)
            val rumboPunto  = navResult?.get(1) ?: 0f

            // Umbral de llegada: 3 cm con GNSS externo RTK, 3 m con GPS interno
            val umbral  = if (externalGnssOk) 0.03f else 3.0f
            val llegada = distMetros != null && distMetros < umbral

            // Sonido — una sola vez por transición false→true
            var sonoAntes by remember { mutableStateOf(false) }
            LaunchedEffect(llegada) {
                if (llegada && !sonoAntes) {
                    runCatching {
                        ToneGenerator(AudioManager.STREAM_MUSIC, 100)
                            .startTone(ToneGenerator.TONE_PROP_BEEP, 800)
                    }
                }
                sonoAntes = llegada
            }

            // Chip de distancia — centrado sobre la barra de coordenadas, sin solapar captura
            val distanciaTexto = when {
                llegada             -> "¡Llegó!"
                distMetros == null  -> "—"
                else                -> distMetros.toDouble().toDisplayDistance(settings.distanceUnit)
            }
            // Sube sobre la barra de captura cuando está activa, si no queda sobre coords
            val chipBot = if (uiState.modoCaptura != ModoCaptura.NINGUNO)
                win.captureBarBot + 60.dp else win.coordBarBot + 40.dp
            NavegacionChip(
                distancia = distanciaTexto,
                onDetener = viewModel::detenerNavegacion,
                modifier  = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = chipBot)
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
                nombreEntidad = predio.nombre.ifBlank { s.sinNombre },
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
                            Text(s.descartar, color = Color(0xFFEF9A9A))
                        }
                        TextButton(onClick = viewModel::confirmarRecuperacion) {
                            Text(s.continuarBtn, color = Color(0xFF81C784))
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
                action = { TextButton(onClick = viewModel::limpiarError) { Text(s.ok) } }
            ) { Text(err, fontSize = 13.sp) }
        }

        // ── Panel de dibujo del área de descarga ─────────────────────────
        if (dibujandoAreaDescarga && !mostrarDialogoDescarga) {
            AreaDescargaPanel(
                numVertices = verticesDescarga.size,
                onDeshacer  = { if (verticesDescarga.isNotEmpty()) verticesDescarga.removeAt(verticesDescarga.lastIndex) },
                onCancelar  = {
                    verticesDescarga.clear()
                    onCancelarAreaDescarga()
                },
                onConfirmar = {
                    val pts = verticesDescarga.toList()
                    descargaBbox = org.osmdroid.util.BoundingBox(
                        pts.maxOf { it.latitude },  pts.maxOf { it.longitude },
                        pts.minOf { it.latitude },  pts.minOf { it.longitude }
                    )
                    mostrarDialogoDescarga = true
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = win.coordBarBot + 48.dp, start = 16.dp, end = 16.dp)
            )
        }

        // ── Diálogo de descarga de mapa offline ──────────────────────────
        val bbox = descargaBbox
        if (mostrarDialogoDescarga && bbox != null) {
            com.act.geomapper.presentation.components.DescargaMapaDialog(
                bbox             = bbox,
                basemapEtiqueta  = basemapActual.etiquetaLocalizada(s),
                progresoDescarga = progresoDescarga,
                onDismiss = {
                    if (progresoDescarga !in 0..100) {
                        progresoDescarga = -1
                        mostrarDialogoDescarga = false
                        onCancelarAreaDescarga()
                    }
                },
                onIniciarDescarga = { zoomMaxArg ->
                    progresoDescarga = 0
                    val polygonPts = verticesDescarga.toList()
                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        downloadTilesForPolygon(
                            context    = context,
                            tileSource = basemapActual.toTileSource(),
                            polygonPts = polygonPts,
                            bbox       = bbox,
                            zoomMin    = 14,
                            zoomMax    = zoomMaxArg,
                            onProgress = { pct -> mainHandler.post { progresoDescarga = pct } },
                            onComplete = { total ->
                                mainHandler.post {
                                    progresoDescarga = 101
                                    onDescargaCompletada(
                                        com.act.geomapper.data.offline.DescargaOffline(
                                            id              = System.currentTimeMillis(),
                                            nombre          = "${basemapActual.etiquetaLocalizada(s)} z14–z$zoomMaxArg",
                                            norte           = bbox.latNorth,
                                            sur             = bbox.latSouth,
                                            este            = bbox.lonEast,
                                            oeste           = bbox.lonWest,
                                            zoomMax         = zoomMaxArg,
                                            tiles           = total,
                                            basemapEtiqueta = basemapActual.etiqueta
                                        )
                                    )
                                }
                            },
                            onError = { mainHandler.post { progresoDescarga = -2 } }
                        )
                    }
                }
            )
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

private const val TAG_CAPTURA          = "captura"
private const val TAG_BORRADOR         = "borrador"
private const val TAG_GUARDADA         = "guardada"
private const val TAG_AREA_LBL         = "guardada_a"
private const val TAG_NAVEGACION       = "navegacion"
private const val TAG_DESCARGA_BORRADOR = "descarga_borrador"

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

    // Z-order: polígonos (base) → líneas → puntos (encima).
    // add(0,x) inserta en el fondo; la última pasada queda arriba.
    val reader = WKTReader()

    // Pasada 1: Polígonos (capa inferior)
    entidades.forEach { (predio, wkt) ->
        runCatching {
            val geom = reader.read(wkt)
            if (geom.geometryType == "Point" || geom.geometryType == "LineString") return@runCatching
            val coords   = geom.coordinates.map { GeoPoint(it.y, it.x) }
            val centroid = geom.centroid.coordinate
            Polygon(mapView).apply {
                title                    = TAG_GUARDADA
                infoWindow               = null
                points                   = coords
                fillPaint.color          = if (conRelleno) AColor.argb(70, 255, 193, 7) else AColor.TRANSPARENT
                outlinePaint.color       = AColor.parseColor("#E65100")
                outlinePaint.strokeWidth = 3f
                mapView.overlays.add(0, this)
            }
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
                    mapView.overlays.add(0, this)
                }
            }
        }
    }

    // Pasada 2: Líneas (capa intermedia)
    entidades.forEach { (predio, wkt) ->
        runCatching {
            val geom = reader.read(wkt)
            if (geom.geometryType != "LineString") return@runCatching
            Polyline(mapView).apply {
                title                    = TAG_GUARDADA
                infoWindow               = null
                setPoints(geom.coordinates.map { GeoPoint(it.y, it.x) })
                outlinePaint.color       = AColor.parseColor("#1565C0")
                outlinePaint.strokeWidth = 3f
                mapView.overlays.add(0, this)
            }
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
                    mapView.overlays.add(0, this)
                }
            }
        }
    }

    // Pasada 3: Puntos (capa superior)
    entidades.forEach { (_, wkt) ->
        runCatching {
            val geom = reader.read(wkt)
            if (geom.geometryType != "Point") return@runCatching
            Marker(mapView).apply {
                id         = TAG_GUARDADA
                position   = GeoPoint(geom.coordinate.y, geom.coordinate.x)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon       = crearPuntoDot(mapView.context, AColor.parseColor("#1A1A1A"), 16)
                infoWindow = null
                mapView.overlays.add(0, this)
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
    val s = com.act.geomapper.ui.theme.LocalStrings.current
    GlassBox(shape = RoundedCornerShape(20.dp), modifier = modifier) {
        Row(
            modifier              = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.EditLocation, null, tint = Color(0xFFFF9800), modifier = Modifier.size(16.dp))
            Text(
                s.editando.format(nombreEntidad),
                color    = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            // Cancelar
            IconButton(onClick = onCancelar, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Default.Close, s.cancelarEdicion, tint = Color(0xFFEF5350), modifier = Modifier.size(16.dp))
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
                Text(s.guardar, fontSize = 12.sp)
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
        mapView.overlays.add(this)
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
            IconButton(onClick = onDetener, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, null, tint = Color(0xFFEF5350), modifier = Modifier.size(16.dp))
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

@Composable
private fun AreaDescargaPanel(
    numVertices : Int,
    onDeshacer  : () -> Unit,
    onCancelar  : () -> Unit,
    onConfirmar : () -> Unit,
    modifier    : Modifier = Modifier
) {
    val s = com.act.geomapper.ui.theme.LocalStrings.current
    GlassBox(shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp), modifier = modifier) {
        Column(
            modifier            = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CloudDownload, null,
                    tint = Color(0xFF81C784), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(s.definirAreaDescarga.removeSuffix("…"),
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    modifier = Modifier.weight(1f))
            }
            Text(
                if (numVertices < 3) s.tocaMapaVertices.format(numVertices)
                else s.poligonoListo.format(numVertices),
                color = Color.White.copy(0.65f), fontSize = 11.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Deshacer último vértice
                OutlinedButton(
                    onClick = onDeshacer,
                    enabled = numVertices > 0,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 6.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp, if (numVertices > 0) Color.White.copy(0.4f) else Color.White.copy(0.15f))
                ) {
                    Icon(Icons.Default.Undo, null,
                        tint = if (numVertices > 0) Color.White else Color.White.copy(0.3f),
                        modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(s.deshacer, color = if (numVertices > 0) Color.White else Color.White.copy(0.3f),
                        fontSize = 11.sp)
                }
                // Cancelar
                OutlinedButton(
                    onClick = onCancelar,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF5350).copy(0.6f))
                ) {
                    Text(s.cancelar, color = Color(0xFFEF5350), fontSize = 11.sp)
                }
                // Confirmar
                Button(
                    onClick  = onConfirmar,
                    enabled  = numVertices >= 3,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 6.dp),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor         = Color(0xFF1565C0),
                        disabledContainerColor = Color(0xFF1565C0).copy(0.3f)
                    )
                ) {
                    Icon(Icons.Default.Check, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(s.confirmar, fontSize = 11.sp)
                }
            }
        }
    }
}

// ── Descarga de teselas filtrada por polígono ─────────────────────────────────

private fun downloadTilesForPolygon(
    context    : android.content.Context,
    tileSource : org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase,
    polygonPts : List<GeoPoint>,
    bbox       : org.osmdroid.util.BoundingBox,
    zoomMin    : Int,
    zoomMax    : Int,
    onProgress : (Int) -> Unit,
    onComplete : (totalDownloaded: Int) -> Unit,
    onError    : () -> Unit
) {
    if (polygonPts.size < 3) { onError(); return }
    val tileCache = org.osmdroid.tileprovider.modules.SqlTileWriter()

    val factory  = org.locationtech.jts.geom.GeometryFactory()
    val ring     = (polygonPts + polygonPts.first())
        .map { org.locationtech.jts.geom.Coordinate(it.longitude, it.latitude) }
        .toTypedArray()
    val prepared = org.locationtech.jts.geom.prep.PreparedGeometryFactory
        .prepare(factory.createPolygon(ring))

    val tiles = mutableListOf<Triple<Int, Int, Int>>()
    for (z in zoomMin..zoomMax) {
        val n    = 1 shl z
        val xMin = lonToTileX(bbox.lonWest,  n)
        val xMax = lonToTileX(bbox.lonEast,  n)
        val yMin = latToTileY(bbox.latNorth, n)
        val yMax = latToTileY(bbox.latSouth, n)
        for (x in xMin..xMax) for (y in yMin..yMax) {
            if (prepared.intersects(factory.toGeometry(tileToEnvelope(z, x, y))))
                tiles.add(Triple(z, x, y))
        }
    }

    val total = tiles.size
    if (total == 0) { onComplete(0); return }

    var done       = 0
    var downloaded = 0
    for ((z, x, y) in tiles) {
        runCatching {
            val idx  = org.osmdroid.util.MapTileIndex.getTileIndex(z, x, y)
            val url  = tileSource.getTileURLString(idx)
            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            conn.setRequestProperty("User-Agent", context.packageName)
            conn.connectTimeout = 15_000
            conn.readTimeout    = 15_000
            val bytes = conn.inputStream.use { it.readBytes() }
            conn.disconnect()
            tileCache.saveFile(
                tileSource,
                org.osmdroid.util.MapTileIndex.getTileIndex(z, x, y),
                java.io.ByteArrayInputStream(bytes),
                System.currentTimeMillis() + 30L * 24 * 3_600_000
            )
            downloaded++
        }
        done++
        onProgress((done * 100) / total)
    }
    if (downloaded > 0) onComplete(downloaded) else onError()
}

private fun lonToTileX(lon: Double, n: Int) =
    ((lon + 180.0) / 360.0 * n).toInt().coerceIn(0, n - 1)

private fun latToTileY(lat: Double, n: Int): Int {
    val r = Math.toRadians(lat.coerceIn(-85.0, 85.0))
    return ((1.0 - Math.log(Math.tan(r) + 1.0 / Math.cos(r)) / Math.PI) / 2.0 * n)
        .toInt().coerceIn(0, n - 1)
}

private fun tileToEnvelope(z: Int, x: Int, y: Int): org.locationtech.jts.geom.Envelope {
    val n    = 1 shl z
    val lonW = x.toDouble() / n * 360.0 - 180.0
    val lonE = (x + 1).toDouble() / n * 360.0 - 180.0
    val latN = Math.toDegrees(Math.atan(Math.sinh(Math.PI * (1 - 2.0 * y / n))))
    val latS = Math.toDegrees(Math.atan(Math.sinh(Math.PI * (1 - 2.0 * (y + 1) / n))))
    return org.locationtech.jts.geom.Envelope(lonW, lonE, latS, latN)
}

// Overlay de teselas recortado al bbox de la descarga — evita mostrar teselas fuera del área
private class BboxTilesOverlay(
    provider : org.osmdroid.tileprovider.MapTileProviderBase,
    context  : android.content.Context,
    private val bbox: org.osmdroid.util.BoundingBox
) : org.osmdroid.views.overlay.TilesOverlay(provider, context) {
    override fun draw(canvas: android.graphics.Canvas, osmv: MapView, shadow: Boolean) {
        if (shadow) return
        val proj = osmv.projection
        val pNW  = proj.toPixels(GeoPoint(bbox.latNorth, bbox.lonWest), null)
        val pSE  = proj.toPixels(GeoPoint(bbox.latSouth, bbox.lonEast), null)
        canvas.save()
        canvas.clipRect(
            minOf(pNW.x, pSE.x).toFloat(),
            minOf(pNW.y, pSE.y).toFloat(),
            maxOf(pNW.x, pSE.x).toFloat(),
            maxOf(pNW.y, pSE.y).toFloat()
        )
        super.draw(canvas, osmv, shadow)
        canvas.restore()
    }
}
