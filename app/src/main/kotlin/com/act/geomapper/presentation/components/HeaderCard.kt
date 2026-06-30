package com.act.geomapper.presentation.components

import android.net.Uri
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.act.geomapper.domain.models.EstadoGps
import com.act.geomapper.ui.theme.GlassLightBox

// Paleta extraída de la imagen de referencia
val AzulHeader  = Color(0xFF0D2B4E)
val AzulChip    = Color(0xFF0D2B4E)
val VerdeActivo = Color(0xFF00C853)
val VerdeTeal   = Color(0xFF00838F)

@Composable
fun HeaderCard(
    estadoGps      : EstadoGps,
    proyectoNombre : String?,
    onProyectos    : () -> Unit,
    onCapas        : () -> Unit,
    onBase         : () -> Unit,
    onImportar     : () -> Unit,
    onConfig       : () -> Unit,
    modifier       : Modifier = Modifier
) {
    val context      = LocalContext.current
    val strings      = com.act.geomapper.ui.theme.LocalStrings.current
    val contentColor = MaterialTheme.colorScheme.onSurface

    // Animación de entrada del logo (una sola vez)
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

    // Color del indicador GPS (verde/amarillo/rojo según precisión)
    val colorGps = when {
        !estadoGps.activo          -> Color(0xFFEF5350)
        estadoGps.precision <= 3f  -> Color(0xFF4CAF50)
        estadoGps.precision <= 10f -> Color(0xFFFFC107)
        else                       -> Color(0xFFEF5350)
    }

    // Punto GPS pulsante
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
            .statusBarsPadding()                          // respeta la status bar del sistema
            .padding(horizontal = 14.dp)
            .padding(top = 6.dp, bottom = 12.dp)
        ) {

            // ── Fila principal ────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Logo desde assets
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

                // Título + subtítulo
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "GeoKepler",
                        color      = contentColor,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize   = 18.sp,
                        letterSpacing = 1.sp
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        // Punto pulsante: verde / amarillo / rojo según precisión GPS
                        Surface(
                            shape    = CircleShape,
                            color    = colorGps.copy(alpha = dotAlpha),
                            modifier = Modifier.size(7.dp)
                        ) {}
                        Text(
                            "Activo",
                            color      = colorGps,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 11.sp,
                            maxLines   = 1,
                            overflow   = TextOverflow.Ellipsis
                        )
                    }
                }

                // Chip GPS compacto
                Surface(shape = RoundedCornerShape(50), color = AzulChip) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.GpsFixed, null, tint = colorGps, modifier = Modifier.size(13.dp))
                        Text(
                            if (estadoGps.activo) "GPS·${estadoGps.precision.toInt()}m" else "Sin GPS",
                            color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Botón menú
                IconButton(onClick = onConfig, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.MoreVert, "Menú", tint = contentColor, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(10.dp))

            // Línea separadora verde
            HorizontalDivider(color = VerdeActivo.copy(0.3f), thickness = 1.dp)

            Spacer(Modifier.height(10.dp))

            // ── Barra de accesos rápidos ──────────────────────────────────
            val acciones = listOf(
                Triple(Icons.Default.FolderOpen,    strings.proyectos,   onProyectos),
                Triple(Icons.Default.Layers,        strings.capas,       onCapas),
                Triple(Icons.Default.Map,           strings.basemap,  onBase),
                Triple(Icons.Default.FileDownload,  strings.importar, onImportar),
            )

            // Teléfono y tablet: Row equitativo, los 5 botones siempre caben
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                acciones.forEach { (icon, label, action) ->
                    QuickActionBtn(icon, label, action, contentColor)
                }
            }
        }
    }
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
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Icon(icon, label, modifier = Modifier.size(20.dp), tint = contentColor)
            Text(label, fontSize = 9.sp, fontWeight = FontWeight.Medium, color = contentColor,
                maxLines = 1, softWrap = false)
        }
    }
}
