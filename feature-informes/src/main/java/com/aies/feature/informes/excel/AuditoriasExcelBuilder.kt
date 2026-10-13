package com.aies.feature.informes.excel

import com.aies.feature.informes.data.InformeExportRow
import org.apache.poi.ss.usermodel.BorderStyle
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.ss.usermodel.VerticalAlignment
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xssf.usermodel.XSSFCellStyle
import org.apache.poi.xssf.usermodel.XSSFColor
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Port de exportar_excel.py (openpyxl) a Apache POI: mismo esquema de columnas y colores
 * que la app Flask original, generando un .xlsx real (no un .csv, a diferencia de
 * feature-certificados, que sí replica fielmente que su "Exportar a Excel" es un .csv).
 */
object AuditoriasExcelBuilder {

    private val NAVY = byteArrayOf(0x1D, 0x34, 0x61)
    private val GREEN = byteArrayOf(0x1B, 0x7A, 0x3E)
    private val RED = byteArrayOf(0xB2.toByte(), 0x22, 0x22)
    private val FONDO_SI = byteArrayOf(0xE4.toByte(), 0xF3.toByte(), 0xE9.toByte())
    private val FONDO_NO = byteArrayOf(0xFB.toByte(), 0xE4.toByte(), 0xE4.toByte())

    private val COLUMNAS = listOf(
        "Nº Informe" to 16, "Cliente" to 32, "CUIT" to 16, "Patente Tractor" to 16,
        "Patente Semi" to 16, "Calibrado Nº" to 16, "Fecha Emisión" to 14, "Fecha Vencimiento" to 16,
        "Cant. Cisternas" to 14, "Resultado Final" to 14, "Vacío Cumple" to 12,
        "Trazabilidad Cumple" to 16, "Posición Cumple" to 14, "Fecha Creado" to 16, "Notas" to 40
    )

    fun generar(registros: List<InformeExportRow>): XSSFWorkbook {
        val wb = XSSFWorkbook()
        val sheet = wb.createSheet("Auditorías")
        val fmtFecha = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val fmtFechaHora = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

        val estiloHeader = wb.createCellStyle() as XSSFCellStyle
        estiloHeader.setFillForegroundColor(XSSFColor(NAVY, null))
        estiloHeader.fillPattern = FillPatternType.SOLID_FOREGROUND
        estiloHeader.alignment = HorizontalAlignment.CENTER
        estiloHeader.verticalAlignment = VerticalAlignment.CENTER
        estiloHeader.wrapText = true
        val fuenteHeader = wb.createFont().apply { bold = true; fontHeightInPoints = 11; color = IndexedColors.WHITE.index }
        estiloHeader.setFont(fuenteHeader)

        val headerRow = sheet.createRow(0)
        headerRow.heightInPoints = 26f
        COLUMNAS.forEachIndexed { i, (nombre, ancho) ->
            headerRow.createCell(i).apply { setCellValue(nombre); cellStyle = estiloHeader }
            sheet.setColumnWidth(i, ancho * 256)
        }

        val bordeFino = wb.createCellStyle() as XSSFCellStyle
        bordeFino.borderTop = BorderStyle.THIN; bordeFino.borderBottom = BorderStyle.THIN
        bordeFino.borderLeft = BorderStyle.THIN; bordeFino.borderRight = BorderStyle.THIN
        bordeFino.verticalAlignment = VerticalAlignment.CENTER

        val bordeNotas = wb.createCellStyle() as XSSFCellStyle
        bordeNotas.cloneStyleFrom(bordeFino)
        bordeNotas.wrapText = true

        var fila = 1
        for (r in registros) {
            val row = sheet.createRow(fila)
            val valores = listOf(
                r.nroInforme, r.clienteRazonSocial, r.clienteCuit, r.patenteTractor, r.patenteSemirremolque,
                r.calibradoNro, fmtFecha.format(Date(r.fechaEmisionTimestamp)), fmtFecha.format(Date(r.fechaVencimientoTimestamp)),
                r.cantCisternas.toString(), r.resultadoFinal, r.cvCumple, r.tdCumple, r.psCumple,
                fmtFechaHora.format(Date(r.fechaCreadoTimestamp)), r.notas ?: ""
            )
            valores.forEachIndexed { col, valor ->
                row.createCell(col).apply {
                    setCellValue(valor)
                    cellStyle = if (col == 14) bordeNotas else bordeFino
                }
            }

            val colorFondo = when (r.resultadoFinal) { "SI" -> FONDO_SI; "NO" -> FONDO_NO; else -> null }
            if (colorFondo != null) {
                val estiloResultado = wb.createCellStyle() as XSSFCellStyle
                estiloResultado.cloneStyleFrom(bordeFino)
                estiloResultado.setFillForegroundColor(XSSFColor(colorFondo, null))
                estiloResultado.fillPattern = FillPatternType.SOLID_FOREGROUND
                val fuenteResultado = wb.createFont().apply {
                    bold = true
                    color = if (r.resultadoFinal == "SI") IndexedColors.GREEN.index else IndexedColors.RED.index
                }
                estiloResultado.setFont(fuenteResultado)
                row.getCell(9).cellStyle = estiloResultado
            }

            fila++
        }

        sheet.setAutoFilter(CellRangeAddress(0, fila - 1, 0, COLUMNAS.size - 1))
        sheet.createFreezePane(0, 1)

        return wb
    }
}
