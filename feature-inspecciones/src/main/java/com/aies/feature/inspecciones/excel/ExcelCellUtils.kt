package com.aies.feature.inspecciones.excel

import android.content.res.AssetManager
import android.util.Log
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.ss.usermodel.ClientAnchor
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.Workbook

private const val TAG = "ExcelCellUtils"

/** Escribe un valor en una celda, creando la fila/celda si todavía no existen. */
fun Sheet.escribirCeldaSegura(filaIndex: Int, colIndex: Int, valor: String) {
    val fila = getRow(filaIndex) ?: createRow(filaIndex)
    val celda = fila.getCell(colIndex) ?: fila.createCell(colIndex)
    celda.setCellValue(valor)
}

/**
 * Igual que [escribirCeldaSegura] pero descarta la celda existente antes de escribir.
 * Útil cuando la celda original trae rich text/formato que rompe un setCellValue directo.
 */
fun Sheet.forzarEscritura(filaIndex: Int, colIndex: Int, valor: String) {
    val fila = getRow(filaIndex) ?: createRow(filaIndex)
    val celdaVieja = fila.getCell(colIndex)
    if (celdaVieja != null) fila.removeCell(celdaVieja)
    fila.createCell(colIndex).setCellValue(valor)
}

/**
 * Inserta la firma PNG del auditor en la primera hoja del workbook.
 * Busca en assets el archivo "firma_PRIMERNOMBRE.png" (ej: auditor "JUAN PEREZ" → "firma_JUAN.png").
 * Si no encuentra el archivo, no hace nada (queda el espacio en blanco para firmar a mano).
 */
fun insertarFirmaEnExcel(
    workbook: HSSFWorkbook,
    assets: AssetManager,
    auditor: String,
    col1: Int, row1: Int,
    col2: Int, row2: Int
) {
    try {
        val primerNombre = auditor.trim().uppercase().split(" ").firstOrNull() ?: return
        val nombreArchivo = "firma_$primerNombre.png"

        val listaAssets = assets.list("") ?: emptyArray()
        if (!listaAssets.contains(nombreArchivo)) {
            Log.w(TAG, "Firma no encontrada: $nombreArchivo | Assets disponibles: ${listaAssets.joinToString()}")
            return
        }

        val firmaBytes = assets.open(nombreArchivo).use { it.readBytes() }
        Log.d(TAG, "Firma OK: $nombreArchivo (${firmaBytes.size} bytes)")

        val pictureIdx = workbook.addPicture(firmaBytes, Workbook.PICTURE_TYPE_PNG)
        val drawing = workbook.getSheetAt(0).createDrawingPatriarch()

        // POI 5.x: crear anchor a través del CreationHelper del workbook
        val anchor = workbook.creationHelper.createClientAnchor()
        anchor.setCol1(col1)
        anchor.setRow1(row1)
        anchor.setCol2(col2)
        anchor.setRow2(row2)
        anchor.setDx1(0)
        anchor.setDy1(0)
        anchor.setDx2(0)
        anchor.setDy2(0)
        anchor.setAnchorType(ClientAnchor.AnchorType.DONT_MOVE_AND_RESIZE)

        drawing.createPicture(anchor, pictureIdx)
        Log.d(TAG, "Firma insertada en cols $col1-$col2 filas $row1-$row2")
    } catch (e: Exception) {
        Log.e(TAG, "Error insertando firma para '$auditor'", e)
    }
}
