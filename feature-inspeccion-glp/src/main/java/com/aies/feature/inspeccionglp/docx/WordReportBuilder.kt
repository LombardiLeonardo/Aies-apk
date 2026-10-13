package com.aies.feature.inspeccionglp.docx

import android.content.res.AssetManager
import com.aies.coredocx.DocxDrawUtils
import com.aies.feature.inspeccionglp.data.Cliente
import com.aies.feature.inspeccionglp.data.EstadoItemResultado
import com.aies.feature.inspeccionglp.data.Foto
import com.aies.feature.inspeccionglp.data.Informe
import com.aies.feature.inspeccionglp.data.ResultadoCompleto
import com.aies.feature.inspeccionglp.data.TipoIncumplimiento
import com.aies.feature.inspeccionglp.data.Vehiculo
import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.UnderlinePatterns
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import java.io.File
import java.io.FileOutputStream
import java.math.BigInteger
import java.util.Calendar

/**
 * Genera el acta de observaciones GLP (`WordReportService.cs` original) sobre core-docx.
 * A diferencia de feature-inspeccion-ejes (todo tablas), acá el cuerpo es un port directo de
 * párrafos con negrita/subrayado/alineación, calcado 1:1 del original: título centrado,
 * fecha en formato largo en español alineada a la derecha, destinatario en negrita, referencia
 * de dominio subrayada, secciones Observaciones (solo si hay algo) / Recomendaciones (siempre
 * no vacía por las 2 recomendaciones fijas), fotos inline de 8x6cm centradas después de cada
 * entrada, y el mismo membrete AIES (logo flotante en el header, blob + contacto en el footer).
 */
object WordReportBuilder {

    private const val CIUDAD = "Avellaneda"
    private const val DIRECCION_EMPRESA = "José J. Podestá 64, Avellaneda, Pcia. de Buenos Aires."
    private const val CONTACTO_EMPRESA = "+54 9 11 3124 0535 | +54 11 4201 0451 | info@aies.com.ar | www.aies.com.ar"

    private val RECOMENDACIONES_FIJAS = listOf(
        "Reponer chapa de características de las válvulas esféricas de bloqueo",
        "Reponer chapa de características de las válvulas esféricas de purga (venteos)"
    )

    private val MESES = listOf(
        "enero", "febrero", "marzo", "abril", "mayo", "junio",
        "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"
    )

    private data class Entrada(val categoria: String?, val texto: String?, val fotos: List<Foto>)

    fun generarInformeWord(
        assets: AssetManager,
        informe: Informe,
        cliente: Cliente,
        vehiculo: Vehiculo,
        resultados: List<ResultadoCompleto>,
        fotosPorResultadoId: Map<String, List<Foto>>,
        archivoSalida: File
    ) {
        val document = XWPFDocument()

        agregarParrafo(document, "ACTA DE OBSERVACIONES N° GLP ${informe.numeroInforme}", negrita = true, tamanioPuntos = 14, centrado = true)
        agregarParrafo(document, "$CIUDAD, ${formatearFecha(informe.fecha)}", derecha = true)
        document.createParagraph()

        agregarParrafo(document, "Sres. ${cliente.razonSocial}", negrita = true)
        agregarParrafo(document, "Ref. Dominio ${vehiculo.patenteTractor} - ${vehiculo.patenteSemirremolque}", subrayado = true)
        document.createParagraph()

        agregarParrafo(document, "De la verificación practicada surgieron las siguientes:")
        document.createParagraph()

        val malos = resultados.filter { it.resultado.estado == EstadoItemResultado.Mal }
        val observaciones = malos.filter { it.item.tipoIncumplimiento == TipoIncumplimiento.Observacion }
        val recomendaciones = malos.filter { it.item.tipoIncumplimiento == TipoIncumplimiento.Recomendacion }

        fun fotosDe(resultadoId: String): List<Foto> =
            (fotosPorResultadoId[resultadoId] ?: emptyList()).filter { File(it.rutaArchivo).exists() }

        val entradasObservaciones = observaciones.map {
            Entrada(aTitulo(it.item.subcategoria ?: it.item.categoria), it.resultado.comentario, fotosDe(it.resultado.id))
        }

        val entradasRecomendaciones = recomendaciones.map {
            Entrada(aTitulo(it.item.subcategoria ?: it.item.categoria), it.resultado.comentario, fotosDe(it.resultado.id))
        }.toMutableList()

        // Siempre presentes, además de las que surjan del checklist real de esta inspección.
        RECOMENDACIONES_FIJAS.forEach { texto -> entradasRecomendaciones.add(Entrada(categoria = null, texto = texto, fotos = emptyList())) }

        fun agregarSeccion(tituloSeccion: String, entradas: List<Entrada>) {
            if (entradas.isEmpty()) return
            agregarParrafo(document, "$tituloSeccion:", negrita = true)
            entradas.forEachIndexed { indice, entrada ->
                val n = indice + 1
                if (entrada.categoria != null) {
                    agregarParrafo(document, "$n. ${entrada.categoria}:", negrita = true)
                    agregarParrafo(document, "- ${entrada.texto}")
                } else {
                    agregarParrafo(document, "$n. ${entrada.texto}")
                }
                entrada.fotos.forEach { foto -> agregarFotoInline(document, foto) }
                document.createParagraph()
            }
        }

        agregarSeccion("Observaciones", entradasObservaciones)
        agregarSeccion("Recomendaciones", entradasRecomendaciones)

        if (entradasObservaciones.isNotEmpty() || entradasRecomendaciones.isNotEmpty()) {
            agregarParrafo(document, "Comentario:", negrita = true)
            if (entradasObservaciones.isNotEmpty())
                agregarParrafo(document, "•  Los ítems enumerados como observaciones deben ser subsanados antes de la próxima carga.")
            if (entradasRecomendaciones.isNotEmpty())
                agregarParrafo(document, "•  Los ítems enunciados como recomendaciones no son condicionantes para la carga.")
        } else {
            agregarParrafo(document, "De la inspección realizada no surgieron observaciones ni recomendaciones.")
        }

        finalizarDocumento(document, assets, archivoSalida)
    }

