package com.aies.corepdf

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

/** 1 mm expresado en puntos PDF (72 pt = 25.4 mm) — para portar coordenadas que en el
 *  generador original (ReportLab/QuestPDF) estaban pensadas en milímetros. */
const val MM = 72f / 25.4f

/**
 * Primitivas de dibujo de bajo nivel compartidas por los generadores de PDF de los
 * feature modules (certificados, informes). Cada función trabaja en "espacio PDF":
 * origen abajo-a-la-izquierda, Y creciendo hacia arriba — la misma convención que usan
 * ReportLab y QuestPDF — y recibe `pageHeight` para convertir al espacio de
 * [android.graphics.Canvas] (origen arriba-a-la-izquierda, Y creciendo hacia abajo) en el
 * momento de dibujar. Esto permite portar el cálculo de coordenadas de los generadores
 * originales casi sin tocarlo, en vez de tener que re-derivar la geometría a mano.
 */
object PdfDrawUtils {

    fun text(
        canvas: Canvas, pageHeight: Float, s: String, x: Float, yBottom: Float,
        size: Float = 9f, bold: Boolean = false, color: Int = Color.BLACK,
        align: Paint.Align = Paint.Align.LEFT
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            isFakeBoldText = bold
            textAlign = align
        }
        canvas.drawText(s, x, pageHeight - yBottom, paint)
    }

    fun line(
        canvas: Canvas, pageHeight: Float, x1: Float, yBottom1: Float, x2: Float, yBottom2: Float,
        color: Int = Color.BLACK, widthPt: Float = 0.7f
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            strokeWidth = widthPt
            style = Paint.Style.STROKE
        }
        canvas.drawLine(x1, pageHeight - yBottom1, x2, pageHeight - yBottom2, paint)
    }

    /** Rectángulo relleno (+ borde opcional). `yBottom`/`h` en espacio PDF: el rectángulo
     *  ocupa desde yBottom hasta yBottom+h. */
    fun filledRect(
        canvas: Canvas, pageHeight: Float, x: Float, yBottom: Float, w: Float, h: Float,
        fill: Int, stroke: Int? = null, strokeWidthPt: Float = 0.5f, cornerRadius: Float = 0f
    ) {
        val top = pageHeight - (yBottom + h)
        val bottom = pageHeight - yBottom
        val rect = RectF(x, top, x + w, bottom)
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill; style = Paint.Style.FILL }
        if (cornerRadius > 0) canvas.drawRoundRect(rect, cornerRadius, cornerRadius, fillPaint)
        else canvas.drawRect(rect, fillPaint)
        if (stroke != null) {
            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = stroke; style = Paint.Style.STROKE; strokeWidth = strokeWidthPt
            }
            if (cornerRadius > 0) canvas.drawRoundRect(rect, cornerRadius, cornerRadius, strokePaint)
            else canvas.drawRect(rect, strokePaint)
        }
    }

    /** Celda de encabezado: fondo de color, texto blanco centrado, soporta multilínea con '\n'. */
    fun labelCell(
        canvas: Canvas, pageHeight: Float, label: String, x: Float, yBottom: Float, w: Float, h: Float,
        fill: Int, border: Int, fontSize: Float = 7f
    ) {
        filledRect(canvas, pageHeight, x, yBottom, w, h, fill, border, 0.4f)
        val lines = label.split("\n")
        val lineHeight = fontSize * 1.2f
        val blockHeight = (lines.size - 1) * lineHeight
        // yBottom + h/2 es el centro vertical de la celda en espacio PDF.
        var yCenter = yBottom + h / 2f + blockHeight / 2f
        for (l in lines) {
            text(canvas, pageHeight, l, x + w / 2f, yCenter - fontSize * 0.38f, fontSize, bold = true, color = Color.WHITE, align = Paint.Align.CENTER)
            yCenter -= lineHeight
        }
    }

    /** Celda de dato: borde + fondo configurable, texto centrado o a la izquierda. */
    fun valueCell(
        canvas: Canvas, pageHeight: Float, value: String?, x: Float, yBottom: Float, w: Float, h: Float,
        border: Int, size: Float = 9f, bold: Boolean = false,
        align: Paint.Align = Paint.Align.CENTER, padPt: Float = 1.5f * MM,
        background: Int = Color.WHITE, textColor: Int = Color.DKGRAY
    ) {
        filledRect(canvas, pageHeight, x, yBottom, w, h, background, border, 0.5f)
        if (!value.isNullOrEmpty()) {
            val cx = if (align == Paint.Align.CENTER) x + w / 2f else x + padPt
            text(canvas, pageHeight, value, cx, yBottom + h / 2f - size * 0.38f, size, bold, textColor, align)
        }
    }

    /** Celda blanca con esquinas redondeadas — para valores destacados (resultado, fecha). */
    fun roundedCell(
        canvas: Canvas, pageHeight: Float, value: String?, x: Float, yBottom: Float, w: Float, h: Float,
        border: Int, size: Float = 9f
    ) {
        filledRect(canvas, pageHeight, x, yBottom, w, h, Color.WHITE, border, 0.8f, cornerRadius = 2.5f * MM)
        if (!value.isNullOrEmpty()) {
            text(canvas, pageHeight, value, x + w / 2f, yBottom + h / 2f - size * 0.38f, size, color = Color.DKGRAY, align = Paint.Align.CENTER)
        }
    }

    /** Envuelve texto a un ancho máximo de caracteres por línea (aproximado por conteo,
     *  igual de estrategia que usan los reportes de texto libre de la app). */
    fun wrapText(texto: String, limiteCaracteres: Int): List<String> {
        val renglones = mutableListOf<String>()
        for (parrafo in texto.split('\n')) {
            val palabras = parrafo.split(" ")
            var actual = ""
            for (palabra in palabras) {
                if (actual.length + palabra.length + 1 > limiteCaracteres) {
                    renglones.add(actual.trim())
                    actual = "$palabra "
                } else {
                    actual += "$palabra "
                }
            }
            if (actual.isNotEmpty()) renglones.add(actual.trim())
        }
        return renglones
    }
}
