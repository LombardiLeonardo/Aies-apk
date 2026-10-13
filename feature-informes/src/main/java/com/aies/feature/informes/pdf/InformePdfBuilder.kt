package com.aies.feature.informes.pdf

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.aies.corepdf.MM
import com.aies.corepdf.PdfDrawUtils
import com.aies.feature.informes.data.CisternaPosicion
import com.aies.feature.informes.data.CisternaTrazabilidad
import com.aies.feature.informes.data.CisternaVacio

/**
 * Port directo de generar_informe.py (ReportLab). Las coordenadas se dejan en "espacio
 * PDF" (origen abajo-izquierda, Y creciente hacia arriba) igual que en el original, y se
 * convierten a espacio de Canvas de Android recién al dibujar, vía [PdfDrawUtils] — así el
 * cálculo geométrico línea por línea se pudo portar casi sin tocar, en vez de tener que
 * re-derivar a mano cada coordenada en un sistema top-down.
 */
object InformePdfBuilder {

    private val AZUL_OSC = Color.parseColor("#1D3461")
    private val AZUL_MED = Color.parseColor("#2E5B9A")
    private val AZUL_CELL = Color.parseColor("#3B6CB7")
    private val BLANCO = Color.WHITE
    private val NEGRO = Color.BLACK
    private val GRIS = Color.parseColor("#333333")
    private val GRIS_CLARO = Color.parseColor("#F5F5F5")

    private const val W = 595f
    private const val H = 842f
    private val ML = 13 * MM
    private val MR = 13 * MM
    private val AW = W - ML - MR

    data class DatosInforme(
        val nroInforme: String,
        val transportista: String,
        val cuit: String,
        val patenteTractor: String,
        val patenteSemi: String,
        val calibradoNro: String,
        val fechaEmision: String,
        val fechaVencimiento: String,
        val cantCisternas: Int,
        val resultadoFinal: String,
        val cvCumple: String,
        val tdCumple: String,
        val psCumple: String,
        val vacioData: List<CisternaVacio>,
        val trazData: List<CisternaTrazabilidad>,
        val posData: List<CisternaPosicion>
    )

    fun generar(assets: AssetManager, data: DatosInforme): PdfDocument {
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(W.toInt(), H.toInt(), 1).create())
        val c = page.canvas
        val n = data.cantCisternas

        // ── LOGO ──────────────────────────────────────────────────────────────────
        val logoW = 36 * MM; val logoH = 17 * MM
        dibujarImagen(c, assets, "logo_aies_clean.png", ML, H - logoH - 8 * MM, logoW, logoH)

        // ── TÍTULO ─────────────────────────────────────────────────────────────────
        PdfDrawUtils.text(c, H, "INFORME DE CARGA SEGURA N°  ${data.nroInforme}", W / 2 + 18 * MM, H - 16 * MM, 18f, bold = true, color = AZUL_OSC, align = Paint.Align.CENTER)

        val yHl = H - 28 * MM
        PdfDrawUtils.line(c, H, ML, yHl, W - MR, yHl, AZUL_OSC, 2f)
        PdfDrawUtils.line(c, H, ML, yHl - 1.5f * MM, W - MR, yHl - 1.5f * MM, AZUL_MED, 0.5f)

        // ── DATOS GENERALES ────────────────────────────────────────────────────────
        val ch = 6.8f * MM
        val y0 = yHl - 4 * MM
        val lw1 = 44 * MM; val vw1 = 52 * MM
        val x2 = ML + lw1 + vw1 + 4 * MM
        val lw2 = 38 * MM
        val vw2 = AW - lw1 - vw1 - 4 * MM - lw2

        val filasIzq = listOf(
            "TRANSPORTISTA" to data.transportista,
            "CUIT" to data.cuit,
            "PATENTE TRACTOR" to data.patenteTractor,
            "PATENTE SEMIRREMOLQUE" to data.patenteSemi
        )
        val filasDer = listOf(
            "CALIBRADO N°" to data.calibradoNro,
            "FECHA DE EMISION" to data.fechaEmision,
            "FECHA VECIMIENTO" to data.fechaVencimiento
        )

