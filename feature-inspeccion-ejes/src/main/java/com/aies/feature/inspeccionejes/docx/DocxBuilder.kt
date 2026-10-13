package com.aies.feature.inspeccionejes.docx

import android.content.res.AssetManager
import com.aies.coredocx.DocxDrawUtils
import com.aies.feature.inspeccionejes.data.ChecklistLado
import com.aies.feature.inspeccionejes.data.Control
import com.aies.feature.inspeccionejes.data.Foto
import com.aies.feature.inspeccionejes.data.Lado
import com.aies.feature.inspeccionejes.data.PuntaDeEje
import com.aies.feature.inspeccionejes.data.ResultadoEstado
import com.aies.feature.inspeccionejes.data.aTexto
import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFTable
import java.io.File
import java.io.FileOutputStream
import java.math.BigInteger
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Genera los dos informes Word de InspeccionEjes (DocxReportService.cs original) sobre
 * core-docx: checklist de piezas por eje y ensayo de líquidos penetrantes. No es un port
 * 1:1 de la geometría OpenXml SDK original (esto es código nuevo para la super app) — las
 * filas de "Observaciones" se resuelven como párrafos debajo de la tabla en vez de una fila
 * con GridSpan, para no tener que manipular fusión de celdas a mano; el resto del contenido
 * (tablas, colores Apto/NoApto, zebra striping, grillas de fotos, membrete) es equivalente.
 */
object DocxBuilder {

    private const val DIRECCION_EMPRESA = "José J. Podestá 64, Avellaneda, Pcia. de Buenos Aires."
    private const val CONTACTO_EMPRESA = "+54 9 11 3124 0535  |  +54 11 4201 0451  |  info@aies.com.ar  |  www.aies.com.ar"

    private const val COLOR_PRIMARIO = "1F3864"
    private const val COLOR_FILA_ALTERNA = "EEF2F8"
    private const val COLOR_BORDE_SUAVE = "BFBFBF"
    private const val COLOR_APTO = "1E7B34"
    private const val COLOR_NO_APTO = "B02A2A"
    private const val COLOR_FONDO_APTO = "E3F2E1"
    private const val COLOR_FONDO_NO_APTO = "FBE4E4"

    private const val ANCHO_TABLA_TWIPS = 9026

    private val fmtFecha = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    fun generarInformeChecklist(
        assets: AssetManager,
        control: Control,
        checklists: List<ChecklistLado>,
        fotosPorChecklistId: Map<String, List<Foto>>,
        archivoSalida: File
    ) {
        val document = XWPFDocument()
        agregarTitulo(document, "INFORME DE CONTROL DE EJES - CHECKLIST DE PIEZAS")
        agregarTablaDatosControl(document, control)

        for (numeroEje in 1..control.cantidadEjes) {
            val izquierdo = checklists.firstOrNull { it.numeroEje == numeroEje && it.lado == Lado.Izquierdo }
            val derecho = checklists.firstOrNull { it.numeroEje == numeroEje && it.lado == Lado.Derecho }

            agregarBanner(document, "EJE $numeroEje", COLOR_PRIMARIO)
            agregarTablaChecklistEje(document, izquierdo, derecho)

            izquierdo?.let { fotosPorChecklistId[it.id] }?.takeIf { it.isNotEmpty() }?.let { fotos ->
                agregarBanner(document, "Eje $numeroEje - Infografía (Izquierdo)", COLOR_PRIMARIO)
                agregarGridFotos(document, fotos)
            }
            derecho?.let { fotosPorChecklistId[it.id] }?.takeIf { it.isNotEmpty() }?.let { fotos ->
                agregarBanner(document, "Eje $numeroEje - Infografía (Derecho)", COLOR_PRIMARIO)
                agregarGridFotos(document, fotos)
            }
        }

        finalizarDocumento(document, assets, archivoSalida)
    }

