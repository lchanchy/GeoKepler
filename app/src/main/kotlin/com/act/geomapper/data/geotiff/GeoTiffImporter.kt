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
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw GeoTiffUnsupportedException("No se pudo abrir el archivo")
        stream.use {
            val bytes = it.readBytes()
            if (bytes.size > maxBytes) {
                throw GeoTiffUnsupportedException(
                    "El archivo es demasiado grande (${bytes.size / (1024 * 1024)} MB). Máximo soportado: 80 MB."
                )
            }
            return bytes
        }
    }

    private fun resolverNombre(uri: Uri): String {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && idx >= 0) cursor.getString(idx)?.let { return it }
        }
        return uri.lastPathSegment ?: "GeoTIFF"
    }
}
