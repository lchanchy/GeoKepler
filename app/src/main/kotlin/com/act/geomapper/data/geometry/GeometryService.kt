package com.act.geomapper.data.geometry

import com.act.geomapper.domain.models.PuntoGps
import org.locationtech.jts.geom.*
import org.locationtech.jts.geom.util.AffineTransformation
import org.locationtech.jts.io.WKTReader
import org.locationtech.jts.io.WKTWriter
import kotlin.math.*

data class SegmentoInfo(
    val latMedio         : Double,
    val lonMedio         : Double,
    val distanciaMetros  : Double,
    val indice           : Int
)

class GeometryService {

    private val factory = GeometryFactory(PrecisionModel(), 4326)
    private val wktReader = WKTReader(factory)
    private val wktWriter = WKTWriter()

    fun crearPunto(punto: PuntoGps): Point =
        factory.createPoint(Coordinate(punto.longitud, punto.latitud))

    fun crearLinea(puntos: List<PuntoGps>): LineString {
        require(puntos.size >= 2) { "Se necesitan al menos 2 puntos para una línea" }
        return factory.createLineString(puntos.toCoordinates())
    }

    fun crearPoligono(puntos: List<PuntoGps>): Polygon {
        require(puntos.size >= 3) { "Se necesitan al menos 3 puntos para un polígono" }
        val coords = puntos.toCoordinates().toMutableList()
        if (coords.first() != coords.last()) coords.add(coords.first())
        return factory.createPolygon(coords.toTypedArray())
    }

    fun calcularAreaM2(wkt: String): Double {
        val geom = wktReader.read(wkt)
        return calcularAreaM2(geom)
    }

    fun calcularAreaM2(geometry: Geometry): Double {
        // Conversión aproximada grados → metros² en Colombia (~lat 4°)
        val factorLat = 111_320.0
        val factorLon = 111_320.0 * Math.cos(Math.toRadians(4.0))
        return geometry.area * factorLat * factorLon
    }

    fun calcularAreaHa(wkt: String): Double = calcularAreaM2(wkt) / 10_000.0

    fun calcularPerimetroM(wkt: String): Double {
        val geom = wktReader.read(wkt)
        val factorLat = 111_320.0
        val factorLon = 111_320.0 * Math.cos(Math.toRadians(4.0))
        return geom.length * ((factorLat + factorLon) / 2.0)
    }

    fun subdividirConLinea(predioWkt: String, lineaWkt: String): List<String> {
        val predio = wktReader.read(predioWkt) as? Polygon
            ?: return listOf(predioWkt)
        val linea = wktReader.read(lineaWkt)
        val resultado = predio.difference(linea.buffer(0.00001))
        return when {
            resultado is GeometryCollection ->
                (0 until resultado.numGeometries).map { wktWriter.write(resultado.getGeometryN(it)) }
            else -> listOf(wktWriter.write(resultado))
        }
    }

    fun aplicarBuffer(wkt: String, distanciaGrados: Double): String {
        val geom = wktReader.read(wkt)
        return wktWriter.write(geom.buffer(distanciaGrados))
    }

    fun geometriaAWkt(geometry: Geometry): String = wktWriter.write(geometry)

    fun wktAGeometria(wkt: String): Geometry = wktReader.read(wkt)

