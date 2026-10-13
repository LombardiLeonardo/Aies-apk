package com.aies.feature.inspecciones.ui

import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.aies.coredata.AppDatabase
import com.aies.feature.inspecciones.R
import com.aies.feature.inspecciones.data.HistorialCompartidoHelper
import com.aies.feature.inspecciones.data.InspeccionesLocalDatabase
import com.aies.feature.inspecciones.data.controlRealizadoDesdeIntent
import com.aies.feature.inspecciones.excel.escribirCeldaSegura
import com.aies.feature.inspecciones.util.MediaStoreUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import java.io.InputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.ArrayList

class EngancheActivity : AppCompatActivity() {

    private var empresa: String = ""
    private var auditor: String = ""
    private var patenteTractor: String = ""
    private var patenteSemi: String = ""

    private val fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val fechaHoy: String by lazy { LocalDate.now().format(fmt) }

    // ── Perno de Anclaje ──────────────────────────────────────────
    private lateinit var etMarcaPerno: EditText
    private lateinit var etModeloPerno: EditText
    private lateinit var etObsPerno: EditText
    private lateinit var rgPerno: RadioGroup
    private lateinit var rbPernoApto: RadioButton
    private lateinit var rbPernoNoApto: RadioButton

    // ── Placa de Apoyo ────────────────────────────────────────────
    private lateinit var rgFijacionPlaca: RadioGroup
    private lateinit var rbAbulonado: RadioButton
    private lateinit var rbSoldado: RadioButton
    private lateinit var etObsPlaca: EditText
    private lateinit var rgPlaca: RadioGroup
    private lateinit var rbPlacaApta: RadioButton
    private lateinit var rbPlacaNoApta: RadioButton

    // ── Base Corrugada ────────────────────────────────────────────
    private lateinit var etObsBase: EditText
    private lateinit var rgBase: RadioGroup
    private lateinit var rbBaseApta: RadioButton
    private lateinit var rbBaseNoApta: RadioButton

    // ── Plato ─────────────────────────────────────────────────────
    private lateinit var etMarcaPlato: EditText
    private lateinit var etModeloPlato: EditText
    private lateinit var etNumeroPlato: EditText
    private lateinit var etObsPlato: EditText
    private lateinit var rgPlato: RadioGroup
    private lateinit var rbPlatoApto: RadioButton
    private lateinit var rbPlatoNoApto: RadioButton

