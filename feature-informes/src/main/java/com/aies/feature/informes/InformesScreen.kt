package com.aies.feature.informes

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.aies.coredata.AppDatabase
import com.aies.feature.informes.data.Cliente
import com.aies.feature.informes.data.CisternaPosicion
import com.aies.feature.informes.data.CisternaTrazabilidad
import com.aies.feature.informes.data.CisternaVacio
import com.aies.feature.informes.data.HistorialCompartidoHelper
import com.aies.feature.informes.data.InformeRegistro
import com.aies.feature.informes.data.InformesLocalDatabase
import com.aies.feature.informes.data.serializarDatosInforme
import com.aies.feature.informes.excel.AuditoriasExcelBuilder
import com.aies.feature.informes.pdf.InformePdfBuilder
import com.aies.feature.informes.util.MediaStoreUtils
import com.aies.coreui.CampoTextoMayusculas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/** Estado editable de una cisterna, compartido por los pasos 2/3/4 del wizard (uno por cisterna). */
private class CisternaState {
    var volumen by mutableStateOf("")
    var vacioRequerido by mutableStateOf("")
    var vacioMedido by mutableStateOf("")
    var accion by mutableStateOf("0")
    var volumenFinal by mutableStateOf("")
    var cumpleVacio by mutableStateOf("")
    var marca by mutableStateOf("")
    var nroSerie by mutableStateOf("")
    var cumpleTraz by mutableStateOf("")
    var litrosSensor by mutableStateOf("")
    var observacion by mutableStateOf("")
    var cumplePos by mutableStateOf("")
}

private val FMT_DDMMYYYY = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
private val REGEX_CUIT = Regex("^\\d{2}-\\d{8}-\\d\$")