    fun generarInformeLpt(
        assets: AssetManager,
        control: Control,
        puntas: List<PuntaDeEje>,
        fotosPorPuntaId: Map<String, List<Foto>>,
        archivoSalida: File
    ) {
        val document = XWPFDocument()
        agregarTitulo(document, "INFORME DE ENSAYO DE LÍQUIDOS PENETRANTES - PUNTAS DE EJE")
        agregarTablaDatosControl(document, control)

        for (numeroEje in 1..control.cantidadEjes) {
            for (lado in listOf(Lado.Izquierdo, Lado.Derecho)) {
                val punta = puntas.firstOrNull { it.numeroEje == numeroEje && it.lado == lado } ?: continue
                val nombreLado = if (lado == Lado.Izquierdo) "Izquierdo" else "Derecho"
                val color = if (punta.resultado == ResultadoEstado.Apto) COLOR_APTO else COLOR_NO_APTO
                agregarBanner(document, "EJE $numeroEje - $nombreLado   |   Resultado: ${punta.resultado.aTexto()}", color)

                if (!punta.observaciones.isNullOrBlank()) {
                    val p = document.createParagraph()
                    p.createRun().setText("Observaciones: ${punta.observaciones}")
                    document.createParagraph()
                }

                fotosPorPuntaId[punta.id]?.takeIf { it.isNotEmpty() }?.let { fotos -> agregarGridFotos(document, fotos) }
            }
        }

        finalizarDocumento(document, assets, archivoSalida)
    }

    // ---------- Membrete + guardado ----------

    private fun finalizarDocumento(document: XWPFDocument, assets: AssetManager, archivoSalida: File) {
        val logoBytes = assets.open("logo.png").use { it.readBytes() }
        val blobBytes = assets.open("blob.png").use { it.readBytes() }

        DocxDrawUtils.configurarMembrete(
            document, logoBytes, blobBytes,
            logoAnchoEmu = 1748155L, logoAltoEmu = 804545L, logoOffsetXEmu = 4128135L, logoOffsetYEmu = -19050L,
            blobAnchoEmu = 2655570L, blobAltoEmu = 1891030L, blobOffsetXEmu = -1263015L, blobOffsetYEmu = -1069975L,
            lineaDireccion = DIRECCION_EMPRESA, lineaContacto = CONTACTO_EMPRESA
        )

        val body = document.document.body
        val sectPr = if (body.isSetSectPr) body.sectPr else body.addNewSectPr()
        sectPr.addNewPgSz().apply { setW(BigInteger.valueOf(11906)); setH(BigInteger.valueOf(16838)) }
        sectPr.addNewPgMar().apply {
            setTop(BigInteger.valueOf(2050)); setRight(BigInteger.valueOf(1127)); setBottom(BigInteger.valueOf(993))
            setLeft(BigInteger.valueOf(1982)); setHeader(BigInteger.valueOf(680)); setFooter(BigInteger.valueOf(454))
            setGutter(BigInteger.ZERO)
        }

        FileOutputStream(archivoSalida).use { document.write(it) }
        document.close()
    }

    // ---------- Contenido ----------

    private fun agregarTitulo(document: XWPFDocument, texto: String) {
        val p = document.createParagraph()
        p.alignment = ParagraphAlignment.CENTER
        val run = p.createRun()
        run.setText(texto)
        run.isBold = true
        run.color = COLOR_PRIMARIO
        run.fontSize = 15
        document.createParagraph()
    }

    private fun agregarBanner(document: XWPFDocument, texto: String, colorFondo: String) {
        val tabla = document.createTable(1, 1)
        tabla.setWidth(ANCHO_TABLA_TWIPS)
        val cell = tabla.getRow(0).getCell(0)
        DocxDrawUtils.celdaConEstilo(cell, texto, colorFondoHex = colorFondo, colorTextoHex = "FFFFFF", negrita = true, centrado = true)
        document.createParagraph()
    }

