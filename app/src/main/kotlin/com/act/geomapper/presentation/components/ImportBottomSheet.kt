package com.act.geomapper.presentation.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.act.geomapper.ui.theme.GlassLightBox
import com.act.geomapper.ui.theme.LocalStrings

private val AzulImport = Color(0xFF1A3A5C)
private val CyanImport = Color(0xFF00BCD4)
private val VerdeGrad  = Color(0xFF00C853)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportBottomSheet(
    rellenoPoligonos     : Boolean,
    onRellenoChange      : (Boolean) -> Unit,
    onArchivoSeleccionado: (Uri) -> Unit,
    onIngresarCoordenada : () -> Unit,   // abre CoordInputDialog
    onDismiss            : () -> Unit
) {
    // Launchers SAF por formato — deben declararse a nivel composable
    val launcherTiff = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { onArchivoSeleccionado(it); onDismiss() }
    }
    val launcherKml = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { onArchivoSeleccionado(it); onDismiss() }
    }
    val launcherGpx = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { onArchivoSeleccionado(it); onDismiss() }
    }
    val launcherJson = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { onArchivoSeleccionado(it); onDismiss() }
    }
    val launcherZip = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { onArchivoSeleccionado(it); onDismiss() }
    }

    val s = LocalStrings.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = Color.White,
        dragHandle       = { BottomSheetDefaults.DragHandle(color = Color(0xFFB0BEC5)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Header card ───────────────────────────────────────────────
            GlassLightBox(shape = RoundedCornerShape(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(s.importarMapa, color = Color(0xFF0D2B4E), fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                        Box(
                            modifier = Modifier
                                .width(100.dp).height(3.dp)
                                .background(Brush.horizontalGradient(listOf(VerdeGrad, CyanImport)), RoundedCornerShape(2.dp))
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    // Ícono cyan cuadrado
                    Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFFE0F7FA)) {
                        Icon(
                            Icons.Default.Map, null,
                            tint     = CyanImport,
                            modifier = Modifier.padding(14.dp).size(28.dp)
                        )
                    }
                }
            }

            // ── Botones de formato ────────────────────────────────────────
            ImportBtn(Icons.Default.Map,    s.importarGeoPdf)    { launcherTiff.launch(arrayOf("image/tiff", "application/pdf", "*/*")) }
            ImportBtn(Icons.Default.Layers, s.importarKml)       { launcherKml.launch(arrayOf("application/vnd.google-earth.kml+xml", "text/xml", "*/*")) }
            ImportBtn(Icons.Default.Layers, s.importarGpx)       { launcherGpx.launch(arrayOf("application/gpx+xml", "text/xml", "*/*")) }
            ImportBtn(Icons.Default.Layers, s.importarGeoJson)   { launcherJson.launch(arrayOf("application/json", "text/plain", "*/*")) }
            ImportBtn(Icons.Default.Layers, s.importarShapefile) { launcherZip.launch(arrayOf("application/zip", "*/*")) }

            // ── Ingresar coordenada manualmente ──────────────────────────
            Button(
                onClick        = { onIngresarCoordenada(); onDismiss() },
                modifier       = Modifier.fillMaxWidth().height(56.dp),
                shape          = RoundedCornerShape(14.dp),
                colors         = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20)),
                contentPadding = PaddingValues(horizontal = 20.dp)
            ) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Surface(shape = RoundedCornerShape(8.dp), color = Color.White.copy(0.15f)) {
                        Icon(Icons.Default.AddLocation, null, tint = Color.White, modifier = Modifier.padding(6.dp).size(18.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(s.ingresarCoordsManual, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            }

            // ── Toggle relleno de polígonos ───────────────────────────────
            GlassLightBox(shape = RoundedCornerShape(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(s.rellenoPoligonos, color = Color(0xFF0D2B4E), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(s.rellenoPoligonosDesc, color = Color(0xFF607D8B), fontSize = 12.sp)
                    }
                    Switch(
                        checked         = rellenoPoligonos,
                        onCheckedChange = onRellenoChange,
                        colors          = SwitchDefaults.colors(
                            checkedThumbColor      = Color.White,
                            checkedTrackColor      = AzulImport,
                            uncheckedThumbColor    = Color.White,
                            uncheckedTrackColor    = Color(0xFFB0BEC5)
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun ImportBtn(icon: ImageVector, label: String, onClick: () -> Unit) {
    Button(
        onClick        = onClick,
        modifier       = Modifier.fillMaxWidth().height(56.dp),
        shape          = RoundedCornerShape(14.dp),
        colors         = ButtonDefaults.buttonColors(containerColor = AzulImport),
        contentPadding = PaddingValues(horizontal = 20.dp)
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // Ícono en círculo semitransparente
            Surface(shape = RoundedCornerShape(8.dp), color = Color.White.copy(0.15f)) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.padding(6.dp).size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
    }
}
