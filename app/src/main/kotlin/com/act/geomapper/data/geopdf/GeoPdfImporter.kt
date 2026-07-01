package com.act.geomapper.data.geopdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeoPdfImporter(private val context: Context) {

    suspend fun importar(uri: Uri): GeoPdfData? = withContext(Dispatchers.IO) {
        val bbox   = extraerBbox(uri)   ?: return@withContext null
        val bitmap = renderPagina(uri)  ?: return@withContext null
        val nombre = resolverNombre(uri)
        GeoPdfData(nombre, bitmap, norte = bbox[0], sur = bbox[1], este = bbox[2], oeste = bbox[3])
    }

    private fun resolverNombre(uri: Uri): String {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && idx >= 0) cursor.getString(idx)?.let { return it }
        }
        return uri.lastPathSegment ?: "GeoPDF"
    }

    /**
     * Busca /GPTS en los bytes crudos del PDF (OGC GeoPDF spec).
     * Formato: /GPTS [lat0 lon0 lat1 lon1 lat2 lon2 lat3 lon3]
     * Retorna [norte, sur, este, oeste].
     */
    private fun extraerBbox(uri: Uri): DoubleArray? {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val bloque   = ByteArray(512 * 1024)
            val sb       = StringBuilder()
            val limiteB  = 10 * 1024 * 1024      // buscar en primeros 10 MB
            var leido    = 0
            var hallado  = false

            while (leido < limiteB) {
                val n = stream.read(bloque)
                if (n < 0) break
                // Conservar últimas 120 chars para no perder tokens en límite de bloque
                val cola = if (sb.length > 120) sb.substring(sb.length - 120) else sb.toString()
                sb.clear()
                sb.append(cola)
                sb.append(String(bloque, 0, n, Charsets.ISO_8859_1))
                leido += n
                if (sb.contains("/GPTS")) { hallado = true; break }
            }
            if (!hallado) return null

            val texto    = sb.toString()
            val idx      = texto.lastIndexOf("/GPTS").takeIf { it >= 0 } ?: return null
            val arrStart = texto.indexOf('[', idx).takeIf { it >= 0 } ?: return null
            val arrEnd   = texto.indexOf(']', arrStart).takeIf { it >= 0 } ?: return null
            val nums     = Regex("""[-\d.]+""")
                .findAll(texto.substring(arrStart + 1, arrEnd))
                .mapNotNull { it.value.toDoubleOrNull() }.toList()
            if (nums.size < 8) return null

            // GPTS: lat0 lon0 lat1 lon1 ... (pares lat, lon)
            val lats = (0 until nums.size step 2).map { nums[it] }
            val lons = (1 until nums.size step 2).map { nums[it] }
            return doubleArrayOf(lats.max(), lats.min(), lons.max(), lons.min())
        }
        return null
    }

    private fun renderPagina(uri: Uri): Bitmap? {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
        return try {
            pfd.use {
                PdfRenderer(it).use { renderer ->
                    renderer.openPage(0).use { page ->
                        // Escalar a máximo 2048 px (evita OOM en PDFs grandes)
                        val maxPx = 2048
                        val scale = minOf(
                            maxPx.toFloat() / page.width,
                            maxPx.toFloat() / page.height,
                            3f
                        )
                        val w   = (page.width  * scale).toInt().coerceAtLeast(1)
                        val h   = (page.height * scale).toInt().coerceAtLeast(1)
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)   // PdfRenderer dibuja sobre fondo transparente
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                        bmp
                    }
                }
            }
        } catch (_: Exception) { null }
    }
}
