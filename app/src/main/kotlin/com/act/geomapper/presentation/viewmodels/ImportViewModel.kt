package com.act.geomapper.presentation.viewmodels

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.act.geomapper.data.geopdf.GeoPdfData
import com.act.geomapper.data.geopdf.GeoPdfImporter
import com.act.geomapper.data.import.EntidadImportada
import com.act.geomapper.data.import.ImportService
import com.act.geomapper.domain.usecase.GuardarPredioUseCase
import com.act.geomapper.domain.models.Predio
import com.act.geomapper.data.geometry.GeometryService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ImportState(
    val preview        : List<EntidadImportada> = emptyList(),
    val procesando     : Boolean                = false,
    val guardadas      : Int                    = 0,
    val error          : String?               = null,
    val progresoRaster : Int?                   = null,  // null = sin carga en curso; 0-100 = %
    val bboxImportado  : ZoomBounds?            = null   // extensión de lo importado, para hacer zoom
)

class ImportViewModel(
    private val guardarPredio  : GuardarPredioUseCase,
    private val geometryService: GeometryService
) : ViewModel() {

    private val _state = MutableStateFlow(ImportState())
    val state: StateFlow<ImportState> = _state.asStateFlow()

    private val _geoPdfData    = MutableStateFlow<GeoPdfData?>(null)
    val geoPdfData: StateFlow<GeoPdfData?> = _geoPdfData.asStateFlow()

    private val _geoPdfVisible = MutableStateFlow(true)
    val geoPdfVisible: StateFlow<Boolean> = _geoPdfVisible.asStateFlow()

    fun toggleGeoPdfVisible() { _geoPdfVisible.value = !_geoPdfVisible.value }

    fun parsearArchivo(context: Context, uri: Uri) {
        val mime   = context.contentResolver.getType(uri) ?: ""
        val nombre = uri.lastPathSegment?.lowercase() ?: ""
        if (nombre.endsWith(".pdf") || mime.contains("pdf")) {
            cargarGeoPdf(context, uri)
            return
        }
        if (nombre.endsWith(".tif") || nombre.endsWith(".tiff") || mime.contains("tiff")) {
            cargarGeoTiff(context, uri)
            return
        }
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

    fun cargarGeoPdf(context: Context, uri: Uri) {
        _state.update { it.copy(procesando = true, error = null) }
        viewModelScope.launch {
            runCatching { GeoPdfImporter(context).importar(uri) }
                .onSuccess { data ->
                    if (data != null) _geoPdfData.value = data
                    else _state.update { it.copy(error = "No se pudo leer el GeoPDF (falta la georreferenciación /GPTS o la página no se pudo renderizar).") }
                    _state.update { it.copy(procesando = false) }
                }
                .onFailure { e -> _state.update { it.copy(procesando = false, error = e.message ?: "Error al importar el GeoPDF") } }
        }
    }

    fun cargarGeoTiff(context: Context, uri: Uri) {
        _state.update { it.copy(procesando = true, error = null, progresoRaster = 0) }
        viewModelScope.launch {
            runCatching {
                com.act.geomapper.data.geotiff.GeoTiffImporter(context).importar(uri) { pct ->
                    _state.update { it.copy(progresoRaster = pct) }
                }
            }
                .onSuccess { data ->
                    _geoPdfData.value = data
                    _state.update { it.copy(procesando = false, progresoRaster = null) }
                }
                .onFailure { e -> _state.update { it.copy(procesando = false, progresoRaster = null, error = e.message ?: "Error al importar el GeoTIFF") } }
        }
    }

    fun cerrarGeoPdf() { _geoPdfData.value = null; _geoPdfVisible.value = true }

    fun confirmarImportacion(proyectoId: Long) {
        val entidades = _state.value.preview
        if (entidades.isEmpty()) return
        _state.update { it.copy(procesando = true) }
        viewModelScope.launch {
            var guardadas = 0
            var minX = Double.MAX_VALUE; var maxX = -Double.MAX_VALUE
            var minY = Double.MAX_VALUE; var maxY = -Double.MAX_VALUE
            entidades.forEach { e ->
                runCatching {
                    val geom     = geometryService.wktAGeometria(e.wkt)
                    val area     = geometryService.calcularAreaHa(e.wkt)
                    val perimetro = geometryService.calcularPerimetroM(e.wkt)
                    val env = geom.envelopeInternal
                    if (env.minX < minX) minX = env.minX
                    if (env.maxX > maxX) maxX = env.maxX
                    if (env.minY < minY) minY = env.minY
                    if (env.maxY > maxY) maxY = env.maxY
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
            val bbox = if (guardadas > 0 && minX <= maxX)
                ZoomBounds(norte = maxY, sur = minY, este = maxX, oeste = minX) else null
            _state.update { it.copy(procesando = false, guardadas = guardadas, preview = emptyList(), bboxImportado = bbox) }
        }
    }

    fun limpiar() = _state.update { ImportState() }
    fun limpiarError() = _state.update { it.copy(error = null) }
}
