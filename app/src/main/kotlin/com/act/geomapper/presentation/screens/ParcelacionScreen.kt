package com.act.geomapper.presentation.screens

import android.content.Context
import android.graphics.*
import android.graphics.Color as AColor
import android.view.MotionEvent
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.act.geomapper.data.geometry.SegmentoInfo
import com.act.geomapper.domain.models.Predio
import com.act.geomapper.presentation.components.Basemap
import com.act.geomapper.presentation.components.NorthArrow
import com.act.geomapper.presentation.components.rememberAzimut
import com.act.geomapper.presentation.components.toTileSource
import com.act.geomapper.presentation.viewmodels.ModoParcelacion
import com.act.geomapper.presentation.viewmodels.ParcelacionViewModel
import com.act.geomapper.presentation.viewmodels.TipoSubdivision
import com.act.geomapper.ui.theme.GlassLightBox
import com.act.geomapper.ui.theme.LocalStrings
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import kotlin.math.pow
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay

// ── Colores de la imagen de referencia ───────────────────────────────────────
private val CyanBtn   = Color(0xFF00BCD4)
private val VerdeBtn  = Color(0xFF4CAF50)
private val NaranjaPoligono = AColor.parseColor("#FF6D00")
private val AmarilloRelleno = AColor.argb(100, 255, 255, 0)
private val RojoCorte       = AColor.parseColor("#F44336")
private val GrisVia         = AColor.argb(100, 120, 120, 120)

// ── Pantalla principal ────────────────────────────────────────────────────────

