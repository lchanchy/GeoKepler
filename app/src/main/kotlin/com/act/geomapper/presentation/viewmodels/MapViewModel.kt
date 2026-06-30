package com.act.geomapper.presentation.viewmodels

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.act.geomapper.data.geometry.GeometryService
import com.act.geomapper.data.gps.GpsService
import com.act.geomapper.domain.models.EstadoGps
import com.act.geomapper.domain.models.Predio
import com.act.geomapper.domain.models.PuntoGps
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class ModoCaptura { NINGUNO, PUNTO, LINEA, POLIGONO }

data class MapUiState(
    val estadoGps           : EstadoGps      = EstadoGps(),
    val modoCaptura         : ModoCaptura    = ModoCaptura.NINGUNO,
    val puntosCapturados    : List<PuntoGps> = emptyList(),
    val entidadesWkt        : List<String>   = emptyList(),
    val wktResultado        : String?        = null,
    val areaHa              : Double         = 0.0,
    val azimut              : Float          = 0f,
    val centrarEnGps        : Boolean        = false,
    val capturaRecuperada   : Int            = 0,
    val editingPredio       : Predio?        = null,
    val navegacionDestino   : PuntoGps?      = null,
    val error               : String?        = null
)

class MapViewModel(
    private val gpsService     : GpsService,
    private val geometryService: GeometryService,
    context                    : Context          // ponytail: Application context para SharedPrefs
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    // ponytail: SharedPreferences es más simple que DataStore para datos pequeños de captura
    private val prefs: SharedPreferences =
        context.getSharedPreferences("capture_state", Context.MODE_PRIVATE)

    private var gpsJob: Job? = null

    init {
        restaurarCapturaGuardada()
    }

    // ── GPS ──────────────────────────────────────────────────────────────────

    fun iniciarGps() {
        if (gpsJob?.isActive == true) return
        gpsJob = viewModelScope.launch {
            gpsService.iniciar()
                .catch { e -> _uiState.update { it.copy(error = e.message) } }
                .collect { _ ->
                    _uiState.update { it.copy(estadoGps = gpsService.estado.value) }
                }
        }
    }

    fun detenerGps() { gpsJob?.cancel(); gpsJob = null }

    fun centrarEnUbicacion() = _uiState.update { it.copy(centrarEnGps = true) }
    fun centradoConsumido()  = _uiState.update { it.copy(centrarEnGps = false) }

    fun centrarEnCoordenada(lat: Double, lon: Double) {
        _uiState.update { it.copy(
            estadoGps    = it.estadoGps.copy(puntoActual = PuntoGps(lat, lon)),
            centrarEnGps = true
        ) }
    }

    fun actualizarAzimut(azimut: Float) = _uiState.update { it.copy(azimut = azimut) }

    // ── Captura ──────────────────────────────────────────────────────────────

    fun iniciarCaptura(modo: ModoCaptura) {
        _uiState.update { it.copy(
            modoCaptura      = modo,
            puntosCapturados = emptyList(),
            wktResultado     = null,
            areaHa           = 0.0
        ) }
        guardarCapturaEnCurso()
    }

    fun capturarPuntoGps() {
        val punto = _uiState.value.estadoGps.puntoActual ?: run {
            _uiState.update { it.copy(error = "Esperando señal GPS…") }
            return
        }
        capturarPuntoManual(punto)
    }

    fun capturarPuntoManual(punto: PuntoGps) {
        if (_uiState.value.modoCaptura == ModoCaptura.NINGUNO) return
        _uiState.update { s -> s.copy(puntosCapturados = s.puntosCapturados + punto) }
        guardarCapturaEnCurso()   // persistir inmediatamente
        if (_uiState.value.modoCaptura == ModoCaptura.PUNTO) finalizarCaptura()
    }

    fun finalizarCaptura() {
        val state  = _uiState.value
        val puntos = state.puntosCapturados
        runCatching {
            when (state.modoCaptura) {
                ModoCaptura.PUNTO -> {
                    val geom = geometryService.crearPunto(puntos.last())
                    geometryService.geometriaAWkt(geom) to 0.0
                }
                ModoCaptura.LINEA -> {
                    require(puntos.size >= 2) { "Se necesitan al menos 2 puntos para una línea" }
                    geometryService.geometriaAWkt(geometryService.crearLinea(puntos)) to 0.0
                }
                ModoCaptura.POLIGONO -> {
                    require(puntos.size >= 3) { "Se necesitan al menos 3 puntos para un polígono" }
                    val geom = geometryService.crearPoligono(puntos)
                    val wkt  = geometryService.geometriaAWkt(geom)
                    wkt to geometryService.calcularAreaHa(wkt)
                }
                ModoCaptura.NINGUNO -> return
            }
        }.onSuccess { (wkt, area) ->
            _uiState.update { s ->
                s.copy(
                    wktResultado     = wkt,
                    areaHa           = area,
                    modoCaptura      = ModoCaptura.NINGUNO,
                    puntosCapturados = emptyList(),
                    entidadesWkt     = s.entidadesWkt + wkt
                )
            }
            limpiarCapturaGuardada()
        }.onFailure { e ->
            _uiState.update { it.copy(error = e.message) }
        }
    }

    fun cancelarCaptura() {
        _uiState.update { it.copy(modoCaptura = ModoCaptura.NINGUNO, puntosCapturados = emptyList()) }
        limpiarCapturaGuardada()
    }

    // ponytail: borra el último punto en curso sin cancelar la captura completa
    fun undoUltimoPunto() {
        val puntos = _uiState.value.puntosCapturados
        if (puntos.isEmpty()) return
        _uiState.update { it.copy(puntosCapturados = puntos.dropLast(1)) }
        guardarCapturaEnCurso()
    }

    fun descartarRecuperacion() {
        _uiState.update { it.copy(capturaRecuperada = 0) }
        limpiarCapturaGuardada()
    }

    fun confirmarRecuperacion() {
        // Los puntos ya están restaurados en el estado — solo limpiar el aviso
        _uiState.update { it.copy(capturaRecuperada = 0) }
    }

    fun limpiarResultado() = _uiState.update { it.copy(wktResultado = null, areaHa = 0.0) }
    fun limpiarError()     = _uiState.update { it.copy(error = null) }

    // ── Edición de geometría ──────────────────────────────────────────────────
    fun iniciarEdicionGeometria(predio: Predio) {
        _uiState.update { it.copy(editingPredio = predio, modoCaptura = ModoCaptura.NINGUNO) }
    }

    fun finalizarEdicion() {
        _uiState.update { it.copy(editingPredio = null) }
    }

    // ── Navegación a punto ────────────────────────────────────────────────────
    fun iniciarNavegacion(lat: Double, lon: Double) {
        _uiState.update { it.copy(navegacionDestino = PuntoGps(lat, lon)) }
    }

    fun detenerNavegacion() {
        _uiState.update { it.copy(navegacionDestino = null) }
    }

    // ── Persistencia de captura en curso ──────────────────────────────────────

    private fun guardarCapturaEnCurso() {
        val state  = _uiState.value
        val puntos = state.puntosCapturados
            .joinToString(";") { "${it.latitud},${it.longitud}" }
        // ponytail: commit() síncrono intencional — necesitamos que persista ANTES
        // de que el proceso pueda morir por una llamada entrante
        prefs.edit()
            .putString("modo", state.modoCaptura.name)
            .putString("puntos", puntos)
            .putBoolean("activa", true)
            .commit()
    }

    private fun limpiarCapturaGuardada() {
        prefs.edit()
            .putBoolean("activa", false)
            .putString("puntos", "")
            .putString("modo", "NINGUNO")
            .apply()
    }

    private fun restaurarCapturaGuardada() {
        if (!prefs.getBoolean("activa", false)) return

        val modoStr = prefs.getString("modo", "NINGUNO") ?: "NINGUNO"
        val modo    = runCatching { ModoCaptura.valueOf(modoStr) }.getOrDefault(ModoCaptura.NINGUNO)
        if (modo == ModoCaptura.NINGUNO) return

        val puntosStr = prefs.getString("puntos", "") ?: ""
        if (puntosStr.isBlank()) return

        val puntos = puntosStr.split(";").mapNotNull { par ->
            val coords = par.split(",")
            if (coords.size == 2) {
                val lat = coords[0].toDoubleOrNull()
                val lon = coords[1].toDoubleOrNull()
                if (lat != null && lon != null) PuntoGps(lat, lon) else null
            } else null
        }

        if (puntos.isEmpty()) return

        // Restaurar estado y señalizar que hay recuperación pendiente
        _uiState.update { it.copy(
            modoCaptura      = modo,
            puntosCapturados = puntos,
            capturaRecuperada = puntos.size
        ) }
    }

    override fun onCleared() { detenerGps(); super.onCleared() }
}