        filasIzq.forEachIndexed { i, (lbl, valor) ->
            val y = y0 - i * ch
            PdfDrawUtils.labelCell(c, H, lbl, ML, y - ch, lw1, ch, AZUL_CELL, AZUL_MED, 7f)
            PdfDrawUtils.valueCell(c, H, valor, ML + lw1, y - ch, vw1, ch, AZUL_MED, 8f, align = Paint.Align.LEFT)
        }
        filasDer.forEachIndexed { i, (lbl, valor) ->
            val y = y0 - i * ch
            PdfDrawUtils.labelCell(c, H, lbl, x2, y - ch, lw2, ch, AZUL_CELL, AZUL_MED, 7f)
            PdfDrawUtils.valueCell(c, H, valor, x2 + lw2, y - ch, vw2, ch, AZUL_MED, 8f, align = Paint.Align.LEFT)
        }

        // ── SECCIÓN 1: CONTROL VACÍO EN CISTERNAS ─────────────────────────────────
        val yCv = y0 - 4 * ch - 18 * MM
        PdfDrawUtils.text(c, H, "CONTROL VACÍO EN CISTERNAS", ML, yCv + 4 * MM, 13f, bold = true, color = AZUL_OSC)
        PdfDrawUtils.line(c, H, ML, yCv + 0.8f * MM, W - MR, yCv + 0.8f * MM, AZUL_OSC, 1f)

        val rh = 7 * MM; val hh = 8 * MM
        val c1 = mutableListOf(16f, 23f, 23f, 23f, 37f, 27f).map { it * MM }.toMutableList()
        c1.add(AW - c1.sum())
        val hdrs1 = listOf("CISTERNA N°", "VOLUMEN\n(lts)", "VACÍO\nREQUERIDO", "VACÍO\nMEDIDO", "ACCION\nTOMADA", "VOLUMEN FINAL\n(lts)", "CUMPLE\n(SI/NO)")

        val yH1 = yCv - hh
        run {
            var x = ML
            hdrs1.forEachIndexed { idx, lbl ->
                PdfDrawUtils.labelCell(c, H, lbl, x, yH1, c1[idx], hh, AZUL_CELL, AZUL_MED, 6.5f)
                x += c1[idx]
            }
        }

        for (i in 0 until n) {
            val bg = if (i % 2 != 0) GRIS_CLARO else BLANCO
            val cv = data.vacioData.getOrNull(i)
            val yR = yH1 - (i + 1) * rh
            var x = ML

            val accionVal = cv?.accion ?: ""
            val accionDisplay = accionVal.toIntOrNull()?.let { litros ->
                when {
                    litros > 0 -> "Se disminuyeron $litros lts"
                    litros == 0 -> "Ninguna"
                    else -> accionVal
                }
            } ?: accionVal

            val vals = listOf((i + 1).toString(), cv?.volumen ?: "", cv?.vacioRequerido ?: "", cv?.vacioMedido ?: "", accionDisplay, cv?.volumenFinal ?: "", cv?.cumple ?: "")
            vals.forEachIndexed { j, valor ->
                PdfDrawUtils.valueCell(
                    c, H, valor, x, yR, c1[j], rh, AZUL_MED, 8f, bold = (j == 0),
                    align = if (j == 4) Paint.Align.LEFT else Paint.Align.CENTER, padPt = 1.5f * MM, background = bg
                )
                x += c1[j]
            }
        }

        // ── SECCIÓN 2+3: TRAZABILIDAD | POSICIÓN ───────────────────────────────────
        val yTp = yH1 - n * rh - 12 * MM
        val half = AW / 2 - 2 * MM
        val xPosSec = ML + half + 4 * MM
        val half2 = AW - half - 4 * MM

        PdfDrawUtils.text(c, H, "TRAZABILIDAD SENSORES", ML, yTp + 4 * MM, 12f, bold = true, color = AZUL_OSC)
        PdfDrawUtils.text(c, H, "POSICIÓN SENSORES", xPosSec, yTp + 4 * MM, 12f, bold = true, color = AZUL_OSC)
        PdfDrawUtils.line(c, H, ML, yTp + 0.8f * MM, ML + half, yTp + 0.8f * MM, AZUL_OSC, 1f)
        PdfDrawUtils.line(c, H, xPosSec, yTp + 0.8f * MM, W - MR, yTp + 0.8f * MM, AZUL_OSC, 1f)

