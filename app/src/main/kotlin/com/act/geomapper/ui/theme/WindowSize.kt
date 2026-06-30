package com.act.geomapper.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class ScreenType { PHONE, TABLET }

data class WindowInfo(
    val type         : ScreenType,
    val widthDp      : Dp,
    val heightDp     : Dp,
    val isTablet     : Boolean,
    // Tamaños adaptativos
    val fabSize      : Dp,
    val iconSize     : Dp,
    val textSm       : Float,
    val textMd       : Float,
    val fabBottomPad : Dp,   // padding inferior del FAB
    val captureBarBot: Dp,   // padding inferior de la barra de captura (sobre el FAB)
    val coordBarBot  : Dp,   // padding inferior de coordenadas
    val northArrowPad: Dp,   // padding superior de la brújula
)

@Composable
fun rememberWindowInfo(): WindowInfo {
    val cfg = LocalConfiguration.current
    val w   = cfg.screenWidthDp.dp
    val h   = cfg.screenHeightDp.dp
    val isTablet = cfg.screenWidthDp >= 600

    return remember(cfg.screenWidthDp, cfg.screenHeightDp) {
        if (isTablet) WindowInfo(
            type          = ScreenType.TABLET,
            widthDp       = w,
            heightDp      = h,
            isTablet      = true,
            fabSize       = 60.dp,
            iconSize      = 26.dp,
            textSm        = 12f,
            textMd        = 15f,
            fabBottomPad  = 24.dp,
            captureBarBot = 100.dp,
            coordBarBot   = 10.dp,
            northArrowPad = 170.dp,
        ) else WindowInfo(
            type          = ScreenType.PHONE,
            widthDp       = w,
            heightDp      = h,
            isTablet      = false,
            fabSize       = 54.dp,
            iconSize      = 20.dp,
            textSm        = 9f,
            textMd        = 12f,
            fabBottomPad  = 16.dp,
            captureBarBot = 80.dp,
            coordBarBot   = 8.dp,
            northArrowPad = 155.dp,
        )
    }
}
