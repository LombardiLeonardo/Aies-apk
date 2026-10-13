package com.aies.feature.certificados.pdf

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Genera los dos documentos de la app de certificados (GeneradorCertificados.cs original,
 * QuestPDF): el certificado de tapas paso de hombre (Shell/Axion) y la nota de reemplazo
 * de precintos. Coordenadas en espacio nativo de Android (Canvas): origen arriba a la
 * izquierda, Y creciente hacia abajo — layout compuesto de nuevo top-down en vez de portar
 * la geometría de QuestPDF, que es un motor de flujo/flexbox sin coordenadas propias.
 */
object CertificadoPdfBuilder {

    private const val PAGE_W = 595f
    private const val PAGE_H = 842f
    private val fmtFecha = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    // ── Certificado de tapas ────────────────────────────────────────────────────────

    fun generarCertificadoTapas(
        assets: AssetManager,
        nroCorrelativo: String,
        transportista: String,
        patenteChasis: String,
        patenteAcoplado: String,
        sistemaTapas: String,
        tipoCertificado: String, // "Shell" | "Axion"
        incluirChasis: Boolean
    ): PdfDocument {
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W.toInt(), PAGE_H.toInt(), 1).create())
        val canvas = page.canvas
        val margin = 40f

        dibujarBitmap(canvas, assets, "blob.png", x = 0f, y = PAGE_H - 130f, ancho = 220f, alto = 130f, alpha = 38)

        var y = margin + 12f
        dibujarTexto(canvas, "R 11-02 / Rev.00", margin, y, 11f)
        dibujarBitmap(canvas, assets, "logo.png", x = PAGE_W - margin - 130f, y = margin - 12f, ancho = 130f, alto = 40f)

        val boxTop = y + 18f
        val boxLeft = margin
        val boxRight = PAGE_W - margin
        y = boxTop + 25f
        val contentLeft = boxLeft + 25f
        val contentRight = boxRight - 25f
        val contentWidth = contentRight - contentLeft

        val titulo = if (tipoCertificado == "Axion")
            "Certificado de control y ensayo de integridad de dispositivos críticos (tapas, válvulas)"
        else
            "Certificado de Tapas Paso de Hombre Tanques Cisterna"

        y = dibujarParrafo(canvas, titulo, PAGE_W / 2f, y, 16f, bold = true, maxChars = 46, align = Paint.Align.CENTER)
        y += 16f

        dibujarTexto(canvas, "Nº $nroCorrelativo", PAGE_W / 2f, y, 13f, bold = true, align = Paint.Align.CENTER)
        y += 24f

        y = dibujarCampo(canvas, "Fecha: ", fmtFecha.format(Date()), contentLeft, y)
        y = dibujarCampo(canvas, "Empresa que efectúa la certificación: ", "AIES S.A. Consultoría e Inspección", contentLeft, y)
        y = dibujarCampo(canvas, "Transportista: ", transportista.uppercase(), contentLeft, y)

        if (incluirChasis) {
            dibujarCampo(canvas, "Patente Chasis/Tractor: ", patenteChasis.uppercase(), contentLeft, y)
            dibujarCampo(canvas, "Patente Acoplado/Semirremolque: ", patenteAcoplado.uppercase(), contentLeft + contentWidth * 0.4f, y)
            y += 20f
        } else {
            y = dibujarCampo(canvas, "Patente Acoplado/Semirremolque: ", patenteAcoplado.uppercase(), contentLeft, y)
        }

        y = dibujarCampo(canvas, "Sistema de Tapas (zunchada / abulonada): ", sistemaTapas, contentLeft, y)
        y += 6f

        val textoLegal = if (tipoCertificado == "Axion")
            "Certifico que he realizado el control y ensayo de integridad de dispositivos críticos (tapas, válvulas), para ser presentado ante Pan American Energy, con resultado SATISFACTORIO.-"
        else
            "Certifico que he realizado el protocolo de inspección de Shell Capsa de “Tapas paso de hombre 20” de Camiones Tanques Productos Livianos” (versión 1.0), a todos los componentes de la/s unidad/es mencionada/s, con resultado SATISFACTORIO.-"

        y = dibujarParrafo(canvas, textoLegal, contentLeft, y, 11f, maxChars = 78, align = Paint.Align.LEFT)
        // Espacio deliberadamente grande: sin esto todo el contenido queda apretado en el
        // primer tercio de la hoja y el resto de la A4 queda vacío. Empuja firma/vencimiento/
        // nota hacia el centro-abajo de la página, dentro del recuadro.
        y += 165f

        val mitad = contentLeft + contentWidth / 2f
        dibujarTexto(canvas, ".".repeat(50), contentLeft + contentWidth * 0.05f, y, 11f)
        dibujarTexto(canvas, ".".repeat(50), mitad + contentWidth * 0.05f, y, 11f)
        y += 16f
        dibujarTexto(canvas, "Firma y aclaración responsable técnico", contentLeft + contentWidth * 0.25f, y, 10f, align = Paint.Align.CENTER)
        dibujarTexto(canvas, "Firma y aclaración del chofer o transportista", mitad + contentWidth * 0.25f, y, 10f, align = Paint.Align.CENTER)
        y += 80f

        val vencimiento = Calendar.getInstance().apply { add(Calendar.YEAR, 1) }.time
        y = dibujarCampo(canvas, "Este certificado es válido hasta: ", fmtFecha.format(vencimiento), contentLeft, y)
        y += 50f
        dibujarParrafo(
            canvas,
            "Nota: En caso de que la unidad sea del tipo tractor con semirremolque, sólo colocar solo la patente del semirremolque. Para chasis y acoplado, colocar ambas.-",
            contentLeft, y, 10f, maxChars = 90, align = Paint.Align.LEFT
        )

        dibujarBorde(canvas, boxLeft, boxTop, boxRight, PAGE_H - 90f)
        dibujarPie(canvas)

        doc.finishPage(page)
        return doc
    }

    // ── Nota de reemplazo de precintos ──────────────────────────────────────────────

    /** [numeroInicial] + auto-cálculo de la numeración de precintos por tapa (1 ó 2 por tapa). */
    fun calcularNumeracionPrecintos(cantidadTapas: Int, precintosPorTapa: Int, numeroInicial: Int): List<String> {
        return (0 until 7).map { i ->
            if (i >= cantidadTapas) ""
            else if (precintosPorTapa == 1) (numeroInicial + i).toString()
            else {
                val n1 = numeroInicial + i * 2
                "$n1/${n1 + 1}"
            }
        }
    }

    fun generarNotaPrecintos(
        assets: AssetManager,
        tablaCalibracion: String,
        tipoCable: String,
        expendidoPor: String,
        transportista: String,
        patenteSemirremolque: String,
        patenteTractor: String,
        chofer: String,
        cantidadTapas: Int,
        precintosPorTapa: Int,
        numeroInicial: Int
    ): PdfDocument {
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W.toInt(), PAGE_H.toInt(), 1).create())
        val canvas = page.canvas
        val margin = 30f
        val precintos = calcularNumeracionPrecintos(cantidadTapas, precintosPorTapa, numeroInicial)
        val fecha = fmtFecha.format(Date())

        // Nota: sin alpha explícito — replica fielmente que la marca de agua acá queda
        // opaca al 100% en el generador original (a diferencia del certificado, que sí
        // le pone 0.15 de opacidad).
        dibujarBitmap(canvas, assets, "blob.png", x = 0f, y = PAGE_H - 130f, ancho = 220f, alto = 130f)
        dibujarBitmap(assets = assets, canvas = canvas, nombreAsset = "logo.png", x = PAGE_W - margin - 130f, y = margin, ancho = 130f, alto = 40f)

        // El logo ocupa y=[margin, margin+40]; el texto arrancaba a la misma altura y quedaba
        // pegado arriba de la hoja. Se baja el inicio para que quede debajo del logo, con más
        // aire entre el título y el resto del encabezado.
        var y = margin + 40f + 20f
        val left = margin
        val right = PAGE_W - margin
        val width = right - left

        dibujarTexto(canvas, "Empresa: Aies S.A.", left, y, 12f, bold = true); y += 20f
        dibujarTexto(canvas, "Tabla de Precintos de Paso de Hombre 20”", left, y, 13f, bold = true); y += 24f
        dibujarTexto(canvas, "Se han reemplazado los precintos de tapas de paso de hombre de 20” de la unidad", left, y, 10.5f); y += 16f
        dibujarTexto(canvas, "No se han modificado ni controlado el resto de los datos de la tabla original", left, y, 10.5f); y += 34f

        dibujarCampo(canvas, "Tipo de precinto: ", "Aluminio grabado y numerado", left, y - 8f)
        dibujarCampo(canvas, "Tipo cable: ", tipoCable, left + width * 0.6f, y - 8f)
        y += 20f

        // ── Tabla principal (2 columnas) ─────────────────────────────────────────
        // Relleno azul solo en las etiquetas pedidas; el resto (fila "Patente" en blanco y
        // los números de cisterna 1-7) va sin relleno, como una celda de valor más.
        val colW = width / 2f
        val rowH = 18f
        data class FilaTabla(val label: String, val valor: String, val azul: Boolean)
        val filas = listOf(
            FilaTabla("Tabla de Calibración Nº:", tablaCalibracion, true),
            FilaTabla("Expendido por:", expendidoPor, true),
            FilaTabla("Transportista:", transportista, true),
            FilaTabla("Patente", "", true),
            FilaTabla("Semirremolque", patenteSemirremolque, true),
            FilaTabla("Tractor", patenteTractor, true),
            FilaTabla("Cisterna Nº", "Precinto Tapa de P.H. 20\" Nº:", true)
        )
        for (fila in filas) {
            if (fila.azul) dibujarCeldaEtiqueta(canvas, fila.label, left, y, colW, rowH)
            else dibujarCeldaValor(canvas, fila.label, left, y, colW, rowH)
            dibujarCeldaValor(canvas, fila.valor, left + colW, y, colW, rowH)
            y += rowH
        }
        for (i in 0 until 7) {
            dibujarCeldaValor(canvas, (i + 1).toString(), left, y, colW, rowH)
            dibujarCeldaValor(canvas, precintos[i], left + colW, y, colW, rowH)
            y += rowH
        }
        y += 14f

        // ── Firma chofer ──────────────────────────────────────────────────────────
        // Misma altura (34) que la fila de sello/firma supervisor/fecha de abajo — antes eran
        // 26 vs 34 y quedaban visualmente descompensadas.
        val halfW = width / 2f
        val filaFirmaH = 34f
        dibujarCeldaValor(canvas, chofer, left, y, halfW, filaFirmaH)
        dibujarCeldaValor(canvas, "", left + halfW, y, halfW, filaFirmaH)
        y += filaFirmaH
        dibujarCeldaEtiqueta(canvas, "Nombre y Apellido Chofer", left, y, halfW, 14f)
        dibujarCeldaEtiqueta(canvas, "Firma Chofer", left + halfW, y, halfW, 14f)
        y += 22f

        // ── Firma supervisor + fecha ─────────────────────────────────────────────
        val thirdW = width / 3f
        dibujarCeldaValor(canvas, "", left, y, thirdW, filaFirmaH)
        dibujarCeldaValor(canvas, "", left + thirdW, y, thirdW, filaFirmaH)
        run {
            val boxLeft = left + thirdW * 2
            dibujarCeldaValor(canvas, "", boxLeft, y, thirdW, filaFirmaH)
            // Fecha agrandada para que pese visualmente lo mismo que el resto del bloque.
            dibujarTexto(canvas, "FECHA:", boxLeft + thirdW / 2f, y + 15f, 12f, bold = true, align = Paint.Align.CENTER)
            dibujarTexto(canvas, fecha, boxLeft + thirdW / 2f, y + 30f, 16f, bold = true, align = Paint.Align.CENTER)
        }
        y += filaFirmaH
        dibujarCeldaEtiqueta(canvas, "Sello supervisor Aies SA.", left, y, thirdW, 14f)
        dibujarCeldaEtiqueta(canvas, "Firma supervisor", left + thirdW, y, thirdW, 14f)
        y += 14f

        if (precintosPorTapa == 1) {
            // Anclada a una posición fija cerca del pie, en vez de continuar el flujo justo
            // después de la tabla — así siempre queda al final de la hoja, completa, sin
            // importar cuánto haya crecido el contenido de arriba. Este párrafo envuelve a 2
            // líneas (ver wrapText) -- PAGE_H - 80f dejaba la 2ª línea a ~35pt del pie
            // (dibujarPie, que arranca en PAGE_H - 30f), lo bastante cerca como para leerse
            // apretado/superpuesto en algunas impresiones. Subido a PAGE_H - 105f para dejar un
            // margen real (~60pt) entre el final de este texto y el pie.
            val yNotaFija = PAGE_H - 105f
            dibujarParrafo(
                canvas,
                "NOTA: La unidad presenta la disponibilidad para la colocación de un solo precinto por tapa paso de hombre.",
                left, yNotaFija, 9.5f, maxChars = 95, align = Paint.Align.LEFT, bold = true
            )
        }

        dibujarPie(canvas, topPad = PAGE_H - 30f)

        doc.finishPage(page)
        return doc
    }

    // ── Helpers de dibujo ────────────────────────────────────────────────────────────

    private fun dibujarTexto(
        canvas: Canvas, s: String, x: Float, y: Float, size: Float,
        bold: Boolean = false, color: Int = Color.BLACK, align: Paint.Align = Paint.Align.LEFT
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size; this.color = color; isFakeBoldText = bold; textAlign = align
        }
        canvas.drawText(s, x, y, paint)
    }

    /** Dibuja "etiqueta: valor" en una sola línea y devuelve el próximo Y disponible. */
    private fun dibujarCampo(canvas: Canvas, etiqueta: String, valor: String, x: Float, y: Float): Float {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f; isFakeBoldText = true }
        canvas.drawText(etiqueta, x, y, paint)
        val anchoEtiqueta = paint.measureText(etiqueta)
        paint.isFakeBoldText = false
        canvas.drawText(valor, x + anchoEtiqueta, y, paint)
        return y + 18f
    }

    private fun dibujarParrafo(
        canvas: Canvas, texto: String, x: Float, y: Float, size: Float,
        maxChars: Int, align: Paint.Align, bold: Boolean = false
    ): Float {
        var yPos = y
        val renglones = com.aies.corepdf.PdfDrawUtils.wrapText(texto, maxChars)
        for (renglon in renglones) {
            dibujarTexto(canvas, renglon, x, yPos, size, bold = bold, align = align)
            yPos += size + 5f
        }
        return yPos
    }

    private fun dibujarBorde(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 1.5f; color = Color.BLACK
        }
        canvas.drawRect(RectF(left, top, right, bottom), paint)
    }

    private fun dibujarCeldaEtiqueta(canvas: Canvas, texto: String, x: Float, y: Float, w: Float, h: Float) {
        val fondo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#3B6CB7"); style = Paint.Style.FILL }
        val borde = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2E5B9A"); style = Paint.Style.STROKE; strokeWidth = 0.6f }
        val rect = RectF(x, y, x + w, y + h)
        canvas.drawRect(rect, fondo)
        canvas.drawRect(rect, borde)
        if (texto.isNotEmpty()) dibujarTexto(canvas, texto, x + w / 2f, y + h / 2f + 4f, 8f, bold = true, color = Color.WHITE, align = Paint.Align.CENTER)
    }

    private fun dibujarCeldaValor(canvas: Canvas, texto: String, x: Float, y: Float, w: Float, h: Float) {
        val borde = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2E5B9A"); style = Paint.Style.STROKE; strokeWidth = 0.6f }
        val rect = RectF(x, y, x + w, y + h)
        canvas.drawRect(rect, borde)
        if (texto.isNotEmpty()) dibujarTexto(canvas, texto, x + w / 2f, y + h / 2f + 4f, 9f, align = Paint.Align.CENTER)
    }

    private fun dibujarPie(canvas: Canvas, topPad: Float = PAGE_H - 55f) {
        dibujarTexto(canvas, "José J. Podestá 64, Avellaneda, Pcia. de Buenos Aires.", PAGE_W - 40f, topPad, 9f, align = Paint.Align.RIGHT)
        dibujarTexto(canvas, "+54 9 11 3124 0535  |  +54 11 4201 0451  |  info@aies.com.ar  |  www.aies.com.ar", PAGE_W - 40f, topPad + 12f, 9f, align = Paint.Align.RIGHT)
    }

    private fun dibujarBitmap(
        canvas: Canvas, assets: AssetManager, nombreAsset: String, x: Float, y: Float,
        ancho: Float, alto: Float, alpha: Int = 255
    ) {
        try {
            val listaAssets = assets.list("") ?: emptyArray()
            if (!listaAssets.contains(nombreAsset)) return
            val bitmap: Bitmap = assets.open(nombreAsset).use { BitmapFactory.decodeStream(it) } ?: return
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.alpha = alpha }
            val destino = RectF(x, y, x + ancho, y + alto)
            canvas.drawBitmap(bitmap, null, destino, paint)
        } catch (_: Exception) {
            // Igual que en el original: si falta el logo/marca de agua, se deja el espacio en blanco.
        }
    }
}
