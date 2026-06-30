package com.act.geomapper.data.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.act.geomapper.domain.models.Predio
import com.act.geomapper.domain.models.Proyecto
import org.locationtech.jts.io.WKTReader
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class ExportFormat { GEOJSON, KML, GPX }

class ExportService(private val context: Context) {

    private val wktReader = WKTReader()
    private val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    fun exportar(
        proyecto: Proyecto,
        predios: List<Predio>,
        formato: ExportFormat
    ): Uri {
        val fechaStr = sdf.format(Date())
        val nombreBase = "${proyecto.nombre.replace(" ", "_")}_$fechaStr"
        val outDir = File(context.cacheDir, "export").also { it.mkdirs() }

        val file = when (formato) {
            ExportFormat.GEOJSON -> exportarGeoJson(predios, outDir, "$nombreBase.geojson")
            ExportFormat.KML     -> exportarKml(proyecto, predios, outDir, "$nombreBase.kml")
            ExportFormat.GPX     -> exportarGpx(predios, outDir, "$nombreBase.gpx")
        }

        return FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    }

    fun exportarZip(proyecto: Proyecto, predios: List<Predio>): Uri {
        val fechaStr = sdf.format(Date())
        val nombreBase = "${proyecto.nombre.replace(" ", "_")}_$fechaStr"
        val outDir = File(context.cacheDir, "export").also { it.mkdirs() }

        val geoJson = exportarGeoJson(predios, outDir, "$nombreBase.geojson")
        val kml     = exportarKml(proyecto, predios, outDir, "$nombreBase.kml")

        val zipFile = File(outDir, "$nombreBase.zip")
        ZipOutputStream(zipFile.outputStream()).use { zip ->
            listOf(geoJson, kml).forEach { f ->
                zip.putNextEntry(ZipEntry(f.name))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.provider", zipFile)
    }

    fun compartir(uri: Uri, nombreArchivo: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type     = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "GeoKepler — $nombreArchivo")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Compartir vía").also {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    // ── Formatos ────────────────────────────────────────────────────────────

    private fun exportarGeoJson(predios: List<Predio>, dir: File, nombre: String): File {
        val features = predios.joinToString(",\n") { p ->
            val geom = p.geometry
            val coordsJson = geomToGeoJsonCoords(geom)
            val tipo = when {
                geom.geometryType == "Point"   -> "Point"
                geom.geometryType == "LineString" -> "LineString"
                else -> "Polygon"
            }
            """
            {
              "type": "Feature",
              "properties": {
                "id": ${p.id},
                "nombre": "${p.nombre}",
                "propietario": "${p.propietario}",
                "area_ha": ${p.area},
                "perimetro_m": ${p.perimetro},
                "tipo": "${p.tipo}"
              },
              "geometry": { "type": "$tipo", "coordinates": $coordsJson }
            }""".trimIndent()
        }
        val content = """{"type":"FeatureCollection","features":[$features]}"""
        return File(dir, nombre).also { it.writeText(content) }
    }

    private fun exportarKml(proyecto: Proyecto, predios: List<Predio>, dir: File, nombre: String): File {
        val placemarks = predios.joinToString("\n") { p ->
            val coords = p.geometry.coordinates.joinToString(" ") { c -> "${c.x},${c.y},0" }
            """
            <Placemark>
              <name>${p.nombre}</name>
              <description>${p.propietario} · ${p.area} ha</description>
              <Polygon><outerBoundaryIs><LinearRing><coordinates>$coords</coordinates></LinearRing></outerBoundaryIs></Polygon>
            </Placemark>""".trimIndent()
        }
        val content = """<?xml version="1.0" encoding="UTF-8"?>
<kml xmlns="http://www.opengis.net/kml/2.2">
  <Document><name>${proyecto.nombre}</name>$placemarks</Document>
</kml>"""
        return File(dir, nombre).also { it.writeText(content) }
    }

    private fun exportarGpx(predios: List<Predio>, dir: File, nombre: String): File {
        val waypoints = predios.joinToString("\n") { p ->
            val c = p.geometry.centroid.coordinate
            """<wpt lat="${c.y}" lon="${c.x}"><name>${p.nombre}</name><desc>${p.propietario}</desc></wpt>"""
        }
        val content = """<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.1" creator="GeoKepler">$waypoints</gpx>"""
        return File(dir, nombre).also { it.writeText(content) }
    }

    private fun geomToGeoJsonCoords(geom: org.locationtech.jts.geom.Geometry): String {
        val coords = geom.coordinates
        return when (geom.geometryType) {
            "Point"      -> "[${coords[0].x},${coords[0].y}]"
            "LineString" -> "[${coords.joinToString(",") { "[${it.x},${it.y}]" }}]"
            else         -> "[[${coords.joinToString(",") { "[${it.x},${it.y}]" }}]]"
        }
    }
}