@Composable
fun ParcelacionScreen(
    vm            : ParcelacionViewModel,
    poligonos     : List<Predio>,
    proyectoId    : Long,
    basemapActual : Basemap = Basemap.OSM,
    predioInicial : Predio? = null,
    estadoGps     : com.act.geomapper.domain.models.EstadoGps = com.act.geomapper.domain.models.EstadoGps(),
    areaUnit      : com.act.geomapper.data.settings.AreaUnit     = com.act.geomapper.data.settings.AreaUnit.HECTARES,
    distanceUnit  : com.act.geomapper.data.settings.DistanceUnit = com.act.geomapper.data.settings.DistanceUnit.METERS,
    onBack        : () -> Unit
) {
    val state  by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val azimut  = rememberAzimut()

    var mostrarSelectorPoligono by remember { mutableStateOf(false) }
    var mostrarEditorParcelas  by remember { mutableStateOf(false) }
    var conRelleno              by remember { mutableStateOf(true) }
    var centroDiana             by remember { mutableStateOf(GeoPoint(4.6097, -74.0817)) }

    val s = LocalStrings.current
    val titulo = when (state.modo) {
        ModoParcelacion.CORTE             -> s.corteManual
        ModoParcelacion.VIA,
        ModoParcelacion.VIA_LINEA         -> s.trazarVia
        ModoParcelacion.SUBDIVISION       -> s.subdividirLote
        ModoParcelacion.SUBDIVISION_LINEA -> s.trazarDireccionCorte
        ModoParcelacion.IDLE              -> s.herramientasTerreno
    }

    // OSMDroid MapView
    val mapView = remember {
        MapView(context).apply {
            setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(17.0)
            controller.setCenter(GeoPoint(4.6097, -74.0817))
            overlays.add(RotationGestureOverlay(this).also { it.isEnabled = true })
            // Dibujo libre: toque + arrastre para trazar líneas irregulares
            overlays.add(object : org.osmdroid.views.overlay.Overlay() {
                private var lastX = Float.NaN
                private var lastY = Float.NaN
                private val MIN_PX = 12f   // distancia mínima en píxeles antes de añadir otro punto

                // Todos los modos usan diana + botón — el overlay de arrastre queda desactivado
                private fun enModoTrazo() = false

                override fun onTouchEvent(e: MotionEvent, mv: MapView): Boolean {
                    if (!enModoTrazo()) return false
                    when (e.action) {
                        MotionEvent.ACTION_DOWN -> {
                            lastX = e.x; lastY = e.y
                            val gp = mv.projection.fromPixels(e.x.toInt(), e.y.toInt()) as GeoPoint
                            vm.agregarPuntoLinea(gp.latitude, gp.longitude)
                            return true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val dx = e.x - lastX; val dy = e.y - lastY
                            if (dx * dx + dy * dy < MIN_PX * MIN_PX) return true
                            lastX = e.x; lastY = e.y
                            val gp = mv.projection.fromPixels(e.x.toInt(), e.y.toInt()) as GeoPoint
                            vm.agregarPuntoLinea(gp.latitude, gp.longitude)
                            return true
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            lastX = Float.NaN; lastY = Float.NaN
                            return true
                        }
                    }
                    return false
                }
            })
        }
    }

    LaunchedEffect(Unit) {
        Configuration.getInstance().apply {
            userAgentValue = context.packageName
            load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        }
    }

    // Auto-seleccionar polígono lanzado desde Capas y hacer zoom
    LaunchedEffect(predioInicial) {
        predioInicial?.let { predio ->
            vm.seleccionarPoligono(predio)
            val c = predio.geometry.centroid.coordinate
            mapView.controller.setZoom(17.0)
            mapView.controller.animateTo(GeoPoint(c.y, c.x))
        }
    }

    // Sincronizar basemap con la pantalla principal
    LaunchedEffect(basemapActual) {
        mapView.setTileSource(basemapActual.toTileSource())
        mapView.invalidate()
    }

    // Centrar y dibujar polígono al seleccionar o cambiar relleno
    LaunchedEffect(state.predioBase, conRelleno) {
        state.predioBase?.let { predio ->
            dibujarPoligonoBase(mapView, predio, state.segmentos, state.areaHa, conRelleno, areaUnit, distanceUnit)
            val centroid = predio.geometry.centroid.coordinate
            mapView.controller.animateTo(GeoPoint(centroid.y, centroid.x))
        }
    }

    // Rastrear el centro del mapa para la diana y el rubber-band
    DisposableEffect(mapView) {
        val listener = object : org.osmdroid.events.MapListener {
            override fun onScroll(e: org.osmdroid.events.ScrollEvent): Boolean {
                centroDiana = mapView.mapCenter as GeoPoint; return false
            }
            override fun onZoom(e: org.osmdroid.events.ZoomEvent): Boolean {
                centroDiana = mapView.mapCenter as GeoPoint; return false
            }
        }
        mapView.addMapListener(listener)
        onDispose { mapView.removeMapListener(listener) }
    }

    // Línea en tiempo real: puntos confirmados + rubber-band hasta la diana
    val enModoTrazo = state.modo == ModoParcelacion.CORTE         ||
                      state.modo == ModoParcelacion.VIA_LINEA     ||
                      state.modo == ModoParcelacion.SUBDIVISION_LINEA
    val puntosConPreview = if (enModoTrazo && state.puntosLinea.isNotEmpty())
        state.puntosLinea + Pair(centroDiana.latitude, centroDiana.longitude)
    else state.puntosLinea

    LaunchedEffect(puntosConPreview, state.modo) {
        dibujarLineaEnCurso(mapView, puntosConPreview, state.modo)
    }

    // Dibujar polígonos de contexto (resto del proyecto) y resaltar el seleccionado
    LaunchedEffect(poligonos, state.predioBase, areaUnit) {
        dibujarPoligonosContexto(mapView, poligonos, state.predioBase?.id, areaUnit)
    }

    // Dibujar subparcelas resultado con etiquetas de área y distancia (vía: sin etiquetas)
    LaunchedEffect(state.subparcelas, state.esVia, areaUnit, distanceUnit) {
        if (state.subparcelas.isNotEmpty()) {
            dibujarSubparcelas(mapView, state.subparcelas, areaUnit, distanceUnit, conEtiquetas = !state.esVia)
        } else {
            mapView.overlays.removeAll { it is SubparcelaPolygon }
            mapView.overlays.removeAll { it is Marker && (it as Marker).id == TAG_SUBPARCELA_LBL }
            mapView.invalidate()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // ── Mapa de fondo ─────────────────────────────────────────────────
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

        // ── Header ────────────────────────────────────────────────────────
        ParcelacionHeader(
            titulo    = titulo,
            subtitulo = state.predioBase?.nombre ?: "Sin polígono",
            onBack    = onBack,
            modifier  = Modifier.align(Alignment.TopCenter)
        )

        // ── Indicador norte — igual que pantalla principal ────────────────
        NorthArrow(
            azimut   = azimut,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 70.dp, end = 10.dp)
        )

        // ── Cards compactas de configuración ─────────────────────────────
        AnimatedVisibility(
            visible = state.modo == ModoParcelacion.SUBDIVISION,
            enter = fadeIn() + scaleIn(initialScale = 0.92f),
            exit  = fadeOut() + scaleOut(targetScale = 0.92f),
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 12.dp, end = 60.dp)
        ) {
            SubdivisionCompactCard(
                areaHa         = state.areaHa,
                procesando     = state.procesando,
                onAplicar      = { tipo, valor ->
                    vm.prepararSubdivision(tipo, valor)
                },
                onSelectorPoly = { mostrarSelectorPoligono = true }
            )
        }

        AnimatedVisibility(
            visible = state.modo == ModoParcelacion.VIA,
            enter = fadeIn() + scaleIn(initialScale = 0.92f),
            exit  = fadeOut() + scaleOut(targetScale = 0.92f),
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 12.dp, end = 60.dp)
        ) {
            ViaCompactCard(
                ancho          = state.anchoViaMetros,
                onAncho        = vm::setAnchoVia,
                onIniciar      = vm::prepararVia,
                onSelectorPoly = { mostrarSelectorPoligono = true }
            )
        }

        // ── Diana + botón confirmar punto (modos de trazo) ───────────────
        if (enModoTrazo) {
            // Crosshair centrada en pantalla
            ParcCrosshair(modifier = Modifier.fillMaxSize())

            // Fila de botones de acción encima de la barra inferior
            // El cancelar va AQUÍ dentro para no solaparse con "Añadir punto"
            Row(
                modifier              = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 80.dp)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                // Botón cancelar dentro del row para evitar solapamiento
                FloatingActionButton(
                    onClick        = vm::cancelar,
                    containerColor = Color(0xFFF44336),
                    shape          = RoundedCornerShape(12.dp),
                    modifier       = Modifier.size(48.dp),
                    elevation      = FloatingActionButtonDefaults.elevation(4.dp)
                ) {
                    Icon(Icons.Default.Close, "Cancelar", tint = Color.White, modifier = Modifier.size(20.dp))
                }

                // Botón undo — elimina el último punto trazado
                val hayPuntos = state.puntosLinea.isNotEmpty()
                FloatingActionButton(
                    onClick        = vm::quitarUltimoPunto,
                    containerColor = if (hayPuntos) Color(0xFF37474F) else Color(0xFF263238),
                    shape          = RoundedCornerShape(12.dp),
                    modifier       = Modifier.size(48.dp),
                    elevation      = FloatingActionButtonDefaults.elevation(4.dp)
                ) {
                    Icon(Icons.Default.Undo, "Deshacer punto",
                        tint     = if (hayPuntos) Color.White else Color(0xFF607D8B),
                        modifier = Modifier.size(20.dp))
                }

                // Botón GPS — añade la posición actual del dispositivo
                val gpsActivo = estadoGps.puntoActual != null
                FloatingActionButton(
                    onClick = {
                        estadoGps.puntoActual?.let { p ->
                            val (sLat, sLon) = snapAPunto(poligonos, p.latitud, p.longitud)
                            vm.agregarPuntoLinea(sLat, sLon)
                            mapView.controller.animateTo(GeoPoint(sLat, sLon))
                        }
                    },
                    containerColor = if (gpsActivo) Color(0xFF00897B) else Color(0xFF546E7A),
                    shape          = CircleShape,
                    modifier       = Modifier.size(52.dp),
                    elevation      = FloatingActionButtonDefaults.elevation(4.dp)
                ) {
                    Icon(
                        if (gpsActivo) Icons.Default.AddLocation else Icons.Default.GpsOff,
                        null,
                        tint     = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Botón diana — añade el centro del mapa (con snap a punto cercano)
                FloatingActionButton(
                    onClick = {
                        val gp = mapView.mapCenter as GeoPoint
                        val (sLat, sLon) = snapAPunto(poligonos, gp.latitude, gp.longitude)
                        vm.agregarPuntoLinea(sLat, sLon)
                    },
                    containerColor = Color(0xFF0D2B4E),
                    shape          = RoundedCornerShape(14.dp),
                    modifier       = Modifier.height(48.dp).widthIn(min = 110.dp),
                    elevation      = FloatingActionButtonDefaults.elevation(4.dp)
                ) {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier              = Modifier.padding(horizontal = 12.dp)
                    ) {
                        Icon(Icons.Default.AddLocation, null, tint = Color(0xFF90CAF9), modifier = Modifier.size(18.dp))
                        Text("Añadir punto", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // ── Botón cancelar (rojo) solo para modos config (VIA, SUBDIVISION) ──
        AnimatedVisibility(
            visible  = !enModoTrazo && state.modo != ModoParcelacion.IDLE,
            enter    = scaleIn(),
            exit     = scaleOut(),
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 80.dp)
        ) {
            FloatingActionButton(
                onClick        = vm::cancelar,
                containerColor = Color(0xFFF44336),
                shape          = RoundedCornerShape(12.dp),
                modifier       = Modifier.size(52.dp)
            ) {
                Icon(Icons.Default.Close, "Cancelar", tint = Color.White)
            }
        }

        // ── Barra inferior: herramientas o confirmar resultado ───────────
        if (state.pendienteGuardar) {
            Column(
                modifier              = Modifier.align(Alignment.BottomCenter),
                verticalArrangement   = Arrangement.Bottom,
                horizontalAlignment   = Alignment.CenterHorizontally
            ) {
                // Selector de subparcela para corte sucesivo (solo en resultado de 2 piezas)
                if (state.subparcelas.size == 2) {
                    Row(
                        modifier              = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1A3A5C))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text(
                            "Cortar de nuevo:",
                            color    = Color(0xFF90CAF9),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        state.subparcelasInfo.forEachIndexed { idx, info ->
                            OutlinedButton(
                                onClick = { vm.seleccionarSubparcelaComoBase(idx) },
                                shape   = RoundedCornerShape(10.dp),
                                border  = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF90CAF9)),
                                colors  = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF90CAF9)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(Icons.Default.ContentCut, null, modifier = Modifier.size(12.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(info.nombre.takeLastWhile { it != '_' }.ifBlank { "${idx + 1}" },
                                    fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                ConfirmationBar(
                    onDeshacer  = vm::deshacerResultado,
                    onConfirmar = vm::confirmarGuardado,
                    onEditar    = { mostrarEditorParcelas = true }
                )
            }
        } else {
            ToolsBottomBar(
                modo             = state.modo,
                tienePredioBase  = state.predioBase != null,
                conRelleno       = conRelleno,
                onSelectorCapas  = { mostrarSelectorPoligono = true },
                onCorte          = vm::iniciarCorte,
                onVia            = { vm.setAnchoVia(10.0); vm.iniciarVia() },
                onSubdividir     = vm::iniciarSubdivision,
                onToggleRelleno  = { conRelleno = !conRelleno },
                onFinalizarCorte       = { vm.finalizarCorte(state.predioBase?.nombre ?: "Corte", proyectoId) },
                onFinalizarVia         = { vm.finalizarVia(proyectoId, state.predioBase?.nombre ?: "Via") },
                onFinalizarSubdivision = { vm.finalizarSubdivision(proyectoId, state.predioBase?.nombre ?: "Parcela") },
                puntosLinea            = state.puntosLinea.size,
                modifier         = Modifier.align(Alignment.BottomCenter)
            )
        }

        // Error
        state.error?.let { err ->
            Snackbar(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 90.dp, start = 16.dp, end = 16.dp),
                action   = { TextButton(onClick = vm::limpiarError) { Text("OK") } }
            ) { Text(err) }
            LaunchedEffect(err) { vm.limpiarError() }
        }
    }

    // ── Editor de atributos de parcelas ──────────────────────────────────────
    if (mostrarEditorParcelas && state.subparcelasInfo.isNotEmpty()) {
        SubparcelasEditSheet(
            infos     = state.subparcelasInfo,
            onEditar  = { idx, nombre, propietario -> vm.editarSubparcela(idx, nombre, propietario) },
            onDismiss = { mostrarEditorParcelas = false }
        )
    }

    // ── Selector de polígono ──────────────────────────────────────────────────
    if (mostrarSelectorPoligono) {
        SelectorPoligonoDialog(
            poligonos    = poligonos.filter {
                it.geometry.geometryType != "Point" &&
                it.geometry.geometryType != "LineString"
            },
            onSeleccionar = { vm.seleccionarPoligono(it) },
            onDismiss     = { mostrarSelectorPoligono = false }
        )
    }
}

// ── Header ────────────────────────────────────────────────────────────────────

@Composable
private fun ParcelacionHeader(
    titulo   : String,
    subtitulo: String,
    onBack   : () -> Unit,
    modifier : Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF0D2B4E))
    ) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FloatingActionButton(
                onClick        = onBack,
                shape          = CircleShape,
                containerColor = Color(0xFF1A1A1A),
                modifier       = Modifier.size(40.dp),
                elevation      = FloatingActionButtonDefaults.elevation(2.dp, 2.dp)
            ) {
                Icon(Icons.Default.ArrowBack, "Volver", tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(titulo,    color = Color(0xFF90CAF9), fontWeight = FontWeight.Bold,   fontSize = 16.sp)
                Text(subtitulo, color = Color(0xFF81C784), fontWeight = FontWeight.Normal, fontSize = 11.sp)
            }
        }
    }
}

