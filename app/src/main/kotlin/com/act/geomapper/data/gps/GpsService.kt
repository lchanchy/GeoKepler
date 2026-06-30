package com.act.geomapper.data.gps

import android.annotation.SuppressLint
import android.content.Context
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import com.act.geomapper.domain.models.EstadoGps
import com.act.geomapper.domain.models.PuntoGps
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class GpsService(context: Context) {

    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val _estado = MutableStateFlow(EstadoGps())
    val estado: StateFlow<EstadoGps> = _estado

    @SuppressLint("MissingPermission")
    fun iniciar(): Flow<PuntoGps> = callbackFlow {
        val listener = LocationListener { location ->
            val punto = location.toPuntoGps()
            _estado.value = _estado.value.copy(
                activo = true,
                puntoActual = punto,
                precision = location.accuracy
            )
            trySend(punto)
        }

        val gnssCallback = object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatus) {
                _estado.value = _estado.value.copy(satelites = status.satelliteCount)
            }
        }

        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            MIN_TIEMPO_MS,
            MIN_DISTANCIA_M,
            listener
        )
        locationManager.registerGnssStatusCallback(gnssCallback, null)

        awaitClose {
            locationManager.removeUpdates(listener)
            locationManager.unregisterGnssStatusCallback(gnssCallback)
            _estado.value = EstadoGps()
        }
    }

    fun ultimaUbicacionConocida(): PuntoGps? {
        return try {
            @SuppressLint("MissingPermission")
            val loc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            loc?.toPuntoGps()
        } catch (e: SecurityException) {
            null
        }
    }

    private fun Location.toPuntoGps() = PuntoGps(
        latitud = latitude,
        longitud = longitude,
        altitud = altitude,
        precision = accuracy,
        timestamp = time
    )

    companion object {
        private const val MIN_TIEMPO_MS = 1000L
        private const val MIN_DISTANCIA_M = 0.5f
    }
}
