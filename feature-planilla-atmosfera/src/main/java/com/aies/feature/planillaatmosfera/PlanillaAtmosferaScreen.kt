package com.aies.feature.planillaatmosfera

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.aies.coredata.AppDatabase
import com.aies.coreui.CampoTextoMayusculas
import com.aies.feature.planillaatmosfera.data.AtmosferaCisterna
import com.aies.feature.planillaatmosfera.data.HistorialCompartidoHelper
import com.aies.feature.planillaatmosfera.data.PlanillaAtmosferaLocalDatabase
import com.aies.feature.planillaatmosfera.data.PlanillaAtmosferaRegistro
import com.aies.feature.planillaatmosfera.data.PlanillaAtmosferaSupabaseHelper
import com.aies.feature.planillaatmosfera.pdf.PlanillaAtmosferaPdfBuilder
import com.aies.feature.planillaatmosfera.util.MediaStoreUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun safeToast(context: Context, msg: String) {
    if (Looper.myLooper() == null) {
        android.util.Log.w("PlanillaAtmosfera", "Toast omitido (sin Looper en este hilo): $msg")
        return
    }
    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
}

private fun String.aDouble(): Double? = replace(",", ".").trim().toDoubleOrNull()

private class CisternaState(val numero: Int) {
    var lel by mutableStateOf("")
    var o2 by mutableStateOf("")
    var co by mutableStateOf("")
    var h2s by mutableStateOf("")
}

/** Alerta visual NO bloqueante: O2 aceptable entre 19,5 y 23; LEL/CO/H2S en 0. */
private fun fueraDeRango(valor: String, esO2: Boolean): Boolean {
    val v = valor.aDouble() ?: return false
    return if (esO2) v < 19.5 || v > 23.0 else v != 0.0
}

/**
 * Servicio "Planilla de Control de Atmósfera de Tanques Cisterna" (R 12-01 Rev.01): un único
 * paso (sin wizard), mismo patrón que PlanillaTapasScreen. Transportista/tractor/tanque vienen
 * precargados de la sesión; la cantidad de cisternas sale de la Unidad (no se hardcodea).
 */