// ── Barra de herramientas inferior ───────────────────────────────────────────

@Composable
private fun ToolsBottomBar(
    modo                   : ModoParcelacion,
    tienePredioBase        : Boolean,
    conRelleno             : Boolean,
    onSelectorCapas        : () -> Unit,
    onCorte                : () -> Unit,
    onVia                  : () -> Unit,
    onSubdividir           : () -> Unit,
    onToggleRelleno        : () -> Unit,
    onFinalizarCorte       : () -> Unit,
    onFinalizarVia         : () -> Unit,
    onFinalizarSubdivision : () -> Unit,
    puntosLinea            : Int,
    modifier               : Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xF0FFFFFF))
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        // Botón capas circular gris
        FloatingActionButton(
            onClick        = onSelectorCapas,
            shape          = CircleShape,
            containerColor = Color(0xFFB0BEC5),
            modifier       = Modifier.size(44.dp),
            elevation      = FloatingActionButtonDefaults.elevation(2.dp)
        ) {
            Icon(Icons.Default.Layers, "Polígonos", tint = Color(0xFF0D2B4E), modifier = Modifier.size(20.dp))
        }

        Spacer(Modifier.width(2.dp))

        val sb = LocalStrings.current
        when (modo) {
            ModoParcelacion.IDLE -> {
                ToolBtn("✂", sb.corte,     CyanBtn,  tienePredioBase, onCorte,      Modifier.weight(1f))
                ToolBtn("⊢", sb.trazarVia, CyanBtn,  tienePredioBase, onVia,        Modifier.weight(1f))
                ToolBtn("⊞", sb.subdividir,VerdeBtn, tienePredioBase, onSubdividir, Modifier.weight(1f))
                // Toggle relleno — mismo estilo que los demás botones
                Button(
                    onClick        = onToggleRelleno,
                    enabled        = tienePredioBase,
                    modifier       = Modifier.weight(1f).height(52.dp),
                    shape          = RoundedCornerShape(14.dp),
                    colors         = ButtonDefaults.buttonColors(
                        containerColor         = if (conRelleno) Color(0xFFFFCC02) else Color(0xFF607D8B),
                        disabledContainerColor = Color(0xFF607D8B).copy(0.35f)
                    ),
                    contentPadding = PaddingValues(4.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            if (conRelleno) Icons.Default.InvertColors else Icons.Default.InvertColorsOff,
                            null,
                            tint     = Color(0xFF0D2B4E),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            if (conRelleno) sb.relleno else sb.borde,
                            fontSize   = 9.sp,
                            fontWeight = FontWeight.Medium,
                            color      = Color(0xFF0D2B4E)
                        )
                    }
                }
            }
            ModoParcelacion.CORTE -> {
                ToolBtn("✓", sb.finalizar, VerdeBtn, puntosLinea >= 2, onFinalizarCorte, Modifier.weight(2f))
            }
            ModoParcelacion.VIA -> {
                // Card de configuración visible arriba — barra queda vacía
            }
            ModoParcelacion.VIA_LINEA -> {
                ToolBtn("✓", sb.finalizar, VerdeBtn, puntosLinea >= 2, onFinalizarVia,   Modifier.weight(2f))
            }
            ModoParcelacion.SUBDIVISION -> {
                // La card de subdivisión está visible arriba — barra queda vacía
            }
            ModoParcelacion.SUBDIVISION_LINEA -> {
                ToolBtn("✓", sb.finalizar, VerdeBtn, puntosLinea >= 2, onFinalizarSubdivision, Modifier.weight(2f))
            }
        }
    }
}

