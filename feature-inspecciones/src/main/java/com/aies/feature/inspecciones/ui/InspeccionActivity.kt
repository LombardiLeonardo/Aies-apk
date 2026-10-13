package com.aies.feature.inspecciones.ui

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aies.coredata.AppDatabase
import com.aies.coredata.remote.SupabaseClientProvider
import com.aies.feature.inspecciones.R
import com.aies.feature.inspecciones.data.HistorialCompartidoHelper
import com.aies.feature.inspecciones.data.InspeccionesLocalDatabase
import com.aies.feature.inspecciones.data.controlRealizadoDesdeIntent
import com.aies.feature.inspecciones.excel.escribirCeldaSegura
import com.aies.feature.inspecciones.excel.forzarEscritura
import com.aies.feature.inspecciones.excel.insertarFirmaEnExcel
import com.aies.feature.inspecciones.util.MediaStoreUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.FormulaEvaluator
import org.apache.poi.ss.util.CellRangeAddress
import java.io.InputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class InspeccionActivity : AppCompatActivity() {

    private lateinit var rvItems: RecyclerView
    private lateinit var btnFinalizar: Button
    private lateinit var btnEstadoGeneral: Button

    private val resultadosInspeccion = mutableMapOf<Int, String>()
    private val textosObservaciones = mutableMapOf<Int, String>()
    private val listaTareasGenerales = mutableListOf<ItemControl>()
    // Observación libre, no atada a ningún ítem numerado del checklist -- pedido explícito: a
    // veces hace falta dejar constancia de algo que no está en ninguna fila del Excel. Se
    // agrega como un ítem más al reporte de observaciones (ver procesarYExportarAmbos), con
    // descripción fija "Estado general de la unidad".
    private var estadoGeneralTexto: String = ""

    private var tipoInspeccion: String = "LIVIANOS"
    private var nombreAuditor: String = ""

    private val fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val fechaHoy: String by lazy { LocalDate.now().format(fmt) }
    private val fechaCaducidad: String by lazy { LocalDate.now().plusMonths(6).format(fmt) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_inspeccion)

        tipoInspeccion = intent.getStringExtra("TIPO_INSPECCION") ?: "LIVIANOS"
        nombreAuditor  = intent.getStringExtra("AUDITOR") ?: ""

        rvItems = findViewById(R.id.rv_items_inspeccion)
        btnFinalizar = findViewById(R.id.btn_finalizar_inspeccion)
        btnEstadoGeneral = findViewById(R.id.btn_estado_general)

        if (savedInstanceState != null) {
            // Rotación — restaurar estado sin recargar el Excel
            @Suppress("UNCHECKED_CAST")
            val resultados = savedInstanceState.getSerializable("RESULTADOS") as? HashMap<Int, String>
            @Suppress("UNCHECKED_CAST")
            val observaciones = savedInstanceState.getSerializable("OBSERVACIONES") as? HashMap<Int, String>
            if (resultados != null) resultadosInspeccion.putAll(resultados)
            if (observaciones != null) textosObservaciones.putAll(observaciones)
            estadoGeneralTexto = savedInstanceState.getString("ESTADO_GENERAL", "")
        }
        actualizarBotonEstadoGeneral()

        lifecycleScope.launch(Dispatchers.IO) {
            cargarItemsDesdeExcel()
        }

        btnEstadoGeneral.setOnClickListener {
            ObservacionFlow.mostrarDialogo(
                activity = this,
                scope = lifecycleScope,
                descripcionItem = "Estado general de la unidad",
                textoExistente = estadoGeneralTexto
            ) { textoFinal ->
                estadoGeneralTexto = textoFinal
                actualizarBotonEstadoGeneral()
            }
        }

        btnFinalizar.setOnClickListener {
            btnFinalizar.isEnabled = false
            val progreso = android.app.ProgressDialog(this).apply {
                setMessage("Generando archivos, por favor esperá...")
                setCancelable(false)
                show()
            }
            lifecycleScope.launch(Dispatchers.IO) {
                procesarYExportarAmbos()
                withContext(Dispatchers.Main) {
                    progreso.dismiss()
                    btnFinalizar.isEnabled = true
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putSerializable("RESULTADOS", HashMap(resultadosInspeccion))
        outState.putSerializable("OBSERVACIONES", HashMap(textosObservaciones))
        outState.putString("ESTADO_GENERAL", estadoGeneralTexto)
    }

    private fun actualizarBotonEstadoGeneral() {
        btnEstadoGeneral.text = if (estadoGeneralTexto.isBlank())
            "+ Estado general de la unidad"
        else
            "✓ Estado general de la unidad (editar)"
    }

    data class ConfigExcel(
        val colId: Int,
        val colDesc: Int,
        val filaInicio: Int,
        val filaFin: Int
    )

    private fun getConfig(): ConfigExcel = when (tipoInspeccion) {
        "PESADOS"  -> ConfigExcel(colId = 2, colDesc = 3, filaInicio = 13, filaFin = 38)
        "COQUE"    -> ConfigExcel(colId = 2, colDesc = 3, filaInicio = 13, filaFin = 27)
        "QUIMICOS" -> ConfigExcel(colId = 0, colDesc = 1, filaInicio = 11, filaFin = 35)
        else       -> ConfigExcel(colId = -1, colDesc = -1, filaInicio = 8, filaFin = 70)
    }

    private fun nombrePlantilla(): String = when (tipoInspeccion) {
        "PESADOS"  -> "SPOT CHECK PESADOS.xls"
        "COQUE"    -> "Check lis FOB Coke - 2023.xls"
        "QUIMICOS" -> "Check list seguridad camiones FOB Quimicos TOP.xls"
        else       -> "Controles FOB unidades 2026 - Livianos - Rev 7 (1).xls"
    }

    private suspend fun cargarItemsDesdeExcel() {
        try {
            listaTareasGenerales.clear()

            val nombrePlantilla = nombrePlantilla()
            val inputStream: InputStream = assets.open(nombrePlantilla)
            val workbook = HSSFWorkbook(inputStream)
            val evaluator: FormulaEvaluator = workbook.creationHelper.createFormulaEvaluator()
            val formatter = DataFormatter()
            val sheet = workbook.getSheetAt(0)
            val config = getConfig()

            if (tipoInspeccion == "LIVIANOS") {
                for (i in config.filaInicio..config.filaFin) {
                    val row = sheet.getRow(i) ?: continue
                    var idItem = ""
                    var idColIndex = -1
                    for (c in 0..3) {
                        val cell = row.getCell(c) ?: continue
                        val valor = formatter.formatCellValue(cell, evaluator).replace(".0", "").trim()
                        if (valor.isNotEmpty() && valor.matches(Regex("^[0-9]+$"))) {
                            idItem = valor
                            idColIndex = c
                            break
                        }
                    }
                    if (idItem.isEmpty()) continue
                    var mejorDescripcion = ""
                    var longitudMaxima = -1
                    for (colIndex in (idColIndex + 1)..10) {
                        val cell = row.getCell(colIndex) ?: continue
                        val valorTexto = formatter.formatCellValue(cell, evaluator).trim()
                        if (valorTexto.isNotEmpty() && valorTexto != "0" && valorTexto.length > longitudMaxima) {
                            mejorDescripcion = valorTexto
                            longitudMaxima = valorTexto.length
                        }
                    }
                    if (mejorDescripcion.isNotEmpty() && mejorDescripcion.length > 5) {
                        val filaExcel = i + 1
                        listaTareasGenerales.add(ItemControl(filaExcel, idItem, mejorDescripcion))
                        // Respetar estado restaurado tras rotación; si no hay, inicializar
                        if (!resultadosInspeccion.containsKey(filaExcel))
                            resultadosInspeccion[filaExcel] = if (idItem == "43") "ABS" else "OK"
                    }
                }
            } else {
                for (i in config.filaInicio..config.filaFin) {
                    val row = sheet.getRow(i) ?: continue
                    val cellId = row.getCell(config.colId) ?: continue
                    val idRaw = formatter.formatCellValue(cellId, evaluator).replace(".0", "").trim()
                    if (idRaw.isEmpty() || !idRaw.matches(Regex("^[0-9]+$"))) continue
                    val cellDesc = row.getCell(config.colDesc) ?: continue
                    val descripcion = formatter.formatCellValue(cellDesc, evaluator).trim()
                        .replace("\n", " ")
                        .replace("\r", " ")
                    if (descripcion.length > 5) {
                        val filaExcel = i + 1
                        listaTareasGenerales.add(ItemControl(filaExcel, idRaw, descripcion))
                        if (!resultadosInspeccion.containsKey(filaExcel))
                            resultadosInspeccion[filaExcel] = "OK"
                    }
                }
            }

            workbook.close()
            inputStream.close()

            withContext(Dispatchers.Main) {
                if (listaTareasGenerales.isEmpty()) {
                    Toast.makeText(this@InspeccionActivity, "No se encontraron ítems en \"$nombrePlantilla\".", Toast.LENGTH_LONG).show()
                } else {
                    rvItems.layoutManager = LinearLayoutManager(this@InspeccionActivity)
                    rvItems.adapter = InspeccionAdapter(listaTareasGenerales, tipoInspeccion, resultadosInspeccion) { numeroFila, nuevoEstado ->
                        val item = listaTareasGenerales.find { it.numeroFilaExcel == numeroFila }
                        resultadosInspeccion[numeroFila] = nuevoEstado
                        // Solo abrir diálogo de observación si es Observado (no para ABS/EBS ni OK)
                        if (nuevoEstado.contains("Observado", ignoreCase = true) || nuevoEstado.contains("Reparación", ignoreCase = true)) {
                            mostrarDialogoObservacion(numeroFila, item?.descripcion ?: "Ítem $numeroFila")
                        } else {
                            textosObservaciones.remove(numeroFila)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("InspeccionActivity", "Error al cargar Excel", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@InspeccionActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun mostrarDialogoObservacion(fila: Int, descripcionItem: String) {
        ObservacionFlow.mostrarDialogo(
            activity = this,
            scope = lifecycleScope,
            descripcionItem = descripcionItem,
            textoExistente = textosObservaciones[fila] ?: ""
        ) { textoFinal ->
            textosObservaciones[fila] = textoFinal
        }
    }

    private suspend fun procesarYExportarAmbos() {
        try {
            val empresa       = intent.getStringExtra("EMPRESA") ?: ""
            val auditor       = intent.getStringExtra("AUDITOR") ?: nombreAuditor
            val marcaTractor  = intent.getStringExtra("MARCA_TRACTOR") ?: ""
            val modeloTractor = intent.getStringExtra("MODELO_TRACTOR") ?: ""
            val patenteTractor= intent.getStringExtra("PATENTE_TRACTOR") ?: ""
            val anioTractor   = intent.getStringExtra("ANIO_TRACTOR") ?: ""
            val combustible   = intent.getStringExtra("COMBUSTIBLE") ?: ""
            val marcaSemi     = intent.getStringExtra("MARCA_SEMI") ?: ""
            val modeloSemi    = intent.getStringExtra("MODELO_SEMI") ?: ""
            val patenteSemi   = intent.getStringExtra("PATENTE_SEMI") ?: ""
            val anioSemi      = intent.getStringExtra("ANIO_SEMI") ?: ""

            val urisAdjuntos = ArrayList<android.net.Uri>()

            // ── 1. EXCEL ──────────────────────────────────────────────────────
            val inputStream: InputStream = assets.open(nombrePlantilla())
            val workbook = HSSFWorkbook(inputStream)
            val sheet = workbook.getSheetAt(0)

            when (tipoInspeccion) {

                "LIVIANOS" -> {
                    sheet.escribirCeldaSegura(4, 4, empresa)
                    sheet.escribirCeldaSegura(4, 9, fechaHoy)
                    sheet.escribirCeldaSegura(6, 4, marcaTractor)
                    sheet.escribirCeldaSegura(7, 4, modeloTractor)
                    sheet.escribirCeldaSegura(8, 4, patenteTractor)
                    sheet.escribirCeldaSegura(9, 4, anioTractor)
                    sheet.escribirCeldaSegura(10, 4, combustible)
                    sheet.escribirCeldaSegura(6, 6, marcaSemi)
                    sheet.escribirCeldaSegura(7, 6, modeloSemi)
                    sheet.escribirCeldaSegura(8, 6, patenteSemi)
                    sheet.escribirCeldaSegura(9, 6, anioSemi)
                    for ((filaExcel, estado) in resultadosInspeccion) {
                        sheet.escribirCeldaSegura(filaExcel - 1, 10, estado)
                        if (estado.contains("Observado", ignoreCase = true) || estado.contains("Reparación", ignoreCase = true)) {
                            sheet.escribirCeldaSegura(filaExcel - 1, 11, textosObservaciones[filaExcel] ?: "")
                        }
                    }
                    sheet.forzarEscritura(59, 3, "Inspeccionado por :   $auditor                    Firma:")
                    // Firma PNG en filas 59-60 columnas G-J (índices 0-based: rows 58-59, cols 6-9)
                    sheet.addMergedRegion(CellRangeAddress(58, 59, 6, 9))
                    insertarFirmaEnExcel(workbook, assets, auditor, col1 = 6, row1 = 58, col2 = 10, row2 = 60)
                    sheet.forzarEscritura(61, 3, "Fecha:   $fechaHoy                                                       Lugar:   AVELLANEDA")
                    sheet.forzarEscritura(63, 3, "Fecha próxima inspección:   $fechaCaducidad")
                }

                "PESADOS", "COQUE" -> {
                    sheet.escribirCeldaSegura(4, 4, empresa)
                    sheet.escribirCeldaSegura(6, 4, marcaTractor)
                    sheet.escribirCeldaSegura(7, 4, modeloTractor)
                    sheet.escribirCeldaSegura(8, 4, patenteTractor)
                    sheet.escribirCeldaSegura(9, 4, anioTractor)
                    sheet.escribirCeldaSegura(6, 6, marcaSemi)
                    sheet.escribirCeldaSegura(7, 6, modeloSemi)
                    sheet.escribirCeldaSegura(8, 6, patenteSemi)
                    sheet.escribirCeldaSegura(9, 6, anioSemi)
                    for ((filaExcel, estado) in resultadosInspeccion) {
                        sheet.escribirCeldaSegura(filaExcel - 1, 10, estado)
                    }

                    if (tipoInspeccion == "PESADOS") {
                        // Fila 5 col J y Fila 43 col E — fecha realización
                        sheet.escribirCeldaSegura(4, 9, fechaHoy)
                        sheet.forzarEscritura(42, 4, fechaHoy)
                        // Fila 44 col F — fecha vencimiento
                        sheet.forzarEscritura(43, 5, fechaCaducidad)
                        // Fila 41 col D — auditor + firma (rich text preexistente)
                        sheet.forzarEscritura(40, 3, "Inspeccionado por :   $auditor                    Firma:    ……………………………………………………….")
                        // Merge G40:J41 y firma PNG
                        sheet.addMergedRegion(CellRangeAddress(39, 40, 6, 9))
                        insertarFirmaEnExcel(workbook, assets, auditor, col1 = 6, row1 = 39, col2 = 10, row2 = 41)

                    } else { // COQUE
                        // Fila 5 col J y Fila 32 col E — fecha realización
                        sheet.escribirCeldaSegura(4, 9, fechaHoy)
                        sheet.forzarEscritura(31, 4, fechaHoy)
                        // Fila 33 col F — fecha vencimiento
                        sheet.forzarEscritura(32, 5, fechaCaducidad)
                        // Fila 30 col D — auditor + firma (rich text preexistente)
                        sheet.forzarEscritura(29, 3, "Inspeccionado por :   $auditor                    Firma:    ……………………………………………………….")
                        // Merge G29:J30 y firma PNG
                        sheet.addMergedRegion(CellRangeAddress(28, 29, 6, 9))
                        insertarFirmaEnExcel(workbook, assets, auditor, col1 = 6, row1 = 28, col2 = 10, row2 = 30)
                    }
                }

                "QUIMICOS" -> {
                    sheet.escribirCeldaSegura(4, 4, empresa)
                    sheet.escribirCeldaSegura(6, 4, marcaTractor)
                    sheet.escribirCeldaSegura(7, 4, modeloTractor)
                    sheet.escribirCeldaSegura(8, 4, patenteTractor)
                    sheet.escribirCeldaSegura(9, 4, anioTractor)
                    sheet.escribirCeldaSegura(6, 6, marcaSemi)
                    sheet.escribirCeldaSegura(7, 6, modeloSemi)
                    sheet.escribirCeldaSegura(8, 6, patenteSemi)
                    sheet.escribirCeldaSegura(9, 6, anioSemi)
                    for ((filaExcel, estado) in resultadosInspeccion) {
                        sheet.escribirCeldaSegura(filaExcel - 1, 5, estado)
                    }
                    // Fila 3 col E — fecha realización
                    sheet.escribirCeldaSegura(2, 4, fechaHoy)

                    // ── Bloque de firma QUIMICOS ─────────────────────────────
                    // Eliminar TODOS los merges existentes en filas 38-41
                    for (idx in sheet.numMergedRegions - 1 downTo 0) {
                        val r = sheet.getMergedRegion(idx)
                        if (r.firstRow >= 38 && r.lastRow <= 41) {
                            sheet.removeMergedRegion(idx)
                        }
                    }

                    // Fila 39 col A-C: "Inspeccionado por: AUDITOR"
                    sheet.addMergedRegion(CellRangeAddress(38, 38, 0, 2))
                    sheet.forzarEscritura(38, 0, "Inspeccionado por :   $auditor")

                    // Fila 39-40 cols D-F: firma PNG
                    sheet.addMergedRegion(CellRangeAddress(38, 39, 3, 5))
                    insertarFirmaEnExcel(workbook, assets, auditor, col1 = 3, row1 = 38, col2 = 6, row2 = 40)

                    // Fila 40 cols A-C: Fecha
                    sheet.addMergedRegion(CellRangeAddress(39, 39, 0, 2))
                    sheet.forzarEscritura(39, 0, "Fecha:   $fechaHoy      Lugar:   AVELLANEDA")

                    // Fila 41 cols A-F: Fecha próxima inspección
                    sheet.addMergedRegion(CellRangeAddress(40, 40, 0, 5))
                    sheet.forzarEscritura(40, 0, "Fecha próxima inspección:   $fechaCaducidad")
                }
            }

            val nombreExcel = "${tipoInspeccion}_${patenteTractor}_${System.currentTimeMillis()}.xls"
            val uriExcel = MediaStoreUtils.guardarEnDescargas(this, nombreExcel, "application/vnd.ms-excel") { out ->
                workbook.write(out)
            }
            if (uriExcel != null) urisAdjuntos.add(uriExcel)
            workbook.close()
            inputStream.close()

            // ── 2. PDF ────────────────────────────────────────────────────────
            val filasEncabezado = listOf(
                "Empresa: $empresa" to if (tipoInspeccion == "LIVIANOS") "Combustible: $combustible" else null,
                "Tractor: $marcaTractor $modeloTractor" to "Semi: $marcaSemi $modeloSemi",
                "Patente Tractor: $patenteTractor ($anioTractor)" to "Patente Semi: $patenteSemi ($anioSemi)"
            )
            val itemsObservados = buildList {
                // Observación libre, sin fila de Excel asociada -- va primero para que se lea
                // antes del detalle ítem por ítem.
                if (estadoGeneralTexto.isNotBlank()) {
                    add(PdfReporteBuilder.ItemObservado(idItem = "-", descripcion = "Estado general de la unidad", detalle = estadoGeneralTexto))
                }
                addAll(
                    resultadosInspeccion
                        .filter { it.value.contains("Observado", ignoreCase = true) || it.value.contains("Reparación", ignoreCase = true) }
                        .keys.sorted()
                        .map { fila ->
                            val item = listaTareasGenerales.find { it.numeroFilaExcel == fila }
                            PdfReporteBuilder.ItemObservado(
                                idItem = item?.idItem ?: "-",
                                descripcion = item?.descripcion ?: "Fila $fila",
                                detalle = textosObservaciones[fila] ?: ""
                            )
                        }
                )
            }
            // Numeración correlativa real, compartida entre dispositivos, vía RPC de Postgres --
            // mismo patrón que Certificados/Calibrado. Sin try/catch propio: si falla, cae al
            // catch de procesarYExportarAmbos() y no se genera/guarda nada.
            val numeroReporte = SupabaseClientProvider.siguienteNumeroReporte()

            val pdfDocument = PdfReporteBuilder.generar(
                titulo = "REPORTE DE OBSERVACIONES - $tipoInspeccion",
                filasEncabezado = filasEncabezado,
                itemsObservados = itemsObservados,
                numeroReporte = numeroReporte
            )

            val nombrePdf = "Reporte_${tipoInspeccion}_${numeroReporte.toString().padStart(5, '0')}_${patenteTractor}_${System.currentTimeMillis()}.pdf"
            val uriPdf = MediaStoreUtils.guardarEnDescargas(this, nombrePdf, "application/pdf") { out ->
                pdfDocument.writeTo(out)
            }
            if (uriPdf != null) urisAdjuntos.add(uriPdf)
            pdfDocument.close()

            // Registrar el control en el historial local (para facturación y autocompletado futuro)
            InspeccionesLocalDatabase.getDatabase(this, lifecycleScope).controlRealizadoDao()
                .insert(controlRealizadoDesdeIntent(intent, tipoInspeccion))

            // Reflejarlo también en el historial compartido de core-data
            val historialServicioId = HistorialCompartidoHelper.registrar(
                context = this,
                tipoInspeccion = tipoInspeccion,
                empresa = empresa,
                auditor = auditor,
                patenteTractor = patenteTractor,
                patenteSemi = patenteSemi,
                documentoUriExcel = uriExcel,
                documentoUriPdf = uriPdf,
                detalle = "Nº de reporte: $numeroReporte"
            )

            // Fase 3 (5/6): si esta planilla vino de la sesión unificada, marcar el ítem
            // correspondiente como COMPLETO.
            val sesionItemId = intent.getLongExtra("sesionItemId", -1L)
            if (sesionItemId > 0 && historialServicioId > 0) {
                AppDatabase.getInstance(this).sesionServicioDao().marcarItemCompleto(sesionItemId, historialServicioId)
            }

            // Mail
            withContext(Dispatchers.Main) {
                Toast.makeText(this@InspeccionActivity, "Archivos generados. Abriendo siguiente...", Toast.LENGTH_SHORT).show()
                // Le mandamos los adjuntos al Gestor y cerramos esta pantalla
                GestorReportes.agregarAdjuntosYContinuar(this@InspeccionActivity, urisAdjuntos)
                finish()
            }

        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                Toast.makeText(this@InspeccionActivity, "Error al procesar archivos: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
