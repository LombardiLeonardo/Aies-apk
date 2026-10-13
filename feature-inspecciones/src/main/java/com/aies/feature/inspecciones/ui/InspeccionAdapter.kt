package com.aies.feature.inspecciones.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.aies.feature.inspecciones.R

// Estructura simple para manejar cada renglón
data class ItemControl(
    val numeroFilaExcel: Int,
    val idItem: String,
    val descripcion: String,
    val detalle: String = ""   // usado por AxionAdapter; ignorado por InspeccionAdapter
)

class InspeccionAdapter(
    private val listaItems: List<ItemControl>,
    private val tipoInspeccion: String = "LIVIANOS",
    private val resultados: Map<Int, String> = emptyMap(),
    private val alCambiarEstado: (Int, String) -> Unit
) : RecyclerView.Adapter<InspeccionAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvNumero: TextView = view.findViewById(R.id.tv_numero_item)
        val tvDescripcion: TextView = view.findViewById(R.id.tv_descripcion_item)
        val tvDetalle: TextView = view.findViewById(R.id.tv_detalle_item)
        val tvHint: TextView = view.findViewById(R.id.tv_expandir_hint)
        val rgEstado: RadioGroup = view.findViewById(R.id.rg_estado_item)
        val rbOk: RadioButton = view.findViewById(R.id.rb_ok)
        val rbReparacion: RadioButton = view.findViewById(R.id.rb_reparacion)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_inspeccion, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = listaItems[position]
        holder.tvNumero.text = item.idItem
        holder.tvDescripcion.text = item.descripcion

        // ── Detalle colapsable ────────────────────────────────────────
        if (item.detalle.isNotBlank()) {
            holder.tvDetalle.text = item.detalle
            holder.tvDetalle.visibility = View.GONE
            holder.tvHint.visibility = View.VISIBLE

            val toggle = View.OnClickListener {
                val expandido = holder.tvDetalle.visibility == View.VISIBLE
                holder.tvDetalle.visibility = if (expandido) View.GONE else View.VISIBLE
                holder.tvHint.text = if (expandido) "▼ ver" else "▲ ocultar"
            }
            holder.tvDescripcion.setOnClickListener(toggle)
            holder.tvNumero.setOnClickListener(toggle)
            holder.tvHint.setOnClickListener(toggle)
        } else {
            holder.tvDetalle.visibility = View.GONE
            holder.tvHint.visibility = View.GONE
            holder.tvDescripcion.setOnClickListener(null)
            holder.tvNumero.setOnClickListener(null)
        }

        // Ítem 43 en LIVIANOS → botones ABS / EBS en lugar de OK / Observado
        val esItemEspecial = tipoInspeccion == "LIVIANOS" && item.idItem == "43"

        if (esItemEspecial) {
            holder.rbOk.text = "ABS"
            holder.rbReparacion.text = "EBS"
            holder.rbReparacion.setTextColor(android.graphics.Color.parseColor("#1565C0")) // azul para EBS
        } else {
            holder.rbOk.text = "OK"
            holder.rbReparacion.text = "Observado"
            holder.rbReparacion.setTextColor(android.graphics.Color.GRAY)
        }

        holder.rgEstado.setOnCheckedChangeListener(null)

        // Restaurar estado visual según el mapa de resultados (sobrevive rotación)
        val estadoActual = resultados[item.numeroFilaExcel] ?: if (esItemEspecial) "ABS" else "OK"
        if (esItemEspecial) {
            holder.rbOk.isChecked = estadoActual != "EBS"
            holder.rbReparacion.isChecked = estadoActual == "EBS"
        } else {
            holder.rbOk.isChecked = estadoActual == "OK"
            holder.rbReparacion.isChecked = estadoActual != "OK"
            holder.rbReparacion.setTextColor(
                if (estadoActual != "OK") android.graphics.Color.RED
                else android.graphics.Color.GRAY
            )
        }

        holder.rgEstado.setOnCheckedChangeListener { _, checkedId ->
            if (esItemEspecial) {
                val seleccion = if (checkedId == R.id.rb_reparacion) "EBS" else "ABS"
                // EBS en azul, ABS en color normal
                holder.rbReparacion.setTextColor(
                    if (checkedId == R.id.rb_reparacion)
                        android.graphics.Color.parseColor("#1565C0")
                    else android.graphics.Color.GRAY
                )
                alCambiarEstado(item.numeroFilaExcel, seleccion)
            } else {
                val seleccion = if (checkedId == R.id.rb_reparacion) {
                    holder.rbReparacion.setTextColor(android.graphics.Color.RED)
                    "OBSERVADO"
                } else {
                    holder.rbReparacion.setTextColor(android.graphics.Color.GRAY)
                    "OK"
                }
                alCambiarEstado(item.numeroFilaExcel, seleccion)
            }
        }
        // Si también tenés botón de Reparación (para el checklist general):
        holder.rbReparacion?.setOnClickListener {
            if (holder.rbReparacion.isChecked) {
                alCambiarEstado(item.numeroFilaExcel, "Reparación")
            }
        }
    }

    override fun getItemCount(): Int = listaItems.size
}