@Composable
private fun ToolBtn(
    emoji    : String,
    label    : String,
    color    : Color,
    enabled  : Boolean,
    onClick  : () -> Unit,
    modifier : Modifier = Modifier
) {
    Button(
        onClick        = onClick,
        enabled        = enabled,
        modifier       = modifier.height(52.dp),
        shape          = RoundedCornerShape(14.dp),
        colors         = ButtonDefaults.buttonColors(
            containerColor         = color,
            disabledContainerColor = color.copy(0.35f)
        ),
        contentPadding = PaddingValues(4.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, fontSize = 18.sp)
            Text(label, fontSize = 9.sp, fontWeight = FontWeight.Medium, color = Color.White)
        }
    }
}

// ── Card Subdivisión compacta ─────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubdivisionCompactCard(
    areaHa         : Double,
    procesando     : Boolean,
    onAplicar      : (TipoSubdivision, Double) -> Unit,
    onSelectorPoly : () -> Unit
) {
    val sc = LocalStrings.current
    var expandido        by remember { mutableStateOf(true) }
    var tipoSeleccionado by remember { mutableStateOf<TipoSubdivision?>(null) }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var valor            by remember { mutableStateOf("") }

    GlassLightBox(shape = RoundedCornerShape(16.dp), elevation = 8.dp) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp).widthIn(max = 260.dp)) {

            // ── Cabecera colapsable ───────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expandido = !expandido },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Default.GridOn, null, tint = Color(0xFF0D2B4E), modifier = Modifier.size(16.dp))
                Text(
                    sc.subdivision,
                    color = Color(0xFF0D2B4E), fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )
                // Botón selector polígono
                IconButton(onClick = onSelectorPoly, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Layers, null, tint = Color(0xFF0D2B4E).copy(0.55f), modifier = Modifier.size(14.dp))
                }
                Icon(
                    if (expandido) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    null, tint = Color(0xFF0D2B4E).copy(0.5f), modifier = Modifier.size(16.dp)
                )
            }

            // ── Contenido colapsable ──────────────────────────────────────
            AnimatedVisibility(expandido) {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Dropdown tipo
                    ExposedDropdownMenuBox(
                        expanded         = dropdownExpanded,
                        onExpandedChange = { dropdownExpanded = it }
                    ) {
                        OutlinedTextField(
                            value         = tipoSeleccionado?.label(sc) ?: "Tipo…",
                            onValueChange = {},
                            readOnly      = true,
                            trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(dropdownExpanded) },
                            modifier      = Modifier.menuAnchor().fillMaxWidth(),
                            textStyle     = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                            colors        = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor   = Color(0xFF0D2B4E),
                                unfocusedBorderColor = Color(0xFF0D2B4E).copy(0.4f),
                                focusedTextColor     = Color(0xFF0D2B4E),
                                unfocusedTextColor   = Color(0xFF0D2B4E)
                            )
                        )
                        ExposedDropdownMenu(
                            expanded         = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false },
                            modifier         = Modifier.background(Color.White)
                        ) {
                            TipoSubdivision.values().forEach { tipo ->
                                DropdownMenuItem(
                                    text    = { Text(tipo.label(sc), color = Color(0xFF0D2B4E), fontSize = 13.sp) },
                                    onClick = { tipoSeleccionado = tipo; dropdownExpanded = false; valor = "" }
                                )
                            }
                        }
                    }

                    tipoSeleccionado?.let { tipo ->
                        OutlinedTextField(
                            value           = valor,
                            onValueChange   = { valor = it },
                            label           = { Text(tipo.campoLabel(sc), fontSize = 11.sp) },
                            singleLine      = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = if (tipo == TipoSubdivision.PARTES_IGUALES) KeyboardType.Number else KeyboardType.Decimal
                            ),
                            textStyle       = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                            modifier        = Modifier.fillMaxWidth(),
                            colors          = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor   = Color(0xFF0D2B4E),
                                focusedTextColor     = Color(0xFF0D2B4E),
                                unfocusedTextColor   = Color(0xFF0D2B4E)
                            )
                        )

                        // Estimado parcelas
                        if (valor.isNotBlank() && tipo != TipoSubdivision.PARTES_IGUALES) {
                            valor.toDoubleOrNull()?.takeIf { it > 0 }?.let { v ->
                                val n = when (tipo) {
                                    TipoSubdivision.POR_AREA -> {
                                        val nFull    = (areaHa / v).toInt()
                                        val residual = areaHa - nFull * v
                                        (nFull + if (residual > 0.001) 1 else 0).coerceAtLeast(2).toString()
                                    }
                                    else -> "~"
                                }
                                Text("→ $n parcelas est.", color = Color(0xFF4CAF50), fontSize = 11.sp)
                            }
                        }

                        Button(
                            onClick        = { onAplicar(tipo, valor.toDoubleOrNull() ?: 2.0) },
                            enabled        = valor.isNotBlank() && !procesando,
                            modifier       = Modifier.fillMaxWidth(),
                            shape          = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(vertical = 8.dp),
                            colors         = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                        ) {
                            if (procesando) CircularProgressIndicator(Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                            else {
                                Icon(Icons.Default.Timeline, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Trazar eje →", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun TipoSubdivision.label(s: com.act.geomapper.ui.theme.AppStrings) = when (this) {
    TipoSubdivision.FRENTE_OBJETIVO -> s.frenteObjetivo
    TipoSubdivision.POR_AREA        -> s.porArea
    TipoSubdivision.PARTES_IGUALES  -> s.partesIguales
}

private fun TipoSubdivision.campoLabel(s: com.act.geomapper.ui.theme.AppStrings) = when (this) {
    TipoSubdivision.FRENTE_OBJETIVO -> s.frenteM
    TipoSubdivision.POR_AREA        -> s.areaPorParcela
    TipoSubdivision.PARTES_IGUALES  -> s.numeroParcelas
}

// ── Barra confirmar / deshacer resultado ──────────────────────────────────────

@Composable
private fun ConfirmationBar(
    onDeshacer  : () -> Unit,
    onConfirmar : () -> Unit,
    onEditar    : () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0D2B4E))
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick  = onDeshacer,
            modifier = Modifier.weight(1f),
            shape    = RoundedCornerShape(12.dp),
            border   = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(0.5f)),
            colors   = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
        ) {
            Icon(Icons.Default.Undo, null, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(4.dp))
            Text("Deshacer", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
        // Botón editar atributos de parcelas
        IconButton(
            onClick  = onEditar,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color(0xFF1A3A5C))
        ) {
            Icon(Icons.Default.Edit, "Editar parcelas", tint = Color(0xFF90CAF9), modifier = Modifier.size(20.dp))
        }
        Button(
            onClick  = onConfirmar,
            modifier = Modifier.weight(1f),
            shape    = RoundedCornerShape(12.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
        ) {
            Icon(Icons.Default.Check, null, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(4.dp))
            Text("Guardar", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
    }
}

// ── Card Vía compacta ─────────────────────────────────────────────────────────

@Composable
private fun ViaCompactCard(
    ancho          : Double,
    onAncho        : (Double) -> Unit,
    onIniciar      : () -> Unit,
    onSelectorPoly : () -> Unit
) {
    var expandido by remember { mutableStateOf(false) }

    GlassLightBox(shape = RoundedCornerShape(20.dp), elevation = 8.dp) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp).widthIn(max = 260.dp)) {

            // ── Fila compacta principal ───────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(Icons.Default.SwapHoriz, null, tint = CyanBtn, modifier = Modifier.size(16.dp))
                Text("Vía", color = Color(0xFF0D2B4E), fontWeight = FontWeight.Bold, fontSize = 13.sp)

                // Selector de polígono
                IconButton(onClick = onSelectorPoly, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Layers, null, tint = Color(0xFF0D2B4E).copy(0.55f), modifier = Modifier.size(14.dp))
                }

                Spacer(Modifier.weight(1f))

                // [-] ancho [+]
                IconButton(
                    onClick  = { onAncho((ancho - 1.0).coerceAtLeast(1.0)) },
                    modifier = Modifier.size(28.dp)
                ) { Text("−", color = Color(0xFF0D2B4E), fontSize = 18.sp, fontWeight = FontWeight.Bold) }

                Text(
                    "%.1fm".format(ancho),
                    color = Color(0xFF0D2B4E), fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    modifier = Modifier.widthIn(min = 40.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                IconButton(
                    onClick  = { onAncho((ancho + 1.0).coerceAtMost(50.0)) },
                    modifier = Modifier.size(28.dp)
                ) { Text("+", color = Color(0xFF0D2B4E), fontSize = 18.sp, fontWeight = FontWeight.Bold) }

                // Expandir slider
                IconButton(onClick = { expandido = !expandido }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        if (expandido) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        null, tint = Color(0xFF0D2B4E).copy(0.5f), modifier = Modifier.size(16.dp)
                    )
                }
            }

            // ── Slider colapsable ─────────────────────────────────────────
            AnimatedVisibility(expandido) {
                Column(modifier = Modifier.padding(top = 4.dp)) {
                    Slider(
                        value         = ancho.toFloat().coerceIn(1f, 50f),
                        onValueChange = { onAncho(it.toDouble()) },
                        valueRange    = 1f..50f,
                        modifier      = Modifier.fillMaxWidth(),
                        colors        = SliderDefaults.colors(
                            thumbColor       = CyanBtn,
                            activeTrackColor = CyanBtn
                        )
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("1m",  color = Color(0xFF0D2B4E).copy(0.45f), fontSize = 10.sp)
                        Text("50m", color = Color(0xFF0D2B4E).copy(0.45f), fontSize = 10.sp)
                    }
                }
            }

            // ── Botón dibujar ─────────────────────────────────────────────
            Button(
                onClick        = onIniciar,
                modifier       = Modifier.fillMaxWidth().padding(top = 6.dp),
                shape          = RoundedCornerShape(12.dp),
                colors         = ButtonDefaults.buttonColors(containerColor = CyanBtn),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) { Text("Dibujar eje →", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
        }
    }
}

