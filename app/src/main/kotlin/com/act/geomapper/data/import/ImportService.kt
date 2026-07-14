package com.act.geomapper.data.import

import android.content.Context
import android.net.Uri
import org.locationtech.jts.io.WKTWriter
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.util.zip.ZipInputStream

data class EntidadImportada(
    val nombre: String,
    val wkt: String,
    val propiedades: Map<String, String> = emptyMap()
)

class ImportService(private val context: Context) {

    private val wktWriter = WKTWriter()

    fun importar(uri: Uri): List<EntidadImportada> {
        val mime  = context.contentResolver.getType(uri) ?: ""
        val nombre = obtenerNombreArchivo(uri).lowercase()
        return when {
            nombre.endsWith(".geojson") || nombre.endsWith(".json") || mime.contains("json") ->
                importarGeoJson(leerTexto(uri))
            nombre.endsWith(".kml") || mime.contains("kml") || mime.contains("xml") ->
                importarKml(leerTexto(uri))
            nombre.endsWith(".gpx") || mime.contains("gpx") ->
                importarGpx(leerTexto(uri))
            nombre.endsWith(".zip") || mime.contains("zip") ->
                importarZip(uri)
            nombre.endsWith(".pdf") || mime.contains("pdf") ->
                importarGeoPdf(uri)
            else -> emptyList()
        }
    }

    // ── GeoJSON ──────────────────────────────────────────────────────────────

    private fun importarGeoJson(texto: String): List<EntidadImportada> {
        val resultado = mutableListOf<EntidadImportada>()
        // Extraer cada Feature como bloque de texto
        val bloques = dividirFeatures(texto)
        for (bloque in bloques) {
            val nombre = extraerString(bloque, "nombre")
                ?: extraerString(bloque, "name")
                ?: extraerString(bloque, "NOMBRE")
                ?: "Sin nombre"
            val tipo   = extraerString(bloque, "type") ?: continue
            val coords = extraerCoordsJson(bloque) ?: continue
            val wkt    = jsonCoordsAWkt(tipo, coords) ?: continue
            val props  = mapOf("fuente" to "GeoJSON", "nombre" to nombre)
            resultado.add(EntidadImportada(nombre = nombre, wkt = wkt, propiedades = props))
        }
        return resultado
    }

    /** Divide el GeoJSON en bloques de Feature buscando la geometría de cada uno */
    private fun dividirFeatures(texto: String): List<String> {
        val resultado = mutableListOf<String>()
        var idx = 0
        while (true) {
            val start = texto.indexOf("\"Feature\"", idx).takeIf { it >= 0 } ?: break
            val end   = encontrarCierreObjeto(texto, start)
            if (end > start) resultado.add(texto.substring(start, end))
            idx = end.coerceAtLeast(start + 1)
        }
        // Caso Feature solo (sin FeatureCollection)
        if (resultado.isEmpty() && texto.contains("\"geometry\"")) resultado.add(texto)
        return resultado
    }

    /** Encuentra la posición del `}` que cierra el objeto JSON que contiene `startIdx` */
    private fun encontrarCierreObjeto(s: String, startIdx: Int): Int {
        var depth = 0
        for (i in startIdx until s.length) {
            when (s[i]) { '{' -> depth++; '}' -> { depth--; if (depth <= 0) return i + 1 } }
        }
        return s.length
    }

