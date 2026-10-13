package com.aies.feature.inspecciones.ui

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.aies.coredata.AppDatabase
import com.aies.feature.inspecciones.R
import com.aies.feature.inspecciones.data.InspeccionesLocalDatabase
import com.aies.feature.inspecciones.data.MarcaSemi
import com.aies.feature.inspecciones.data.MarcaTractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DatosEquipoActivity : AppCompatActivity() {

    private lateinit var db: InspeccionesLocalDatabase
    private var nombreEmpresa: String = ""
    private var nombreAuditor: String = ""

    // Guardamos qué control eligió en la primera pantalla
    private var tipoInspeccionInicial: String = ""

    // Extras de feature-sesion (Fase 3, 5/6), propagados desde InspeccionesMenuActivity.
    // domicilioPrecargado/cuitPrecargado se aceptan por uniformidad con la firma de los otros
    // módulos, pero este formulario nunca tuvo esos campos (no hay "domicilio" ni "CUIT" en
    // ninguna pantalla de este módulo) — quedan sin usar a propósito, solo se re-propagan en
    // el Bundle hacia GestorReportes por si algún control futuro los necesita.
    private var sesionId: Long = -1L
    private var sesionItemId: Long = -1L
    private var unidadIdPrecargada: Long = -1L
    private var clientePrecargado: String = ""
    private var domicilioPrecargado: String = ""
    private var cuitPrecargado: String = ""
    private var auditorPrecargado: String = ""
    // Cambio 1/3: los 8 campos de tractor/semi ya se cargaron en Nueva Visita — si vienen
    // presentes (sesionId != -1L), se usan para precargar y bloquear estos mismos campos acá en
    // vez de volver a pedirlos (ver aplicarPrecargaDeSesion más abajo).
    private var patenteTractorPrecargado: String = ""
    private var marcaTractorPrecargado: String = ""
    private var anioTractorPrecargado: Int = -1
    private var modeloTractorPrecargado: String = ""
    private var patenteSemiPrecargado: String = ""
    private var marcaSemiPrecargado: String = ""
    private var anioSemiPrecargado: Int = -1
    private var modeloSemiPrecargado: String = ""

    // Componentes del Tractor
    private lateinit var spinnerMarcaTractor: Spinner
    private lateinit var etModeloTractor: EditText
    private lateinit var etPatenteTractor: EditText
    private lateinit var etAnioTractor: EditText
    private lateinit var rgCombustible: RadioGroup

    // Componentes del Semirremolque
    private lateinit var spinnerMarcaSemi: Spinner
    private lateinit var etModeloSemi: EditText
    private lateinit var etPatenteSemi: EditText
    private lateinit var etAnioSemi: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Recuperar los datos que vienen de la pantalla principal
        nombreEmpresa         = intent.getStringExtra("EMPRESA_SELECCIONADA") ?: ""
        nombreAuditor         = intent.getStringExtra("AUDITOR_SELECCIONADO") ?: ""
        tipoInspeccionInicial = intent.getStringExtra("TIPO_INSPECCION") ?: ""

        sesionId = intent.getLongExtra("sesionId", -1L)
        sesionItemId = intent.getLongExtra("sesionItemId", -1L)
        unidadIdPrecargada = intent.getLongExtra("unidadIdPrecargada", -1L)
        clientePrecargado = intent.getStringExtra("clientePrecargado") ?: ""
        domicilioPrecargado = intent.getStringExtra("domicilioPrecargado") ?: ""
        cuitPrecargado = intent.getStringExtra("cuitPrecargado") ?: ""
        auditorPrecargado = intent.getStringExtra("auditorPrecargado") ?: ""
        patenteTractorPrecargado = intent.getStringExtra("patenteTractorPrecargado") ?: ""
        marcaTractorPrecargado = intent.getStringExtra("marcaTractorPrecargado") ?: ""
        anioTractorPrecargado = intent.getIntExtra("anioTractorPrecargado", -1)
        modeloTractorPrecargado = intent.getStringExtra("modeloTractorPrecargado") ?: ""
        patenteSemiPrecargado = intent.getStringExtra("patenteSemiPrecargado") ?: ""
        marcaSemiPrecargado = intent.getStringExtra("marcaSemiPrecargado") ?: ""
        anioSemiPrecargado = intent.getIntExtra("anioSemiPrecargado", -1)
        modeloSemiPrecargado = intent.getStringExtra("modeloSemiPrecargado") ?: ""

        // Cambio 3 (ronda 2): "DATOS DEL TRACTOR/SEMIRREMOLQUE" (patente/marca/modelo/año) más
        // el checklist de "CONTROLES A REALIZAR" son exactamente los mismos datos que Nueva
        // Visita ya pidió (Cambio 1) y el tipo ya elegido en el checklist de la sesión — cuando
        // los 8 vienen completos no tiene sentido ni mostrarlos de nuevo grisados/deshabilitados:
        // se salta esta pantalla entera. El único dato sin equivalente en Nueva Visita es el
        // combustible; no hay otra fuente para inferirlo así que se asume DIESEL (el caso
        // ampliamente mayoritario) — si algún control necesita GNC específicamente, por ahora
        // hay que abrir Inspecciones fuera de una sesión, donde este formulario sigue intacto.
        if (sesionId != -1L && patenteTractorPrecargado.isNotBlank() && patenteSemiPrecargado.isNotBlank()) {
            continuarSinFormulario()
            return
        }

        setContentView(R.layout.activity_datos_equipo)

        // Inicializar Base de Datos
        db = InspeccionesLocalDatabase.getDatabase(this, lifecycleScope)

        // 2. Vincular componentes del diseño XML
        inicializarComponentes()

        // 3. Auto-marcar el control que el usuario ya eligió al iniciar la app
        premarcarControlInicial()

        // 4. Cargar las Marcas desde la Base de Datos a los Spinners
        observarMarcas()

        // 4b. Modo sesión: la unidad ya viene resuelta desde feature-sesion — precargamos la
        // patente del semirremolque (mismo campo que usa feature-calibrado para unidadIdPrecargada).
        if (unidadIdPrecargada > 0) precargarPatenteSemiDesdeUnidad()

        // Cambio 1/3: si estos 8 datos ya se cargaron en Nueva Visita (sesionId != -1L), se
        // precargan y se bloquean acá para no volver a pedirlos.
        if (sesionId != -1L) aplicarPrecargaDeSesion()

        // 5. Configurar botones para agregar nuevas marcas
        findViewById<Button>(R.id.btn_nueva_marca_tractor).setOnClickListener { mostrarDialogoAgregarMarca(true) }
        findViewById<Button>(R.id.btn_nueva_marca_semi).setOnClickListener { mostrarDialogoAgregarMarca(false) }

        // 5b. Autocompletar datos del equipo si ya conocemos esta patente de un control anterior
        etPatenteTractor.setOnFocusChangeListener { _, tieneFoco ->
            if (!tieneFoco) autocompletarPorPatente(esTractor = true)
        }
        etPatenteSemi.setOnFocusChangeListener { _, tieneFoco ->
            if (!tieneFoco) autocompletarPorPatente(esTractor = false)
        }

        // 6. Botón Continuar
        findViewById<Button>(R.id.btn_continuar_inspeccion).setOnClickListener {
            validarYContinuar()
        }
    }

    /**
     * Si ya se hizo un control a esta patente antes, completa marca/modelo/año/combustible
     * automáticamente. Nunca pisa datos que el usuario ya haya escrito a mano.
     */
    private fun autocompletarPorPatente(esTractor: Boolean) {
        val patente = (if (esTractor) etPatenteTractor else etPatenteSemi).text.toString().trim().uppercase()
        if (patente.isEmpty()) return

        val etModelo = if (esTractor) etModeloTractor else etModeloSemi
        if (etModelo.text.isNotBlank()) return // ya hay datos cargados a mano, no tocar

        lifecycleScope.launch(Dispatchers.IO) {
            val dao = db.controlRealizadoDao()
            val ultimo = if (esTractor) dao.getUltimoPorPatenteTractor(patente) else dao.getUltimoPorPatenteSemi(patente)
            if (ultimo == null) return@launch

            withContext(Dispatchers.Main) {
                if (etModelo.text.isNotBlank()) return@withContext // se completó mientras consultábamos

                if (esTractor) {
                    seleccionarEnSpinner(spinnerMarcaTractor, ultimo.marcaTractor)
                    etModeloTractor.setText(ultimo.modeloTractor)
                    etAnioTractor.setText(ultimo.anioTractor)
                    if (ultimo.combustible.equals("GNC", ignoreCase = true)) {
                        rgCombustible.check(R.id.rb_gnc)
                    } else {
                        rgCombustible.check(R.id.rb_diesel)
                    }
                } else {
                    seleccionarEnSpinner(spinnerMarcaSemi, ultimo.marcaSemi)
                    etModeloSemi.setText(ultimo.modeloSemi)
                    etAnioSemi.setText(ultimo.anioSemi)
                }
                Toast.makeText(
                    this@DatosEquipoActivity,
                    "Datos completados del último control a esta patente",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /** Cambio 3 (ronda 2): arma el mismo Bundle que [validarYContinuar] pero con los datos ya
     *  cargados en Nueva Visita, sin pasar por ningún campo de esta pantalla, y arranca
     *  [GestorReportes] directo. */
    private fun continuarSinFormulario() {
        val datosVehiculo = Bundle().apply {
            putString("EMPRESA", nombreEmpresa)
            putString("AUDITOR", nombreAuditor)
            putString("MARCA_TRACTOR", marcaTractorPrecargado)
            putString("MODELO_TRACTOR", modeloTractorPrecargado)
            putString("PATENTE_TRACTOR", patenteTractorPrecargado)
            putString("ANIO_TRACTOR", anioTractorPrecargado.toString())
            putString("COMBUSTIBLE", "DIESEL")
            putString("MARCA_SEMI", marcaSemiPrecargado)
            putString("MODELO_SEMI", modeloSemiPrecargado)
            putString("PATENTE_SEMI", patenteSemiPrecargado)
            putString("ANIO_SEMI", anioSemiPrecargado.toString())
            putLong("sesionId", sesionId)
            putLong("sesionItemId", sesionItemId)
            putLong("unidadIdPrecargada", unidadIdPrecargada)
            putString("clientePrecargado", clientePrecargado)
            putString("domicilioPrecargado", domicilioPrecargado)
            putString("cuitPrecargado", cuitPrecargado)
            putString("auditorPrecargado", auditorPrecargado)
        }
        GestorReportes.iniciar(this, listOf(tipoInspeccionInicial), datosVehiculo)
        finish()
    }

    /** Cambio 1/3: bloquea (deshabilita, en vez de ocultar — los EditText/Spinner del layout
     *  heredado no están pensados para desaparecer sin romper el resto del formulario) y
     *  precarga los campos de patente/modelo/año que Nueva Visita ya pidió, para no volver a
     *  preguntarlos acá. La marca se resuelve contra el spinner cuando su adapter ya cargó
     *  (ver observarMarcas). */
    private fun aplicarPrecargaDeSesion() {
        if (patenteTractorPrecargado.isNotBlank()) {
            etPatenteTractor.setText(patenteTractorPrecargado)
            etPatenteTractor.isEnabled = false
        }
        if (modeloTractorPrecargado.isNotBlank()) {
            etModeloTractor.setText(modeloTractorPrecargado)
            etModeloTractor.isEnabled = false
        }
        if (anioTractorPrecargado > 0) {
            etAnioTractor.setText(anioTractorPrecargado.toString())
            etAnioTractor.isEnabled = false
        }
        if (patenteSemiPrecargado.isNotBlank()) {
            etPatenteSemi.setText(patenteSemiPrecargado)
            etPatenteSemi.isEnabled = false
        }
        if (modeloSemiPrecargado.isNotBlank()) {
            etModeloSemi.setText(modeloSemiPrecargado)
            etModeloSemi.isEnabled = false
        }
        if (anioSemiPrecargado > 0) {
            etAnioSemi.setText(anioSemiPrecargado.toString())
            etAnioSemi.isEnabled = false
        }
    }

    private fun precargarPatenteSemiDesdeUnidad() {
        lifecycleScope.launch(Dispatchers.IO) {
            val unidad = AppDatabase.getInstance(this@DatosEquipoActivity).unidadDao().getPorId(unidadIdPrecargada) ?: return@launch
            withContext(Dispatchers.Main) {
                etPatenteSemi.setText(unidad.placa)
                autocompletarPorPatente(esTractor = false)
            }
        }
    }

    private fun seleccionarEnSpinner(spinner: Spinner, valor: String) {
        val adapter = spinner.adapter ?: return
        for (i in 0 until adapter.count) {
            if (adapter.getItem(i) == valor) {
                spinner.setSelection(i)
                return
            }
        }
    }

    private fun inicializarComponentes() {
        spinnerMarcaTractor = findViewById(R.id.spinner_marca_tractor)
        etModeloTractor     = findViewById(R.id.et_modelo_tractor)
        etPatenteTractor    = findViewById(R.id.et_patente_tractor)
        etAnioTractor       = findViewById(R.id.et_anio_tractor)
        rgCombustible       = findViewById(R.id.rg_combustible)

        spinnerMarcaSemi = findViewById(R.id.spinner_marca_semi)
        etModeloSemi     = findViewById(R.id.et_modelo_semi)
        etPatenteSemi    = findViewById(R.id.et_patente_semi)
        etAnioSemi       = findViewById(R.id.et_anio_semi)
    }

    // Esta función marca automáticamente el CheckBox correspondiente al control base
    private fun premarcarControlInicial() {
        when (tipoInspeccionInicial) {
            "LIVIANOS" -> findViewById<CheckBox>(R.id.cb_livianos).isChecked = true
            "PESADOS"  -> findViewById<CheckBox>(R.id.cb_pesados).isChecked = true
            "COQUE"    -> findViewById<CheckBox>(R.id.cb_coque).isChecked = true
            "QUIMICOS" -> findViewById<CheckBox>(R.id.cb_quimicos).isChecked = true
            "AXION"    -> findViewById<CheckBox>(R.id.cb_axion).isChecked = true
            "ENGANCHE" -> findViewById<CheckBox>(R.id.cb_enganche).isChecked = true
        }
    }

    private fun observarMarcas() {
        lifecycleScope.launch {
            db.inspeccionDao().getMarcasTractor().collect { lista ->
                val nombres = lista.map { it.nombre }
                val adapter = ArrayAdapter(this@DatosEquipoActivity, android.R.layout.simple_spinner_item, nombres)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinnerMarcaTractor.adapter = adapter
                if (marcaTractorPrecargado.isNotBlank()) {
                    seleccionarEnSpinner(spinnerMarcaTractor, marcaTractorPrecargado)
                    spinnerMarcaTractor.isEnabled = false
                }
            }
        }

        lifecycleScope.launch {
            db.inspeccionDao().getMarcasSemi().collect { lista ->
                val nombres = lista.map { it.nombre }
                val adapter = ArrayAdapter(this@DatosEquipoActivity, android.R.layout.simple_spinner_item, nombres)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinnerMarcaSemi.adapter = adapter
                if (marcaSemiPrecargado.isNotBlank()) {
                    seleccionarEnSpinner(spinnerMarcaSemi, marcaSemiPrecargado)
                    spinnerMarcaSemi.isEnabled = false
                }
            }
        }
    }

    private fun mostrarDialogoAgregarMarca(esTractor: Boolean) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(if (esTractor) "Agregar Marca de Tractor" else "Agregar Marca de Semi")

        val input = EditText(this)
        input.hint = "Ej. Scania / Randon"
        builder.setView(input)

        builder.setPositiveButton("Agregar") { _, _ ->
            val nombreMarca = input.text.toString().trim().uppercase()
            if (nombreMarca.isNotEmpty()) {
                lifecycleScope.launch(Dispatchers.IO) {
                    if (esTractor) {
                        db.inspeccionDao().insertMarcaTractor(MarcaTractor(nombreMarca))
                    } else {
                        db.inspeccionDao().insertMarcaSemi(MarcaSemi(nombreMarca))
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@DatosEquipoActivity, "Marca guardada", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        builder.setNegativeButton("Cancelar") { dialog, _ -> dialog.cancel() }
        builder.show()
    }

    private fun validarYContinuar() {
        val modeloTractor  = etModeloTractor.text.toString().trim()
        val patenteTractor = etPatenteTractor.text.toString().trim().uppercase()
        val anioTractor    = etAnioTractor.text.toString().trim()

        val modeloSemi  = etModeloSemi.text.toString().trim()
        val patenteSemi = etPatenteSemi.text.toString().trim().uppercase()
        val anioSemi    = etAnioSemi.text.toString().trim()

        if (modeloTractor.isEmpty() || patenteTractor.isEmpty() || anioTractor.isEmpty() ||
            modeloSemi.isEmpty() || patenteSemi.isEmpty() || anioSemi.isEmpty()) {
            Toast.makeText(this, "Por favor, completa todos los campos", Toast.LENGTH_LONG).show()
            return
        }

        // --- Recolectar Controles Seleccionados ---
        val controlesElegidos = mutableListOf<String>()
        if (findViewById<CheckBox>(R.id.cb_livianos).isChecked) controlesElegidos.add("LIVIANOS")
        if (findViewById<CheckBox>(R.id.cb_pesados).isChecked)  controlesElegidos.add("PESADOS")
        if (findViewById<CheckBox>(R.id.cb_coque).isChecked)    controlesElegidos.add("COQUE")
        if (findViewById<CheckBox>(R.id.cb_quimicos).isChecked) controlesElegidos.add("QUIMICOS")
        if (findViewById<CheckBox>(R.id.cb_axion).isChecked)    controlesElegidos.add("AXION")
        if (findViewById<CheckBox>(R.id.cb_enganche).isChecked) controlesElegidos.add("ENGANCHE")

        // Si destildaron todo por error, usamos el control que eligieron al principio para que no se trabe
        if (controlesElegidos.isEmpty()) {
            if (tipoInspeccionInicial.isNotEmpty()) {
                controlesElegidos.add(tipoInspeccionInicial)
            } else {
                Toast.makeText(this, "Seleccioná al menos un control a realizar", Toast.LENGTH_LONG).show()
                return
            }
        }

        val rbSeleccionado = findViewById<RadioButton>(rgCombustible.checkedRadioButtonId)
        val combustible = rbSeleccionado?.text?.toString() ?: "DIESEL"

        val marcaTractor = spinnerMarcaTractor.selectedItem?.toString() ?: ""
        val marcaSemi = spinnerMarcaSemi.selectedItem?.toString() ?: ""

        // --- Empaquetar todo en el Bundle para el Gestor ---
        val datosVehiculo = Bundle().apply {
            putString("EMPRESA", nombreEmpresa)
            putString("AUDITOR", nombreAuditor)
            putString("MARCA_TRACTOR", marcaTractor)
            putString("MODELO_TRACTOR", modeloTractor)
            putString("PATENTE_TRACTOR", patenteTractor)
            putString("ANIO_TRACTOR", anioTractor)
            putString("COMBUSTIBLE", combustible)
            putString("MARCA_SEMI", marcaSemi)
            putString("MODELO_SEMI", modeloSemi)
            putString("PATENTE_SEMI", patenteSemi)
            putString("ANIO_SEMI", anioSemi)
            // Fase 3 (5/6): GestorReportes reinyecta este mismo Bundle en cada Activity
            // siguiente (intent.putExtras(datosVehiculo)), así que sumar estos 7 extras acá
            // alcanza para que lleguen hasta InspeccionActivity/EngancheActivity sin tocar
            // GestorReportes.kt.
            putLong("sesionId", sesionId)
            putLong("sesionItemId", sesionItemId)
            putLong("unidadIdPrecargada", unidadIdPrecargada)
            putString("clientePrecargado", clientePrecargado)
            putString("domicilioPrecargado", domicilioPrecargado)
            putString("cuitPrecargado", cuitPrecargado)
            putString("auditorPrecargado", auditorPrecargado)
        }

        // Delegamos el flujo de pantallas al Gestor
        GestorReportes.iniciar(this, controlesElegidos, datosVehiculo)
    }
}