// ── Selector de polígono ──────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectorPoligonoDialog(
    poligonos    : List<Predio>,
    onSeleccionar: (Predio) -> Unit,
    onDismiss    : () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White) {
        Column(Modifier.padding(16.dp).navigationBarsPadding()) {
            Text("Seleccionar polígono", color = Color(0xFF0D2B4E), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))
            if (poligonos.isEmpty()) {
                Text("No hay polígonos en el proyecto activo.", color = Color.Gray, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(poligonos, key = { it.id }) { p ->
                        OutlinedButton(
                            onClick   = { onSeleccionar(p); onDismiss() },
                            modifier  = Modifier.fillMaxWidth(),
                            shape     = RoundedCornerShape(12.dp),
                            colors    = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF0D2B4E))
                        ) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(p.nombre.ifBlank { "Polígono #${p.id}" }, fontWeight = FontWeight.SemiBold)
                                Text("%.4f ha".format(p.area), color = Color.Gray, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ── Editor de atributos de parcelas pendientes ────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubparcelasEditSheet(
    infos    : List<com.act.geomapper.presentation.viewmodels.SubparcelaEditable>,
    onEditar : (Int, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = Color(0xFF111111),
        dragHandle       = { BottomSheetDefaults.DragHandle(color = Color.White.copy(0.3f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Icon(Icons.Default.Edit, null, tint = Color(0xFF90CAF9), modifier = Modifier.size(18.dp))
                Text("Editar parcelas", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.weight(1f))
                Text("${infos.size} parcelas", color = Color.White.copy(0.4f), fontSize = 12.sp)
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(infos) { index, info ->
                    ParcelaEditRow(
                        index    = index + 1,
                        info     = info,
                        onEditar = { nombre, prop -> onEditar(index, nombre, prop) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ParcelaEditRow(
    index   : Int,
    info    : com.act.geomapper.presentation.viewmodels.SubparcelaEditable,
    onEditar: (String, String) -> Unit
) {
    var nombre      by remember(info.nombre)      { mutableStateOf(info.nombre) }
    var propietario by remember(info.propietario) { mutableStateOf(info.propietario) }
    var expandido   by remember { mutableStateOf(false) }

    androidx.compose.material3.Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF1A1A1A),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Cabecera compacta
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expandido = !expandido }
            ) {
                // Badge número
                androidx.compose.material3.Surface(
                    shape = CircleShape,
                    color = Color(0xFF0D2B4E),
                    modifier = Modifier.size(26.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("$index", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        nombre.ifBlank { "Sin nombre" },
                        color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                    )
                    if (info.areaHa > 0) {
                        Text("%.4f ha".format(info.areaHa), color = Color.White.copy(0.45f), fontSize = 10.sp)
                    }
                }
                Icon(
                    if (expandido) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    null, tint = Color.White.copy(0.4f), modifier = Modifier.size(18.dp)
                )
            }

            // Campos editables expandibles
            AnimatedVisibility(expandido) {
                Column(
                    modifier = Modifier.padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value           = nombre,
                        onValueChange   = { nombre = it; onEditar(it, propietario) },
                        label           = { Text("Nombre", fontSize = 11.sp) },
                        singleLine      = true,
                        modifier        = Modifier.fillMaxWidth(),
                        textStyle       = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = Color.White),
                        colors          = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = Color(0xFF90CAF9),
                            unfocusedBorderColor = Color.White.copy(0.3f),
                            focusedLabelColor    = Color(0xFF90CAF9),
                            unfocusedLabelColor  = Color.White.copy(0.4f),
                            focusedTextColor     = Color.White,
                            unfocusedTextColor   = Color.White,
                            cursorColor          = Color(0xFF90CAF9)
                        )
                    )
                    OutlinedTextField(
                        value           = propietario,
                        onValueChange   = { propietario = it; onEditar(nombre, it) },
                        label           = { Text("Propietario / Atributo", fontSize = 11.sp) },
                        singleLine      = true,
                        modifier        = Modifier.fillMaxWidth(),
                        textStyle       = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = Color.White),
                        colors          = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = Color(0xFF90CAF9),
                            unfocusedBorderColor = Color.White.copy(0.3f),
                            focusedLabelColor    = Color(0xFF90CAF9),
                            unfocusedLabelColor  = Color.White.copy(0.4f),
                            focusedTextColor     = Color.White,
                            unfocusedTextColor   = Color.White,
                            cursorColor          = Color(0xFF90CAF9)
                        )
                    )
                }
            }
        }
    }
}

// ── Diana crosshair (modo trazo) ─────────────────────────────────────────────

@Composable
private fun ParcCrosshair(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val cx  = size.width  / 2f
        val cy  = size.height / 2f
        val r   = 18.dp.toPx()
        val arm = 10.dp.toPx()
        val s   = 2.dp.toPx()
        val col = Color(0xDD000000)
        drawCircle(col, r, Offset(cx, cy), style = Stroke(s))
        drawLine(col, Offset(cx, cy - r),     Offset(cx, cy - r - arm), s)
        drawLine(col, Offset(cx, cy + r),     Offset(cx, cy + r + arm), s)
        drawLine(col, Offset(cx - r, cy),     Offset(cx - r - arm, cy), s)
        drawLine(col, Offset(cx + r, cy),     Offset(cx + r + arm, cy), s)
        drawCircle(col, 3.dp.toPx(), Offset(cx, cy))
    }
}

// ── Overlays OSMDroid ─────────────────────────────────────────────────────────

private const val TAG_POLIGONO_BASE   = "parcBase"
private const val TAG_DIST_LABEL      = "distLabel"
private const val TAG_AREA_LABEL_PARC = "areaLabelParc"
private const val TAG_LINEA_CORTE     = "lineaCorte"
private const val TAG_SUBPARCELA      = "subpar"
private const val TAG_SUBPARCELA_LBL  = "subparLbl"
private const val TAG_CONTEXTO        = "contexto"
private const val TAG_CONTEXTO_LBL    = "contextoLbl"
private const val TAG_REF_POINT       = "refPoint"    // puntos de referencia en Parcelación

/** Distancia haversine simplificada en metros para snap (sin necesitar GeometryService) */
private fun distM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a    = kotlin.math.sin(dLat / 2).pow(2) +
               kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
               kotlin.math.sin(dLon / 2).pow(2)
    return 6_371_000.0 * 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
}

