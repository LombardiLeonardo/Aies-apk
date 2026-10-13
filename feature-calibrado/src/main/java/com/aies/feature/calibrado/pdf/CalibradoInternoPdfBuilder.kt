package com.aies.feature.calibrado.pdf

import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.aies.corepdf.MM
import com.aies.corepdf.PdfDrawUtils

/**
 * Planilla de Calibración de Uso Interno (R 14-03) -- documento NUEVO y separado de la Tarjeta
 * de Calibración externa ([CalibradoPdfBuilder]): no hace falta que se vea igual ni lleva
 * membrete con logo AIES (planilla de uso interno, mismo criterio que las 3 planillas de
 * feature-controles-visuales). Reutiliza el paginador simple estilo
 * feature-controles-visuales.pdf.ControlPdfCommon porque el contenido (7 filas de cisterna +
 * trazabilidad/posición de sensores + descarga patrón) puede desbordar una sola página A4.
 */
object CalibradoInternoPdfBuilder {

    private val AZUL_OSC = Color.parseColor("#1D3461")
    private val AZUL_MED = Color.parseColor("#2E5B9A")
    private val AZUL_CELL = Color.parseColor("#3B6CB7")
    private val BLANCO = Color.WHITE
    private val NEGRO = Color.BLACK
    private val GRIS_CLARO = Color.parseColor("#F5F5F5")

    private const val W = 595f
    private const val H = 842f
    private val ML = 13 * MM
    private val MR = 13 * MM
    private val AW = W - ML - MR
    private val MARGEN_INFERIOR = 15 * MM

    /** Los 5 puntos fijos de la tabla "CONTROL DE DESCARGA PATRON", en el orden de la planilla. */
    val PUNTOS_DESCARGA_PATRON = listOf(3000, 1000, 500, 100, 20)

    data class DatosSensorCisterna(
        val cisternaNumero: Int,
        val sensorMarca: String,
        val sensorNumeroSerie: String,
        val sensorCumple: String, // "SI"/"NO"/""
        val posicionCumple: String, // "SI"/"NO"/""
        val posicionObservacion: String,
        val litrosSensorRebalse: String
    )

    /** [descargaPatron] -- por cada cisterna (índice = cisternaNumero - 1), un mapa
     *  puntoVolumenLts -> lista de lecturas (texto) en el orden en que se ingresaron. */
    data class DatosCalibracionInterno(
        val numero: String,
        val fecha: String,
        val horaInicio: String,
        val horaFin: String,
        val transportista: String,
        val patenteTractor: String,
        val marcaTractor: String,
        val tipoEjesTractor: String,
        val rodadoTractor: String,
        val tara: String,
        val maximoPesoPermitido: String,
        val patenteSemi: String,
        val marcaSemi: String,
        val modeloSemi: String,
        val tipoEjesSemi: String,
        val rodadoSemi: String,
        val cotaAnterior: String,
        val cotaPosterior: String,
        val volumenSemi: String,
        val cisternas: List<CalibradoPdfBuilder.DatosCisterna>,
        val sensores: List<DatosSensorCisterna>,
        val descargaPatron: Map<Int, Map<Int, List<String>>>
    )

    private class Pager(private val doc: PdfDocument) {
        var numeroPagina = 1
            private set
        var page: PdfDocument.Page = doc.startPage(PdfDocument.PageInfo.Builder(W.toInt(), H.toInt(), numeroPagina).create())
            private set
        var canvas = page.canvas
            private set
        var y: Float = H - 15 * MM

