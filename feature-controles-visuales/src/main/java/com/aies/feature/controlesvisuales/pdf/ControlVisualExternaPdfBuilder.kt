package com.aies.feature.controlesvisuales.pdf

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.aies.corepdf.MM
import com.aies.corepdf.PdfDrawUtils
import com.aies.feature.controlesvisuales.data.EncabezadoEmbed
import com.aies.feature.controlesvisuales.data.ItemChecklistExterna

/** PDF de "Control Visual Externa" (R 16-02): encabezado + detalle de compartimentos comunes,
 *  seguido del checklist de ~67 ítems agrupados por sección, paginado automáticamente. */
object ControlVisualExternaPdfBuilder {

    fun generar(
        encabezado: EncabezadoEmbed,
        detalles: List<ControlPdfCommon.DetalleRow>,
        items: List<ItemChecklistExterna>
    ): PdfDocument {
        val doc = PdfDocument()
        val pager = ControlPdfCommon.Pager(doc)

        ControlPdfCommon.titulo(pager, "CONTROL VISUAL EXTERNA DE CISTERNAS")
        ControlPdfCommon.dibujarEncabezado(pager, encabezado)
        ControlPdfCommon.dibujarTablaCompartimentos(pager, detalles)

        val hh = 7 * MM
        val rh = 6.5f * MM
        val colCod = 12 * MM
        val colCumple = 18 * MM
        val colDesc = 95 * MM
        val colObs = ControlPdfCommon.AW - colCod - colCumple - colDesc

        pager.asegurarEspacio(hh)
        PdfDrawUtils.text(pager.canvas, ControlPdfCommon.H, "CHECKLIST VISUAL EXTERNA", ControlPdfCommon.ML, pager.y, 10.5f, bold = true, color = ControlPdfCommon.AZUL_OSC)
        pager.y -= 5 * MM

        fun dibujarEncabezadoTabla() {
            var x = ControlPdfCommon.ML
            val hdrs = listOf("N°" to colCod, "DESCRIPCIÓN" to colDesc, "CUMPLE" to colCumple, "OBSERVACIONES" to colObs)
            hdrs.forEach { (lbl, w) ->
                PdfDrawUtils.labelCell(pager.canvas, ControlPdfCommon.H, lbl, x, pager.y - hh, w, hh, ControlPdfCommon.AZUL_CELL, ControlPdfCommon.AZUL_MED, 7f)
                x += w
            }
            pager.y -= hh
        }
        dibujarEncabezadoTabla()

        var seccionActual: String? = null
        items.sortedBy { it.orden }.forEachIndexed { i, item ->
            pager.asegurarEspacio(rh * 2)
            if (item.seccion != seccionActual) {
                seccionActual = item.seccion
                PdfDrawUtils.filledRect(pager.canvas, ControlPdfCommon.H, ControlPdfCommon.ML, pager.y - rh, ControlPdfCommon.AW, rh, ControlPdfCommon.AZUL_OSC)
                PdfDrawUtils.text(pager.canvas, ControlPdfCommon.H, item.seccion, ControlPdfCommon.ML + 2 * MM, pager.y - rh / 2 - 1.2f, 7.5f, bold = true, color = ControlPdfCommon.BLANCO)
                pager.y -= rh
                pager.asegurarEspacio(rh)
                dibujarEncabezadoTabla()
            }

            val bg = if (i % 2 != 0) ControlPdfCommon.GRIS_CLARO else ControlPdfCommon.BLANCO
            val descLineas = ControlPdfCommon.wrapAAncho(item.descripcion, colDesc - 3 * MM, 6.3f)
            val obsLineas = ControlPdfCommon.wrapAAncho(item.observaciones, colObs - 3 * MM, 6.3f)
            val filaAlto = maxOf(rh, (maxOf(descLineas.size, obsLineas.size, 1)) * 6.3f * 1.25f + 2 * MM)
            pager.asegurarEspacio(filaAlto)

            var x = ControlPdfCommon.ML
            PdfDrawUtils.valueCell(pager.canvas, ControlPdfCommon.H, item.codigo, x, pager.y - filaAlto, colCod, filaAlto, ControlPdfCommon.AZUL_MED, 7f, bold = true, background = bg)
            x += colCod
            ControlPdfCommon.celdaMultilinea(pager.canvas, descLineas, x, pager.y - filaAlto, colDesc, filaAlto, ControlPdfCommon.AZUL_MED, 6.3f, bg)
            x += colDesc
            PdfDrawUtils.valueCell(pager.canvas, ControlPdfCommon.H, if (item.cumple) "SI" else "NO", x, pager.y - filaAlto, colCumple, filaAlto, ControlPdfCommon.AZUL_MED, 7f, background = bg)
            x += colCumple
            ControlPdfCommon.celdaMultilinea(pager.canvas, obsLineas, x, pager.y - filaAlto, colObs, filaAlto, ControlPdfCommon.AZUL_MED, 6.3f, bg)
            pager.y -= filaAlto
        }

        pager.finalizar()
        return doc
    }
}
