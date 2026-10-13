package com.aies.feature.inspeccionejes

import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aies.feature.inspeccionejes.data.ChecklistLado
import com.aies.feature.inspeccionejes.data.Cliente
import com.aies.feature.inspeccionejes.data.Control
import com.aies.feature.inspeccionejes.data.Foto
import com.aies.feature.inspeccionejes.data.HistorialCompartidoHelper
import com.aies.feature.inspeccionejes.data.InspeccionEjesLocalDatabase
import com.aies.feature.inspeccionejes.data.Lado
import com.aies.feature.inspeccionejes.data.PuntaDeEje
import com.aies.feature.inspeccionejes.data.Unidad
import com.aies.feature.inspeccionejes.docx.DocxBuilder
import com.aies.feature.inspeccionejes.util.ArchivoUtils
import com.aies.coreui.CampoTextoMayusculas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val FMT_FECHA = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

private sealed class Paso {
    data object ListaControles : Paso()
    data object SeleccionarCliente : Paso()
    data class SeleccionarUnidad(val clienteId: String, val clienteNombre: String) : Paso()
    data class NuevaUnidad(val clienteId: String) : Paso()
    data class NuevoControl(val clienteId: String? = null, val unidadId: String? = null, val controlId: String? = null) : Paso()
    data class ChecklistEje(val controlId: String) : Paso()
    data class InformeLpt(val controlId: String) : Paso()
    data class Resumen(val controlId: String) : Paso()
}

/**
 * Portado de InspeccionEjes (.NET MAUI). Wizard flat en Compose sin ViewModels (igual
 * convención que feature-certificados/feature-informes): un stack de pasos propio en vez
 * de Jetpack Navigation, porque el flujo es lineal y cada paso necesita poca navegación
 * más allá de "ir al siguiente" / "volver".
 */
