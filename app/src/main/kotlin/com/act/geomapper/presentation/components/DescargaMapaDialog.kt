package com.act.geomapper.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.osmdroid.util.BoundingBox
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.tan

@Composable
fun DescargaMapaDialog(
    bbox             : BoundingBox,
    basemapEtiqueta  : String,
    progresoDescarga : Int,           // -1=idle, 0-100=progreso, 101=completado, -2=error
    onDismiss        : () -> Unit,
    onIniciarDescarga: (zoomMax: Int) -> Unit
) {
    var zoomMax    by remember { mutableStateOf(16) }
    val descargando = progresoDescarga in 0..100
    val completado  = progresoDescarga == 101
    val error       = progresoDescarga == -2

    val tilesEstimados = remember(bbox, zoomMax) { estimarTiles(bbox, 14, zoomMax) }

    Dialog(onDismissRequest = { if (!descargando) onDismiss() }) {
        Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFF1A1A2E)) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudDownload, null,
                        tint = Color(0xFF81C784), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Descargar área visible",
                        color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                        modifier = Modifier.weight(1f)
                    )
                    if (!descargando) {
                        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, null,
                                tint = Color.White.copy(0.5f), modifier = Modifier.size(16.dp))
                        }
                    }
                }

                Text("Fuente: $basemapEtiqueta", color = Color.White.copy(0.6f), fontSize = 11.sp)
                Text(
                    "N%.4f  S%.4f  E%.4f  O%.4f".format(
                        bbox.latNorth, bbox.latSouth, bbox.lonEast, bbox.lonWest),
                    color = Color.White.copy(0.45f), fontSize = 10.sp
                )

                HorizontalDivider(color = Color.White.copy(0.1f))

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row {
                        Text("Nivel máximo de detalle: ",
                            color = Color.White.copy(0.7f), fontSize = 12.sp)
                        Text("z$zoomMax",
                            color = Color(0xFF90CAF9), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = zoomMax.toFloat(), onValueChange = { zoomMax = it.toInt() },
                        valueRange = 14f..19f, steps = 4, enabled = !descargando,
                        colors = SliderDefaults.colors(
                            thumbColor         = Color(0xFF4CAF50),
                            activeTrackColor   = Color(0xFF4CAF50),
                            inactiveTrackColor = Color.White.copy(0.2f)
                        )
                    )
                    Text(
                        when (zoomMax) {
                            14   -> "Barrios (resolución ~10m)"
                            15   -> "Calles (~5m)"
                            16   -> "Manzanas (~3m)"
                            17   -> "Edificios (~1.5m)"
                            18   -> "Detalle alto (~0.7m)"
                            else -> "Máximo (~0.3m)"
                        },
                        color = Color.White.copy(0.45f), fontSize = 10.sp
                    )
                }

                val tilesTexto = if (tilesEstimados > 10_000)
                    "~${tilesEstimados / 1000}k tiles" else "~$tilesEstimados tiles"
                Text(
                    "Estimado: $tilesTexto (z14–z$zoomMax)",
                    color = if (tilesEstimados > 5000) Color(0xFFFFC107) else Color.White.copy(0.6f),
                    fontSize = 11.sp
                )
                if (tilesEstimados > 5000) {
                    Text(
                        "El área es grande — la descarga puede tardar varios minutos.",
                        color = Color(0xFFFF9800), fontSize = 10.sp
                    )
                }

                if (descargando) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        LinearProgressIndicator(
                            progress  = { progresoDescarga / 100f },
                            modifier  = Modifier.fillMaxWidth(),
                            color     = Color(0xFF4CAF50),
                            trackColor = Color.White.copy(0.1f)
                        )
                        Text("$progresoDescarga% descargado…",
                            color = Color.White.copy(0.6f), fontSize = 11.sp)
                    }
                }
                if (completado) {
                    Text("¡Descarga completada!",
                        color = Color(0xFF81C784), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                if (error) {
                    Text("Error en la descarga. Verifique la conexión de datos.",
                        color = Color(0xFFEF5350), fontSize = 12.sp)
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    if (completado || error) {
                        Button(
                            onClick = onDismiss,
                            colors  = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                        ) { Text("Cerrar") }
                    } else {
                        TextButton(onClick = onDismiss, enabled = !descargando) {
                            Text("Cancelar",
                                color = Color.White.copy(if (descargando) 0.3f else 0.6f))
                        }
                        Button(
                            onClick  = { onIniciarDescarga(zoomMax) },
                            enabled  = !descargando && tilesEstimados > 0,
                            colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
                        ) {
                            Icon(Icons.Default.CloudDownload, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Descargar")
                        }
                    }
                }
            }
        }
    }
}

private fun estimarTiles(bbox: BoundingBox, zoomMin: Int, zoomMax: Int): Int {
    var count = 0
    for (z in zoomMin..zoomMax) {
        val n = 1 shl z
        val xMin   = ((bbox.lonWest + 180.0) / 360.0 * n).toInt()
        val xMax   = ((bbox.lonEast + 180.0) / 360.0 * n).toInt()
        val latN   = Math.toRadians(bbox.latNorth.coerceIn(-85.0, 85.0))
        val latS   = Math.toRadians(bbox.latSouth.coerceIn(-85.0, 85.0))
        val yMin   = ((1.0 - ln(tan(latN) + 1.0 / cos(latN)) / Math.PI) / 2.0 * n).toInt()
        val yMax   = ((1.0 - ln(tan(latS) + 1.0 / cos(latS)) / Math.PI) / 2.0 * n).toInt()
        count += (xMax - xMin + 1) * (yMax - yMin + 1)
    }
    return count.coerceAtLeast(0)
}
