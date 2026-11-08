package com.aies.feature.planillaatmosfera.pdf

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.aies.corepdf.MM
import com.aies.corepdf.PdfDrawUtils
import com.aies.feature.planillaatmosfera.data.AtmosferaCisterna
import com.aies.feature.planillaatmosfera.data.PlanillaAtmosferaRegistro
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * PDF de la "Planilla de Control de Atmósfera de Tanques Cisterna" (R 12-01 Rev.01), sin membrete
 * AIES (mismo criterio que feature-planilla-tapas). Con más de 7 cisternas la tabla de gases
 * pagina sola.
 */
object PlanillaAtmosferaPdfBuilder {

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

    data class Encabezado(
        val fecha: Long,
        val transportista: String,
        val tractor: String,
        val tanque: String
    )

    private fun fmt(v: Double?): String = v?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: ""

    fun generar(enc: Encabezado, r: PlanillaAtmosferaRegistro, cisternas: List<AtmosferaCisterna>): PdfDocument {
        val doc = PdfDocument()
        val pager = Pager(doc)

        PdfDrawUtils.text(pager.canvas, H, "PLANILLA DE CONTROL DE ATMÓSFERA DE TANQUES CISTERNA", W / 2, pager.y, 12.5f, bold = true, color = AZUL_OSC, align = Paint.Align.CENTER)
        pager.y -= 5 * MM
        PdfDrawUtils.text(pager.canvas, H, "R 12-01 — Revisión 01 — Fecha: ${FMT_FECHA.format(Date(enc.fecha))}", W / 2, pager.y, 9f, color = Color.DKGRAY, align = Paint.Align.CENTER)
        pager.y -= 3 * MM
        PdfDrawUtils.line(pager.canvas, H, ML, pager.y, W - MR, pager.y, AZUL_OSC, 1.2f)
        pager.y -= 5 * MM

        val ch = 6.3f * MM
        val lw = 38 * MM
        val vw = AW - lw
        listOf(
            "TRANSPORTISTA" to enc.transportista,
            "TRACTOR" to enc.tractor,
            "TANQUE / UNIDAD CÓDIGO" to enc.tanque,
            "FECHA" to FMT_FECHA.format(Date(enc.fecha))
        ).forEach { (lbl, valor) ->
            PdfDrawUtils.labelCell(pager.canvas, H, lbl, ML, pager.y - ch, lw, ch, AZUL_CELL, AZUL_MED, 6.3f)
            PdfDrawUtils.valueCell(pager.canvas, H, valor, ML + lw, pager.y - ch, vw, ch, AZUL_MED, 7.5f, align = Paint.Align.LEFT)
            pager.y -= ch
        }
        pager.y -= 5 * MM

        fun marca(b: Boolean) = if (b) "[X]" else "[  ]"
        fun titulo(t: String) {
            pager.asegurarEspacio(12 * MM)
            PdfDrawUtils.text(pager.canvas, H, t, ML, pager.y, 10f, bold = true, color = AZUL_OSC)
            pager.y -= 5 * MM
        }
        fun renglon(t: String) {
            pager.asegurarEspacio(5 * MM)
            PdfDrawUtils.text(pager.canvas, H, t, ML + 2 * MM, pager.y, 8.5f, color = Color.DKGRAY)
            pager.y -= 4.5f * MM
        }

        titulo("TIPO DE TRABAJO")
        renglon("${marca(r.tipoEstanqueidad)} Prueba de estanqueidad")
        renglon("${marca(r.tipoIntegridadDispositivos)} Control y ensayo de integridad de dispositivos críticos (tapas, válvulas de presión y vacío)")
        renglon("${marca(r.tipoEspaciosConfinados)} Ingreso a espacios confinados")
        renglon("${marca(r.tipoOtros)} Otros trabajos${if (r.tipoOtros && r.tipoOtrosDetalle.isNotBlank()) ": ${r.tipoOtrosDetalle}" else ""}")
        pager.y -= 3 * MM

        titulo("PRODUCTOS TRANSPORTADOS EN LOS DOS ÚLTIMOS VIAJES")
        renglon("${marca(r.productoDiesel)} Diesel     ${marca(r.productoNafta)} Nafta     ${marca(r.productoFame)} Fame")
        pager.y -= 3 * MM

        titulo("MEDICIÓN DE GASES POR CISTERNA")
        val hh = 7 * MM
        val rh = 7 * MM
        val colNum = 30 * MM
        val colGas = (AW - colNum) / 4
        fun encabezadoTabla() {
            pager.asegurarEspacio(hh)
            var x = ML
            listOf("N° CISTERNA" to colNum, "LEL (%)" to colGas, "O2 (%)" to colGas, "CO (ppm)" to colGas, "H2S (ppm)" to colGas).forEach { (lbl, w) ->
                PdfDrawUtils.labelCell(pager.canvas, H, lbl, x, pager.y - hh, w, hh, AZUL_CELL, AZUL_MED, 7f)
                x += w
            }
            pager.y -= hh
        }
        encabezadoTabla()
        cisternas.sortedBy { it.cisternaNumero }.forEachIndexed { i, c ->
            if (pager.y - rh < MARGEN_INFERIOR) { pager.nuevaPagina(); encabezadoTabla() }
            val bg = if (i % 2 != 0) GRIS_CLARO else BLANCO
            var x = ML
            PdfDrawUtils.valueCell(pager.canvas, H, c.cisternaNumero.toString(), x, pager.y - rh, colNum, rh, AZUL_MED, 8f, align = Paint.Align.CENTER, background = bg)
            x += colNum
            listOf(fmt(c.lel), fmt(c.o2), fmt(c.co), fmt(c.h2s)).forEach { v ->
                PdfDrawUtils.valueCell(pager.canvas, H, v, x, pager.y - rh, colGas, rh, AZUL_MED, 8f, align = Paint.Align.CENTER, background = bg)
                x += colGas
            }
            pager.y -= rh
        }
        pager.y -= 3 * MM
        renglon("Valores aceptables: O2 entre 19,5% y 23%; LEL, CO y H2S en 0.")
        pager.y -= 3 * MM

        fun bloqueTexto(tit: String, texto: String) {
            titulo(tit)
            val lineas = PdfDrawUtils.wrapText(texto.ifBlank { "-" }, 105)
            lineas.forEach { renglon(it) }
            pager.y -= 3 * MM
        }
        bloqueTexto("EQUIPO UTILIZADO", r.equipoUtilizado)
        bloqueTexto("OBSERVACIONES", r.observaciones)

        pager.y -= 8 * MM
        pager.asegurarEspacio(25 * MM)
        PdfDrawUtils.line(pager.canvas, H, ML + AW / 2 - 40 * MM, pager.y, ML + AW / 2 + 40 * MM, pager.y, Color.DKGRAY, 0.7f)
        pager.y -= 4 * MM
        if (r.firmaResponsable.isNotBlank()) {
            PdfDrawUtils.text(pager.canvas, H, r.firmaResponsable, W / 2, pager.y, 8.5f, color = Color.DKGRAY, align = Paint.Align.CENTER)
            pager.y -= 4 * MM
        }
        PdfDrawUtils.text(pager.canvas, H, "Firma y aclaración del responsable", W / 2, pager.y, 8f, color = Color.DKGRAY, align = Paint.Align.CENTER)

        pager.finalizar()
        return doc
    }
}
