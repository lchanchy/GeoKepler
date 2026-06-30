package com.act.geomapper.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.act.geomapper.domain.models.Proyecto
import com.act.geomapper.domain.usecase.CrearProyectoUseCase
import com.act.geomapper.domain.usecase.EliminarProyectoUseCase
import com.act.geomapper.domain.usecase.ObtenerProyectosUseCase
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ProyectoUiState(
    val proyectos: List<Proyecto> = emptyList(),
    val cargando: Boolean = false,
    val error: String? = null,
    val proyectoSeleccionadoId: Long? = null
)

class ProyectoViewModel(
    private val obtenerProyectos: ObtenerProyectosUseCase,
    private val crearProyecto: CrearProyectoUseCase,
    private val eliminarProyecto: EliminarProyectoUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProyectoUiState(cargando = true))
    val uiState: StateFlow<ProyectoUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // Garantizar que exista al menos un proyecto ANTES de que la UI pueda capturar.
            // Flow.first() es síncrono dentro del coroutine — elimina la race condition.
            val inicial = obtenerProyectos().first()
            if (inicial.isEmpty()) {
                runCatching { crearProyecto("Proyecto por defecto") }
            }
            // Ahora suscribir el Flow continuo para actualizaciones
            obtenerProyectos()
                .catch { e -> _uiState.update { it.copy(error = e.message, cargando = false) } }
                .collect { lista ->
                    _uiState.update { s ->
                        s.copy(
                            proyectos  = lista,
                            cargando   = false,
                            // Auto-seleccionar si ninguno está seleccionado
                            proyectoSeleccionadoId = s.proyectoSeleccionadoId
                                ?: lista.firstOrNull()?.id
                        )
                    }
                }
        }
    }

    fun crear(nombre: String, descripcion: String = "") {
        viewModelScope.launch {
            runCatching { crearProyecto(nombre, descripcion) }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun eliminar(id: Long) {
        viewModelScope.launch {
            runCatching { eliminarProyecto(id) }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun seleccionar(id: Long?) = _uiState.update { it.copy(proyectoSeleccionadoId = id) }

    fun limpiarError() = _uiState.update { it.copy(error = null) }
}
