package com.aies.feature.inspecciones.ui

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import com.aies.feature.inspecciones.R
import com.aies.feature.inspecciones.data.ObservacionSugerida

/**
 * Diálogo de observaciones con:
 *  - Lista de sugerencias filtradas por palabras clave del ítem
 *  - Campo de texto libre para escribir o combinar
 *  - Botón para agregar nueva observación a la base de datos
 *
 * Uso:
 *   ObservacionesDialog(context, sugerencias, textoActual) { textoFinal, nuevaObs ->
 *       // textoFinal: lo que el auditor guardó
 *       // nuevaObs: observación nueva a guardar en BD (puede ser null)
 *   }.show()
 */
class ObservacionesDialog(
    context: Context,
    private val sugerencias: List<ObservacionSugerida>,
    private val textoActual: String,
    private val onGuardar: (textoFinal: String, nuevaObservacion: String?) -> Unit
) : Dialog(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_observaciones)

        val etTextoLibre   = findViewById<EditText>(R.id.et_observacion_libre)
        val lvSugerencias  = findViewById<ListView>(R.id.lv_sugerencias)
        val btnGuardar     = findViewById<Button>(R.id.btn_guardar_obs)
        val btnCancelar    = findViewById<Button>(R.id.btn_cancelar_obs)
        val cbGuardarNueva = findViewById<CheckBox>(R.id.cb_guardar_nueva)
        val tvTitulo       = findViewById<TextView>(R.id.tv_titulo_obs)

        tvTitulo.text = if (sugerencias.size > 3)
            "Sugerencias para este ítem" else "Observaciones"

        // Campo libre — pre-llenar con texto existente
        etTextoLibre.setText(textoActual)
        etTextoLibre.setSelection(textoActual.length)

        // Lista de sugerencias — al tocar una, la inserta/agrega al campo libre
        val adapter = ArrayAdapter(
            context, android.R.layout.simple_list_item_1,
            sugerencias.map { it.texto })
        lvSugerencias.adapter = adapter

        lvSugerencias.setOnItemClickListener { _, _, position, _ ->
            val sugerencia = sugerencias[position].texto
            val actual = etTextoLibre.text.toString().trim()
            etTextoLibre.setText(
                if (actual.isEmpty()) sugerencia else "$actual. $sugerencia"
            )
            etTextoLibre.setSelection(etTextoLibre.text.length)
        }

        // Mostrar checkbox de "guardar como nueva" solo si el texto libre
        // no coincide exactamente con ninguna sugerencia existente
        etTextoLibre.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val texto = s?.toString()?.trim() ?: ""
                val esNueva = texto.isNotEmpty() &&
                        sugerencias.none { it.texto.equals(texto, ignoreCase = true) }
                cbGuardarNueva.visibility = if (esNueva) View.VISIBLE else View.GONE
            }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })

        btnGuardar.setOnClickListener {
            val texto = etTextoLibre.text.toString().trim()
            if (texto.isEmpty()) {
                Toast.makeText(context, "Escribí o elegí una observación", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val nuevaAGuardar = if (cbGuardarNueva.isChecked) texto else null
            onGuardar(texto, nuevaAGuardar)
            dismiss()
        }

        btnCancelar.setOnClickListener { dismiss() }

        // Ancho completo
        window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.95).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
}
