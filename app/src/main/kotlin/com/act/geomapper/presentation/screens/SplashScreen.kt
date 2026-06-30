package com.act.geomapper.presentation.screens

import android.graphics.BitmapFactory
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val context = LocalContext.current

    // Cargar logo desde assets via BitmapFactory (no Coil, no URI) — más fiable
    val logoBitmap = remember {
        runCatching {
            context.assets.open("GeoKepler.png").use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
    }

    var started by remember { mutableStateOf(false) }
    val alphaAnim  by animateFloatAsState(if (started) 1f else 0f,  tween(800, easing = FastOutSlowInEasing), label = "a")
    val scaleAnim  by animateFloatAsState(if (started) 1f else 0.80f, tween(800, easing = FastOutSlowInEasing), label = "s")
    val offsetAnim by animateFloatAsState(if (started) 0f else 60f,  tween(800, easing = FastOutSlowInEasing), label = "o")

    LaunchedEffect(Unit) {
        started = true
        delay(2200)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0A1F0A), Color(0xFF050D15)))),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .alpha(alphaAnim)
                .scale(scaleAnim)
                .offset(y = offsetAnim.dp)
        ) {
            if (logoBitmap != null) {
                Image(
                    bitmap             = logoBitmap.asImageBitmap(),
                    contentDescription = "GeoKepler logo",
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier
                        .size(140.dp)
                        .clip(CircleShape)
                )
            } else {
                // Fallback texto si el asset falla (no debería ocurrir)
                Text("G", fontSize = 80.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF00C853))
            }

            Spacer(Modifier.height(8.dp))

            Text("GeoKepler",             fontSize = 36.sp, fontWeight = FontWeight.Bold,   color = Color.White, letterSpacing = 2.sp)
            Text("Cartografía de campo", fontSize = 13.sp, color = Color(0xFF81C784), textAlign = TextAlign.Center, letterSpacing = 1.sp)
        }

        Text(
            text     = "v1.0.0",
            fontSize = 11.sp,
            color    = Color.White.copy(alpha = 0.3f),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)
        )
    }
}
