package com.aies.feature.inspecciones.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.datepicker.MaterialDatePicker
import com.aies.feature.inspecciones.R
import com.aies.feature.inspecciones.data.ControlRealizado
import com.aies.feature.inspecciones.data.InspeccionesLocalDatabase
import com.aies.feature.inspecciones.excel.HistorialExcelBuilder
import com.aies.feature.inspecciones.util.MediaStoreUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Pantalla para exportar a Excel el historial de controles realizados,
 * filtrado por empresa y rango de fechas — pensada para armar la facturación.
 */
class HistorialActivity : AppCompatActivity() {

    private lateinit var db: InspeccionesLocalDatabase
    private lateinit var spinnerEmpresa: Spinner
    private lateinit var etDesde: EditText
    private lateinit var etHasta: EditText
    private lateinit var tvConteo: TextView

    private val fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private var fechaDesde: LocalDate = LocalDate.now().withDayOfMonth(1)
    private var fechaHasta: LocalDate = LocalDate.now()
    private var empresasDisponibles: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_historial)

        db = InspeccionesLocalDatabase.getDatabase(this, lifecycleScope)

        spinnerEmpresa = findViewById(R.id.spinner_empresa_historial)
        etDesde = findViewById(R.id.et_fecha_desde)
        etHasta = findViewById(R.id.et_fecha_hasta)
        tvConteo = findViewById(R.id.tv_conteo_historial)

        etDesde.setText(fechaDesde.format(fmt))
        etHasta.setText(fechaHasta.format(fmt))

        etDesde.setOnClickListener { elegirFecha(esDesde = true) }
        etHasta.setOnClickListener { elegirFecha(esDesde = false) }

        cargarEmpresas()

        findViewById<Button>(R.id.btn_exportar_historial).setOnClickListener {
            exportar()
        }
    }

    private fun cargarEmpresas() {
        lifecycleScope.launch(Dispatchers.IO) {
            val empresas = db.controlRealizadoDao().getEmpresasConControles()
            withContext(Dispatchers.Main) {
                empresasDisponibles = empresas
                val nombres = mutableListOf("Todas las empresas").apply { addAll(empresas) }
                val adapter = ArrayAdapter(this@HistorialActivity, android.R.layout.simple_spinner_item, nombres)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinnerEmpresa.adapter = adapter
                spinnerEmpresa.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                        actualizarConteo()
                    }
                    override fun onNothingSelected(parent: AdapterView<*>?) {}
                }
                actualizarConteo()
            }
        }
    }

    private fun elegirFecha(esDesde: Boolean) {
        val actual = if (esDesde) fechaDesde else fechaHasta
        val selMillis = actual.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()

        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(if (esDesde) "Desde" else "Hasta")
            .setSelection(selMillis)
            .build()

        picker.addOnPositiveButtonClickListener { seleccionUtcMillis ->
            val fecha = LocalDate.ofInstant(
                java.time.Instant.ofEpochMilli(seleccionUtcMillis),
                ZoneId.of("UTC")
            )
            if (esDesde) {
                fechaDesde = fecha
                etDesde.setText(fecha.format(fmt))
            } else {
                fechaHasta = fecha
                etHasta.setText(fecha.format(fmt))
            }
            actualizarConteo()
        }
        picker.show(supportFragmentManager, "date_picker_historial")
    }

    private fun rangoMillis(): Pair<Long, Long> {
        val zona = ZoneId.systemDefault()
        val desdeMillis = fechaDesde.atStartOfDay(zona).toInstant().toEpochMilli()
        val hastaMillis = fechaHasta.plusDays(1).atStartOfDay(zona).toInstant().toEpochMilli() - 1
        return desdeMillis to hastaMillis
    }

    private fun empresaSeleccionada(): String? {
        val seleccion = spinnerEmpresa.selectedItem?.toString() ?: return null
        return if (seleccion == "Todas las empresas") null else seleccion
    }

    private suspend fun buscarControles(): List<ControlRealizado> {
        val (desde, hasta) = rangoMillis()
        val todos = db.controlRealizadoDao().getEnRango(desde, hasta)
        val empresa = empresaSeleccionada()
        return if (empresa == null) todos else todos.filter { it.empresa == empresa }
    }

    private fun actualizarConteo() {
        lifecycleScope.launch(Dispatchers.IO) {
            val cantidad = buscarControles().size
            withContext(Dispatchers.Main) {
                tvConteo.text = "Se encontraron $cantidad controles en este período."
            }
        }
    }

    private fun exportar() {
        lifecycleScope.launch(Dispatchers.IO) {
            val controles = buscarControles()
            if (controles.isEmpty()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@HistorialActivity, "No hay controles en ese período/empresa.", Toast.LENGTH_LONG).show()
                }
                return@launch
            }

            val workbook = HistorialExcelBuilder.generar(controles)
            val nombreArchivo = "Historial_controles_${System.currentTimeMillis()}.xlsx"
            // MediaStoreUtils.guardarEnDescargas tira si el archivo no se pudo escribir de
            // verdad (ver doc de esa función) -- sin este try/catch eso crashearía la corrutina
            // en vez de mostrar el mismo "No se pudo generar el archivo." que ya cubría el caso
            // de uri == null.
            val uri = try {
                MediaStoreUtils.guardarEnDescargas(this@HistorialActivity, nombreArchivo, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") { out ->
                    workbook.write(out)
                }
            } catch (e: Exception) {
                null
            } finally {
                workbook.close()
            }

            withContext(Dispatchers.Main) {
                if (uri == null) {
                    Toast.makeText(this@HistorialActivity, "No se pudo generar el archivo.", Toast.LENGTH_LONG).show()
                    return@withContext
                }
                val compartir = Intent(Intent.ACTION_SEND).apply {
                    type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    putExtra(Intent.EXTRA_SUBJECT, "Historial de controles")
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(compartir, "Compartir historial"))
            }
        }
    }
}
