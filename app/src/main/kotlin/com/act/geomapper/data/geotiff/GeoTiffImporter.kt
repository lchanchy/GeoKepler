package com.act.geomapper.data.geotiff

import android.content.Context
import android.net.Uri
import com.act.geomapper.data.geopdf.GeoPdfData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeoTiffImporter(private val context: Context) {

    private val maxBytes = 80 * 1024 * 1024 // 80 MB

    suspend fun importar(uri: Uri): GeoPdfData = withContext(Dispatchers.IO) {
        val bytes = leerBytes(uri)
        val resultado = GeoTiffReader.leer(bytes)
        GeoPdfData(
            nombre = resolverNombre(uri),
            bitmap = resultado.bitmap,
            norte  = resultado.norte,
            sur    = resultado.sur,
            este   = resultado.este,
            oeste  = resultado.oeste
        )
    }

    private fun leerBytes(uri: Uri): ByteArray {
        // Rechazar por tamaño declarado ANTES de tocar el contenido — evita el OOM
        // que provocaba readBytes() intentando cargar archivos enormes de una vez.
        val tamanoDeclarado = obtenerTamano(uri)
        if (tamanoDeclarado != null && tamanoDeclarado > maxBytes) {
            throw GeoTiffUnsupportedException(
                "El archivo es demasiado grande (${tamanoDeclarado / (1024 * 1024)} MB). Máximo soportado: ${maxBytes / (1024 * 1024)} MB."
            )
        }
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw GeoTiffUnsupportedException("No se pudo abrir el archivo")
        stream.use {
            // Lectura acotada por bloques: nunca se aloja más de maxBytes, ni siquiera
            // si el proveedor no reportó el tamaño real (respaldo defensivo).
            val buffer = java.io.ByteArrayOutputStream(
                minOf(tamanoDeclarado ?: (8L * 1024 * 1024), maxBytes.toLong()).toInt()
            )
            val chunk = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val n = it.read(chunk)
                if (n < 0) break
                total += n
                if (total > maxBytes) {
                    throw GeoTiffUnsupportedException(
                        "El archivo es demasiado grande. Máximo soportado: ${maxBytes / (1024 * 1024)} MB."
                    )
                }
                buffer.write(chunk, 0, n)
            }
            return buffer.toByteArray()
        }
    }

    private fun obtenerTamano(uri: Uri): Long? {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
            if (cursor.moveToFirst() && idx >= 0 && !cursor.isNull(idx)) return cursor.getLong(idx)
        }
        return null
    }

    private fun resolverNombre(uri: Uri): String {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && idx >= 0) cursor.getString(idx)?.let { return it }
        }
        return uri.lastPathSegment ?: "GeoTIFF"
    }
}
