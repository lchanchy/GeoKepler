package com.act.geomapper.data.gnss

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface IGnssDevice {
    val lineaRaw : SharedFlow<String>
    val fixActual: StateFlow<GnssFix?>
    fun enviarDatos(data: ByteArray)
}