        val ct = mutableListOf(15f, 28f, 26f).map { it * MM }.toMutableList()
        ct.add(half - ct.sum())
        val hdrsT = listOf("CISTERNA N°", "MARCA", "N° DE SERIE", "CUMPLE\n(SI/NO)")

        val yTh = yTp - hh
        run {
            var x = ML
            hdrsT.forEachIndexed { idx, lbl ->
                PdfDrawUtils.labelCell(c, H, lbl, x, yTh, ct[idx], hh, AZUL_CELL, AZUL_MED, 6.5f)
                x += ct[idx]
            }
        }
        for (i in 0 until n) {
            val bg = if (i % 2 != 0) GRIS_CLARO else BLANCO
            val cv = data.trazData.getOrNull(i)
            val yR = yTh - (i + 1) * rh
            var x = ML
            val vals = listOf((i + 1).toString(), cv?.marca ?: "", cv?.nroSerie ?: "", cv?.cumple ?: "")
            vals.forEachIndexed { j, valor ->
                PdfDrawUtils.valueCell(c, H, valor, x, yR, ct[j], rh, AZUL_MED, 7.5f, bold = (j == 0), align = Paint.Align.CENTER, padPt = 1.5f * MM, background = bg)
                x += ct[j]
            }
        }

        val cp = mutableListOf(17f * MM)
        cp.add(half2 - 17 * MM - 30 * MM)
        cp.add(30f * MM)
        val hdrsP = listOf("CUMPLE\n(SI/NO)", "OBSERVACIÓN", "LITROS\nSENSOR/REBALSE")
        run {
            var x = xPosSec
            hdrsP.forEachIndexed { idx, lbl ->
                PdfDrawUtils.labelCell(c, H, lbl, x, yTh, cp[idx], hh, AZUL_CELL, AZUL_MED, 6.5f)
                x += cp[idx]
            }
        }
        for (i in 0 until n) {
            val bg = if (i % 2 != 0) GRIS_CLARO else BLANCO
            val cv = data.posData.getOrNull(i)
            val vd = data.vacioData.getOrNull(i)
            val yR = yTh - (i + 1) * rh
            var x = xPosSec

            val vacioMed = vd?.vacioMedido?.toDoubleOrNull() ?: 0.0
            val sensorLts = cv?.litros?.toDoubleOrNull() ?: 0.0
            val rebalseStr = if (vacioMed > 0 && sensorLts > 0) (vacioMed - sensorLts).toInt().toString() else ""

            val vals = listOf(cv?.cumple ?: "", cv?.observacion ?: "", rebalseStr)
            vals.forEachIndexed { j, valor ->
                PdfDrawUtils.valueCell(c, H, valor, x, yR, cp[j], rh, AZUL_MED, 7.5f, align = Paint.Align.CENTER, padPt = 1.5f * MM, background = bg)
                x += cp[j]
            }
        }

        // ── NOTA ───────────────────────────────────────────────────────────────────
        val yNota = yTh - n * rh - 5 * MM
        PdfDrawUtils.text(c, H, "* No existe la posibilidad de posicionar el sensor de sobrellenado por debajo del valor mencionado", ML, yNota, 7.5f, bold = true, color = NEGRO)
        PdfDrawUtils.line(c, H, ML, yNota - 3 * MM, W - MR, yNota - 3 * MM, AZUL_MED, 0.5f)

        // ── RESULTADO FINAL ────────────────────────────────────────────────────────
        val yResTop = yNota - 10 * MM
        val resH = 9 * MM; val resLblW = 90 * MM; val resValW = 32 * MM

