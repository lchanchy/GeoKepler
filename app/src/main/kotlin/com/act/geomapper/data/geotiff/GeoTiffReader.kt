package com.act.geomapper.data.geotiff

import android.graphics.Bitmap
import android.graphics.Color
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Inflater
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** Lanzada cuando el TIFF no es un GeoTIFF válido o usa una variante no soportada (compresión, CRS, tamaño, etc). */
class GeoTiffUnsupportedException(message: String) : Exception(message)

data class GeoTiffResult(
    val bitmap: Bitmap,
    val norte : Double,
    val sur   : Double,
    val este  : Double,
    val oeste : Double
)

/**
 * Lector mínimo de GeoTIFF (baseline): TIFF 8 bits/canal, gris/RGB/RGBA, chunky,
 * sin comprimir / PackBits / Deflate / LZW, en tiras o en mosaicos (tiles).
 * Georreferenciación: ModelPixelScale+ModelTiepoint o ModelTransformation,
 * con CRS geográfico (grados), UTM WGS84 o MAGNA-SIRGAS (Colombia, IGAC).
 */
object GeoTiffReader {

    private data class Tag(val type: Int, val count: Long, val dataOffset: Long)
    private data class TmParams(
        val a: Double, val f: Double,
        val lat0Deg: Double, val lon0Deg: Double,
        val k0: Double, val fe: Double, val fn: Double
    )

    private const val WGS84_A = 6378137.0
    private const val WGS84_F = 1.0 / 298.257223563
    private const val GRS80_A = 6378137.0
    private const val GRS80_F = 1.0 / 298.257222101
    private const val MAGNA_LAT0 = 4.596200417

    // EPSG (Colombia, IGAC) — zonas MAGNA-SIRGAS y el Origen-Nacional (9377)
    private val MAGNA_ZONES = mapOf(
        3116 to TmParams(GRS80_A, GRS80_F, MAGNA_LAT0, -74.077750417, 1.0, 1_000_000.0, 1_000_000.0),
        3117 to TmParams(GRS80_A, GRS80_F, MAGNA_LAT0, -72.077750417, 1.0, 1_000_000.0, 1_000_000.0),
        3118 to TmParams(GRS80_A, GRS80_F, MAGNA_LAT0, -70.077750417, 1.0, 1_000_000.0, 1_000_000.0),
        3119 to TmParams(GRS80_A, GRS80_F, MAGNA_LAT0, -76.077750417, 1.0, 1_000_000.0, 1_000_000.0),
        3120 to TmParams(GRS80_A, GRS80_F, MAGNA_LAT0, -78.077750417, 1.0, 1_000_000.0, 1_000_000.0),
        3121 to TmParams(GRS80_A, GRS80_F, MAGNA_LAT0, -80.077750417, 1.0, 1_000_000.0, 1_000_000.0),
        9377 to TmParams(GRS80_A, GRS80_F, 4.0, -73.0, 0.9992, 5_000_000.0, 2_000_000.0)
    )

    private const val MAX_PIXELES = 12_000_000L

    fun leer(bytes: ByteArray): GeoTiffResult {
        if (bytes.size < 8) throw GeoTiffUnsupportedException("Archivo TIFF inválido o vacío")
        val buf = ByteBuffer.wrap(bytes)
        buf.order(
            when {
                bytes[0] == 0x49.toByte() && bytes[1] == 0x49.toByte() -> ByteOrder.LITTLE_ENDIAN
                bytes[0] == 0x4D.toByte() && bytes[1] == 0x4D.toByte() -> ByteOrder.BIG_ENDIAN
                else -> throw GeoTiffUnsupportedException("No es un archivo TIFF válido")
            }
        )
        val magic = buf.getShort(2).toInt() and 0xFFFF
        if (magic != 42) throw GeoTiffUnsupportedException("No es un archivo TIFF válido (o es BigTIFF, no soportado)")

        val ifdOffset = buf.getInt(4).toLong() and 0xFFFFFFFFL
        val ifd = parseIfd(buf, ifdOffset)

        val width  = readLongArray(buf, ifd[256] ?: throw GeoTiffUnsupportedException("TIFF sin ancho definido"))[0].toInt()
        val height = readLongArray(buf, ifd[257] ?: throw GeoTiffUnsupportedException("TIFF sin alto definido"))[0].toInt()

        val totalPixeles = width.toLong() * height.toLong()
        if (totalPixeles > MAX_PIXELES) {
            throw GeoTiffUnsupportedException(
                "La imagen es demasiado grande para el dispositivo (${width}x${height}). Redúzcala antes de importar."
            )
        }

        val bbox = extraerGeoreferencia(ifd, buf, width, height)
        val pixeles = decodificarPixeles(buf, ifd, width, height)
        var bitmap = Bitmap.createBitmap(pixeles, width, height, Bitmap.Config.ARGB_8888)

        // Reducir a un máximo razonable para el mapa (igual criterio que el importador de GeoPDF).
        // Se libera el bitmap de resolución completa apenas se obtiene el escalado para no
        // mantener dos copias grandes en memoria a la vez (riesgo de OOM en equipos de gama baja).
        val maxPx = 2048
        val escala = minOf(maxPx.toFloat() / width, maxPx.toFloat() / height, 1f)
        if (escala < 1f) {
            val w = (width * escala).toInt().coerceAtLeast(1)
            val h = (height * escala).toInt().coerceAtLeast(1)
            val escalado = Bitmap.createScaledBitmap(bitmap, w, h, true)
            bitmap.recycle()
            bitmap = escalado
        }

        return GeoTiffResult(bitmap, norte = bbox[0], sur = bbox[1], este = bbox[2], oeste = bbox[3])
    }

