package com.act.geomapper.data.import

import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** Lanzada cuando el shapefile usa una variante no soportada (CRS/proyección desconocida). */
class ShapefileUnsupportedException(message: String) : Exception(message)

/**
 * Lector de ESRI Shapefile (.shp) con reproyección a WGS84 (lat/lon) a partir del .prj.
 * Soporta puntos, líneas y polígonos (con y sin Z/M). El .prj se parsea directamente para
 * obtener los parámetros de proyección, así que funciona con cualquier zona MAGNA-SIRGAS /
 * UTM / Transverse Mercator sin depender de una tabla de EPSG. Los nombres salen del .dbf.
 */
object ShapefileReader {

    private data class Proyeccion(
        val geografico: Boolean,   // ya está en grados lat/lon
        val tm: Boolean,           // Transverse Mercator
        val webMercator: Boolean,  // Mercator esférico (EPSG:3857)
        val a: Double, val f: Double,
        val lat0: Double, val lon0: Double,
        val k0: Double, val fe: Double, val fn: Double
    )

    fun leer(shp: ByteArray, prj: String?, dbf: ByteArray?): List<EntidadImportada> {
        val proj = parsearPrj(prj)
        val nombres = dbf?.let { parsearDbfNombres(it) } ?: emptyList()
        val geometrias = parsearShp(shp)

        return geometrias.mapIndexedNotNull { i, geom ->
            val ptsLL = geom.puntos.map { (x, y) -> aLatLon(x, y, proj) }
            val wkt = when (geom.tipo) {
                Tipo.POINT   -> ptsLL.firstOrNull()?.let { "POINT(${it.first} ${it.second})" }
                Tipo.LINE    -> if (ptsLL.size >= 2) "LINESTRING(${ptsLL.joinToString(", ") { "${it.first} ${it.second}" }})" else null
                Tipo.POLYGON -> {
                    if (ptsLL.size >= 3) {
                        val cerrado = if (ptsLL.first() != ptsLL.last()) ptsLL + ptsLL.first() else ptsLL
                        "POLYGON((${cerrado.joinToString(", ") { "${it.first} ${it.second}" }}))"
                    } else null
                }
            } ?: return@mapIndexedNotNull null
            val nombre = nombres.getOrNull(i)?.takeIf { it.isNotBlank() } ?: "Entidad ${i + 1}"
            EntidadImportada(nombre = nombre, wkt = wkt, propiedades = mapOf("fuente" to "Shapefile"))
        }
    }

    // ── .prj ──────────────────────────────────────────────────────────────────

    private fun parsearPrj(prj: String?): Proyeccion {
        if (prj.isNullOrBlank() || !prj.contains("PROJCS")) {
            // Sin proyección → se asume geográfico (lat/lon en grados)
            return Proyeccion(true, false, false, 6378137.0, 1.0 / 298.257223563, 0.0, 0.0, 1.0, 0.0, 0.0)
        }
        fun param(nombre: String): Double? =
            Regex("""PARAMETER\["$nombre",([-\d.eE]+)\]""", RegexOption.IGNORE_CASE).find(prj)?.groupValues?.get(1)?.toDoubleOrNull()
        val sph = Regex("""SPHEROID\["[^"]*",([-\d.eE]+),([-\d.eE]+)\]""").find(prj)
        val a   = sph?.groupValues?.get(1)?.toDoubleOrNull() ?: 6378137.0
        val inv = sph?.groupValues?.get(2)?.toDoubleOrNull() ?: 298.257222101
        val f   = if (inv != 0.0) 1.0 / inv else 0.0

        val esTm  = prj.contains("Transverse_Mercator", ignoreCase = true)
        val esWeb = prj.contains("Mercator_Auxiliary_Sphere", ignoreCase = true) ||
                    prj.contains("Popular_Visualisation_Pseudo_Mercator", ignoreCase = true)

        if (!esTm && !esWeb) {
            throw ShapefileUnsupportedException("Proyección del shapefile no soportada. Reproyéctalo a WGS84 (EPSG:4326) o MAGNA/UTM antes de importar.")
        }

        return Proyeccion(
            geografico = false, tm = esTm, webMercator = esWeb,
            a = a, f = f,
            lat0 = param("Latitude_Of_Origin") ?: 0.0,
            lon0 = param("Central_Meridian") ?: 0.0,
            k0   = param("Scale_Factor") ?: 1.0,
            fe   = param("False_Easting") ?: 0.0,
            fn   = param("False_Northing") ?: 0.0
        )
    }