        PdfDrawUtils.filledRect(c, H, ML, yResTop - resH, resLblW, resH, AZUL_CELL, AZUL_MED, 0.5f, cornerRadius = 3f * MM)
        PdfDrawUtils.text(c, H, "RESULTADO FINAL DE AUDITORIA – CUMPLE SI/NO", ML + resLblW / 2, yResTop - resH / 2 - 3.5f, 8.5f, bold = true, color = BLANCO, align = Paint.Align.CENTER)
        PdfDrawUtils.roundedCell(c, H, data.resultadoFinal, ML + resLblW + 3 * MM, yResTop - resH, resValW, resH, AZUL_MED, 10f)

        val respX = ML + resLblW + resValW + 8 * MM
        PdfDrawUtils.text(c, H, "RESPONSABLE TÉCNICO", respX + 30 * MM, yResTop - 4 * MM, 8f, color = AZUL_MED, align = Paint.Align.CENTER)

        val subH = 7 * MM; val subLblW = 68 * MM; val subValW = 30 * MM
        val subLabels = listOf("CONTROL VACÍO CISTERNAS", "TRAZABILIDAD DE SENSORES", "POSICIÓN DE SENSORES")
        val subVals = listOf(data.cvCumple, data.tdCumple, data.psCumple)
        subLabels.forEachIndexed { i, lbl ->
            val ys = yResTop - resH - 2 * MM - (i + 1) * (subH + 1.5f * MM) + subH
            PdfDrawUtils.filledRect(c, H, ML, ys - subH, subLblW, subH, AZUL_CELL, null, cornerRadius = 3f * MM)
            PdfDrawUtils.text(c, H, lbl, ML + subLblW / 2, ys - subH / 2 - 3.5f, 7.5f, color = BLANCO, align = Paint.Align.CENTER)
            PdfDrawUtils.roundedCell(c, H, subVals[i], ML + subLblW + 3 * MM, ys - subH, subValW, subH, AZUL_MED, 9f)
        }

        // Firma — al 60% del tamaño original (96x36mm), que ocupaba casi media hoja. Centrada
        // horizontalmente debajo de la etiqueta "RESPONSABLE TÉCNICO" (esta se centra en
        // respX + 30*MM), no pegada al margen izquierdo del bloque como antes.
        val fw = 96 * MM * 0.6f; val fh = 36 * MM * 0.6f
        val fx = respX + 30 * MM - fw / 2f
        val fy = yResTop - 4 * MM - 8 * MM - fh
        dibujarImagen(c, assets, "firma_sola.png", fx, fy, fw, fh)

        // ── PIE DE PÁGINA ──────────────────────────────────────────────────────────
        val yf = 15 * MM
        PdfDrawUtils.line(c, H, ML, yf + 7 * MM, W - MR, yf + 7 * MM, AZUL_OSC, 2.5f)
        PdfDrawUtils.line(c, H, ML, yf + 5.5f * MM, W - MR, yf + 5.5f * MM, AZUL_MED, 0.4f)
        dibujarImagen(c, assets, "logo_aies_clean.png", ML, yf - 4 * MM, 20 * MM, 12 * MM)
        PdfDrawUtils.text(c, H, "José J. Podestá 64, Avellaneda, Provincia de Buenos Aires.", ML + 24 * MM, yf + 2.5f * MM, 8f, color = GRIS)
        PdfDrawUtils.text(c, H, "www.aies.com.ar", ML + 24 * MM, yf - 1.5f * MM, 8f, color = GRIS)

        doc.finishPage(page)
        return doc
    }

    /** [x],[yBottom],[width],[height] en espacio PDF (igual convención que canvas.drawImage de ReportLab). */
    private fun dibujarImagen(canvas: Canvas, assets: AssetManager, nombreAsset: String, x: Float, yBottom: Float, width: Float, height: Float) {
        try {
            val listaAssets = assets.list("") ?: emptyArray()
            if (!listaAssets.contains(nombreAsset)) return
            val bitmap: Bitmap = assets.open(nombreAsset).use { BitmapFactory.decodeStream(it) } ?: return
            val top = H - (yBottom + height)
            val bottom = H - yBottom
            canvas.drawBitmap(bitmap, null, RectF(x, top, x + width, bottom), Paint(Paint.ANTI_ALIAS_FLAG))
        } catch (_: Exception) {
            // Igual que el original: si falta el logo/firma, se deja el espacio en blanco.
        }
    }
}
