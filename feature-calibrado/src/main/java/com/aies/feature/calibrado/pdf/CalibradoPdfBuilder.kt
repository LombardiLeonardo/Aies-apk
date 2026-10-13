package com.aies.feature.calibrado.pdf

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

/**
 * Réplica de Tarjeta_Calibrado(AF117PL).pdf (referencia real de AIES). Mismo esquema de
 * colores/celdas que InformePdfBuilder/CertificadoPdfBuilder (labelCell azul oscuro +
 * valueCell blanco), coordenadas en "espacio PDF" vía PdfDrawUtils.
 */
object CalibradoPdfBuilder {

    private val AZUL_OSC = Color.parseColor("#1D3461")
    private val AZUL_MED = Color.parseColor("#2E5B9A")
    private val AZUL_CELL = Color.parseColor("#3B6CB7")
    private val BLANCO = Color.WHITE
    private val NEGRO = Color.BLACK
    private val GRIS = Color.parseColor("#333333")
    private val GRIS_CLARO = Color.parseColor("#F5F5F5")
    private val ROJO_NOTA = Color.parseColor("#B23B3B")

    private const val W = 595f
    private const val H = 842f
    private val ML = 13 * MM
    private val MR = 13 * MM
    private val AW = W - ML - MR

    /** Siempre se muestran 7 filas en las tablas de cisternas (la referencia usa 6 reales + 1 fila N/A);
     *  si hay más cisternas reales que 7 se extiende, nunca se truncan datos. */
    private const val FILAS_MINIMAS_CISTERNAS = 7

    data class DatosCisterna(
        val cisternaNumero: Int,
        val capacidad: String,
        val enraseMm: String,
        val vacioMm: String,
        val vacioLts: String,
        val rebalseMm: String,
        val precintoEnrase: String,
        val precintoBoca: String,
        val precintoSoporte: String
    )

    data class DatosCalibracion(
        val numero: String,
        val fechaEmision: String,
        val fechaVencimiento: String,
        val transportista: String,
        val cuit: String,
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
        val cisternas: List<DatosCisterna>
    )

    fun generar(assets: AssetManager, data: DatosCalibracion): PdfDocument {
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(W.toInt(), H.toInt(), 1).create())
        val c = page.canvas

        // ── LOGO ──────────────────────────────────────────────────────────────────
        val logoW = 30 * MM; val logoH = 17 * MM
        dibujarImagen(c, assets, "logo_aies_clean.png", ML, H - logoH - 8 * MM, logoW, logoH)

        // ── TÍTULO + FECHAS ────────────────────────────────────────────────────────
        PdfDrawUtils.text(c, H, "CERTIFICADO DE CALIBRACIÓN N° ${data.numero}", ML + 34 * MM, H - 11 * MM, 15f, bold = true, color = AZUL_OSC)
        PdfDrawUtils.text(c, H, "FECHA DE EMISION  ${data.fechaEmision}", W - MR, H - 16 * MM, 9f, bold = true, color = AZUL_MED, align = Paint.Align.RIGHT)
        PdfDrawUtils.text(c, H, "FECHA DE VTO  ${data.fechaVencimiento}", W - MR, H - 20.5f * MM, 9f, bold = true, color = AZUL_MED, align = Paint.Align.RIGHT)

        val yHl = H - 27 * MM
        PdfDrawUtils.line(c, H, ML, yHl, W - MR, yHl, AZUL_OSC, 2f)
        PdfDrawUtils.line(c, H, ML, yHl - 1.5f * MM, W - MR, yHl - 1.5f * MM, AZUL_MED, 0.5f)

        // ── DATOS GENERALES: dos columnas label/valor, 8 filas ────────────────────
        val ch = 7f * MM
        val y0 = yHl - 4 * MM
        val lw1 = 44 * MM; val vw1 = 46 * MM
        val x2 = ML + lw1 + vw1 + 4 * MM
        val lw2 = 40 * MM
        val vw2 = AW - lw1 - vw1 - 4 * MM - lw2