    private fun aLatLon(x: Double, y: Double, p: Proyeccion): Pair<Double, Double> = when {
        p.geografico  -> x to y
        p.webMercator -> {
            val r = 6378137.0
            Math.toDegrees(x / r) to Math.toDegrees(2 * atan(exp(y / r)) - Math.PI / 2)
        }
        else -> inversaTransversaMercator(x, y, p)
    }

    /** Fórmula de Snyder para Mercator Transversa inversa (verificada contra pyproj/casos conocidos). */
    private fun inversaTransversaMercator(x: Double, y: Double, p: Proyeccion): Pair<Double, Double> {
        val a = p.a; val f = p.f
        val e2 = f * (2 - f)
        val e1 = (1 - sqrt(1 - e2)) / (1 + sqrt(1 - e2))
        val ePrime2 = e2 / (1 - e2)
        val lat0 = Math.toRadians(p.lat0); val lon0 = Math.toRadians(p.lon0)

        fun arco(lat: Double) = a * (
            (1 - e2 / 4 - 3 * e2 * e2 / 64 - 5 * e2 * e2 * e2 / 256) * lat -
            (3 * e2 / 8 + 3 * e2 * e2 / 32 + 45 * e2 * e2 * e2 / 1024) * sin(2 * lat) +
            (15 * e2 * e2 / 256 + 45 * e2 * e2 * e2 / 1024) * sin(4 * lat) -
            (35 * e2 * e2 * e2 / 3072) * sin(6 * lat)
        )

        val m0 = arco(lat0)
        val m  = (y - p.fn) / p.k0 + m0
        val mu = m / (a * (1 - e2 / 4 - 3 * e2 * e2 / 64 - 5 * e2 * e2 * e2 / 256))

        val lat1 = mu +
            (3 * e1 / 2 - 27 * e1.pow(3) / 32) * sin(2 * mu) +
            (21 * e1 * e1 / 16 - 55 * e1.pow(4) / 32) * sin(4 * mu) +
            (151 * e1.pow(3) / 96) * sin(6 * mu) +
            (1097 * e1.pow(4) / 512) * sin(8 * mu)

        val c1 = ePrime2 * cos(lat1).pow(2)
        val t1 = tan(lat1).pow(2)
        val n1 = a / sqrt(1 - e2 * sin(lat1).pow(2))
        val r1 = a * (1 - e2) / (1 - e2 * sin(lat1).pow(2)).pow(1.5)
        val d  = (x - p.fe) / (n1 * p.k0)

        val lat = lat1 - (n1 * tan(lat1) / r1) * (
            d.pow(2) / 2 -
            (5 + 3 * t1 + 10 * c1 - 4 * c1 * c1 - 9 * ePrime2) * d.pow(4) / 24 +
            (61 + 90 * t1 + 298 * c1 + 45 * t1 * t1 - 252 * ePrime2 - 3 * c1 * c1) * d.pow(6) / 720
        )
        val lon = lon0 + (
            d -
            (1 + 2 * t1 + c1) * d.pow(3) / 6 +
            (5 - 2 * c1 + 28 * t1 - 3 * c1 * c1 + 8 * ePrime2 + 24 * t1 * t1) * d.pow(5) / 120
        ) / cos(lat1)

        return Math.toDegrees(lon) to Math.toDegrees(lat)
    }

    // ── .shp ──────────────────────────────────────────────────────────────────

    private enum class Tipo { POINT, LINE, POLYGON }
    private data class Geom(val tipo: Tipo, val puntos: List<Pair<Double, Double>>)

    private fun leInt(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xFF) or ((b[off + 1].toInt() and 0xFF) shl 8) or
        ((b[off + 2].toInt() and 0xFF) shl 16) or ((b[off + 3].toInt() and 0xFF) shl 24)

    private fun beInt(b: ByteArray, off: Int): Int =
        ((b[off].toInt() and 0xFF) shl 24) or ((b[off + 1].toInt() and 0xFF) shl 16) or
        ((b[off + 2].toInt() and 0xFF) shl 8) or (b[off + 3].toInt() and 0xFF)

