package com.act.geomapper.presentation.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.act.geomapper.ui.theme.GlassBox
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.MapTileIndex

// Fuentes de teselas disponibles
sealed class Basemap(val etiqueta: String, val icono: String) {
    object OSM        : Basemap("OpenStreetMap", "🗺")
    object GoogleSat  : Basemap("Satélite",       "🛰")
    object GoogleHyb  : Basemap("Híbrido",         "🌍")
    object GoogleStr  : Basemap("Calles",           "🛣")
}

fun Basemap.toTileSource(): OnlineTileSourceBase = when (this) {
    Basemap.OSM       -> TileSourceFactory.MAPNIK
    Basemap.GoogleSat -> googleTile("s")
    Basemap.GoogleHyb -> googleTile("y")
    Basemap.GoogleStr -> googleTile("m")
}

fun basemapDesdeEtiqueta(etiqueta: String): Basemap = when (etiqueta) {
    Basemap.GoogleSat.etiqueta -> Basemap.GoogleSat
    Basemap.GoogleHyb.etiqueta -> Basemap.GoogleHyb
    Basemap.GoogleStr.etiqueta -> Basemap.GoogleStr
    else                       -> Basemap.OSM
}

private fun googleTile(lyrs: String): XYTileSource {
    // Capturamos las URLs en una val local; no accedemos a mBaseUrl (campo interno de osmdroid)
    val hosts = arrayOf(
        "https://mt0.google.com",
        "https://mt1.google.com",
        "https://mt2.google.com",
        "https://mt3.google.com"
    )
    return object : XYTileSource("Google_$lyrs", 0, 20, 256, ".png", hosts) {
        override fun getTileURLString(pTileIndex: Long): String {
            val x    = MapTileIndex.getX(pTileIndex)
            val y    = MapTileIndex.getY(pTileIndex)
            val z    = MapTileIndex.getZoom(pTileIndex)
            val host = hosts[(x + y + z).toInt().and(0x7FFFFFFF) % hosts.size]
            return "$host/vt/lyrs=$lyrs&x=$x&y=$y&z=$z"
        }
    }
}

/**
 * Solo el panel de opciones (sin botón trigger).
 * Úsalo desde el HeaderCard BaseMap — el trigger es el botón del header.
 */
@Composable
fun BasemapPanel(
    seleccionado   : Basemap,
    onSeleccionar  : (Basemap) -> Unit,
    onDismiss      : () -> Unit,
    onDescargarArea: () -> Unit = {},
    modifier       : Modifier = Modifier
) {
    val opciones = listOf(Basemap.OSM, Basemap.GoogleSat, Basemap.GoogleHyb, Basemap.GoogleStr)

    GlassBox(shape = RoundedCornerShape(16.dp), modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Mapa de fondo", color = Color.White.copy(0.7f), fontSize = 11.sp,
                 fontWeight = FontWeight.SemiBold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(opciones) { bm ->
                    val activo = bm == seleccionado
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSeleccionar(bm); onDismiss() }
                            .border(
                                width = if (activo) 2.dp else 1.dp,
                                color = if (activo) Color(0xFF4CAF50) else Color.White.copy(0.2f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                               verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(bm.icono, fontSize = 22.sp)
                            Text(bm.etiqueta,
                                 color      = if (activo) Color(0xFF81C784) else Color.White,
                                 fontSize   = 10.sp,
                                 fontWeight = if (activo) FontWeight.Bold else FontWeight.Normal)
                            if (activo) Icon(Icons.Default.Check, null,
                                             tint     = Color(0xFF4CAF50),
                                             modifier = Modifier.size(12.dp))
                        }
                    }
                }
            }
            HorizontalDivider(color = Color.White.copy(0.15f))
            TextButton(
                onClick  = { onDescargarArea(); onDismiss() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.CloudDownload, null,
                    tint = Color(0xFF81C784), modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("Definir área de descarga…", color = Color(0xFF81C784), fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun BasemapSelector(
    seleccionado: Basemap,
    onSeleccionar: (Basemap) -> Unit,
    modifier: Modifier = Modifier
) {
    var expandido by remember { mutableStateOf(false) }
    val opciones = listOf(Basemap.OSM, Basemap.GoogleSat, Basemap.GoogleHyb, Basemap.GoogleStr)

    Column(modifier = modifier, horizontalAlignment = Alignment.End) {

        // Panel de opciones
        if (expandido) {
            GlassBox(shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Mapa de fondo", color = Color.White.copy(0.7f), fontSize = 11.sp,
                         fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 4.dp))

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(opciones) { bm ->
                            val activo = bm == seleccionado
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onSeleccionar(bm); expandido = false }
                                    .border(
                                        width = if (activo) 2.dp else 1.dp,
                                        color = if (activo) Color(0xFF4CAF50) else Color.White.copy(0.2f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(bm.icono, fontSize = 22.sp)
                                    Text(bm.etiqueta, color = if (activo) Color(0xFF81C784) else Color.White, fontSize = 10.sp, fontWeight = if (activo) FontWeight.Bold else FontWeight.Normal)
                                    if (activo) Icon(Icons.Default.Check, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(12.dp))
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }

        // Botón disparador
        GlassBox(shape = RoundedCornerShape(12.dp)) {
            IconButton(onClick = { expandido = !expandido }, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Default.Layers, "Capas", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
    }
}
