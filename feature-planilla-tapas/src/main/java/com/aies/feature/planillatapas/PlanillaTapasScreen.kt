package com.aies.feature.planillatapas

import android.content.Context
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aies.coredata.AppDatabase
import com.aies.coreui.CampoTextoMayusculas
import com.aies.feature.planillatapas.data.DetalleCompartimentoTapas
import com.aies.feature.planillatapas.data.HistorialCompartidoHelper
import com.aies.feature.planillatapas.data.ItemChecklistTapas
import com.aies.feature.planillatapas.data.PlanillaTapasLocalDatabase
import com.aies.feature.planillatapas.data.PlanillaTapasRegistro
import com.aies.feature.planillatapas.data.PlanillaTapasSupabaseHelper
import com.aies.feature.planillatapas.data.SeedData
import com.aies.feature.planillatapas.pdf.PlanillaTapasPdfBuilder
import com.aies.feature.planillatapas.util.MediaStoreUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

/** Ver el mismo comentario en feature-repuestos.RepuestosScreen.safeToast: guarda contra un
 *  hilo sin Looper (relevante en tests instrumentados). */
internal fun safeToast(context: Context, msg: String) {
    if (Looper.myLooper() == null) {
        android.util.Log.w("PlanillaTapas", "Toast omitido (sin Looper en este hilo): $msg")
        return
    }
    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
}

/** Combina una hora "HH:mm" con la fecha DE HOY para armar un epoch millis completo (mismo
 *  criterio pedido para el horario de Calibrado). Devuelve null si el texto no tiene el formato
 *  esperado o está vacío. */
internal fun combinarFechaHora(horaTexto: String): Long? {
    val partes = horaTexto.trim().split(":")
    if (partes.size != 2) return null
    val h = partes[0].toIntOrNull() ?: return null
    val m = partes[1].toIntOrNull() ?: return null
    if (h !in 0..23 || m !in 0..59) return null
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, h)
    cal.set(Calendar.MINUTE, m)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

private class DetalleCompartimentoState(val numero: Int) {
    var ultimoProducto by mutableStateOf("")
    var medicionGases by mutableStateOf("")
}

private class ItemChecklistState(val codigo: String, val seccion: String, val descripcion: String, val numeroVisible: String, val orden: Int) {
    var noCumple by mutableStateOf(false)
    var observacion by mutableStateOf("")
}

/**
 * Servicio "Planilla de Control de Tapas de Cisternas" (R 11-01): un único paso (sin wizard,
 * scroll largo), mismo criterio que RepuestosScreen. A diferencia de Repuestos/Controles
 * Visuales, el encabezado SÍ incluye datos de tractor y semi (además de la cisterna), y además
 * de dual-writear a HistorialServicio esta pantalla escribe directo 3 tablas nuevas de Supabase
 * (ver PlanillaTapasSupabaseHelper) que todavía no tienen wrapper en core-data.
 */