    private fun leDouble(b: ByteArray, off: Int): Double {
        var bits = 0L
        for (i in 7 downTo 0) bits = (bits shl 8) or (b[off + i].toLong() and 0xFF)
        return Double.fromBits(bits)
    }

    private fun parsearShp(data: ByteArray): List<Geom> {
        if (data.size < 100 || beInt(data, 0) != 9994) {
            throw ShapefileUnsupportedException("El .shp dentro del ZIP no es válido")
        }
        val geoms = mutableListOf<Geom>()
        var pos = 100
        while (pos + 8 <= data.size) {
            val clen = beInt(data, pos + 4) // longitud del contenido en words de 16 bits
            val contentOff = pos + 8
            val contentLen = clen * 2
            if (contentOff + contentLen > data.size || contentLen < 4) break
            val shpType = leInt(data, contentOff)
            runCatching {
                when (shpType) {
                    1, 11, 21 -> { // Point (+Z/M)
                        val x = leDouble(data, contentOff + 4)
                        val y = leDouble(data, contentOff + 12)
                        geoms.add(Geom(Tipo.POINT, listOf(x to y)))
                    }
                    3, 13, 23, 5, 15, 25 -> { // PolyLine / Polygon (+Z/M)
                        val numParts  = leInt(data, contentOff + 36)
                        val numPoints = leInt(data, contentOff + 40)
                        val partsOff  = contentOff + 44
                        val ptsOff    = partsOff + numParts * 4
                        val primeraParteInicio = leInt(data, partsOff)
                        val primeraParteFin = if (numParts > 1) leInt(data, partsOff + 4) else numPoints
                        val ring = ArrayList<Pair<Double, Double>>(primeraParteFin - primeraParteInicio)
                        for (idx in primeraParteInicio until primeraParteFin) {
                            val o = ptsOff + idx * 16
                            ring.add(leDouble(data, o) to leDouble(data, o + 8))
                        }
                        val tipo = if (shpType in intArrayOf(5, 15, 25)) Tipo.POLYGON else Tipo.LINE
                        geoms.add(Geom(tipo, ring))
                    }
                    // 0 = Null, otros multipunto no soportados → se ignoran silenciosamente
                }
            }
            pos = contentOff + contentLen
        }
        if (geoms.isEmpty()) throw ShapefileUnsupportedException("El shapefile no contiene geometrías soportadas (puntos/líneas/polígonos)")
        return geoms
    }

    // ── .dbf (nombres) ──────────────────────────────────────────────────────────

    private fun parsearDbfNombres(data: ByteArray): List<String> {
        if (data.size < 32) return emptyList()
        val numRecords = leInt(data, 4)
        val headerSize = (data[8].toInt() and 0xFF) or ((data[9].toInt() and 0xFF) shl 8)
        val recordSize = (data[10].toInt() and 0xFF) or ((data[11].toInt() and 0xFF) shl 8)

        data class Campo(val nombre: String, val tipo: Char, val offset: Int, val largo: Int)
        val campos = mutableListOf<Campo>()
        var pos = 32; var off = 1
        while (pos < data.size && data[pos] != 0x0D.toByte()) {
            val nombre = String(data, pos, 11, Charsets.ISO_8859_1).substringBefore('\u0000').trim()
            val tipo   = (data[pos + 11].toInt() and 0xFF).toChar()
            val largo  = data[pos + 16].toInt() and 0xFF
            campos.add(Campo(nombre, tipo, off, largo))
            off += largo; pos += 32
        }

        val preferido = campos.firstOrNull { it.nombre.lowercase() in listOf("usos", "nombre", "name", "nom", "descripci", "descripcion") }
            ?: campos.firstOrNull { it.tipo == 'C' }
            ?: return emptyList()

        val nombres = ArrayList<String>(numRecords)
        for (i in 0 until numRecords) {
            val recStart = headerSize + i * recordSize
            if (recStart + preferido.offset + preferido.largo > data.size) break
            nombres.add(String(data, recStart + preferido.offset, preferido.largo, Charsets.ISO_8859_1).trim())
        }
        return nombres
    }
}