@Composable
fun InspeccionEjesScreen(
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
    onVolverASesion: (() -> Unit)? = null
) {
    // domicilioPrecargado/cuitPrecargado se aceptan por uniformidad con la firma que usa
    // feature-sesion para los otros módulos, pero este control no registra domicilio ni CUIT
    // (ver Control en Modelos.kt) — quedan sin usar a propósito.
    // patenteTractorPrecargado/marcaTractorPrecargado/anioTractorPrecargado/modeloTractorPrecargado/
    // patenteSemiPrecargado/marcaSemiPrecargado/anioSemiPrecargado/modeloSemiPrecargado se aceptan
    // por uniformidad con Cambio 1, pero este control ya resuelve tractor/semi por Unidad
    // (dominioTractor/dominioSemirremolque, ver LaunchedEffect de abajo) y no distingue una
    // patente de "visita" separada de la de la Unidad — quedan sin usar a propósito.
    val context = LocalContext.current
    val dao = remember { InspeccionEjesLocalDatabase.getDatabase(context).inspeccionEjesDao() }
    val backStack = remember { mutableListOf((if (unidadIdPrecargada != null) Paso.NuevoControl() else Paso.ListaControles) as Paso) }
    var version by remember { mutableStateOf(0) }
    val pasoActual = backStack.last()

    fun navegarA(paso: Paso) {
        backStack.add(paso)
        version++
    }

    fun volver() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
        version++
    }

    fun irALista() {
        backStack.clear()
        backStack.add(Paso.ListaControles)
        version++
    }

    // Modo sesión: la unidad ya viene resuelta desde feature-sesion — se saltan
    // SeleccionarCliente/SeleccionarUnidad (redundantes acá, cliente y unidad ya se conocen) y
    // se arranca directo en NuevoControl con esos datos precargados. cantidadEjes/fecha quedan
    // a criterio del auditor (no viajan en la sesión), así que no se salta más allá de eso.
    LaunchedEffect(unidadIdPrecargada) {
        val id = unidadIdPrecargada ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            val coreUnidad = com.aies.coredata.AppDatabase.getInstance(context).unidadDao().getPorId(id) ?: return@withContext null
            val nombreCliente = clientePrecargado ?: coreUnidad.cliente
            var cliente = dao.getClientes().first().firstOrNull { it.nombre == nombreCliente }
            if (cliente == null) {
                cliente = Cliente(nombre = nombreCliente)
                dao.guardarCliente(cliente)
            }
            var unidadLocal = dao.getUnidadesPorCliente(cliente.id).firstOrNull { it.dominioSemirremolque == coreUnidad.placa }
            if (unidadLocal == null) {
                unidadLocal = Unidad(clienteId = cliente.id, dominioTractor = null, dominioSemirremolque = coreUnidad.placa, marcaTractor = null, marcaSemirremolque = null)
                dao.guardarUnidad(unidadLocal)
            }
            cliente.id to unidadLocal.id
        }?.let { (clienteId, unidadId) ->
            backStack.clear()
            backStack.add(Paso.NuevoControl(clienteId = clienteId, unidadId = unidadId))
            version++
        }
    }

    BackHandler(enabled = backStack.size > 1) { volver() }

    Box(modifier = modifier.fillMaxSize()) {
        // `version` fuerza recomposición al mutar el stack (mutableListOf plano, no snapshot state).
        androidx.compose.runtime.key(version) {
            when (val paso = pasoActual) {
                is Paso.ListaControles -> ListaControlesPaso(
                    dao = dao,
                    onNuevoControl = { navegarA(Paso.SeleccionarCliente) },
                    onEditar = { navegarA(Paso.NuevoControl(controlId = it.id)) },
                    onChecklist = { navegarA(Paso.ChecklistEje(it.id)) },
                    onLpt = { navegarA(Paso.InformeLpt(it.id)) }
                )
                is Paso.SeleccionarCliente -> SeleccionarClientePaso(
                    dao = dao,
                    onSeleccionar = { navegarA(Paso.SeleccionarUnidad(it.id, it.nombre)) }
                )
                is Paso.SeleccionarUnidad -> SeleccionarUnidadPaso(
                    dao = dao, clienteId = paso.clienteId, clienteNombre = paso.clienteNombre,
                    onNuevaUnidad = { navegarA(Paso.NuevaUnidad(paso.clienteId)) },
                    onSeleccionar = { navegarA(Paso.NuevoControl(clienteId = paso.clienteId, unidadId = it.id)) }
                )
                is Paso.NuevaUnidad -> NuevaUnidadPaso(
                    dao = dao, clienteId = paso.clienteId,
                    onGuardado = { volver() },
                    onCancelar = { volver() }
                )
                is Paso.NuevoControl -> NuevoControlPaso(
                    dao = dao, clienteId = paso.clienteId, unidadId = paso.unidadId, controlId = paso.controlId,
                    responsablePrecargado = auditorPrecargado,
                    onGuardado = { controlId -> navegarA(Paso.ChecklistEje(controlId)) },
                    onCancelar = { if (sesionId != null && sesionItemId != null) onVolverASesion?.invoke() else volver() }
                )
                is Paso.ChecklistEje -> ChecklistEjePaso(
                    dao = dao, controlId = paso.controlId,
                    onContinuar = { navegarA(Paso.InformeLpt(paso.controlId)) }
                )
                is Paso.InformeLpt -> InformeLptPaso(
                    dao = dao, controlId = paso.controlId,
                    onFinalizar = { navegarA(Paso.Resumen(paso.controlId)) }
                )
                is Paso.Resumen -> ResumenPaso(
                    dao = dao, controlId = paso.controlId,
                    sesionId = sesionId, sesionItemId = sesionItemId,
                    onVolverALaLista = { irALista() },
                    onVolverASesion = onVolverASesion
                )
            }
        }
    }
}

// ---------- Paso 1: Lista de controles ----------

