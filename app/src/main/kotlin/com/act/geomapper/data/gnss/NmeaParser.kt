package com.act.geomapper.data.gnss

class NmeaParser {

    // Estado acumulado — se actualiza con GST/GSA/RMC, se emite con GGA
    private var errLat = 0.0
    private var errLon = 0.0
    private var errAlt = 0.0
    private var pdop   = 99f
    private var hdop   = 99f
    private var vdop   = 99f
    private var speed  = 0f
    private var course = 0f

    /**
     * Parsea una línea NMEA. Retorna GnssFix solo cuando procesa GGA con fix válido.
     * Las demás sentencias (GST, GSA, RMC) acumulan datos para el próximo GGA.
     * Soporta talker IDs GP (GPS) y GN (multi-constelación).
     */
    fun parsear(linea: String): GnssFix? {
        val trim = linea.trim()
        if (!trim.startsWith('$')) return null
        if (!validarChecksum(trim)) return null
        val datos = trim.substringBefore('*').split(',')
        if (datos.isEmpty()) return null
        return when {
            datos[0].endsWith("GGA") -> parseGGA(datos)
            datos[0].endsWith("GST") -> { parseGST(datos); null }
            datos[0].endsWith("GSA") -> { parseGSA(datos); null }
            datos[0].endsWith("RMC") -> { parseRMC(datos); null }
            else                     -> null
        }
    }

    private fun validarChecksum(linea: String): Boolean {
        val inicio = linea.indexOf('$').takeIf { it >= 0 } ?: return false
        val fin    = linea.indexOf('*', inicio).takeIf { it >= 0 } ?: return false
        if (fin + 2 >= linea.length) return false
        val esperado = linea.substring(fin + 1, fin + 3).toIntOrNull(16) ?: return false
        var xor = 0
        for (i in inicio + 1 until fin) xor = xor xor linea[i].code
        return xor == esperado
    }

    // $GP/GNGGA,hhmmss.ss,Lat,N,Lon,E,Calidad,NSat,HDOP,Alt,M,...
    private fun parseGGA(p: List<String>): GnssFix? {
        if (p.size < 10) return null
        val calidad = p.getOrNull(6)?.toIntOrNull() ?: return null
        if (calidad == 0) return null
        val lat  = parseLat(p.getOrNull(2) ?: "", p.getOrNull(3) ?: "") ?: return null
        val lon  = parseLon(p.getOrNull(4) ?: "", p.getOrNull(5) ?: "") ?: return null
        val sats = p.getOrNull(7)?.toIntOrNull() ?: 0
        val h    = p.getOrNull(8)?.toFloatOrNull() ?: hdop
        val alt  = p.getOrNull(9)?.toDoubleOrNull() ?: 0.0
        hdop = h
        return GnssFix(
            latitud     = lat,
            longitud    = lon,
            altitud     = alt,
            hdop        = h,
            vdop        = vdop,
            pdop        = pdop,
            satelites   = sats,
            calidad     = calidad,
            errorLatM   = errLat,
            errorLonM   = errLon,
            errorAltM   = errAlt,
            velocidadMs = speed,
            rumbo       = course
        )
    }

    // $GP/GNGST,UTC,RMS,SemiMaj,SemiMin,Orient,SigLat,SigLon,SigAlt
    private fun parseGST(p: List<String>) {
        errLat = p.getOrNull(6)?.toDoubleOrNull() ?: errLat
        errLon = p.getOrNull(7)?.toDoubleOrNull() ?: errLon
        errAlt = p.getOrNull(8)?.toDoubleOrNull() ?: errAlt
    }

    // $GP/GNGSA,A,FixType,PRN...,PDOP,HDOP,VDOP
    private fun parseGSA(p: List<String>) {
        pdop = p.getOrNull(15)?.toFloatOrNull() ?: pdop
        hdop = p.getOrNull(16)?.toFloatOrNull() ?: hdop
        vdop = p.getOrNull(17)?.substringBefore('*')?.toFloatOrNull() ?: vdop
    }

    // $GP/GNRMC,UTC,A,Lat,N,Lon,E,SpeedKn,Course,Date,...
    private fun parseRMC(p: List<String>) {
        if (p.getOrNull(2) != "A") return  // V = void
        val kts = p.getOrNull(7)?.toFloatOrNull() ?: return
        speed  = kts * 0.514444f  // nudos → m/s
        course = p.getOrNull(8)?.toFloatOrNull() ?: course
    }

    // ddmm.mmmm → grados decimales
    private fun parseLat(value: String, ns: String): Double? {
        if (value.length < 4) return null
        val deg = value.substring(0, 2).toDoubleOrNull() ?: return null
        val min = value.substring(2).toDoubleOrNull() ?: return null
        val dec = deg + min / 60.0
        return if (ns.equals("S", ignoreCase = true)) -dec else dec
    }

    // dddmm.mmmm → grados decimales
    private fun parseLon(value: String, ew: String): Double? {
        if (value.length < 5) return null
        val deg = value.substring(0, 3).toDoubleOrNull() ?: return null
        val min = value.substring(3).toDoubleOrNull() ?: return null
        val dec = deg + min / 60.0
        return if (ew.equals("W", ignoreCase = true)) -dec else dec
    }
}
