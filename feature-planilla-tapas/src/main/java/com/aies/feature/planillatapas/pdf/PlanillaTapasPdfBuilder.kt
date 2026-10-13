package com.aies.feature.planillatapas.pdf

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.aies.corepdf.MM
import com.aies.corepdf.PdfDrawUtils
import com.aies.feature.planillatapas.data.DetalleCompartimentoTapas
import com.aies.feature.planillatapas.data.ItemChecklistTapas
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * PDF de la "Planilla de Control de Tapas de Cisternas" (R 11-01): sin membrete AIES, mismo
 * criterio que feature-controles-visuales/feature-repuestos. Encabezado (con datos de tractor Y
 * semi, a diferencia de Repuestos/Controles Visuales que solo llevan la cisterna, porque esta
 * planilla se pide con el combo completo) + tabla por compartimiento (producto/gases) +
 * checklist de ~38 filas agrupado por sección, paginado automáticamente ya que no entra en una
 * sola página A4.
 */
object PlanillaTapasPdfBuilder {

    private const val W = 595f
    private const val H = 842f
    private val ML = 13 * MM
    private val MR = 13 * MM
    private val AW = W - ML - MR
    private val MARGEN_INFERIOR = 15 * MM

    private val AZUL_OSC: Int = Color.parseColor("#1D3461")
    private val AZUL_MED: Int = Color.parseColor("#2E5B9A")
    private val AZUL_CELL: Int = Color.parseColor("#3B6CB7")
    private val GRIS_CLARO: Int = Color.parseColor("#F5F5F5")
    private val BLANCO = Color.WHITE

    private val FMT_FECHA = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private val FMT_HORA = SimpleDateFormat("HH:mm", Locale.getDefault())
    private fun fFecha(millis: Long?): String = millis?.let { FMT_FECHA.format(Date(it)) } ?: ""
    private fun fHora(millis: Long?): String = millis?.let { FMT_HORA.format(Date(it)) } ?: ""

    private class Pager(private val doc: PdfDocument) {
        var page: PdfDocument.Page = doc.startPage(PdfDocument.PageInfo.Builder(W.toInt(), H.toInt(), 1).create())
            private set
        var canvas: Canvas = page.canvas
            private set
        var y: Float = H - 15 * MM
        private var numeroPagina = 1

