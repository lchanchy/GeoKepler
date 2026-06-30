package com.act.geomapper.presentation.components

import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.act.geomapper.domain.models.Proyecto
import com.act.geomapper.ui.theme.GlassBox
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NuevoProyectoSheet(
    onDismiss: () -> Unit,
    onCrear: (nombre: String, descripcion: String) -> Unit
) {
    var nombre      by remember { mutableStateOf("") }
    var descripcion by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest  = onDismiss,
        containerColor    = Color(0xFF111111),
        dragHandle        = { BottomSheetDefaults.DragHandle(color = Color.White.copy(0.3f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Nuevo Proyecto", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)

            OutlinedTextField(
                value         = nombre,
                onValueChange = { nombre = it },
                label         = { Text("Nombre del proyecto") },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
                colors        = sheetTextFieldColors()
            )
            OutlinedTextField(
                value         = descripcion,
                onValueChange = { descripcion = it },
                label         = { Text("Descripción (opcional)") },
                maxLines      = 3,
                modifier      = Modifier.fillMaxWidth(),
                colors        = sheetTextFieldColors()
            )

            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    colors  = ButtonDefaults.outlinedButtonColors(contentColor = Color.White.copy(0.7f)),
                    border  = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp)
                ) { Text("Cancelar") }

                Button(
                    onClick  = { onCrear(nombre, descripcion); onDismiss() },
                    enabled  = nombre.isNotBlank(),
                    colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20))
                ) { Text("Crear proyecto") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListaProyectosSheet(
    proyectos: List<Proyecto>,
    proyectoActivoId: Long?,
    onSeleccionar: (Long) -> Unit,
    onEliminar: (Long) -> Unit,
    onExportar: (Proyecto) -> Unit,
    onNuevo: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = Color(0xFF111111),
        dragHandle       = { BottomSheetDefaults.DragHandle(color = Color.White.copy(0.3f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Proyectos", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = onNuevo, colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF81C784))) {
                    Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Nuevo", fontSize = 13.sp)
                }
            }

            if (proyectos.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("Sin proyectos. Toca + para crear uno.", color = Color.White.copy(0.5f), fontSize = 13.sp)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(proyectos, key = { it.id }) { proy ->
                        val activo = proy.id == proyectoActivoId
                        GlassBox(shape = RoundedCornerShape(14.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSeleccionar(proy.id); onDismiss() }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (activo) Icons.Default.FolderOpen else Icons.Default.Folder,
                                    null,
                                    tint     = if (activo) Color(0xFF81C784) else Color.White.copy(0.5f),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(proy.nombre, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text(
                                        "%.2f ha · %s".format(
                                            proy.totalArea,
                                            SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(Date(proy.fechaCreacion * 1000))
                                        ),
                                        color = Color.White.copy(0.5f), fontSize = 11.sp
                                    )
                                }
                                if (activo) {
                                    Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(18.dp))
                                }
                                IconButton(onClick = { onExportar(proy); onDismiss() }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Share, null, tint = Color(0xFF00838F), modifier = Modifier.size(16.dp))
                                }
                                IconButton(onClick = { onEliminar(proy.id) }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Delete, null, tint = Color(0xFFEF5350), modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ponytail: simplificado — solo nombre, fecha automática
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuardarEntidadSheet(
    areaHa: Double,
    modoCaptura: String,
    onDismiss: () -> Unit,
    onGuardar: (nombre: String, propietario: String) -> Unit  // propietario="" siempre
) {
    var nombre by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = Color(0xFF111111),
        dragHandle       = { BottomSheetDefaults.DragHandle(color = Color.White.copy(0.3f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Guardar $modoCaptura", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)

            if (areaHa > 0) {
                GlassBox(shape = RoundedCornerShape(10.dp)) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.SquareFoot, null, tint = Color(0xFF81C784), modifier = Modifier.size(18.dp))
                        Text("%.4f ha".format(areaHa), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }

            OutlinedTextField(
                value = nombre, onValueChange = { nombre = it },
                label = { Text("Nombre") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), colors = sheetTextFieldColors()
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                OutlinedButton(onClick = onDismiss, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White.copy(0.7f))) {
                    Text("Descartar")
                }
                Button(
                    onClick  = { onGuardar(nombre, ""); onDismiss() },
                    enabled  = nombre.isNotBlank(),
                    colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20))
                ) { Text("Guardar") }
            }
        }
    }
}

@Composable
private fun sheetTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor     = Color.White,
    unfocusedTextColor   = Color.White.copy(0.8f),
    focusedBorderColor   = Color(0xFF2E7D32),
    unfocusedBorderColor = Color.White.copy(0.3f),
    focusedLabelColor    = Color(0xFF81C784),
    unfocusedLabelColor  = Color.White.copy(0.5f),
    cursorColor          = Color.White
)