@Composable
private fun ListaControlesPaso(
    dao: com.aies.feature.inspeccionejes.data.InspeccionEjesDao,
    onNuevoControl: () -> Unit,
    onEditar: (Control) -> Unit,
    onChecklist: (Control) -> Unit,
    onLpt: (Control) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var controles by remember { mutableStateOf(listOf<Control>()) }
    var controlAEliminar by remember { mutableStateOf<Control?>(null) }

    suspend fun cargar() { controles = withContext(Dispatchers.IO) { dao.getControles() } }

    LaunchedEffect(Unit) { cargar() }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Controles de ejes", style = MaterialTheme.typography.titleLarge)
            Button(onClick = onNuevoControl) { Text("+ Nuevo") }
        }

        if (controles.isEmpty()) {
            Text("No hay controles todavía. Tocá + Nuevo para crear el primero.", style = MaterialTheme.typography.bodyMedium)
        }

        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            controles.forEach { control ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(control.cliente, style = MaterialTheme.typography.titleMedium)
                        Text("Tractor: ${control.dominioTractor ?: "-"}", style = MaterialTheme.typography.bodySmall)
                        Text(FMT_FECHA.format(Date(control.fecha)), style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { onEditar(control) }) { Text("Editar") }
                            OutlinedButton(onClick = { onChecklist(control) }) { Text("Checklist") }
                            OutlinedButton(onClick = { onLpt(control) }) { Text("LPT") }
                            OutlinedButton(onClick = { controlAEliminar = control }) { Text("Eliminar") }
                        }
                    }
                }
            }
        }
    }

    val paraEliminar = controlAEliminar
    if (paraEliminar != null) {
        AlertDialog(
            onDismissRequest = { controlAEliminar = null },
            title = { Text("Eliminar control") },
            text = { Text("¿Seguro que querés eliminar el control de ${paraEliminar.cliente} (Tractor: ${paraEliminar.dominioTractor ?: "-"})? Esta acción no se puede deshacer.") },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { dao.eliminarControlCascada(paraEliminar.id) }
                        controlAEliminar = null
                        cargar()
                    }
                }) { Text("Eliminar") }
            },
            dismissButton = { OutlinedButton(onClick = { controlAEliminar = null }) { Text("Cancelar") } }
        )
    }
}

// ---------- Paso 2: Seleccionar cliente ----------

@Composable
private fun SeleccionarClientePaso(
    dao: com.aies.feature.inspeccionejes.data.InspeccionEjesDao,
    onSeleccionar: (Cliente) -> Unit
) {
    val scope = rememberCoroutineScope()
    var clientes by remember { mutableStateOf(listOf<Cliente>()) }
    var mostrarNuevo by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { dao.getClientes().collectValue { clientes = it } }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Elegí cliente", style = MaterialTheme.typography.titleLarge)
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            clientes.forEach { cliente ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(cliente.nombre, style = MaterialTheme.typography.titleSmall)
                        Button(onClick = { onSeleccionar(cliente) }) { Text("Elegir") }
                    }
                }
            }
        }
        Button(onClick = { mostrarNuevo = true }, modifier = Modifier.fillMaxWidth()) { Text("+ Nuevo cliente") }
    }

    if (mostrarNuevo) {
        var nombre by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { mostrarNuevo = false },
            title = { Text("Nuevo cliente") },
            text = { CampoTextoMayusculas(value = nombre, onValueChange = { nombre = it }, label = { Text("Nombre / Razón social") }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                Button(onClick = {
                    val nombreFinal = nombre.trim()
                    if (nombreFinal.isNotEmpty()) {
                        scope.launch {
                            withContext(Dispatchers.IO) { dao.guardarCliente(Cliente(nombre = nombreFinal)) }
                            mostrarNuevo = false
                        }
                    }
                }) { Text("Guardar") }
            },
            dismissButton = { OutlinedButton(onClick = { mostrarNuevo = false }) { Text("Cancelar") } }
        )
    }
}

/** Colecta un Flow dentro de un LaunchedEffect sin traer collectAsState (evita otro import de lifecycle-compose). */
private suspend fun <T> kotlinx.coroutines.flow.Flow<T>.collectValue(onEach: (T) -> Unit) {
    collect { onEach(it) }
}

// ---------- Paso 3: Seleccionar unidad ----------