    // ── IFD / tags ────────────────────────────────────────────────────────────

    private fun typeSize(type: Int): Int = when (type) {
        1, 2, 6, 7 -> 1
        3, 8       -> 2
        4, 9, 11   -> 4
        5, 10, 12  -> 8
        else       -> 1
    }

    private fun parseIfd(buf: ByteBuffer, offset: Long): Map<Int, Tag> {
        val map = mutableMapOf<Int, Tag>()
        var pos = offset.toInt()
        val numEntries = buf.getShort(pos).toInt() and 0xFFFF
        pos += 2
        repeat(numEntries) {
            val tag   = buf.getShort(pos).toInt() and 0xFFFF
            val type  = buf.getShort(pos + 2).toInt() and 0xFFFF
            val count = buf.getInt(pos + 4).toLong() and 0xFFFFFFFFL
            val sz    = typeSize(type).toLong() * count
            val dataOffset = if (sz <= 4L) (pos + 8).toLong() else (buf.getInt(pos + 8).toLong() and 0xFFFFFFFFL)
            map[tag] = Tag(type, count, dataOffset)
            pos += 12
        }
        return map
    }

    private fun readLongArray(buf: ByteBuffer, tag: Tag): LongArray {
        val arr = LongArray(tag.count.toInt())
        var off = tag.dataOffset.toInt()
        for (i in arr.indices) {
            arr[i] = when (tag.type) {
                1, 6, 7 -> (buf.get(off).toLong() and 0xFF)
                3       -> (buf.getShort(off).toLong() and 0xFFFF)
                4       -> (buf.getInt(off).toLong() and 0xFFFFFFFFL)
                else    -> 0L
            }
            off += typeSize(tag.type)
        }
        return arr
    }

    private fun readDoubleArray(buf: ByteBuffer, tag: Tag): DoubleArray {
        val arr = DoubleArray(tag.count.toInt())
        var off = tag.dataOffset.toInt()
        for (i in arr.indices) { arr[i] = buf.getDouble(off); off += 8 }
        return arr
    }

    // ── Georreferenciación ───────────────────────────────────────────────────

    private fun parseGeoKeys(ifd: Map<Int, Tag>, buf: ByteBuffer): Map<Int, Int> {
        val dirTag = ifd[34735] ?: return emptyMap()
        val raw = readLongArray(buf, dirTag)
        if (raw.size < 4) return emptyMap()
        val numKeys = raw[3].toInt()
        val result = mutableMapOf<Int, Int>()
        for (i in 0 until numKeys) {
            val base = 4 + i * 4
            if (base + 3 >= raw.size) break
            val keyId       = raw[base].toInt()
            val tiffTagLoc  = raw[base + 1].toInt()
            val valueOffset = raw[base + 3].toInt()
            if (tiffTagLoc == 0) result[keyId] = valueOffset
        }
        return result
    }