        fun nuevaPagina() {
            doc.finishPage(page)
            numeroPagina++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W.toInt(), H.toInt(), numeroPagina).create())
            canvas = page.canvas
            y = H - 15 * MM
        }

        fun asegurarEspacio(altura: Float) {
            if (y - altura < MARGEN_INFERIOR) nuevaPagina()
        }

        fun finalizar() = doc.finishPage(page)
    }

    fun generar(data: DatosCalibracionInterno): PdfDocument {
        val doc = PdfDocument()
        val pager = Pager(doc)

        titulo(pager, "PLANILLA DE CALIBRACIÓN DE USO INTERNO (R 14-03)")
        dibujarEncabezado(pager, data)
        dibujarControlCisternas(pager, data.cisternas)
        dibujarControlPrecintos(pager, data.cisternas)
        dibujarTrazabilidadSensores(pager, data.sensores)
        dibujarPosicionSensores(pager, data.sensores)
        dibujarFirma(pager, "Firma responsable técnico:")
        dibujarDescargaPatron(pager, data.cisternas.size.coerceAtLeast(1), data.descargaPatron)
        dibujarFirma(pager, "Firma de responsable técnico:")

        pager.finalizar()
        return doc
    }

    private fun titulo(pager: Pager, texto: String) {
        PdfDrawUtils.text(pager.canvas, H, texto, W / 2, pager.y, 13f, bold = true, color = AZUL_OSC, align = Paint.Align.CENTER)
        pager.y -= 5 * MM
        PdfDrawUtils.line(pager.canvas, H, ML, pager.y, W - MR, pager.y, AZUL_OSC, 1.2f)
        pager.y -= 5 * MM
    }

    private fun seccion(pager: Pager, titulo: String) {
        pager.asegurarEspacio(9 * MM)
        PdfDrawUtils.text(pager.canvas, H, titulo, ML, pager.y, 10.5f, bold = true, color = AZUL_OSC)
        pager.y -= 5 * MM
    }

    private fun dibujarEncabezado(pager: Pager, data: DatosCalibracionInterno) {
        val ch = 6.3f * MM
        val lw = 40 * MM
        val vw = AW / 2 - lw - 2 * MM
        val x2 = ML + AW / 2 + 2 * MM

        val izq = listOf(
            "RAZÓN SOCIAL" to data.transportista,
            "FECHA" to data.fecha,
            "HORARIO INICIO" to data.horaInicio,
            "HORARIO FIN" to data.horaFin,
            "PATENTE TRACTOR" to data.patenteTractor,
            "MARCA TRACTOR" to data.marcaTractor,
            "TIPO DE EJES TRACTOR" to data.tipoEjesTractor,
            "RODADO TRACTOR" to data.rodadoTractor,
            "TARA TOTAL" to data.tara,
            "P.M.P" to data.maximoPesoPermitido
        )
        val der = listOf(
            "PATENTE SEMIRREMOLQUE" to data.patenteSemi,
            "MARCA SEMIRREMOLQUE" to data.marcaSemi,
            "MODELO SEMIRREMOLQUE" to data.modeloSemi,
            "TIPO DE EJES SEMI" to data.tipoEjesSemi,
            "RODADO SEMI" to data.rodadoSemi,
            "COTA ANTERIOR" to data.cotaAnterior,
            "COTA POSTERIOR" to data.cotaPosterior,
            "VOLUMEN" to data.volumenSemi
        )

        val filas = maxOf(izq.size, der.size)
        pager.asegurarEspacio(filas * ch)
        for (i in 0 until filas) {
            val y = pager.y - i * ch
            izq.getOrNull(i)?.let { (lbl, valor) ->
                PdfDrawUtils.labelCell(pager.canvas, H, lbl, ML, y - ch, lw, ch, AZUL_CELL, AZUL_MED, 6.3f)
                PdfDrawUtils.valueCell(pager.canvas, H, valor, ML + lw, y - ch, vw, ch, AZUL_MED, 7.5f, align = Paint.Align.LEFT)
            }
            der.getOrNull(i)?.let { (lbl, valor) ->
                PdfDrawUtils.labelCell(pager.canvas, H, lbl, x2, y - ch, lw, ch, AZUL_CELL, AZUL_MED, 6.3f)
                PdfDrawUtils.valueCell(pager.canvas, H, valor, x2 + lw, y - ch, AW / 2 - lw, ch, AZUL_MED, 7.5f, align = Paint.Align.LEFT)
            }
        }
        pager.y -= filas * ch + 4 * MM
    }

    private fun dibujarControlCisternas(pager: Pager, cisternas: List<CalibradoPdfBuilder.DatosCisterna>) {
        seccion(pager, "CONTROL CISTERNAS")
        val hh = 8 * MM; val rh = 6.5f * MM
        val cols = listOf(16f, 20f, 20f, 18f, 18f, 20f).map { it * MM }.toMutableList()
        cols[5] = AW - cols.take(5).sum()
        val hdrs = listOf("CISTERNA\nN°", "VOLUMEN\n(lts)", "ENRASE\n(mm)", "VACIO\n(mm)", "VACIO\n(lts)", "REBALSE\n(mm)")

        pager.asegurarEspacio(hh)
        var x = ML
        hdrs.forEachIndexed { idx, lbl -> PdfDrawUtils.labelCell(pager.canvas, H, lbl, x, pager.y - hh, cols[idx], hh, AZUL_CELL, AZUL_MED, 6.5f); x += cols[idx] }
        pager.y -= hh

        val filas = maxOf(7, cisternas.size)
        for (i in 0 until filas) {
            pager.asegurarEspacio(rh)
            val bg = if (i % 2 != 0) GRIS_CLARO else BLANCO
            val ci = cisternas.getOrNull(i)
            val na = "N/A"
            x = ML
            val vals = listOf(
                (i + 1).toString(), ci?.capacidad?.ifBlank { na } ?: na, ci?.enraseMm?.ifBlank { na } ?: na,
                ci?.vacioMm?.ifBlank { na } ?: na, ci?.vacioLts?.ifBlank { na } ?: na, ci?.rebalseMm?.ifBlank { na } ?: na
            )
            vals.forEachIndexed { j, valor ->
                PdfDrawUtils.valueCell(pager.canvas, H, valor, x, pager.y - rh, cols[j], rh, AZUL_MED, 7.5f, bold = (j == 0), align = Paint.Align.CENTER, background = bg)
                x += cols[j]
            }
            pager.y -= rh
        }
        pager.y -= 4 * MM
    }

    private fun dibujarControlPrecintos(pager: Pager, cisternas: List<CalibradoPdfBuilder.DatosCisterna>) {
        seccion(pager, "CONTROL PRECINTOS")
        val hh = 8 * MM; val rh = 6.5f * MM
        val cols = listOf(16f, 0f, 0f, 0f).map { it * MM }.toMutableList()
        val resto = (AW - 16 * MM) / 3f
        cols[1] = resto; cols[2] = resto; cols[3] = AW - cols[0] - cols[1] - cols[2]
        val hdrs = listOf("CISTERNA\nN°", "PRECINTO\nENRASE", "PRECINTO\nDE BOCA", "PRECINTO DE\nSOPORTE")

        pager.asegurarEspacio(hh)
        var x = ML
        hdrs.forEachIndexed { idx, lbl -> PdfDrawUtils.labelCell(pager.canvas, H, lbl, x, pager.y - hh, cols[idx], hh, AZUL_CELL, AZUL_MED, 6.5f); x += cols[idx] }
        pager.y -= hh

        val filas = maxOf(7, cisternas.size)
        for (i in 0 until filas) {
            pager.asegurarEspacio(rh)
            val bg = if (i % 2 != 0) GRIS_CLARO else BLANCO
            val ci = cisternas.getOrNull(i)
            val na = "N/A"
            x = ML
            val vals = listOf((i + 1).toString(), ci?.precintoEnrase?.ifBlank { na } ?: na, ci?.precintoBoca?.ifBlank { na } ?: na, ci?.precintoSoporte?.ifBlank { na } ?: na)
            vals.forEachIndexed { j, valor ->
                PdfDrawUtils.valueCell(pager.canvas, H, valor, x, pager.y - rh, cols[j], rh, AZUL_MED, 7.5f, bold = (j == 0), align = Paint.Align.CENTER, background = bg)
                x += cols[j]
            }
            pager.y -= rh
        }
        pager.y -= 4 * MM
    }

    private fun dibujarTrazabilidadSensores(pager: Pager, sensores: List<DatosSensorCisterna>) {
        seccion(pager, "DATOS INFORME DE CARGA SEGURA (SEGÚN CORRESPONDA) — TRAZABILIDAD SENSORES")
        val hh = 8 * MM; val rh = 6.5f * MM
        val cols = listOf(16f, 0f, 0f, 0f).map { it * MM }.toMutableList()
        val resto = (AW - 16 * MM)
        cols[1] = resto * 0.42f; cols[2] = resto * 0.38f; cols[3] = AW - cols[0] - cols[1] - cols[2]
        val hdrs = listOf("CISTERNA\nN°", "MARCA", "N° DE SERIE", "CUMPLE\n(SI/NO)")

        pager.asegurarEspacio(hh)
        var x = ML
        hdrs.forEachIndexed { idx, lbl -> PdfDrawUtils.labelCell(pager.canvas, H, lbl, x, pager.y - hh, cols[idx], hh, AZUL_CELL, AZUL_MED, 6.5f); x += cols[idx] }
        pager.y -= hh

        val filas = maxOf(7, sensores.size)
        for (i in 0 until filas) {
            pager.asegurarEspacio(rh)
            val bg = if (i % 2 != 0) GRIS_CLARO else BLANCO
            val s = sensores.getOrNull(i)
            val na = "N/A"
            x = ML
            val vals = listOf((i + 1).toString(), s?.sensorMarca?.ifBlank { na } ?: na, s?.sensorNumeroSerie?.ifBlank { na } ?: na, s?.sensorCumple?.ifBlank { na } ?: na)
            vals.forEachIndexed { j, valor ->
                PdfDrawUtils.valueCell(pager.canvas, H, valor, x, pager.y - rh, cols[j], rh, AZUL_MED, 7.5f, bold = (j == 0), align = if (j == 1) Paint.Align.LEFT else Paint.Align.CENTER, background = bg)
                x += cols[j]
            }
            pager.y -= rh
        }
        pager.y -= 4 * MM
    }

    private fun dibujarPosicionSensores(pager: Pager, sensores: List<DatosSensorCisterna>) {
        seccion(pager, "POSICIÓN SENSORES")
        val hh = 8 * MM; val rh = 6.5f * MM
        val cols = listOf(16f, 18f, 0f, 26f).map { it * MM }.toMutableList()
        cols[2] = AW - cols[0] - cols[1] - cols[3]
        val hdrs = listOf("CISTERNA\nN°", "CUMPLE\n(SI/NO)", "OBSERVACIÓN", "LITROS\nSENSOR/REBALSE")

        pager.asegurarEspacio(hh)
        var x = ML
        hdrs.forEachIndexed { idx, lbl -> PdfDrawUtils.labelCell(pager.canvas, H, lbl, x, pager.y - hh, cols[idx], hh, AZUL_CELL, AZUL_MED, 6.5f); x += cols[idx] }
        pager.y -= hh

        val filas = maxOf(7, sensores.size)
        for (i in 0 until filas) {
            pager.asegurarEspacio(rh)
            val bg = if (i % 2 != 0) GRIS_CLARO else BLANCO
            val s = sensores.getOrNull(i)
            val na = "N/A"
            x = ML
            val vals = listOf((i + 1).toString(), s?.posicionCumple?.ifBlank { na } ?: na, s?.posicionObservacion?.ifBlank { na } ?: na, s?.litrosSensorRebalse?.ifBlank { na } ?: na)
            vals.forEachIndexed { j, valor ->
                PdfDrawUtils.valueCell(pager.canvas, H, valor, x, pager.y - rh, cols[j], rh, AZUL_MED, 7.5f, bold = (j == 0), align = if (j == 2) Paint.Align.LEFT else Paint.Align.CENTER, background = bg)
                x += cols[j]
            }
            pager.y -= rh
        }
        pager.y -= 4 * MM
    }

    private fun dibujarFirma(pager: Pager, texto: String) {
        pager.asegurarEspacio(14 * MM)
        pager.y -= 4 * MM
        PdfDrawUtils.line(pager.canvas, H, ML, pager.y, ML + 70 * MM, pager.y, NEGRO, 0.8f)
        pager.y -= 4 * MM
        PdfDrawUtils.text(pager.canvas, H, texto, ML, pager.y, 8.5f, color = Color.DKGRAY)
        pager.y -= 8 * MM
    }

    /** Tabla "CONTROL DE DESCARGA PATRON": columnas = compartimientos 1..[cantidadCompartimientos],
     *  filas = puntos fijos de volumen. Cada celda puede tener más de una lectura -- se muestran
     *  todas separadas por "/" dentro de la misma celda. */
    private fun dibujarDescargaPatron(pager: Pager, cantidadCompartimientos: Int, descargaPatron: Map<Int, Map<Int, List<String>>>) {
        seccion(pager, "CONTROL DE DESCARGA PATRON")
        val hh = 8 * MM; val rh = 8 * MM
        val colEtiqueta = 26 * MM
        val colComp = (AW - colEtiqueta) / cantidadCompartimientos

        pager.asegurarEspacio(hh)
        var x = ML
        PdfDrawUtils.labelCell(pager.canvas, H, "PUNTO\n(lts)", x, pager.y - hh, colEtiqueta, hh, AZUL_CELL, AZUL_MED, 6.5f)
        x += colEtiqueta
        for (comp in 1..cantidadCompartimientos) {
            PdfDrawUtils.labelCell(pager.canvas, H, comp.toString(), x, pager.y - hh, colComp, hh, AZUL_CELL, AZUL_MED, 7f)
            x += colComp
        }
        pager.y -= hh

        // Fila CAPACIDAD -- no hay dato numérico propio en el modelo (la capacidad ya está en
        // CONTROL CISTERNAS), se deja en blanco para que el técnico la complete a mano si hace
        // falta reproducir el papel tal cual.
        pager.asegurarEspacio(rh)
        x = ML
        PdfDrawUtils.valueCell(pager.canvas, H, "CAPACIDAD", x, pager.y - rh, colEtiqueta, rh, AZUL_MED, 7f, bold = true, align = Paint.Align.LEFT, background = GRIS_CLARO)
        x += colEtiqueta
        for (comp in 1..cantidadCompartimientos) {
            PdfDrawUtils.valueCell(pager.canvas, H, "", x, pager.y - rh, colComp, rh, AZUL_MED, 7.5f, background = GRIS_CLARO)
            x += colComp
        }
        pager.y -= rh

        for (punto in PUNTOS_DESCARGA_PATRON) {
            pager.asegurarEspacio(rh)
            x = ML
            PdfDrawUtils.valueCell(pager.canvas, H, punto.toString(), x, pager.y - rh, colEtiqueta, rh, AZUL_MED, 8f, bold = true, align = Paint.Align.LEFT)
            x += colEtiqueta
            for (comp in 1..cantidadCompartimientos) {
                val lecturas = descargaPatron[comp]?.get(punto).orEmpty().filter { it.isNotBlank() }
                val texto = if (lecturas.isEmpty()) "" else lecturas.joinToString(" / ")
                PdfDrawUtils.valueCell(pager.canvas, H, texto, x, pager.y - rh, colComp, rh, AZUL_MED, 7f, align = Paint.Align.CENTER)
                x += colComp
            }
            pager.y -= rh
        }
        pager.y -= 4 * MM
    }
}
