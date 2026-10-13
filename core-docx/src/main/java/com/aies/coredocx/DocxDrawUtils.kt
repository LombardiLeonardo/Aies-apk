package com.aies.coredocx

import org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy
import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFHeaderFooter
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFTableCell
import org.openxmlformats.schemas.drawingml.x2006.main.CTGraphicalObject
import org.openxmlformats.schemas.drawingml.x2006.wordprocessingDrawing.STRelFromH
import org.openxmlformats.schemas.drawingml.x2006.wordprocessingDrawing.STRelFromV
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder
import java.io.ByteArrayInputStream
import java.math.BigInteger

/**
 * Helpers de dibujo compartidos por los generadores .docx de los feature modules (mismo rol
 * que PdfDrawUtils para los PDF): imagen flotante/anclada para el membrete (logo/blob),
 * imagen inline centrada, y celdas de tabla con estilo (usadas para las tablas de resultado
 * Apto/NoApto y encabezados).
 *
 * Todo lo que toca tipos generados por XmlBeans (Anchor, Posiciones, Bordes) usa llamadas a
 * método explícitas (getX()/setX()/addNewX()) en vez de sintaxis de propiedad de Kotlin:
 * varios de estos tipos exponen un atributo y un elemento hijo con el mismo nombre XML
 * (ej. wp:anchor tiene el atributo booleano `simplePos` Y el elemento `<wp:simplePos>`), lo
 * que hace que Kotlin no pueda sintetizar una propiedad única y sin ambigüedad.
 */
object DocxDrawUtils {

    /**
     * Inserta una imagen flotante posicionada relativa a la PÁGINA en ambos ejes, sin
     * ajuste de texto (wrapNone) — la técnica estándar para logos/marcas de agua de
     * membrete. El nodo `pic:pic` se arma parseando un fragmento XML en vez de construir
     * el árbol XmlBeans a mano: los namespaces cruzados (a:/pic:/r:) que necesita ese nodo
     * son mucho más frágiles de completar campo a campo que de parsear directamente.
     */
    fun agregarImagenFlotante(
        part: XWPFHeaderFooter,
        imagenBytes: ByteArray,
        tipoImagen: Int,
        anchoEmu: Long,
        altoEmu: Long,
        offsetXEmu: Long,
        offsetYEmu: Long,
        detrasDelTexto: Boolean = true
    ) {
        val paragraph = part.paragraphs.firstOrNull() ?: part.createParagraph()
        val run = paragraph.createRun()
        val relId = part.addPictureData(imagenBytes, tipoImagen)
        val picId = (0..Int.MAX_VALUE).random()

        val drawing = run.getCTR().addNewDrawing()
        val anchor = drawing.addNewAnchor()
        anchor.setSimplePos2(false)
        anchor.addNewSimplePos().apply { setX(0); setY(0) }
        anchor.addNewExtent().apply { setCx(anchoEmu); setCy(altoEmu) }
        anchor.addNewDocPr().apply { setId(picId.toLong()); setName("Imagen $picId") }
        anchor.addNewCNvGraphicFramePr()
        anchor.addNewPositionH().apply {
            setRelativeFrom(STRelFromH.PAGE)
            setPosOffset(offsetXEmu.toInt())
        }
        anchor.addNewPositionV().apply {
            setRelativeFrom(STRelFromV.PAGE)
            setPosOffset(offsetYEmu.toInt())
        }
        anchor.addNewWrapNone()
        anchor.setBehindDoc(detrasDelTexto)
        anchor.setLocked(false)
        anchor.setLayoutInCell(true)
        anchor.setAllowOverlap(true)
        anchor.setDistT(0L)
        anchor.setDistB(0L)
        anchor.setDistL(0L)
        anchor.setDistR(0L)

        val graphicXml = """
            <a:graphic xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
              <a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture">
                <pic:pic xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">
                  <pic:nvPicPr>
                    <pic:cNvPr id="$picId" name="Imagen $picId"/>
                    <pic:cNvPicPr/>
                  </pic:nvPicPr>
                  <pic:blipFill>
                    <a:blip r:embed="$relId" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"/>
                    <a:stretch><a:fillRect/></a:stretch>
                  </pic:blipFill>
                  <pic:spPr>
                    <a:xfrm><a:off x="0" y="0"/><a:ext cx="$anchoEmu" cy="$altoEmu"/></a:xfrm>
                    <a:prstGeom prst="rect"><a:avLst/></a:prstGeom>
                  </pic:spPr>
                </pic:pic>
              </a:graphicData>
            </a:graphic>
        """.trimIndent()
        anchor.setGraphic(CTGraphicalObject.Factory.parse(graphicXml))
    }

