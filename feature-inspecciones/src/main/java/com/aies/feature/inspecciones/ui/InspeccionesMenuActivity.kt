package com.aies.feature.inspecciones.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.aies.feature.inspecciones.data.Auditor
import com.aies.feature.inspecciones.data.Empresa
import com.aies.feature.inspecciones.data.InspeccionesLocalDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Punto de entrada del módulo de inspecciones — ya no muestra ningún menú propio: el tipo de
 * control (LIVIANOS/PESADOS/COQUE/QUIMICOS/ENGANCHE/AXION) se elige en el checklist de "Nueva
 * Visita" de feature-sesion, que es el único caller de este Activity (no está exportado ni
 * tiene otra entrada en el repo — ver AndroidManifest.xml). Solo resuelve empresa/auditor
 * (ya conocidos por la sesión) contra el catálogo local y sigue directo a DatosEquipoActivity.
 */
class InspeccionesMenuActivity : AppCompatActivity() {

    private lateinit var db: InspeccionesLocalDatabase
    private var tipoInspeccionSeleccionado: String = ""

    // Extras de feature-sesion (Fase 3, 5/6): -1L/"" significa "no vino de una sesión", igual
    // que hoy al abrir este módulo desde el bottom nav. Se propagan tal cual hasta el punto
    // donde de verdad se escribe el HistorialServicio (ver InspeccionActivity/EngancheActivity).
    private var sesionId: Long = -1L
    private var sesionItemId: Long = -1L
    private var unidadIdPrecargada: Long = -1L
    private var clientePrecargado: String = ""
    private var domicilioPrecargado: String = ""
    private var cuitPrecargado: String = ""
    private var auditorPrecargado: String = ""
    // Cambio 1: 8 campos obligatorios de tractor/semi de la visita, propagados desde
    // feature-sesion — "" / -1 significa "no vino de una sesión".
    private var patenteTractorPrecargado: String = ""
    private var marcaTractorPrecargado: String = ""
    private var anioTractorPrecargado: Int = -1
    private var modeloTractorPrecargado: String = ""
    private var patenteSemiPrecargado: String = ""
    private var marcaSemiPrecargado: String = ""
    private var anioSemiPrecargado: Int = -1
    private var modeloSemiPrecargado: String = ""
    // Cambio 3: tipo ya elegido en el checklist de Nueva Visita.
    private var tipoInspeccionPrecargado: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
        tipoInspeccionPrecargado = intent.getStringExtra("tipoInspeccionPrecargado")

        db = InspeccionesLocalDatabase.getDatabase(this, lifecycleScope)

        val tipo = tipoInspeccionPrecargado
        if (tipo != null) {
            iniciarFlujo(tipo)
        } else {
            // No debería pasar nunca (este Activity no es exported y el único caller siempre
            // manda el tipo), pero si pasa no hay nada útil que mostrar.
            Toast.makeText(this, "Este módulo se abre desde una visita en Nueva Visita", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    /**
     * Empresa/auditor ya vienen resueltos por la sesión — solo se aseguran en el catálogo local
     * (para que HistorialActivity los pueda filtrar como siempre) y se sigue directo.
     */
    private fun iniciarFlujo(tipo: String) {
        tipoInspeccionSeleccionado = tipo
        val empresa = clientePrecargado.trim().uppercase()
        val auditor = auditorPrecargado.trim().uppercase()
        lifecycleScope.launch(Dispatchers.IO) {
            if (empresa.isNotBlank()) db.inspeccionDao().insertEmpresa(Empresa(empresa))
            if (auditor.isNotBlank()) db.inspeccionDao().insertAuditor(Auditor(auditor))
            withContext(Dispatchers.Main) { irADatosEquipo(empresa, auditor) }
        }
    }

    private fun irADatosEquipo(empresa: String, auditor: String) {
        val intent = Intent(this, DatosEquipoActivity::class.java)
        intent.putExtra("EMPRESA_SELECCIONADA", empresa)
        intent.putExtra("AUDITOR_SELECCIONADO", auditor)
        intent.putExtra("TIPO_INSPECCION", tipoInspeccionSeleccionado)
        // Fase 3 (5/6): seguimos propagando los extras de la sesión hop a hop hasta que
        // InspeccionActivity/EngancheActivity registren el HistorialServicio real.
        intent.putExtra("sesionId", sesionId)
        intent.putExtra("sesionItemId", sesionItemId)
        intent.putExtra("unidadIdPrecargada", unidadIdPrecargada)
        intent.putExtra("clientePrecargado", clientePrecargado)
        intent.putExtra("domicilioPrecargado", domicilioPrecargado)
        intent.putExtra("cuitPrecargado", cuitPrecargado)
        intent.putExtra("auditorPrecargado", auditorPrecargado)
        intent.putExtra("patenteTractorPrecargado", patenteTractorPrecargado)
        intent.putExtra("marcaTractorPrecargado", marcaTractorPrecargado)
        intent.putExtra("anioTractorPrecargado", anioTractorPrecargado)
        intent.putExtra("modeloTractorPrecargado", modeloTractorPrecargado)
        intent.putExtra("patenteSemiPrecargado", patenteSemiPrecargado)
        intent.putExtra("marcaSemiPrecargado", marcaSemiPrecargado)
        intent.putExtra("anioSemiPrecargado", anioSemiPrecargado)
        intent.putExtra("modeloSemiPrecargado", modeloSemiPrecargado)
        startActivity(intent)
        finish()
    }
}