/**
 * Portado de InformeAIES (Flask/SQLAlchemy/ReportLab). Igual que Certificados, la UI se
 * rehizo en Compose (wizard de 5 pasos con validación por paso, réplica del formulario
 * HTML original) porque es un formulario de datos, sin mapeo celda-a-celda; lo preciso acá
 * es la generación del PDF (InformePdfBuilder) y el Excel (AuditoriasExcelBuilder), que sí
 * se portaron manteniendo la geometría/estilo exactos del generador original.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InformesScreen(
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
    // domicilioPrecargado/auditorPrecargado se aceptan por uniformidad con la firma de los
    // otros módulos, pero este wizard no tiene esos campos (no hay domicilio, y el informe lo
    // firma "AIES S.A." fijo — no se le pidió a esta tarea tocar eso, a diferencia de
    // Certificados) — quedan sin usar a propósito.
    // marcaTractorPrecargado/anioTractorPrecargado/modeloTractorPrecargado/marcaSemiPrecargado/
    // anioSemiPrecargado/modeloSemiPrecargado se aceptan por uniformidad con Cambio 1, pero este
    // wizard solo tiene patente de tractor/semi (no marca/modelo/año) — quedan sin usar a
    // propósito. patenteTractorPrecargado/patenteSemiPrecargado sí se usan (ver más abajo).
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { InformesLocalDatabase.getDatabase(context) }
    val dao = remember { db.informesDao() }
    val coreDb = remember { AppDatabase.getInstance(context) }

    var currentStep by remember { mutableIntStateOf(0) }
    // Cambio 2: en modo sesión, n viene fijado por la cantidad de cisternas confirmada en Nueva
    // Visita (ya no hay control +/- manual); fuera de sesión sigue el default histórico de 7.
    var n by remember { mutableIntStateOf(cantidadCisternasPrecargada?.coerceIn(1, 15) ?: 7) }
    val cisternas = remember { mutableStateListOf<CisternaState>().apply { repeat(15) { add(CisternaState()) } } }

    var clientes by remember { mutableStateOf(listOf<Cliente>()) }
    var clienteSeleccionadoId by remember { mutableStateOf<Long?>(null) }
    var transportista by remember { mutableStateOf(clientePrecargado ?: "") }
    var cuit by remember { mutableStateOf(cuitPrecargado ?: "") }
    var patenteTractor by remember { mutableStateOf("") }
    var patenteSemi by remember { mutableStateOf("") }
    var calibradoNro by remember { mutableStateOf("") }
    var fechaEmisionMillis by remember { mutableStateOf<Long?>(null) }
    var fechaVencimientoMillis by remember { mutableStateOf<Long?>(null) }

    var resultadoFinal by remember { mutableStateOf("") }
    var cvCumple by remember { mutableStateOf("") }
    var tdCumple by remember { mutableStateOf("") }
    var psCumple by remember { mutableStateOf("") }

    var generando by remember { mutableStateOf(false) }

    suspend fun recargarClientes() {
        clientes = withContext(Dispatchers.IO) { dao.getClientesActivos() }
    }

    LaunchedEffect(Unit) { recargarClientes() }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

    fun calcVacioRequerido(c: CisternaState) {
        val vol = c.volumen.toDoubleOrNull() ?: 0.0
        c.vacioRequerido = if (vol > 0) Math.round(vol * 0.03).toString() else ""
    }

    fun calcVolFinal(c: CisternaState) {
        val vol = c.volumen.toDoubleOrNull() ?: 0.0
        val med = c.vacioMedido.toDoubleOrNull() ?: 0.0
        val req = c.vacioRequerido.toDoubleOrNull() ?: 0.0
        val acc = c.accion.toDoubleOrNull() ?: 0.0
        c.volumenFinal = if (vol > 0 && med >= 0) {
            if (med > req) vol.toInt().toString() else (vol - acc).toInt().toString()
        } else ""
    }

    fun autoCumpleVacio(c: CisternaState) {
        val req = c.vacioRequerido.toDoubleOrNull()
        val med = c.vacioMedido.toDoubleOrNull()
        if (req != null && med != null) c.cumpleVacio = if (med >= req) "SI" else "NO"
    }

    // Modo sesión: la unidad ya viene resuelta desde feature-sesion. Completar patenteSemi acá
    // (no bloqueado, el usuario lo puede corregir) es justamente lo que hace que el
    // LaunchedEffect(patenteSemi) de más abajo dispare solo, igual que si el usuario lo hubiera
    // tipeado a mano — las dos precargas (sesión y calibración) no se pisan porque completan
    // campos distintos: acá transportista/cuit/patenteSemi, la de calibración solo vacioMedido.
    LaunchedEffect(unidadIdPrecargada) {
        val id = unidadIdPrecargada ?: return@LaunchedEffect
        val unidad = withContext(Dispatchers.IO) { coreDb.unidadDao().getPorId(id) }
        if (unidad != null) {
            patenteSemi = unidad.placa
            if (clientePrecargado == null) transportista = unidad.cliente
        }
        if (patenteTractor.isBlank()) patenteTractorPrecargado?.let { patenteTractor = it }
        patenteSemiPrecargado?.let { if (patenteSemi.isBlank()) patenteSemi = it }
    }

    // Precarga de vacioMedido desde la última calibración vigente de la unidad (feature-calibrado).
    // Alcance deliberadamente acotado: el wizard no tiene campos de enrase/rebalse para
    // precargar (solo volumen/vacioRequerido/vacioMedido existen acá), así que lo único que
    // se prellena es vacioMedido a partir de CalibracionCisterna.vacioLts, y solo si el campo
    // todavía está vacío (nunca se pisa lo que el usuario ya tipeó).
    LaunchedEffect(patenteSemi) {
        val placa = patenteSemi.trim()
        if (placa.isBlank()) return@LaunchedEffect
        delay(500)
        val unidad = withContext(Dispatchers.IO) { coreDb.unidadDao().getPorPlaca(placa) } ?: return@LaunchedEffect
        val vigente = withContext(Dispatchers.IO) {
            coreDb.calibracionDao().calibracionVigenteDeUnidad(unidad.id, System.currentTimeMillis())
        } ?: return@LaunchedEffect
        for (i in 0 until n) {
            val cisternaCal = vigente.cisternas.find { it.cisternaNumero == i + 1 } ?: continue
            val vacioLts = cisternaCal.vacioLts ?: continue
            val c = cisternas[i]
            if (c.vacioMedido.isBlank()) {
                c.vacioMedido = vacioLts.toString()
                calcVolFinal(c)
                autoCumpleVacio(c)
            }
        }
    }

    fun validarPaso(paso: Int): Boolean {
        when (paso) {
            0 -> {
                if (transportista.isBlank() || cuit.isBlank() || calibradoNro.isBlank() ||
                    patenteTractor.isBlank() || patenteSemi.isBlank() || fechaEmisionMillis == null || fechaVencimientoMillis == null
                ) { toast("⚠️ Completá todos los campos requeridos"); return false }
                if (!REGEX_CUIT.matches(cuit)) { toast("⚠️ CUIT con formato inválido (XX-XXXXXXXX-X)"); return false }
                if (fechaVencimientoMillis!! <= fechaEmisionMillis!!) { toast("⚠️ La fecha de vencimiento debe ser posterior a la de emisión"); return false }
                return true
            }
            1 -> {
                for (i in 0 until n) {
                    val c = cisternas[i]
                    val vol = c.volumen.toDoubleOrNull()
                    val med = c.vacioMedido.toDoubleOrNull()
                    if (vol == null || vol <= 0) { toast("⚠️ Cisterna ${i + 1}: Volumen Total requerido"); return false }
                    if (med == null || med < 0) { toast("⚠️ Cisterna ${i + 1}: Vacío Medido requerido"); return false }
                    val req = c.vacioRequerido.toDoubleOrNull() ?: 0.0
                    if (med <= req && c.accion.toDoubleOrNull() == null) { toast("⚠️ Cisterna ${i + 1}: Indicá cuántos litros se bajaron en Acción Tomada"); return false }
                    if (c.cumpleVacio.isEmpty()) { toast("⚠️ Cisterna ${i + 1}: Seleccioná SI o NO en Cumple"); return false }
                }
                return true
            }
            2 -> {
                val series = (0 until n).map { cisternas[it].nroSerie.trim() }
                for (i in 0 until n) {
                    val c = cisternas[i]
                    if (c.marca.isBlank()) { toast("⚠️ Cisterna ${i + 1}: Marca requerida"); return false }
                    if (c.nroSerie.isBlank()) { toast("⚠️ Cisterna ${i + 1}: N° de Serie requerido"); return false }
                    if (c.cumpleTraz.isEmpty()) { toast("⚠️ Cisterna ${i + 1}: Seleccioná SI o NO en Cumple"); return false }
                }
                val dups = series.filter { s -> s.isNotEmpty() && series.count { it == s } > 1 }
                if (dups.isNotEmpty()) { toast("⚠️ Hay números de serie duplicados en los sensores"); return false }
                return true
            }
            3 -> {
                for (i in 0 until n) {
                    val c = cisternas[i]
                    val lts = c.litrosSensor.toDoubleOrNull()
                    if (lts == null || lts <= 0) { toast("⚠️ Cisterna ${i + 1}: Litros sensor requerido"); return false }
                    if (c.cumplePos.isEmpty()) { toast("⚠️ Cisterna ${i + 1}: Seleccioná SI o NO en Cumple"); return false }
                }
                return true
            }
        }
        return true
    }

    fun autoResultado() {
        val allVacioOk = (0 until n).none { cisternas[it].cumpleVacio == "NO" }
        val allTrazOk = (0 until n).none { cisternas[it].cumpleTraz == "NO" }
        val allPosOk = (0 until n).none { cisternas[it].cumplePos == "NO" }
        cvCumple = if (allVacioOk) "SI" else "NO"
        tdCumple = if (allTrazOk) "SI" else "NO"
        psCumple = if (allPosOk) "SI" else "NO"
        resultadoFinal = if (allVacioOk && allTrazOk && allPosOk) "SI" else "NO"
    }

    fun irAPaso(destino: Int) {
        if (destino > currentStep && !validarPaso(currentStep)) return
        if (destino == 4) autoResultado()
        currentStep = destino
    }

    fun generarPdf() {
        if (resultadoFinal.isEmpty()) { toast("⚠️ Seleccioná el resultado final de auditoría"); return }
        generando = true
        scope.launch {
            try {
                val vacioData = (0 until n).map { i ->
                    val c = cisternas[i]
                    val medNum = c.vacioMedido.toDoubleOrNull() ?: 0.0
                    val reqNum = c.vacioRequerido.toDoubleOrNull() ?: 0.0
                    val accion = if (medNum > reqNum) "Ninguna" else c.accion.ifBlank { "0" }
                    CisternaVacio(c.volumen, c.vacioRequerido, c.vacioMedido, accion, c.volumenFinal, c.cumpleVacio)
                }
                val trazData = (0 until n).map { i -> cisternas[i].let { CisternaTrazabilidad(it.marca, it.nroSerie, it.cumpleTraz) } }
                val posData = (0 until n).map { i -> cisternas[i].let { CisternaPosicion(it.litrosSensor, it.observacion, it.cumplePos) } }

                withContext(Dispatchers.IO) {
                    // Cliente: crear si no existe (igual que app.py::generar_route)
                    var cliente = dao.getClientePorCuit(cuit)
                    val clienteId = if (cliente == null) {
                        dao.insertCliente(Cliente(razonSocial = transportista, cuit = cuit, fechaRegistradoTimestamp = System.currentTimeMillis()))
                    } else cliente.id

                    val datos = InformePdfBuilder.DatosInforme(
                        nroInforme = calibradoNro, transportista = transportista, cuit = cuit,
                        patenteTractor = patenteTractor, patenteSemi = patenteSemi, calibradoNro = calibradoNro,
                        fechaEmision = FMT_DDMMYYYY.format(Date(fechaEmisionMillis!!)),
                        fechaVencimiento = FMT_DDMMYYYY.format(Date(fechaVencimientoMillis!!)),
                        cantCisternas = n, resultadoFinal = resultadoFinal, cvCumple = cvCumple, tdCumple = tdCumple, psCumple = psCumple,
                        vacioData = vacioData, trazData = trazData, posData = posData
                    )
                    val pdf = InformePdfBuilder.generar(context.assets, datos)
                    val nombreArchivo = "informe_${calibradoNro.replace("/", "-")}.pdf"
                    val uri: Uri? = MediaStoreUtils.guardarEnDescargas(context, nombreArchivo, "application/pdf") { pdf.writeTo(it) }
                    pdf.close()

                    dao.insertInforme(
                        InformeRegistro(
                            clienteId = clienteId, nroInforme = calibradoNro, patenteTractor = patenteTractor,
                            patenteSemirremolque = patenteSemi, calibradoNro = calibradoNro,
                            fechaEmisionTimestamp = fechaEmisionMillis!!, fechaVencimientoTimestamp = fechaVencimientoMillis!!,
                            cantCisternas = n, resultadoFinal = resultadoFinal, cvCumple = cvCumple, tdCumple = tdCumple, psCumple = psCumple,
                            datosJson = serializarDatosInforme(vacioData, trazData, posData),
                            fechaCreadoTimestamp = System.currentTimeMillis(), archivoPdf = nombreArchivo
                        )
                    )

                    val historialServicioId = HistorialCompartidoHelper.registrar(context, transportista, patenteTractor, patenteSemi, uri)
                    if (sesionId != null && sesionItemId != null && historialServicioId > 0) {
                        coreDb.sesionServicioDao().marcarItemCompleto(sesionItemId, historialServicioId)
                    }
                }

                toast("✅ PDF generado en Descargas")
                recargarClientes()
                if (sesionId != null && sesionItemId != null) onVolverASesion?.invoke()
            } catch (e: Exception) {
                toast("❌ Error: ${e.message}")
            } finally {
                generando = false
            }
        }
    }

    fun exportarExcel() {
        scope.launch {
            try {
                val registros = withContext(Dispatchers.IO) { dao.listarParaExportar(null, null, null) }
                val wb = AuditoriasExcelBuilder.generar(registros)
                val nombre = "auditorias_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.xlsx"
                withContext(Dispatchers.IO) {
                    MediaStoreUtils.guardarEnDescargas(context, nombre, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") { wb.write(it) }
                }
                wb.close()
                toast("✅ Excel exportado a Descargas")
            } catch (e: Exception) {
                toast("❌ Error al exportar: ${e.message}")
            }
        }
    }

    Column(modifier = modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Informe de Carga Segura", style = MaterialTheme.typography.titleLarge)
            OutlinedButton(onClick = { exportarExcel() }) { Text("Exportar Excel") }
        }

        Text("Paso ${currentStep + 1} de 5", style = MaterialTheme.typography.labelLarge)

        when (currentStep) {
            0 -> StepDatosGenerales(
                clientes, clienteSeleccionadoId, { clienteSeleccionadoId = it }, transportista, { transportista = it },
                cuit, { cuit = it }, patenteTractor, { patenteTractor = it }, patenteSemi, { patenteSemi = it },
                calibradoNro, { calibradoNro = it; }, fechaEmisionMillis, { fechaEmisionMillis = it },
                fechaVencimientoMillis, { fechaVencimientoMillis = it }, n,
                onGuardarCliente = {
                    if (transportista.isBlank() || cuit.isBlank()) { toast("⚠️ Completá transportista y CUIT"); return@StepDatosGenerales }
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            val existente = dao.getClientePorCuit(cuit)
                            if (existente != null) dao.actualizarCliente(existente.copy(razonSocial = transportista))
                            else dao.insertCliente(Cliente(razonSocial = transportista, cuit = cuit, fechaRegistradoTimestamp = System.currentTimeMillis()))
                        }
                        recargarClientes()
                        toast("✅ Cliente guardado")
                    }
                },
                onSeleccionarCliente = { cliente ->
                    clienteSeleccionadoId = cliente?.id
                    transportista = cliente?.razonSocial ?: ""
                    cuit = cliente?.cuit ?: ""
                }
            )
            1 -> StepControlVacio(n, cisternas, ::calcVacioRequerido, ::calcVolFinal, ::autoCumpleVacio)
            2 -> StepTrazabilidad(n, cisternas)
            3 -> StepPosicion(n, cisternas)
            4 -> StepResultado(resultadoFinal, { resultadoFinal = it }, cvCumple, { cvCumple = it }, tdCumple, { tdCumple = it }, psCumple, { psCumple = it })
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (currentStep > 0) OutlinedButton(onClick = { irAPaso(currentStep - 1) }) { Text("← Volver") }
            Spacer(modifier = Modifier.weight(1f))
            if (currentStep < 4) Button(onClick = { irAPaso(currentStep + 1) }) { Text("Siguiente →") }
            else Button(enabled = !generando, onClick = { generarPdf() }) { Text(if (generando) "Generando…" else "📄 Generar PDF") }
        }
    }
}

// ── Paso 1: Datos Generales ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StepDatosGenerales(
    clientes: List<Cliente>, clienteSeleccionadoId: Long?, onClienteIdChange: (Long?) -> Unit,
    transportista: String, onTransportistaChange: (String) -> Unit,
    cuit: String, onCuitChange: (String) -> Unit,
    patenteTractor: String, onPatenteTractorChange: (String) -> Unit,
    patenteSemi: String, onPatenteSemiChange: (String) -> Unit,
    calibradoNro: String, onCalibradoNroChange: (String) -> Unit,
    fechaEmisionMillis: Long?, onFechaEmisionChange: (Long?) -> Unit,
    fechaVencimientoMillis: Long?, onFechaVencimientoChange: (Long?) -> Unit,
    n: Int,
    onGuardarCliente: () -> Unit,
    onSeleccionarCliente: (Cliente?) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        var expanded by remember { mutableStateOf(false) }
        val etiqueta = clientes.find { it.id == clienteSeleccionadoId }?.let { "${it.razonSocial} (${it.cuit})" } ?: "-- Nuevo cliente --"
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = etiqueta, onValueChange = {}, readOnly = true,
                label = { Text("Seleccionar Cliente Existente (opcional)") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(text = { Text("-- Nuevo cliente --") }, onClick = { onSeleccionarCliente(null); expanded = false })
                clientes.forEach { c ->
                    DropdownMenuItem(text = { Text("${c.razonSocial} (${c.cuit})") }, onClick = { onSeleccionarCliente(c); expanded = false })
                }
            }
        }

        CampoTextoMayusculas(value = transportista, onValueChange = onTransportistaChange, label = { Text("Transportista *") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = cuit, onValueChange = onCuitChange, label = { Text("CUIT * (XX-XXXXXXXX-X)") }, modifier = Modifier.fillMaxWidth())
        CampoTextoMayusculas(value = patenteTractor, onValueChange = onPatenteTractorChange, label = { Text("Patente Tractor *") }, modifier = Modifier.fillMaxWidth())
        CampoTextoMayusculas(value = patenteSemi, onValueChange = onPatenteSemiChange, label = { Text("Patente Semirremolque *") }, modifier = Modifier.fillMaxWidth())
        CampoTextoMayusculas(
            value = calibradoNro,
            onValueChange = { onCalibradoNroChange(it) },
            label = { Text("Calibrado N° * (= Nro. Informe)") },
            modifier = Modifier.fillMaxWidth()
        )

        DateField("Fecha de Emisión *", fechaEmisionMillis, onFechaEmisionChange)
        DateField("Fecha de Vencimiento *", fechaVencimientoMillis, onFechaVencimientoChange)

        // Cambio 2: ya no es editable acá — viene fijado por la cantidad de cisternas
        // confirmada en Nueva Visita (o el default de 7 si el wizard se abrió standalone).
        Text("Cantidad de Cisternas: $n", style = MaterialTheme.typography.labelLarge)

        OutlinedButton(onClick = onGuardarCliente, modifier = Modifier.fillMaxWidth()) { Text("💾 Guardar/Actualizar Cliente") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(label: String, millis: Long?, onChange: (Long?) -> Unit) {
    var mostrar by remember { mutableStateOf(false) }
    val texto = millis?.let { FMT_DDMMYYYY.format(Date(it)) } ?: ""
    OutlinedTextField(
        value = texto, onValueChange = {}, readOnly = true, label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        trailingIcon = { OutlinedButton(onClick = { mostrar = true }) { Text("📅") } }
    )
    if (mostrar) {
        val state = rememberDatePickerState(initialSelectedDateMillis = millis ?: Calendar.getInstance().timeInMillis)
        DatePickerDialog(
            onDismissRequest = { mostrar = false },
            confirmButton = { Button(onClick = { onChange(state.selectedDateMillis); mostrar = false }) { Text("Aceptar") } },
            dismissButton = { OutlinedButton(onClick = { mostrar = false }) { Text("Cancelar") } }
        ) { DatePicker(state = state) }
    }
}

// ── Paso 2: Control Vacío ───────────────────────────────────────────────────────────────

@Composable
private fun StepControlVacio(
    n: Int, cisternas: List<CisternaState>,
    calcVacioRequerido: (CisternaState) -> Unit, calcVolFinal: (CisternaState) -> Unit, autoCumpleVacio: (CisternaState) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Si Vacío Medido > Vacío Requerido, Volumen Final = Volumen Total. Si no, se resta lo indicado en Acción Tomada.", style = MaterialTheme.typography.bodySmall)
        for (i in 0 until n) {
            val c = cisternas[i]
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Cisterna N° ${i + 1}", style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(
                        value = c.volumen, onValueChange = { c.volumen = it; calcVacioRequerido(c); calcVolFinal(c) },
                        label = { Text("Volumen Total (lts) *") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(value = c.vacioRequerido, onValueChange = {}, readOnly = true, label = { Text("Vacío Requerido (auto: 3%)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        value = c.vacioMedido, onValueChange = { c.vacioMedido = it; calcVolFinal(c); autoCumpleVacio(c) },
                        label = { Text("Vacío Medido (lts) *") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = c.accion, onValueChange = { c.accion = it; calcVolFinal(c) },
                        label = { Text("Acción Tomada (litros bajados)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(value = c.volumenFinal, onValueChange = {}, readOnly = true, label = { Text("Volumen Final (calculado)") }, modifier = Modifier.fillMaxWidth())
                    CumpleToggle(c.cumpleVacio) { c.cumpleVacio = it }
                }
            }
        }
    }
}

// ── Paso 3: Trazabilidad ────────────────────────────────────────────────────────────────

@Composable
private fun StepTrazabilidad(n: Int, cisternas: List<CisternaState>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Registrá marca y número de serie del sensor de cada cisterna (el N° de serie debe ser único).", style = MaterialTheme.typography.bodySmall)
        for (i in 0 until n) {
            val c = cisternas[i]
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Cisterna N° ${i + 1} — Sensor", style = MaterialTheme.typography.titleSmall)
                    CampoTextoMayusculas(value = c.marca, onValueChange = { c.marca = it }, label = { Text("Marca *") }, modifier = Modifier.fillMaxWidth())
                    CampoTextoMayusculas(value = c.nroSerie, onValueChange = { c.nroSerie = it }, label = { Text("N° de Serie * (único)") }, modifier = Modifier.fillMaxWidth())
                    CumpleToggle(c.cumpleTraz) { c.cumpleTraz = it }
                }
            }
        }
    }
}

// ── Paso 4: Posición de Sensores ────────────────────────────────────────────────────────

@Composable
private fun StepPosicion(n: Int, cisternas: List<CisternaState>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Ingresá la posición del sensor de sobrellenado (litros a partir de los cuales actúa).", style = MaterialTheme.typography.bodySmall)
        for (i in 0 until n) {
            val c = cisternas[i]
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Cisterna N° ${i + 1} — Sensor Sobrellenado", style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(
                        value = c.litrosSensor, onValueChange = { c.litrosSensor = it },
                        label = { Text("Litros sensor *") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    CampoTextoMayusculas(value = c.observacion, onValueChange = { c.observacion = it }, label = { Text("Observación") }, modifier = Modifier.fillMaxWidth())
                    CumpleToggle(c.cumplePos) { c.cumplePos = it }
                }
            }
        }
    }
}

// ── Paso 5: Resultado Final ─────────────────────────────────────────────────────────────

@Composable
private fun StepResultado(
    resultadoFinal: String, onResultadoChange: (String) -> Unit,
    cvCumple: String, onCvChange: (String) -> Unit,
    tdCumple: String, onTdChange: (String) -> Unit,
    psCumple: String, onPsChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("El resultado de cada ítem se precompletó en base a los datos ingresados; podés ajustarlo si hace falta.", style = MaterialTheme.typography.bodySmall)
        Text("Control Vacío Cisternas"); CumpleToggle(cvCumple, onCvChange)
        Text("Trazabilidad de Sensores"); CumpleToggle(tdCumple, onTdChange)
        Text("Posición de Sensores"); CumpleToggle(psCumple, onPsChange)
        Text("Resultado Final — CUMPLE", style = MaterialTheme.typography.titleSmall); CumpleToggle(resultadoFinal, onResultadoChange)
    }
}

@Composable
private fun CumpleToggle(valor: String, onChange: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = valor == "SI", onClick = { onChange("SI") })
        Text("SI", modifier = Modifier.padding(end = 16.dp))
        RadioButton(selected = valor == "NO", onClick = { onChange("NO") })
        Text("NO")
    }
}
