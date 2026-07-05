package com.act.geomapper.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.act.geomapper.data.settings.CoordFormat
import com.act.geomapper.domain.models.PuntoGps
import com.act.geomapper.ui.theme.GlassLightBox
import kotlin.math.abs

@Composable
fun CoordInputDialog(
    coordFormat: CoordFormat = CoordFormat.DECIMAL,
    onDismiss  : () -> Unit,
    onConfirm  : (PuntoGps) -> Unit
) {
    if (coordFormat == CoordFormat.DMS) {
        CoordInputDms(onDismiss, onConfirm)
    } else {
        CoordInputDecimal(onDismiss, onConfirm)
    }
}

// ── Ingreso decimal (lat/lon) ─────────────────────────────────────────────────

@Composable
private fun CoordInputDecimal(onDismiss: () -> Unit, onConfirm: (PuntoGps) -> Unit) {
    var lat   by remember { mutableStateOf("") }
    var lon   by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val s = com.act.geomapper.ui.theme.LocalStrings.current
    Dialog(onDismissRequest = onDismiss) {
        GlassLightBox(shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(s.ingresarCoord, color = AzulHeader, fontWeight = FontWeight.Bold, fontSize = 16.sp)

                CampoCoord("${s.latitud}  (ej: 4.609800)",   lat, KeyboardType.Decimal) { lat = it; error = null }
                CampoCoord("${s.longitud} (ej: -74.081700)", lon, KeyboardType.Decimal) { lon = it; error = null }

                error?.let { Text(it, color = Color(0xFFEF5350), fontSize = 11.sp) }

                BotonesCoord(
                    onDismiss  = onDismiss,
                    habilitado = lat.isNotBlank() && lon.isNotBlank(),
                    onCapturar = {
                        val latD = lat.toDoubleOrNull()
                        val lonD = lon.toDoubleOrNull()
                        if (latD == null || lonD == null) { error = s.coordInvalidas; return@BotonesCoord }
                        onConfirm(PuntoGps(latD, lonD)); onDismiss()
                    }
                )
            }
        }
    }
}

// ── Ingreso GMS (grados°minutos'segundos") ────────────────────────────────────

@Composable
private fun CoordInputDms(onDismiss: () -> Unit, onConfirm: (PuntoGps) -> Unit) {
    // Latitud
    var latG by remember { mutableStateOf("") }
    var latM by remember { mutableStateOf("") }
    var latS by remember { mutableStateOf("") }
    var latH by remember { mutableStateOf("N") }   // N / S
    // Longitud
    var lonG by remember { mutableStateOf("") }
    var lonM by remember { mutableStateOf("") }
    var lonS by remember { mutableStateOf("") }
    var lonH by remember { mutableStateOf("E") }   // E / O
    var error by remember { mutableStateOf<String?>(null) }
    val s = com.act.geomapper.ui.theme.LocalStrings.current

    Dialog(onDismissRequest = onDismiss) {
        GlassLightBox(shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(s.ingresarCoordGms, color = AzulHeader, fontWeight = FontWeight.Bold, fontSize = 16.sp)

                // Latitud
                Text(s.latitud, color = AzulHeader.copy(0.7f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                FilaDms(latG, latM, latS, latH, listOf("N","S"),
                    { latG = it; error = null }, { latM = it; error = null },
                    { latS = it; error = null }, { latH = it })

                // Longitud
                Text(s.longitud, color = AzulHeader.copy(0.7f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                FilaDms(lonG, lonM, lonS, lonH, listOf("E","O"),
                    { lonG = it; error = null }, { lonM = it; error = null },
                    { lonS = it; error = null }, { lonH = it })

                error?.let { Text(it, color = Color(0xFFEF5350), fontSize = 11.sp) }

                BotonesCoord(
                    onDismiss  = onDismiss,
                    habilitado = latG.isNotBlank() && lonG.isNotBlank(),
                    onCapturar = {
                        val lat = parseDms(latG, latM, latS, latH)
                        val lon = parseDms(lonG, lonM, lonS, lonH)
                        if (lat == null || lon == null) { error = s.valoresInvalidos; return@BotonesCoord }
                        onConfirm(PuntoGps(lat, lon)); onDismiss()
                    }
                )
            }
        }
    }
}

@Composable
private fun FilaDms(
    g: String, m: String, s: String, h: String,
    hemisferios: List<String>,
    onG: (String) -> Unit, onM: (String) -> Unit,
    onS: (String) -> Unit, onH: (String) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        CampoCoord("°", g, KeyboardType.Number,  Modifier.weight(1.2f), onG)
        CampoCoord("'", m, KeyboardType.Number,  Modifier.weight(1f),   onM)
        CampoCoord("\"",s, KeyboardType.Decimal, Modifier.weight(1.2f), onS)
        // Selector N/S o E/O
        Surface(shape = RoundedCornerShape(8.dp), color = AzulChip, modifier = Modifier.weight(0.8f)) {
            Row(Modifier.padding(2.dp), horizontalArrangement = Arrangement.Center) {
                hemisferios.forEach { opt ->
                    TextButton(
                        onClick = { onH(opt) },
                        colors  = ButtonDefaults.textButtonColors(
                            contentColor      = if (h == opt) Color.White else Color.White.copy(0.5f),
                            containerColor    = if (h == opt) Color(0xFF1565C0) else Color.Transparent
                        ),
                        contentPadding = PaddingValues(4.dp),
                        modifier = Modifier.weight(1f)
                    ) { Text(opt, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

@Composable
private fun CampoCoord(
    label   : String,
    value   : String,
    keyboard: KeyboardType,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value           = value,
        onValueChange   = onChange,
        label           = { Text(label, fontSize = 11.sp) },
        singleLine      = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier        = modifier
    )
}

@Composable
private fun BotonesCoord(onDismiss: () -> Unit, habilitado: Boolean, onCapturar: () -> Unit) {
    val s = com.act.geomapper.ui.theme.LocalStrings.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
        TextButton(onClick = onDismiss) { Text(s.cancelar) }
        Button(
            onClick = onCapturar,
            enabled = habilitado,
            colors  = ButtonDefaults.buttonColors(containerColor = VerdeTeal)
        ) { Text(s.capturar) }
    }
}

private fun parseDms(g: String, m: String, s: String, h: String): Double? {
    val gD = g.toDoubleOrNull() ?: return null
    val mD = m.toDoubleOrNull() ?: 0.0
    val sD = s.toDoubleOrNull() ?: 0.0
    val dec = gD + mD / 60.0 + sD / 3600.0
    return if (h == "S" || h == "O") -abs(dec) else abs(dec)
}
