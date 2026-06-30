package com.act.geomapper.presentation.components

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.act.geomapper.domain.models.EstadoGps
import com.act.geomapper.ui.theme.GlassBox

@Composable
fun GpsIndicator(
    estadoGps: EstadoGps,
    onConectarBluetooth: () -> Unit = {},
    onConectarNtrip: (host: String, puerto: Int, mount: String, user: String, pass: String) -> Unit = { _, _, _, _, _ -> },
    modifier: Modifier = Modifier
) {
    var mostrarPanel by remember { mutableStateOf(false) }

    val (colorGps, etiquetaCalidad) = when {
        !estadoGps.activo               -> Color(0xFFEF5350) to "Sin señal"
        estadoGps.precision <= 3f       -> Color(0xFF4CAF50) to "Excelente"
        estadoGps.precision <= 10f      -> Color(0xFFFFC107) to "Bueno"
        else                            -> Color(0xFFEF5350) to "Débil"
    }

    GlassBox(
        modifier = modifier.clickable { mostrarPanel = true },
        shape    = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = if (estadoGps.activo) Icons.Default.GpsFixed else Icons.Default.GpsOff,
                contentDescription = null,
                tint     = colorGps,
                modifier = Modifier.size(15.dp)
            )
            Column {
                Text(
                    text       = if (estadoGps.activo) "±${estadoGps.precision.toInt()} m" else "Sin GPS",
                    color      = Color.White,
                    fontSize   = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text     = if (estadoGps.satelites > 0) "${estadoGps.satelites} sat · $etiquetaCalidad" else etiquetaCalidad,
                    color    = colorGps.copy(alpha = 0.85f),
                    fontSize = 9.sp
                )
            }
        }
    }

    if (mostrarPanel) {
        GpsPanelDialog(
            estadoGps          = estadoGps,
            colorGps           = colorGps,
            onDismiss          = { mostrarPanel = false },
            onConectarBluetooth = onConectarBluetooth,
            onConectarNtrip    = onConectarNtrip
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GpsPanelDialog(
    estadoGps: EstadoGps,
    colorGps: Color,
    onDismiss: () -> Unit,
    onConectarBluetooth: () -> Unit,
    onConectarNtrip: (String, Int, String, String, String) -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }

    // Campos NTRIP
    var ntripHost  by remember { mutableStateOf("") }
    var ntripPort  by remember { mutableStateOf("2101") }
    var ntripMount by remember { mutableStateOf("") }
    var ntripUser  by remember { mutableStateOf("") }
    var ntripPass  by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        GlassBox(shape = RoundedCornerShape(20.dp)) {
            Column(modifier = Modifier.padding(20.dp)) {

                // Encabezado
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.GpsFixed, null, tint = colorGps, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Configuración GPS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, null, tint = Color.White.copy(0.7f), modifier = Modifier.size(16.dp))
                    }
                }

                Spacer(Modifier.height(4.dp))

                // Estado actual
                GlassBox(shape = RoundedCornerShape(10.dp)) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(Modifier.weight(1f)) {
                            LabelVal("Precisión", if (estadoGps.activo) "±${estadoGps.precision.toInt()} m" else "—", colorGps)
                            LabelVal("Satélites", "${estadoGps.satelites}", Color.White)
                        }
                        estadoGps.puntoActual?.let { p ->
                            Column(Modifier.weight(1f)) {
                                LabelVal("Lat",  "%.6f".format(p.latitud),  Color.White)
                                LabelVal("Lon",  "%.6f".format(p.longitud), Color.White)
                                LabelVal("Alt",  "%.1f m".format(p.altitud), Color(0xFF90CAF9))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Tabs
                TabRow(
                    selectedTabIndex = tab,
                    containerColor   = Color.Transparent,
                    contentColor     = Color.White
                ) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Celular", fontSize = 12.sp) })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Bluetooth", fontSize = 12.sp) })
                    Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("NTRIP", fontSize = 12.sp) })
                }

                Spacer(Modifier.height(12.dp))

                when (tab) {
                    0 -> Text("GPS interno del dispositivo activo.", color = Color.White.copy(0.7f), fontSize = 13.sp)

                    1 -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Conectar receptor RTK vía Bluetooth.", color = Color.White.copy(0.7f), fontSize = 13.sp)
                        Button(
                            onClick = { onConectarBluetooth(); onDismiss() },
                            colors  = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
                        ) {
                            Icon(Icons.Default.Bluetooth, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Buscar dispositivos BT")
                        }
                    }

                    2 -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassTextField(ntripHost,  { ntripHost  = it }, "Host NTRIP")
                        GlassTextField(ntripPort,  { ntripPort  = it }, "Puerto")
                        GlassTextField(ntripMount, { ntripMount = it }, "Mountpoint")
                        GlassTextField(ntripUser,  { ntripUser  = it }, "Usuario")
                        GlassTextField(ntripPass,  { ntripPass  = it }, "Contraseña")
                        Button(
                            onClick = {
                                onConectarNtrip(
                                    ntripHost,
                                    ntripPort.toIntOrNull() ?: 2101,
                                    ntripMount, ntripUser, ntripPass
                                )
                                onDismiss()
                            },
                            enabled = ntripHost.isNotBlank() && ntripMount.isNotBlank(),
                            colors  = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20))
                        ) { Text("Conectar NTRIP") }
                    }
                }
            }
        }
    }
}

@Composable
private fun LabelVal(label: String, value: String, valueColor: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("$label:", color = Color.White.copy(0.5f), fontSize = 10.sp)
        Text(value, color = valueColor, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun GlassTextField(value: String, onValue: (String) -> Unit, label: String) {
    OutlinedTextField(
        value           = value,
        onValueChange   = onValue,
        label           = { Text(label, fontSize = 11.sp) },
        singleLine      = true,
        modifier        = Modifier.fillMaxWidth(),
        colors          = OutlinedTextFieldDefaults.colors(
            focusedTextColor     = Color.White,
            unfocusedTextColor   = Color.White.copy(0.8f),
            focusedBorderColor   = Color(0xFF2E7D32),
            unfocusedBorderColor = Color.White.copy(0.3f),
            focusedLabelColor    = Color(0xFF81C784),
            unfocusedLabelColor  = Color.White.copy(0.5f),
            cursorColor          = Color.White
        ),
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
    )
}
