package com.act.geomapper.presentation.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.act.geomapper.data.settings.*
import com.act.geomapper.data.settings.CoordFormat
import com.act.geomapper.presentation.viewmodels.SettingsViewModel
import com.act.geomapper.ui.theme.GlassBox

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm      : SettingsViewModel,
    onBack  : () -> Unit
) {
    val settings = vm.settings.collectAsStateWithLifecycle().value
    val context  = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configuración", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Volver") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor        = Color(0xFF0A0A0A),
                    titleContentColor     = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF0A0A0A)
    ) { padding ->

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // ── Unidades de distancia ────────────────────────────────────
            SettingsSection(Icons.Default.Straighten, "Unidades de distancia") {
                SettingsChipGroup(
                    opciones   = listOf("Metros (m)" to DistanceUnit.METERS, "Kilómetros (km)" to DistanceUnit.KILOMETERS),
                    seleccion  = settings.distanceUnit,
                    onSelect   = vm::setDistanceUnit
                )
            }

            // ── Unidades de área ─────────────────────────────────────────
            SettingsSection(Icons.Default.SquareFoot, "Unidades de área") {
                SettingsChipGroup(
                    opciones  = listOf(
                        "m²"  to AreaUnit.SQUARE_METERS,
                        "km²" to AreaUnit.SQUARE_KILOMETERS,
                        "ha"  to AreaUnit.HECTARES
                    ),
                    seleccion = settings.areaUnit,
                    onSelect  = vm::setAreaUnit
                )
            }

            // ── Idioma ───────────────────────────────────────────────────
            SettingsSection(Icons.Default.Language, "Idioma") {
                SettingsChipGroup(
                    opciones  = listOf("Español" to AppLanguage.SPANISH, "English" to AppLanguage.ENGLISH),
                    seleccion = settings.language,
                    onSelect  = vm::setLanguage
                )
            }

            // ── Formato de coordenadas ───────────────────────────────────
            SettingsSection(Icons.Default.GpsFixed, "Coordenadas") {
                SettingsChipGroup(
                    opciones  = listOf(
                        "Decimal  37.4219°"    to CoordFormat.DECIMAL,
                        "GMS  37°25'19\"N"     to CoordFormat.DMS
                    ),
                    seleccion = settings.coordFormat,
                    onSelect  = vm::setCoordFormat
                )
            }

            // ── Tema ─────────────────────────────────────────────────────
            SettingsSection(Icons.Default.DarkMode, "Tema") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = settings.darkMode,
                        onClick  = { vm.setDarkMode(true) },
                        label    = { Text("Oscuro") },
                        leadingIcon = if (settings.darkMode) {{ Icon(Icons.Default.Check, null, Modifier.size(14.dp)) }} else null
                    )
                    FilterChip(
                        selected = !settings.darkMode,
                        onClick  = { vm.setDarkMode(false) },
                        label    = { Text("Claro") },
                        leadingIcon = if (!settings.darkMode) {{ Icon(Icons.Default.Check, null, Modifier.size(14.dp)) }} else null
                    )
                }
            }

            // ── Contacto / Mejoras ────────────────────────────────────────
            GlassBox(shape = RoundedCornerShape(16.dp)) {
                Column(
                    modifier            = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.ChatBubbleOutline, null, tint = Color(0xFF81C784), modifier = Modifier.size(18.dp))
                        Text("Mejoras y comentarios", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                    Text(
                        "¿Tienes sugerencias o encontraste un problema? Contáctanos por WhatsApp.",
                        color    = Color(0xFFB0BEC5),
                        fontSize = 12.sp
                    )
                    Button(
                        onClick = {
                            val numero  = "573124803853"
                            val mensaje = Uri.encode("Hola, tengo una sugerencia o comentario sobre GeoKepler:")
                            val intent  = Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://wa.me/$numero?text=$mensaje"))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(12.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))
                    ) {
                        Icon(Icons.Default.Chat, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Escribir por WhatsApp", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    icono    : ImageVector,
    titulo   : String,
    content  : @Composable () -> Unit
) {
    GlassBox(shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icono, null, tint = Color(0xFF81C784), modifier = Modifier.size(18.dp))
                Text(titulo, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
            content()
        }
    }
}

@Composable
private fun <T> SettingsChipGroup(
    opciones : List<Pair<String, T>>,
    seleccion: T,
    onSelect : (T) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        opciones.forEach { (label, valor) ->
            FilterChip(
                selected    = seleccion == valor,
                onClick     = { onSelect(valor) },
                label       = { Text(label, fontSize = 12.sp) },
                leadingIcon = if (seleccion == valor) {
                    { Icon(Icons.Default.Check, null, Modifier.size(14.dp)) }
                } else null
            )
        }
    }
}