        fun nuevaPagina() {
            doc.finishPage(page)
            numeroPagina++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W.toInt(), H.toInt(), numeroPagina).create())
            canvas = page.canvas
            y = H - 15 * MM
        }

        fun asegurarEspacio(alturaFila: Float) {
            if (y - alturaFila < MARGEN_INFERIOR) nuevaPagina()
        }

        fun finalizar() = doc.finishPage(page)
    }

    private fun wrapAAncho(texto: String, anchoPt: Float, fontSize: Float): List<String> {
        if (texto.isEmpty()) return emptyList()
        val limiteCaracteres = (anchoPt / (fontSize * 0.52f)).toInt().coerceAtLeast(4)
        return PdfDrawUtils.wrapText(texto, limiteCaracteres)
    }

    private fun celdaMultilinea(
        canvas: Canvas, lineas: List<String>, x: Float, yBottom: Float, w: Float, h: Float,
        border: Int, fontSize: Float, background: Int
    ) {
        PdfDrawUtils.filledRect(canvas, H, x, yBottom, w, h, background, border, 0.5f)
        if (lineas.isEmpty()) return
        val lineHeight = fontSize * 1.25f
        val bloque = (lineas.size - 1) * lineHeight
        var yCenter = yBottom + h / 2f + bloque / 2f
        for (linea in lineas) {
            PdfDrawUtils.text(canvas, H, linea, x + 1.5f * MM, yCenter - fontSize * 0.38f, fontSize, color = Color.DKGRAY, align = Paint.Align.LEFT)
            yCenter -= lineHeight
        }
    }

    data class Encabezado(
        val fecha: Long,
        val horaInicio: Long?,
        val horaFin: Long?,
        val cliente: String,
        val domicilio: String,
        val cuit: String,
        val auditor: String,
        val fabricanteTapaPh: String,
        val dominioCisterna: String,
        val patenteTractor: String,
        val marcaTractor: String,
        val anioTractor: Int,
        val modeloTractor: String,
        val patenteSemi: String,
        val marcaSemi: String,
        val anioSemi: Int,
        val modeloSemi: String
    )

    fun generar(
        enc: Encabezado,
        detalles: List<DetalleCompartimentoTapas>,
        items: List<ItemChecklistTapas>
    ): PdfDocument {
        val doc = PdfDocument()
        val pager = Pager(doc)

        PdfDrawUtils.text(pager.canvas, H, "PLANILLA DE CONTROL DE TAPAS DE CISTERNAS (R 11-01)", W / 2, pager.y, 12.5f, bold = true, color = AZUL_OSC, align = Paint.Align.CENTER)
        pager.y -= 5 * MM
        PdfDrawUtils.line(pager.canvas, H, ML, pager.y, W - MR, pager.y, AZUL_OSC, 1.2f)
        pager.y -= 5 * MM

        // Encabezado: 2 columnas (izquierda datos del servicio/cisterna, derecha tractor + semi).
        val ch = 6.3f * MM
        val lw = 34 * MM
        val vw = AW / 2 - lw - 2 * MM
        val x2 = ML + AW / 2 + 2 * MM

        val izq = listOf(
            "FECHA" to fFecha(enc.fecha),
            "HORA INICIO" to fHora(enc.horaInicio),
            "HORA FIN" to fHora(enc.horaFin),
            "CLIENTE / RAZÓN SOCIAL" to enc.cliente,
            "DOMICILIO" to enc.domicilio,
            "CUIT" to enc.cuit,
            "AUDITOR" to enc.auditor,
            "DOMINIO CISTERNA" to enc.dominioCisterna,
            "FABRICANTE TAPA PH." to enc.fabricanteTapaPh
        )
        val der = listOf(
            "PATENTE TRACTOR" to enc.patenteTractor,
            "MARCA TRACTOR" to enc.marcaTractor,
            "AÑO TRACTOR" to if (enc.anioTractor > 0) enc.anioTractor.toString() else "",
            "MODELO TRACTOR" to enc.modeloTractor,
            "PATENTE SEMI" to enc.patenteSemi,
            "MARCA SEMI" to enc.marcaSemi,
            "AÑO SEMI" to if (enc.anioSemi > 0) enc.anioSemi.toString() else "",
            "MODELO SEMI" to enc.modeloSemi
        )

        val filas = maxOf(izq.size, der.size)
        for (i in 0 until filas) {
            val yFila = pager.y - i * ch
            izq.getOrNull(i)?.let { (lbl, valor) ->
                PdfDrawUtils.labelCell(pager.canvas, H, lbl, ML, yFila - ch, lw, ch, AZUL_CELL, AZUL_MED, 6.3f)
                PdfDrawUtils.valueCell(pager.canvas, H, valor, ML + lw, yFila - ch, vw, ch, AZUL_MED, 7.5f, align = Paint.Align.LEFT)
            }
            der.getOrNull(i)?.let { (lbl, valor) ->
                PdfDrawUtils.labelCell(pager.canvas, H, lbl, x2, yFila - ch, lw, ch, AZUL_CELL, AZUL_MED, 6.3f)
                PdfDrawUtils.valueCell(pager.canvas, H, valor, x2 + lw, yFila - ch, AW / 2 - lw, ch, AZUL_MED, 7.5f, align = Paint.Align.LEFT)
            }
        }
        pager.y -= filas * ch + 5 * MM

        // Tabla "Puntos de control" por compartimiento/cisterna.
        pager.asegurarEspacio(10 * MM)
        PdfDrawUtils.text(pager.canvas, H, "PUNTOS DE CONTROL POR COMPARTIMIENTO", ML, pager.y, 10.5f, bold = true, color = AZUL_OSC)
        pager.y -= 5 * MM

        val hh = 7 * MM
        val rh = 7 * MM
        val colNum = 22 * MM
        val colProd = 95 * MM
        val colGas = AW - colNum - colProd
        val hdrsCompart = listOf("N° CISTERNA" to colNum, "ÚLTIMO PRODUCTO TRANSPORTADO" to colProd, "MEDICIÓN GASES % LEL" to colGas)

        pager.asegurarEspacio(hh)
        var x = ML
        hdrsCompart.forEach { (lbl, w) ->
            PdfDrawUtils.labelCell(pager.canvas, H, lbl, x, pager.y - hh, w, hh, AZUL_CELL, AZUL_MED, 7f)
            x += w
        }
        pager.y -= hh

        detalles.sortedBy { it.cisternaNumero }.forEachIndexed { i, d ->
            pager.asegurarEspacio(rh)
            val bg = if (i % 2 != 0) GRIS_CLARO else BLANCO
            x = ML
            PdfDrawUtils.valueCell(pager.canvas, H, d.cisternaNumero.toString(), x, pager.y - rh, colNum, rh, AZUL_MED, 8f, align = Paint.Align.CENTER, background = bg)
            x += colNum
            PdfDrawUtils.valueCell(pager.canvas, H, d.ultimoProductoTransportado, x, pager.y - rh, colProd, rh, AZUL_MED, 8f, align = Paint.Align.LEFT, background = bg)
            x += colProd
            PdfDrawUtils.valueCell(pager.canvas, H, d.medicionGasesLel?.toString() ?: "", x, pager.y - rh, colGas, rh, AZUL_MED, 8f, align = Paint.Align.CENTER, background = bg)
            pager.y -= rh
        }
        pager.y -= 5 * MM

        // Checklist, agrupado por sección.
        val colCod = 16 * MM
        val colDesc = 100 * MM
        val colCumple = 16 * MM
        val colObs = AW - colCod - colDesc - colCumple

        fun dibujarEncabezadoChecklist() {
            pager.asegurarEspacio(hh)
            var xh = ML
            val hdrs = listOf("N°" to colCod, "DESCRIPCIÓN" to colDesc, "CUMPLE" to colCumple, "OBSERVACIONES" to colObs)
            hdrs.forEach { (lbl, w) ->
                PdfDrawUtils.labelCell(pager.canvas, H, lbl, xh, pager.y - hh, w, hh, AZUL_CELL, AZUL_MED, 7f)
                xh += w
            }
            pager.y -= hh
        }

        pager.asegurarEspacio(hh * 2)
        PdfDrawUtils.text(pager.canvas, H, "CHECKLIST", ML, pager.y, 10.5f, bold = true, color = AZUL_OSC)
        pager.y -= 5 * MM
        dibujarEncabezadoChecklist()

        var seccionActual: String? = null
        items.sortedBy { it.orden }.forEachIndexed { i, item ->
            pager.asegurarEspacio(rh * 2)
            if (item.seccion != seccionActual) {
                seccionActual = item.seccion
                PdfDrawUtils.filledRect(pager.canvas, H, ML, pager.y - rh, AW, rh, AZUL_OSC)
                PdfDrawUtils.text(pager.canvas, H, item.seccion, ML + 2 * MM, pager.y - rh / 2 - 1.2f, 7.5f, bold = true, color = BLANCO)
                pager.y -= rh
                pager.asegurarEspacio(rh)
                dibujarEncabezadoChecklist()
            }

            val bg = if (i % 2 != 0) GRIS_CLARO else BLANCO
            val descLineas = wrapAAncho(item.descripcion, colDesc - 3 * MM, 6.3f)
            val obsLineas = wrapAAncho(item.observacion, colObs - 3 * MM, 6.3f)
            val filaAlto = maxOf(rh, maxOf(descLineas.size, obsLineas.size, 1) * 6.3f * 1.25f + 2 * MM)
            pager.asegurarEspacio(filaAlto)

            var xr = ML
            PdfDrawUtils.valueCell(pager.canvas, H, item.numeroVisible, xr, pager.y - filaAlto, colCod, filaAlto, AZUL_MED, 7.5f, bold = true, background = bg)
            xr += colCod
            celdaMultilinea(pager.canvas, descLineas, xr, pager.y - filaAlto, colDesc, filaAlto, AZUL_MED, 6.3f, bg)
            xr += colDesc
            PdfDrawUtils.valueCell(pager.canvas, H, if (item.cumple) "SI" else "NO", xr, pager.y - filaAlto, colCumple, filaAlto, AZUL_MED, 7.5f, background = bg)
            xr += colCumple
            celdaMultilinea(pager.canvas, obsLineas, xr, pager.y - filaAlto, colObs, filaAlto, AZUL_MED, 6.3f, bg)
            pager.y -= filaAlto
        }

        pager.y -= 10 * MM
        pager.asegurarEspacio(20 * MM)
        PdfDrawUtils.line(pager.canvas, H, ML + AW / 2 - 30 * MM, pager.y, ML + AW / 2 + 30 * MM, pager.y, Color.DKGRAY, 0.7f)
        pager.y -= 4 * MM
        PdfDrawUtils.text(pager.canvas, H, "Firma responsable técnico", W / 2, pager.y, 8f, color = Color.DKGRAY, align = Paint.Align.CENTER)

        pager.finalizar()
        return doc
    }
}