/**
 * Si el centro del mapa está a menos de [umbralM] metros de algún punto de referencia
 * del proyecto, retorna sus coordenadas exactas (snap). Si no, retorna [lat],[lon].
 */
private fun snapAPunto(
    predios  : List<com.act.geomapper.domain.models.Predio>,
    lat      : Double,
    lon      : Double,
    umbralM  : Double = 15.0
): Pair<Double, Double> {
    var mejorDist = umbralM
    var resultado = Pair(lat, lon)
    for (p in predios) {
        if (p.geometry.geometryType != "Point") continue
        val c = p.geometry.coordinates.firstOrNull() ?: continue
        val d = distM(lat, lon, c.y, c.x)
        if (d < mejorDist) { mejorDist = d; resultado = Pair(c.y, c.x) }
    }
    return resultado
}

/** Polígono base seleccionado */
private class PoligonoBasePolygon(mv: MapView) : Polygon(mv)
/** Subparcela resultado */
private class SubparcelaPolygon(mv: MapView) : Polygon(mv)
/** Polígono de contexto (otros predios del proyecto, solo visual) */
private class ContextoPolygon(mv: MapView) : Polygon(mv)

/** Marker que solo dibuja cuando zoom ≥ minZoom (idéntico al de MapScreen) */
private class ParcMinZoomMarker(mapView: MapView, private val minZoom: Double) : Marker(mapView) {
    init { infoWindow = null }
    override fun draw(c: android.graphics.Canvas, osmv: MapView, shadow: Boolean) {
        if (osmv.zoomLevelDouble >= minZoom) super.draw(c, osmv, shadow)
    }
}

/** Chip de área — fondo oscuro semitransparente, texto blanco (distinto al chip naranja de distancia) */
private fun crearChipArea(context: Context, texto: String): android.graphics.drawable.BitmapDrawable {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize  = 30f
        color     = AColor.WHITE
        typeface  = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    val textW = paint.measureText(texto)
    val padH  = 14f; val padV = 8f
    val w     = (textW + padH * 2).toInt()
    val h     = (paint.textSize + padV * 2).toInt()
    val bmp   = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c     = Canvas(bmp)
    val bgP   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AColor.argb(200, 20, 20, 20) }
    c.drawRoundRect(0f, 0f, w.toFloat(), h.toFloat(), h / 2f, h / 2f, bgP)
    c.drawText(texto, w / 2f, h - padV - 4f, paint)
    return android.graphics.drawable.BitmapDrawable(context.resources, bmp)
}

/** Haversine en metros (para lados sin segmento pre-calculado) */
private fun haversineParcM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val R    = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1); val dLon = Math.toRadians(lon2 - lon1)
    val sl   = Math.sin(dLat / 2); val slo = Math.sin(dLon / 2)
    val a    = sl * sl + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * slo * slo
    return R * 2 * Math.asin(Math.sqrt(a))
}