    private fun extraerGeoreferencia(ifd: Map<Int, Tag>, buf: ByteBuffer, width: Int, height: Int): DoubleArray {
        val pixelScaleTag = ifd[33550]
        val tiepointTag   = ifd[33922]
        val transformTag  = ifd[34264]

        val minX: Double; val maxX: Double; val minY: Double; val maxY: Double

        when {
            transformTag != null -> {
                val m = readDoubleArray(buf, transformTag)
                fun aModelo(col: Double, row: Double) =
                    (m[0] * col + m[1] * row + m[3]) to (m[4] * col + m[5] * row + m[7])
                val esquinas = listOf(
                    aModelo(0.0, 0.0), aModelo(width.toDouble(), 0.0),
                    aModelo(0.0, height.toDouble()), aModelo(width.toDouble(), height.toDouble())
                )
                minX = esquinas.minOf { it.first };  maxX = esquinas.maxOf { it.first }
                minY = esquinas.minOf { it.second };  maxY = esquinas.maxOf { it.second }
            }
            pixelScaleTag != null && tiepointTag != null -> {
                val escala = readDoubleArray(buf, pixelScaleTag)
                val tie    = readDoubleArray(buf, tiepointTag)
                val i0 = tie[0]; val j0 = tie[1]; val x0 = tie[3]; val y0 = tie[4]
                val sx = escala[0]; val sy = escala[1]
                val origenX = x0 - i0 * sx
                val origenY = y0 + j0 * sy
                minX = origenX
                maxX = origenX + width * sx
                maxY = origenY
                minY = origenY - height * sy
            }
            else -> throw GeoTiffUnsupportedException(
                "El TIFF no tiene georreferenciación incrustada (no es un GeoTIFF)."
            )
        }

        val geoKeys  = parseGeoKeys(ifd, buf)
        val modelType = geoKeys[1024]
        val geogCode  = geoKeys[2048]
        val projCode  = geoKeys[3072]

        return when {
            modelType == 2 || (geogCode != null && geogCode != 32767) ->
                doubleArrayOf(maxY, minY, maxX, minX)

            projCode == 3857 -> {
                val (loMin, laMin) = webMercatorInverso(minX, minY)
                val (loMax, laMax) = webMercatorInverso(maxX, maxY)
                doubleArrayOf(laMax, laMin, loMax, loMin)
            }

            projCode != null && esUtmWgs84(projCode) -> {
                val params = utmParams(projCode)
                val esquinas = listOf(minX to minY, maxX to minY, minX to maxY, maxX to maxY)
                    .map { (x, y) -> inversaTransversaMercator(x, y, params) }
                doubleArrayOf(
                    esquinas.maxOf { it.second }, esquinas.minOf { it.second },
                    esquinas.maxOf { it.first },  esquinas.minOf { it.first }
                )
            }

            projCode != null && MAGNA_ZONES.containsKey(projCode) -> {
                val params = MAGNA_ZONES.getValue(projCode)
                val esquinas = listOf(minX to minY, maxX to minY, minX to maxY, maxX to maxY)
                    .map { (x, y) -> inversaTransversaMercator(x, y, params) }
                doubleArrayOf(
                    esquinas.maxOf { it.second }, esquinas.minOf { it.second },
                    esquinas.maxOf { it.first },  esquinas.minOf { it.first }
                )
            }

            else -> throw GeoTiffUnsupportedException("Sistema de coordenadas no soportado (EPSG:${projCode ?: "desconocido"}).")
        }
    }

    private fun esUtmWgs84(epsg: Int) = epsg in 32601..32660 || epsg in 32701..32760

    private fun utmParams(epsg: Int): TmParams {
        val sur  = epsg in 32701..32760
        val zone = if (sur) epsg - 32700 else epsg - 32600
        val lon0 = -183.0 + 6.0 * zone
        return TmParams(WGS84_A, WGS84_F, 0.0, lon0, 0.9996, 500_000.0, if (sur) 10_000_000.0 else 0.0)
    }

    private fun webMercatorInverso(x: Double, y: Double): Pair<Double, Double> {
        val r = 6378137.0
        val lon = Math.toDegrees(x / r)
        val lat = Math.toDegrees(2 * atan(exp(y / r)) - Math.PI / 2)
        return lon to lat
    }

