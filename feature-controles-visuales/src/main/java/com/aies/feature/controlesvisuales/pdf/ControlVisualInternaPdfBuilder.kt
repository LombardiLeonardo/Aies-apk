package com.aies.feature.controlesvisuales.pdf

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.aies.corepdf.MM
import com.aies.corepdf.PdfDrawUtils
import com.aies.feature.controlesvisuales.data.EncabezadoEmbed
import com.aies.feature.controlesvisuales.data.VerificacionCisternaInterna
import com.aies.feature.controlesvisuales.data.VerificacionInternaTextos

/** PDF de "Control Visual Interna" (R 16-01): encabezado + detalle de compartimentos comunes,
 *  seguido de una tabla de 4 verificaciones fijas por cada compartimento. */
object ControlVisualInternaPdfBuilder {

    fun generar(
        encabezado: EncabezadoEmbed,
        detalles: List<ControlPdfCommon.DetalleRow>,
        verificaciones: List<VerificacionCisternaInterna>
    ): PdfDocument {
        val doc = PdfDocument()
        val pager = ControlPdfCommon.Pager(doc)

        ControlPdfCommon.titulo(pager, "CONTROL VISUAL INTERNA DE CISTERNAS")
        ControlPdfCommon.dibujarEncabezado(pager, encabezado)
        ControlPdfCommon.dibujarTablaCompartimentos(pager, detalles)

        val hh = 7 * MM
        val rh = 9 * MM
        val colItem = 100 * MM
        val colCumple = 20 * MM
        val colObs = ControlPdfCommon.AW - colItem - colCumple

        verificaciones.sortedBy { it.numeroCompartimento }.forEach { v ->
            pager.asegurarEspacio(hh + rh)
            PdfDrawUtils.text(pager.canvas, ControlPdfCommon.H, "COMPARTIMENTO N° ${v.numeroCompartimento}", ControlPdfCommon.ML, pager.y, 10f, bold = true, color = ControlPdfCommon.AZUL_OSC)
            pager.y -= 5 * MM

            var x = ControlPdfCommon.ML
            val hdrs = listOf("ÍTEM" to colItem, "CUMPLE" to colCumple, "OBSERVACIONES" to colObs)
            hdrs.forEach { (lbl, w) ->
                PdfDrawUtils.labelCell(pager.canvas, ControlPdfCommon.H, lbl, x, pager.y - hh, w, hh, ControlPdfCommon.AZUL_CELL, ControlPdfCommon.AZUL_MED, 7f)
                x += w
            }
            pager.y -= hh

            val items = listOf(
                Triple(VerificacionInternaTextos.items[0], v.item1Cumple, v.item1Obs),
                Triple(VerificacionInternaTextos.items[1], v.item2Cumple, v.item2Obs),
                Triple(VerificacionInternaTextos.items[2], v.item3Cumple, v.item3Obs),
                Triple(VerificacionInternaTextos.items[3], v.item4Cumple, v.item4Obs)
            )
            items.forEachIndexed { i, (desc, cumple, obs) ->
                pager.asegurarEspacio(rh)
                val bg = if (i % 2 != 0) ControlPdfCommon.GRIS_CLARO else ControlPdfCommon.BLANCO
                x = ControlPdfCommon.ML
                PdfDrawUtils.valueCell(pager.canvas, ControlPdfCommon.H, desc, x, pager.y - rh, colItem, rh, ControlPdfCommon.AZUL_MED, 6.8f, align = Paint.Align.LEFT, background = bg)
                x += colItem
                PdfDrawUtils.valueCell(pager.canvas, ControlPdfCommon.H, if (cumple) "SI" else "NO", x, pager.y - rh, colCumple, rh, ControlPdfCommon.AZUL_MED, 7.5f, background = bg)
                x += colCumple
                PdfDrawUtils.valueCell(pager.canvas, ControlPdfCommon.H, obs, x, pager.y - rh, colObs, rh, ControlPdfCommon.AZUL_MED, 6.8f, align = Paint.Align.LEFT, background = bg)
                pager.y -= rh
            }
            pager.y -= 3 * MM
        }

        pager.finalizar()
        return doc
    }
}
