package com.act.geomapper.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.act.geomapper.data.geometry.GeometryService
import com.act.geomapper.data.geometry.SegmentoInfo
import com.act.geomapper.domain.models.Predio
import com.act.geomapper.domain.usecase.EliminarPredioUseCase
import com.act.geomapper.domain.usecase.GuardarPredioUseCase
import kotlin.math.atan2
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.PrecisionModel
import org.locationtech.jts.io.WKTReader
import org.locationtech.jts.io.WKTWriter

enum class ModoParcelacion { IDLE, CORTE, VIA, VIA_LINEA, SUBDIVISION, SUBDIVISION_LINEA }

enum class TipoSubdivision { FRENTE_OBJETIVO, POR_AREA, PARTES_IGUALES }

/** Metadatos editables de cada parcela resultado antes de confirmar el guardado */
data class SubparcelaEditable(
    val nombre      : String = "",
    val propietario : String = "",
    val areaHa      : Double = 0.0
)

data class ParcelacionState(
    val predioBase            : Predio?                    = null,
    val segmentos             : List<SegmentoInfo>          = emptyList(),
    val areaHa                : Double                     = 0.0,
    val subparcelas           : List<String>               = emptyList(),   // WKTs para preview en mapa
    val subparcelasInfo       : List<SubparcelaEditable>   = emptyList(),   // nombres/atributos editables
    val pendienteGuardar      : Boolean                    = false,
    val esVia                 : Boolean                    = false,         // resultado de trazado de vía (sin etiquetas)
    val puntosLinea           : List<Pair<Double, Double>> = emptyList(),
    val anchoViaMetros        : Double                     = 10.0,
    val modo                  : ModoParcelacion            = ModoParcelacion.IDLE,
    val procesando            : Boolean                    = false,
    val error                 : String?                    = null,
    val idOriginalParaEliminar: Long?                      = null  // id DB del predio base original (antes de cortes sucesivos)
)

