package com.act.geomapper.data.gnss

data class GnssFix(
    val latitud: Double,
    val longitud: Double,
    val altitud: Double = 0.0,
    val hdop: Float = 99f,
    val vdop: Float = 99f,
    val pdop: Float = 99f,
    val satelites: Int = 0,
    // GGA fix quality: 0=sin fix, 1=GPS, 2=DGPS, 4=RTK fijo, 5=RTK flotante
    val calidad: Int = 0,
    val errorLatM: Double = 0.0,  // GST sigma latitud (m)
    val errorLonM: Double = 0.0,  // GST sigma longitud (m)
    val errorAltM: Double = 0.0,  // GST sigma altitud (m)
    val velocidadMs: Float = 0f,  // RMC velocidad (m/s)
    val rumbo: Float = 0f,        // RMC rumbo (°)
    val timestamp: Long = System.currentTimeMillis()
)
