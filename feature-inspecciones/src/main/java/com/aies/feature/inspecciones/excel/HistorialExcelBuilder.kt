package com.aies.feature.inspecciones.excel

import com.aies.feature.inspecciones.data.ControlRealizado
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Arma el .xlsx de historial de controles para facturación: una hoja "Resumen"
 * (cantidad de controles por empresa y tipo) y una hoja "Detalle" (un renglón por control).
 *
 * Bug real encontrado en logcat (crash sin diagnosticar de "Exportar a Excel" del Historial,
 * reportado como pendiente en varias auditorías): con `HSSFWorkbook` (.xls viejo), el simple
 * hecho de llamar a `createCellStyle()` -- sin ni siquiera pedir un color -- dispara la
 * inicialización estática de `HSSFColor$HSSFColorPredefined`, que referencia `java.awt.Color`.
 * Esa clase no existe en el runtime de Android (`java.awt` es de escritorio) → NoClassDefFoundError
 * y crash inmediato. El resto de los exportadores de .xls del módulo (InspeccionActivity/
 * AxionActivity/EngancheActivity) nunca lo sufrieron porque abren una PLANTILLA .xls ya armada
 * desde assets (`HSSFWorkbook(inputStream)`) y solo pisan valores de celda, sin llamar nunca a
 * `createCellStyle()` -- acá, en cambio, el workbook se arma desde cero. Se cambia a
 * `XSSFWorkbook` (.xlsx), que ya se usa sin problemas en feature-informes/AuditoriasExcelBuilder.kt
 * con el mismo patrón (`createCellStyle()` desde cero) -- XSSFColor trabaja con RGB en bytes, sin
 * tocar `java.awt` para nada.
 *
 * OJO: no usar Sheet.autoSizeColumn() acá — depende de java.awt.Font, que tampoco existe
 * en el runtime de Android. Por eso los anchos son fijos.
 */
object HistorialExcelBuilder {

    fun generar(controles: List<ControlRealizado>): XSSFWorkbook {
        val workbook = XSSFWorkbook()
        val fmtFecha = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val estiloTitulo = workbook.createCellStyle().apply {
            setFont(workbook.createFont().apply { bold = true })
        }

        // ── Resumen: cantidad de controles por empresa y tipo ──────────────
        val resumen = workbook.createSheet("Resumen")
        resumen.setColumnWidth(0, 24 * 256)
        resumen.setColumnWidth(1, 20 * 256)
        resumen.setColumnWidth(2, 12 * 256)

        var filaResumen = 0
        resumen.createRow(filaResumen++).apply {
            listOf("Empresa", "Tipo de control", "Cantidad").forEachIndexed { i, texto ->
                createCell(i).apply { setCellValue(texto); setCellStyle(estiloTitulo) }
            }
        }

        val porEmpresaYTipo = controles
            .groupingBy { it.empresa to it.tipoInspeccion }
            .eachCount()
            .toList()
            .sortedWith(compareBy({ it.first.first }, { it.first.second }))

        for ((clave, cantidad) in porEmpresaYTipo) {
            val (empresa, tipo) = clave
            resumen.createRow(filaResumen++).apply {
                createCell(0).setCellValue(empresa)
                createCell(1).setCellValue(tipo)
                createCell(2).setCellValue(cantidad.toDouble())
            }
        }

        val totalPorEmpresa = controles.groupingBy { it.empresa }.eachCount().toList().sortedBy { it.first }
        if (totalPorEmpresa.isNotEmpty()) {
            filaResumen++
            for ((empresa, total) in totalPorEmpresa) {
                resumen.createRow(filaResumen++).apply {
                    createCell(0).apply { setCellValue("TOTAL $empresa"); setCellStyle(estiloTitulo) }
                    createCell(2).apply { setCellValue(total.toDouble()); setCellStyle(estiloTitulo) }
                }
            }
        }

        // ── Detalle: un renglón por control ─────────────────────────────────
        val detalle = workbook.createSheet("Detalle")
        val anchosDetalle = listOf(12, 22, 16, 14, 14, 18)
        anchosDetalle.forEachIndexed { col, ancho -> detalle.setColumnWidth(col, ancho * 256) }

        var filaDetalle = 0
        detalle.createRow(filaDetalle++).apply {
            listOf("Fecha", "Empresa", "Tipo", "Patente Tractor", "Patente Semi", "Auditor").forEachIndexed { i, texto ->
                createCell(i).apply { setCellValue(texto); setCellStyle(estiloTitulo) }
            }
        }
        for (control in controles.sortedBy { it.fechaTimestamp }) {
            detalle.createRow(filaDetalle++).apply {
                createCell(0).setCellValue(fmtFecha.format(Date(control.fechaTimestamp)))
                createCell(1).setCellValue(control.empresa)
                createCell(2).setCellValue(control.tipoInspeccion)
                createCell(3).setCellValue(control.patenteTractor)
                createCell(4).setCellValue(control.patenteSemi)
                createCell(5).setCellValue(control.auditor)
            }
        }

        return workbook
    }
}
