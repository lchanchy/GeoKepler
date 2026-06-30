package com.act.geomapper

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.act.geomapper.data.database.AppDatabase
import com.act.geomapper.data.database.PredioRepositoryImpl
import com.act.geomapper.data.database.ProyectoRepositoryImpl
import com.act.geomapper.data.geometry.GeometryService
import com.act.geomapper.data.gps.GpsService
import com.act.geomapper.data.settings.SettingsRepository
import com.act.geomapper.service.GeoKeplerService
import com.act.geomapper.domain.usecase.*
import com.act.geomapper.domain.usecase.ObtenerTodosPrediosUseCase
import com.act.geomapper.presentation.components.*
import com.act.geomapper.presentation.screens.*
import com.act.geomapper.presentation.viewmodels.*
import com.act.geomapper.security.RootDetector
import com.act.geomapper.ui.theme.GeoMapperTheme

class MainActivity : ComponentActivity() {

    // ── Dependencias manuales (YAGNI — sin Hilt) ─────────────────────────────
    private val db              by lazy { AppDatabase.getInstance(this) }
    private val gpsService      by lazy { GpsService(this) }
    private val geometryService by lazy { GeometryService() }
    private val proyectoRepo    by lazy { ProyectoRepositoryImpl(db.proyectoDao()) }
    private val predioRepo      by lazy { PredioRepositoryImpl(db.predioDao()) }
    private val settingsRepo    by lazy { SettingsRepository.getInstance(this) }

    private val proyectoViewModel by lazy {
        ProyectoViewModel(
            ObtenerProyectosUseCase(proyectoRepo),
            CrearProyectoUseCase(proyectoRepo),
            EliminarProyectoUseCase(proyectoRepo)
        )
    }
    private val mapViewModel by lazy { MapViewModel(gpsService, geometryService, applicationContext) }
    private val settingsViewModel by lazy { SettingsViewModel(settingsRepo) }
    private val parcelacionViewModel by lazy {
        ParcelacionViewModel(geometryService, GuardarPredioUseCase(predioRepo), EliminarPredioUseCase(predioRepo))
    }
    private val predioViewModel by lazy {
        PredioViewModel(
            ObtenerPrediosUseCase(predioRepo),
            ObtenerTodosPrediosUseCase(predioRepo),
            GuardarPredioUseCase(predioRepo),
            EliminarPredioUseCase(predioRepo),
            geometryService
        )
    }
    private val importViewModel by lazy {
        ImportViewModel(GuardarPredioUseCase(predioRepo), geometryService)
    }

    private val permisosLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permisos ->
        if (permisos[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            mapViewModel.iniciarGps()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Desactiva el system splash de Android 12+ antes de que dibuje nada
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()   // la app dibuja bajo status bar y nav bar

        permisosLauncher.launch(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ))

        // Iniciar ForegroundService para mantener GPS vivo en background
        GeoKeplerService.iniciar(this)

        // SAF file picker para importar
        val safLauncher = registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            uri?.let { importViewModel.parsearArchivo(this, it) }
        }

        // Archivo abierto directamente (app estaba cerrada) desde WhatsApp / Files / etc.
        if (intent?.action == android.content.Intent.ACTION_VIEW) {
            intent.data?.let { importViewModel.parsearArchivo(this, it) }
        }

        // Observar cambios de idioma fuera del Compose para aplicar locale
        settingsViewModel.settings
            .map { it.language }
            .distinctUntilChanged()
            .onEach { lang ->
                val tag = if (lang == com.act.geomapper.data.settings.AppLanguage.ENGLISH) "en" else "es"
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
            }
            .launchIn(lifecycleScope)