@Composable
private fun SeleccionarUnidadPaso(
    dao: com.aies.feature.inspeccionejes.data.InspeccionEjesDao,
    clienteId: String,
    clienteNombre: String,
    onNuevaUnidad: () -> Unit,
    onSeleccionar: (Unidad) -> Unit
) {
    var unidades by remember { mutableStateOf(listOf<Unidad>()) }

    LaunchedEffect(clienteId) { unidades = withContext(Dispatchers.IO) { dao.getUnidadesPorCliente(clienteId) } }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(clienteNombre, style = MaterialTheme.typography.titleLarge)
        Text("Elegí la unidad a controlar", style = MaterialTheme.typography.bodyMedium)

        if (unidades.isEmpty()) Text("Este cliente todavía no tiene unidades.")

        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            unidades.forEach { unidad ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp).fillMaxWidth()) {
                        Text("Tractor: ${unidad.dominioTractor ?: "-"}", style = MaterialTheme.typography.titleSmall)
                        Text("Semirremolque: ${unidad.dominioSemirremolque ?: "-"}", style = MaterialTheme.typography.bodySmall)
                        Text("Marca tractor: ${unidad.marcaTractor ?: "-"}", style = MaterialTheme.typography.bodySmall)
                        Text("Marca semirremolque: ${unidad.marcaSemirremolque ?: "-"}", style = MaterialTheme.typography.bodySmall)
                        Button(onClick = { onSeleccionar(unidad) }, modifier = Modifier.padding(top = 8.dp)) { Text("Elegir") }
                    }
                }
            }
        }

        Button(onClick = onNuevaUnidad, modifier = Modifier.fillMaxWidth()) { Text("+ Nueva unidad") }
    }
}

// ---------- Paso 4: Nueva unidad ----------

@Composable
private fun NuevaUnidadPaso(
    dao: com.aies.feature.inspeccionejes.data.InspeccionEjesDao,
    clienteId: String,
    onGuardado: () -> Unit,
    onCancelar: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var dominioTractor by remember { mutableStateOf("") }
    var marcaTractor by remember { mutableStateOf("") }
    var dominioSemirremolque by remember { mutableStateOf("") }
    var marcaSemirremolque by remember { mutableStateOf("") }
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Nueva unidad", style = MaterialTheme.typography.titleLarge)
        CampoTextoMayusculas(value = dominioTractor, onValueChange = { dominioTractor = it }, label = { Text("Dominio tractor") }, modifier = Modifier.fillMaxWidth())
        CampoTextoMayusculas(value = marcaTractor, onValueChange = { marcaTractor = it }, label = { Text("Marca tractor") }, modifier = Modifier.fillMaxWidth())
        CampoTextoMayusculas(value = dominioSemirremolque, onValueChange = { dominioSemirremolque = it }, label = { Text("Dominio semirremolque") }, modifier = Modifier.fillMaxWidth())
        CampoTextoMayusculas(value = marcaSemirremolque, onValueChange = { marcaSemirremolque = it }, label = { Text("Marca semirremolque") }, modifier = Modifier.fillMaxWidth())

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = {
                if (dominioTractor.isBlank()) {
                    Toast.makeText(context, "Ingresá al menos el dominio del tractor.", Toast.LENGTH_SHORT).show()
                } else {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            dao.guardarUnidad(
                                Unidad(
                                    clienteId = clienteId, dominioTractor = dominioTractor,
                                    dominioSemirremolque = dominioSemirremolque.ifBlank { null },
                                    marcaTractor = marcaTractor.ifBlank { null }, marcaSemirremolque = marcaSemirremolque.ifBlank { null }
                                )
                            )
                        }
                        onGuardado()
                    }
                }
            }) { Text("Guardar") }
            OutlinedButton(onClick = onCancelar) { Text("Cancelar") }
        }
    }
}

