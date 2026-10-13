package com.aies.feature.repuestos

import android.content.Context
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aies.coredata.AppDatabase
import com.aies.feature.repuestos.data.DetalleRepuestoCisterna
import com.aies.feature.repuestos.data.HistorialCompartidoHelper
import com.aies.feature.repuestos.data.RepuestoOptions
import com.aies.feature.repuestos.data.RepuestoRegistro
import com.aies.feature.repuestos.data.RepuestosLocalDatabase
import com.aies.feature.repuestos.pdf.RepuestosPdfBuilder
import com.aies.feature.repuestos.util.MediaStoreUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Ver el mismo comentario en feature-controles-visuales.ControlVisualInternaScreen.safeToast:
 *  guarda contra un hilo sin Looper (relevante en tests instrumentados). */
internal fun safeToast(context: Context, msg: String) {
    if (Looper.myLooper() == null) {
        android.util.Log.w("Repuestos", "Toast omitido (sin Looper en este hilo): $msg")
        return
    }
    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
}

/**
 * Servicio "Cambio de Repuestos": por cada compartimiento de la cisterna se marca a lo sumo
 * un repuesto cambiado (Ninguno / Juntas paso de hombre / Juntas de sensor / Kit de bulones).
 * Un único paso (sin wizard) — mismo criterio que ControlEspesoresScreen de
 * feature-controles-visuales, del que también se copia la firma de precarga uniforme que usa
 * feature-sesion para todos los módulos de servicio.
 */