        val filasIzq = listOf(
            "TRANSPORTISTA" to data.transportista,
            "CUIT" to data.cuit,
            "PATENTE TRACTOR" to data.patenteTractor,
            "MARCA" to data.marcaTractor,
            "TIPO DE EJES" to data.tipoEjesTractor,
            "RODADO" to data.rodadoTractor,
            "TARA" to data.tara,
            "MAXIMO PESO PERMITIDO" to data.maximoPesoPermitido
        )
        val filasDer = listOf(
            "PATENTE SEMIRREMOLQUE" to data.patenteSemi,
            "MARCA" to data.marcaSemi,
            "MODELO" to data.modeloSemi,
            "TIPO DE EJES" to data.tipoEjesSemi,
            "RODADO" to data.rodadoSemi,
            "COTA ANTERIOR" to data.cotaAnterior,
            "COTA POSTERIOR" to data.cotaPosterior,
            "VOLUMEN" to data.volumenSemi
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

        // ── TABLA DE CISTERNAS: dos bloques lado a lado ───────────────────────────
        val filas = maxOf(FILAS_MINIMAS_CISTERNAS, data.cisternas.size)
        val yCi = y0 - 8 * ch - 10 * MM
        PdfDrawUtils.text(c, H, "DETALLE DE CISTERNAS", ML, yCi + 4 * MM, 12f, bold = true, color = AZUL_OSC)
        PdfDrawUtils.line(c, H, ML, yCi + 0.8f * MM, W - MR, yCi + 0.8f * MM, AZUL_OSC, 1f)

        val hh = 9 * MM; val rh = 7 * MM
        val half = AW * 0.56f
        val xDer = ML + half + 4 * MM
        val half2 = AW - half - 4 * MM

        val colsIzq = mutableListOf(18f, 24f, 24f, 22f, 22f).map { it * MM }.toMutableList()
        colsIzq.add(half - colsIzq.sum())
        val hdrsIzq = listOf("CISTERNA\nN°", "CAPACIDAD", "ENRASE\n(mm)", "VACIO\n(mm)", "VACIO\n(lts)", "REBALSE\n(mm)")

        val colsDer = mutableListOf(18f * MM)
        val restoDer = half2 - 18 * MM
        colsDer.add(restoDer / 3f)
        colsDer.add(restoDer / 3f)
        colsDer.add(half2 - colsDer.sum())
        val hdrsDer = listOf("CISTERNA\nN°", "PRECINTO\nENRASE", "PRECINTO\nDE BOCA", "PRECINTO DE\nSOPORTE")

        val yHdr = yCi - hh
        run {
            var x = ML
            hdrsIzq.forEachIndexed { idx, lbl -> PdfDrawUtils.labelCell(c, H, lbl, x, yHdr, colsIzq[idx], hh, AZUL_CELL, AZUL_MED, 6.5f); x += colsIzq[idx] }
        }
        run {
            var x = xDer
            hdrsDer.forEachIndexed { idx, lbl -> PdfDrawUtils.labelCell(c, H, lbl, x, yHdr, colsDer[idx], hh, AZUL_CELL, AZUL_MED, 6.5f); x += colsDer[idx] }
        }

        for (i in 0 until filas) {
            val bg = if (i % 2 != 0) GRIS_CLARO else BLANCO
            val ci = data.cisternas.getOrNull(i)
            val yR = yHdr - (i + 1) * rh
            val na = "N/A"

            var x = ML
            val valsIzq = listOf(
                (i + 1).toString(),
                ci?.capacidad?.ifBlank { na } ?: na,
                ci?.enraseMm?.ifBlank { na } ?: na,
                ci?.vacioMm?.ifBlank { na } ?: na,
                ci?.vacioLts?.ifBlank { na } ?: na,
                ci?.rebalseMm?.ifBlank { na } ?: na
            )
            valsIzq.forEachIndexed { j, valor ->
                PdfDrawUtils.valueCell(c, H, valor, x, yR, colsIzq[j], rh, AZUL_MED, 8f, bold = (j == 0), align = Paint.Align.CENTER, padPt = 1.5f * MM, background = bg)
                x += colsIzq[j]
            }

            x = xDer
            val valsDer = listOf(
                (i + 1).toString(),
                ci?.precintoEnrase?.ifBlank { na } ?: na,
                ci?.precintoBoca?.ifBlank { na } ?: na,
                ci?.precintoSoporte?.ifBlank { na } ?: na
            )
            valsDer.forEachIndexed { j, valor ->
                PdfDrawUtils.valueCell(c, H, valor, x, yR, colsDer[j], rh, AZUL_MED, 8f, bold = (j == 0), align = Paint.Align.CENTER, padPt = 1.5f * MM, background = bg)
                x += colsDer[j]
            }
        }

        // ── NOTA ───────────────────────────────────────────────────────────────────
        val yNota = yHdr - filas * rh - 6 * MM
        PdfDrawUtils.text(c, H, "Calibrado con válvulas abiertas.", ML, yNota, 9f, bold = true, color = ROJO_NOTA)

        // ── PIE DE PÁGINA ──────────────────────────────────────────────────────────
        val yPie1 = yNota - 8 * MM
        val piePartes = listOf(
            "VARILLA TROFEO T2500 Nº 1715 / PRECINTOS SEGÚN IRAM 3853",
            "MEDIDAS PATRON VERIFICADAS POR INTI / APROBACION DE MODELO SEGÚN",
            "RES. EX M.C. e I.M. Nº197/1981 / SISTEMA DE GESTION DE CALIDAD SEGÚN ISO 9001:2015"
        )
        piePartes.forEachIndexed { i, linea ->
            PdfDrawUtils.text(c, H, linea, ML, yPie1 - i * 3.6f * MM, 6.5f, color = GRIS)
        }

        // Firma
        val fw = 60 * MM
        val fx = W - MR - fw
        val fy = yPie1 - 2 * MM
        PdfDrawUtils.text(c, H, "Lic. Pablo González", fx + fw / 2, fy, 8.5f, bold = true, color = NEGRO, align = Paint.Align.CENTER)
        PdfDrawUtils.text(c, H, "Director Técnico", fx + fw / 2, fy - 3.6f * MM, 7.5f, color = GRIS, align = Paint.Align.CENTER)

        val yf = 14 * MM
        PdfDrawUtils.line(c, H, ML, yf + 7 * MM, W - MR, yf + 7 * MM, AZUL_OSC, 2.5f)
        PdfDrawUtils.line(c, H, ML, yf + 5.5f * MM, W - MR, yf + 5.5f * MM, AZUL_MED, 0.4f)
        dibujarImagen(c, assets, "logo_aies_clean.png", ML, yf - 4 * MM, 20 * MM, 12 * MM)
        PdfDrawUtils.text(c, H, "José J. Podestá 64, Avellaneda, Provincia de Buenos Aires.", ML + 24 * MM, yf + 2.5f * MM, 8f, color = GRIS)
        PdfDrawUtils.text(c, H, "www.aies.com.ar", ML + 24 * MM, yf - 1.5f * MM, 8f, color = GRIS)
        // Nota: el sello de calidad IRAM de la referencia (marca de un tercero) se omite a propósito, no vale la pena reproducirlo como imagen genérica.

        doc.finishPage(page)
        return doc
    }

    private fun dibujarImagen(canvas: Canvas, assets: AssetManager, nombreAsset: String, x: Float, yBottom: Float, width: Float, height: Float) {
        try {
            val listaAssets = assets.list("") ?: emptyArray()
            if (!listaAssets.contains(nombreAsset)) return
            val bitmap: Bitmap = assets.open(nombreAsset).use { BitmapFactory.decodeStream(it) } ?: return
            val top = H - (yBottom + height)
            val bottom = H - yBottom
            canvas.drawBitmap(bitmap, null, RectF(x, top, x + width, bottom), Paint(Paint.ANTI_ALIAS_FLAG))
        } catch (_: Exception) {
        }
    }
}