private fun dibujarPoligonoBase(
    mapView      : MapView,
    predio       : Predio,
    segmentos    : List<SegmentoInfo>,
    areaHa       : Double,
    conRelleno   : Boolean = true,
    areaUnit     : com.act.geomapper.data.settings.AreaUnit     = com.act.geomapper.data.settings.AreaUnit.HECTARES,
    distanceUnit : com.act.geomapper.data.settings.DistanceUnit = com.act.geomapper.data.settings.DistanceUnit.METERS
) {
    mapView.overlays.removeAll { it is PoligonoBasePolygon }
    mapView.overlays.removeAll { it is Marker  && (it as Marker).id in setOf(TAG_DIST_LABEL, TAG_AREA_LABEL_PARC) }

    val centroid = predio.geometry.centroid.coordinate
    val coords   = predio.geometry.coordinates.map { GeoPoint(it.y, it.x) }

    // Polígono naranja (sin title → no muestra texto flotante en el mapa)
    PoligonoBasePolygon(mapView).apply {
        infoWindow               = null
        points                   = coords
        outlinePaint.color       = NaranjaPoligono
        outlinePaint.strokeWidth = 6f
        fillPaint.color          = if (conRelleno) AmarilloRelleno else AColor.TRANSPARENT
        mapView.overlays.add(0, this)
    }

    // ── Etiqueta de ÁREA en el centroide (zoom ≥ 13, fondo oscuro) ───────────
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
    ParcMinZoomMarker(mapView, 13.0).apply {
        id       = TAG_AREA_LABEL_PARC
        position = GeoPoint(centroid.y, centroid.x)
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        icon     = crearChipArea(mapView.context, textoArea)
        mapView.overlays.add(this)
    }

    // ── Etiquetas de DISTANCIA por lado (zoom ≥ 15, chip naranja) ────────────
    // Offset 30% hacia el centroide → lados compartidos quedan en lados opuestos
    val rawCoords = predio.geometry.coordinates
    for (i in 0 until rawCoords.size - 1) {
        val c1 = rawCoords[i]; val c2 = rawCoords[i + 1]
        val seg = segmentos.getOrNull(i)
        val distM = seg?.distanciaMetros ?: haversineParcM(c1.y, c1.x, c2.y, c2.x)
        if (distM < 0.5) continue
        val texto = when (distanceUnit) {
            com.act.geomapper.data.settings.DistanceUnit.KILOMETERS ->
                "%.2f km".format(distM / 1000.0)
            else ->
                if (distM >= 1000) "%.2f km".format(distM / 1000.0)
                else "%.1f m".format(distM)
        }
        val midLat = (c1.y + c2.y) / 2 + (centroid.y - (c1.y + c2.y) / 2) * 0.30
        val midLon = (c1.x + c2.x) / 2 + (centroid.x - (c1.x + c2.x) / 2) * 0.30
        ParcMinZoomMarker(mapView, 15.0).apply {
            id       = TAG_DIST_LABEL
            position = GeoPoint(midLat, midLon)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            icon     = crearChipNaranja(mapView.context, texto)
            mapView.overlays.add(this)
        }
    }

    mapView.invalidate()
}

private fun dibujarLineaEnCurso(
    mapView : MapView,
    puntos  : List<Pair<Double, Double>>,
    modo    : ModoParcelacion
) {
    mapView.overlays.removeAll { it is Polyline && it.title == TAG_LINEA_CORTE }
    if (puntos.size < 2) { mapView.invalidate(); return }

    val color = when (modo) {
        ModoParcelacion.CORTE,
        ModoParcelacion.VIA_LINEA,
        ModoParcelacion.SUBDIVISION_LINEA -> AColor.parseColor("#00BCD4")  // cian
        else                              -> AColor.GRAY
    }

    Polyline(mapView).apply {
        title            = TAG_LINEA_CORTE
        infoWindow       = null
        setPoints(puntos.map { (lat, lon) -> GeoPoint(lat, lon) })
        outlinePaint.color       = color
        outlinePaint.strokeWidth = 4f
        if (modo == ModoParcelacion.VIA) {
            outlinePaint.pathEffect = DashPathEffect(floatArrayOf(15f, 8f), 0f)
        }
        mapView.overlays.add(this)
    }
    mapView.invalidate()
}

private fun dibujarSubparcelas(
    mapView      : MapView,
    wkts         : List<String>,
    areaUnit     : com.act.geomapper.data.settings.AreaUnit     = com.act.geomapper.data.settings.AreaUnit.HECTARES,
    distanceUnit : com.act.geomapper.data.settings.DistanceUnit = com.act.geomapper.data.settings.DistanceUnit.METERS,
    conEtiquetas : Boolean = true
) {
    mapView.overlays.removeAll { it is SubparcelaPolygon }
    mapView.overlays.removeAll { it is Marker && (it as Marker).id == TAG_SUBPARCELA_LBL }
    // Ocultar etiquetas del polígono base para que no se dupliquen con las de las subparcelas
    mapView.overlays.removeAll { it is Marker && (it as Marker).id in setOf(TAG_DIST_LABEL, TAG_AREA_LABEL_PARC) }
    val reader = org.locationtech.jts.io.WKTReader()
    wkts.forEach { wkt ->
        runCatching {
            val geom     = reader.read(wkt)
            val centroid = geom.centroid.coordinate
            val coords   = geom.coordinates.map { GeoPoint(it.y, it.x) }

            SubparcelaPolygon(mapView).apply {
                infoWindow               = null
                points                   = coords
                outlinePaint.color       = NaranjaPoligono
                outlinePaint.strokeWidth = 3f
                fillPaint.color          = AColor.argb(60, 255, 255, 0)
                mapView.overlays.add(this)
            }

            if (conEtiquetas) {
                // ── Etiqueta de ÁREA en el centroide ──────────────────────
                val areaM2 = geom.area * 111_320.0 * (111_320.0 * Math.cos(Math.toRadians(4.0)))
                val areaHa = areaM2 / 10_000.0
                val textoArea = "%.2f".format(when (areaUnit) {
                    com.act.geomapper.data.settings.AreaUnit.SQUARE_METERS     -> areaM2
                    com.act.geomapper.data.settings.AreaUnit.SQUARE_KILOMETERS -> areaHa / 100
                    com.act.geomapper.data.settings.AreaUnit.HECTARES          -> areaHa
                }) + when (areaUnit) {
                    com.act.geomapper.data.settings.AreaUnit.SQUARE_METERS     -> " m²"
                    com.act.geomapper.data.settings.AreaUnit.SQUARE_KILOMETERS -> " km²"
                    com.act.geomapper.data.settings.AreaUnit.HECTARES          -> " ha"
                }
                ParcMinZoomMarker(mapView, 13.0).apply {
                    id       = TAG_SUBPARCELA_LBL
                    position = GeoPoint(centroid.y, centroid.x)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    icon     = crearChipArea(mapView.context, textoArea)
                    mapView.overlays.add(this)
                }

                // ── Etiquetas de DISTANCIA por lado ───────────────────────
                val raw = geom.coordinates
                for (i in 0 until raw.size - 1) {
                    val c1 = raw[i]; val c2 = raw[i + 1]
                    val distM = haversineParcM(c1.y, c1.x, c2.y, c2.x)
                    if (distM < 0.5) continue
                    val texto = when (distanceUnit) {
                        com.act.geomapper.data.settings.DistanceUnit.KILOMETERS -> "%.2f km".format(distM / 1000.0)
                        else -> if (distM >= 1000) "%.2f km".format(distM / 1000.0) else "%.1f m".format(distM)
                    }
                    val midLat = (c1.y + c2.y) / 2 + (centroid.y - (c1.y + c2.y) / 2) * 0.30
                    val midLon = (c1.x + c2.x) / 2 + (centroid.x - (c1.x + c2.x) / 2) * 0.30
                    ParcMinZoomMarker(mapView, 15.0).apply {
                        id       = TAG_SUBPARCELA_LBL
                        position = GeoPoint(midLat, midLon)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon     = crearChipNaranja(mapView.context, texto)
                        mapView.overlays.add(this)
                    }
                }
            }
        }
    }
    mapView.invalidate()
}