    /** Fórmula estándar de Snyder para Mercator Transversa inversa (UTM y MAGNA-SIRGAS). */
    private fun inversaTransversaMercator(x: Double, y: Double, p: TmParams): Pair<Double, Double> {
        val a = p.a
        val f = p.f
        val e2 = f * (2 - f)
        val e1 = (1 - sqrt(1 - e2)) / (1 + sqrt(1 - e2))
        val ePrime2 = e2 / (1 - e2)
        val lat0 = Math.toRadians(p.lat0Deg)
        val lon0 = Math.toRadians(p.lon0Deg)

        fun arcoMeridiano(lat: Double) = a * (
            (1 - e2 / 4 - 3 * e2 * e2 / 64 - 5 * e2 * e2 * e2 / 256) * lat -
            (3 * e2 / 8 + 3 * e2 * e2 / 32 + 45 * e2 * e2 * e2 / 1024) * sin(2 * lat) +
            (15 * e2 * e2 / 256 + 45 * e2 * e2 * e2 / 1024) * sin(4 * lat) -
            (35 * e2 * e2 * e2 / 3072) * sin(6 * lat)
        )

        val m0 = arcoMeridiano(lat0)
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

    // ── Decodificación de píxeles ────────────────────────────────────────────

    private fun decodificarPixeles(buf: ByteBuffer, ifd: Map<Int, Tag>, width: Int, height: Int): IntArray {
        val bitsPerSample = readLongArray(buf, ifd[258] ?: throw GeoTiffUnsupportedException("TIFF sin BitsPerSample"))
        if (bitsPerSample.any { it != 8L }) {
            throw GeoTiffUnsupportedException("Solo se soportan TIFF de 8 bits por canal")
        }
        val samplesPerPixel = ifd[277]?.let { readLongArray(buf, it)[0] } ?: 1L
        if (samplesPerPixel !in longArrayOf(1L, 3L, 4L)) {
            throw GeoTiffUnsupportedException("Formato de color TIFF no soportado ($samplesPerPixel canales)")
        }
        val planarConfig = ifd[284]?.let { readLongArray(buf, it)[0] } ?: 1L
        if (planarConfig != 1L) {
            throw GeoTiffUnsupportedException("Configuración planar de TIFF no soportada")
        }
        val compression = ifd[259]?.let { readLongArray(buf, it)[0] } ?: 1L
        val photometric = ifd[262]?.let { readLongArray(buf, it)[0] } ?: 1L
        val spp = samplesPerPixel.toInt()

        val pixeles = IntArray(width * height)

        fun descomprimir(raw: ByteArray, esperado: Int): ByteArray = when (compression) {
            1L          -> raw
            32773L      -> packBitsDecode(raw, esperado)
            5L          -> lzwDecode(raw)
            8L, 32946L  -> deflateDecode(raw, esperado)
            else        -> throw GeoTiffUnsupportedException("Compresión TIFF no soportada (código $compression)")
        }

        fun escribirPixel(x: Int, y: Int, datos: ByteArray, off: Int) {
            if (x >= width || y >= height || off + spp > datos.size) return
            val argb = when (spp) {
                1 -> {
                    var v = datos[off].toInt() and 0xFF
                    if (photometric == 0L) v = 255 - v
                    Color.rgb(v, v, v)
                }
                3 -> Color.rgb(
                    datos[off].toInt() and 0xFF, datos[off + 1].toInt() and 0xFF, datos[off + 2].toInt() and 0xFF
                )
                else -> Color.argb(
                    datos[off + 3].toInt() and 0xFF,
                    datos[off].toInt() and 0xFF, datos[off + 1].toInt() and 0xFF, datos[off + 2].toInt() and 0xFF
                )
            }
            pixeles[y * width + x] = argb
        }

        val tileWidthTag = ifd[322]
        if (tileWidthTag != null) {
            val tileWidth  = readLongArray(buf, tileWidthTag)[0].toInt()
            val tileLength = readLongArray(buf, ifd[323]!!)[0].toInt()
            val tileOffsets = readLongArray(buf, ifd[324]!!)
            val tileByteCounts = readLongArray(buf, ifd[325]!!)
            val tilesAcross = (width + tileWidth - 1) / tileWidth
            val tilesDown   = (height + tileLength - 1) / tileLength
            val stride = tileWidth * spp
            val esperado = tileWidth * tileLength * spp

            for (ty in 0 until tilesDown) {
                for (tx in 0 until tilesAcross) {
                    val idx = ty * tilesAcross + tx
                    if (idx >= tileOffsets.size) continue
                    val off = tileOffsets[idx].toInt()
                    val len = tileByteCounts[idx].toInt()
                    val raw = ByteArray(len)
                    buf.position(off); buf.get(raw)
                    val decodificado = descomprimir(raw, esperado)
                    val x0 = tx * tileWidth
                    val y0 = ty * tileLength
                    val filas = min(tileLength, height - y0)
                    val cols  = min(tileWidth, width - x0)
                    for (row in 0 until filas) {
                        var srcOff = row * stride
                        for (col in 0 until cols) {
                            escribirPixel(x0 + col, y0 + row, decodificado, srcOff)
                            srcOff += spp
                        }
                    }
                }
            }
        } else {
            val rowsPerStrip = ifd[278]?.let { readLongArray(buf, it)[0].toInt() } ?: height
            val stripOffsets = readLongArray(buf, ifd[273] ?: throw GeoTiffUnsupportedException("TIFF sin StripOffsets"))
            val stripByteCounts = readLongArray(buf, ifd[279] ?: throw GeoTiffUnsupportedException("TIFF sin StripByteCounts"))
            val stride = width * spp

            for (s in stripOffsets.indices) {
                val y0 = s * rowsPerStrip
                if (y0 >= height) break
                val filas = min(rowsPerStrip, height - y0)
                val off = stripOffsets[s].toInt()
                val len = stripByteCounts[s].toInt()
                val raw = ByteArray(len)
                buf.position(off); buf.get(raw)
                val decodificado = descomprimir(raw, filas * stride)
                for (row in 0 until filas) {
                    var srcOff = row * stride
                    for (col in 0 until width) {
                        escribirPixel(col, y0 + row, decodificado, srcOff)
                        srcOff += spp
                    }
                }
            }
        }

        return pixeles
    }

    // ── Descompresores ───────────────────────────────────────────────────────

    private fun packBitsDecode(data: ByteArray, esperado: Int): ByteArray {
        val out = ByteArrayOutputStream(esperado)
        var i = 0
        while (i < data.size) {
            val n = data[i].toInt(); i++
            when {
                n in 0..127 -> {
                    val count = (n + 1).coerceAtMost(data.size - i)
                    out.write(data, i, count)
                    i += count
                }
                n in -127..-1 -> {
                    if (i < data.size) {
                        val b = data[i]; i++
                        repeat(-n + 1) { out.write(b.toInt()) }
                    }
                }
                else -> Unit // n == -128: sin operación
            }
        }
        return out.toByteArray()
    }

    private fun deflateDecode(data: ByteArray, esperado: Int): ByteArray {
        val inflater = Inflater()
        inflater.setInput(data)
        val out = ByteArrayOutputStream(esperado)
        val buffer = ByteArray(8192)
        while (!inflater.finished()) {
            val n = inflater.inflate(buffer)
            if (n == 0) {
                if (inflater.needsInput() || inflater.needsDictionary()) break
            } else {
                out.write(buffer, 0, n)
            }
        }
        inflater.end()
        return out.toByteArray()
    }

    private const val LZW_CLEAR = 256
    private const val LZW_EOI   = 257

    private fun lzwDecode(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(data.size * 3)
        var bitPos = 0

        fun leerBits(n: Int): Int {
            var result = 0
            for (i in 0 until n) {
                val bytePos = bitPos / 8
                if (bytePos >= data.size) return LZW_EOI
                val bit = (data[bytePos].toInt() shr (7 - (bitPos % 8))) and 1
                result = (result shl 1) or bit
                bitPos++
            }
            return result
        }

        var codeSize = 9
        var dictionary = ArrayList<ByteArray>(4096)
        fun reiniciarDiccionario() {
            dictionary = ArrayList(4096)
            for (i in 0..255) dictionary.add(byteArrayOf(i.toByte()))
            dictionary.add(ByteArray(0)) // 256 CLEAR
            dictionary.add(ByteArray(0)) // 257 EOI
            codeSize = 9
        }
        reiniciarDiccionario()

        var previo: ByteArray? = null
        while (true) {
            val code = leerBits(codeSize)
            if (code == LZW_EOI) break
            if (code == LZW_CLEAR) { reiniciarDiccionario(); previo = null; continue }

            val entrada: ByteArray = when {
                code < dictionary.size -> dictionary[code]
                code == dictionary.size && previo != null -> previo + previo[0]
                else -> throw GeoTiffUnsupportedException("Secuencia LZW inválida")
            }
            out.write(entrada)
            if (previo != null) dictionary.add(previo + entrada[0])
            previo = entrada

            codeSize = when {
                dictionary.size >= 2046 -> 12
                dictionary.size >= 1022 -> 11
                dictionary.size >= 510  -> 10
                else -> 9
            }
        }
        return out.toByteArray()
    }
}
