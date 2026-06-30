package com.act.geomapper.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.act.geomapper.data.geometry.GeometryService
import com.act.geomapper.domain.models.Predio
import com.act.geomapper.domain.models.TipoPredio
import com.act.geomapper.domain.usecase.EliminarPredioUseCase
import com.act.geomapper.domain.usecase.GuardarPredioUseCase
import com.act.geomapper.domain.usecase.ObtenerPrediosUseCase
import com.act.geomapper.domain.usecase.ObtenerTodosPrediosUseCase
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// ponytail: visibilidad en memoria, no persiste — suficiente para sesión
data class PredioUiState(
    val predios    : List<Predio>     = emptyList(),
    val ocultos    : Set<Long>        = emptySet(),   // ids con visibilidad OFF
    val cargando   : Boolean          = false,
    val error      : String?          = null
)

class PredioViewModel(
    private val obtenerPredios    : ObtenerPrediosUseCase,
    private val obtenerTodosPredios: ObtenerTodosPrediosUseCase,
    private val guardarPredio     : GuardarPredioUseCase,
    private val eliminarPredio    : EliminarPredioUseCase,
    private val geometryService   : GeometryService
) : ViewModel() {

    private val _uiState = MutableStateFlow(PredioUiState())
    val uiState: StateFlow<PredioUiState> = _uiState.asStateFlow()

    private var cargarJob: kotlinx.coroutines.Job? = null
    private var proyectoIdActual: Long = -1L

    /** Carga TODOS los predios sin importar proyecto — fuente de verdad para el panel Capas. */
    fun cargarTodos() {
        if (proyectoIdActual == 0L && cargarJob?.isActive == true) return
        proyectoIdActual = 0L
        cargarJob?.cancel()
        cargarJob = viewModelScope.launch {
            obtenerTodosPredios()
                .catch { e -> _uiState.update { it.copy(error = e.message, cargando = false) } }
                .collect { lista -> _uiState.update { it.copy(predios = lista, cargando = false) } }
        }
    }

    fun cargarPredios(proyectoId: Long) {
        if (proyectoId == proyectoIdActual && cargarJob?.isActive == true) return
        proyectoIdActual = proyectoId
        cargarJob?.cancel()
        cargarJob = viewModelScope.launch {
            obtenerPredios(proyectoId)
                .catch { e -> _uiState.update { it.copy(error = e.message, cargando = false) } }
                .collect { lista -> _uiState.update { it.copy(predios = lista, cargando = false) } }
        }
    }

    fun guardar(
        proyectoId : Long,
        nombre     : String,
        propietario: String = "",
        geometryWKT: String,
        tipo       : TipoPredio = TipoPredio.RURAL
    ) {
        viewModelScope.launch {
            runCatching {
                val geom     = geometryService.wktAGeometria(geometryWKT)
                val area     = geometryService.calcularAreaHa(geometryWKT)
                val perimetro = geometryService.calcularPerimetroM(geometryWKT)
                guardarPredio(Predio(proyectoId = proyectoId, nombre = nombre,
                    propietario = propietario, geometry = geom,
                    area = area, perimetro = perimetro, tipo = tipo))
            }.onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun eliminar(id: Long) {
        viewModelScope.launch {
            runCatching { eliminarPredio(id) }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun renombrar(id: Long, nuevoNombre: String) {
        viewModelScope.launch {
            val predio = _uiState.value.predios.firstOrNull { it.id == id } ?: return@launch
            runCatching { guardarPredio(predio.copy(nombre = nuevoNombre)) }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun actualizarAtributos(id: Long, nombre: String, propietario: String) {
        viewModelScope.launch {
            val predio = _uiState.value.predios.firstOrNull { it.id == id } ?: return@launch
            runCatching { guardarPredio(predio.copy(nombre = nombre, propietario = propietario)) }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    /** Reemplaza la geometría de una entidad existente y recalcula área/perímetro. */
    fun actualizarGeometria(id: Long, nuevaGeom: org.locationtech.jts.geom.Geometry) {
        viewModelScope.launch {
            val predio = _uiState.value.predios.firstOrNull { it.id == id } ?: return@launch
            val wkt    = org.locationtech.jts.io.WKTWriter().write(nuevaGeom)
            runCatching {
                guardarPredio(predio.copy(
                    geometry  = nuevaGeom,
                    area      = geometryService.calcularAreaHa(wkt),
                    perimetro = geometryService.calcularPerimetroM(wkt)
                ))
            }.onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun toggleVisibilidad(id: Long) {
        _uiState.update { s ->
            val ocultos = if (id in s.ocultos) s.ocultos - id else s.ocultos + id
            s.copy(ocultos = ocultos)
        }
    }

    fun toggleTodosVisibles() {
        _uiState.update { s ->
            val todosOcultos = s.predios.all { it.id in s.ocultos }
            val nuevos = if (todosOcultos) emptySet() else s.predios.map { it.id }.toSet()
            s.copy(ocultos = nuevos)
        }
    }

    fun limpiarError() = _uiState.update { it.copy(error = null) }
}
