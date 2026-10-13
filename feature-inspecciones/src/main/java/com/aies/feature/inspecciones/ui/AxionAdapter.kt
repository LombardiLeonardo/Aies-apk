package com.aies.feature.inspecciones.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.aies.feature.inspecciones.R

class AxionAdapter(
    private val listaItems: List<ItemControl>,
    private val resultados: Map<Int, String> = emptyMap(),
    private val alCambiarEstado: (Int, String) -> Unit
) : RecyclerView.Adapter<AxionAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvNumero:      TextView    = view.findViewById(R.id.tv_numero_axion)
        val tvDescripcion: TextView    = view.findViewById(R.id.tv_descripcion_axion)
        val tvDetalle:     TextView    = view.findViewById(R.id.tv_detalle_axion)
        val tvHint:        TextView    = view.findViewById(R.id.tv_hint_axion)
        val rgEstado:      RadioGroup  = view.findViewById(R.id.rg_estado_axion)
        val rbOk:          RadioButton = view.findViewById(R.id.rb_ok_axion)
        val rbObs:         RadioButton = view.findViewById(R.id.rb_obs_axion)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_axion, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = listaItems[position]

        holder.tvNumero.text      = item.idItem
        holder.tvDescripcion.text = item.descripcion

        // ── Detalle colapsable ────────────────────────────────────
        if (item.detalle.isNotBlank()) {
            holder.tvDetalle.text       = item.detalle
            holder.tvDetalle.visibility = View.GONE
            holder.tvHint.visibility    = View.VISIBLE
            holder.tvHint.text          = "▼ VER"

            val toggle = View.OnClickListener {
                val expandido = holder.tvDetalle.visibility == View.VISIBLE
                holder.tvDetalle.visibility = if (expandido) View.GONE else View.VISIBLE
                holder.tvHint.text          = if (expandido) "▼ VER" else "▲ OCULTAR"
            }
            holder.tvDescripcion.setOnClickListener(toggle)
            holder.tvNumero.setOnClickListener(toggle)
            holder.tvHint.setOnClickListener(toggle)
        } else {
            holder.tvDetalle.visibility = View.GONE
            holder.tvHint.visibility    = View.GONE
            holder.tvDescripcion.setOnClickListener(null)
            holder.tvNumero.setOnClickListener(null)
        }

        // ── Radio buttons — restaurar estado al rotar ─────────────
        holder.rgEstado.setOnCheckedChangeListener(null)
        val estadoActual = resultados[item.numeroFilaExcel] ?: "OK"
        holder.rbOk.isChecked  = estadoActual == "OK"
        holder.rbObs.isChecked = estadoActual != "OK"
        holder.rbObs.setTextColor(
            if (estadoActual != "OK")
                android.graphics.Color.parseColor("#B71C1C")
            else
                android.graphics.Color.parseColor("#E65100")
        )

        holder.rgEstado.setOnCheckedChangeListener { _, checkedId ->
            val seleccion = if (checkedId == R.id.rb_obs_axion) {
                holder.rbObs.setTextColor(android.graphics.Color.parseColor("#B71C1C"))
                "OBSERVADO"
            } else {
                holder.rbObs.setTextColor(android.graphics.Color.parseColor("#E65100"))
                "OK"
            }
            alCambiarEstado(item.numeroFilaExcel, seleccion)
        }
        // --- AGREGAR ESTO ---
        // Si el botón ya está marcado y lo vuelven a tocar, reabrimos el menú
        holder.rbObs.setOnClickListener {
            if (holder.rbObs.isChecked) {
                alCambiarEstado(item.numeroFilaExcel, "OBSERVADO")
            }
        }
    }

    override fun getItemCount(): Int = listaItems.size
}