    private fun agregarTablaDatosControl(document: XWPFDocument, control: Control) {
        val datos = listOf(
            "Cliente" to control.cliente,
            "Fecha" to fmtFecha.format(java.util.Date(control.fecha)),
            "Responsable" to (control.responsable ?: "-"),
            "Dominio tractor" to (control.dominioTractor ?: "-"),
            "Marca tractor" to (control.marcaTractor ?: "-"),
            "Dominio semirremolque" to (control.dominioSemirremolque ?: "-"),
            "Marca semirremolque" to (control.marcaSemirremolque ?: "-")
        )

        val tabla = document.createTable(datos.size, 2)
        tabla.setWidth(ANCHO_TABLA_TWIPS)
        datos.forEachIndexed { i, (etiqueta, valor) ->
            val fila = tabla.getRow(i)
            estilarCeldaDato(fila.getCell(0), etiqueta, negrita = true, colorFondo = COLOR_FILA_ALTERNA)
            estilarCeldaDato(fila.getCell(1), valor)
        }
        document.createParagraph()
    }

    private fun agregarTablaChecklistEje(document: XWPFDocument, izq: ChecklistLado?, der: ChecklistLado?) {
        val filas = listOf(
            Triple("Holgura en rodamientos de rueda armada", izq?.holguraRodamientos, der?.holguraRodamientos),
            Triple("Ajuste de tuerca central", izq?.ajusteTuercaCentral, der?.ajusteTuercaCentral),
            Triple("Arandela de seguridad", izq?.arandelaSeguridad?.aTexto(), der?.arandelaSeguridad?.aTexto()),
            Triple("Lubricante de rodamientos", izq?.lubricanteRodamientos?.aTexto(), der?.lubricanteRodamientos?.aTexto()),
            Triple("Estado de rodamiento", izq?.estadoRodamiento?.aTexto(), der?.estadoRodamiento?.aTexto()),
            Triple("Desgaste de eje en alojamiento de pista interior", izq?.desgasteEjeAlojamientoPistaInterior, der?.desgasteEjeAlojamientoPistaInterior),
            Triple("Desgaste de alojamiento de pistas exteriores en maza", izq?.desgasteAlojamientoPistasExterioresMaza, der?.desgasteAlojamientoPistasExterioresMaza),
            Triple("Fisuras en puntera de eje", izq?.fisurasPunteraEje, der?.fisurasPunteraEje),
            Triple("Holgura de bujes de tensor / brazo de suspensión", izq?.holguraBujesTensor, der?.holguraBujesTensor),
            Triple("Retenes", izq?.retenes?.aTexto(), der?.retenes?.aTexto()),
            Triple("Campana de freno", izq?.campanaFreno?.aTexto(), der?.campanaFreno?.aTexto()),
            Triple("Disco de freno", izq?.discoFreno?.aTexto(), der?.discoFreno?.aTexto()),
            Triple("RESULTADO", izq?.resultado?.aTexto(), der?.resultado?.aTexto())
        )

        val tabla = document.createTable(filas.size + 1, 3)
        tabla.setWidth(ANCHO_TABLA_TWIPS)

        val encabezado = tabla.getRow(0)
        estilarCeldaDato(encabezado.getCell(0), "Ítem", negrita = true, colorFondo = COLOR_PRIMARIO, colorTexto = "FFFFFF")
        estilarCeldaDato(encabezado.getCell(1), "Izquierdo", negrita = true, colorFondo = COLOR_PRIMARIO, colorTexto = "FFFFFF")
        estilarCeldaDato(encabezado.getCell(2), "Derecho", negrita = true, colorFondo = COLOR_PRIMARIO, colorTexto = "FFFFFF")

        filas.forEachIndexed { indice, (etiqueta, valorIzq, valorDer) ->
            val esResultado = etiqueta == "RESULTADO"
            val colorFondoFila = if (!esResultado && indice % 2 == 1) COLOR_FILA_ALTERNA else null
            val fila = tabla.getRow(indice + 1)

            estilarCeldaDato(fila.getCell(0), etiqueta, negrita = esResultado, colorFondo = colorFondoFila)

            if (esResultado) {
                val aptoIzq = valorIzq == "APTO"
                val aptoDer = valorDer == "APTO"
                estilarCeldaDato(fila.getCell(1), valorIzq ?: "-", negrita = true,
                    colorFondo = if (aptoIzq) COLOR_FONDO_APTO else COLOR_FONDO_NO_APTO,
                    colorTexto = if (aptoIzq) COLOR_APTO else COLOR_NO_APTO)
                estilarCeldaDato(fila.getCell(2), valorDer ?: "-", negrita = true,
                    colorFondo = if (aptoDer) COLOR_FONDO_APTO else COLOR_FONDO_NO_APTO,
                    colorTexto = if (aptoDer) COLOR_APTO else COLOR_NO_APTO)
            } else {
                estilarCeldaDato(fila.getCell(1), valorIzq ?: "-", colorFondo = colorFondoFila)
                estilarCeldaDato(fila.getCell(2), valorDer ?: "-", colorFondo = colorFondoFila)
            }
        }

        val pObs = document.createParagraph()
        val runObs = pObs.createRun()
        runObs.isItalic = true
        runObs.color = "555555"
        runObs.setText("Observaciones (Izquierdo): ${izq?.observaciones?.takeIf { it.isNotBlank() } ?: "-"}")

        val pObs2 = document.createParagraph()
        val runObs2 = pObs2.createRun()
        runObs2.isItalic = true
        runObs2.color = "555555"
        runObs2.setText("Observaciones (Derecho): ${der?.observaciones?.takeIf { it.isNotBlank() } ?: "-"}")

        document.createParagraph()
    }

