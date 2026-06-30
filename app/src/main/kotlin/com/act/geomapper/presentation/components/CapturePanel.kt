package com.act.geomapper.presentation.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import com.act.geomapper.ui.theme.GlassLightBox

@Composable
fun CapturePanel(
    onGps    : () -> Unit,   // captura desde GPS del dispositivo
    onMapa   : () -> Unit,   // captura desde centro del mapa (diana)
    modifier : Modifier = Modifier
) {
    var expandido by remember { mutableStateOf(true) }

    // Punto pulsante
    val inf = rememberInfiniteTransition(label = "panelDot")
    val dotAlpha by inf.animateFloat(
        1f, 0.2f,
        infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "da"
    )

    GlassLightBox(
        modifier  = modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        elevation = 8.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {

            // ── Header del panel ──────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(8.dp).background(VerdeActivo.copy(alpha = dotAlpha), CircleShape))
                Spacer(Modifier.width(6.dp))
                Text(
                    "HERRAMIENTA DE CAPTURA",
                    color      = VerdeActivo,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize   = 12.sp,
                    letterSpacing = 0.5.sp,
                    modifier   = Modifier.weight(1f)
                )
                // Línea decorativa
                HorizontalDivider(
                    modifier  = Modifier.weight(0.3f).padding(horizontal = 8.dp),
                    color     = VerdeActivo.copy(0.4f)
                )
                // Toggle collapse
                IconButton(onClick = { expandido = !expandido }, modifier = Modifier.size(30.dp)) {
                    Icon(
                        if (expandido) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                        "Colapsar",
                        tint     = AzulHeader,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // ── Botones 2×2 ──────────────────────────────────────────────
            AnimatedVisibility(
                visible = expandido,
                enter   = expandVertically() + fadeIn(),
                exit    = shrinkVertically() + fadeOut()
            ) {
                Row(
                    modifier              = Modifier.padding(top = 10.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CapturePanelBtn(Icons.Default.GpsFixed, "GPS",  VerdeActivo, onClick = onGps,  modifier = Modifier.weight(1f))
                    CapturePanelBtn(Icons.Default.Map,      "Mapa", AzulHeader,  onClick = onMapa, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CapturePanelBtn(
    icon     : ImageVector,
    label    : String,
    iconColor: Color,
    onClick  : () -> Unit,
    modifier : Modifier = Modifier
) {
    OutlinedButton(
        onClick        = onClick,
        modifier       = modifier.height(54.dp),
        shape          = RoundedCornerShape(14.dp),
        colors         = ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(0.7f),
            contentColor   = MaterialTheme.colorScheme.onSurface
        ),
        border         = BorderStroke(1.dp, AzulHeader.copy(0.15f)),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, label, tint = iconColor, modifier = Modifier.size(18.dp))
            Text(label, fontWeight = FontWeight.Medium, fontSize = 13.sp)
        }
    }
}
