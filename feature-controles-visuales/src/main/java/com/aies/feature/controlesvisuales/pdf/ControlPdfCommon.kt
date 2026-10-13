package com.aies.feature.controlesvisuales.pdf

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.aies.corepdf.MM
import com.aies.corepdf.PdfDrawUtils
import com.aies.feature.controlesvisuales.data.EncabezadoEmbed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Layout compartido por los 3 generadores de PDF de controles visuales: encabezado (idéntico
 * en las 3 planillas R 16-0x) + tabla de "Detalle de compartimentos" (también idéntica), más
 * un paginador simple para el contenido específico de cada control que puede desbordar a
 * varias páginas (el checklist de ~50+ ítems de la externa, sobre todo).
 */
object ControlPdfCommon {

    const val W = 595f
    const val H = 842f
    val ML = 13 * MM
    val MR = 13 * MM
    val AW = W - ML - MR
    val MARGEN_INFERIOR = 15 * MM

    val AZUL_OSC: Int = Color.parseColor("#1D3461")
    val AZUL_MED: Int = Color.parseColor("#2E5B9A")
    val AZUL_CELL: Int = Color.parseColor("#3B6CB7")
    val GRIS_CLARO: Int = Color.parseColor("#F5F5F5")
    val BLANCO = Color.WHITE
    val NEGRO = Color.BLACK

    private val FMT = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private fun f(millis: Long?): String = millis?.let { FMT.format(Date(it)) } ?: ""

    data class DetalleRow(val numero: Int, val capacidad: String, val producto: String, val estado: String)

    /** Estado mutable de paginación: qué página/canvas se está usando y en qué altura (yBottom,
     *  espacio PDF) va el cursor de dibujo. `nuevaPagina` cierra la página actual y abre otra. */
    class Pager(private val doc: PdfDocument) {
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

        /** Si no entra una fila de alto [alturaFila] antes del margen inferior, arranca página nueva. */
        fun asegurarEspacio(alturaFila: Float) {
            if (y - alturaFila < MARGEN_INFERIOR) nuevaPagina()
        }

        fun finalizar() = doc.finishPage(page)
    }

    fun titulo(pager: Pager, titulo: String) {
        PdfDrawUtils.text(pager.canvas, H, titulo, W / 2, pager.y, 14f, bold = true, color = AZUL_OSC, align = Paint.Align.CENTER)
        pager.y -= 5 * MM
        PdfDrawUtils.line(pager.canvas, H, ML, pager.y, W - MR, pager.y, AZUL_OSC, 1.2f)
        pager.y -= 5 * MM
    }

    /** Dibuja el bloque de encabezado común (17 campos, 2 columnas) y devuelve el pager con el
     *  cursor ya avanzado debajo del bloque. */
    fun dibujarEncabezado(pager: Pager, enc: EncabezadoEmbed) {
        val ch = 6.3f * MM
        val lw = 42 * MM
        val vw = AW / 2 - lw - 2 * MM
        val x2 = ML + AW / 2 + 2 * MM

        val izq = listOf(
            "FECHA" to f(enc.fecha),
            "RAZÓN SOCIAL" to enc.razonSocial,
            "DOMICILIO" to enc.domicilio,
            "TIPO DE ACTIVIDAD" to enc.tipoActividad,
            "CUIT" to enc.cuit,
            "TIPO (SEMI/ACOPLADO/CHASIS)" to enc.tipoUnidad,
            "DOMINIO" to enc.dominio,
            "N° DE RUTA" to enc.numeroRuta,
            "TIPO DE CARGA" to enc.tipoCarga
        )
        val der = listOf(
            "MARCA" to enc.marca,
            "MODELO" to enc.modelo,
            "FABRICANTE" to enc.fabricante,
            "FECHA FABRIC. CISTERNA" to f(enc.fechaFabricacionCisterna),
            "ANTIGÜEDAD" to enc.antiguedad,
            "VTO. VISUAL INTERNA" to f(enc.fechaVencimientoVisualInterna),
            "VTO. VISUAL EXTERNA" to f(enc.fechaVencimientoVisualExterna),
            "VTO. ESTANQ./ESPESORES" to f(enc.fechaVencimientoEstanqueidadEspesores)
        )

        val filas = maxOf(izq.size, der.size)
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

    /** Envuelve [texto] a un ancho en puntos PDF, estimando ~0.52*fontSize por carácter (misma
     *  idea que [PdfDrawUtils.wrapText], que trabaja por cantidad de caracteres). */
    fun wrapAAncho(texto: String, anchoPt: Float, fontSize: Float): List<String> {
        if (texto.isEmpty()) return emptyList()
        val limiteCaracteres = (anchoPt / (fontSize * 0.52f)).toInt().coerceAtLeast(4)
        return PdfDrawUtils.wrapText(texto, limiteCaracteres)
    }

    /** Celda con borde/fondo que dibuja [lineas] ya envueltas, alineadas a la izquierda y
     *  centradas verticalmente en la celda — para texto largo (descripciones, observaciones)
     *  que no entra en una sola línea de [PdfDrawUtils.valueCell]. */
    fun celdaMultilinea(
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

    /** Tabla "Detalle de compartimentos" (N°, capacidad, producto, estado), idéntica en las 3 planillas. */
    fun dibujarTablaCompartimentos(pager: Pager, detalles: List<DetalleRow>) {
        PdfDrawUtils.text(pager.canvas, H, "DETALLE DE COMPARTIMENTOS", ML, pager.y, 10.5f, bold = true, color = AZUL_OSC)
        pager.y -= 5 * MM

        val hh = 7 * MM
        val rh = 6.5f * MM
        val cols = listOf(18f, 40f, 90f, 32f).map { it * MM }.toMutableList()
        cols[2] = AW - cols[0] - cols[1] - cols[3]
        val hdrs = listOf("N°", "CAPACIDAD", "PRODUCTO", "ESTADO")

        pager.asegurarEspacio(hh)
        var x = ML
        hdrs.forEachIndexed { idx, lbl ->
            PdfDrawUtils.labelCell(pager.canvas, H, lbl, x, pager.y - hh, cols[idx], hh, AZUL_CELL, AZUL_MED, 7f)
            x += cols[idx]
        }
        pager.y -= hh

        detalles.forEachIndexed { i, d ->
            pager.asegurarEspacio(rh)
            val bg = if (i % 2 != 0) GRIS_CLARO else BLANCO
            x = ML
            val vals = listOf(d.numero.toString(), d.capacidad, d.producto, d.estado)
            vals.forEachIndexed { j, valor ->
                PdfDrawUtils.valueCell(
                    pager.canvas, H, valor, x, pager.y - rh, cols[j], rh, AZUL_MED, 7.5f,
                    align = if (j == 2) Paint.Align.LEFT else Paint.Align.CENTER, background = bg
                )
                x += cols[j]
            }
            pager.y -= rh
        }
        pager.y -= 4 * MM
    }
}