    /** Imagen inline centrada — a diferencia de la flotante, POI la soporta nativamente. */
    fun agregarImagenInline(
        paragraph: XWPFParagraph,
        imagenBytes: ByteArray,
        tipoImagen: Int,
        anchoEmu: Long,
        altoEmu: Long,
        nombreArchivo: String = "imagen.jpg"
    ) {
        if (paragraph.alignment != ParagraphAlignment.CENTER) paragraph.alignment = ParagraphAlignment.CENTER
        val run = paragraph.createRun()
        ByteArrayInputStream(imagenBytes).use { stream ->
            run.addPicture(stream, tipoImagen, nombreArchivo, anchoEmu.toInt(), altoEmu.toInt())
        }
    }

    /** Celda con fondo/color de texto/negrita/alineación — usada para resultado Apto/NoApto y encabezados. */
    fun celdaConEstilo(
        cell: XWPFTableCell,
        texto: String,
        colorFondoHex: String? = null,
        colorTextoHex: String = "000000",
        negrita: Boolean = false,
        centrado: Boolean = true
    ) {
        if (colorFondoHex != null) cell.color = colorFondoHex
        val paragraph = cell.paragraphs.firstOrNull() ?: cell.addParagraph()
        if (centrado) paragraph.alignment = ParagraphAlignment.CENTER
        val run = paragraph.createRun()
        run.setText(texto)
        run.isBold = negrita
        run.color = colorTextoHex
    }

    /** Fuerza los 4 bordes de la celda a un único trazo sólido (grosor en octavos de punto). */
    fun configurarBordesCelda(cell: XWPFTableCell, colorHex: String = "BFBFBF", grosorEighthPoints: Int = 4) {
        val tcPr = cell.getCTTc().tcPr ?: cell.getCTTc().addNewTcPr()
        val borders = tcPr.tcBorders ?: tcPr.addNewTcBorders()
        val sz = BigInteger.valueOf(grosorEighthPoints.toLong())
        listOf(borders.addNewTop(), borders.addNewBottom(), borders.addNewLeft(), borders.addNewRight()).forEach { border ->
            border.setVal(STBorder.SINGLE)
            border.setSz(sz)
            border.setColor(colorHex)
        }
    }

    /** Membrete completo: logo flotante en el header, blob decorativo + datos de contacto en el footer. */
    fun configurarMembrete(
        document: XWPFDocument,
        logoBytes: ByteArray,
        blobBytes: ByteArray,
        logoAnchoEmu: Long, logoAltoEmu: Long, logoOffsetXEmu: Long, logoOffsetYEmu: Long,
        blobAnchoEmu: Long, blobAltoEmu: Long, blobOffsetXEmu: Long, blobOffsetYEmu: Long,
        lineaDireccion: String, lineaContacto: String
    ) {
        val policy = document.headerFooterPolicy ?: document.createHeaderFooterPolicy()
        val header = policy.createHeader(XWPFHeaderFooterPolicy.DEFAULT)
        val footer = policy.createFooter(XWPFHeaderFooterPolicy.DEFAULT)

        agregarImagenFlotante(header, logoBytes, XWPFDocument.PICTURE_TYPE_PNG, logoAnchoEmu, logoAltoEmu, logoOffsetXEmu, logoOffsetYEmu)
        agregarImagenFlotante(footer, blobBytes, XWPFDocument.PICTURE_TYPE_PNG, blobAnchoEmu, blobAltoEmu, blobOffsetXEmu, blobOffsetYEmu)

        listOf(lineaDireccion, lineaContacto).forEach { linea ->
            val paragraph = footer.createParagraph()
            paragraph.alignment = ParagraphAlignment.RIGHT
            val run = paragraph.createRun()
            run.setText(linea)
            run.fontSize = 8
        }
    }
}