class ParcelacionViewModel(
    private val geometryService : GeometryService,
    private val guardarPredio   : GuardarPredioUseCase,
    private val eliminarPredio  : EliminarPredioUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(ParcelacionState())
    val state: StateFlow<ParcelacionState> = _state.asStateFlow()

    private val wktWriter = WKTWriter()
    private val factory   = GeometryFactory(PrecisionModel(), 4326)

    // Fallback para nombres cuando el usuario no edita
    private var pendingProyectoId     : Long             = 1L
    private var pendingNombreBase     : String           = ""
    private var pendingSufijos        : List<String>     = emptyList()
    // Parámetros de subdivisión guardados mientras el usuario traza la línea guía
    private var pendingTipoSubdiv     : TipoSubdivision  = TipoSubdivision.PARTES_IGUALES
    private var pendingValorSubdiv    : Double           = 2.0

    fun seleccionarPoligono(predio: Predio) {
        val wkt      = geometryService.geometriaAWkt(predio.geometry)
        val segmentos = geometryService.calcularDistanciasPorLado(wkt)
        val areaHa   = geometryService.calcularAreaHa(wkt)
        _state.update { it.copy(
            predioBase             = predio,
            segmentos              = segmentos,
            areaHa                 = areaHa,
            subparcelas            = emptyList(),
            subparcelasInfo        = emptyList(),
            pendienteGuardar       = false,
            puntosLinea            = emptyList(),
            modo                   = ModoParcelacion.IDLE,
            idOriginalParaEliminar = null
        ) }
    }

    // ── Corte manual ─────────────────────────────────────────────────────────
    fun iniciarCorte() = _state.update { it.copy(modo = ModoParcelacion.CORTE, puntosLinea = emptyList()) }

    fun agregarPuntoLinea(lat: Double, lon: Double) {
        _state.update { it.copy(puntosLinea = it.puntosLinea + Pair(lat, lon)) }
    }

    fun quitarUltimoPunto() {
        _state.update { s -> if (s.puntosLinea.isEmpty()) s else s.copy(puntosLinea = s.puntosLinea.dropLast(1)) }
    }

    fun finalizarCorte(nombreOriginal: String, proyectoId: Long) {
        val base   = _state.value.predioBase ?: return
        val puntos = _state.value.puntosLinea
        if (puntos.size < 2) { _state.update { it.copy(error = "Dibuja al menos 2 puntos") }; return }
        _state.update { it.copy(procesando = true) }
        viewModelScope.launch {
            runCatching {
                val predioWkt = geometryService.geometriaAWkt(base.geometry)
                val lineaWkt  = puntosALineaWkt(puntos)
                geometryService.cortarConLinea(predioWkt, lineaWkt)
            }.onSuccess { partes ->
                val sufijos = listOf("_A", "_B")
                pendingProyectoId = base.proyectoId.takeIf { it > 0L } ?: proyectoId
                pendingNombreBase = nombreOriginal
                pendingSufijos    = sufijos
                // Preservar el id original solo la primera vez (si ya hay uno guardado, mantenerlo)
                val idOriginal = _state.value.idOriginalParaEliminar
                    ?: base.id.takeIf { it > 0L }
                val infos = buildInfos(partes, nombreOriginal, sufijos)
                _state.update { it.copy(
                    subparcelas            = partes,
                    subparcelasInfo        = infos,
                    procesando             = false,
                    modo                   = ModoParcelacion.IDLE,
                    pendienteGuardar       = true,
                    idOriginalParaEliminar = idOriginal
                ) }
            }.onFailure { e -> _state.update { it.copy(error = e.message, procesando = false) } }
        }
    }

    // ── Trazar vía ───────────────────────────────────────────────────────────
    fun setAnchoVia(metros: Double) = _state.update { it.copy(anchoViaMetros = metros) }

    fun iniciarVia() = _state.update { it.copy(modo = ModoParcelacion.VIA, puntosLinea = emptyList()) }

    /** El usuario confirmó el ancho y va a trazar el eje — colapsa la card de config */
    fun prepararVia() = _state.update { it.copy(modo = ModoParcelacion.VIA_LINEA) }

    fun finalizarVia(proyectoId: Long, nombreBase: String) {
        val base   = _state.value.predioBase
        val puntos = _state.value.puntosLinea
        if (puntos.size < 2) { _state.update { it.copy(error = "Dibuja al menos 2 puntos") }; return }
        _state.update { it.copy(procesando = true) }
        viewModelScope.launch {
            runCatching {
                val lineaWkt    = puntosALineaWkt(puntos)
                val anchoGrados = _state.value.anchoViaMetros / 111_320.0
                val bufferWkt   = geometryService.aplicarBuffer(lineaWkt, anchoGrados / 2)
                val bufferGeom  = geometryService.wktAGeometria(bufferWkt)
                // Corredor de la vía (intersection)
                val viaGeom = base!!.geometry.intersection(bufferGeom)
                val viaWkt  = geometryService.geometriaAWkt(viaGeom)
                // Lotes que quedan a los lados (difference)
                val restoGeom = base.geometry.difference(bufferGeom)
                val restoWkts = (0 until restoGeom.numGeometries)
                    .map { restoGeom.getGeometryN(it) }
                    .filter { it.geometryType == "Polygon" && !it.isEmpty && it.area > 0 }
                    .map { geometryService.geometriaAWkt(it) }
                listOf(viaWkt) + restoWkts
            }.onSuccess { todasPartes ->
                val restoSize = todasPartes.size - 1
                val sufijos   = listOf("_via") + ('A'..'Z').take(restoSize).map { "_$it" }
                pendingProyectoId = base?.proyectoId?.takeIf { it > 0L } ?: proyectoId
                pendingNombreBase = nombreBase
                pendingSufijos    = sufijos
                val infos = buildInfos(todasPartes, nombreBase, sufijos)
                _state.update { it.copy(
                    subparcelas      = todasPartes,
                    subparcelasInfo  = infos,
                    procesando       = false,
                    modo             = ModoParcelacion.IDLE,
                    pendienteGuardar = true,
                    esVia            = true
                ) }
            }.onFailure { e -> _state.update { it.copy(error = e.message, procesando = false) } }
        }
    }

    // ── Subdivisión ───────────────────────────────────────────────────────────

    /** Paso 1: el usuario configuró tipo/valor — ahora debe trazar la línea de dirección */
    fun iniciarSubdivision() = _state.update { it.copy(modo = ModoParcelacion.SUBDIVISION) }

    /** Paso 2: guarda los parámetros y entra al modo de trazado de línea guía */
    fun prepararSubdivision(tipo: TipoSubdivision, valor: Double) {
        pendingTipoSubdiv  = tipo
        pendingValorSubdiv = valor
        _state.update { it.copy(modo = ModoParcelacion.SUBDIVISION_LINEA, puntosLinea = emptyList()) }
    }

    /** Paso 3: el usuario finalizó la línea guía → calcular y previsualizar */
    fun finalizarSubdivision(proyectoId: Long, nombreBase: String) {
        val base   = _state.value.predioBase ?: return
        val puntos = _state.value.puntosLinea
        if (puntos.size < 2) { _state.update { it.copy(error = "Dibuja al menos 2 puntos para la dirección") }; return }
        val wkt  = geometryService.geometriaAWkt(base.geometry)
        _state.update { it.copy(procesando = true) }

        // Ángulo de la línea guía: bearing desde el primer al último punto
        val (lat1, lon1) = puntos.first()
        val (lat2, lon2) = puntos.last()
        val anguloRad = atan2(lat2 - lat1, lon2 - lon1)  // ángulo estándar: atan2(dy, dx) en sistema lon=X lat=Y

        viewModelScope.launch {
            runCatching {
                val tipo  = pendingTipoSubdiv
                val valor = pendingValorSubdiv
                when (tipo) {
                    TipoSubdivision.POR_AREA -> {
                        // Piezas de área exacta + residual
                        geometryService.dividirPorAreaObjetivo(wkt, valor, anguloRad)
                    }
                    else -> {
                        val n = when (tipo) {
                            TipoSubdivision.PARTES_IGUALES  -> valor.toInt()
                            TipoSubdivision.FRENTE_OBJETIVO -> {
                                val segs   = geometryService.calcularDistanciasPorLado(wkt)
                                val frente = segs.maxOf { it.distanciaMetros }
                                (frente / valor).toInt().coerceAtLeast(2)
                            }
                            else -> 2
                        }
                        geometryService.dividirEnFranjasAngulado(wkt, n, anguloRad)
                    }
                }
            }.onSuccess { partes ->
                val sufijos = partes.indices.map { " ${it + 1}" }
                pendingProyectoId = base.proyectoId.takeIf { it > 0L } ?: proyectoId
                pendingNombreBase = nombreBase
                pendingSufijos    = sufijos
                val infos = buildInfos(partes, nombreBase, sufijos)
                _state.update { it.copy(
                    subparcelas      = partes,
                    subparcelasInfo  = infos,
                    procesando       = false,
                    modo             = ModoParcelacion.IDLE,
                    puntosLinea      = emptyList(),
                    pendienteGuardar = true
                ) }
            }.onFailure { e -> _state.update { it.copy(error = e.message, procesando = false) } }
        }
    }

    // ── Edición de atributos de parcelas pendientes ───────────────────────────
    fun editarSubparcela(index: Int, nombre: String, propietario: String) {
        _state.update { s ->
            val lista = s.subparcelasInfo.toMutableList()
            if (index in lista.indices) lista[index] = lista[index].copy(nombre = nombre, propietario = propietario)
            s.copy(subparcelasInfo = lista)
        }
    }

    // ── Seleccionar subparcela de un corte para cortarla de nuevo ────────────
    fun seleccionarSubparcelaComoBase(index: Int) {
        val wkt     = _state.value.subparcelas.getOrNull(index) ?: return
        val otros   = _state.value.subparcelas.filterIndexed   { i, _ -> i != index }
        val otrosInfo = _state.value.subparcelasInfo.filterIndexed { i, _ -> i != index }
        val info    = _state.value.subparcelasInfo.getOrNull(index)
        val geom    = runCatching { geometryService.wktAGeometria(wkt) }.getOrNull() ?: return
        val nombre  = info?.nombre?.ifBlank { null } ?: "${pendingNombreBase}_${index + 1}"
        val tempPredio = Predio(
            id         = 0L,
            proyectoId = pendingProyectoId,
            nombre     = nombre,
            geometry   = geom,
            area       = info?.areaHa ?: 0.0
        )
        val segmentos = geometryService.calcularDistanciasPorLado(wkt)
        val areaHa    = geometryService.calcularAreaHa(wkt)
        val origId    = _state.value.idOriginalParaEliminar

        viewModelScope.launch {
            // Guardar los demás fragmentos en DB (quedan visibles como contexto)
            otros.forEachIndexed { i, otroWkt ->
                runCatching {
                    val oi = otrosInfo.getOrElse(i) { SubparcelaEditable(nombre = "${pendingNombreBase}_${i + 1}") }
                    guardarPredio(Predio(
                        proyectoId  = pendingProyectoId,
                        nombre      = oi.nombre.ifBlank { "${pendingNombreBase}_${i + 1}" },
                        propietario = oi.propietario,
                        geometry    = geometryService.wktAGeometria(otroWkt),
                        area        = geometryService.calcularAreaHa(otroWkt),
                        perimetro   = geometryService.calcularPerimetroM(otroWkt)
                    ))
                }
            }
            // Eliminar el polígono original de DB (ya está reemplazado por los fragmentos)
            if (origId != null && origId > 0L) eliminarPredio(origId)

            _state.update { it.copy(
                predioBase             = tempPredio,
                segmentos              = segmentos,
                areaHa                 = areaHa,
                subparcelas            = emptyList(),
                subparcelasInfo        = emptyList(),
                pendienteGuardar       = false,
                puntosLinea            = emptyList(),
                modo                   = ModoParcelacion.IDLE,
                idOriginalParaEliminar = null  // original ya eliminado
            ) }
        }
    }

    // ── Confirmar / Deshacer ──────────────────────────────────────────────────
    fun confirmarGuardado() {
        viewModelScope.launch {
            // Usa el id original rastreado; si no hay (cortes sucesivos), usa predioBase.id
            val elimId = _state.value.idOriginalParaEliminar
                ?: _state.value.predioBase?.id
            guardarResultados()
            if (elimId != null && elimId > 0L) eliminarPredio(elimId)
            // subparcelas se mantienen para que las etiquetas de área/distancia sigan visibles.
            // Se limpian cuando el usuario selecciona un nuevo polígono o cancela.
            _state.update { it.copy(
                pendienteGuardar       = false,
                esVia                  = false,
                idOriginalParaEliminar = null
            ) }
        }
    }

    fun deshacerResultado() {
        _state.update { it.copy(
            subparcelas      = emptyList(),
            subparcelasInfo  = emptyList(),
            pendienteGuardar = false,
            esVia            = false
            // idOriginalParaEliminar se mantiene para el próximo intento de corte
        ) }
    }

    fun cancelar() {
        pendingTipoSubdiv  = TipoSubdivision.PARTES_IGUALES
        pendingValorSubdiv = 2.0
        _state.update { it.copy(
            modo                   = ModoParcelacion.IDLE,
            puntosLinea            = emptyList(),
            subparcelas            = emptyList(),
            subparcelasInfo        = emptyList(),
            pendienteGuardar       = false,
            idOriginalParaEliminar = null
        ) }
    }

    fun limpiarError() = _state.update { it.copy(error = null) }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private fun puntosALineaWkt(puntos: List<Pair<Double, Double>>): String {
        val coords = puntos.joinToString(", ") { (lat, lon) -> "$lon $lat" }
        return "LINESTRING($coords)"
    }

    /** Genera los metadatos iniciales editables para cada parcela resultado */
    private fun buildInfos(wkts: List<String>, nombreBase: String, sufijos: List<String>): List<SubparcelaEditable> =
        wkts.mapIndexed { i, wkt ->
            val nombre = "$nombreBase${sufijos.getOrElse(i) { " ${i + 1}" }}"
            val area   = runCatching { geometryService.calcularAreaHa(wkt) }.getOrDefault(0.0)
            SubparcelaEditable(nombre = nombre, areaHa = area)
        }

    private suspend fun guardarResultados() {
        val partes = _state.value.subparcelas
        val infos  = _state.value.subparcelasInfo
        partes.forEachIndexed { i, wkt ->
            runCatching {
                val geom        = geometryService.wktAGeometria(wkt)
                val info        = infos.getOrElse(i) { SubparcelaEditable(nombre = "$pendingNombreBase ${i + 1}") }
                val nombre      = info.nombre.ifBlank { "$pendingNombreBase ${i + 1}" }
                guardarPredio(Predio(
                    proyectoId  = pendingProyectoId,
                    nombre      = nombre,
                    propietario = info.propietario,
                    geometry    = geom,
                    area        = geometryService.calcularAreaHa(wkt),
                    perimetro   = geometryService.calcularPerimetroM(wkt)
                ))
            }
        }
    }
}