    // ── Liberación final ──────────────────────────────────────────
    private lateinit var rgLiberacion: RadioGroup
    private lateinit var rbLiberacionSi: RadioButton
    private lateinit var rbLiberacionNo: RadioButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_enganche)

        empresa       = intent.getStringExtra("EMPRESA") ?: ""
        auditor       = intent.getStringExtra("AUDITOR") ?: ""
        patenteTractor= intent.getStringExtra("PATENTE_TRACTOR") ?: ""
        patenteSemi   = intent.getStringExtra("PATENTE_SEMI") ?: ""

        vincularComponentes()

        findViewById<Button>(R.id.btn_finalizar_enganche).setOnClickListener {
            if (validar()) {
                lifecycleScope.launch(Dispatchers.IO) { exportar() }
            }
        }
    }

    private fun vincularComponentes() {
        // Perno
        etMarcaPerno   = findViewById(R.id.et_marca_perno)
        etModeloPerno  = findViewById(R.id.et_modelo_perno)
        etObsPerno     = findViewById(R.id.et_obs_perno)
        rgPerno        = findViewById(R.id.rg_perno)
        rbPernoApto    = findViewById(R.id.rb_perno_apto)
        rbPernoNoApto  = findViewById(R.id.rb_perno_no_apto)
        // Placa
        rgFijacionPlaca= findViewById(R.id.rg_fijacion_placa)
        rbAbulonado    = findViewById(R.id.rb_abulonado)
        rbSoldado      = findViewById(R.id.rb_soldado)
        etObsPlaca     = findViewById(R.id.et_obs_placa)
        rgPlaca        = findViewById(R.id.rg_placa)
        rbPlacaApta    = findViewById(R.id.rb_placa_apta)
        rbPlacaNoApta  = findViewById(R.id.rb_placa_no_apta)
        // Base
        etObsBase      = findViewById(R.id.et_obs_base)
        rgBase         = findViewById(R.id.rg_base)
        rbBaseApta     = findViewById(R.id.rb_base_apta)
        rbBaseNoApta   = findViewById(R.id.rb_base_no_apta)
        // Plato
        etMarcaPlato   = findViewById(R.id.et_marca_plato)
        etModeloPlato  = findViewById(R.id.et_modelo_plato)
        etNumeroPlato  = findViewById(R.id.et_numero_plato)
        etObsPlato     = findViewById(R.id.et_obs_plato)
        rgPlato        = findViewById(R.id.rg_plato)
        rbPlatoApto    = findViewById(R.id.rb_plato_apto)
        rbPlatoNoApto  = findViewById(R.id.rb_plato_no_apto)
        // Liberación
        rgLiberacion   = findViewById(R.id.rg_liberacion)
        rbLiberacionSi = findViewById(R.id.rb_liberacion_si)
        rbLiberacionNo = findViewById(R.id.rb_liberacion_no)
    }

    private fun validar(): Boolean {
        if (etMarcaPerno.text.isBlank() || etModeloPerno.text.isBlank()) {
            Toast.makeText(this, "Completá Marca y Modelo del Perno de Anclaje", Toast.LENGTH_SHORT).show()
            return false
        }
        if (etMarcaPlato.text.isBlank() || etModeloPlato.text.isBlank() || etNumeroPlato.text.isBlank()) {
            Toast.makeText(this, "Completá Marca, Modelo y Número del Plato", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private suspend fun exportar() {
        try {
            val inputStream: InputStream = assets.open("R_10-01_Planilla_Verificacion_Sistema_de_Enganche_Rev_04.xls")
            val workbook = HSSFWorkbook(inputStream)
            val sheet = workbook.getSheetAt(0)

            fun escribir(filaIdx: Int, colIdx: Int, valor: String) = sheet.escribirCeldaSegura(filaIdx, colIdx, valor)

            fun tilde(apto: Boolean) = if (apto) "☑" else " "

            // ── Cabecera ──────────────────────────────────────────
            escribir(5, 3, fechaHoy)                         // Fila 6  col D — Fecha
            escribir(5, 7, patenteTractor)                   // Fila 6  col H — Pat. Tractor
            escribir(6, 7, patenteSemi)                      // Fila 7  col H — Pat. Semi

            // ── Perno de Anclaje ──────────────────────────────────
            escribir(9,  2, etMarcaPerno.text.toString().trim().uppercase())   // Fila 10 col C
            escribir(9,  5, etModeloPerno.text.toString().trim().uppercase())  // Fila 10 col F
            val pernoApto = rgPerno.checkedRadioButtonId == R.id.rb_perno_apto
            escribir(12, 7, tilde(pernoApto))                // Fila 13 col H — APTO
            escribir(12, 8, tilde(!pernoApto))               // Fila 13 col I — NO APTO
            if (etObsPerno.text.isNotBlank())
                escribir(15, 1, "Observacion:"+etObsPerno.text.toString().trim())  // Fila 16 col B

            // ── Placa de Apoyo ────────────────────────────────────
            val esAbulonado = rgFijacionPlaca.checkedRadioButtonId == R.id.rb_abulonado
            escribir(18, 2, if (esAbulonado) "X" else " ")  // Fila 19 col C — Abulonado
            escribir(19, 2, if (!esAbulonado) "X" else " ") // Fila 20 col C — Soldado
            val placaApta = rgPlaca.checkedRadioButtonId == R.id.rb_placa_apta
            escribir(21, 7, tilde(placaApta))
            escribir(21, 8, tilde(!placaApta))
            if (etObsPlaca.text.isNotBlank())
                escribir(24, 1, "Observacion: " + etObsPlaca.text.toString().trim())

            // ── Base Corrugada ────────────────────────────────────
            val baseApta = rgBase.checkedRadioButtonId == R.id.rb_base_apta
            escribir(30, 7, tilde(baseApta))
            escribir(30, 8, tilde(!baseApta))
            if (etObsBase.text.isNotBlank())
                escribir(33, 1, "Observación: " + etObsBase.text.toString().trim())

            // ── Plato ─────────────────────────────────────────────
            escribir(36, 2, etMarcaPlato.text.toString().trim().uppercase())   // Fila 37 col C — Marca
            escribir(36, 4, etModeloPlato.text.toString().trim().uppercase())  // Fila 37 col E — Modelo
            escribir(36, 7, etNumeroPlato.text.toString().trim())              // Fila 37 col H — Número
            val platoApto = rgPlato.checkedRadioButtonId == R.id.rb_plato_apto
            escribir(38, 7, tilde(platoApto))                // Fila 38 col H
            escribir(38, 8, tilde(!platoApto))               // Fila 38 col I
            if (etObsPlato.text.isNotBlank())
                escribir(41, 1, etObsPlato.text.toString().trim())  // Fila 42 col B

            // ── Liberación final ──────────────────────────────────
            val liberado = rgLiberacion.checkedRadioButtonId == R.id.rb_liberacion_si
            escribir(42, 7, tilde(liberado))   // Fila 43 col H — SI
            escribir(42, 8, tilde(!liberado))  // Fila 43 col I — NO
            // Si está liberado, tildar también devolución docs, retiro tacos, acoplamiento, disposición trapos
            if (liberado) {
                escribir(44, 7, "☑")  // Fila 45 col H — Devolución documentación
                escribir(45, 7, "☑")  // Fila 46 col H — Retiro tacos antideslizantes
                escribir(46, 7, "☑")  // Fila 47 col H — Verificación acoplamiento
                escribir(47, 7, "☑")  // Fila 48 col H — Disposición trapos

            } else (!liberado)
                escribir(44, 8, "☑")
                escribir(45, 8, "☑")
                escribir(46, 8, "☑")
                escribir(47, 8, "☑")



            // ── Guardar Excel ─────────────────────────────────────
            val nombreExcel = "ENGANCHE_${patenteTractor}_${System.currentTimeMillis()}.xls"
            val urisAdjuntos = ArrayList<android.net.Uri>()
            val uriExcel = MediaStoreUtils.guardarEnDescargas(this, nombreExcel, "application/vnd.ms-excel") { out ->
                workbook.write(out)
            }
            if (uriExcel != null) urisAdjuntos.add(uriExcel)
            workbook.close()
            inputStream.close()

            // Registrar el control en el historial local (para facturación y autocompletado futuro)
            InspeccionesLocalDatabase.getDatabase(this, lifecycleScope).controlRealizadoDao()
                .insert(controlRealizadoDesdeIntent(intent, "ENGANCHE"))

            // Reflejarlo también en el historial compartido de core-data
            val historialServicioId = HistorialCompartidoHelper.registrar(
                context = this,
                tipoInspeccion = "ENGANCHE",
                empresa = empresa,
                auditor = auditor,
                patenteTractor = patenteTractor,
                patenteSemi = patenteSemi,
                documentoUriExcel = uriExcel,
                documentoUriPdf = null
            )

            // Fase 3 (5/6): si esta planilla vino de la sesión unificada, marcar el ítem
            // correspondiente como COMPLETO.
            val sesionItemId = intent.getLongExtra("sesionItemId", -1L)
            if (sesionItemId > 0 && historialServicioId > 0) {
                AppDatabase.getInstance(this).sesionServicioDao().marcarItemCompleto(sesionItemId, historialServicioId)
            }

            // ── Enviar por email ──────────────────────────────────
            withContext(Dispatchers.Main) {
                Toast.makeText(this@EngancheActivity, "Archivos generados. Abriendo siguiente...", Toast.LENGTH_SHORT).show()
                // Le mandamos los adjuntos al Gestor y cerramos esta pantalla
                GestorReportes.agregarAdjuntosYContinuar(this@EngancheActivity, urisAdjuntos)
                finish()
            }
        } catch (e: Exception) {
            Log.e("EngancheActivity", "Error al exportar", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@EngancheActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

}
