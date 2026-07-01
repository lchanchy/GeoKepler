package com.act.geomapper.data.gnss

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.UUID

sealed class BtGnssEstado {
    object Desconectado : BtGnssEstado()
    object Conectando   : BtGnssEstado()
    data class Conectado(val nombre: String) : BtGnssEstado()
    data class Error(val mensaje: String)    : BtGnssEstado()
}

class GnssBluetoothService(context: Context) {

    private val SPP_UUID            = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val RECONEXION_DELAY_MS = 3_000L
    private val MAX_REINTENTOS      = 5

    private val appContext  = context.applicationContext
    private val scope       = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val parser      = NmeaParser()
    private val writeMutex  = Mutex()

    @Volatile private var socketOutput: OutputStream? = null

    private val _estado = MutableStateFlow<BtGnssEstado>(BtGnssEstado.Desconectado)
    val estado: StateFlow<BtGnssEstado> = _estado.asStateFlow()

    private val _fixActual = MutableStateFlow<GnssFix?>(null)
    val fixActual: StateFlow<GnssFix?> = _fixActual.asStateFlow()

    // 4096 líneas ≈ ~13 min a 5 Hz — DROP_OLDEST solo si el logger es extremadamente lento
    private val _lineaRaw = MutableSharedFlow<String>(
        extraBufferCapacity = 4096,
        onBufferOverflow    = BufferOverflow.DROP_OLDEST
    )
    val lineaRaw: SharedFlow<String> = _lineaRaw.asSharedFlow()

    private val _dispositivosEmparejados = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val dispositivosEmparejados: StateFlow<List<BluetoothDevice>> = _dispositivosEmparejados.asStateFlow()

    private var conexionJob: Job? = null

    private val adapter by lazy {
        (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    }

    @SuppressLint("MissingPermission")
    fun cargarEmparejados() {
        _dispositivosEmparejados.value = adapter?.bondedDevices?.toList() ?: emptyList()
    }

    @SuppressLint("MissingPermission")
    fun conectar(device: BluetoothDevice) {
        conexionJob?.cancel()
        conexionJob = scope.launch {
            var intentos = 0
            while (isActive && intentos < MAX_REINTENTOS) {
                _estado.value = BtGnssEstado.Conectando
                val socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                try {
                    adapter?.cancelDiscovery()
                    socket.connect()
                    socketOutput = socket.outputStream
                    _estado.value = BtGnssEstado.Conectado(device.name ?: device.address)
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
                        _estado.value = BtGnssEstado.Error(e.message ?: "Error BT")
                        break
                    }
                    delay(RECONEXION_DELAY_MS)
                } finally {
                    socketOutput = null
                    runCatching { socket.close() }
                }
            }
            if (_estado.value is BtGnssEstado.Conectando) {
                _estado.value = BtGnssEstado.Desconectado
            }
        }
    }

    /** Envía datos binarios (RTCM) al receptor GNSS vía la misma conexión BT. */
    fun enviarDatos(data: ByteArray) {
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
        conexionJob    = null
        socketOutput   = null
        _estado.value  = BtGnssEstado.Desconectado
        _fixActual.value = null
    }

    fun release() {
        desconectar()
        scope.cancel()
    }
}
