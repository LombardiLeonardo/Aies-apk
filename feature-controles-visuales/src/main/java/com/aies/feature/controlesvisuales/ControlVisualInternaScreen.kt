package com.aies.feature.controlesvisuales

import android.content.Context
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aies.coredata.AppDatabase
import com.aies.feature.controlesvisuales.data.ControlVisualInterna
import com.aies.feature.controlesvisuales.data.ControlesVisualesLocalDatabase
import com.aies.feature.controlesvisuales.data.DetalleCompartimentoInterna
import com.aies.feature.controlesvisuales.data.HistorialCompartidoHelper
import com.aies.feature.controlesvisuales.data.toEmbed
import com.aies.feature.controlesvisuales.data.VerificacionCisternaInterna
import com.aies.feature.controlesvisuales.data.VerificacionInternaTextos
import com.aies.coreui.CampoTextoMayusculas
import com.aies.feature.controlesvisuales.pdf.ControlVisualInternaPdfBuilder
import com.aies.feature.controlesvisuales.util.MediaStoreUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** BUGFIX/hardening encontrado por EndToEndFlowTest: bajo ComposeTestRule, una continuación de
 *  coroutine que retoma después de un withContext(Dispatchers.IO) puede resumir en un hilo del
 *  pool de kotlinx.coroutines sin Looper propio (en vez del hilo principal real) -- llamar
 *  Toast.makeText(...).show() ahí revienta con NullPointerException("Can't toast on a thread
 *  that has not called Looper.prepare()"), y como esta pantalla no tenía el toast dentro de un
 *  try/catch propio, esa excepción quedaba sin atrapar y terminaba matando el Recomposer de la
 *  composición completa. No es solo un problema de test: es un caso real (aunque raro) donde
 *  cualquier hop de dispatcher fuera de lo esperado dejaría a la app sin poder avisar el
 *  resultado al usuario ni recuperarse. Esta guarda es un no-op en el uso real de la app (el
 *  hilo principal de Android siempre tiene Looper), y evita el crash en ambos casos. */
internal fun safeToast(context: Context, msg: String) {
    if (Looper.myLooper() == null) {
        android.util.Log.w("ControlesVisuales", "Toast omitido (sin Looper en este hilo): $msg")
        return
    }
    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
}

private class VerificacionCompState(val numero: Int) {
    var item1NoCumple by mutableStateOf(false); var item1Obs by mutableStateOf("")
    var item2NoCumple by mutableStateOf(false); var item2Obs by mutableStateOf("")
    var item3NoCumple by mutableStateOf(false); var item3Obs by mutableStateOf("")
    var item4NoCumple by mutableStateOf(false); var item4Obs by mutableStateOf("")
}

@Composable
fun ControlVisualInternaScreen(
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
    // auditorPrecargado se acepta por uniformidad con la firma que usa feature-sesion para los
    // otros módulos, pero ninguna de las 3 planillas de este módulo tiene un campo de auditor/
    // responsable en su encabezado (ver HistorialCompartidoHelper) — queda sin usar a propósito.
    // patenteTractorPrecargado/marcaTractorPrecargado/anioTractorPrecargado/modeloTractorPrecargado/
    // patenteSemiPrecargado/marcaSemiPrecargado/anioSemiPrecargado/modeloSemiPrecargado se aceptan
    // por uniformidad con Cambio 1, pero esta planilla no tiene campos de tractor/semirremolque
    // en su encabezado (solo dominio de la Unidad/cisterna) — quedan sin usar a propósito.
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var paso by remember { mutableIntStateOf(0) }
    var dominioBuscado by remember { mutableStateOf("") }
    var cantidad by remember { mutableIntStateOf(1) }
    var mensajeBusqueda by remember { mutableStateOf<String?>(null) }
    val encabezado = remember {
        EncabezadoState().apply {
            clientePrecargado?.let { razonSocial = it }
            domicilioPrecargado?.let { domicilio = it }
            cuitPrecargado?.let { cuit = it }
        }
    }
    val detalles = remember { mutableStateListOf<DetalleCompartimentoState>() }
    val verificaciones = remember { mutableStateListOf<VerificacionCompState>() }
    var generando by remember { mutableStateOf(false) }

    fun sincronizarListas() {
        while (detalles.size < cantidad) detalles.add(DetalleCompartimentoState(detalles.size + 1))
        while (detalles.size > cantidad) detalles.removeAt(detalles.size - 1)
        while (verificaciones.size < cantidad) verificaciones.add(VerificacionCompState(verificaciones.size + 1))
        while (verificaciones.size > cantidad) verificaciones.removeAt(verificaciones.size - 1)
    }

    // Modo sesión: la unidad ya viene resuelta desde feature-sesion, se salta la búsqueda manual.
    LaunchedEffect(unidadIdPrecargada) {
        val id = unidadIdPrecargada ?: return@LaunchedEffect
        val unidad = withContext(Dispatchers.IO) { AppDatabase.getInstance(context).unidadDao().getPorId(id) }
        if (unidad != null) {
            cantidad = cantidadCisternasPrecargada ?: unidad.cantidadCisternas
            encabezado.dominio = unidad.placa
            if (clientePrecargado == null) encabezado.razonSocial = unidad.cliente
            mensajeBusqueda = "Unidad precargada desde la sesión: $cantidad compartimento(s)."
            sincronizarListas()
        }
    }

    fun buscarUnidad() {
        scope.launch {
            val unidad = withContext(Dispatchers.IO) { AppDatabase.getInstance(context).unidadDao().getPorPlaca(dominioBuscado.trim()) }
            if (unidad != null) {
                cantidad = unidad.cantidadCisternas
                encabezado.dominio = unidad.placa
                encabezado.razonSocial = unidad.cliente
                mensajeBusqueda = "Unidad encontrada: ${unidad.cantidadCisternas} compartimento(s)."
            } else {
                encabezado.dominio = dominioBuscado.trim()
                mensajeBusqueda = "No se encontró la unidad; se usará la cantidad indicada manualmente."
            }
            sincronizarListas()
        }
    }

    fun generarPdf() {
        generando = true
        scope.launch {
            try {
                val enc = encabezado.aEncabezadoControl()
                val filas = detalles.map { it.aFila() }
                withContext(Dispatchers.IO) {
                    val db = ControlesVisualesLocalDatabase.getDatabase(context)
                    val dao = db.controlesVisualesDao()
                    val controlId = dao.insertInterna(ControlVisualInterna(encabezado = enc.toEmbed(), fechaCreadoTimestamp = System.currentTimeMillis()))

                    dao.insertDetalleInterna(detalles.map { d -> DetalleCompartimentoInterna(controlId = controlId, numero = d.numero, capacidad = d.capacidad, producto = d.productoFinal(), estado = d.estado) })
                    val verifDb = verificaciones.map { v ->
                        VerificacionCisternaInterna(
                            controlId = controlId, numeroCompartimento = v.numero,
                            item1Cumple = !v.item1NoCumple, item1Obs = v.item1Obs,
                            item2Cumple = !v.item2NoCumple, item2Obs = v.item2Obs,
                            item3Cumple = !v.item3NoCumple, item3Obs = v.item3Obs,
                            item4Cumple = !v.item4NoCumple, item4Obs = v.item4Obs
                        )
                    }
                    dao.insertVerificacionesInterna(verifDb)

                    val pdf = ControlVisualInternaPdfBuilder.generar(enc.toEmbed(), filas, verifDb)
                    val nombreArchivo = "control_visual_interna_${enc.dominio}_${System.currentTimeMillis()}.pdf"
                    val uri = MediaStoreUtils.guardarEnDescargas(context, nombreArchivo, "application/pdf") { pdf.writeTo(it) }
                    pdf.close()

                    val historialServicioId = HistorialCompartidoHelper.registrar(
                        context, HistorialCompartidoHelper.TIPO_CONTROL_VISUAL_INTERNA, "Control Visual Interna",
                        enc.razonSocial, enc.dominio, enc.tipoUnidad, cantidad, uri
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

    Column(modifier = Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Control Visual Interna", style = MaterialTheme.typography.titleLarge)
        Text("Paso ${paso + 1} de 4", style = MaterialTheme.typography.labelLarge)

        when (paso) {
            0 -> PasoDominio(dominioBuscado, { dominioBuscado = it }, cantidad, { cantidad = it; sincronizarListas() }, ::buscarUnidad, mensajeBusqueda)
            1 -> EncabezadoControlForm(encabezado, TipoControlVisual.INTERNA)
            2 -> DetalleCompartimentosSection(detalles)
            3 -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                verificaciones.forEach { v ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Compartimento N° ${v.numero}", style = MaterialTheme.typography.titleSmall)
                            ItemVerificacion(VerificacionInternaTextos.items[0], v.item1NoCumple, { v.item1NoCumple = it }, v.item1Obs) { v.item1Obs = it }
                            ItemVerificacion(VerificacionInternaTextos.items[1], v.item2NoCumple, { v.item2NoCumple = it }, v.item2Obs) { v.item2Obs = it }
                            ItemVerificacion(VerificacionInternaTextos.items[2], v.item3NoCumple, { v.item3NoCumple = it }, v.item3Obs) { v.item3Obs = it }
                            ItemVerificacion(VerificacionInternaTextos.items[3], v.item4NoCumple, { v.item4NoCumple = it }, v.item4Obs) { v.item4Obs = it }
                        }
                    }
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { if (paso == 0) onVolver() else paso-- }) { Text("← Volver") }
            Spacer(modifier = Modifier.weight(1f))
            if (paso < 3) Button(onClick = { paso++ }) { Text("Siguiente →") }
            else Button(enabled = !generando, onClick = { generarPdf() }) { Text(if (generando) "Generando…" else "📄 Generar PDF") }
        }
    }
}

@Composable
private fun ItemVerificacion(desc: String, noCumple: Boolean, onNoCumpleChange: (Boolean) -> Unit, obs: String, onObsChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(desc, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Switch(checked = noCumple, onCheckedChange = onNoCumpleChange)
        }
        if (noCumple) {
            CampoTextoMayusculas(value = obs, onValueChange = onObsChange, label = { Text("Observaciones") }, modifier = Modifier.fillMaxWidth())
        }
    }
}
