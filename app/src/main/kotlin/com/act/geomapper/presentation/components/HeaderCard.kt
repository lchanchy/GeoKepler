package com.act.geomapper.presentation.components

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.net.Uri
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.act.geomapper.data.gnss.BtGnssEstado
import com.act.geomapper.data.gnss.LoggerEstado
import com.act.geomapper.data.gnss.NtripConfig
import com.act.geomapper.data.gnss.NtripEstado
import com.act.geomapper.data.gnss.WifiGnssEstado
import com.act.geomapper.domain.models.EstadoGps
import com.act.geomapper.ui.theme.GlassBox
import com.act.geomapper.ui.theme.GlassLightBox

val AzulHeader  = Color(0xFF0D2B4E)
val AzulChip    = Color(0xFF0D2B4E)
val VerdeActivo = Color(0xFF00C853)
val VerdeTeal   = Color(0xFF00838F)

@Composable
fun HeaderCard(
    estadoGps            : EstadoGps,
    estadoBt             : BtGnssEstado             = BtGnssEstado.Desconectado,
    estadoWifi           : WifiGnssEstado            = WifiGnssEstado.Desconectado,
    estadoNtrip          : NtripEstado               = NtripEstado.Desconectado,
    estadoLogger         : LoggerEstado              = LoggerEstado.Detenido,
    ultimoArchivoLogger  : java.io.File?             = null,
    bytesRecibidosNtrip  : Long                      = 0L,
    velocidadNtrip       : Float                     = 0f,
    dispositivosBt       : List<BluetoothDevice>     = emptyList(),
    onBuscarDispositivos : () -> Unit                = {},
    onConectarBt         : (BluetoothDevice) -> Unit = {},
    onDesconectarBt      : () -> Unit                = {},
    onConectarWifi       : (String, Int) -> Unit     = { _, _ -> },
    onDesconectarWifi    : () -> Unit                = {},
    onConectarNtrip      : (NtripConfig) -> Unit     = {},
    onDesconectarNtrip   : () -> Unit                = {},
    onIniciarGrabacion   : () -> Unit                = {},
    onDetenerGrabacion   : () -> Unit                = {},
    onCompartirGrabacion : () -> Unit                = {},
    proyectoNombre       : String?,
    onProyectos          : () -> Unit,
    onCapas              : () -> Unit,
    onBase               : () -> Unit,
    onImportar           : () -> Unit,
    onConfig             : () -> Unit,
    modifier             : Modifier                  = Modifier
) {
    val context      = LocalContext.current
    val strings      = com.act.geomapper.ui.theme.LocalStrings.current
    val contentColor = MaterialTheme.colorScheme.onSurface

    var mostrarDialogGnss by remember { mutableStateOf(false) }

    var logoAnimado by remember { mutableStateOf(false) }
    val logoScale by animateFloatAsState(
        targetValue   = if (logoAnimado) 1f else 0.6f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label         = "logoScale"
    )
    val logoRotate by animateFloatAsState(
        targetValue   = if (logoAnimado) 0f else -30f,
        animationSpec = tween(600),
        label         = "logoRotate"
    )
    LaunchedEffect(Unit) { logoAnimado = true }

    val colorGps = when {
        !estadoGps.activo          -> Color(0xFFEF5350)
        estadoGps.precision <= 3f  -> Color(0xFF4CAF50)
        estadoGps.precision <= 10f -> Color(0xFFFFC107)
        else                       -> Color(0xFFEF5350)
    }

    // Chip GNSS externo: verde RTK cuando BT/WiFi+NTRIP activos
    val btConectado    = estadoBt   is BtGnssEstado.Conectado
    val wifiConectado  = estadoWifi is WifiGnssEstado.Conectado
    val wifiConectando = estadoWifi == WifiGnssEstado.Conectando
    val ntripConectado  = estadoNtrip is NtripEstado.Conectado
    val ntripConectando = estadoNtrip == NtripEstado.Conectando
    val gnssConectando  = estadoBt   == BtGnssEstado.Conectando || ntripConectando || wifiConectando

    val (colorGnss, textoGnss, iconGnss) = when {
        (btConectado || wifiConectado) && ntripConectado ->
            Triple(Color(0xFF2E7D32), "RTK·ON", Icons.Default.SatelliteAlt)
        wifiConectado ->
            Triple(Color(0xFF00C853), "WiFi·ON", Icons.Default.Wifi)
        wifiConectando ->
            Triple(Color(0xFF42A5F5), "WiFi·…", Icons.Default.Wifi)
        estadoWifi is WifiGnssEstado.Error ->
            Triple(Color(0xFFB71C1C), "WiFi·ERR", Icons.Default.WifiOff)
        btConectado ->
            Triple(Color(0xFF1565C0), "BT·ON", Icons.Default.Bluetooth)
        gnssConectando ->
            Triple(Color(0xFF42A5F5), "BT·…", Icons.Default.Bluetooth)
        else ->
            Triple(Color(0xFF546E7A), "BT·OFF", Icons.Default.BluetoothDisabled)
    }

    val (colorNtrip, textoNtrip) = when {
        ntripConectado  -> Color(0xFF69F0AE) to "NTRIP·ON"
        ntripConectando -> Color(0xFF90CAF9) to "NTRIP·…"
        else            -> Color(0xFFEF5350) to "NTRIP·OFF"
    }

    val inf = rememberInfiniteTransition(label = "gpsDot")
    val dotAlpha by inf.animateFloat(
        1f, 0.3f,
        infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "dotAlpha"
    )

    GlassLightBox(
        modifier  = modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
        elevation = 6.dp
    ) {
        Column(modifier = Modifier
            .statusBarsPadding()
            .padding(horizontal = 14.dp)
            .padding(top = 6.dp, bottom = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(Uri.parse("file:///android_asset/GeoKepler.png"))
                        .build(),
                    contentDescription = "Logo",
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .scale(logoScale)
                        .rotate(logoRotate)
                )
                Column(modifier = Modifier.weight(1f)) {
                    var tituloFontSize by remember { mutableStateOf(18.sp) }
                    Text(
                        "GeoKepler",
                        color         = contentColor,
                        fontWeight    = FontWeight.ExtraBold,
                        fontSize      = tituloFontSize,
                        maxLines      = 1,
                        softWrap      = false,
                        overflow      = TextOverflow.Clip,
                        onTextLayout  = { resultado ->
                            if (resultado.hasVisualOverflow && tituloFontSize > 12.sp) {
                                tituloFontSize = (tituloFontSize.value - 1).sp
                            }
                        }
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Surface(shape = CircleShape, color = colorGps.copy(alpha = dotAlpha), modifier = Modifier.size(7.dp)) {}
                        Text(strings.campoActivo, color = colorGps, fontWeight = FontWeight.Bold, fontSize = 11.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                // Chip GPS interno
                // Chip GPS — tamaño fijo para evitar reflejo al cambiar precisión
                Surface(
                    shape    = RoundedCornerShape(8.dp),
                    color    = AzulChip,
                    modifier = Modifier.height(34.dp).widthIn(min = 76.dp)
                ) {
                    Row(
                        modifier              = Modifier
                            .padding(horizontal = 9.dp)
                            .fillMaxHeight(),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.GpsFixed, null, tint = colorGps, modifier = Modifier.size(12.dp))
                        Text(
                            if (estadoGps.activo) "GPS·${estadoGps.precision.toInt()}m" else "Sin GPS",
                            color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Chip GNSS externo — tamaño fijo para evitar reflejo al conectar
                Surface(
                    shape    = RoundedCornerShape(8.dp),
                    color    = colorGnss,
                    modifier = Modifier
                        .height(34.dp)
                        .widthIn(min = 82.dp)
                        .clickable {
                            onBuscarDispositivos()
                            mostrarDialogGnss = true
                        }
                ) {
                    Column(
                        modifier              = Modifier
                            .padding(horizontal = 9.dp)
                            .fillMaxHeight(),
                        verticalArrangement   = Arrangement.Center,
                        horizontalAlignment   = Alignment.Start
                    ) {
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(iconGnss, null, tint = Color.White, modifier = Modifier.size(12.dp))
                            Text(textoGnss, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(1.dp))
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Surface(shape = CircleShape, color = colorNtrip,
                                modifier = Modifier.size(6.dp)) {}
                            Text(textoNtrip, color = colorNtrip,
                                fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                IconButton(onClick = onConfig, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.MoreVert, strings.menuLabel, tint = contentColor, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = VerdeActivo.copy(0.3f), thickness = 1.dp)
            Spacer(Modifier.height(10.dp))

            val acciones = listOf(
                Triple(Icons.Default.FolderOpen,   strings.proyectos, onProyectos),
                Triple(Icons.Default.Layers,        strings.capas,     onCapas),
                Triple(Icons.Default.Map,           strings.basemap,   onBase),
                Triple(Icons.Default.FileDownload,  strings.importar,  onImportar),
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                acciones.forEach { (icon, label, action) ->
                    QuickActionBtn(icon, label, action, contentColor)
                }
            }
        }
    }

    if (mostrarDialogGnss) {
        GnssDialog(
            estadoBt             = estadoBt,
            estadoWifi           = estadoWifi,
            estadoNtrip          = estadoNtrip,
            estadoLogger         = estadoLogger,
            ultimoArchivoLogger  = ultimoArchivoLogger,
            bytesRecibidosNtrip  = bytesRecibidosNtrip,
            velocidadNtrip       = velocidadNtrip,
            dispositivos         = dispositivosBt,
            onBuscar             = onBuscarDispositivos,
            onConectarBt         = onConectarBt,
            onDesconectarBt      = onDesconectarBt,
            onConectarWifi       = onConectarWifi,
            onDesconectarWifi    = onDesconectarWifi,
            onConectarNtrip      = onConectarNtrip,
            onDesconectarNtrip   = onDesconectarNtrip,
            onIniciarGrabacion   = onIniciarGrabacion,
            onDetenerGrabacion   = onDetenerGrabacion,
            onCompartirGrabacion = onCompartirGrabacion,
            onDismiss            = { mostrarDialogGnss = false }
        )
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun GnssDialog(
    estadoBt             : BtGnssEstado,
    estadoWifi           : WifiGnssEstado,
    estadoNtrip          : NtripEstado,
    estadoLogger         : LoggerEstado,
    ultimoArchivoLogger  : java.io.File?,
    bytesRecibidosNtrip  : Long,
    velocidadNtrip       : Float,
    dispositivos         : List<BluetoothDevice>,
    onBuscar             : () -> Unit,
    onConectarBt         : (BluetoothDevice) -> Unit,
    onDesconectarBt      : () -> Unit,
    onConectarWifi       : (String, Int) -> Unit,
    onDesconectarWifi    : () -> Unit,
    onConectarNtrip      : (NtripConfig) -> Unit,
    onDesconectarNtrip   : () -> Unit,
    onIniciarGrabacion   : () -> Unit,
    onDetenerGrabacion   : () -> Unit,
    onCompartirGrabacion : () -> Unit,
    onDismiss            : () -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }
    val s = com.act.geomapper.ui.theme.LocalStrings.current

    Dialog(onDismissRequest = onDismiss) {
        GlassBox(shape = RoundedCornerShape(20.dp)) {
            Column(modifier = Modifier.padding(20.dp)) {

                // Encabezado
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.SatelliteAlt, null, tint = Color(0xFF42A5F5), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(s.gnssExterno, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, null, tint = Color.White.copy(0.7f), modifier = Modifier.size(16.dp))
                    }
                }

                Spacer(Modifier.height(10.dp))

                TabRow(selectedTabIndex = tab, containerColor = Color.Transparent, contentColor = Color.White) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Bluetooth", fontSize = 12.sp) })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("WiFi TCP",  fontSize = 12.sp) })
                    Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("NTRIP",     fontSize = 12.sp) })
                }

                Spacer(Modifier.height(12.dp))

                when (tab) {
                    0 -> BtTab(estadoBt, estadoLogger, ultimoArchivoLogger, dispositivos, onBuscar, onConectarBt, onDesconectarBt, onIniciarGrabacion, onDetenerGrabacion, onCompartirGrabacion, onDismiss)
                    1 -> WifiTab(estadoWifi, estadoLogger, ultimoArchivoLogger, onConectarWifi, onDesconectarWifi, onIniciarGrabacion, onDetenerGrabacion, onCompartirGrabacion, onDismiss)
                    2 -> NtripTab(estadoNtrip, bytesRecibidosNtrip, velocidadNtrip, estadoLogger, ultimoArchivoLogger, onConectarNtrip, onDesconectarNtrip, onDismiss)
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun BtTab(
    estado              : BtGnssEstado,
    estadoLogger        : LoggerEstado,
    ultimoArchivo       : java.io.File?,
    dispositivos        : List<BluetoothDevice>,
    onBuscar            : () -> Unit,
    onConectar          : (BluetoothDevice) -> Unit,
    onDesconectar       : () -> Unit,
    onIniciarGrabacion  : () -> Unit,
    onDetenerGrabacion  : () -> Unit,
    onCompartirGrabacion: () -> Unit,
    onDismiss           : () -> Unit
) {
    val s = com.act.geomapper.ui.theme.LocalStrings.current
    val (colorEstado, textoEstado) = when (estado) {
        is BtGnssEstado.Conectado -> Color(0xFF4CAF50) to s.conectadoA.format(estado.nombre)
        BtGnssEstado.Conectando   -> Color(0xFF42A5F5) to s.conectando
        is BtGnssEstado.Error     -> Color(0xFFEF5350) to s.errorConDetalle.format(estado.mensaje)
        BtGnssEstado.Desconectado -> Color(0xFF78909C) to s.desconectado
    }

    GlassBox(shape = RoundedCornerShape(10.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(shape = CircleShape, color = colorEstado, modifier = Modifier.size(10.dp)) {}
            Text(textoEstado, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }

    Spacer(Modifier.height(10.dp))

    when (estado) {
        is BtGnssEstado.Conectado -> {
            Button(
                onClick  = { onDesconectar(); onDismiss() },
                colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.BluetoothDisabled, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(s.desconectar)
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = Color.White.copy(0.15f))
            Spacer(Modifier.height(8.dp))

            LoggerControles(estadoLogger, ultimoArchivo, onIniciarGrabacion, onDetenerGrabacion, onCompartirGrabacion)
        }
        else -> {
            if (dispositivos.isEmpty()) {
                Text(
                    s.sinReceptoresBt,
                    color = Color.White.copy(0.7f), fontSize = 12.sp
                )
            } else {
                Text(s.seleccionaReceptor, color = Color.White.copy(0.7f), fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.heightIn(max = 200.dp)) {
                    items(dispositivos) { device ->
                        Surface(
                            shape    = RoundedCornerShape(10.dp),
                            color    = Color.White.copy(0.08f),
                            modifier = Modifier.fillMaxWidth()
                                .clickable { onConectar(device); onDismiss() }
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(Icons.Default.Bluetooth, null, tint = Color(0xFF42A5F5), modifier = Modifier.size(18.dp))
                                Column {
                                    Text(device.name ?: s.dispositivoGenerico, color = Color.White,
                                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text(device.address, color = Color.White.copy(0.5f), fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onBuscar, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF42A5F5))) {
                Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(s.actualizarLista)
            }
        }
    }
}

@Composable
private fun WifiTab(
    estado              : WifiGnssEstado,
    estadoLogger        : LoggerEstado,
    ultimoArchivo       : java.io.File?,
    onConectar          : (String, Int) -> Unit,
    onDesconectar       : () -> Unit,
    onIniciarGrabacion  : () -> Unit,
    onDetenerGrabacion  : () -> Unit,
    onCompartirGrabacion: () -> Unit,
    onDismiss           : () -> Unit
) {
    val ctx    = LocalContext.current
    val prefs  = remember { ctx.getSharedPreferences("gnss_wifi", android.content.Context.MODE_PRIVATE) }
    var ip     by remember { mutableStateOf(prefs.getString("ip", "") ?: "") }
    var puerto by remember { mutableStateOf(prefs.getString("puerto", "") ?: "") }
    val s      = com.act.geomapper.ui.theme.LocalStrings.current

    val (colorEstado, textoEstado) = when (estado) {
        is WifiGnssEstado.Conectado  -> Color(0xFF4CAF50) to s.conectadoA.format("${estado.host}:${estado.puerto}")
        WifiGnssEstado.Conectando    -> Color(0xFF42A5F5) to s.conectando
        is WifiGnssEstado.Error      -> Color(0xFFEF5350) to s.errorConDetalle.format(estado.mensaje)
        WifiGnssEstado.Desconectado  -> Color(0xFF78909C) to s.desconectado
    }

    GlassBox(shape = RoundedCornerShape(10.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(shape = CircleShape, color = colorEstado, modifier = Modifier.size(10.dp)) {}
            Text(textoEstado, color = Color.White, fontSize = 13.sp,
                fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }

    Spacer(Modifier.height(10.dp))

    when (estado) {
        is WifiGnssEstado.Conectado -> {
            Button(
                onClick  = { onDesconectar(); onDismiss() },
                colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.WifiOff, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(s.desconectar)
            }
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = Color.White.copy(0.15f))
            Spacer(Modifier.height(8.dp))
            LoggerControles(estadoLogger, ultimoArchivo, onIniciarGrabacion, onDetenerGrabacion, onCompartirGrabacion)
        }
        is WifiGnssEstado.Error -> {
            Button(
                onClick  = { onDesconectar() },
                colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF546E7A)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.WifiOff, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(s.limpiarError)
            }
        }
        else -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value         = ip,
                    onValueChange = { ip = it },
                    label         = { Text(s.ipReceptor, fontSize = 11.sp) },
                    placeholder   = { Text("192.168.1.100", color = Color.White.copy(0.3f)) },
                    singleLine    = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier      = Modifier.fillMaxWidth(),
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedTextColor     = Color.White, unfocusedTextColor = Color.White,
                        focusedBorderColor   = Color(0xFF00838F),
                        unfocusedBorderColor = Color.White.copy(0.3f),
                        focusedLabelColor    = Color(0xFF4DD0E1),
                        unfocusedLabelColor  = Color.White.copy(0.4f)
                    )
                )
                OutlinedTextField(
                    value         = puerto,
                    onValueChange = { puerto = it },
                    label         = { Text(s.puertoTcp, fontSize = 11.sp) },
                    placeholder   = { Text("ej. 9001", color = Color.White.copy(0.3f)) },
                    singleLine    = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier      = Modifier.fillMaxWidth(),
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedTextColor     = Color.White, unfocusedTextColor = Color.White,
                        focusedBorderColor   = Color(0xFF00838F),
                        unfocusedBorderColor = Color.White.copy(0.3f),
                        focusedLabelColor    = Color(0xFF4DD0E1),
                        unfocusedLabelColor  = Color.White.copy(0.4f)
                    )
                )
                Text(
                    s.conectarWifiPrimero,
                    color    = Color.White.copy(0.5f),
                    fontSize = 10.sp
                )
            }
            Spacer(Modifier.height(4.dp))
            Button(
                onClick  = {
                    val p = puerto.trim().toIntOrNull()
                    if (ip.trim().isNotBlank() && p != null) {
                        prefs.edit().putString("ip", ip.trim()).putString("puerto", puerto.trim()).apply()
                        onConectar(ip.trim(), p)
                    }
                },
                enabled  = ip.trim().isNotBlank() && puerto.trim().toIntOrNull() != null && estado != WifiGnssEstado.Conectando,
                colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF00838F)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Wifi, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(s.conectarBtn)
            }
        }
    }
}

@Composable
private fun LoggerControles(
    estado     : LoggerEstado,
    ultimoArchivo: java.io.File?,
    onIniciar  : () -> Unit,
    onDetener  : () -> Unit,
    onCompartir: () -> Unit
) {
    val s = com.act.geomapper.ui.theme.LocalStrings.current
    when (estado) {
        is LoggerEstado.Grabando -> {
            val inf      = rememberInfiniteTransition(label = "recDot")
            val dotAlpha by inf.animateFloat(
                1f, 0.25f,
                infiniteRepeatable(tween(700), RepeatMode.Reverse),
                label = "recAlpha"
            )
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier              = Modifier.fillMaxWidth()
            ) {
                Surface(shape = CircleShape, color = Color(0xFFE53935).copy(alpha = dotAlpha),
                    modifier = Modifier.size(10.dp)) {}
                Column(modifier = Modifier.weight(1f)) {
                    Text(estado.archivo, color = Color.White, fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(formatBytes(estado.bytes), color = Color.White.copy(0.6f), fontSize = 10.sp)
                }
            }
            Spacer(Modifier.height(6.dp))
            Button(
                onClick  = onDetener,
                colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Stop, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(s.detenerGrabacion)
            }
        }
        LoggerEstado.Detenido -> {
            OutlinedButton(
                onClick  = onIniciar,
                modifier = Modifier.fillMaxWidth(),
                colors   = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF9A9A))
            ) {
                Icon(Icons.Default.FiberManualRecord, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(s.grabarNmea)
            }
            // Botón compartir si hay un archivo grabado previamente
            if (ultimoArchivo != null && ultimoArchivo.exists() && ultimoArchivo.length() > 0) {
                Spacer(Modifier.height(6.dp))
                OutlinedButton(
                    onClick  = onCompartir,
                    modifier = Modifier.fillMaxWidth(),
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF80CBC4))
                ) {
                    Icon(Icons.Default.Share, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(s.compartirArchivoLbl.format(ultimoArchivo.name), fontSize = 11.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long) = when {
    bytes < 1024      -> "$bytes B"
    bytes < 1_048_576 -> "${"%.1f".format(bytes / 1024f)} KB"
    else              -> "${"%.1f".format(bytes / 1_048_576f)} MB"
}

@Composable
private fun NtripTab(
    estado           : NtripEstado,
    bytesRecibidos   : Long,
    velocidadKbs     : Float,
    estadoLogger     : LoggerEstado,
    ultimoArchivo    : java.io.File?,
    onConectar       : (NtripConfig) -> Unit,
    onDesconectar    : () -> Unit,
    onDismiss        : () -> Unit
) {
    val ctx     = LocalContext.current
    val carpeta = ctx.getExternalFilesDir(null)?.absolutePath ?: ""
    val prefs   = remember { ctx.getSharedPreferences("ntrip_config", android.content.Context.MODE_PRIVATE) }
    var host       by remember { mutableStateOf(prefs.getString("host",   "") ?: "") }
    var puerto     by remember { mutableStateOf(prefs.getString("puerto", "2101") ?: "2101") }
    var mount      by remember { mutableStateOf(prefs.getString("mount",  "") ?: "") }
    var user       by remember { mutableStateOf(prefs.getString("user",   "") ?: "") }
    var pass       by remember { mutableStateOf(prefs.getString("pass",   "") ?: "") }
    var mostrarPass by remember { mutableStateOf(false) }
    val s = com.act.geomapper.ui.theme.LocalStrings.current

    val (colorChip, textoChip, subtextoHost) = when (estado) {
        is NtripEstado.Conectado ->
            Triple(Color(0xFF1B5E20), s.conectadoLabel, "${estado.host}/${estado.mountpoint}")
        NtripEstado.Conectando   -> Triple(Color(0xFF1565C0), s.conectando, "")
        is NtripEstado.Error     -> Triple(Color(0xFFB71C1C), s.errorLabel, estado.mensaje)
        NtripEstado.Desconectado -> Triple(Color(0xFF263238), s.idleLabel, "")
    }

    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ── Estado ────────────────────────────────────────────────────────
        NtripSeccion(s.estadoLabel) {
            // Chip pill igual al reference
            Surface(
                shape    = RoundedCornerShape(6.dp),
                color    = colorChip
            ) {
                Row(
                    modifier              = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(shape = CircleShape, color = Color.White.copy(0.8f),
                        modifier = Modifier.size(7.dp)) {}
                    Text(textoChip, color = Color.White, fontSize = 13.sp,
                        fontWeight = FontWeight.Bold)
                }
            }
            if (subtextoHost.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(subtextoHost, color = Color.White.copy(0.6f), fontSize = 10.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            // Stats SIEMPRE visibles (0,00 cuando no conectado)
            Spacer(Modifier.height(10.dp))
            NtripStatRow(s.velocidadLabel, "%.2f KB/s".format(velocidadKbs))
            Spacer(Modifier.height(4.dp))
            NtripStatRow(s.recibidosLabel, formatBytes(bytesRecibidos))
        }

        // ── Grabación ─────────────────────────────────────────────────────
        NtripSeccion(s.grabacionLabel) {
            val archivoActual = when (estadoLogger) {
                is LoggerEstado.Grabando -> estadoLogger.archivo
                LoggerEstado.Detenido   -> s.sinGrabacionActiva
            }
            val rutaActual = ultimoArchivo?.absolutePath ?: carpeta
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                NtripInfoRow(s.archivoActualLbl, archivoActual)
                NtripInfoRow(s.carpetaActivaLbl, carpeta)
                NtripInfoRow(s.rutaGuardadoLbl,  rutaActual)
            }
        }

        // ── Configuración ─────────────────────────────────────────────────
        NtripSeccion(s.configuracionCompleta) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                GnssTextField(host,   { host   = it }, s.ipHostLabel,  KeyboardType.Uri)
                GnssTextField(puerto, { puerto = it }, s.puertoLabel,  KeyboardType.Number)
                GnssTextField(user,   { user   = it }, s.usuarioLabel, KeyboardType.Text)

                // Contraseña con ojo toggle
                OutlinedTextField(
                    value               = pass,
                    onValueChange       = { pass = it },
                    label               = { Text(s.contrasenaLabel.format(pass.length), fontSize = 11.sp) },
                    singleLine          = true,
                    modifier            = Modifier.fillMaxWidth(),
                    keyboardOptions     = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = if (mostrarPass) VisualTransformation.None
                                          else PasswordVisualTransformation(),
                    trailingIcon        = {
                        IconButton(onClick = { mostrarPass = !mostrarPass }, modifier = Modifier.size(32.dp)) {
                            Icon(
                                if (mostrarPass) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                null, tint = Color.White.copy(0.6f), modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    colors              = OutlinedTextFieldDefaults.colors(
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

                GnssTextField(mount,  { mount  = it }, s.mountpointLabel,  KeyboardType.Text)
            }
        }

        // ── Botones ───────────────────────────────────────────────────────
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick  = {
                    val h = host.trim(); val m = mount.trim()
                    val u = user.trim(); val p = pass.trim()
                    prefs.edit()
                        .putString("host",   h)
                        .putString("puerto", puerto.trim())
                        .putString("mount",  m)
                        .putString("user",   u)
                        .putString("pass",   p)
                        .apply()
                    onConectar(NtripConfig(h, puerto.trim().toIntOrNull() ?: 2101, m, u, p))
                    onDismiss()
                },
                enabled  = estado !is NtripEstado.Conectado && host.isNotBlank() && mount.isNotBlank(),
                colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20)),
                modifier = Modifier.weight(1f)
            ) {
                Text(s.conectarBtn)
            }
            Button(
                onClick  = { onDesconectar(); onDismiss() },
                enabled  = estado is NtripEstado.Conectado,
                colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF37474F)),
                modifier = Modifier.weight(1f)
            ) {
                Text(s.desconectar)
            }
        }
    }
}

@Composable
private fun NtripSeccion(titulo: String, contenido: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(titulo, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Spacer(Modifier.height(4.dp))
        GlassBox(shape = RoundedCornerShape(10.dp)) {
            Column(modifier = Modifier.padding(12.dp), content = contenido)
        }
    }
}

@Composable
private fun NtripStatRow(label: String, valor: String) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White.copy(0.5f), fontSize = 10.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Text(valor, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun NtripInfoRow(label: String, valor: String) {
    Surface(
        shape    = RoundedCornerShape(6.dp),
        color    = Color.White.copy(0.05f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier              = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.Top
        ) {
            Text(label, color = Color.White.copy(0.45f), fontSize = 9.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                modifier = Modifier.weight(0.45f))
            Text(valor, color = Color.White, fontSize = 11.sp,
                fontWeight = FontWeight.Medium, maxLines = 2,
                overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End,
                modifier = Modifier.weight(0.55f))
        }
    }
}

@Composable
private fun GnssTextField(
    value    : String,
    onValue  : (String) -> Unit,
    label    : String,
    keyboard : KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value         = value,
        onValueChange = onValue,
        label         = { Text(label, fontSize = 11.sp) },
        singleLine    = true,
        modifier      = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        colors        = OutlinedTextFieldDefaults.colors(
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

@Composable
private fun QuickActionBtn(
    icon         : ImageVector,
    label        : String,
    onClick      : () -> Unit,
    contentColor : Color = AzulHeader
) {
    TextButton(
        onClick        = onClick,
        shape          = RoundedCornerShape(10.dp),
        colors         = ButtonDefaults.textButtonColors(contentColor = contentColor),
        contentPadding = PaddingValues(horizontal = 5.dp, vertical = 5.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Icon(icon, label, modifier = Modifier.size(20.dp), tint = contentColor)
            Text(label, fontSize = 9.sp, fontWeight = FontWeight.Medium, color = contentColor,
                maxLines = 1, softWrap = false)
        }
    }
}