// ---------- Paso 5: Nuevo/editar control ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NuevoControlPaso(
    dao: com.aies.feature.inspeccionejes.data.InspeccionEjesDao,
    clienteId: String?,
    unidadId: String?,
    controlId: String?,
    responsablePrecargado: String? = null,
    onGuardado: (String) -> Unit,
    onCancelar: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var idEditando by remember { mutableStateOf(controlId) }
    var clienteIdActual by remember { mutableStateOf(clienteId ?: "") }
    var unidadIdActual by remember { mutableStateOf(unidadId ?: "") }
    var clienteNombre by remember { mutableStateOf("") }
    var dominioTractor by remember { mutableStateOf<String?>(null) }
    var dominioSemirremolque by remember { mutableStateOf<String?>(null) }
    var marcaTractor by remember { mutableStateOf<String?>(null) }
    var marcaSemirremolque by remember { mutableStateOf<String?>(null) }
    var fechaMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    // Modo sesión: precarga el auditor de la visita — solo tiene sentido en un control nuevo,
    // uno existente (controlId != null) ya trae su propio responsable desde el LaunchedEffect de abajo.
    var responsable by remember { mutableStateOf(responsablePrecargado ?: "") }
    var cantidadEjes by remember { mutableStateOf(1) }
    var mostrarFecha by remember { mutableStateOf(false) }

    LaunchedEffect(controlId, unidadId) {
        if (controlId != null) {
            val control = withContext(Dispatchers.IO) { dao.getControl(controlId) } ?: return@LaunchedEffect
            idEditando = control.id
            clienteIdActual = control.clienteId
            unidadIdActual = control.unidadId
            clienteNombre = control.cliente
            dominioTractor = control.dominioTractor
            dominioSemirremolque = control.dominioSemirremolque
            marcaTractor = control.marcaTractor
            marcaSemirremolque = control.marcaSemirremolque
            fechaMillis = control.fecha
            responsable = control.responsable ?: ""
            cantidadEjes = control.cantidadEjes
        } else if (unidadId != null) {
            val unidad = withContext(Dispatchers.IO) { dao.getUnidad(unidadId) } ?: return@LaunchedEffect
            val cliente = withContext(Dispatchers.IO) { dao.getCliente(unidad.clienteId) }
            clienteNombre = cliente?.nombre ?: ""
            dominioTractor = unidad.dominioTractor
            dominioSemirremolque = unidad.dominioSemirremolque
            marcaTractor = unidad.marcaTractor
            marcaSemirremolque = unidad.marcaSemirremolque
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(if (idEditando != null) "Editar control" else "Nuevo control", style = MaterialTheme.typography.titleLarge)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(clienteNombre, style = MaterialTheme.typography.titleMedium)
                Text("Tractor: ${dominioTractor ?: "-"}")
                Text("Semirremolque: ${dominioSemirremolque ?: "-"}")
                Text("Marca tractor: ${marcaTractor ?: "-"}")
                Text("Marca semirremolque: ${marcaSemirremolque ?: "-"}")
            }
        }

        OutlinedTextField(
            value = FMT_FECHA.format(Date(fechaMillis)), onValueChange = {}, readOnly = true,
            label = { Text("Fecha") }, modifier = Modifier.fillMaxWidth(),
            trailingIcon = { OutlinedButton(onClick = { mostrarFecha = true }) { Text("Cambiar") } }
        )

        CampoTextoMayusculas(value = responsable, onValueChange = { responsable = it }, label = { Text("Responsable / Auditor") }, modifier = Modifier.fillMaxWidth())

        Text("Cantidad de ejes", style = MaterialTheme.typography.titleSmall)
        DropdownEnum(listOf(1, 2, 3), cantidadEjes, { it.toString() }) { cantidadEjes = it }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = {
                if (clienteNombre.isBlank()) {
                    Toast.makeText(context, "Elegí un cliente y una unidad antes de guardar.", Toast.LENGTH_SHORT).show()
                } else {
                    scope.launch {
                        val id = idEditando ?: java.util.UUID.randomUUID().toString()
                        withContext(Dispatchers.IO) {
                            dao.guardarControl(
                                Control(
                                    id = id, clienteId = clienteIdActual, unidadId = unidadIdActual, cliente = clienteNombre,
                                    dominioTractor = dominioTractor, dominioSemirremolque = dominioSemirremolque,
                                    marcaTractor = marcaTractor, marcaSemirremolque = marcaSemirremolque,
                                    fecha = fechaMillis, responsable = responsable.ifBlank { null }, cantidadEjes = cantidadEjes
                                )
                            )
                        }
                        onGuardado(id)
                    }
                }
            }) { Text("Guardar") }
            OutlinedButton(onClick = onCancelar) { Text("Cancelar") }
        }
    }

    if (mostrarFecha) {
        val estado = rememberDatePickerState(initialSelectedDateMillis = fechaMillis)
        DatePickerDialog(
            onDismissRequest = { mostrarFecha = false },
            confirmButton = { Button(onClick = { estado.selectedDateMillis?.let { fechaMillis = it }; mostrarFecha = false }) { Text("Aceptar") } },
            dismissButton = { OutlinedButton(onClick = { mostrarFecha = false }) { Text("Cancelar") } }
        ) { DatePicker(state = estado) }
    }
}

// ---------- Paso 6: Checklist por eje ----------

