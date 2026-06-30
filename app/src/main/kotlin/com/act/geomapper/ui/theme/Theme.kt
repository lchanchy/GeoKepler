package com.act.geomapper.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import com.act.geomapper.data.settings.AppLanguage

val VerdeACT       = Color(0xFF1B5E20)
val VerdeACTClaro  = Color(0xFF2E7D32)
val AzulMapeo      = Color(0xFF0D47A1)
val AzulMapeoClaro = Color(0xFF1565C0)

private val EsquemaOscuro = darkColorScheme(
    primary          = VerdeACTClaro,
    onPrimary        = Color.White,
    primaryContainer = VerdeACT,
    onPrimaryContainer = Color(0xFFA5D6A7),
    secondary        = AzulMapeoClaro,
    onSecondary      = Color.White,
    secondaryContainer = AzulMapeo,
    onSecondaryContainer = Color(0xFF90CAF9),
    background       = Color(0xFF0A0A0A),
    onBackground     = Color(0xFFE8E8E8),
    surface          = Color(0xFF111111),
    onSurface        = Color(0xFFE8E8E8),
    surfaceVariant   = Color(0xFF1E1E1E),
    onSurfaceVariant = Color(0xFFBDBDBD),
    error            = Color(0xFFEF5350),
    outline          = Color(0xFF424242)
)

private val EsquemaClaro = lightColorScheme(
    primary          = VerdeACT,
    onPrimary        = Color.White,
    primaryContainer = Color(0xFFA5D6A7),
    onPrimaryContainer = VerdeACT,
    secondary        = AzulMapeo,
    onSecondary      = Color.White,
    secondaryContainer = Color(0xFF90CAF9),
    onSecondaryContainer = AzulMapeo,
    background       = Color(0xFFF5F5F5),
    surface          = Color(0xFFFFFFFF),
    error            = Color(0xFFB71C1C)
)

@Composable
fun GeoMapperTheme(
    oscuro  : Boolean     = true,
    idioma  : AppLanguage = AppLanguage.SPANISH,
    content : @Composable () -> Unit
) {
    CompositionLocalProvider(LocalStrings provides stringsFor(idioma)) {
        MaterialTheme(
            colorScheme = if (oscuro) EsquemaOscuro else EsquemaClaro,
            typography  = Typography(),
            content     = content
        )
    }
}
