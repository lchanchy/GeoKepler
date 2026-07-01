package com.act.geomapper.data.gnss

import android.util.Base64
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.IOException
import java.net.Socket

sealed class NtripEstado {
    object Desconectado : NtripEstado()
    object Conectando   : NtripEstado()
    data class Conectado(val host: String, val mountpoint: String) : NtripEstado()
    data class Error(val mensaje: String) : NtripEstado()
}

data class NtripConfig(
    val host       : String,
    val puerto     : Int    = 2101,
    val mountpoint : String,
    val usuario    : String = "",
    val contrasena : String = ""
)

class NtripClient(private val btService: GnssBluetoothService) {

    private val RECONEXION_DELAY_MS = 5_000L
    private val MAX_REINTENTOS      = 3
    private val BUFFER_SIZE         = 4096
    private val USER_AGENT          = "NTRIP GeoKepler/1.0"

    private val scope          = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _estado        = MutableStateFlow<NtripEstado>(NtripEstado.Desconectado)
    val estado: StateFlow<NtripEstado> = _estado.asStateFlow()

    private val _bytesRecibidos = MutableStateFlow(0L)
    val bytesRecibidos: StateFlow<Long> = _bytesRecibidos.asStateFlow()

    private val _velocidadKbs = MutableStateFlow(0f)
    val velocidadKbs: StateFlow<Float> = _velocidadKbs.asStateFlow()

    private var conexionJob: Job? = null

    fun conectar(config: NtripConfig) {
        conexionJob?.cancel()
        conexionJob = scope.launch {
            var intentos = 0
            while (isActive && intentos < MAX_REINTENTOS) {
                _estado.value = NtripEstado.Conectando
                var socket: Socket? = null
                try {
                    socket = Socket(config.host, config.puerto)
                    socket.soTimeout = 15_000

                    // Request NTRIP v1
                    val credencial = if (config.usuario.isNotBlank()) {
                        Base64.encodeToString(
                            "${config.usuario}:${config.contrasena}".toByteArray(Charsets.UTF_8),
                            Base64.NO_WRAP
                        )
                    } else null

                    val request = buildString {
                        append("GET /${config.mountpoint} HTTP/1.0\r\n")
                        append("Host: ${config.host}:${config.puerto}\r\n")
                        append("User-Agent: $USER_AGENT\r\n")
                        credencial?.let { append("Authorization: Basic $it\r\n") }
                        append("Connection: close\r\n")
                        append("\r\n")
                    }
                    val rawIn = socket.inputStream
                    socket.outputStream.write(request.toByteArray(Charsets.US_ASCII))
                    socket.outputStream.flush()

                    // Leer cabeceras HTTP byte a byte hasta \r\n\r\n (no pierde datos RTCM)
                    val header = StringBuilder()
                    var state  = 0
                    while (state < 4) {
                        val b = rawIn.read()
                        if (b == -1) throw IOException("Conexión cerrada en cabeceras")
                        header.append(b.toChar())
                        state = when {
                            b == '\r'.code -> if (state == 0 || state == 2) state + 1 else 1
                            b == '\n'.code -> if (state == 1 || state == 3) state + 1 else 0
                            else           -> 0
                        }
                    }
                    val respuesta = header.toString()
                    if (!respuesta.contains("200") && !respuesta.startsWith("ICY")) {
                        throw IOException("NTRIP error: ${respuesta.lines().firstOrNull()?.take(80)}")
                    }

                    // Enviar GGA al caster si hay fix disponible (necesario para VRS)
                    btService.fixActual.value?.let { fix ->
                        val gga = generarGGA(fix)
                        runCatching {
                            socket.outputStream.write(gga.toByteArray(Charsets.US_ASCII))
                            socket.outputStream.flush()
                        }
                    }

                    // Conectado — resetear timeout para stream continuo
                    socket.soTimeout = 30_000
                    _estado.value = NtripEstado.Conectado(config.host, config.mountpoint)
                    _bytesRecibidos.value = 0L
                    _velocidadKbs.value   = 0f
                    intentos = 0

                    // Leer stream RTCM, reenviar al receptor BT y contabilizar stats
                    val buffer             = ByteArray(BUFFER_SIZE)
                    var ultimaTs           = System.currentTimeMillis()
                    var bytesEnVentana     = 0L
                    while (isActive) {
                        val n = rawIn.read(buffer)
                        if (n <= 0) break
                        btService.enviarDatos(buffer.copyOf(n))
                        _bytesRecibidos.value += n
                        bytesEnVentana += n
                        val ahora = System.currentTimeMillis()
                        if (ahora - ultimaTs >= 1000L) {
                            _velocidadKbs.value = bytesEnVentana / 1024f / ((ahora - ultimaTs) / 1000f)
                            bytesEnVentana = 0L
                            ultimaTs       = ahora
                        }
                    }

                } catch (e: Exception) {
                    if (!isActive) break
                    intentos++
                    if (intentos >= MAX_REINTENTOS) {
                        _estado.value = NtripEstado.Error(e.message ?: "Error NTRIP")
                        break
                    }
                    delay(RECONEXION_DELAY_MS)
                } finally {
                    runCatching { socket?.close() }
                }
            }
            if (_estado.value is NtripEstado.Conectando) {
                _estado.value = NtripEstado.Desconectado
            }
        }
    }

    fun desconectar() {
        conexionJob?.cancel()
        conexionJob         = null
        _estado.value       = NtripEstado.Desconectado
        _bytesRecibidos.value = 0L
        _velocidadKbs.value   = 0f
    }

    fun release() {
        desconectar()
        scope.cancel()
    }

    // GGA mínimo para enviar posición al caster VRS
    private fun generarGGA(fix: GnssFix): String {
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        val hora = "%02d%02d%05.2f".format(
            cal.get(java.util.Calendar.HOUR_OF_DAY),
            cal.get(java.util.Calendar.MINUTE),
            cal.get(java.util.Calendar.SECOND).toDouble()
        )
        val latAbs = Math.abs(fix.latitud)
        val latMin = (latAbs - latAbs.toInt()) * 60.0
        val lonAbs = Math.abs(fix.longitud)
        val lonMin = (lonAbs - lonAbs.toInt()) * 60.0
        val cuerpo = "GPGGA,$hora,%02d%08.5f,%s,%03d%08.5f,%s,%d,%02d,%.1f,%.1f,M,0.0,M,,".format(
            latAbs.toInt(), latMin, if (fix.latitud >= 0) "N" else "S",
            lonAbs.toInt(), lonMin, if (fix.longitud >= 0) "E" else "W",
            fix.calidad, fix.satelites, fix.hdop, fix.altitud
        )
        var cs = 0
        cuerpo.forEach { cs = cs xor it.code }
        return "\$$cuerpo*%02X\r\n".format(cs)
    }
}