    /** Distancia haversine en metros entre dos puntos lat/lon */
    fun haversineMetros(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R    = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a    = sin(dLat / 2).pow(2) +
                   cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return R * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    /** Retorna punto medio y distancia haversine de cada segmento del polígono */
    fun calcularDistanciasPorLado(wkt: String): List<SegmentoInfo> {
        val coords = wktReader.read(wkt).coordinates
        val resultado = mutableListOf<SegmentoInfo>()
        // El primer y último punto son iguales en un polígono cerrado
        val n = coords.size - 1
        for (i in 0 until n) {
            val a   = coords[i]
            val b   = coords[i + 1]
            val lat1 = a.y; val lon1 = a.x
            val lat2 = b.y; val lon2 = b.x
            resultado.add(SegmentoInfo(
                latMedio        = (lat1 + lat2) / 2,
                lonMedio        = (lon1 + lon2) / 2,
                distanciaMetros = haversineMetros(lat1, lon1, lat2, lon2),
                indice          = i
            ))
        }
        return resultado
    }

    /** Corta un polígono con una línea, devuelve hasta 2 WKTs resultantes */
    fun cortarConLinea(poligonoWkt: String, lineaWkt: String): List<String> {
        val poligono = wktReader.read(poligonoWkt)
        val linea    = wktReader.read(lineaWkt)
        // Ampliar ligeramente la línea para asegurar intersección completa
        val lineaBuffer = linea.buffer(0.000005)
        val diferencia  = poligono.difference(lineaBuffer)
        return when {
            diferencia is GeometryCollection ->
                (0 until diferencia.numGeometries)
                    .map { wktWriter.write(diferencia.getGeometryN(it)) }
                    .filter { it.isNotBlank() }
            else -> listOf(wktWriter.write(diferencia))
        }
    }

    /** Divide polígono en N franjas paralelas al eje Y (norte-sur) */
    fun dividirEnFranjas(wkt: String, n: Int): List<String> =
        dividirEnFranjasAngulado(wkt, n, 0.0)

    /**
     * Divide el polígono en piezas de exactamente [areaObjetivoHa] ha cada una,
     * más un polígono residual con el área sobrante.
     * Usa búsqueda binaria para encontrar cada línea de corte con precisión.
     */
    fun dividirPorAreaObjetivo(wkt: String, areaObjetivoHa: Double, anguloRad: Double): List<String> {
        val poligono  = wktReader.read(wkt)
        val centroid  = poligono.centroid.coordinate
        val areaTotal = calcularAreaHa(wkt)
        val nFull     = (areaTotal / areaObjetivoHa).toInt()
        if (nFull < 1) return listOf(wkt)

        val rotInv   = AffineTransformation.rotationInstance(-anguloRad, centroid.x, centroid.y)
        val rotDir   = AffineTransformation.rotationInstance( anguloRad, centroid.x, centroid.y)
        val polRot   = rotInv.transform(poligono.copy())
        val env      = polRot.envelopeInternal
        // Convertir área en grados² a hectáreas usando latitud real del centroide
        val factorHa = 111_320.0 * (111_320.0 * cos(Math.toRadians(centroid.y))) / 10_000.0

        val resultado = mutableListOf<String>()
        var yMin      = env.minY

        for (i in 0 until nFull) {
            // Búsqueda binaria: hallar yCut donde área(polígono ∩ banda[yMin, yCut]) = areaObjetivoHa
            var yLow  = yMin
            var yHigh = env.maxY
            repeat(35) {
                val yMid = (yLow + yHigh) / 2.0
                val area = polRot.intersection(makeBand(env, yMin, yMid)).area * factorHa
                if (area < areaObjetivoHa) yLow = yMid else yHigh = yMid
            }
            val yCut  = (yLow + yHigh) / 2.0
            val pieza = polRot.intersection(makeBand(env, yMin, yCut))
            agregarPiezasPoligonales(pieza, rotDir, factorHa, resultado)
            yMin = yCut
        }

        // Residual: todo lo que queda por encima del último corte
        val residual = polRot.intersection(makeBand(env, yMin, env.maxY + 0.001))
        agregarPiezasPoligonales(residual, rotDir, factorHa, resultado)

        return resultado
    }

    /** Descompone el resultado de una intersección (que puede ser Multi/GeometryCollection
     * cuando el polígono base es cóncavo) en piezas Polygon individuales, descartando
     * fragmentos sin área (slivers de precisión). */
    private fun agregarPiezasPoligonales(
        geom: Geometry, rotDir: AffineTransformation, factorHa: Double, resultado: MutableList<String>
    ) {
        if (geom.isEmpty) return
        val umbralHa = 0.001
        for (i in 0 until geom.numGeometries) {
            val parte = geom.getGeometryN(i)
            if (parte.geometryType != "Polygon" || parte.isEmpty) continue
            if (parte.area * factorHa < umbralHa) continue
            resultado.add(wktWriter.write(rotDir.transform(parte.copy())))
        }
    }

    private fun makeBand(env: Envelope, yMin: Double, yMax: Double): Polygon =
        factory.createPolygon(arrayOf(
            Coordinate(env.minX - 0.001, yMin),
            Coordinate(env.maxX + 0.001, yMin),
            Coordinate(env.maxX + 0.001, yMax),
            Coordinate(env.minX - 0.001, yMax),
            Coordinate(env.minX - 0.001, yMin)
        ))

    /**
     * Divide polígono en N franjas en la dirección indicada por [anguloRad].
     * El ángulo es el bearing de la línea guía trazada por el usuario (en radianes desde el norte).
     * Las franjas corren paralelas a esa dirección; los cortes son perpendiculares.
     */
    fun dividirEnFranjasAngulado(wkt: String, n: Int, anguloRad: Double): List<String> {
        if (n < 2) return listOf(wkt)
        val poligono = wktReader.read(wkt)
        val centroid = poligono.centroid.coordinate

        // Rotar el polígono en -ángulo para que los cortes queden horizontales
        val rotInv = AffineTransformation.rotationInstance(-anguloRad, centroid.x, centroid.y)
        val polRot = rotInv.transform(poligono.copy())

        val env  = polRot.envelopeInternal
        val paso = (env.maxY - env.minY) / n

        val rotDir   = AffineTransformation.rotationInstance(anguloRad, centroid.x, centroid.y)
        val factorHa = 111_320.0 * (111_320.0 * cos(Math.toRadians(centroid.y))) / 10_000.0

        val resultado = mutableListOf<String>()
        for (i in 0 until n) {
            val yMin  = env.minY + i * paso
            val yMax  = env.minY + (i + 1) * paso
            val banda = factory.createPolygon(arrayOf(
                Coordinate(env.minX - 0.001, yMin),
                Coordinate(env.maxX + 0.001, yMin),
                Coordinate(env.maxX + 0.001, yMax),
                Coordinate(env.minX - 0.001, yMax),
                Coordinate(env.minX - 0.001, yMin)
            ))
            val inter = polRot.intersection(banda)
            agregarPiezasPoligonales(inter, rotDir, factorHa, resultado)
        }
        return resultado
    }

    private fun List<PuntoGps>.toCoordinates() =
        map { Coordinate(it.longitud, it.latitud) }.toTypedArray()
}