@Composable
fun RepuestosScreen(
    onVolver: () -> Unit,
    sesionId: Long? = null,
    sesionItemId: Long? = null,
    unidadIdPrecargada: Long? = null,
    clientePrecargado: String? = null,
    domicilioPrecargado: String? = null,
    cuitPrecargado: String? = null,
    auditorPrecargado: String? = null,
    patenteTractorPrecargado: String? = null,
    marcaTractorPrecargado: String? = null,
    anioTractorPrecargado: Int? = null,
    modeloTractorPrecargado: String? = null,
    patenteSemiPrecargado: String? = null,
    marcaSemiPrecargado: String? = null,
    anioSemiPrecargado: Int? = null,
    modeloSemiPrecargado: String? = null,
    cantidadCisternasPrecargada: Int? = null
) {
    // domicilioPrecargado/cuitPrecargado se aceptan por uniformidad con la firma que usa
    // feature-sesion para los demás módulos, pero el PDF de este servicio no tiene domicilio
    // ni CUIT en su encabezado (solo fecha/cliente/dominio/auditor) — quedan sin usar a propósito.
    // patenteTractorPrecargado/marcaTractorPrecargado/anioTractorPrecargado/modeloTractorPrecargado/
    // marcaSemiPrecargado/anioSemiPrecargado/modeloSemiPrecargado se aceptan por la misma razón:
    // acá hay una sola Unidad relevante (la cisterna/semirremolque, vía patenteSemiPrecargado o
    // unidadIdPrecargada), no un par tractor+semi — quedan sin usar a propósito.
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var dominio by remember { mutableStateOf(patenteSemiPrecargado ?: "") }
    var cliente by remember { mutableStateOf(clientePrecargado ?: "") }
    var tipoUnidad by remember { mutableStateOf("Semi") }
    var cantidad by remember { mutableStateOf(cantidadCisternasPrecargada ?: 1) }
    // Un compartimiento puede llevar MÁS DE UN repuesto cambiado (por eso checkboxes, no
    // RadioButton) -- cada elemento es el conjunto de opciones tildadas para esa cisterna.
    // NINGUNO nunca se agrega al set: la ausencia de selección YA significa "ninguno".
    val seleccion = remember { mutableStateListOf<MutableSet<RepuestoOptions>>() }
    var generando by remember { mutableStateOf(false) }

    fun sincronizarSeleccion() {
        while (seleccion.size < cantidad) seleccion.add(mutableSetOf())
        while (seleccion.size > cantidad) seleccion.removeAt(seleccion.size - 1)
    }

    LaunchedEffect(unidadIdPrecargada) {
        val id = unidadIdPrecargada ?: return@LaunchedEffect
        val unidad = withContext(Dispatchers.IO) { AppDatabase.getInstance(context).unidadDao().getPorId(id) }
        if (unidad != null) {
            cantidad = cantidadCisternasPrecargada ?: unidad.cantidadCisternas
            dominio = unidad.placa
            tipoUnidad = unidad.tipoUnidad.ifBlank { "Semi" }
            if (clientePrecargado == null) cliente = unidad.cliente
        }
        sincronizarSeleccion()
    }

    LaunchedEffect(cantidad) { sincronizarSeleccion() }

    fun generarPdf() {
        generando = true
        scope.launch {
            try {
                val auditor = auditorPrecargado ?: ""
                withContext(Dispatchers.IO) {
                    val db = RepuestosLocalDatabase.getDatabase(context)
                    val dao = db.repuestosDao()
                    val ahora = System.currentTimeMillis()

                    val registroId = dao.insertRegistro(
                        RepuestoRegistro(dominio = dominio, cliente = cliente, auditor = auditor, fechaCreadoTimestamp = ahora)
                    )
                    val detalles = seleccion.flatMapIndexed { idx, opciones ->
                        if (opciones.isEmpty()) {
                            listOf(DetalleRepuestoCisterna(registroId = registroId, cisternaNumero = idx + 1, repuestoCambiado = RepuestoOptions.NINGUNO.valor))
                        } else {
                            opciones.map { opcion -> DetalleRepuestoCisterna(registroId = registroId, cisternaNumero = idx + 1, repuestoCambiado = opcion.valor) }
                        }
                    }
                    dao.insertDetalles(detalles)

                    val pdf = RepuestosPdfBuilder.generar(ahora, cliente, dominio, auditor, detalles)
                    val nombreArchivo = "repuestos_${dominio}_${ahora}.pdf"
                    val uri = MediaStoreUtils.guardarEnDescargas(context, nombreArchivo, "application/pdf") { pdf.writeTo(it) }
                    pdf.close()

                    // Detalle para la Nota de Trabajo: total de compartimientos donde se usó cada
                    // tipo de repuesto, uno por línea ("\n" -- NotaTrabajoPdfBuilder.wrapText
                    // corta por línea explícita antes de envolver por ancho), para poder
                    // identificar de un vistazo qué y cuánto cobrar. Solo los tipos realmente
                    // usados -- "Ninguno" no aporta nada a esa lectura.
                    val detalle = RepuestoOptions.entries
                        .filter { it != RepuestoOptions.NINGUNO }
                        .mapNotNull { opcion ->
                            val cantidadUsada = seleccion.count { it.contains(opcion) }
                            if (cantidadUsada > 0) "${opcion.etiqueta}: $cantidadUsada" else null
                        }
                        .joinToString("\n")

                    val historialServicioId = HistorialCompartidoHelper.registrar(
                        context, cliente, dominio, tipoUnidad, cantidad, auditor, uri, detalle.ifBlank { null }
                    )
                    if (sesionId != null && sesionItemId != null && historialServicioId > 0) {
                        AppDatabase.getInstance(context).sesionServicioDao().marcarItemCompleto(sesionItemId, historialServicioId)
                    }
                }
                safeToast(context, "✅ PDF generado en Descargas")
                onVolver()
            } catch (e: Exception) {
                safeToast(context, "❌ Error: ${e.message}")
            } finally {
                generando = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Cambio de Repuestos", style = MaterialTheme.typography.titleLarge)
        Text("Cliente: $cliente", style = MaterialTheme.typography.bodyMedium)
        Text("Dominio: $dominio — $cantidad compartimento(s)", style = MaterialTheme.typography.bodyMedium)
        Text(
            "Marcá, para cada compartimiento, los repuestos que se cambiaron (podés tildar más de uno).",
            style = MaterialTheme.typography.bodySmall
        )

        seleccion.forEachIndexed { idx, seleccionadas ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Compartimiento N° ${idx + 1}", style = MaterialTheme.typography.titleSmall)
                    RepuestoOptions.entries.filter { it != RepuestoOptions.NINGUNO }.forEach { opcion ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Checkbox(
                                checked = seleccionadas.contains(opcion),
                                onCheckedChange = { marcado ->
                                    val nuevo = seleccionadas.toMutableSet()
                                    if (marcado) nuevo.add(opcion) else nuevo.remove(opcion)
                                    seleccion[idx] = nuevo
                                }
                            )
                            Text(opcion.etiqueta, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { onVolver() }) { Text("← Volver") }
            Button(enabled = !generando, onClick = { generarPdf() }) { Text(if (generando) "Generando…" else "📄 Generar PDF") }
        }
    }
}
