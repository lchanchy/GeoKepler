package com.act.geomapper.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.act.geomapper.BuildConfig
import java.security.MessageDigest

object SecurityChecker {

    private const val TAG = "SecurityChecker"

    // SHA-256 del certificado de firma esperado.
    // Dejar vacío durante desarrollo; rellenar con el hash real al firmar con keystore de producción.
    private const val FIRMA_ESPERADA =
        "46:AA:CA:3E:39:EF:70:DB:06:F1:81:CA:02:30:37:AB:8D:0E:87:FB:C1:8A:C0:77:D2:E3:3B:69:C1:1F:68:3F"

    fun verificar(context: Context) {
        if (BuildConfig.DEBUG) return  // solo actúa en release
        if (FIRMA_ESPERADA.isEmpty()) return
        if (!obtenerFirmaActual(context).equals(FIRMA_ESPERADA, ignoreCase = true)) {
            Log.e(TAG, "Firma inválida — APK modificado o re-empaquetado")
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }

    private fun obtenerFirmaActual(context: Context): String {
        return try {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNATURES
                )
            }
            val bytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
            } else {
                @Suppress("DEPRECATION")
                info.signatures?.firstOrNull()?.toByteArray()
            } ?: return ""
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            digest.joinToString(":") { "%02X".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error al leer firma: ${e.message}")
            ""
        }
    }
}