    private fun estilarCeldaDato(
        cell: org.apache.poi.xwpf.usermodel.XWPFTableCell,
        texto: String,
        negrita: Boolean = false,
        colorFondo: String? = null,
        colorTexto: String = "000000"
    ) {
        DocxDrawUtils.celdaConEstilo(cell, texto, colorFondoHex = colorFondo, colorTextoHex = colorTexto, negrita = negrita, centrado = true)
        DocxDrawUtils.configurarBordesCelda(cell, COLOR_BORDE_SUAVE)
    }

    private fun agregarGridFotos(document: XWPFDocument, fotos: List<Foto>) {
        val columnas = 3
        val filas = (fotos.size + columnas - 1) / columnas
        val tabla: XWPFTable = document.createTable(filas, columnas)
        tabla.setWidth(ANCHO_TABLA_TWIPS)

        fotos.forEachIndexed { indice, foto ->
            val fila = tabla.getRow(indice / columnas)
            val cell = fila.getCell(indice % columnas)
            DocxDrawUtils.configurarBordesCelda(cell, COLOR_BORDE_SUAVE)

            val archivo = File(foto.rutaLocal)
            val parrafoImagen = cell.paragraphs.firstOrNull() ?: cell.addParagraph()
            parrafoImagen.alignment = ParagraphAlignment.CENTER
            if (archivo.exists()) {
                val bytes = archivo.readBytes()
                DocxDrawUtils.agregarImagenInline(
                    parrafoImagen, bytes, org.apache.poi.xwpf.usermodel.XWPFDocument.PICTURE_TYPE_JPEG,
                    anchoEmu = 1440000L, altoEmu = 1080000L, nombreArchivo = "${foto.id}.jpg"
                )
            }

            val parrafoDesc = cell.addParagraph()
            parrafoDesc.alignment = ParagraphAlignment.CENTER
            val runDesc = parrafoDesc.createRun()
            runDesc.setText(foto.descripcion ?: "")
            runDesc.fontSize = 8
        }

        document.createParagraph()
    }
}
