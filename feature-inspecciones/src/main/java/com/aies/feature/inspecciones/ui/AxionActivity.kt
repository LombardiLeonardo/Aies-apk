package com.aies.feature.inspecciones.ui

import android.os.Bundle
import android.util.Log
import android.widget.*
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
import com.aies.feature.inspecciones.util.MediaStoreUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import java.io.InputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class AxionActivity : AppCompatActivity() {

    private lateinit var rvItems: RecyclerView
    private lateinit var btnFinalizar: Button
    private lateinit var btnEstadoGeneral: Button

    private val resultadosInspeccion = mutableMapOf<Int, String>()
    private val textosObservaciones  = mutableMapOf<Int, String>()
    private val listaTareas          = mutableListOf<ItemControl>()
    // Ver el mismo comentario en InspeccionActivity.kt -- observación libre, no atada a
    // ningún ítem numerado del checklist.
    private var estadoGeneralTexto: String = ""

    private var empresa:        String = ""
    private var auditor:        String = ""
    private var patenteTractor: String = ""
    private var patenteSemi:    String = ""

    private val fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val fechaHoy:       String by lazy { LocalDate.now().format(fmt) }
    private val fechaCaducidad: String by lazy { LocalDate.now().plusMonths(6).format(fmt) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_axion)

        empresa        = intent.getStringExtra("EMPRESA")         ?: ""
        auditor        = intent.getStringExtra("AUDITOR")         ?: ""
        patenteTractor = intent.getStringExtra("PATENTE_TRACTOR") ?: ""
        patenteSemi    = intent.getStringExtra("PATENTE_SEMI")    ?: ""

        rvItems      = findViewById(R.id.rv_items_axion)
        btnFinalizar = findViewById(R.id.btn_finalizar_axion)
        btnEstadoGeneral = findViewById(R.id.btn_estado_general_axion)

        // Restaurar estado tras rotación
        if (savedInstanceState != null) {
            @Suppress("UNCHECKED_CAST")
            val resultados = savedInstanceState.getSerializable("RESULTADOS") as? HashMap<Int, String>
            @Suppress("UNCHECKED_CAST")
            val observaciones = savedInstanceState.getSerializable("OBSERVACIONES") as? HashMap<Int, String>
            if (resultados != null) resultadosInspeccion.putAll(resultados)
            if (observaciones != null) textosObservaciones.putAll(observaciones)
            estadoGeneralTexto = savedInstanceState.getString("ESTADO_GENERAL", "")
        }
        actualizarBotonEstadoGeneral()

        lifecycleScope.launch(Dispatchers.IO) { cargarItems() }

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
                exportar()
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

    // ── Carga de ítems ────────────────────────────────────────────────────────
    private suspend fun cargarItems() {
        try {
            listaTareas.clear()
            val inputStream: InputStream = assets.open("Checklist_de_Camiones_AXION.xls")
            val workbook  = HSSFWorkbook(inputStream)
            val sheet     = workbook.getSheetAt(0)
            val formatter = DataFormatter()

            for (i in 12..50) {
                val row      = sheet.getRow(i) ?: continue
                val idCell   = row.getCell(1)  ?: continue
                val descCell = row.getCell(2)  ?: continue

                val id   = formatter.formatCellValue(idCell).trim()
                val desc = formatter.formatCellValue(descCell).trim()
                    .replace("\r\n", "\n").replace("\r", "\n").trimEnd()

                val detalleCell = row.getCell(3)
                val detalle = if (detalleCell != null)
                    formatter.formatCellValue(detalleCell).trim()
                        .replace("\r\n", "\n").replace("\r", "\n").trimEnd()
                else ""

                if (id.isEmpty() || desc.length < 3) continue

                val filaExcel = i + 1
                listaTareas.add(ItemControl(filaExcel, id, desc, detalle))
                // Respetar estado restaurado tras rotación
                if (!resultadosInspeccion.containsKey(filaExcel))
                    resultadosInspeccion[filaExcel] = "OK"
            }

            workbook.close()
            inputStream.close()

            withContext(Dispatchers.Main) {
                if (listaTareas.isEmpty()) {
                    Toast.makeText(this@AxionActivity, "No se encontraron ítems.", Toast.LENGTH_LONG).show()
                    return@withContext
                }
                rvItems.layoutManager = LinearLayoutManager(this@AxionActivity)
                rvItems.adapter = AxionAdapter(listaTareas, resultadosInspeccion) { numeroFila, nuevoEstado ->
                    resultadosInspeccion[numeroFila] = nuevoEstado
                    if (nuevoEstado.contains("OBSERVADO", ignoreCase = true)) {
                        val item = listaTareas.find { it.numeroFilaExcel == numeroFila }
                        mostrarDialogoObservacion(numeroFila, item?.descripcion ?: "Ítem $numeroFila")
                    } else {
                        textosObservaciones.remove(numeroFila)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("AxionActivity", "Error al cargar ítems", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@AxionActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ── Diálogo de observación ────────────────────────────────────────────────
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

    // ── Exportar XLS y Generar PDF ───────────────────────────────────────────
    private suspend fun exportar() {
        try {
            val urisAdjuntos = ArrayList<android.net.Uri>()

            // ==========================================
            // 1. GENERAR EL EXCEL ORIGINAL (.xls)
            // ==========================================
            val inputStream: InputStream = assets.open("Checklist_de_Camiones_AXION.xls")
            val workbook = HSSFWorkbook(inputStream)
            val sheet    = workbook.getSheetAt(0)

            sheet.escribirCeldaSegura(7, 1, "Transportista: $empresa")
            sheet.escribirCeldaSegura(7, 3, patenteTractor)
            sheet.escribirCeldaSegura(7, 5, fechaHoy)
            sheet.escribirCeldaSegura(8, 3, patenteSemi)
            sheet.escribirCeldaSegura(8, 5, fechaCaducidad)
            sheet.escribirCeldaSegura(9, 1, "Nombre del inspector: $auditor")

            for ((filaExcel, estado) in resultadosInspeccion) {
                val filaIdx = filaExcel - 1
                sheet.escribirCeldaSegura(filaIdx, 5, estado)
                if (estado.contains("OBSERVADO", ignoreCase = true)) {
                    val obs = textosObservaciones[filaExcel]
                    if (!obs.isNullOrBlank()) sheet.escribirCeldaSegura(filaIdx, 3, obs)
                }
            }

            val nombreArchivoXls = "AXION_${patenteTractor}_${System.currentTimeMillis()}.xls"
            val uriXls = MediaStoreUtils.guardarEnDescargas(this, nombreArchivoXls, "application/vnd.ms-excel") { out ->
                workbook.write(out)
            }
            if (uriXls != null) urisAdjuntos.add(uriXls)
            workbook.close()
            inputStream.close()

            // ==========================================
            // 2. GENERAR EL REPORTE EN PDF (SOLO OBSERVADOS)
            // ==========================================
            val filasEncabezado = listOf(
                "Empresa: $empresa" to "Fecha: $fechaHoy",
                "Auditor: $auditor" to "",
                "Patente Tractor: $patenteTractor" to "Patente Semi: $patenteSemi"
            )
            val itemsObservados = buildList {
                if (estadoGeneralTexto.isNotBlank()) {
                    add(PdfReporteBuilder.ItemObservado(idItem = "-", descripcion = "Estado general de la unidad", detalle = estadoGeneralTexto))
                }
                addAll(
                    resultadosInspeccion
                        .filter { it.value.contains("OBSERVADO", ignoreCase = true) }
                        .keys.sorted()
                        .map { fila ->
                            val item = listaTareas.find { it.numeroFilaExcel == fila }
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
            // catch de exportar() y no se genera/guarda nada.
            val numeroReporte = SupabaseClientProvider.siguienteNumeroReporte()

            val pdfDocument = PdfReporteBuilder.generar(
                titulo = "REPORTE DE OBSERVACIONES - AXION",
                filasEncabezado = filasEncabezado,
                itemsObservados = itemsObservados,
                numeroReporte = numeroReporte
            )

            val nombrePdf = "Reporte_AXION_${numeroReporte.toString().padStart(5, '0')}_${patenteTractor}_${System.currentTimeMillis()}.pdf"
            val uriPdf = MediaStoreUtils.guardarEnDescargas(this, nombrePdf, "application/pdf") { out ->
                pdfDocument.writeTo(out)
            }
            if (uriPdf != null) urisAdjuntos.add(uriPdf)
            pdfDocument.close()

            // Registrar el control en el historial local (para facturación y autocompletado futuro)
            InspeccionesLocalDatabase.getDatabase(this, lifecycleScope).controlRealizadoDao()
                .insert(controlRealizadoDesdeIntent(intent, "AXION"))

            // Reflejarlo también en el historial compartido de core-data
            val historialServicioId = HistorialCompartidoHelper.registrar(
                context = this,
                tipoInspeccion = "AXION",
                empresa = empresa,
                auditor = auditor,
                patenteTractor = patenteTractor,
                patenteSemi = patenteSemi,
                documentoUriExcel = uriXls,
                documentoUriPdf = uriPdf,
                detalle = "Nº de reporte: $numeroReporte"
            )

            // Bug real: a este Activity le faltaba por completo este paso -- por eso "Axion
            // Liviano" nunca marcaba el ítem de la visita como completo (a diferencia de
            // InspeccionActivity/EngancheActivity, que sí lo hacían).
            val sesionItemId = intent.getLongExtra("sesionItemId", -1L)
            if (sesionItemId > 0 && historialServicioId > 0) {
                AppDatabase.getInstance(this).sesionServicioDao().marcarItemCompleto(sesionItemId, historialServicioId)
            }

            // ==========================================
            // 3. ABRIR GMAIL CON AMBOS ARCHIVOS ADJUNTOS
            // ==========================================
            withContext(Dispatchers.Main) {
                Toast.makeText(this@AxionActivity, "Archivos de Axion generados.", Toast.LENGTH_SHORT).show()
                GestorReportes.agregarAdjuntosYContinuar(this@AxionActivity, urisAdjuntos)
                finish()
            }

        } catch (e: Exception) {
            Log.e("AxionActivity", "Error al exportar", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@AxionActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