@Composable
private fun ChecklistEjePaso(
    dao: com.aies.feature.inspeccionejes.data.InspeccionEjesDao,
    controlId: String,
    onContinuar: () -> Unit
) {
    var control by remember { mutableStateOf<Control?>(null) }
    LaunchedEffect(controlId) { control = withContext(Dispatchers.IO) { dao.getControl(controlId) } }

    val controlActual = control ?: return

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "Checklist de eje — ${controlActual.cliente}",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp)
        )
        TabsEjeLado<ChecklistLado>(
            cantidadEjes = controlActual.cantidadEjes,
            obtenerNumeroEje = { it.numeroEje },
            obtenerLado = { it.lado },
            obtenerRevisado = { it.revisado },
            cargarOCrear = { eje, lado ->
                withContext(Dispatchers.IO) {
                    dao.getChecklistsPorControl(controlId).firstOrNull { it.numeroEje == eje && it.lado == lado }
                        ?: ChecklistLado(controlId = controlId, numeroEje = eje, lado = lado).also { dao.guardarChecklistLado(it) }
                }
            },
            guardar = { withContext(Dispatchers.IO) { dao.guardarChecklistLado(it) } },
            tituloBotonFinal = "Continuar",
            onFinalizar = { onContinuar() }
        ) { item, actualizar -> ChecklistLadoForm(item, actualizar, dao) }
    }
}

// ---------- Paso 7: Líquidos penetrantes ----------

@Composable
private fun InformeLptPaso(
    dao: com.aies.feature.inspeccionejes.data.InspeccionEjesDao,
    controlId: String,
    onFinalizar: () -> Unit
) {
    var control by remember { mutableStateOf<Control?>(null) }
    var informeId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(controlId) {
        control = withContext(Dispatchers.IO) { dao.getControl(controlId) }
        informeId = withContext(Dispatchers.IO) { dao.obtenerOCrearInforme(controlId) }.id
    }

    val controlActual = control ?: return
    val informeIdActual = informeId ?: return

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "Líquidos penetrantes — ${controlActual.cliente}",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp)
        )
        TabsEjeLado<PuntaDeEje>(
            cantidadEjes = controlActual.cantidadEjes,
            obtenerNumeroEje = { it.numeroEje },
            obtenerLado = { it.lado },
            obtenerRevisado = { it.revisado },
            cargarOCrear = { eje, lado ->
                withContext(Dispatchers.IO) {
                    dao.getPuntasDeEje(informeIdActual).firstOrNull { it.numeroEje == eje && it.lado == lado }
                        ?: PuntaDeEje(informeId = informeIdActual, numeroEje = eje, lado = lado).also { dao.guardarPuntaDeEje(it) }
                }
            },
            guardar = { withContext(Dispatchers.IO) { dao.guardarPuntaDeEje(it) } },
            tituloBotonFinal = "Finalizar informe",
            onFinalizar = { onFinalizar() }
        ) { item, actualizar -> PuntaDeEjeForm(item, actualizar, dao) }
    }
}

// ---------- Paso 8: Resumen / generación de informes ----------