/** Dibuja todos los polígonos del proyecto como contexto. El seleccionado se resalta;
 *  los demás muestran su contorno y etiqueta de área para ser identificables. */
private fun dibujarPoligonosContexto(
    mapView      : MapView,
    poligonos    : List<com.act.geomapper.domain.models.Predio>,
    predioBaseId : Long?,
    areaUnit     : com.act.geomapper.data.settings.AreaUnit = com.act.geomapper.data.settings.AreaUnit.HECTARES
) {
    mapView.overlays.removeAll { it is ContextoPolygon }
    mapView.overlays.removeAll { it is Marker && (it as Marker).id in setOf(TAG_CONTEXTO_LBL, TAG_REF_POINT) }
    mapView.overlays.removeAll { it is Polyline && (it as Polyline).title == TAG_CONTEXTO }
    val reader = org.locationtech.jts.io.WKTReader()
    poligonos.forEach { predio ->
        val tipo = predio.geometry.geometryType
        // ── Puntos de referencia ──────────────────────────────────────────────
        if (tipo == "Point") {
            runCatching {
                val c = predio.geometry.coordinates.firstOrNull() ?: return@runCatching
                Marker(mapView).apply {
                    id       = TAG_REF_POINT
                    position = GeoPoint(c.y, c.x)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    icon     = crearPinAzul(mapView.context, predio.nombre)
                    infoWindow = null
                    mapView.overlays.add(this)
                }
            }
            return@forEach
        }
        // ── Líneas de referencia ──────────────────────────────────────────────
        if (tipo == "LineString") {
            runCatching {
                val coords = predio.geometry.coordinates.map { GeoPoint(it.y, it.x) }
                Polyline(mapView).apply {
                    title   = TAG_CONTEXTO
                    setPoints(coords)
                    outlinePaint.color       = AColor.argb(200, 0, 188, 212)
                    outlinePaint.strokeWidth = 3f
                    infoWindow = null
                    mapView.overlays.add(this)
                }
            }
            return@forEach
        }
        // ── Polígonos ─────────────────────────────────────────────────────────
        if (tipo !in listOf("Point", "LineString")) {
            runCatching {
                val wkt      = org.locationtech.jts.io.WKTWriter().write(predio.geometry)
                val geom     = reader.read(wkt)
                val coords   = geom.coordinates.map { GeoPoint(it.y, it.x) }
                val centroid = geom.centroid.coordinate
                val esSel    = predio.id == predioBaseId

                ContextoPolygon(mapView).apply {
                    infoWindow               = null
                    points                   = coords
                    outlinePaint.color       = if (esSel) AColor.parseColor("#FF6D00")
                                              else AColor.argb(200, 255, 109, 0)
                    outlinePaint.strokeWidth = if (esSel) 6f else 3f
                    fillPaint.color          = if (esSel) AColor.argb(60, 255, 109, 0)
                                              else AColor.argb(30, 255, 109, 0)
                    mapView.overlays.add(0, this)  // detrás de todo
                }

                // Etiqueta de área para polígonos NO seleccionados (el seleccionado ya la tiene en dibujarPoligonoBase)
                if (!esSel) {
                    val areaM2 = geom.area * 111_320.0 * (111_320.0 * Math.cos(Math.toRadians(centroid.y)))
                    val areaHa = areaM2 / 10_000.0
                    val textoArea = "%.2f".format(when (areaUnit) {
                        com.act.geomapper.data.settings.AreaUnit.SQUARE_METERS     -> areaM2
                        com.act.geomapper.data.settings.AreaUnit.SQUARE_KILOMETERS -> areaHa / 100
                        com.act.geomapper.data.settings.AreaUnit.HECTARES          -> areaHa
                    }) + when (areaUnit) {
                        com.act.geomapper.data.settings.AreaUnit.SQUARE_METERS     -> " m²"
                        com.act.geomapper.data.settings.AreaUnit.SQUARE_KILOMETERS -> " km²"
                        com.act.geomapper.data.settings.AreaUnit.HECTARES          -> " ha"
                    }
                    ParcMinZoomMarker(mapView, 13.0).apply {
                        id       = TAG_CONTEXTO_LBL
                        position = GeoPoint(centroid.y, centroid.x)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon     = crearChipArea(mapView.context, textoArea)
                        mapView.overlays.add(this)
                    }
                }
            }
        }
    }
    mapView.invalidate()
}

/** Pin azul pequeño con etiqueta del nombre, para puntos de referencia en Parcelación */
private fun crearPinAzul(context: Context, nombre: String): android.graphics.drawable.BitmapDrawable {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize  = 24f
        color     = AColor.WHITE
        typeface  = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    val txtCorto = if (nombre.length > 12) nombre.take(11) + "…" else nombre
    val textW = paint.measureText(txtCorto)
    val padH = 10f; val padV = 6f
    val chipW = (textW + padH * 2).toInt()
    val chipH = (paint.textSize + padV * 2).toInt()
    val pinH  = 14
    val w     = chipW.coerceAtLeast(24)
    val h     = chipH + pinH

    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c   = Canvas(bmp)
    val bgP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AColor.parseColor("#1565C0") }
    c.drawRoundRect(0f, 0f, w.toFloat(), chipH.toFloat(), chipH / 2f, chipH / 2f, bgP)
    c.drawText(txtCorto, w / 2f, chipH - padV - 2f, paint)
    // Triángulo puntero
    val path = android.graphics.Path().apply {
        moveTo(w / 2f - 7f, chipH.toFloat())
        lineTo(w / 2f + 7f, chipH.toFloat())
        lineTo(w / 2f, h.toFloat())
        close()
    }
    c.drawPath(path, bgP)

    return android.graphics.drawable.BitmapDrawable(context.resources, bmp)
}

// Crea un Bitmap con chip naranja y texto blanco para usar como icono de Marker
private fun crearChipNaranja(context: Context, texto: String): android.graphics.drawable.BitmapDrawable {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize  = 28f
        color     = AColor.WHITE
        typeface  = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    val textW = paint.measureText(texto)
    val padH  = 14f; val padV = 8f
    val w     = (textW + padH * 2).toInt()
    val h     = (paint.textSize + padV * 2).toInt()

    val bmp  = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c    = Canvas(bmp)
    val bgP  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = NaranjaPoligono }
    c.drawRoundRect(0f, 0f, w.toFloat(), h.toFloat(), h / 2f, h / 2f, bgP)
    c.drawText(texto, w / 2f, h - padV - 4f, paint)

    return android.graphics.drawable.BitmapDrawable(context.resources, bmp)
}
