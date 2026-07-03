package com.act.geomapper.data.gnss

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

sealed class WifiGnssEstado {
    object Desconectado : WifiGnssEstado()
    object Conectando   : WifiGnssEstado()
    data class Conectado(val host: String, val puerto: Int) : WifiGnssEstado()
    data class Error(val mensaje: String)                   : WifiGnssEstado()
}

class GnssWifiService : IGnssDevice {

    private val TIMEOUT_CONNECT_MS = 8_000
    private val TIMEOUT_READ_MS    = 15_000
    private val RECONEXION_DELAY   = 3_000L
    private val MAX_REINTENTOS     = 5

    private val scope      = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val parser     = NmeaParser()
    private val writeMutex = Mutex()

    @Volatile private var socketOutput: OutputStream? = null

    private val _estado = MutableStateFlow<WifiGnssEstado>(WifiGnssEstado.Desconectado)
    val estado: StateFlow<WifiGnssEstado> = _estado.asStateFlow()

    private val _fixActual = MutableStateFlow<GnssFix?>(null)
    override val fixActual: StateFlow<GnssFix?> = _fixActual.asStateFlow()

    private val _lineaRaw = MutableSharedFlow<String>(
        extraBufferCapacity = 4096,
        onBufferOverflow    = BufferOverflow.DROP_OLDEST
    )
    override val lineaRaw: SharedFlow<String> = _lineaRaw.asSharedFlow()

    private var conexionJob: Job? = null

    fun conectar(ip: String, puerto: Int) {
        conexionJob?.cancel()
        conexionJob = scope.launch {
            var intentos = 0
            while (isActive && intentos < MAX_REINTENTOS) {
                _estado.value = WifiGnssEstado.Conectando
                var socket: Socket? = null
                try {
                    socket = Socket()
                    socket.connect(InetSocketAddress(ip, puerto), TIMEOUT_CONNECT_MS)
                    socket.soTimeout = TIMEOUT_READ_MS
                    socketOutput = socket.outputStream
                    _estado.value = WifiGnssEstado.Conectado(ip, puerto)
                    intentos = 0
                    BufferedReader(InputStreamReader(socket.inputStream)).use { reader ->
                        while (isActive) {
                            val linea = reader.readLine() ?: break
                            _lineaRaw.tryEmit(linea)
                            parser.parsear(linea)?.let { _fixActual.value = it }
                        }
                    }
                } catch (e: Exception) {
                    if (!isActive) break
                    intentos++
                    if (intentos >= MAX_REINTENTOS) {
                        _estado.value = WifiGnssEstado.Error(e.message ?: "Error WiFi TCP")
                        break
                    }
                    delay(RECONEXION_DELAY)
                } finally {
                    socketOutput = null
                    runCatching { socket?.close() }
                }
            }
            if (_estado.value is WifiGnssEstado.Conectando) {
                _estado.value = WifiGnssEstado.Desconectado
            }
        }
    }

    /** Envía datos binarios (RTCM) al receptor GNSS vía la misma conexión TCP. */
    override fun enviarDatos(data: ByteArray) {
        scope.launch {
            writeMutex.withLock {
                runCatching {
                    socketOutput?.write(data)
                    socketOutput?.flush()
                }
            }
        }
    }

    fun desconectar() {
        conexionJob?.cancel()
        conexionJob      = null
        socketOutput     = null
        _estado.value    = WifiGnssEstado.Desconectado
        _fixActual.value = null
    }

    fun release() {
        desconectar()
        scope.cancel()
    }
}