@Composable
private fun ResumenPaso(
    dao: com.aies.feature.inspeccionejes.data.InspeccionEjesDao,
    controlId: String,
    sesionId: Long? = null,
    sesionItemId: Long? = null,
    onVolverALaLista: () -> Unit,
    onVolverASesion: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var control by remember { mutableStateOf<Control?>(null) }
    var generando by remember { mutableStateOf(false) }
    val enModoSesion = sesionId != null && sesionItemId != null

    LaunchedEffect(controlId) { control = withContext(Dispatchers.IO) { dao.getControl(controlId) } }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

    // Checklist y LPT son 2 puntos de salida independientes de un mismo control (el auditor
    // puede generar uno, los dos, o volver a generar el mismo de nuevo), no 2 pasos secuenciales
    // de un único documento — pero ambos alimentan el mismo tipoServicioCodigo "control_ejes",
    // así que cualquiera de los dos ya es evidencia válida del servicio. En modo sesión se marca
    // el ítem completo apenas se genera el primero (llamar 2 veces es inofensivo, solo actualiza
    // el historialServicioId), sin forzar la salida — el auditor sigue pudiendo generar el otro
    // documento antes de tocar "Volver a la sesión".
    suspend fun marcarCompletoSiCorresponde(historialServicioId: Long) {
        if (enModoSesion && historialServicioId > 0) {
            com.aies.coredata.AppDatabase.getInstance(context).sesionServicioDao().marcarItemCompleto(sesionItemId!!, historialServicioId)
        }
    }

    fun generarChecklist() {
        val controlActual = control ?: return
        generando = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val checklists = dao.getChecklistsPorControl(controlId)
                    val fotosPorChecklist = checklists.associate { it.id to dao.getFotosPorParent(it.id) }
                    val archivo = ArchivoUtils.crearArchivoInforme(context, "Checklist_${controlActual.cliente}_${controlActual.fecha}")
                    DocxBuilder.generarInformeChecklist(context.assets, controlActual, checklists, fotosPorChecklist, archivo)
                    val uri = ArchivoUtils.uriParaArchivo(context, archivo)
                    val historialServicioId = HistorialCompartidoHelper.registrar(
                        context, controlActual.cliente, controlActual.dominioTractor, controlActual.dominioSemirremolque,
                        controlActual.responsable, uri
                    )
                    marcarCompletoSiCorresponde(historialServicioId)
                    // En modo sesión el envío se consolida al final de la visita entera (ver
                    // "Finalizar visita" en feature-sesion) en vez de abrir un compartir por cada
                    // documento generado.
                    if (!enModoSesion) {
                        withContext(Dispatchers.Main) {
                            ArchivoUtils.compartirDocumento(context, archivo, "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "Informe de checklist")
                        }
                    }
                }
            } catch (e: Exception) {
                toast("Error al generar el informe: ${e.message}")
            } finally {
                generando = false
            }
        }
    }

    fun generarLpt() {
        val controlActual = control ?: return
        generando = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val informe = dao.obtenerOCrearInforme(controlId)
                    val puntas = dao.getPuntasDeEje(informe.id)
                    val fotosPorPunta = puntas.associate { it.id to dao.getFotosPorParent(it.id) }
                    val archivo = ArchivoUtils.crearArchivoInforme(context, "LPT_${controlActual.cliente}_${controlActual.fecha}")
                    DocxBuilder.generarInformeLpt(context.assets, controlActual, puntas, fotosPorPunta, archivo)
                    val uri = ArchivoUtils.uriParaArchivo(context, archivo)
                    val historialServicioId = HistorialCompartidoHelper.registrar(
                        context, controlActual.cliente, controlActual.dominioTractor, controlActual.dominioSemirremolque,
                        controlActual.responsable, uri
                    )
                    marcarCompletoSiCorresponde(historialServicioId)
                    if (!enModoSesion) {
                        withContext(Dispatchers.Main) {
                            ArchivoUtils.compartirDocumento(context, archivo, "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "Informe de líquidos penetrantes")
                        }
                    }
                }
            } catch (e: Exception) {
                toast("Error al generar el informe: ${e.message}")
            } finally {
                generando = false
            }
        }
    }

    val controlActual = control
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Revisión final", style = MaterialTheme.typography.titleLarge)

        if (controlActual != null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(controlActual.cliente, style = MaterialTheme.typography.titleMedium)
                    Text("Tractor: ${controlActual.dominioTractor ?: "-"}")
                    Text("Semirremolque: ${controlActual.dominioSemirremolque ?: "-"}")
                    Text("Fecha: ${FMT_FECHA.format(Date(controlActual.fecha))}")
                    Text("Responsable: ${controlActual.responsable ?: "-"}")
                }
            }
        }

        Text("Generá cada informe en Word y compartilo desde tu celular.", style = MaterialTheme.typography.bodyMedium)

        Button(enabled = !generando, onClick = { generarChecklist() }, modifier = Modifier.fillMaxWidth()) {
            Text("Generar informe de Checklist (Word)")
        }
        Button(enabled = !generando, onClick = { generarLpt() }, modifier = Modifier.fillMaxWidth()) {
            Text("Generar informe de Líquidos Penetrantes (Word)")
        }

        Divider()

        OutlinedButton(
            onClick = { if (enModoSesion) onVolverASesion?.invoke() else onVolverALaLista() },
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (enModoSesion) "Volver a la sesión" else "Volver a la lista") }
    }
}
