package com.aies.feature.certificados

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aies.coredata.AppDatabase
import com.aies.coredata.remote.SupabaseClientProvider
import com.aies.feature.certificados.data.CertificadoRegistro
import com.aies.feature.certificados.data.CertificadosLocalDatabase
import com.aies.feature.certificados.data.CsvExporter
import com.aies.feature.certificados.data.EmpresaTransportista
import com.aies.feature.certificados.data.HistorialCompartidoHelper
import com.aies.feature.certificados.pdf.CertificadoPdfBuilder
import com.aies.feature.certificados.util.MediaStoreUtils
import com.aies.coreui.CampoTextoMayusculas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Portado de CertificadorEscritorio (Avalonia/QuestPDF/SQLite+Dapper). A diferencia de
 * Inspecciones, acá la UI se rehizo en Compose en vez de portar Views+XML: es un único
 * formulario (sin RecyclerViews ni mapeo celda-a-celda), así que el riesgo de reescritura
 * es bajo — lo delicado es la generación del PDF en sí (CertificadoPdfBuilder), que sí se
 * portó con precisión, no la superficie de UI que lo alimenta.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CertificadosScreen(
    modifier: Modifier = Modifier,
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
    cantidadCisternasPrecargada: Int? = null,
    onVolverASesion: (() -> Unit)? = null
) {
    // domicilioPrecargado/cuitPrecargado se aceptan por uniformidad con la firma de los otros
    // módulos, pero este formulario nunca tuvo esos campos (ni domicilio ni CUIT) — quedan sin
    // usar a propósito. La Nota de Reemplazo de Precintos ("cert_precinto") se separó a su
    // propia pantalla (ver NotaPrecintosScreen) -- este formulario ahora solo genera el
    // Certificado de Tapas Paso de Hombre ("cert_tapa_hombre").
    // marcaTractorPrecargado/anioTractorPrecargado/modeloTractorPrecargado/marcaSemiPrecargado/
    // anioSemiPrecargado/modeloSemiPrecargado/cantidadCisternasPrecargada se aceptan por
    // uniformidad con Cambio 1, pero este formulario solo tiene patente de chasis/acoplado (no
    // marca/modelo/año/cantidad de tapas) — quedan sin usar a propósito.
    // patenteTractorPrecargado/patenteSemiPrecargado sí se usan (ver más abajo).
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { CertificadosLocalDatabase.getDatabase(context) }
    val dao = remember { db.certificadosDao() }

    var cargado by remember { mutableStateOf(false) }

    var tipoCertificado by remember { mutableStateOf("Shell") }
    var empresas by remember { mutableStateOf(listOf<String>()) }
    var transportista by remember { mutableStateOf(clientePrecargado ?: "") }
    var mostrarDialogoNuevaEmpresa by remember { mutableStateOf(false) }
    var incluirChasis by remember { mutableStateOf(true) }
    var patenteChasis by remember { mutableStateOf("") }
    var patenteAcoplado by remember { mutableStateOf("") }
    var sistemaTapas by remember { mutableStateOf("ABULONADAS") }

    var historial by remember { mutableStateOf(listOf<CertificadoRegistro>()) }
    var generando by remember { mutableStateOf(false) }

    suspend fun refrescarHistorial() {
        historial = dao.historial()
    }

    // Modo sesión: la unidad ya viene resuelta desde feature-sesion, se salta la carga manual
    // de patente (acá no hay "cantidad de cisternas" que precargar, solo la patente del chasis).
    LaunchedEffect(unidadIdPrecargada) {
        val id = unidadIdPrecargada ?: return@LaunchedEffect
        val unidad = withContext(Dispatchers.IO) { AppDatabase.getInstance(context).unidadDao().getPorId(id) }
        if (unidad != null) {
            patenteChasis = unidad.placa
            if (clientePrecargado == null) transportista = unidad.cliente
        }
        // Cambio 1: patente de tractor/semirremolque de esta visita puntual — solo si el
        // usuario todavía no los tocó.
        if (patenteChasis.isBlank()) patenteTractorPrecargado?.let { patenteChasis = it }
        if (patenteAcoplado.isBlank()) patenteSemiPrecargado?.let { patenteAcoplado = it }
    }

    LaunchedEffect(Unit) {
        empresas = dao.getEmpresas().first().map { it.nombre }
        withContext(Dispatchers.IO) { refrescarHistorial() }
        cargado = true
    }

    if (mostrarDialogoNuevaEmpresa) {
        var nombreNuevo by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { mostrarDialogoNuevaEmpresa = false },
            title = { Text("Agregar Transportista") },
            text = {
                CampoTextoMayusculas(
                    value = nombreNuevo, onValueChange = { nombreNuevo = it },
                    label = { Text("Nombre de la empresa") }, modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    val nombre = nombreNuevo.trim()
                    if (nombre.isNotEmpty()) {
                        scope.launch {
                            withContext(Dispatchers.IO) { dao.insertEmpresa(EmpresaTransportista(nombre.uppercase())) }
                            empresas = dao.getEmpresas().first().map { it.nombre }
                            transportista = nombre.uppercase()
                            mostrarDialogoNuevaEmpresa = false
                        }
                    }
                }) { Text("Guardar") }
            },
            dismissButton = { Button(onClick = { mostrarDialogoNuevaEmpresa = false }) { Text("Cancelar") } }
        )
    }

    if (!cargado) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Text("Cargando…")
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Emisión de Documentos", style = androidx.compose.material3.MaterialTheme.typography.titleLarge)

        Dropdown("Tipo de Certificado", listOf("Shell", "Axion", "Ambos (Genera 2 PDFs)"), tipoCertificado) { tipoCertificado = it }

        Text(
            "El número de documento se asigna automáticamente al generar.",
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall
        )

        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.weight(1f)) {
                Dropdown("Transportista", empresas, transportista) { transportista = it }
            }
            IconButton(onClick = { mostrarDialogoNuevaEmpresa = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Agregar transportista")
            }
        }

        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Checkbox(checked = incluirChasis, onCheckedChange = { incluirChasis = it })
            Text("Incluir Patente de Tractor/Chasis")
        }
        CampoTextoMayusculas(value = patenteChasis, onValueChange = { patenteChasis = it }, label = { Text("Patente Chasis") }, modifier = Modifier.fillMaxWidth())
        CampoTextoMayusculas(value = patenteAcoplado, onValueChange = { patenteAcoplado = it }, label = { Text("Patente Acoplado") }, modifier = Modifier.fillMaxWidth())

        Dropdown("Sistema de Tapas", listOf("ABULONADAS", "ZUNCHADA"), sistemaTapas) { sistemaTapas = it }

        Button(
            enabled = !generando && transportista.isNotBlank(),
            onClick = {
                generando = true
                scope.launch {
                    try {
                        // Fase D paso 5: número correlativo real, compartido entre dispositivos,
                        // vía RPC de Postgres. Sin try/catch propio -- si falla, cae al catch de
                        // abajo y no se escribe nada (ni dao.insertCertificado, ni PDFs, ni
                        // HistorialCompartidoHelper.registrar).
                        val numeroTapaHombre = SupabaseClientProvider.siguienteNumeroTapaHombre()
                        val nro = numeroTapaHombre.toString().padStart(5, '0')
                        val hora = SimpleDateFormat("HHmmss", Locale.getDefault()).format(Date())
                        val anio = SimpleDateFormat("yy", Locale.getDefault()).format(Date())
                        val numImpreso = "$nro/$anio"

                        withContext(Dispatchers.IO) {
                            dao.insertCertificado(
                                CertificadoRegistro(
                                    nroCorrelativo = nro, transportista = transportista,
                                    patenteChasis = patenteChasis, patenteAcoplado = patenteAcoplado,
                                    sistemaTapas = sistemaTapas, fechaEmisionTimestamp = System.currentTimeMillis()
                                )
                            )

                            var uriShell: android.net.Uri? = null
                            var uriAxion: android.net.Uri? = null

                            if (tipoCertificado.startsWith("Ambos")) {
                                val pdfShell = CertificadoPdfBuilder.generarCertificadoTapas(
                                    context.assets, numImpreso + "S", transportista, patenteChasis, patenteAcoplado, sistemaTapas, "Shell", incluirChasis
                                )
                                uriShell = MediaStoreUtils.guardarEnDescargas(context, "Certificado_Et${nro}S_$hora.pdf", "application/pdf") { pdfShell.writeTo(it) }
                                pdfShell.close()

                                val pdfAxion = CertificadoPdfBuilder.generarCertificadoTapas(
                                    context.assets, numImpreso + "A", transportista, patenteChasis, patenteAcoplado, sistemaTapas, "Axion", incluirChasis
                                )
                                uriAxion = MediaStoreUtils.guardarEnDescargas(context, "Certificado_Et${nro}A_$hora.pdf", "application/pdf") { pdfAxion.writeTo(it) }
                                pdfAxion.close()
                            } else {
                                val suf = if (tipoCertificado == "Shell") "S" else "A"
                                val pdf = CertificadoPdfBuilder.generarCertificadoTapas(
                                    context.assets, numImpreso + suf, transportista, patenteChasis, patenteAcoplado, sistemaTapas, tipoCertificado, incluirChasis
                                )
                                uriShell = MediaStoreUtils.guardarEnDescargas(context, "Certificado_Et$nro${suf}_$hora.pdf", "application/pdf") { pdf.writeTo(it) }
                                pdf.close()
                            }

                            val idTapaHombre = HistorialCompartidoHelper.registrar(
                                context, "cert_tapa_hombre", "Certificado de Tapas Paso de Hombre",
                                transportista, patenteChasis, patenteAcoplado, uriShell ?: uriAxion, auditorPrecargado
                            )

                            if (sesionId != null && sesionItemId != null && idTapaHombre > 0) {
                                AppDatabase.getInstance(context).sesionServicioDao().marcarItemCompleto(sesionItemId, idTapaHombre)
                            }
                        }

                        patenteChasis = ""; patenteAcoplado = ""
                        withContext(Dispatchers.IO) { refrescarHistorial() }
                        Toast.makeText(context, "Documentos generados en Descargas", Toast.LENGTH_SHORT).show()
                        if (sesionId != null && sesionItemId != null) onVolverASesion?.invoke()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    } finally {
                        generando = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (generando) "Generando…" else "GENERAR DOCUMENTOS") }

        Text("Últimos Emitidos:", style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            historial.forEach { c ->
                val fecha = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(c.fechaEmisionTimestamp))
                Text("Nº ${c.nroCorrelativo} | $fecha | ${c.transportista} | ${c.patenteAcoplado}")
                Divider()
            }
        }

        Button(
            onClick = {
                scope.launch {
                    try {
                        val registros = withContext(Dispatchers.IO) { dao.exportarDatos() }
                        val csv = CsvExporter.generarCsv(registros)
                        val nombreArchivo = "Reporte_AIES_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.csv"
                        withContext(Dispatchers.IO) {
                            MediaStoreUtils.guardarEnDescargas(context, nombreArchivo, "text/csv") { out ->
                                out.write(csv.toByteArray(Charsets.UTF_8))
                            }
                        }
                        Toast.makeText(context, "Exportado a Descargas", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error al exportar: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Exportar a Excel") }
    }
}

// internal (no private): NotaPrecintosScreen, en el mismo módulo, también lo usa.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun Dropdown(label: String, options: List<String>, selected: String, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected, onValueChange = {}, readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opcion ->
                DropdownMenuItem(text = { Text(opcion) }, onClick = { onSelected(opcion); expanded = false })
            }
        }
    }
}
