package com.act.geomapper.data.gnss

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class LoggerEstado {
    object Detenido : LoggerEstado()
    data class Grabando(val archivo: String, val bytes: Long) : LoggerEstado()
}

/**
 * Graba sentencias NMEA crudas en un archivo .nmea con cero pérdida de datos.
 *
 * Arquitectura anti-pérdida:
 *   1. btService.lineaRaw (SharedFlow 4096) → collectJob → Channel(UNLIMITED)
 *      El collector nunca bloquea al lector BT porque trySend en UNLIMITED siempre es exitoso.
 *   2. writeJob drena el Channel a disco con flush+sync cada 10 líneas.
 *   3. Al detener: el collectJob se cancela primero, luego se cierra el channel.
 *      El writeJob continúa hasta vaciar los items pendientes antes de cerrar el archivo.
 */
class GnssLoggerService(
    context   : Context,
    private val btService: GnssBluetoothService
) {
    private val dir   = context.getExternalFilesDir(null) ?: context.filesDir
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _estado        = MutableStateFlow<LoggerEstado>(LoggerEstado.Detenido)
    val estado: StateFlow<LoggerEstado> = _estado.asStateFlow()

    private val _ultimoArchivo = MutableStateFlow<java.io.File?>(null)
    val ultimoArchivo: StateFlow<java.io.File?> = _ultimoArchivo.asStateFlow()

    private var collectJob: Job?             = null
    private var writeJob  : Job?             = null
    private var canal     : Channel<String>? = null

    fun iniciarGrabacion() {
        if (_estado.value is LoggerEstado.Grabando) return

        val ch   = Channel<String>(Channel.UNLIMITED)
        canal    = ch
        val ts   = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "gnss_$ts.nmea")
        _ultimoArchivo.value = file

        // Etapa 1: SharedFlow → Channel UNLIMITED (nunca bloquea al hilo BT)
        collectJob = scope.launch {
            btService.lineaRaw.collect { ch.trySend(it) }
        }

        // Etapa 2: Channel → disco
        writeJob = scope.launch {
            val fos    = FileOutputStream(file)
            val writer = fos.bufferedWriter(Charsets.UTF_8)
            try {
                _estado.value = LoggerEstado.Grabando(file.name, 0L)
                var n = 0
                for (linea in ch) {          // suspende cuando vacío, termina cuando se cierra
                    writer.write(linea)
                    writer.write("\r\n")
                    if (++n % 10 == 0) {
                        writer.flush()
                        runCatching { fos.fd.sync() }   // fuerza persistencia al almacenamiento
                        _estado.value = LoggerEstado.Grabando(file.name, file.length())
                    }
                }
            } finally {
                // Se ejecuta tanto en cierre normal como en cancelación
                runCatching { writer.flush() }
                runCatching { fos.fd.sync() }
                runCatching { writer.close() }
            }
        }
    }

    fun detenerGrabacion() {
        collectJob?.cancel()    // deja de alimentar el canal
        collectJob = null
        canal?.close()          // señal EOF: writeJob drena items pendientes y cierra el archivo
        canal    = null
        writeJob = null
        _estado.value = LoggerEstado.Detenido
    }

    fun release() {
        detenerGrabacion()
        scope.cancel()
    }
}