        setContent {
            val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
            var mostrarAvisoRoot by remember { mutableStateOf(RootDetector.isRooted()) }

            GeoMapperTheme(oscuro = settings.darkMode, idioma = settings.language) {
                if (mostrarAvisoRoot) {
                    AlertDialog(
                        onDismissRequest = { mostrarAvisoRoot = false },
                        title   = { Text("Dispositivo con permisos root") },
                        text    = { Text("Se detectaron permisos root en este dispositivo. GeoKepler puede continuar, pero los datos no están garantizados como seguros en este entorno.") },
                        confirmButton = {
                            TextButton(onClick = { mostrarAvisoRoot = false }) { Text("Entendido") }
                        }
                    )
                }
                AppRoot(
                    proyectoVM    = proyectoViewModel,
                    mapVM         = mapViewModel,
                    settingsVM    = settingsViewModel,
                    predioVM      = predioViewModel,
                    parcelacionVM = parcelacionViewModel,
                    importVM      = importViewModel,
                    onAbrirPicker = {
                        safLauncher.launch(arrayOf(
                            "application/json", "*/*",
                            "application/vnd.google-earth.kml+xml",
                            "application/gpx+xml", "application/zip", "image/tiff"
                        ))
                    }
                )
            }
        }
    }

    // Recibe archivo abierto desde WhatsApp / Archivos cuando la app ya está corriendo
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        if (intent.action == android.content.Intent.ACTION_VIEW) {
            intent.data?.let { importViewModel.parsearArchivo(this, it) }
        }
    }

    override fun onPause() {
        super.onPause()
        // Persistir proyecto activo para restaurar si Android mata el proceso
        val proyId = proyectoViewModel.uiState.value.proyectoSeleccionadoId
        getSharedPreferences("app_state", MODE_PRIVATE).edit()
            .putLong("proyecto_activo_id", proyId ?: -1L)
            .apply()
        // Actualizar nombre del proyecto en la notificación del servicio
        val nombre = proyectoViewModel.uiState.value.proyectos
            .firstOrNull { it.id == proyId }?.nombre ?: ""
        GeoKeplerService.iniciar(this, nombre)
    }

    override fun onResume() {
        super.onResume()
        // Restaurar proyecto activo si fue guardado en onPause
        val proyId = getSharedPreferences("app_state", MODE_PRIVATE)
            .getLong("proyecto_activo_id", -1L)
        if (proyId != -1L) {
            proyectoViewModel.seleccionar(proyId)
            predioViewModel.cargarPredios(proyId)
        }
    }

    override fun onSaveInstanceState(outState: android.os.Bundle) {
        super.onSaveInstanceState(outState)
        val mapState = mapViewModel.uiState.value
        outState.putString("captura_modo",   mapState.modoCaptura.name)
        outState.putBoolean("captura_activa", mapState.modoCaptura != com.act.geomapper.presentation.viewmodels.ModoCaptura.NINGUNO)
        outState.putLong("proyecto_id",
            proyectoViewModel.uiState.value.proyectoSeleccionadoId ?: -1L)
    }
}

