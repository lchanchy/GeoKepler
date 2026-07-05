package com.act.geomapper.presentation.components

import android.content.Context
import android.content.Intent
import android.os.Environment
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.act.geomapper.data.export.ExportFormat
import com.act.geomapper.data.export.ExportService
import com.act.geomapper.domain.models.Predio
import com.act.geomapper.domain.models.Proyecto
import com.act.geomapper.ui.theme.GlassLightBox
import com.act.geomapper.ui.theme.LocalStrings
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

private val AzulExport  = Color(0xFF1A3A5C)
private val VerdeExport = Color(0xFF4CAF50)
private val CyanExport  = Color(0xFF00BCD4)
private val VerdeGradEx = Color(0xFF00C853)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportBottomSheet(
    proyecto  : Proyecto?,
    predios   : List<Predio>,
    onDismiss : () -> Unit
) {
    val context = LocalContext.current
    val s       = LocalStrings.current
    var snackMsg by remember { mutableStateOf<String?>(null) }

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
                        Text(s.exportarDatos, color = Color(0xFF0D2B4E), fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                        Box(
                            modifier = Modifier
                                .width(100.dp).height(3.dp)
                                .background(Brush.horizontalGradient(listOf(VerdeGradEx, CyanExport)), RoundedCornerShape(2.dp))
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFFE0F7FA)) {
                        Icon(Icons.Default.Upload, null, tint = CyanExport, modifier = Modifier.padding(14.dp).size(28.dp))
                    }
                }
            }


            // ── Cards de formato ──────────────────────────────────────────
            FormatoCard(
                icono       = Icons.Default.Layers,
                nombre      = "GeoJSON",
                descripcion = s.geoJsonDesc,
                guardarLbl  = s.guardar,
                compartirLbl= s.compartir,
                onGuardar   = { snackMsg = guardarArchivo(context, proyecto, predios, ExportFormat.GEOJSON) },
                onCompartir = { compartirArchivo(context, proyecto, predios, ExportFormat.GEOJSON, compartirLabel = s.compartir) }
            )
            FormatoCard(
                icono       = Icons.Default.Map,
                nombre      = "KML",
                descripcion = s.kmlDesc,
                guardarLbl  = s.guardar,
                compartirLbl= s.compartir,
                onGuardar   = { snackMsg = guardarArchivo(context, proyecto, predios, ExportFormat.KML) },
                onCompartir = { compartirArchivo(context, proyecto, predios, ExportFormat.KML, compartirLabel = s.compartir) }
            )
            FormatoCard(
                icono       = Icons.Default.GpsFixed,
                nombre      = "GPX",
                descripcion = s.gpxDesc,
                guardarLbl  = s.guardar,
                compartirLbl= s.compartir,
                onGuardar   = { snackMsg = guardarArchivo(context, proyecto, predios, ExportFormat.GPX) },
                onCompartir = { compartirArchivo(context, proyecto, predios, ExportFormat.GPX, compartirLabel = s.compartir) }
            )
            FormatoCard(
                icono       = Icons.Default.FolderOpen,
                nombre      = "Shapefile ZIP",
                descripcion = s.shpDesc,
                guardarLbl  = s.guardar,
                compartirLbl= s.compartir,
                onGuardar   = { snackMsg = guardarArchivo(context, proyecto, predios, ExportFormat.GEOJSON, zip = true) },
                onCompartir = { compartirArchivo(context, proyecto, predios, ExportFormat.GEOJSON, zip = true, compartirLabel = s.compartir) }
            )
        }
    }

    // Snackbar de confirmación
    snackMsg?.let { msg ->
        LaunchedEffect(msg) {
            kotlinx.coroutines.delay(3000)
            snackMsg = null
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .background(AzulExport, RoundedCornerShape(12.dp))
                .padding(14.dp)
        ) {
            Text(msg, color = Color.White, fontSize = 13.sp)
        }
    }
}

@Composable
private fun FormatoCard(
    icono       : ImageVector,
    nombre      : String,
    descripcion : String,
    guardarLbl  : String,
    compartirLbl: String,
    onGuardar   : () -> Unit,
    onCompartir : () -> Unit
) {
    GlassLightBox(shape = RoundedCornerShape(16.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFE3F2FD)) {
                    Icon(icono, null, tint = AzulExport, modifier = Modifier.padding(10.dp).size(24.dp))
                }
                Column {
                    Text(nombre,      color = Color(0xFF0D2B4E), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    Text(descripcion, color = Color(0xFF607D8B), fontSize = 12.sp)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onGuardar,
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape  = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AzulExport)
                ) {
                    Icon(Icons.Default.SaveAlt, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(guardarLbl, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onCompartir,
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape  = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = VerdeExport)
                ) {
                    Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(compartirLbl, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ── Helpers de exportación ────────────────────────────────────────────────────

private val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

private fun guardarArchivo(
    context : Context,
    proyecto: Proyecto?,
    predios : List<Predio>,
    formato : ExportFormat,
    zip     : Boolean = false
): String {
    if (proyecto == null || predios.isEmpty()) return "Sin entidades para exportar."
    return runCatching {
        val ext  = if (zip) "zip" else formato.name.lowercase()
        val name = "${proyecto.nombre.replace(" ", "_")}_${sdf.format(Date())}.$ext"
        // ponytail: guardar en carpeta privada de la app (no requiere permisos)
        val dir  = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "GeoKepler")
            .also { it.mkdirs() }
        val file = File(dir, name)
        val service = ExportService(context)
        val uri  = if (zip) service.exportarZip(proyecto, predios)
                   else service.exportar(proyecto, predios, formato)
        // Copiar del caché al destino
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        "Guardado: GeoKepler/$name"
    }.getOrElse { e -> "Error: ${e.message}" }
}

private fun compartirArchivo(
    context : Context,
    proyecto: Proyecto?,
    predios : List<Predio>,
    formato : ExportFormat,
    zip     : Boolean = false,
    compartirLabel: String = "Compartir vía"
) {
    if (proyecto == null) return
    runCatching {
        val service = ExportService(context)
        val uri     = if (zip) service.exportarZip(proyecto, predios)
                      else service.exportar(proyecto, predios, formato)
        val intent  = Intent(Intent.ACTION_SEND).apply {
            type    = "application/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "GeoKepler — ${proyecto.nombre}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, compartirLabel).also {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}
