package com.aies.feature.notatrabajo.pdf

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.aies.coredata.dao.ServicioDelDia
import com.aies.coredata.entities.Unidad
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Nota de trabajo consolidada: todos los servicios que una unidad tuvo en un día,
 * pensada para pasarle a facturación. Layout top-down simple con
 * [android.graphics.pdf.PdfDocument] — sin librerías nuevas, mismo enfoque nativo que ya
 * se usa en el resto de core-pdf/feature-*.
 */
object NotaTrabajoPdfBuilder {

    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private val fmtFecha = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private val fmtHora = SimpleDateFormat("HH:mm", Locale.getDefault())

    fun generar(
        context: Context,
        unidad: Unidad,
        fechaMillis: Long,
        servicios: List<ServicioDelDia>,
        capacidadTotalCisternasLitros: Double? = null,
        patenteTractor: String? = null
    ): PdfDocument {
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create())
        val canvas = page.canvas
        val margin = 40f
        var y = margin + 10f

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.textSize = 18f; paint.isFakeBoldText = true
        canvas.drawText("NOTA DE TRABAJO", margin, y, paint)
        // Espacio para completar a mano el N° correlativo de cada nota de trabajo.
        paint.textSize = 13f
        canvas.drawText("N°:", PAGE_W - margin - 150f, y, paint)
        canvas.drawLine(
            PAGE_W - margin - 120f, y + 2f, PAGE_W - margin, y + 2f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; strokeWidth = 1f }
        )
        paint.textSize = 18f
        y += 14f
        paint.textSize = 11f; paint.isFakeBoldText = false
        canvas.drawText("Fecha: ${fmtFecha.format(Date(fechaMillis))}", margin, y, paint)
        y += 24f

        canvas.drawLine(margin, y, PAGE_W - margin, y, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; strokeWidth = 1.2f })
        y += 20f

        y = campo(canvas, "Patente Semirremolque: ", unidad.placa, margin, y)
        if (!patenteTractor.isNullOrBlank()) {
            y = campo(canvas, "Patente Tractor: ", patenteTractor, margin, y)
        }
        y = campo(canvas, "Tipo: ", unidad.tipoUnidad, margin, y)
        y = campo(canvas, "Cliente: ", unidad.cliente, margin, y)
        y += 16f

        // ── Tabla de servicios ──────────────────────────────────────────────────
        val colTipo = margin
        val colAuditor = margin + 260f
        val colHora = margin + 420f
        val rowH = 20f

        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 10f; isFakeBoldText = true }
        val headerBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#37474F") }
        canvas.drawRect(RectF(margin, y, PAGE_W - margin, y + rowH), headerBg)
        canvas.drawText("Tipo de Servicio", colTipo + 6f, y + 14f, headerPaint)
        canvas.drawText("Auditor", colAuditor + 6f, y + 14f, headerPaint)
        canvas.drawText("Hora", colHora + 6f, y + 14f, headerPaint)
        y += rowH

        val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 10f }
        val detallePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#546E7A"); textSize = 9f }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#CFD8DC"); style = Paint.Style.STROKE; strokeWidth = 0.6f }
        val bgAlterna = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F5F5F5") }

        // Debajo del tipo de servicio se agrega una línea de detalle: si el módulo que generó el
        // servicio dejó algo puntual en HistorialServicio.detalle (feature-certificados: total
        // de precintos utilizados; feature-repuestos: qué repuesto se cambió en cada
        // compartimiento -- lo que factibilidad/facturación necesita para saber qué cobrar), se
        // usa eso. Si no, se cae al detalle genérico de cisternas de la unidad de siempre (con
        // capacidad total para el Informe de Carga Segura). Puede ocupar más de una línea
        // (wrapText), así que la altura de la fila es dinámica.
        val lineHDetalle = 12f
        val serviciosParaListar = servicios.filterNot { omitirEnNota(context, it) }
        serviciosParaListar.forEachIndexed { i, s ->
            val textoDetalle = detalleParaServicio(s, unidad, capacidadTotalCisternasLitros)
            val lineasDetalle = if (textoDetalle != null) com.aies.corepdf.PdfDrawUtils.wrapText(textoDetalle, 95) else emptyList()
            val alturaFila = if (lineasDetalle.isEmpty()) rowH else rowH + 14f + (lineasDetalle.size - 1) * lineHDetalle
            if (i % 2 == 1) canvas.drawRect(RectF(margin, y, PAGE_W - margin, y + alturaFila), bgAlterna)
            canvas.drawText(etiquetaServicio(s), colTipo + 6f, y + 14f, cellPaint)
            canvas.drawText(s.auditor, colAuditor + 6f, y + 14f, cellPaint)
            canvas.drawText(fmtHora.format(Date(s.fecha)), colHora + 6f, y + 14f, cellPaint)
            lineasDetalle.forEachIndexed { li, linea ->
                canvas.drawText(linea, colTipo + 6f, y + 27f + li * lineHDetalle, detallePaint)
            }
            canvas.drawRect(RectF(margin, y, PAGE_W - margin, y + alturaFila), borderPaint)
            y += alturaFila
        }

        y += 20f
        val resumenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10.5f; isFakeBoldText = true }
        canvas.drawText("Total de servicios: ${serviciosParaListar.size}", margin, y, resumenPaint)

        doc.finishPage(page)
        return doc
    }

    /**
     * Livianos/Pesados/Coque/Quimicos/Enganche/Axion ("insp_*") generan DOS documentos por
     * servicio (el .xls del checklist y el PDF de reporte de observaciones) — desde que
     * HistorialCompartidoHelper registra ambos, [servicios] trae una fila por documento, y las
     * dos comparten el mismo `tipoServicioNombre` ("Inspección PESADOS" x2). Distingue cuál es
     * cuál por el tipo MIME real (vía ContentResolver) en vez de mirar la URI como string: las
     * URIs de MediaStore son ids opacos (`content://media/external/downloads/12345`), nunca
     * terminan en ".pdf"/".xls" — ese fue el bug de la primera versión de este chequeo.
     *
     * Pedido del usuario: la Nota de Trabajo no debe tomar el PDF de reporte como un ítem de
     * servicio aparte -- es el mismo servicio que el .xls del checklist, solo un segundo
     * documento. Se filtra de la tabla/el conteo con esta función (ver [generar] y
     * `SesionScreen.kt`), pero sigue adjuntándose igual al mail consolidado -- eso usa la lista
     * [servicios] sin filtrar, en `compartirDocumentosDeVisita`.
     */
    fun esReportePdfDuplicado(context: Context, s: ServicioDelDia): Boolean {
        if (!s.tipoServicioCodigo.startsWith("insp_") || s.documentoPath.isBlank()) return false
        val mime = try { context.contentResolver.getType(Uri.parse(s.documentoPath)) } catch (_: Exception) { null }
        return mime == "application/pdf"
    }

    /** Servicios que no deben figurar en la Nota de Trabajo: el PDF de reporte duplicado y la
     *  Planilla de Control de Tapas (pedido del usuario). Siguen adjuntándose al mail. */
    fun omitirEnNota(context: Context, s: ServicioDelDia): Boolean =
        s.tipoServicioCodigo == "planilla_tapas" || esReportePdfDuplicado(context, s)

    private fun etiquetaServicio(s: ServicioDelDia): String = s.tipoServicioNombre

    /** Ver el comentario en el loop de la tabla: prioriza el detalle puntual que dejó el módulo
     *  de origen, y si no hay, cae al detalle genérico de cisternas de siempre. */
    private fun detalleParaServicio(s: ServicioDelDia, unidad: Unidad, capacidadTotalCisternasLitros: Double?): String? {
        if (s.detalle.isNotBlank()) return s.detalle
        if (unidad.cantidadCisternas <= 0) return null
        return if (s.tipoServicioCodigo == "informe_carga_segura" && capacidadTotalCisternasLitros != null) {
            "Cisternas: ${unidad.cantidadCisternas} — Capacidad total: ${capacidadTotalCisternasLitros.toInt()} L"
        } else {
            "Cisternas: ${unidad.cantidadCisternas}"
        }
    }

    private fun campo(canvas: android.graphics.Canvas, etiqueta: String, valor: String, x: Float, y: Float): Float {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f; isFakeBoldText = true }
        canvas.drawText(etiqueta, x, y, paint)
        val ancho = paint.measureText(etiqueta)
        paint.isFakeBoldText = false
        canvas.drawText(valor, x + ancho, y, paint)
        return y + 18f
    }
}
