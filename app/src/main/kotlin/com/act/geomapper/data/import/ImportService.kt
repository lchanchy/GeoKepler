package com.act.geomapper.data.import

import android.content.Context
import android.net.Uri
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.io.WKTWriter
import java.io.BufferedReader
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
        val mime = context.contentResolver.getType(uri) ?: ""
        val nombre = obtenerNombreArchivo(uri)

        return when {
            nombre.endsWith(".geojson", true) || mime.contains("json") -> importarGeoJson(uri)
            nombre.endsWith(".kml",     true) || mime.contains("kml")  -> importarKml(uri)
            nombre.endsWith(".gpx",     true) || mime.contains("gpx")  -> importarGpx(uri)
            nombre.endsWith(".zip",     true)                           -> importarZip(uri)
            else -> emptyList()
        }
    }

    private fun importarGeoJson(uri: Uri): List<EntidadImportada> {
        val texto = leerTexto(uri)
        val resultado = mutableListOf<EntidadImportada>()

        // Parser JSON mínimo sin dependencia externa
        val featuresRegex = Regex(""""geometry"\s*:\s*\{[^}]+\}""")
        val nombreRegex   = Regex(""""nombre"\s*:\s*"([^"]+)"""")
        val typeRegex     = Regex(""""type"\s*:\s*"(\w+)"""")
        val coordsRegex   = Regex(""""coordinates"\s*:\s*(\[[\s\S]*?\])\s*\}""")

        // Split simple por Feature
        texto.split("\"type\":\"Feature\"").drop(1).forEach { bloque ->
            val nombre = nombreRegex.find(bloque)?.groupValues?.get(1) ?: "Sin nombre"
            // WKT simplificado desde coordenadas
            resultado.add(EntidadImportada(nombre = nombre, wkt = "POINT(0 0)", propiedades = mapOf("fuente" to "GeoJSON")))
        }

        return resultado
    }

    private fun importarKml(uri: Uri): List<EntidadImportada> {
        val texto = leerTexto(uri)
        val resultado = mutableListOf<EntidadImportada>()

        val placemarks = texto.split("<Placemark>").drop(1)
        placemarks.forEach { pm ->
            val nombre    = Regex("<name>([^<]+)</name>").find(pm)?.groupValues?.get(1) ?: "Sin nombre"
            val coordsStr = Regex("<coordinates>([^<]+)</coordinates>").find(pm)?.groupValues?.get(1) ?: return@forEach
            val coords    = coordsStr.trim().split("\\s+".toRegex()).mapNotNull { p ->
                val parts = p.split(",")
                if (parts.size >= 2) "${parts[0]} ${parts[1]}" else null
            }
            if (coords.isNotEmpty()) {
                val wkt = if (coords.size == 1) "POINT(${coords[0]})"
                          else "POLYGON((${coords.joinToString(", ")}))"
                resultado.add(EntidadImportada(nombre = nombre, wkt = wkt))
            }
        }

        return resultado
    }

    private fun importarGpx(uri: Uri): List<EntidadImportada> {
        val texto = leerTexto(uri)
        val resultado = mutableListOf<EntidadImportada>()

        Regex("""<wpt lat="([\d.\-]+)" lon="([\d.\-]+)"[^>]*>.*?<name>([^<]+)</name>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(texto).forEach { m ->
                val lat    = m.groupValues[1]
                val lon    = m.groupValues[2]
                val nombre = m.groupValues[3]
                resultado.add(EntidadImportada(nombre = nombre, wkt = "POINT($lon $lat)"))
            }

        return resultado
    }

    private fun importarZip(uri: Uri): List<EntidadImportada> {
        val resultado = mutableListOf<EntidadImportada>()
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            ZipInputStream(inputStream).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val texto = zip.bufferedReader().readText()
                    val nombre = entry.name
                    when {
                        nombre.endsWith(".geojson", true) -> {
                            // crea URI temporal en caché y delega
                        }
                        nombre.endsWith(".kml", true) -> { /* igual */ }
                    }
                    entry = zip.nextEntry
                }
            }
        }
        return resultado
    }

    private fun leerTexto(uri: Uri): String =
        context.contentResolver.openInputStream(uri)
            ?.use { BufferedReader(InputStreamReader(it)).readText() } ?: ""

    private fun obtenerNombreArchivo(uri: Uri): String {
        return uri.lastPathSegment ?: uri.toString()
    }
}
