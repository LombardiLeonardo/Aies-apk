package com.aies.feature.controlesvisuales.pdf

import android.graphics.pdf.PdfDocument
import com.aies.corepdf.MM
import com.aies.corepdf.PdfDrawUtils
import com.aies.feature.controlesvisuales.data.EncabezadoEmbed
import com.aies.feature.controlesvisuales.data.GrillaEspesores
import com.aies.feature.controlesvisuales.data.MedicionEspesor

/**
 * PDF de "Control de Espesores" (R 16-03): encabezado + detalle de compartimentos comunes,
 * seguido de las 4 grillas de mediciones (mm) — Lateral Derecho/Izquierdo, Parte
 * Anterior/Posterior — cada una dibujada como una grilla rectangular genérica de
 * [GrillaEspesores.FILAS] x [GrillaEspesores.COLUMNAS], en vez de reproducir el contorno
 * irregular a mano alzada de la planilla en papel (no vale la complejidad para este generador).
 */
object ControlEspesoresPdfBuilder {

    fun generar(
        encabezado: EncabezadoEmbed,
        detalles: List<ControlPdfCommon.DetalleRow>,
        mediciones: List<MedicionEspesor>
    ): PdfDocument {
        val doc = PdfDocument()
        val pager = ControlPdfCommon.Pager(doc)

        ControlPdfCommon.titulo(pager, "CONTROL DE ESPESORES")
        ControlPdfCommon.dibujarEncabezado(pager, encabezado)
        ControlPdfCommon.dibujarTablaCompartimentos(pager, detalles)

        val porGrilla = mediciones.groupBy { it.grilla }
        val cellW = ControlPdfCommon.AW / GrillaEspesores.COLUMNAS
        val cellH = 7 * MM

        GrillaEspesores.grillas.forEach { nombreGrilla ->
            val alturaNecesaria = 8 * MM + (GrillaEspesores.FILAS + 1) * cellH
            pager.asegurarEspacio(alturaNecesaria)

            PdfDrawUtils.text(pager.canvas, ControlPdfCommon.H, "MEDICIÓN DE ESPESORES (mm) — $nombreGrilla", ControlPdfCommon.ML, pager.y, 10f, bold = true, color = ControlPdfCommon.AZUL_OSC)
            pager.y -= 5 * MM

            val celdas = porGrilla[nombreGrilla].orEmpty().associateBy { it.fila to it.columna }

            var x = ControlPdfCommon.ML
            for (col in 0 until GrillaEspesores.COLUMNAS) {
                PdfDrawUtils.labelCell(pager.canvas, ControlPdfCommon.H, "C${col + 1}", x, pager.y - cellH, cellW, cellH, ControlPdfCommon.AZUL_CELL, ControlPdfCommon.AZUL_MED, 6.5f)
                x += cellW
            }
            pager.y -= cellH

            for (fila in 0 until GrillaEspesores.FILAS) {
                x = ControlPdfCommon.ML
                for (col in 0 until GrillaEspesores.COLUMNAS) {
                    val valor = celdas[fila to col]?.valorMm?.let { "%.1f".format(it) } ?: ""
                    val bg = if (fila % 2 != 0) ControlPdfCommon.GRIS_CLARO else ControlPdfCommon.BLANCO
                    PdfDrawUtils.valueCell(pager.canvas, ControlPdfCommon.H, valor, x, pager.y - cellH, cellW, cellH, ControlPdfCommon.AZUL_MED, 7.5f, background = bg)
                    x += cellW
                }
                pager.y -= cellH
            }
            pager.y -= 5 * MM
        }

        pager.finalizar()
        return doc
    }
}