@Composable
fun PlanillaAtmosferaScreen(
    onVolver: () -> Unit,
    sesionId: Long? = null,
    sesionItemId: Long? = null,
    unidadIdPrecargada: Long? = null,
    clientePrecargado: String? = null,
    auditorPrecargado: String? = null,
    patenteTractorPrecargado: String? = null,
    patenteSemiPrecargado: String? = null,
    cantidadCisternasPrecargada: Int? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var dominio by remember { mutableStateOf(patenteSemiPrecargado ?: "") }
    var cliente by remember { mutableStateOf(clientePrecargado ?: "") }
    var tipoUnidad by remember { mutableStateOf("Semi") }
    var cantidad by remember { mutableStateOf(cantidadCisternasPrecargada ?: 1) }

    var tipoEstanqueidad by remember { mutableStateOf(false) }
    var tipoIntegridad by remember { mutableStateOf(false) }
    var tipoEspacios by remember { mutableStateOf(false) }
    var tipoOtros by remember { mutableStateOf(false) }
    var tipoOtrosDetalle by remember { mutableStateOf("") }
    var diesel by remember { mutableStateOf(false) }
    var nafta by remember { mutableStateOf(false) }
    var fame by remember { mutableStateOf(false) }
    var equipo by remember { mutableStateOf("") }
    var observaciones by remember { mutableStateOf("") }
    var firma by remember { mutableStateOf("") }

    val cisternas = remember { mutableStateListOf<CisternaState>() }
    var generando by remember { mutableStateOf(false) }

    fun sincronizarCisternas() {
        while (cisternas.size < cantidad) cisternas.add(CisternaState(cisternas.size + 1))
        while (cisternas.size > cantidad) cisternas.removeAt(cisternas.size - 1)
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
        sincronizarCisternas()
    }
    LaunchedEffect(cantidad) { sincronizarCisternas() }

    fun generarPdf() {
        generando = true
        scope.launch {
            try {
                val ahora = System.currentTimeMillis()
                withContext(Dispatchers.IO) {
                    val dao = PlanillaAtmosferaLocalDatabase.getDatabase(context).planillaAtmosferaDao()
                    val auditor = auditorPrecargado ?: ""
                    val registroSinId = PlanillaAtmosferaRegistro(
                        unidadId = unidadIdPrecargada ?: 0, dominio = dominio, cliente = cliente, auditor = auditor,
                        fechaCreadoTimestamp = ahora,
                        tipoEstanqueidad = tipoEstanqueidad, tipoIntegridadDispositivos = tipoIntegridad,
                        tipoEspaciosConfinados = tipoEspacios, tipoOtros = tipoOtros,
                        tipoOtrosDetalle = if (tipoOtros) tipoOtrosDetalle else "",
                        productoDiesel = diesel, productoNafta = nafta, productoFame = fame,
                        equipoUtilizado = equipo, observaciones = observaciones, firmaResponsable = firma
                    )
                    val registroId = dao.insertRegistro(registroSinId)
                    val cisternasDb = cisternas.map {
                        AtmosferaCisterna(
                            registroId = registroId, cisternaNumero = it.numero,
                            lel = it.lel.aDouble(), o2 = it.o2.aDouble(), co = it.co.aDouble(), h2s = it.h2s.aDouble()
                        )
                    }
                    dao.insertCisternas(cisternasDb)

                    val pdf = PlanillaAtmosferaPdfBuilder.generar(
                        PlanillaAtmosferaPdfBuilder.Encabezado(
                            fecha = ahora, transportista = cliente,
                            tractor = patenteTractorPrecargado ?: "", tanque = dominio
                        ),
                        registroSinId, cisternasDb
                    )
                    val uri = try {
                        MediaStoreUtils.guardarEnDescargas(context, "planilla_atmosfera_${dominio}_$ahora.pdf", "application/pdf") { pdf.writeTo(it) }
                    } finally {
                        pdf.close()
                    }

                    val historialServicioId = HistorialCompartidoHelper.registrar(
                        context, cliente, dominio, tipoUnidad, cantidad, auditor, uri
                    )

                    val unidadIdReal = unidadIdPrecargada
                        ?: AppDatabase.getInstance(context).unidadDao().getPorPlaca(dominio)?.id
                    if (unidadIdReal != null) {
                        PlanillaAtmosferaSupabaseHelper.registrar(unidadIdReal, ahora, registroSinId, cisternasDb)
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

    Column(modifier = Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Planilla de Control de Atmósfera de Tanques Cisterna (R 12-01)", style = MaterialTheme.typography.titleLarge)
        Text("Transportista: $cliente — Tractor: ${patenteTractorPrecargado ?: "-"} — Tanque: $dominio — $cantidad cisterna(s)", style = MaterialTheme.typography.bodyMedium)

        Text("Tipo de trabajo", style = MaterialTheme.typography.titleMedium)
        Opcion("Prueba de estanqueidad", tipoEstanqueidad) { tipoEstanqueidad = it }
        Opcion("Control y ensayo de integridad de dispositivos críticos (tapas, válvulas de presión y vacío)", tipoIntegridad) { tipoIntegridad = it }
        Opcion("Ingreso a espacios confinados", tipoEspacios) { tipoEspacios = it }
        Opcion("Otros trabajos", tipoOtros) { tipoOtros = it }
        if (tipoOtros) {
            CampoTextoMayusculas(value = tipoOtrosDetalle, onValueChange = { tipoOtrosDetalle = it }, label = { Text("Detalle de otros trabajos") }, modifier = Modifier.fillMaxWidth())
        }

        Text("Productos transportados en los dos últimos viajes", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Opcion("Diesel", diesel, Modifier.weight(1f)) { diesel = it }
            Opcion("Nafta", nafta, Modifier.weight(1f)) { nafta = it }
            Opcion("Fame", fame, Modifier.weight(1f)) { fame = it }
        }

        Text("Medición de gases por cisterna", style = MaterialTheme.typography.titleMedium)
        Text("Aceptable: O2 entre 19,5% y 23%; LEL, CO y H2S en 0 (alerta visual, no bloquea el guardado).", style = MaterialTheme.typography.bodySmall)
        cisternas.forEach { c ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Cisterna N° ${c.numero}", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CampoGas(c.lel, { c.lel = it }, "LEL", fueraDeRango(c.lel, false), Modifier.weight(1f))
                        CampoGas(c.o2, { c.o2 = it }, "O2", fueraDeRango(c.o2, true), Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CampoGas(c.co, { c.co = it }, "CO", fueraDeRango(c.co, false), Modifier.weight(1f))
                        CampoGas(c.h2s, { c.h2s = it }, "H2S", fueraDeRango(c.h2s, false), Modifier.weight(1f))
                    }
                }
            }
        }

        CampoTextoMayusculas(value = equipo, onValueChange = { equipo = it }, label = { Text("Equipo utilizado") }, modifier = Modifier.fillMaxWidth())
        CampoTextoMayusculas(value = observaciones, onValueChange = { observaciones = it }, label = { Text("Observaciones") }, modifier = Modifier.fillMaxWidth())
        CampoTextoMayusculas(value = firma, onValueChange = { firma = it }, label = { Text("Firma y aclaración del responsable") }, modifier = Modifier.fillMaxWidth())

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { onVolver() }) { Text("← Volver") }
            Button(enabled = !generando, onClick = { generarPdf() }) { Text(if (generando) "Generando…" else "📄 Generar PDF") }
        }
    }
}

@Composable
private fun Opcion(texto: String, marcado: Boolean, modifier: Modifier = Modifier, onChange: (Boolean) -> Unit) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = marcado, onCheckedChange = onChange)
        Text(texto)
    }
}

@Composable
private fun CampoGas(valor: String, onChange: (String) -> Unit, etiqueta: String, alerta: Boolean, modifier: Modifier) {
    OutlinedTextField(
        value = valor, onValueChange = onChange, label = { Text(etiqueta) },
        singleLine = true, isError = alerta,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier
    )
}
