package com.aies.feature.inspecciones.ui

import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument

/**
 * Arma el PDF "REPORTE DE OBSERVACIONES" que comparten todas las inspecciones:
 * título, 2-3 líneas de encabezado (empresa/patentes/etc.) y el detalle de cada
 * ítem observado, con salto de página automático.
 */
object PdfReporteBuilder {

    data class ItemObservado(val idItem: String, val descripcion: String, val detalle: String)

    fun generar(
        titulo: String,
        filasEncabezado: List<Pair<String, String?>>,
        itemsObservados: List<ItemObservado>,
        // Numeración correlativa compartida entre dispositivos (RPC de Postgres, mismo patrón
        // que Certificados/Calibrado), para que el técnico pueda identificar de un vistazo si ya
        // revisó "la hoja de reporte número 8" de una unidad sin abrir el PDF. Null = sin
        // número asignado (nunca debería pasar en un flujo normal, pero no rompe el PDF).
        numeroReporte: Long? = null,
        fechaControl: Long = System.currentTimeMillis()
    ): PdfDocument {
        val pdfDocument = PdfDocument()
        var pageNumber = 1
        var page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNumber).create())
        var canvas = page.canvas
        val paint = Paint()

        paint.color = Color.BLACK
        paint.textSize = 14f
        paint.isFakeBoldText = true
        canvas.drawText(titulo, 40f, 50f, paint)
        if (numeroReporte != null) {
            paint.textSize = 12f
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("Nº ${numeroReporte.toString().padStart(5, '0')}", 555f, 50f, paint)
            paint.textAlign = Paint.Align.LEFT
        }

        paint.textSize = 10f
        paint.isFakeBoldText = false
        var yPos = 80f

        for ((izquierda, derecha) in filasEncabezado) {
            canvas.drawText(izquierda, 40f, yPos, paint)
            if (derecha != null) canvas.drawText(derecha, 350f, yPos, paint)
            yPos += 20f
        }
        yPos += 10f
        canvas.drawLine(40f, yPos, 550f, yPos, paint)
        yPos += 20f

        fun nuevaPagina() {
            pdfDocument.finishPage(page)
            pageNumber++
            page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNumber).create())
            canvas = page.canvas
            yPos = 50f
        }

        if (itemsObservados.isEmpty()) {
            paint.isFakeBoldText = true
            paint.textSize = 12f
            canvas.drawText("El equipo se encuentra en perfectas condiciones. No hay observaciones.", 40f, yPos, paint)
        } else {
            paint.isFakeBoldText = true
            paint.textSize = 11f
            canvas.drawText("Del control realizado se observaron los siguientes puntos:", 40f, yPos, paint)
            paint.isFakeBoldText = false
            paint.textSize = 10f
            yPos += 25f

            for (item in itemsObservados) {
                val viñeta = "• Ítem ${item.idItem}: ${item.descripcion}"
                val textoDetalle = "  → Detalle:\n${item.detalle.ifBlank { "Sin descripción adjunta" }}"

                paint.isFakeBoldText = true
                paint.color = Color.BLACK
                for (renglon in dividirTextoPorPalabras(viñeta, 80)) {
                    if (yPos > 780f) {
                        nuevaPagina()
                        paint.isFakeBoldText = true; paint.color = Color.BLACK; paint.textSize = 10f
                    }
                    canvas.drawText(renglon, 40f, yPos, paint)
                    yPos += 15f
                }

                paint.isFakeBoldText = false
                paint.color = Color.RED
                for (renglon in dividirTextoPorPalabras(textoDetalle, 85)) {
                    if (yPos > 780f) {
                        nuevaPagina()
                        paint.color = Color.RED; paint.textSize = 10f
                    }
                    canvas.drawText(renglon, 40f, yPos, paint)
                    yPos += 15f
                }
                paint.color = Color.BLACK
                yPos += 10f
            }
        }

        // Bloque final de fecha y firmas (auditor / chofer), compartido por todas las inspecciones.
        yPos += 30f
        if (yPos > 690f) nuevaPagina()
        paint.color = Color.BLACK
        paint.isFakeBoldText = true
        paint.textSize = 10f
        canvas.drawText("Fecha del control: ${java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date(fechaControl))}", 40f, yPos, paint)
        paint.isFakeBoldText = false
        val yLinea = yPos + 70f
        canvas.drawLine(40f, yLinea, 260f, yLinea, paint)
        canvas.drawLine(330f, yLinea, 550f, yLinea, paint)
        paint.textSize = 9f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("Firma y aclaración del Auditor", 150f, yLinea + 14f, paint)
        canvas.drawText("Firma y aclaración del Chofer", 440f, yLinea + 14f, paint)
        paint.textAlign = Paint.Align.LEFT

        pdfDocument.finishPage(page)
        return pdfDocument
    }

    fun dividirTextoPorPalabras(texto: String, limiteCaracteres: Int): List<String> {
        val renglones = mutableListOf<String>()
        for (parrafo in texto.split('\n')) {
            val palabras = parrafo.split(" ")
            var renglonActual = ""
            for (palabra in palabras) {
                if (renglonActual.length + palabra.length + 1 > limiteCaracteres) {
                    renglones.add(renglonActual.trim())
                    renglonActual = "$palabra "
                } else {
                    renglonActual += "$palabra "
                }
            }
            if (renglonActual.isNotEmpty()) renglones.add(renglonActual.trim())
        }
        return renglones
    }
}