@Composable
fun PlanillaTapasScreen(
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var dominio by remember { mutableStateOf(patenteSemiPrecargado ?: "") }
    var cliente by remember { mutableStateOf(clientePrecargado ?: "") }
    var domicilio by remember { mutableStateOf(domicilioPrecargado ?: "") }
    var cuit by remember { mutableStateOf(cuitPrecargado ?: "") }
    var auditor by remember { mutableStateOf(auditorPrecargado ?: "") }
    var tipoUnidad by remember { mutableStateOf("Semi") }
    var cantidad by remember { mutableStateOf(cantidadCisternasPrecargada ?: 1) }

    var fabricanteTapaPh by remember { mutableStateOf("") }
    var horaInicioTexto by remember { mutableStateOf("") }
    var horaFinTexto by remember { mutableStateOf("") }

    val detalles = remember { mutableStateListOf<DetalleCompartimentoState>() }
    val items = remember {
        mutableStateListOf<ItemChecklistState>().apply {
            SeedData.filas.forEachIndexed { orden, f -> add(ItemChecklistState(f.codigo, f.seccion, f.desc, f.numeroVisible, orden)) }
        }
    }
    var generando by remember { mutableStateOf(false) }

    fun sincronizarDetalles() {
        while (detalles.size < cantidad) detalles.add(DetalleCompartimentoState(detalles.size + 1))
        while (detalles.size > cantidad) detalles.removeAt(detalles.size - 1)
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
        sincronizarDetalles()
    }

    LaunchedEffect(cantidad) { sincronizarDetalles() }

    fun generarPdf() {
        generando = true
        scope.launch {
            try {
                val ahora = System.currentTimeMillis()
                val horaInicioMillis = combinarFechaHora(horaInicioTexto)
                val horaFinMillis = combinarFechaHora(horaFinTexto)

                withContext(Dispatchers.IO) {
                    val db = PlanillaTapasLocalDatabase.getDatabase(context)
                    val dao = db.planillaTapasDao()

                    val registroId = dao.insertRegistro(
                        PlanillaTapasRegistro(
                            unidadId = unidadIdPrecargada ?: 0,
                            dominio = dominio,
                            cliente = cliente,
                            auditor = auditor,
                            fechaCreadoTimestamp = ahora,
                            fabricanteTapaPh = fabricanteTapaPh,
                            horaInicio = horaInicioMillis,
                            horaFin = horaFinMillis
                        )
                    )

                    val detallesDb = detalles.map { d ->
                        DetalleCompartimentoTapas(
                            registroId = registroId, cisternaNumero = d.numero,
                            ultimoProductoTransportado = d.ultimoProducto,
                            medicionGasesLel = d.medicionGases.replace(",", ".").toDoubleOrNull()
                        )
                    }
                    dao.insertDetalles(detallesDb)

                    val itemsDb = items.map { i ->
                        ItemChecklistTapas(
                            registroId = registroId, codigo = i.codigo, seccion = i.seccion,
                            descripcion = i.descripcion, numeroVisible = i.numeroVisible, orden = i.orden,
                            cumple = !i.noCumple, observacion = i.observacion
                        )
                    }
                    dao.insertItemsChecklist(itemsDb)

                    val pdf = PlanillaTapasPdfBuilder.generar(
                        PlanillaTapasPdfBuilder.Encabezado(
                            fecha = ahora, horaInicio = horaInicioMillis, horaFin = horaFinMillis,
                            cliente = cliente, domicilio = domicilio, cuit = cuit, auditor = auditor,
                            fabricanteTapaPh = fabricanteTapaPh, dominioCisterna = dominio,
                            patenteTractor = patenteTractorPrecargado ?: "", marcaTractor = marcaTractorPrecargado ?: "",
                            anioTractor = anioTractorPrecargado ?: 0, modeloTractor = modeloTractorPrecargado ?: "",
                            patenteSemi = patenteSemiPrecargado ?: dominio, marcaSemi = marcaSemiPrecargado ?: "",
                            anioSemi = anioSemiPrecargado ?: 0, modeloSemi = modeloSemiPrecargado ?: ""
                        ),
                        detallesDb, itemsDb
                    )
                    val nombreArchivo = "planilla_tapas_${dominio}_${ahora}.pdf"
                    val uri = MediaStoreUtils.guardarEnDescargas(context, nombreArchivo, "application/pdf") { pdf.writeTo(it) }
                    pdf.close()

                    val historialServicioId = HistorialCompartidoHelper.registrar(
                        context, cliente, dominio, tipoUnidad, cantidad, auditor, uri
                    )

                    // Escritura directa a las 3 tablas nuevas de Supabase (planillas_tapas y sus
                    // 2 hijas) -- sin FK hacia HistorialServicio, quedan asociadas implícitamente
                    // por unidad+fecha. unidadIdPrecargada puede venir null si se abrió esta
                    // pantalla fuera del flujo de sesión; en ese caso se resuelve la Unidad real
                    // que HistorialCompartidoHelper.registrar() acaba de asegurar por dominio.
                    val unidadIdReal = unidadIdPrecargada
                        ?: AppDatabase.getInstance(context).unidadDao().getPorPlaca(dominio)?.id
                    if (unidadIdReal != null) {
                        PlanillaTapasSupabaseHelper.registrar(
                            unidadId = unidadIdReal, fecha = ahora, fabricanteTapaPh = fabricanteTapaPh,
                            horaInicio = horaInicioMillis, horaFin = horaFinMillis,
                            detalles = detallesDb, items = itemsDb
                        )
                    }

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

    val grupos = items.groupBy { it.seccion }.toList().sortedBy { (_, lista) -> lista.minOf { it.orden } }

    Column(modifier = Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Planilla de Control de Tapas de Cisternas (R 11-01)", style = MaterialTheme.typography.titleLarge)
        Text("Cliente: $cliente — Dominio: $dominio — $cantidad compartimento(s)", style = MaterialTheme.typography.bodyMedium)

        CampoTextoMayusculas(value = fabricanteTapaPh, onValueChange = { fabricanteTapaPh = it }, label = { Text("Fabricante Tapa PH. (homologado)") }, modifier = Modifier.fillMaxWidth())

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(value = horaInicioTexto, onValueChange = { horaInicioTexto = it }, label = { Text("Hora inicio (HH:mm)") }, modifier = Modifier.weight(1f), singleLine = true)
            OutlinedTextField(value = horaFinTexto, onValueChange = { horaFinTexto = it }, label = { Text("Hora fin (HH:mm)") }, modifier = Modifier.weight(1f), singleLine = true)
        }

        Text("Puntos de control por compartimiento", style = MaterialTheme.typography.titleMedium)
        detalles.forEachIndexed { idx, d ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Cisterna N° ${idx + 1}", style = MaterialTheme.typography.titleSmall)
                    CampoTextoMayusculas(value = d.ultimoProducto, onValueChange = { d.ultimoProducto = it }, label = { Text("Último producto transportado") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        value = d.medicionGases, onValueChange = { d.medicionGases = it },
                        label = { Text("Medición de gases % LEL (P12 Rev.01)") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                }
            }
        }

        Text("Checklist", style = MaterialTheme.typography.titleMedium)
        grupos.forEach { (seccion, lista) ->
            Text(seccion, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            lista.sortedBy { it.orden }.forEach { item ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(item.numeroVisible, color = Color.Gray)
                            Text(item.descripcion, modifier = Modifier.weight(1f))
                            Switch(checked = item.noCumple, onCheckedChange = { item.noCumple = it })
                        }
                        if (item.noCumple) {
                            CampoTextoMayusculas(value = item.observacion, onValueChange = { item.observacion = it }, label = { Text("Observación") }, modifier = Modifier.fillMaxWidth())
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
