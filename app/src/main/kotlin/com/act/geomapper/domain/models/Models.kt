package com.act.geomapper.domain.models

import org.locationtech.jts.geom.Geometry

data class Proyecto(
    val id: Long = 0,
    val nombre: String,
    val descripcion: String = "",
    val fechaCreacion: Long = 0L,
    val totalArea: Double = 0.0,
    val municipio: String = "",
    val departamento: String = ""
)

data class Predio(
    val id: Long = 0,
    val proyectoId: Long,
    val nombre: String,
    val propietario: String = "",
    val geometry: Geometry,
    val area: Double = 0.0,
    val perimetro: Double = 0.0,
    val tipo: TipoPredio = TipoPredio.RURAL,
    val observaciones: String = ""
)

enum class TipoPredio { RURAL, URBANO, EXPANSION }

data class PuntoGps(
    val latitud: Double,
    val longitud: Double,
    val altitud: Double = 0.0,
    val precision: Float = 0f,
    val timestamp: Long = System.currentTimeMillis()
)

data class EstadoGps(
    val activo: Boolean = false,
    val puntoActual: PuntoGps? = null,
    val precision: Float = 0f,
    val satelites: Int = 0
)

sealed class ResultadoCaptura {
    data class Exito(val geometryWKT: String, val area: Double) : ResultadoCaptura()
    data class Error(val mensaje: String) : ResultadoCaptura()
    object Cancelado : ResultadoCaptura()
}
