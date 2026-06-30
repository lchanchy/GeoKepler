package com.act.geomapper.presentation.overlay

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable

/**
 * Crea un marcador pin profesional estilo "gota" con sombra y punto interior.
 *
 * @param colorFill   Color de relleno del pin  (default rojo captura)
 * @param colorBorder Color del borde           (default rojo oscuro)
 * @param sizeDp      Tamaño total del pin en dp
 */
fun crearPinProfesional(
    context    : Context,
    colorFill  : Int = Color.parseColor("#E53935"),
    colorBorder: Int = Color.parseColor("#B71C1C"),
    sizeDp     : Int = 44
): BitmapDrawable {
    val density = context.resources.displayMetrics.density
    val w   = (sizeDp * density).toInt()
    val h   = (sizeDp * 1.35f * density).toInt()   // más alto que ancho para la punta
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c   = Canvas(bmp)

    val cx  = w / 2f
    val r   = w / 2f - 4 * density           // radio del círculo principal
    val cy  = r + 3 * density                // centro del círculo

    // ── Sombra (desplazada abajo-derecha) ─────────────────────────────────
    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color  = Color.argb(50, 0, 0, 0)
        maskFilter = BlurMaskFilter(6 * density, BlurMaskFilter.Blur.NORMAL)
    }
    // Sombra del círculo
    c.drawCircle(cx + 2 * density, cy + 2 * density, r, shadowPaint)
    // Sombra de la punta
    val shadowPath = Path().apply {
        moveTo(cx + 2 * density, h.toFloat() - 2 * density)
        lineTo(cx - r * 0.45f + 2 * density, cy + r * 0.7f + 2 * density)
        lineTo(cx + r * 0.45f + 2 * density, cy + r * 0.7f + 2 * density)
        close()
    }
    c.drawPath(shadowPath, shadowPaint)

    // ── Punta (triángulo inferior) ─────────────────────────────────────────
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorFill }
    val puntaPath = Path().apply {
        moveTo(cx, h.toFloat() - 2 * density)
        lineTo(cx - r * 0.42f, cy + r * 0.68f)
        lineTo(cx + r * 0.42f, cy + r * 0.68f)
        close()
    }
    c.drawPath(puntaPath, fillPaint)

    // ── Círculo principal ──────────────────────────────────────────────────
    c.drawCircle(cx, cy, r, fillPaint)

    // Borde exterior
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color       = colorBorder
        style       = Paint.Style.STROKE
        strokeWidth = 2.5f * density
    }
    c.drawCircle(cx, cy, r, borderPaint)

    // Highlight (brillo blanco superior izquierda)
    val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(60, 255, 255, 255)
        style = Paint.Style.FILL
    }
    c.drawCircle(cx - r * 0.28f, cy - r * 0.28f, r * 0.38f, highlightPaint)

    // Punto blanco central
    val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    c.drawCircle(cx, cy, r * 0.28f, dotPaint)

    return BitmapDrawable(context.resources, bmp)
}

/** Pin más pequeño para vértices de polígono/línea en curso */
fun crearPinVertice(context: Context, colorFill: Int = Color.parseColor("#E53935")): BitmapDrawable =
    crearPinProfesional(context, colorFill, Color.parseColor("#B71C1C"), sizeDp = 32)

/**
 * Punto circular profesional con efecto 3D sutil.
 * - Sombra desplazada abajo-derecha para sensación de elevación.
 * - Highlight en arco superior (simula luz cenital).
 * - Borde blanco fino para contraste sobre mapa claro y satelital.
 * - Reflejo central pequeño.
 */
fun crearPuntoDot(
    context   : Context,
    colorFill : Int = Color.parseColor("#E53935"),
    sizeDp    : Int = 14
): BitmapDrawable {
    val density = context.resources.displayMetrics.density
    // Canvas más grande que el dot para que la sombra no se corte
    val dot     = (sizeDp * density).toInt()
    val pad     = (3 * density).toInt()
    val size    = dot + pad * 2
    val bmp     = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c       = Canvas(bmp)
    val cx      = size / 2f
    val cy      = size / 2f
    val r       = dot / 2f - density           // radio del círculo visible

    // ── Sombra exterior (elevación 3D) ────────────────────────────────────
    val shadowP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color      = Color.argb(110, 0, 0, 0)
        maskFilter = BlurMaskFilter(3 * density, BlurMaskFilter.Blur.NORMAL)
    }
    c.drawCircle(cx + 1.5f * density, cy + 1.5f * density, r, shadowP)

    // ── Círculo base ──────────────────────────────────────────────────────
    val fillP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorFill }
    c.drawCircle(cx, cy, r, fillP)

    // ── Highlight superior (arco claro = luz cenital = efecto esfera 3D) ──
    val highlightP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(80, 255, 255, 255)   // blanco 31% — discreto
        style = Paint.Style.FILL
    }
    // Elipse achatada en la parte superior del círculo
    c.drawArc(
        cx - r * 0.65f,
        cy - r * 0.72f,
        cx + r * 0.65f,
        cy - r * 0.02f,
        180f, 180f, false, highlightP
    )

    // ── Borde blanco fino — contraste en mapa claro y satelital ──────────
    val borderP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color       = Color.WHITE
        style       = Paint.Style.STROKE
        strokeWidth = 1.2f * density
    }
    c.drawCircle(cx, cy, r, borderP)

    // ── Reflejo central pequeño (punto de luz) ────────────────────────────
    val reflectP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(120, 255, 255, 255)
    }
    c.drawCircle(cx - r * 0.25f, cy - r * 0.25f, r * 0.18f, reflectP)

    return BitmapDrawable(context.resources, bmp)
}
