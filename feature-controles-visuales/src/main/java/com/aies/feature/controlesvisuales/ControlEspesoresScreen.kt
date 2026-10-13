package com.aies.feature.controlesvisuales

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.aies.coredata.AppDatabase
import com.aies.feature.controlesvisuales.data.ControlEspesores
import com.aies.feature.controlesvisuales.data.ControlesVisualesLocalDatabase
import com.aies.feature.controlesvisuales.data.DetalleCompartimentoEspesores
import com.aies.feature.controlesvisuales.data.GrillaEspesores
import com.aies.feature.controlesvisuales.data.HistorialCompartidoHelper
import com.aies.feature.controlesvisuales.data.toEmbed
import com.aies.feature.controlesvisuales.data.MedicionEspesor
import com.aies.feature.controlesvisuales.pdf.ControlEspesoresPdfBuilder
import com.aies.feature.controlesvisuales.util.MediaStoreUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ControlEspesoresScreen(
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
    // clave "grilla|fila|columna" -> valor en mm como texto
    val mediciones = remember { mutableStateMapOf<String, String>() }
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
            encabezado.dominio = unidad.placa
            if (clientePrecargado == null) encabezado.razonSocial = unidad.cliente
            mensajeBusqueda = "Unidad precargada desde la sesión: $cantidad compartimento(s)."
            sincronizarDetalles()
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
            sincronizarDetalles()
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
                    val controlId = dao.insertEspesores(ControlEspesores(encabezado = enc.toEmbed(), fechaCreadoTimestamp = System.currentTimeMillis()))

                    dao.insertDetalleEspesores(detalles.map { d -> DetalleCompartimentoEspesores(controlId = controlId, numero = d.numero, capacidad = d.capacidad, producto = d.productoFinal(), estado = d.estado) })

                    val medicionesDb = mutableListOf<MedicionEspesor>()
                    GrillaEspesores.grillas.forEach { grilla ->
                        for (fila in 0 until GrillaEspesores.FILAS) {
                            for (col in 0 until GrillaEspesores.COLUMNAS) {
                                val valor = mediciones["$grilla|$fila|$col"]?.toDoubleOrNull()
                                medicionesDb.add(MedicionEspesor(controlId = controlId, grilla = grilla, fila = fila, columna = col, valorMm = valor))
                            }
                        }
                    }
                    dao.insertMedicionesEspesor(medicionesDb)

                    val pdf = ControlEspesoresPdfBuilder.generar(enc.toEmbed(), filas, medicionesDb)
                    val nombreArchivo = "control_espesores_${enc.dominio}_${System.currentTimeMillis()}.pdf"
                    val uri = MediaStoreUtils.guardarEnDescargas(context, nombreArchivo, "application/pdf") { pdf.writeTo(it) }
                    pdf.close()

                    val historialServicioId = HistorialCompartidoHelper.registrar(
                        context, HistorialCompartidoHelper.TIPO_CONTROL_ESPESORES, "Control de Espesores",
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
        Text("Control de Espesores", style = MaterialTheme.typography.titleLarge)
        Text("Paso ${paso + 1} de 4", style = MaterialTheme.typography.labelLarge)

        when (paso) {
            0 -> PasoDominio(dominioBuscado, { dominioBuscado = it }, cantidad, { cantidad = it; sincronizarDetalles() }, ::buscarUnidad, mensajeBusqueda)
            1 -> EncabezadoControlForm(encabezado, TipoControlVisual.ESPESORES)
            2 -> DetalleCompartimentosSection(detalles)
            3 -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Mediciones de espesor (mm) — grilla genérica de ${GrillaEspesores.FILAS}x${GrillaEspesores.COLUMNAS}, no reproduce el contorno irregular del papel.", style = MaterialTheme.typography.bodySmall)
                GrillaEspesores.grillas.forEach { grilla ->
                    Text(grilla, style = MaterialTheme.typography.titleSmall)
                    for (fila in 0 until GrillaEspesores.FILAS) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (col in 0 until GrillaEspesores.COLUMNAS) {
                                val clave = "$grilla|$fila|$col"
                                OutlinedTextField(
                                    value = mediciones[clave] ?: "",
                                    onValueChange = { mediciones[clave] = it },
                                    modifier = Modifier.width(56.dp),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true
                                )
                            }
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
