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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.aies.coredata.AppDatabase
import com.aies.feature.certificados.data.CertificadosLocalDatabase
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
 * Nota de Reemplazo de Precintos ("cert_precinto"), separada de CertificadosScreen a pedido del
 * usuario -- antes era un checkbox opcional dentro del formulario del Certificado de Tapas Paso
 * de Hombre ("Generar también Nota de Precintos"); ahora es su propia pantalla/flujo, con su
 * propio botón de generar. Reusa el mismo `CertificadosDao`/`EmpresaTransportista` (lista de
 * transportistas compartida con CertificadosScreen) y el `Dropdown` de ese archivo (declarado
 * `internal`), pero no toca la tabla `certificados` (esa es solo del certificado de tapas) ni su
 * "Últimos Emitidos" -- la nota nunca tuvo esa tabla local, el correlativo real vive en Supabase
 * (RPC `siguiente_numero_precinto`) y el registro de auditoría es el HistorialServicio compartido.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotaPrecintosScreen(
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
    // domicilioPrecargado/cuitPrecargado/marcaTractorPrecargado/anioTractorPrecargado/
    // modeloTractorPrecargado/marcaSemiPrecargado/anioSemiPrecargado/modeloSemiPrecargado se
    // aceptan por uniformidad con la firma de los otros módulos (mismo criterio que
    // CertificadosScreen) pero este formulario nunca tuvo esos campos -- quedan sin usar a
    // propósito. patenteTractorPrecargado/patenteSemiPrecargado/cantidadCisternasPrecargada sí
    // se usan (ver más abajo).
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { CertificadosLocalDatabase.getDatabase(context) }
    val dao = remember { db.certificadosDao() }

    var cargado by remember { mutableStateOf(false) }

    var empresas by remember { mutableStateOf(listOf<String>()) }
    var transportista by remember { mutableStateOf(clientePrecargado ?: "") }
    var mostrarDialogoNuevaEmpresa by remember { mutableStateOf(false) }
    var patenteChasis by remember { mutableStateOf("") } // "Tractor" en la nota impresa
    var patenteAcoplado by remember { mutableStateOf("") } // "Semirremolque" en la nota impresa
    var tablaCalibracion by remember { mutableStateOf("") }
    var tipoCable by remember { mutableStateOf("Bronce") }
    var expendidoPor by remember { mutableStateOf("MONTAJES CAMPANA") }
    var cantidadTapas by remember { mutableStateOf("1") }
    var precintosPorTapa by remember { mutableStateOf("1") }
    var numeroInicialPrecinto by remember { mutableStateOf("") }

    var generando by remember { mutableStateOf(false) }

    // Modo sesión: la unidad ya viene resuelta desde feature-sesion, se salta la carga manual.
    LaunchedEffect(unidadIdPrecargada) {
        val id = unidadIdPrecargada ?: return@LaunchedEffect
        val unidad = withContext(Dispatchers.IO) { AppDatabase.getInstance(context).unidadDao().getPorId(id) }
        if (unidad != null) {
            patenteChasis = unidad.placa
            if (clientePrecargado == null) transportista = unidad.cliente
        }
        if (patenteChasis.isBlank()) patenteTractorPrecargado?.let { patenteChasis = it }
        if (patenteAcoplado.isBlank()) patenteSemiPrecargado?.let { patenteAcoplado = it }
        // En modo sesión la cantidad de tapas (una por cisterna) ya se conoce, no se pregunta.
        cantidadCisternasPrecargada?.let { cantidadTapas = it.coerceAtLeast(1).toString() }
    }

    LaunchedEffect(Unit) {
        empresas = dao.getEmpresas().first().map { it.nombre }
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
        Text("Nota de Reemplazo de Precintos", style = androidx.compose.material3.MaterialTheme.typography.titleLarge)

        Text(
            "El número de acta se asigna automáticamente al generar.",
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

        CampoTextoMayusculas(value = patenteChasis, onValueChange = { patenteChasis = it }, label = { Text("Patente Tractor") }, modifier = Modifier.fillMaxWidth())
        CampoTextoMayusculas(value = patenteAcoplado, onValueChange = { patenteAcoplado = it }, label = { Text("Patente Semirremolque") }, modifier = Modifier.fillMaxWidth())
        CampoTextoMayusculas(value = tablaCalibracion, onValueChange = { tablaCalibracion = it }, label = { Text("Tabla de Calibración Nº") }, modifier = Modifier.fillMaxWidth())
        Dropdown("Tipo de cable", listOf("Bronce", "Acero"), tipoCable) { tipoCable = it }
        Dropdown("Expendido por", listOf("MONTAJES CAMPANA", "AIES S.A.", "ELECTROMECANICA DAMIANI", "METALURGICA FERNANDEZ", "CONTROLAR SRL"), expendidoPor) { expendidoPor = it }
        if (sesionId == null) {
            Dropdown("Cantidad de tapas de la unidad", (1..7).map { it.toString() }, cantidadTapas) { cantidadTapas = it }
        } else {
            OutlinedTextField(
                value = cantidadTapas, onValueChange = {}, readOnly = true,
                label = { Text("Cantidad de tapas de la unidad (= cisternas de la visita)") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        Dropdown("Precintos por cada tapa", listOf("1", "2"), precintosPorTapa) { precintosPorTapa = it }
        OutlinedTextField(
            value = numeroInicialPrecinto, onValueChange = { numeroInicialPrecinto = it },
            label = { Text("Número primer precinto") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            enabled = !generando && transportista.isNotBlank(),
            onClick = {
                generando = true
                scope.launch {
                    try {
                        // A pedido del usuario se eliminó el número de seguimiento (Acta Nº) de
                        // esta nota -- ya no se pide ningún correlativo por RPC acá.
                        val hora = SimpleDateFormat("HHmmss", Locale.getDefault()).format(Date())
                        val cantidad = cantidadTapas.toIntOrNull() ?: 1
                        val porTapa = precintosPorTapa.toIntOrNull() ?: 1
                        val inicial = numeroInicialPrecinto.toIntOrNull() ?: 0
                        // Total físico de precintos usados: cada tapa lleva `porTapa` precintos
                        // (1 ó 2), no `cantidad` (esa es la cantidad de TAPAS). Rango real de
                        // numeración según [CertificadoPdfBuilder.calcularNumeracionPrecintos]:
                        // con porTapa=2 el último número impreso es inicial + cantidad*2 - 1.
                        val totalPrecintos = cantidad * porTapa
                        val detalle = "Total precintos utilizados: $totalPrecintos" +
                            if (inicial > 0) " (Nº $inicial a ${inicial + totalPrecintos - 1})" else ""

                        withContext(Dispatchers.IO) {
                            val pdfNota = CertificadoPdfBuilder.generarNotaPrecintos(
                                context.assets, tablaCalibracion, tipoCable, expendidoPor,
                                transportista, patenteAcoplado, patenteChasis, "", cantidad, porTapa, inicial
                            )
                            val nombreArchivo = "Nota_Precintos_${patenteChasis}_${SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())}_$hora.pdf".replace(" ", "")
                            val uriNota = MediaStoreUtils.guardarEnDescargas(context, nombreArchivo, "application/pdf") { pdfNota.writeTo(it) }
                            pdfNota.close()

                            val idPrecinto = HistorialCompartidoHelper.registrar(
                                context, "cert_precinto", "Acta de Reemplazo de Precinto",
                                transportista, patenteChasis, patenteAcoplado, uriNota, auditorPrecargado, detalle
                            )

                            if (sesionId != null && sesionItemId != null && idPrecinto > 0) {
                                AppDatabase.getInstance(context).sesionServicioDao().marcarItemCompleto(sesionItemId, idPrecinto)
                            }
                        }

                        patenteChasis = ""; patenteAcoplado = ""; tablaCalibracion = ""
                        numeroInicialPrecinto = ""
                        Toast.makeText(context, "Nota de precintos generada en Descargas", Toast.LENGTH_SHORT).show()
                        if (sesionId != null && sesionItemId != null) onVolverASesion?.invoke()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    } finally {
                        generando = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (generando) "Generando…" else "GENERAR NOTA DE PRECINTOS") }
    }
}
