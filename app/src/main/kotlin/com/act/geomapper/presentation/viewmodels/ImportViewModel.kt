package com.act.geomapper.presentation.viewmodels

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.act.geomapper.data.import.EntidadImportada
import com.act.geomapper.data.import.ImportService
import com.act.geomapper.domain.usecase.GuardarPredioUseCase
import com.act.geomapper.domain.models.Predio
import com.act.geomapper.data.geometry.GeometryService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ImportState(
    val preview    : List<EntidadImportada> = emptyList(),
    val procesando : Boolean                = false,
    val guardadas  : Int                    = 0,
    val error      : String?               = null
)

class ImportViewModel(
    private val guardarPredio  : GuardarPredioUseCase,
    private val geometryService: GeometryService
) : ViewModel() {

    private val _state = MutableStateFlow(ImportState())
    val state: StateFlow<ImportState> = _state.asStateFlow()

    fun parsearArchivo(context: Context, uri: Uri) {
        _state.update { it.copy(procesando = true, preview = emptyList(), error = null) }
        viewModelScope.launch {
            runCatching {
                ImportService(context).importar(uri)
            }.onSuccess { lista ->
                _state.update { it.copy(preview = lista, procesando = false) }
            }.onFailure { e ->
                _state.update { it.copy(error = e.message, procesando = false) }
            }
        }
    }

    fun confirmarImportacion(proyectoId: Long) {
        val entidades = _state.value.preview
        if (entidades.isEmpty()) return
        _state.update { it.copy(procesando = true) }
        viewModelScope.launch {
            var guardadas = 0
            entidades.forEach { e ->
                runCatching {
                    val geom     = geometryService.wktAGeometria(e.wkt)
                    val area     = geometryService.calcularAreaHa(e.wkt)
                    val perimetro = geometryService.calcularPerimetroM(e.wkt)
                    guardarPredio(Predio(
                        proyectoId  = proyectoId,
                        nombre      = e.nombre,
                        geometry    = geom,
                        area        = area,
                        perimetro   = perimetro
                    ))
                    guardadas++
                }
            }
            _state.update { it.copy(procesando = false, guardadas = guardadas, preview = emptyList()) }
        }
    }

    fun limpiar() = _state.update { ImportState() }
    fun limpiarError() = _state.update { it.copy(error = null) }
}