    // ---------- Helpers de texto ----------

    private fun agregarParrafo(
        document: XWPFDocument, texto: String, negrita: Boolean = false, subrayado: Boolean = false,
        tamanioPuntos: Int = 11, centrado: Boolean = false, derecha: Boolean = false
    ): XWPFParagraph {
        val p = document.createParagraph()
        p.alignment = when {
            centrado -> ParagraphAlignment.CENTER
            derecha -> ParagraphAlignment.RIGHT
            else -> ParagraphAlignment.LEFT
        }
        val run = p.createRun()
        run.setText(texto)
        run.isBold = negrita
        run.setUnderline(if (subrayado) UnderlinePatterns.SINGLE else UnderlinePatterns.NONE)
        run.fontSize = tamanioPuntos
        return p
    }

    private fun aTitulo(texto: String): String =
        texto.lowercase().split(" ").joinToString(" ") { palabra ->
            if (palabra.isNotEmpty()) palabra.replaceFirstChar { it.uppercase() } else palabra
        }

    private fun formatearFecha(fechaMillis: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = fechaMillis }
        val dia = cal.get(Calendar.DAY_OF_MONTH)
        val mes = MESES[cal.get(Calendar.MONTH)].replaceFirstChar { it.uppercase() }
        val anio = cal.get(Calendar.YEAR)
        return "$dia de $mes del $anio"
    }

    private fun agregarFotoInline(document: XWPFDocument, foto: Foto) {
        val bytes = File(foto.rutaArchivo).readBytes()
        val p = document.createParagraph()
        p.alignment = ParagraphAlignment.CENTER
        // 8x6cm fijo, igual que el original (2880000/2160000 EMU = 8cm/6cm a 360000 EMU/cm).
        DocxDrawUtils.agregarImagenInline(
            p, bytes, XWPFDocument.PICTURE_TYPE_JPEG,
            anchoEmu = 2880000L, altoEmu = 2160000L, nombreArchivo = "${foto.id}.jpg"
        )
    }

    // ---------- Membrete + guardado ----------

    private fun finalizarDocumento(document: XWPFDocument, assets: AssetManager, archivoSalida: File) {
        val logoBytes = assets.open("logo.png").use { it.readBytes() }
        val blobBytes = assets.open("blob.png").use { it.readBytes() }

        DocxDrawUtils.configurarMembrete(
            document, logoBytes, blobBytes,
            logoAnchoEmu = 1576305L, logoAltoEmu = 715810L, logoOffsetXEmu = 5482800L, logoOffsetYEmu = 487360L,
            blobAnchoEmu = 3007925L, blobAltoEmu = 2260129L, blobOffsetXEmu = 0L, blobOffsetYEmu = 8833400L,
            lineaDireccion = DIRECCION_EMPRESA, lineaContacto = CONTACTO_EMPRESA
        )

        val body = document.document.body
        val sectPr = if (body.isSetSectPr) body.sectPr else body.addNewSectPr()
        sectPr.addNewPgSz().apply { setW(BigInteger.valueOf(11906)); setH(BigInteger.valueOf(16838)) }
        sectPr.addNewPgMar().apply {
            setTop(BigInteger.valueOf(2400)); setRight(BigInteger.valueOf(1440)); setBottom(BigInteger.valueOf(1440))
            setLeft(BigInteger.valueOf(1440)); setHeader(BigInteger.valueOf(720)); setFooter(BigInteger.valueOf(720))
            setGutter(BigInteger.ZERO)
        }

        FileOutputStream(archivoSalida).use { document.write(it) }
        document.close()
    }
}
