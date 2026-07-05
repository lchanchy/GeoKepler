package com.act.geomapper.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.act.geomapper.data.geopdf.GeoPdfData
import com.act.geomapper.data.offline.DescargaOffline
import com.act.geomapper.data.settings.toDisplayArea
import com.act.geomapper.data.settings.toDisplayDistance
import com.act.geomapper.domain.models.Predio
import com.act.geomapper.ui.theme.GlassBox
import org.locationtech.jts.io.WKTWriter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayersBottomSheet(
    predios             : List<Predio>,
    ocultos             : Set<Long>,
    areaUnit            : com.act.geomapper.data.settings.AreaUnit = com.act.geomapper.data.settings.AreaUnit.HECTARES,
    distanceUnit        : com.act.geomapper.data.settings.DistanceUnit = com.act.geomapper.data.settings.DistanceUnit.METERS,
    onToggleVisibilidad : (Long) -> Unit,
    onEliminar          : (Long) -> Unit,
    onRenombrar         : (Long, String) -> Unit,
    onCentrarEn         : (String) -> Unit,
    onEditarGeometria   : (Predio) -> Unit,
    onExportarEntidad      : (Predio) -> Unit              = {},
    onParcelar             : (Predio) -> Unit              = {},
    onEliminarTodos        : () -> Unit                    = {},
    onToggleTodosVisibles  : () -> Unit                    = {},
    onEditarAtributos      : (Long, String, String) -> Unit = { _, _, _ -> },
    onNavegar              : (Double, Double) -> Unit       = { _, _ -> },
    geoPdfData             : GeoPdfData?                    = null,
    geoPdfVisible          : Boolean                        = true,
    onToggleGeoPdfVisible  : () -> Unit                    = {},
    onZoomGeoPdf           : () -> Unit                    = {},
    onEliminarGeoPdf       : () -> Unit                    = {},
    descargasOffline           : List<DescargaOffline>      = emptyList(),
    onEliminarDescarga         : (Long) -> Unit             = {},
    onToggleDescargaVisible    : (Long) -> Unit             = {},
    onZoomDescarga             : (Long) -> Unit             = {},
    onDismiss              : () -> Unit
) {
    var confirmarEliminarTodos by remember { mutableStateOf(false) }

    if (confirmarEliminarTodos) {
        AlertDialog(
            onDismissRequest = { confirmarEliminarTodos = false },
            containerColor   = Color(0xFF1E1E1E),
            title  = { Text("¿Eliminar todo?", color = Color.White, fontWeight = FontWeight.Bold) },
            text   = { Text("Se eliminarán las ${predios.size} entidades. Esta acción no se puede deshacer.", color = Color.White.copy(0.7f)) },
            confirmButton = {
                Button(
                    onClick = { onEliminarTodos(); confirmarEliminarTodos = false; onDismiss() },
                    colors  = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C))
                ) { Text("Eliminar todo") }
            },
            dismissButton = {
                TextButton(onClick = { confirmarEliminarTodos = false }) { Text("Cancelar", color = Color.White.copy(0.6f)) }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = Color(0xFF111111),
        dragHandle       = { BottomSheetDefaults.DragHandle(color = Color.White.copy(0.3f)) },
        sheetMaxWidth    = androidx.compose.ui.unit.Dp.Unspecified
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val s = com.act.geomapper.ui.theme.LocalStrings.current
                Icon(Icons.Default.Layers, null, tint = Color(0xFF81C784), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(s.capasTitle, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.weight(1f))
                Text(
                    "${predios.size} entidad${if (predios.size != 1) "es" else ""}",
                    color = Color.White.copy(0.4f), fontSize = 11.sp
                )
                if (predios.isNotEmpty()) {
                    Spacer(Modifier.width(4.dp))
                    IconButton(
                        onClick  = onToggleTodosVisibles,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Visibility, null, tint = Color(0xFF90CAF9), modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick  = { confirmarEliminarTodos = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.DeleteSweep, null, tint = Color(0xFFEF5350), modifier = Modifier.size(18.dp))
                    }
                }
            }

            val puntos    = predios.filter { it.geometry.geometryType == "Point" }
            val lineas    = predios.filter { it.geometry.geometryType == "LineString" }
            val poligonos = predios.filter { it.geometry.geometryType !in listOf("Point", "LineString") }

            val s = com.act.geomapper.ui.theme.LocalStrings.current
            if (predios.isEmpty() && geoPdfData == null && descargasOffline.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(s.sinEntidades, color = Color.White.copy(0.4f), fontSize = 13.sp)
                }
            } else {
                var puntosExpandidos    by remember { mutableStateOf(false) }
                var lineasExpandidas    by remember { mutableStateOf(false) }
                var poligonosExpandidos by remember { mutableStateOf(false) }
                var rasteresExpandidos  by remember { mutableStateOf(false) }

                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // ── Sección de capas raster (GeoPDF / offline) ───────────
                    if (geoPdfData != null || descargasOffline.isNotEmpty()) {
                        item {
                            SeccionHeader(
                                icon      = Icons.Default.Map,
                                titulo    = "Rásteres",
                                count     = (if (geoPdfData != null) 1 else 0) + descargasOffline.size,
                                color     = Color(0xFFCE93D8),
                                expandido = rasteresExpandidos,
                                onToggle  = { rasteresExpandidos = !rasteresExpandidos }
                            )
                        }
                        if (rasteresExpandidos) {
                            if (geoPdfData != null) {
                                item { RasterRow(geoPdfData, geoPdfVisible, onToggleGeoPdfVisible, onZoomGeoPdf, onEliminarGeoPdf) }
                            }
                            items(descargasOffline, key = { it.id }) { d ->
                                RasterOfflineRow(
                                    data              = d,
                                    onEliminar        = onEliminarDescarga,
                                    onToggleVisible   = { onToggleDescargaVisible(d.id) },
                                    onZoom            = { onZoomDescarga(d.id); onDismiss() }
                                )
                            }
                            item { Spacer(Modifier.height(4.dp)) }
                        }
                    }
                    if (puntos.isNotEmpty()) {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.weight(1f)) {
                                    SeccionHeader(
                                        icon       = Icons.Default.Place,
                                        titulo     = s.puntos,
                                        count      = puntos.size,
                                        color      = Color(0xFF81C784),
                                        expandido  = puntosExpandidos,
                                        onToggle   = { puntosExpandidos = !puntosExpandidos }
                                    )
                                }
                            }
                        }
                        if (puntosExpandidos) {
                            items(puntos, key = { it.id }) { p ->
                                EntidadRow(
                                    predio              = p,
                                    ocultos             = ocultos,
                                    onToggleVisibilidad = onToggleVisibilidad,
                                    onEliminar          = onEliminar,
                                    onEditarAtributos   = onEditarAtributos,
                                    onCentrarEn         = onCentrarEn,
                                    onEditarGeometria   = { onEditarGeometria(p); onDismiss() },
                                    onExportarEntidad   = { onExportarEntidad(p) },
                                    onNavegar           = {
                                        val c = p.geometry.coordinate
                                        onNavegar(c.y, c.x)
                                        onDismiss()
                                    },
                                    areaUnit            = areaUnit,
                                    distanceUnit        = distanceUnit
                                )
                            }
                        }
                    }
                    if (lineas.isNotEmpty()) {
                        item {
                            SeccionHeader(
                                icon       = Icons.Default.Timeline,
                                titulo     = s.lineas,
                                count      = lineas.size,
                                color      = Color(0xFF90CAF9),
                                expandido  = lineasExpandidas,
                                onToggle   = { lineasExpandidas = !lineasExpandidas }
                            )
                        }
                        if (lineasExpandidas) {
                            items(lineas, key = { it.id }) { p ->
                                EntidadRow(
                                    predio              = p,
                                    ocultos             = ocultos,
                                    onToggleVisibilidad = onToggleVisibilidad,
                                    onEliminar          = onEliminar,
                                    onEditarAtributos   = onEditarAtributos,
                                    onCentrarEn         = onCentrarEn,
                                    onEditarGeometria   = { onEditarGeometria(p); onDismiss() },
                                    onExportarEntidad   = { onExportarEntidad(p) },
                                    areaUnit            = areaUnit,
                                    distanceUnit        = distanceUnit
                                )
                            }
                        }
                    }
                    if (poligonos.isNotEmpty()) {
                        item {
                            SeccionHeader(
                                icon       = Icons.Default.Crop,
                                titulo     = s.poligonos,
                                count      = poligonos.size,
                                color      = Color(0xFFFFCC02),
                                expandido  = poligonosExpandidos,
                                onToggle   = { poligonosExpandidos = !poligonosExpandidos }
                            )
                        }
                        if (poligonosExpandidos) {
                            items(poligonos, key = { it.id }) { p ->
                                EntidadRow(
                                    predio              = p,
                                    ocultos             = ocultos,
                                    onToggleVisibilidad = onToggleVisibilidad,
                                    onEliminar          = onEliminar,
                                    onEditarAtributos   = onEditarAtributos,
                                    onCentrarEn         = onCentrarEn,
                                    onEditarGeometria   = { onEditarGeometria(p); onDismiss() },
                                    onExportarEntidad   = { onExportarEntidad(p) },
                                    onParcelar          = { onParcelar(p) },
                                    areaUnit            = areaUnit,
                                    distanceUnit        = distanceUnit
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Cabecera colapsable ───────────────────────────────────────────────────────

@Composable
private fun SeccionHeader(
    icon     : ImageVector,
    titulo   : String,
    count    : Int,
    color    : Color,
    expandido: Boolean,
    onToggle : () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
        Text(
            "$titulo ($count)",
            color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Icon(
            if (expandido) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = if (expandido) "Colapsar" else "Expandir",
            tint     = color.copy(0.7f),
            modifier = Modifier.size(16.dp)
        )
    }
}

// ── Fila de entidad ───────────────────────────────────────────────────────────

@Composable
private fun EntidadRow(
    predio              : Predio,
    ocultos             : Set<Long>,
    onToggleVisibilidad : (Long) -> Unit,
    onEliminar          : (Long) -> Unit,
    onEditarAtributos   : (Long, String, String) -> Unit,
    onCentrarEn         : (String) -> Unit,
    onEditarGeometria   : () -> Unit,
    onExportarEntidad   : () -> Unit        = {},
    onParcelar          : (() -> Unit)?     = null,
    onNavegar           : (() -> Unit)?     = null,
    areaUnit            : com.act.geomapper.data.settings.AreaUnit     = com.act.geomapper.data.settings.AreaUnit.HECTARES,
    distanceUnit        : com.act.geomapper.data.settings.DistanceUnit = com.act.geomapper.data.settings.DistanceUnit.METERS
) {
    val visible          = predio.id !in ocultos
    var editandoNombre   by remember { mutableStateOf(false) }
    var nuevoNombre      by remember(predio.id) { mutableStateOf(predio.nombre) }
    var nuevoPropietario by remember(predio.id) { mutableStateOf(predio.propietario) }
    var confirmarBorrar  by remember { mutableStateOf(false) }
    val wktWriter        = remember { WKTWriter() }
    val wkt              = remember(predio.id) { wktWriter.write(predio.geometry) }
    val fecha            = "#${predio.id}"

    GlassBox(shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (editandoNombre) {
                    // Edición de nombre + propietario inline
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(
                            value         = nuevoNombre,
                            onValueChange = { nuevoNombre = it },
                            label         = { Text("Nombre", fontSize = 10.sp) },
                            singleLine    = true,
                            modifier      = Modifier.fillMaxWidth().height(52.dp),
                            colors        = OutlinedTextFieldDefaults.colors(
                                focusedTextColor     = Color.White,
                                unfocusedTextColor   = Color.White,
                                focusedBorderColor   = Color(0xFF2E7D32),
                                unfocusedBorderColor = Color.White.copy(0.3f),
                                focusedLabelColor    = Color(0xFF81C784),
                                unfocusedLabelColor  = Color.White.copy(0.4f)
                            ),
                            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                        )
                        OutlinedTextField(
                            value         = nuevoPropietario,
                            onValueChange = { nuevoPropietario = it },
                            label         = { Text("Propietario", fontSize = 10.sp) },
                            singleLine    = true,
                            modifier      = Modifier.fillMaxWidth().height(52.dp),
                            colors        = OutlinedTextFieldDefaults.colors(
                                focusedTextColor     = Color.White,
                                unfocusedTextColor   = Color.White,
                                focusedBorderColor   = Color(0xFF2E7D32),
                                unfocusedBorderColor = Color.White.copy(0.3f),
                                focusedLabelColor    = Color(0xFF81C784),
                                unfocusedLabelColor  = Color.White.copy(0.4f)
                            ),
                            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                        )
                    }
                    IconButton(
                        onClick  = { onEditarAtributos(predio.id, nuevoNombre, nuevoPropietario); editandoNombre = false },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Check, null, tint = Color(0xFF81C784), modifier = Modifier.size(16.dp))
                    }
                } else {
                    // Vista normal
                    Column(Modifier.weight(1f).clickable { onCentrarEn(wkt) }) {
                        Text(
                            predio.nombre.ifBlank { "Sin nombre" },
                            color      = if (visible) Color.White else Color.White.copy(0.35f),
                            fontSize   = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        if (predio.propietario.isNotBlank()) {
                            Text(predio.propietario, color = Color.White.copy(0.6f), fontSize = 11.sp)
                        }
                        val detalle = when {
                            predio.area      > 0 -> "${predio.area.toDisplayArea(areaUnit)} · $fecha"
                            predio.perimetro > 0 -> "${predio.perimetro.toDisplayDistance(distanceUnit)} · $fecha"
                            else -> fecha
                        }
                        Text(detalle, color = Color.White.copy(0.45f), fontSize = 10.sp)
                    }

                    // 🔍 Zoom a la entidad
                    IconButton(onClick = { onCentrarEn(wkt) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.ZoomIn, null, tint = Color(0xFF90CAF9), modifier = Modifier.size(16.dp))
                    }
                    // 👁 Visibilidad
                    IconButton(onClick = { onToggleVisibilidad(predio.id) }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            if (visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            null,
                            tint     = if (visible) Color(0xFF81C784) else Color.White.copy(0.3f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    // ✏️ Renombrar
                    IconButton(onClick = { editandoNombre = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, null, tint = Color(0xFF90CAF9), modifier = Modifier.size(16.dp))
                    }
                    // 🗺 Editar geometría
                    IconButton(onClick = onEditarGeometria, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.EditLocation, null, tint = Color(0xFFFF9800), modifier = Modifier.size(16.dp))
                    }
                    // ⬆ Exportar entidad individual
                    IconButton(onClick = onExportarEntidad, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Share, null, tint = Color(0xFF1565C0), modifier = Modifier.size(16.dp))
                    }
                    // ⬡ Parcelar (solo polígonos)
                    if (onParcelar != null) {
                        IconButton(onClick = onParcelar, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.GridOn, null, tint = Color(0xFF00838F), modifier = Modifier.size(16.dp))
                        }
                    }
                    // 🧭 Ir a (solo puntos)
                    if (onNavegar != null) {
                        IconButton(onClick = onNavegar, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Navigation, null, tint = Color(0xFF2196F3), modifier = Modifier.size(16.dp))
                        }
                    }
                    // 🗑 Eliminar
                    IconButton(onClick = { confirmarBorrar = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, null, tint = Color(0xFFEF5350), modifier = Modifier.size(16.dp))
                    }
                }
            }

            // Confirmación de borrado inline
            AnimatedVisibility(confirmarBorrar) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    TextButton(onClick = { confirmarBorrar = false }) { Text("Cancelar", fontSize = 11.sp) }
                    Button(
                        onClick = { onEliminar(predio.id); confirmarBorrar = false },
                        colors  = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) { Text("Eliminar", fontSize = 11.sp) }
                }
            }
        }
    }
}

