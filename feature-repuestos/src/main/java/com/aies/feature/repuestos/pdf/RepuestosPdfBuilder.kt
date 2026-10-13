package com.aies.feature.repuestos.pdf

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.aies.corepdf.MM
import com.aies.corepdf.PdfDrawUtils
import com.aies.feature.repuestos.data.DetalleRepuestoCisterna
import com.aies.feature.repuestos.data.RepuestoOptions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * PDF simple (sin membrete AIES, mismo criterio que feature-controles-visuales): encabezado
 * básico + tabla "N° de compartimiento | Repuesto cambiado". Un renglón por compartimiento;
 * si no hubo cambio, la fila muestra "Ninguno".
 */
object RepuestosPdfBuilder {

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

    private val FMT = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    private class Pager(private val doc: PdfDocument) {
        var numeroPagina = 1
            private set
        var page: PdfDocument.Page = doc.startPage(PdfDocument.PageInfo.Builder(W.toInt(), H.toInt(), numeroPagina).create())
            private set
        var canvas: Canvas = page.canvas
            private set
        var y: Float = H - 15 * MM

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

    fun generar(
        fecha: Long,
        razonSocial: String,
        dominio: String,
        auditor: String,
        detalles: List<DetalleRepuestoCisterna>
    ): PdfDocument {
        val doc = PdfDocument()
        val pager = Pager(doc)

        PdfDrawUtils.text(pager.canvas, H, "CAMBIO DE REPUESTOS", W / 2, pager.y, 14f, bold = true, color = AZUL_OSC, align = Paint.Align.CENTER)
        pager.y -= 5 * MM
        PdfDrawUtils.line(pager.canvas, H, ML, pager.y, W - MR, pager.y, AZUL_OSC, 1.2f)
        pager.y -= 6 * MM

        // Encabezado básico: fecha, razón social, dominio, auditor.
        val ch = 6.3f * MM
        val lw = 32 * MM
        val encabezado = listOf(
            "FECHA" to FMT.format(Date(fecha)),
            "CLIENTE / RAZÓN SOCIAL" to razonSocial,
            "PATENTE / DOMINIO" to dominio,
            "AUDITOR" to auditor
        )
        encabezado.forEachIndexed { i, (lbl, valor) ->
            val y = pager.y - i * ch
            PdfDrawUtils.labelCell(pager.canvas, H, lbl, ML, y - ch, lw, ch, AZUL_CELL, AZUL_MED, 7f)
            PdfDrawUtils.valueCell(pager.canvas, H, valor, ML + lw, y - ch, AW - lw, ch, AZUL_MED, 8f, align = Paint.Align.LEFT)
        }
        pager.y -= encabezado.size * ch + 6 * MM

        // Tabla "N° de compartimiento | Repuesto cambiado".
        PdfDrawUtils.text(pager.canvas, H, "DETALLE POR COMPARTIMIENTO", ML, pager.y, 10.5f, bold = true, color = AZUL_OSC)
        pager.y -= 5 * MM

        val hh = 7 * MM
        val rh = 7 * MM
        val col0 = 50 * MM
        val col1 = AW - col0
        val hdrs = listOf("N° DE COMPARTIMIENTO", "REPUESTO CAMBIADO")

        pager.asegurarEspacio(hh)
        var x = ML
        listOf(col0, col1).forEachIndexed { idx, w ->
            PdfDrawUtils.labelCell(pager.canvas, H, hdrs[idx], x, pager.y - hh, w, hh, AZUL_CELL, AZUL_MED, 7.5f)
            x += w
        }
        pager.y -= hh

        // Ahora un compartimiento puede tener MÁS DE UN repuesto cambiado (varias filas de
        // DetalleRepuestoCisterna comparten el mismo cisternaNumero) -- se agrupan acá y se
        // listan todas las etiquetas separadas por coma en una sola fila de la tabla.
        detalles.groupBy { it.cisternaNumero }.toSortedMap().values.forEachIndexed { i, filas ->
            pager.asegurarEspacio(rh)
            val bg = if (i % 2 != 0) GRIS_CLARO else BLANCO
            val etiquetas = filas.map { RepuestoOptions.porValor(it.repuestoCambiado).etiqueta }
            val texto = etiquetas.filter { it != RepuestoOptions.NINGUNO.etiqueta }.ifEmpty { listOf(RepuestoOptions.NINGUNO.etiqueta) }.joinToString(", ")
            x = ML
            PdfDrawUtils.valueCell(pager.canvas, H, filas.first().cisternaNumero.toString(), x, pager.y - rh, col0, rh, AZUL_MED, 8f, align = Paint.Align.CENTER, background = bg)
            x += col0
            PdfDrawUtils.valueCell(pager.canvas, H, texto, x, pager.y - rh, col1, rh, AZUL_MED, 8f, align = Paint.Align.LEFT, background = bg)
            pager.y -= rh
        }

        pager.finalizar()
        return doc
    }
}
