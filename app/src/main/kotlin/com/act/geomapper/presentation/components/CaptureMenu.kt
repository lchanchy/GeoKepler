package com.act.geomapper.presentation.components

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.act.geomapper.presentation.viewmodels.ModoCaptura

@Composable
fun CaptureMenu(
    modoActivo: ModoCaptura,
    onSeleccionar: (ModoCaptura) -> Unit,
    onCapturarPunto: () -> Unit,
    onFinalizar: () -> Unit,
    onCancelar: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expandido by remember { mutableStateOf(false) }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Botones de acción activos
        AnimatedVisibility(visible = modoActivo != ModoCaptura.NINGUNO) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SmallFloatingActionButton(
                    onClick = onCancelar,
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ) {
                    Icon(Icons.Default.Close, "Cancelar")
                }
                SmallFloatingActionButton(
                    onClick = onCapturarPunto,
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(Icons.Default.AddLocation, "Capturar punto")
                }
                if (modoActivo != ModoCaptura.PUNTO) {
                    SmallFloatingActionButton(
                        onClick = onFinalizar,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Icon(Icons.Default.Check, "Finalizar")
                    }
                }
            }
        }

        // Menú de selección de modo
        AnimatedVisibility(visible = expandido && modoActivo == ModoCaptura.NINGUNO) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OpcionCaptura(Icons.Default.Place, "Punto") {
                    onSeleccionar(ModoCaptura.PUNTO); expandido = false
                }
                OpcionCaptura(Icons.Default.Timeline, "Línea") {
                    onSeleccionar(ModoCaptura.LINEA); expandido = false
                }
                OpcionCaptura(Icons.Default.Crop, "Polígono") {
                    onSeleccionar(ModoCaptura.POLIGONO); expandido = false
                }
            }
        }

        // FAB principal
        if (modoActivo == ModoCaptura.NINGUNO) {
            FloatingActionButton(
                onClick = { expandido = !expandido },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    if (expandido) Icons.Default.Close else Icons.Default.Add,
                    "Capturar"
                )
            }
        }
    }
}

@Composable
private fun OpcionCaptura(icon: ImageVector, label: String, onClick: () -> Unit) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        icon = { Icon(icon, label) },
        text = { Text(label) },
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    )
}
