package com.act.geomapper.service

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.act.geomapper.MainActivity
import com.act.geomapper.R

class GeoKeplerService : Service() {

    private lateinit var wakeLock        : PowerManager.WakeLock
    private lateinit var locationManager : LocationManager
    private var proyectoNombre           = "Sin proyecto"

    companion object {
        const val CHANNEL_ID        = "geokepler_channel"
        const val NOTIF_ID          = 1001
        const val EXTRA_PROYECTO    = "proyecto_nombre"

        fun iniciar(context: Context, proyectoNombre: String = "") {
            val intent = Intent(context, GeoKeplerService::class.java).apply {
                putExtra(EXTRA_PROYECTO, proyectoNombre)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                context.startForegroundService(intent)
            else
                context.startService(intent)
        }

        fun detener(context: Context) {
            context.stopService(Intent(context, GeoKeplerService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        crearCanalNotificacion()

        // WakeLock parcial — mantiene CPU activo para GPS en background
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GeoKepler:GpsLock")
        wakeLock.acquire(10 * 60 * 1000L)  // máx 10 min

        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        registrarGps()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        proyectoNombre = intent?.getStringExtra(EXTRA_PROYECTO) ?: "Sin proyecto"
        startForeground(NOTIF_ID, construirNotificacion())
        return START_STICKY   // el sistema reinicia el servicio si lo mata
    }

    override fun onDestroy() {
        if (::wakeLock.isInitialized && wakeLock.isHeld) wakeLock.release()
        if (::locationManager.isInitialized) locationManager.removeUpdates(locationListener)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Notificación persistente ──────────────────────────────────────────────

    private fun construirNotificacion(): Notification {
        val abrirIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        else PendingIntent.FLAG_UPDATE_CURRENT

        val pendingIntent = PendingIntent.getActivity(this, 0, abrirIntent, pendingFlags)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)   // ponytail: ícono del sistema hasta que haya drawable propio
            .setContentTitle("GeoKepler activo")
            .setContentText("GPS en seguimiento · $proyectoNombre")
            .setContentIntent(pendingIntent)
            .setOngoing(true)          // no se puede deslizar para quitar
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun crearCanalNotificacion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "GeoKepler GPS",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Seguimiento GPS en segundo plano" }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    // ── GPS en background ─────────────────────────────────────────────────────

    private val locationListener = LocationListener { location: Location ->
        // Guardar última posición conocida en SharedPreferences
        // para que MapViewModel la use al volver de background
        getSharedPreferences("capture_state", MODE_PRIVATE).edit()
            .putFloat("last_lat", location.latitude.toFloat())
            .putFloat("last_lon", location.longitude.toFloat())
            .putFloat("last_acc", location.accuracy)
            .apply()
    }

    @SuppressLint("MissingPermission")
    private fun registrarGps() {
        runCatching {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                2000L,  // intervalo ms
                1f,     // distancia mínima metros
                locationListener
            )
        }
    }
}