// ── Rutas de navegación simple (sin Nav Component para evitar overhead) ───────
enum class Pantalla { SPLASH, MAPA, CONFIGURACION, PARCELACION }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot(
    proyectoVM   : ProyectoViewModel,
    mapVM        : MapViewModel,
    settingsVM   : SettingsViewModel,
    predioVM     : PredioViewModel,
    parcelacionVM: ParcelacionViewModel,
    importVM     : ImportViewModel,
    onAbrirPicker: () -> Unit
) {
    var pantalla            by remember { mutableStateOf(Pantalla.SPLASH) }
    var basemapActual       by remember { mutableStateOf<com.act.geomapper.presentation.components.Basemap>(com.act.geomapper.presentation.components.Basemap.OSM) }
    var predioParaParcelar  by remember { mutableStateOf<com.act.geomapper.domain.models.Predio?>(null) }

    AnimatedContent(
        targetState    = pantalla,
        transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(300)) },
        label          = "nav"
    ) { dest ->
        when (dest) {
            Pantalla.SPLASH        -> SplashScreen { pantalla = Pantalla.MAPA }
            Pantalla.CONFIGURACION -> SettingsScreen(settingsVM) { pantalla = Pantalla.MAPA }
            Pantalla.PARCELACION   -> {
                val predioState  by predioVM.uiState.collectAsStateWithLifecycle()
                val proyState    by proyectoVM.uiState.collectAsStateWithLifecycle()
                val settings     by settingsVM.settings.collectAsStateWithLifecycle()
                val mapStateParc by mapVM.uiState.collectAsStateWithLifecycle()
                ParcelacionScreen(
                    vm            = parcelacionVM,
                    poligonos     = predioState.predios,
                    proyectoId    = proyState.proyectoSeleccionadoId ?: 1L,
                    basemapActual = basemapActual,
                    predioInicial = predioParaParcelar,
                    estadoGps     = mapStateParc.estadoGps,
                    areaUnit      = settings.areaUnit,
                    distanceUnit  = settings.distanceUnit,
                    onBack        = { pantalla = Pantalla.MAPA; predioParaParcelar = null }
                )
            }
            Pantalla.MAPA          -> MapaApp(
                proyectoVM, mapVM, settingsVM, predioVM, importVM, onAbrirPicker,
                basemapActual   = basemapActual,
                onBasemapChange = { basemapActual = it },
                onParcelar      = { predio -> predioParaParcelar = predio; pantalla = Pantalla.PARCELACION }
            ) { pantalla = it }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MapaApp(
    proyectoVM      : ProyectoViewModel,
    mapVM           : MapViewModel,
    settingsVM      : SettingsViewModel,
    predioVM        : PredioViewModel,
    importVM        : ImportViewModel,
    onAbrirPicker   : () -> Unit,
    basemapActual   : com.act.geomapper.presentation.components.Basemap,
    onBasemapChange : (com.act.geomapper.presentation.components.Basemap) -> Unit,
    onParcelar      : (com.act.geomapper.domain.models.Predio) -> Unit,
    navegar         : (Pantalla) -> Unit
) {
    val proyState   by proyectoVM.uiState.collectAsStateWithLifecycle()
    val mapState    by mapVM.uiState.collectAsStateWithLifecycle()
    val settings    by settingsVM.settings.collectAsStateWithLifecycle()
    val predioState by predioVM.uiState.collectAsStateWithLifecycle()
    val strings     = com.act.geomapper.ui.theme.LocalStrings.current  // para usar en LaunchedEffect
    val importState by importVM.state.collectAsStateWithLifecycle()

    val proyectoActivo = proyState.proyectos.firstOrNull { it.id == proyState.proyectoSeleccionadoId }

    var fabExpandido        by remember { mutableStateOf(false) }
    var mostrarNuevoProy    by remember { mutableStateOf(false) }
    var mostrarListaProy    by remember { mutableStateOf(false) }

    var mostrarCapas             by remember { mutableStateOf(false) }
    var entidadParaExportar      by remember { mutableStateOf<com.act.geomapper.domain.models.Predio?>(null) }
    var proyectoParaExportar     by remember { mutableStateOf<com.act.geomapper.domain.models.Proyecto?>(null) }
    var mostrarImport  by remember { mutableStateOf(false) }
    var mostrarBasemap by remember { mutableStateOf(false) }

    // Capas siempre muestra TODAS las entidades — independiente del proyecto activo
    LaunchedEffect(Unit) { predioVM.cargarTodos() }

    // Int primitivo como clave estable: Compose GARANTIZA recomposición cuando cambia.
    // Esto fuerza que MapScreen redibuje el mapa al eliminar/añadir entidades.
    var mapRedrawVersion by remember { mutableIntStateOf(0) }
    LaunchedEffect(predioState.predios) { mapRedrawVersion++ }

    // Auto-guardar al finalizar captura. Usa proyectoSeleccionadoId como clave adicional:
    // si cuando llega wktResultado aún no hay proyecto (race condition al arrancar),
    // el efecto se re-ejecuta en cuanto el proyecto quede disponible.
    LaunchedEffect(mapState.wktResultado, proyState.proyectoSeleccionadoId) {
        val wkt = mapState.wktResultado ?: return@LaunchedEffect
        // Fallback a proyecto 1 ("Sin proyecto") — siempre existe por DB callback
        val pId = proyState.proyectoSeleccionadoId ?: 1L

        // Nombre automático en el idioma activo
        val tipoGeo = when {
            wkt.startsWith("POINT")      -> "Point"
            wkt.startsWith("LINESTRING") -> "LineString"
            else                         -> "Polygon"
        }
        val tipoLabel = when (tipoGeo) {
            "Point"      -> strings.punto
            "LineString" -> strings.linea
            else         -> strings.poligono
        }
        val conteo = predioState.predios.count { p ->
            when (tipoGeo) {
                "Point"      -> p.geometry.geometryType == "Point"
                "LineString" -> p.geometry.geometryType == "LineString"
                else         -> p.geometry.geometryType !in listOf("Point", "LineString")
            }
        }
        val autoNombre = "$tipoLabel ${conteo + 1}"

        predioVM.guardar(pId, autoNombre, geometryWKT = wkt)
        // No llamar cargarPredios aquí — cargarTodos() es reactivo, Room emite solo
        mapVM.limpiarResultado()
    }

    // Preview de importación: confirmar automáticamente si hay proyecto activo
    LaunchedEffect(importState.preview) {
        if (importState.preview.isNotEmpty() && proyState.proyectoSeleccionadoId != null) {
            importVM.confirmarImportacion(proyState.proyectoSeleccionadoId!!)
        }
    }

    var mostrarCoordInput by remember { mutableStateOf(false) }

    // Layout sin Scaffold — mapa ocupa toda la pantalla, header y panel se superponen
    Box(modifier = Modifier.fillMaxSize()) {

        // Mapa de fondo (toda la pantalla)
        MapScreen(
            viewModel        = mapVM,
            settings         = settings,
            basemapActual    = basemapActual,
            predios          = predioState.predios,
            ocultos          = predioState.ocultos,
            redrawVersion    = mapRedrawVersion,
            onGuardarEdicion = { id, geom -> predioVM.actualizarGeometria(id, geom) },
            modifier         = Modifier.fillMaxSize()
        )

        // Header card superior
        HeaderCard(
            estadoGps      = mapState.estadoGps,
            proyectoNombre = proyectoActivo?.nombre,
            onProyectos    = { mostrarListaProy = true },
            onCapas        = { mostrarCapas = true },
            onBase         = { mostrarBasemap = !mostrarBasemap },
            onImportar     = { mostrarImport = true },
            onConfig       = { navegar(Pantalla.CONFIGURACION) },
            modifier       = Modifier.align(Alignment.TopCenter)
        )

        // Panel de basemap — se abre con el botón BaseMap del header, sin botón propio
        if (mostrarBasemap) {
            com.act.geomapper.presentation.components.BasemapPanel(
                seleccionado  = basemapActual,
                onSeleccionar = { onBasemapChange(it) },
                onDismiss     = { mostrarBasemap = false },
                modifier      = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 128.dp, end = 8.dp)
            )
        }

        // ── FAB inferior derecho: abre opciones de captura ───────────────
        val win = com.act.geomapper.ui.theme.rememberWindowInfo()
        if (mapState.modoCaptura == ModoCaptura.NINGUNO) {
            Column(
                modifier              = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 16.dp, bottom = win.fabBottomPad),
                horizontalAlignment   = Alignment.End,
                verticalArrangement   = Arrangement.spacedBy(10.dp)
            ) {
                // Opciones expandidas: Punto | Línea | Polígono
                AnimatedVisibility(
                    visible = fabExpandido,
                    enter   = fadeIn() + slideInVertically { it },
                    exit    = fadeOut() + slideOutVertically { it }
                ) {
                    val s = com.act.geomapper.ui.theme.LocalStrings.current
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OpcionCaptura("⬡  ${s.poligono}", Color(0xFF1B5E20)) {
                            mapVM.iniciarCaptura(ModoCaptura.POLIGONO); fabExpandido = false
                        }
                        OpcionCaptura("〰  ${s.linea}", Color(0xFF0D47A1)) {
                            mapVM.iniciarCaptura(ModoCaptura.LINEA); fabExpandido = false
                        }
                        OpcionCaptura("📍  ${s.punto}", Color(0xFF00838F)) {
                            mapVM.iniciarCaptura(ModoCaptura.PUNTO); fabExpandido = false
                        }
                    }
                }

                // FAB principal
                FloatingActionButton(
                    onClick        = { fabExpandido = !fabExpandido },
                    containerColor = Color(0xFF00838F),
                    shape          = androidx.compose.foundation.shape.CircleShape
                ) {
                    Icon(
                        if (fabExpandido) Icons.Default.Close else Icons.Default.AddLocation,
                        "Capturar", tint = Color.White
                    )
                }
            }
        }
    }

    // ── Sheets ────────────────────────────────────────────────────────────────
    if (mostrarNuevoProy) {
        NuevoProyectoSheet(
            onDismiss = { mostrarNuevoProy = false },
            onCrear   = { nombre, desc -> proyectoVM.crear(nombre, desc) }
        )
    }

    if (mostrarListaProy) {
        ListaProyectosSheet(
            proyectos        = proyState.proyectos.filter { it.id != 1L }, // ocultar "Sin proyecto"
            proyectoActivoId = proyState.proyectoSeleccionadoId,
            onSeleccionar    = { proyectoVM.seleccionar(it) }, // cargarTodos() ya está activo
            onEliminar       = proyectoVM::eliminar,
            onExportar       = { proy -> proyectoParaExportar = proy },
            onNuevo          = { mostrarNuevoProy = true },
            onDismiss        = { mostrarListaProy = false }
        )
    }

    if (mostrarCapas) {
        LayersBottomSheet(
            predios             = predioState.predios,
            ocultos             = predioState.ocultos,
            areaUnit            = settings.areaUnit,
            distanceUnit        = settings.distanceUnit,
            onToggleVisibilidad = predioVM::toggleVisibilidad,
            onEliminar          = predioVM::eliminar,
            onRenombrar         = predioVM::renombrar,
            onCentrarEn         = { wkt ->
                runCatching {
                    val geom = com.act.geomapper.data.geometry.GeometryService().wktAGeometria(wkt)
                    val c    = geom.centroid.coordinate
                    mapVM.centrarEnCoordenada(c.y, c.x)
                }
            },
            onEditarGeometria   = { predio ->
                mostrarCapas = false
                mapVM.iniciarEdicionGeometria(predio)
            },
            onExportarEntidad   = { predio ->
                entidadParaExportar = predio
                mostrarCapas = false
            },
            onParcelar          = { predio ->
                mostrarCapas = false
                onParcelar(predio)
            },
            onEliminarTodos       = { predioState.predios.forEach { predioVM.eliminar(it.id) } },
            onToggleTodosVisibles = predioVM::toggleTodosVisibles,
            onEditarAtributos     = predioVM::actualizarAtributos,
            onNavegar             = { lat, lon -> mapVM.iniciarNavegacion(lat, lon) },
            onDismiss             = { mostrarCapas = false }
        )
    }

    // Exportar entidad individual (desde Capas)
    entidadParaExportar?.let { predio ->
        ExportBottomSheet(
            proyecto  = proyectoActivo?.copy(nombre = predio.nombre),
            predios   = listOf(predio),
            onDismiss = { entidadParaExportar = null }
        )
    }

    // Exportar proyecto completo (desde Lista de Proyectos)
    proyectoParaExportar?.let { proy ->
        val prediosDelProyecto = predioState.predios.filter { it.proyectoId == proy.id }
        ExportBottomSheet(
            proyecto  = proy,
            predios   = prediosDelProyecto,
            onDismiss = { proyectoParaExportar = null }
        )
    }

    if (mostrarImport) {
        val ctx = androidx.compose.ui.platform.LocalContext.current
        ImportBottomSheet(
            rellenoPoligonos     = settings.rellenoPoligonos,
            onRellenoChange      = settingsVM::setRellenoPoligonos,
            onArchivoSeleccionado = { uri ->
                importVM.parsearArchivo(ctx, uri)
                mostrarImport = false
            },
            onIngresarCoordenada = { mostrarCoordInput = true },
            onDismiss            = { mostrarImport = false }
        )
    }

    if (mostrarCoordInput) {
        CoordInputDialog(
            coordFormat = settings.coordFormat,
            onDismiss   = { mostrarCoordInput = false },
            onConfirm   = { punto ->
                mapVM.iniciarCaptura(ModoCaptura.PUNTO)
                mapVM.capturarPuntoManual(punto)
            }
        )
    }

    // Toast de importación completada
    if (importState.guardadas > 0) {
        LaunchedEffect(importState.guardadas) {
            importVM.limpiar() // cargarTodos() reactivo — Room emite automáticamente
        }
    }
}

@Composable
private fun FabOpcion(icon: ImageVector, label: String, onClick: () -> Unit) {
    ExtendedFloatingActionButton(
        onClick          = onClick,
        icon             = { Icon(icon, label, modifier = Modifier.size(18.dp)) },
        text             = { Text(label, fontSize = 13.sp) },
        containerColor   = Color(0xCC1A1A1A),
        contentColor     = Color.White,
        elevation        = FloatingActionButtonDefaults.elevation(0.dp, 0.dp)
    )
}

// ponytail: opción de captura expandida — etiqueta con emoji + color propio
@Composable
private fun OpcionCaptura(label: String, color: Color, onClick: () -> Unit) {
    ExtendedFloatingActionButton(
        onClick          = onClick,
        text             = { Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold) },
        icon             = {},
        containerColor   = color,
        contentColor     = Color.White,
        elevation        = FloatingActionButtonDefaults.elevation(4.dp, 4.dp)
    )
}
