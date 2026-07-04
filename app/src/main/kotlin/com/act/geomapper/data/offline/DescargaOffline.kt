package com.act.geomapper.data.offline

import android.content.SharedPreferences

data class DescargaOffline(
    val id      : Long,
    val nombre  : String,
    val norte   : Double,
    val sur     : Double,
    val este    : Double,
    val oeste   : Double,
    val zoomMax : Int,
    val tiles   : Int
)

private fun DescargaOffline.serializar(): String =
    "$id|${nombre.replace("|", "_")}|$norte|$sur|$este|$oeste|$zoomMax|$tiles"

private fun deserializarDescarga(s: String): DescargaOffline? = runCatching {
    val p = s.split("|")
    require(p.size == 8)
    DescargaOffline(
        id      = p[0].toLong(),
        nombre  = p[1],
        norte   = p[2].toDouble(),
        sur     = p[3].toDouble(),
        este    = p[4].toDouble(),
        oeste   = p[5].toDouble(),
        zoomMax = p[6].toInt(),
        tiles   = p[7].toInt()
    )
}.getOrNull()

fun cargarDescargasOffline(prefs: SharedPreferences): List<DescargaOffline> =
    (prefs.getString("offline_downloads", "") ?: "")
        .split("\n")
        .filter { it.isNotBlank() }
        .mapNotNull { deserializarDescarga(it) }

fun guardarDescargasOffline(prefs: SharedPreferences, descargas: List<DescargaOffline>) {
    prefs.edit()
        .putString("offline_downloads", descargas.joinToString("\n") { it.serializar() })
        .apply()
}