// ── Fila de mapa offline descargado ──────────────────────────────────────────

@Composable
private fun RasterOfflineRow(
    data            : DescargaOffline,
    onEliminar      : (Long) -> Unit,
    onToggleVisible : () -> Unit = {},
    onZoom          : () -> Unit = {}
) {
    var confirmarBorrar by remember { mutableStateOf(false) }
    val visible = data.visible

    GlassBox(shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.CloudDownload, null,
                    tint     = if (visible) Color(0xFFCE93D8) else Color(0xFFCE93D8).copy(0.35f),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(data.nombre,
                        color = if (visible) Color.White else Color.White.copy(0.35f),
                        fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                    Text(
                        "N%.4f  S%.4f  E%.4f  O%.4f".format(
                            data.norte, data.sur, data.este, data.oeste),
                        color = Color.White.copy(0.4f), fontSize = 10.sp, maxLines = 1
                    )
                    Text("${data.tiles} tiles · z14–z${data.zoomMax}",
                        color = Color.White.copy(0.4f), fontSize = 10.sp)
                }
                // 🔍 Zoom al extent
                IconButton(onClick = onZoom, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.ZoomIn, null,
                        tint = Color(0xFF90CAF9), modifier = Modifier.size(16.dp))
                }
                // 👁 Visibilidad
                IconButton(onClick = onToggleVisible, modifier = Modifier.size(32.dp)) {
                    Icon(
                        if (visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        null,
                        tint     = if (visible) Color(0xFF81C784) else Color.White.copy(0.3f),
                        modifier = Modifier.size(16.dp)
                    )
                }
                // 🗑 Eliminar
                IconButton(onClick = { confirmarBorrar = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, null,
                        tint = Color(0xFFEF5350), modifier = Modifier.size(16.dp))
                }
            }
            AnimatedVisibility(confirmarBorrar) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    TextButton(onClick = { confirmarBorrar = false }) {
                        Text("Cancelar", fontSize = 11.sp)
                    }
                    Button(
                        onClick = { onEliminar(data.id); confirmarBorrar = false },
                        colors  = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) { Text("Eliminar", fontSize = 11.sp) }
                }
            }
        }
    }
}

