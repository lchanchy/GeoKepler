package com.act.geomapper.data.geotiff

import android.content.Context
import android.net.Uri
import com.act.geomapper.data.geopdf.GeoPdfData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.nio.channels.FileChannel

class GeoTiffImporter(private val context: Context) {

    // Con mapeo en memoria el archivo NUNCA se carga completo a RAM (lo pagina el SO),
    // así que el límite es solo para evitar procesar archivos absurdos, no por memoria.
    private val maxBytes = 1024L * 1024 * 1024 // 1 GB

    suspend fun importar(uri: Uri): GeoPdfData = withContext(Dispatchers.IO) {
        val nombre = resolverNombre(uri)
        val resultado = context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            FileInputStream(pfd.fileDescriptor).channel.use { canal ->
                val tamano = canal.size()
                if (tamano > maxBytes) {
                    throw GeoTiffUnsupportedException(
                        "El archivo es demasiado grande (${tamano / (1024 * 1024)} MB). Máximo soportado: ${maxBytes / (1024 * 1024)} MB."
                    )
                }
                val buffer = canal.map(FileChannel.MapMode.READ_ONLY, 0, tamano)
                GeoTiffReader.leer(buffer)
            }
        } ?: throw GeoTiffUnsupportedException("No se pudo abrir el archivo")

        GeoPdfData(
            nombre = nombre,
            bitmap = resultado.bitmap,
            norte  = resultado.norte,
            sur    = resultado.sur,
            este   = resultado.este,
            oeste  = resultado.oeste
        )
    }

    private fun resolverNombre(uri: Uri): String {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && idx >= 0) cursor.getString(idx)?.let { return it }
        }
        return uri.lastPathSegment ?: "GeoTIFF"
    }
}