    private fun extraerString(texto: String, clave: String): String? =
        Regex(""""$clave"\s*:\s*"([^"]+)"""").find(texto)?.groupValues?.get(1)

    private fun extraerCoordsJson(bloque: String): String? {
        // Buscar el bloque "coordinates": [...]
        val start = bloque.indexOf("\"coordinates\"").takeIf { it >= 0 } ?: return null
        val arrStart = bloque.indexOf('[', start).takeIf { it >= 0 } ?: return null
        var depth = 0
        val sb = StringBuilder()
        for (i in arrStart until bloque.length) {
            val c = bloque[i]
            sb.append(c)
            when (c) { '[' -> depth++; ']' -> { depth--; if (depth == 0) return sb.toString() } }
        }
        return null
    }

    private fun jsonCoordsAWkt(tipo: String, coordsJson: String): String? {
        return try {
            when {
                tipo.contains("Point", ignoreCase = true) -> {
                    val nums = extraerNumeros(coordsJson)
                    if (nums.size >= 2) "POINT(${nums[0]} ${nums[1]})" else null
                }
                tipo.contains("LineString", ignoreCase = true) -> {
                    val pares = extraerPares(coordsJson)
                    if (pares.size >= 2) "LINESTRING(${pares.joinToString(", ")})" else null
                }
                tipo.contains("Polygon", ignoreCase = true) -> {
                    // Tomar solo el anillo exterior (primer array de pares)
                    val anillo = extraerPrimerAnillo(coordsJson)
                    if (anillo.size >= 3) {
                        val pts = anillo.toMutableList()
                        if (pts.first() != pts.last()) pts.add(pts.first())
                        "POLYGON((${pts.joinToString(", ")}))"
                    } else null
                }
                else -> null
            }
        } catch (_: Exception) { null }
    }

    private fun extraerNumeros(s: String): List<String> =
        Regex("""[-\d.]+""").findAll(s).map { it.value }.toList()

    private fun extraerPares(s: String): List<String> {
        val nums = extraerNumeros(s)
        return nums.chunked(2) { (x, y) -> "$x $y" }
    }

    private fun extraerPrimerAnillo(s: String): List<String> {
        // El primer [ interior es el anillo exterior en Polygon
        val inner = s.trim().let { if (it.startsWith("[[")) it.substring(1) else it }
        val start = inner.indexOf('[').takeIf { it >= 0 } ?: return emptyList()
        var depth = 0
        val sb = StringBuilder()
        for (i in start until inner.length) {
            val c = inner[i]
            sb.append(c)
            when (c) { '[' -> depth++; ']' -> { depth--; if (depth == 0) break } }
        }
        return extraerPares(sb.toString())
    }

    // ── KML ──────────────────────────────────────────────────────────────────

    private fun importarKml(texto: String): List<EntidadImportada> {
        val resultado = mutableListOf<EntidadImportada>()
        val placemarks = texto.split("<Placemark").drop(1)
        placemarks.forEach { pm ->
            val nombre    = Regex("<name>\\s*<!\\[CDATA\\[([^]]+)]]>\\s*</name>|<name>([^<]+)</name>")
                                .find(pm)?.let { it.groupValues[1].ifBlank { it.groupValues[2] } }?.trim()
                            ?: "Sin nombre"
            val coordsStr = Regex("<coordinates>([^<]+)</coordinates>").find(pm)
                                ?.groupValues?.get(1)?.trim() ?: return@forEach
            val coords = coordsStr.split("\\s+".toRegex()).mapNotNull { p ->
                val parts = p.split(",")
                if (parts.size >= 2) "${parts[0].trim()} ${parts[1].trim()}" else null
            }
            if (coords.isNotEmpty()) {
                val wkt = when {
                    coords.size == 1 -> "POINT(${coords[0]})"
                    texto.contains("<LineString") -> "LINESTRING(${coords.joinToString(", ")})"
                    else -> {
                        val pts = coords.toMutableList()
                        if (pts.first() != pts.last()) pts.add(pts.first())
                        "POLYGON((${pts.joinToString(", ")}))"
                    }
                }
                resultado.add(EntidadImportada(nombre = nombre, wkt = wkt))
            }
        }
        return resultado
    }

    // ── GPX ──────────────────────────────────────────────────────────────────

    private fun importarGpx(texto: String): List<EntidadImportada> {
        val resultado = mutableListOf<EntidadImportada>()
        // Waypoints
        Regex("""<wpt\s+lat="([\d.\-]+)"\s+lon="([\d.\-]+)"[^>]*>(.*?)</wpt>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(texto).forEach { m ->
                val lat    = m.groupValues[1]
                val lon    = m.groupValues[2]
                val nombre = Regex("<name>([^<]+)</name>").find(m.groupValues[3])?.groupValues?.get(1) ?: "Punto"
                resultado.add(EntidadImportada(nombre = nombre, wkt = "POINT($lon $lat)"))
            }
        // Tracks
        Regex("""<trk>(.*?)</trk>""", RegexOption.DOT_MATCHES_ALL).findAll(texto).forEach { trk ->
            val nombre = Regex("<name>([^<]+)</name>").find(trk.groupValues[1])?.groupValues?.get(1) ?: "Track"
            val pts = Regex("""<trkpt\s+lat="([\d.\-]+)"\s+lon="([\d.\-]+)"""")
                .findAll(trk.groupValues[1]).map { "${it.groupValues[2]} ${it.groupValues[1]}" }.toList()
            if (pts.size >= 2) resultado.add(EntidadImportada(nombre = nombre, wkt = "LINESTRING(${pts.joinToString(", ")})"))
        }
        // Routes
        Regex("""<rte>(.*?)</rte>""", RegexOption.DOT_MATCHES_ALL).findAll(texto).forEach { rte ->
            val nombre = Regex("<name>([^<]+)</name>").find(rte.groupValues[1])?.groupValues?.get(1) ?: "Ruta"
            val pts = Regex("""<rtept\s+lat="([\d.\-]+)"\s+lon="([\d.\-]+)"""")
                .findAll(rte.groupValues[1]).map { "${it.groupValues[2]} ${it.groupValues[1]}" }.toList()
            if (pts.size >= 2) resultado.add(EntidadImportada(nombre = nombre, wkt = "LINESTRING(${pts.joinToString(", ")})"))
        }
        return resultado
    }

    // ── ZIP (Shapefile en ZIP o colección de archivos) ────────────────────────

    private fun importarZip(uri: Uri): List<EntidadImportada> {
        val resultado = mutableListOf<EntidadImportada>()
        // Los shapefiles vienen como varios archivos (.shp/.shx/.dbf/.prj), a veces dentro de
        // una subcarpeta. Se agrupan por nombre base para poder combinarlos.
        val shpPorBase = mutableMapOf<String, ByteArray>()
        val prjPorBase = mutableMapOf<String, ByteArray>()
        val dbfPorBase = mutableMapOf<String, ByteArray>()

        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            ZipInputStream(inputStream).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val nombre = entry.name.lowercase()
                    if (!entry.isDirectory) {
                        val bytes = zip.readBytes()
                        val base = nombre.substringBeforeLast('.')
                        when {
                            nombre.endsWith(".geojson") || nombre.endsWith(".json") ->
                                resultado.addAll(importarGeoJson(String(bytes, Charsets.UTF_8)))
                            nombre.endsWith(".kml") ->
                                resultado.addAll(importarKml(String(bytes, Charsets.UTF_8)))
                            nombre.endsWith(".gpx") ->
                                resultado.addAll(importarGpx(String(bytes, Charsets.UTF_8)))
                            nombre.endsWith(".shp") -> shpPorBase[base] = bytes
                            nombre.endsWith(".prj") -> prjPorBase[base] = bytes
                            nombre.endsWith(".dbf") -> dbfPorBase[base] = bytes
                        }
                    }
                    entry = zip.nextEntry
                }
            }
        }

        // Procesar cada shapefile encontrado (reproyectando con su .prj)
        shpPorBase.forEach { (base, shp) ->
            resultado.addAll(
                ShapefileReader.leer(
                    shp = shp,
                    prj = prjPorBase[base]?.let { String(it, Charsets.ISO_8859_1) },
                    dbf = dbfPorBase[base]
                )
            )
        }
        return resultado
    }

    // ── GeoPDF (extrae metadatos de nombre si están disponibles) ─────────────

    private fun importarGeoPdf(uri: Uri): List<EntidadImportada> {
        // Los GeoPDFs contienen geometría en metadatos XMP/GeoRef — sin biblioteca nativa
        // en Android para parsearlos. Devolver lista vacía con mensaje descriptivo.
        return emptyList()
    }

    // ── Utilidades ────────────────────────────────────────────────────────────

    private fun leerTexto(uri: Uri): String =
        context.contentResolver.openInputStream(uri)
            ?.use { BufferedReader(InputStreamReader(it)).readText() } ?: ""

    private fun obtenerNombreArchivo(uri: Uri): String {
        // Intentar obtener nombre real del ContentResolver
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (it.moveToFirst() && idx >= 0) return it.getString(idx) ?: ""
        }
        return uri.lastPathSegment ?: uri.toString()
    }
}