// ── Fila de capa raster (GeoPDF / TIFF) ──────────────────────────────────────

@Composable
private fun RasterRow(
    data           : GeoPdfData,
    visible        : Boolean,
    onToggleVisible: () -> Unit,
    onZoom         : () -> Unit,
    onEliminar     : () -> Unit
) {
    var confirmarBorrar by remember { mutableStateOf(false) }

    GlassBox(shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Map,
                    null,
                    tint     = if (visible) Color(0xFFCE93D8) else Color(0xFFCE93D8).copy(0.35f),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        data.nombre,
                        color      = if (visible) Color.White else Color.White.copy(0.35f),
                        fontSize   = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines   = 1
                    )
                    val bbox = "N%.5f S%.5f E%.5f O%.5f".format(
                        data.norte, data.sur, data.este, data.oeste
                    )
                    Text(bbox, color = Color.White.copy(0.4f), fontSize = 10.sp, maxLines = 1)
                }
                // 🔍 Zoom al extent
                IconButton(onClick = { onZoom() }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.ZoomIn, null, tint = Color(0xFF90CAF9), modifier = Modifier.size(16.dp))
                }
                // 👁 Visibilidad
                IconButton(onClick = onToggleVisible, modifier = Modifier.size(32.dp)) {
                    Icon(
                        if (visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        null,
                        tint     = if (visible) Color(0xFF81C784) else Color.White.copy(0.3f),
                        modifier = Modifier.size(16.dp)
                    )
                }
                // 🗑 Eliminar
                IconButton(onClick = { confirmarBorrar = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, null, tint = Color(0xFFEF5350), modifier = Modifier.size(16.dp))
                }
            }

            AnimatedVisibility(confirmarBorrar) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    TextButton(onClick = { confirmarBorrar = false }) {
                        Text("Cancelar", fontSize = 11.sp)
                    }
                    Button(
                        onClick = { onEliminar(); confirmarBorrar = false },
                        colors  = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) { Text("Eliminar", fontSize = 11.sp) }
                }
            }
        }
    }
}
