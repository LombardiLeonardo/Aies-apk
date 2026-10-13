package com.aies.feature.inspeccionglp

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.aies.feature.inspeccionglp.data.Cliente
import com.aies.feature.inspeccionglp.data.Informe
import com.aies.feature.inspeccionglp.data.EstadoInforme
import com.aies.feature.inspeccionglp.data.HistorialCompartidoHelper
import com.aies.feature.inspeccionglp.data.InspeccionGlpDao
import com.aies.feature.inspeccionglp.data.InspeccionGlpLocalDatabase
import com.aies.feature.inspeccionglp.data.SmtpConfigService
import com.aies.feature.inspeccionglp.data.SupabaseConfigService
import com.aies.feature.inspeccionglp.data.SupabaseService
import com.aies.feature.inspeccionglp.data.SyncService
import com.aies.feature.inspeccionglp.data.Vehiculo
import com.aies.feature.inspeccionglp.util.ArchivoUtils
import com.aies.coreui.CampoTextoMayusculas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val FMT_FECHA = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

private sealed class Paso {
    data object Login : Paso()
    data object ConfiguracionSync : Paso()
    data object ConfiguracionSmtp : Paso()
    data object ListaClientes : Paso()
    data class ListaVehiculos(val clienteId: String) : Paso()
    data class Checklist(val clienteId: String, val vehiculoId: String, val informeIdExistente: String? = null) : Paso()
    data class Hallazgos(val informeId: String) : Paso()
    data object ListaInformes : Paso()
}

/**
 * Portado de InspeccionApp (.NET MAUI). Wizard flat en Compose sin ViewModels (misma
 * convención que el resto de los feature modules): stack de pasos propio en vez de Jetpack
 * Navigation. `irARaiz` reemplaza el `Shell.Current.GoToAsync("//ClientePage")` del original
 * (navegación absoluta que resetea el stack a la lista de clientes).
 */
@Composable
fun InspeccionGlpScreen(
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
    // domicilioPrecargado/cuitPrecargado/auditorPrecargado se aceptan por uniformidad con la
    // firma que usa feature-sesion para los otros módulos, pero este wizard no tiene esos campos
    // (Cliente/Vehiculo no registran domicilio/CUIT, y el checklist no tiene un "auditor"
    // por-inspección — lo firma "AIES S.A." fijo, ver HistorialCompartidoHelper) — quedan sin
    // usar a propósito.
    // patenteTractorPrecargado/marcaTractorPrecargado/anioTractorPrecargado/modeloTractorPrecargado/
    // patenteSemiPrecargado/marcaSemiPrecargado/anioSemiPrecargado/modeloSemiPrecargado se aceptan
    // por uniformidad con Cambio 1, pero acá el Vehiculo ya se resuelve por patenteTractor de la
    // Unidad (ver LaunchedEffect de abajo) y no tiene campos de marca/año — quedan sin usar a
    // propósito.
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = remember { InspeccionGlpLocalDatabase.getDatabase(context).inspeccionGlpDao() }
    // Modo sesión: arranca directo en ListaClientes (nunca en Login) para no exigir
    // autenticación/sync — el LaunchedEffect de abajo la reemplaza en cuanto resuelve
    // cliente+vehículo. Fuera de sesión, sigue siendo Login como siempre.
    val backStack = remember { mutableListOf((if (unidadIdPrecargada != null) Paso.ListaClientes else Paso.Login) as Paso) }
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

    fun irARaiz(paso: Paso) {
        backStack.clear()
        backStack.add(paso)
        version++
    }

    // Modo sesión: cliente/vehículo ya vienen resueltos desde feature-sesion (la Unidad
    // core-data es de una sola patente — se precarga sobre patenteTractor, ver
    // HistorialCompartidoHelper.registrar) — se saltan Login/ListaClientes/ListaVehiculos
    // (redundantes acá) y se arranca directo en el Checklist.
    LaunchedEffect(unidadIdPrecargada) {
        val id = unidadIdPrecargada ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            val coreUnidad = com.aies.coredata.AppDatabase.getInstance(context).unidadDao().getPorId(id) ?: return@withContext null
            val nombreCliente = clientePrecargado ?: coreUnidad.cliente
            var cliente = dao.getClientes().firstOrNull { it.razonSocial == nombreCliente }
            if (cliente == null) {
                cliente = Cliente(razonSocial = nombreCliente)
                dao.guardarCliente(cliente)
            }
            var vehiculo = dao.getVehiculosPorCliente(cliente.id).firstOrNull { it.patenteTractor == coreUnidad.placa }
            if (vehiculo == null) {
                vehiculo = Vehiculo(clienteId = cliente.id, patenteTractor = coreUnidad.placa, patenteSemirremolque = "")
                dao.guardarVehiculo(vehiculo)
            }
            cliente.id to vehiculo.id
        }?.let { (clienteId, vehiculoId) ->
            backStack.clear()
            backStack.add(Paso.Checklist(clienteId, vehiculoId))
            version++
        }
    }

    BackHandler(enabled = backStack.size > 1) { volver() }

    androidx.compose.foundation.layout.Box(modifier = modifier.fillMaxSize()) {
        androidx.compose.runtime.key(version) {
            when (val paso = pasoActual) {
                is Paso.Login -> LoginPaso(
                    context = context,
                    onIngresar = { irARaiz(Paso.ListaClientes) },
                    onConfigurar = { navegarA(Paso.ConfiguracionSync) },
                    onSeguirSinSync = { irARaiz(Paso.ListaClientes) }
                )
                is Paso.ConfiguracionSync -> ConfiguracionSyncPaso(context = context, onGuardado = { volver() }, onCancelar = { volver() })
                is Paso.ConfiguracionSmtp -> ConfiguracionSmtpPaso(context = context, onGuardado = { volver() }, onCancelar = { volver() })
                is Paso.ListaClientes -> ListaClientesPaso(
                    context = context, dao = dao,
                    onAbrirVehiculos = { cliente -> navegarA(Paso.ListaVehiculos(cliente.id)) },
                    onInformes = { navegarA(Paso.ListaInformes) },
                    onConfiguracionSmtp = { navegarA(Paso.ConfiguracionSmtp) }
                )
                is Paso.ListaVehiculos -> ListaVehiculosPaso(
                    dao = dao, clienteId = paso.clienteId,
                    onAbrirChecklist = { vehiculo -> navegarA(Paso.Checklist(paso.clienteId, vehiculo.id)) },
                    onVolver = { volver() }
                )
                is Paso.Checklist -> ChecklistPaso(
                    dao = dao, clienteId = paso.clienteId, vehiculoId = paso.vehiculoId, informeIdExistente = paso.informeIdExistente,
                    // Sin hallazgos no hay documento que generar (HallazgosPaso nunca se
                    // alcanza), así que en modo sesión el registro del historial compartido se
                    // dispara acá mismo — sin URI de documento — para que el ítem de la sesión
                    // tenga a qué HistorialServicio apuntar antes de volver.
                    onSinHallazgos = {
                        if (sesionId != null && sesionItemId != null) {
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) {
                                        val cliente = dao.getCliente(paso.clienteId)
                                        val vehiculo = dao.getVehiculo(paso.vehiculoId)
                                        if (cliente != null && vehiculo != null) {
                                            val historialServicioId = HistorialCompartidoHelper.registrar(
                                                context, cliente.razonSocial, vehiculo.patenteTractor, vehiculo.patenteSemirremolque, null
                                            )
                                            if (historialServicioId > 0) {
                                                com.aies.coredata.AppDatabase.getInstance(context).sesionServicioDao().marcarItemCompleto(sesionItemId, historialServicioId)
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error al registrar el servicio: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                                onVolverASesion?.invoke()
                            }
                        } else volver()
                    },
                    onConHallazgos = { informeId -> navegarA(Paso.Hallazgos(informeId)) }
                )
                is Paso.Hallazgos -> HallazgosPaso(
                    context = context, dao = dao, informeId = paso.informeId,
                    sesionId = sesionId, sesionItemId = sesionItemId,
                    onConfigurarSmtp = { navegarA(Paso.ConfiguracionSmtp) },
                    onInformeResuelto = { if (sesionId != null && sesionItemId != null) onVolverASesion?.invoke() else irARaiz(Paso.ListaClientes) }
                )
                is Paso.ListaInformes -> ListaInformesPaso(
                    context = context, dao = dao,
                    onContinuarChecklist = { clienteId, vehiculoId, informeId -> navegarA(Paso.Checklist(clienteId, vehiculoId, informeId)) },
                    onVolver = { volver() }
                )
            }
        }
    }
}

// ---------- Login ----------

@Composable
private fun LoginPaso(
    context: Context,
    onIngresar: () -> Unit,
    onConfigurar: () -> Unit,
    onSeguirSinSync: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var contraseña by remember { mutableStateOf("") }
    var cargando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Inspección GLP", style = MaterialTheme.typography.headlineSmall)
        Text("Iniciá sesión para sincronizar tus datos", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = contraseña, onValueChange = { contraseña = it }, label = { Text("Contraseña") },
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()
        )

        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(12.dp))
        Button(
            enabled = !cargando,
            onClick = {
                if (email.isBlank() || contraseña.isBlank()) {
                    error = "Ingresá tu email y contraseña."
                    return@Button
                }
                scope.launch {
                    if (!SupabaseConfigService.estaConfigurado(context)) {
                        error = "Todavía no cargaste la URL/clave de Supabase. Configurala primero."
                        return@launch
                    }
                    cargando = true
                    error = null
                    try {
                        withContext(Dispatchers.IO) {
                            SupabaseService.inicializar(context)
                            SupabaseService.iniciarSesion(email.trim(), contraseña)
                        }
                        onIngresar()
                    } catch (e: Exception) {
                        error = "No se pudo iniciar sesión. Revisá el email y la contraseña.\n\nDetalle: ${e.message}"
                    } finally {
                        cargando = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (cargando) "Ingresando…" else "Ingresar") }

        TextButton(onClick = onConfigurar, modifier = Modifier.fillMaxWidth()) { Text("Configurar conexión (URL / clave de Supabase)") }
        TextButton(onClick = onSeguirSinSync, modifier = Modifier.fillMaxWidth()) { Text("Seguir sin sincronizar (usar solo local)") }
    }
}

// ---------- Lista de clientes ----------

@Composable
private fun ListaClientesPaso(
    context: Context,
    dao: InspeccionGlpDao,
    onAbrirVehiculos: (Cliente) -> Unit,
    onInformes: () -> Unit,
    onConfiguracionSmtp: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var todos by remember { mutableStateOf(listOf<Cliente>()) }
    var busqueda by remember { mutableStateOf("") }
    var mostrarForm by remember { mutableStateOf(false) }
    var editando by remember { mutableStateOf<Cliente?>(null) }
    var razonSocial by remember { mutableStateOf("") }
    var direccion by remember { mutableStateOf("") }
    var contacto by remember { mutableStateOf("") }
    var sincronizando by remember { mutableStateOf(false) }
    var mensaje by remember { mutableStateOf<String?>(null) }
    var clienteAEliminar by remember { mutableStateOf<Cliente?>(null) }
    var confirmarBorrarTodo by remember { mutableStateOf(false) }

    suspend fun cargar() { todos = withContext(Dispatchers.IO) { dao.getClientes() } }

    LaunchedEffect(Unit) {
        try {
            withContext(Dispatchers.IO) { SyncService(context, dao).sincronizarPlantillas() }
        } catch (e: Exception) {
            // sin conexión en este momento: seguimos con la siembra local de abajo
        }
        withContext(Dispatchers.IO) { dao.seedPlantillaGlpSiNoExiste() }
        cargar()
    }

    val filtrados = if (busqueda.isBlank()) todos else todos.filter { it.razonSocial.contains(busqueda, ignoreCase = true) }

    Column(modifier = Modifier.fillMaxSize().imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Clientes", style = MaterialTheme.typography.titleLarge)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = !sincronizando, onClick = {
                scope.launch {
                    sincronizando = true
                    try {
                        val resultado = withContext(Dispatchers.IO) { SyncService(context, dao).sincronizarTodo() }
                        cargar()
                        mensaje = resultado
                    } catch (e: Exception) {
                        mensaje = "No se pudo sincronizar: ${e.message}"
                    } finally {
                        sincronizando = false
                    }
                }
            }) { Text(if (sincronizando) "Sincronizando…" else "⟳ Sync") }
            OutlinedButton(onClick = onInformes) { Text("Informes") }
            OutlinedButton(onClick = onConfiguracionSmtp) { Text("Correo") }
            OutlinedButton(onClick = { confirmarBorrarTodo = true }) { Text("Borrar todo") }
        }

        OutlinedTextField(
            value = busqueda, onValueChange = { busqueda = it },
            label = { Text("Buscar cliente por razón social") }, modifier = Modifier.fillMaxWidth()
        )

        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (filtrados.isEmpty()) {
                Text("Todavía no hay clientes cargados. Usá el botón de abajo para agregar el primero.", style = MaterialTheme.typography.bodyMedium)
            }
            filtrados.forEach { cliente ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(cliente.razonSocial, style = MaterialTheme.typography.titleMedium)
                        cliente.contacto?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onAbrirVehiculos(cliente) }) { Text("Vehículos") }
                            OutlinedButton(onClick = {
                                editando = cliente
                                razonSocial = cliente.razonSocial
                                direccion = cliente.direccion ?: ""
                                contacto = cliente.contacto ?: ""
                                mostrarForm = true
                            }) { Text("Editar") }
                            OutlinedButton(onClick = { clienteAEliminar = cliente }) { Text("Eliminar") }
                        }
                    }
                }
            }
        }

        if (!mostrarForm) {
            Button(
                onClick = { editando = null; razonSocial = ""; direccion = ""; contacto = ""; mostrarForm = true },
                modifier = Modifier.fillMaxWidth()
            ) { Text("+ Nuevo cliente") }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CampoTextoMayusculas(value = razonSocial, onValueChange = { razonSocial = it }, label = { Text("Razón social") }, modifier = Modifier.fillMaxWidth())
                CampoTextoMayusculas(value = direccion, onValueChange = { direccion = it }, label = { Text("Dirección") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = contacto, onValueChange = { contacto = it }, label = { Text("Contacto (teléfono / email)") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        if (razonSocial.isBlank()) {
                            mensaje = "La razón social es obligatoria."
                            return@Button
                        }
                        scope.launch {
                            val base = editando ?: Cliente(razonSocial = razonSocial.trim())
                            val cliente = base.copy(
                                razonSocial = razonSocial.trim(),
                                direccion = direccion.trim().ifBlank { null },
                                contacto = contacto.trim().ifBlank { null },
                                fechaModificacion = System.currentTimeMillis()
                            )
                            withContext(Dispatchers.IO) { dao.guardarCliente(cliente) }
                            mostrarForm = false
                            editando = null
                            cargar()
                        }
                    }) { Text(if (editando != null) "Guardar cambios" else "Guardar cliente") }
                    OutlinedButton(onClick = { mostrarForm = false; editando = null }) { Text("Cancelar") }
                }
            }
        }
    }

    mensaje?.let { msg ->
        AlertDialog(
            onDismissRequest = { mensaje = null },
            title = { Text("Aviso") },
            text = { Text(msg) },
            confirmButton = { Button(onClick = { mensaje = null }) { Text("OK") } }
        )
    }

    clienteAEliminar?.let { cliente ->
        AlertDialog(
            onDismissRequest = { clienteAEliminar = null },
            title = { Text("Eliminar cliente") },
            text = { Text("¿Eliminar a ${cliente.razonSocial}? Esto también borra sus vehículos e informes asociados. No se puede deshacer.") },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { dao.eliminarClienteCascada(cliente.id) }
                        clienteAEliminar = null
                        cargar()
                    }
                }) { Text("Sí, eliminar") }
            },
            dismissButton = { OutlinedButton(onClick = { clienteAEliminar = null }) { Text("Cancelar") } }
        )
    }

    if (confirmarBorrarTodo) {
        AlertDialog(
            onDismissRequest = { confirmarBorrarTodo = false },
            title = { Text("Borrar todo") },
            text = { Text("Esto borra TODOS los clientes, vehículos, informes y fotos guardados. No se puede deshacer. ¿Continuar?") },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            dao.borrarTodosLosDatos()
                            File(context.cacheDir, "fotos").deleteRecursively()
                            File(context.cacheDir, "informes").deleteRecursively()
                            dao.seedPlantillaGlpSiNoExiste()
                        }
                        confirmarBorrarTodo = false
                        cargar()
                    }
                }) { Text("Sí, borrar todo") }
            },
            dismissButton = { OutlinedButton(onClick = { confirmarBorrarTodo = false }) { Text("Cancelar") } }
        )
    }
}

// ---------- Lista de vehículos ----------

@Composable
private fun ListaVehiculosPaso(
    dao: InspeccionGlpDao,
    clienteId: String,
    onAbrirChecklist: (Vehiculo) -> Unit,
    onVolver: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var cliente by remember { mutableStateOf<Cliente?>(null) }
    var vehiculos by remember { mutableStateOf(listOf<Vehiculo>()) }
    var mostrarForm by remember { mutableStateOf(false) }
    var editando by remember { mutableStateOf<Vehiculo?>(null) }
    var vehiculoAEliminar by remember { mutableStateOf<Vehiculo?>(null) }
    var mensaje by remember { mutableStateOf<String?>(null) }

    var patenteTractor by remember { mutableStateOf("") }
    var marcaTractor by remember { mutableStateOf("") }
    var modeloTractor by remember { mutableStateOf("") }
    var patenteSemi by remember { mutableStateOf("") }
    var marcaSemi by remember { mutableStateOf("") }
    var modeloSemi by remember { mutableStateOf("") }
    var fabricanteSemi by remember { mutableStateOf("") }
    var matricula by remember { mutableStateOf("") }
    var tara by remember { mutableStateOf("") }
    var capacidad by remember { mutableStateOf("") }
    var bandera by remember { mutableStateOf("") }

    suspend fun cargar() { vehiculos = withContext(Dispatchers.IO) { dao.getVehiculosPorCliente(clienteId) } }

    fun limpiarForm() {
        patenteTractor = ""; marcaTractor = ""; modeloTractor = ""
        patenteSemi = ""; marcaSemi = ""; modeloSemi = ""
        fabricanteSemi = ""; matricula = ""; tara = ""; capacidad = ""; bandera = ""
    }

    LaunchedEffect(clienteId) {
        cliente = withContext(Dispatchers.IO) { dao.getCliente(clienteId) }
        cargar()
    }

    Column(modifier = Modifier.fillMaxSize().imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Cliente: ${cliente?.razonSocial ?: ""}", style = MaterialTheme.typography.bodyMedium)
        Text("Vehículos", style = MaterialTheme.typography.titleLarge)

        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (vehiculos.isEmpty()) {
                Text("Este cliente todavía no tiene vehículos. Usá el botón de abajo para agregar el primero.")
            }
            vehiculos.forEach { vehiculo ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(vehiculo.patenteTractor, style = MaterialTheme.typography.titleMedium)
                        Text(vehiculo.patenteSemirremolque, style = MaterialTheme.typography.bodySmall)
                        Text(
                            if (vehiculo.bandera.isNullOrBlank()) "Unidad blanca (FOB)" else "Unidad de color (CIF) — ${vehiculo.bandera}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onAbrirChecklist(vehiculo) }) { Text("Checklist") }
                            OutlinedButton(onClick = {
                                editando = vehiculo
                                patenteTractor = vehiculo.patenteTractor; marcaTractor = vehiculo.marcaTractor ?: ""; modeloTractor = vehiculo.modeloTractor ?: ""
                                patenteSemi = vehiculo.patenteSemirremolque; marcaSemi = vehiculo.marcaSemirremolque ?: ""; modeloSemi = vehiculo.modeloSemirremolque ?: ""
                                fabricanteSemi = vehiculo.fabricanteSemirremolque ?: ""; matricula = vehiculo.numeroMatricula ?: ""
                                tara = vehiculo.taraSemirremolque?.toString() ?: ""; capacidad = vehiculo.capacidadSemirremolque?.toString() ?: ""
                                bandera = vehiculo.bandera ?: ""
                                mostrarForm = true
                            }) { Text("Editar") }
                            OutlinedButton(onClick = { vehiculoAEliminar = vehiculo }) { Text("Eliminar") }
                        }
                    }
                }
            }
        }

        if (!mostrarForm) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { editando = null; limpiarForm(); mostrarForm = true }, modifier = Modifier.weight(1f)) { Text("+ Nuevo vehículo") }
                OutlinedButton(onClick = onVolver) { Text("Volver") }
            }
        } else {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Tractor", style = MaterialTheme.typography.titleSmall)
                CampoTextoMayusculas(value = patenteTractor, onValueChange = { patenteTractor = it }, label = { Text("Patente tractor") }, modifier = Modifier.fillMaxWidth())
                CampoTextoMayusculas(value = marcaTractor, onValueChange = { marcaTractor = it }, label = { Text("Marca tractor") }, modifier = Modifier.fillMaxWidth())
                CampoTextoMayusculas(value = modeloTractor, onValueChange = { modeloTractor = it }, label = { Text("Modelo tractor") }, modifier = Modifier.fillMaxWidth())

                Text("Semirremolque", style = MaterialTheme.typography.titleSmall)
                CampoTextoMayusculas(value = patenteSemi, onValueChange = { patenteSemi = it }, label = { Text("Patente semirremolque") }, modifier = Modifier.fillMaxWidth())
                CampoTextoMayusculas(value = marcaSemi, onValueChange = { marcaSemi = it }, label = { Text("Marca semirremolque") }, modifier = Modifier.fillMaxWidth())
                CampoTextoMayusculas(value = modeloSemi, onValueChange = { modeloSemi = it }, label = { Text("Modelo semirremolque") }, modifier = Modifier.fillMaxWidth())
                CampoTextoMayusculas(value = fabricanteSemi, onValueChange = { fabricanteSemi = it }, label = { Text("Fabricante semirremolque") }, modifier = Modifier.fillMaxWidth())
                CampoTextoMayusculas(value = matricula, onValueChange = { matricula = it }, label = { Text("Matrícula de aprobación N°") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = tara, onValueChange = { tara = it }, label = { Text("TARA") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = capacidad, onValueChange = { capacidad = it }, label = { Text("Volumen / Capacidad") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()
                )

                Text("Bandera (dejar vacío si es unidad blanca)", style = MaterialTheme.typography.titleSmall)
                CampoTextoMayusculas(
                    value = bandera, onValueChange = { bandera = it },
                    label = { Text("Ej: YPF, Shell… (vacío = unidad blanca)") }, modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        if (patenteTractor.isBlank() || patenteSemi.isBlank()) {
                            mensaje = "Las patentes de tractor y semirremolque son obligatorias."
                            return@Button
                        }
                        scope.launch {
                            val base = editando ?: Vehiculo(clienteId = clienteId, patenteTractor = patenteTractor, patenteSemirremolque = patenteSemi)
                            val vehiculo = base.copy(
                                patenteTractor = patenteTractor.trim(),
                                marcaTractor = marcaTractor.trim().ifBlank { null },
                                modeloTractor = modeloTractor.trim().ifBlank { null },
                                patenteSemirremolque = patenteSemi.trim(),
                                marcaSemirremolque = marcaSemi.trim().ifBlank { null },
                                modeloSemirremolque = modeloSemi.trim().ifBlank { null },
                                fabricanteSemirremolque = fabricanteSemi.trim().ifBlank { null },
                                numeroMatricula = matricula.trim().ifBlank { null },
                                taraSemirremolque = tara.toDoubleOrNull(),
                                capacidadSemirremolque = capacidad.toDoubleOrNull(),
                                bandera = bandera.trim().ifBlank { null },
                                fechaModificacion = System.currentTimeMillis()
                            )
                            withContext(Dispatchers.IO) { dao.guardarVehiculo(vehiculo) }
                            mostrarForm = false
                            editando = null
                            cargar()
                        }
                    }) { Text(if (editando != null) "Guardar cambios" else "Guardar vehículo") }
                    OutlinedButton(onClick = { mostrarForm = false; editando = null }) { Text("Cancelar") }
                }
            }
        }
    }

    mensaje?.let { msg ->
        AlertDialog(onDismissRequest = { mensaje = null }, title = { Text("Faltan datos") }, text = { Text(msg) }, confirmButton = { Button(onClick = { mensaje = null }) { Text("OK") } })
    }

    vehiculoAEliminar?.let { vehiculo ->
        AlertDialog(
            onDismissRequest = { vehiculoAEliminar = null },
            title = { Text("Eliminar vehículo") },
            text = { Text("¿Eliminar ${vehiculo.patenteTractor} / ${vehiculo.patenteSemirremolque}? Esto también borra sus informes asociados. No se puede deshacer.") },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { dao.eliminarVehiculoCascada(vehiculo.id) }
                        vehiculoAEliminar = null
                        cargar()
                    }
                }) { Text("Sí, eliminar") }
            },
            dismissButton = { OutlinedButton(onClick = { vehiculoAEliminar = null }) { Text("Cancelar") } }
        )
    }
}

// ---------- Lista de informes ----------

private data class InformeConDatos(val informe: Informe, val clienteNombre: String, val vehiculoTexto: String)

private fun estadoTexto(estado: EstadoInforme) = when (estado) {
    EstadoInforme.Borrador -> "Borrador"
    EstadoInforme.Finalizado -> "Generado (sin enviar)"
    EstadoInforme.Enviado -> "Enviado"
}

@Composable
private fun ListaInformesPaso(
    context: Context,
    dao: InspeccionGlpDao,
    onContinuarChecklist: (clienteId: String, vehiculoId: String, informeId: String) -> Unit,
    onVolver: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var lista by remember { mutableStateOf(listOf<InformeConDatos>()) }
    var informeAEliminar by remember { mutableStateOf<Informe?>(null) }

    suspend fun cargar() {
        lista = withContext(Dispatchers.IO) {
            dao.getInformes().map { informe ->
                val cliente = dao.getCliente(informe.clienteId)
                val vehiculo = dao.getVehiculo(informe.vehiculoId)
                InformeConDatos(
                    informe,
                    cliente?.razonSocial ?: "(cliente eliminado)",
                    vehiculo?.let { "${it.patenteTractor} / ${it.patenteSemirremolque}" } ?: ""
                )
            }
        }
    }

    LaunchedEffect(Unit) { cargar() }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Informes", style = MaterialTheme.typography.titleLarge)

        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (lista.isEmpty()) Text("Todavía no generaste ningún informe.")
            lista.forEach { item ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(item.informe.numeroInforme, style = MaterialTheme.typography.titleMedium)
                            Text(estadoTexto(item.informe.estado), style = MaterialTheme.typography.labelMedium)
                        }
                        Text(item.clienteNombre, style = MaterialTheme.typography.bodyMedium)
                        Text(item.vehiculoTexto, style = MaterialTheme.typography.bodySmall)
                        Text(FMT_FECHA.format(Date(item.informe.fecha)), style = MaterialTheme.typography.bodySmall)

                        if (item.informe.estado == EstadoInforme.Borrador) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { onContinuarChecklist(item.informe.clienteId, item.informe.vehiculoId, item.informe.id) }) { Text("Continuar checklist") }
                                OutlinedButton(onClick = { informeAEliminar = item.informe }) { Text("Eliminar") }
                            }
                        } else {
                            val rutaValida = !item.informe.rutaDocx.isNullOrBlank() && File(item.informe.rutaDocx).exists()
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(enabled = rutaValida, onClick = {
                                    ArchivoUtils.abrirDocumento(context, File(item.informe.rutaDocx!!), "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                                }) { Text("Abrir") }
                                OutlinedButton(enabled = rutaValida, onClick = {
                                    ArchivoUtils.compartirDocumento(context, File(item.informe.rutaDocx!!), "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "Compartir informe")
                                }) { Text("Compartir") }
                            }
                            if (!rutaValida) Text("Este informe no tiene un Word generado.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        OutlinedButton(onClick = onVolver, modifier = Modifier.fillMaxWidth()) { Text("Volver") }
    }

    informeAEliminar?.let { informe ->
        AlertDialog(
            onDismissRequest = { informeAEliminar = null },
            title = { Text("Eliminar informe") },
            text = { Text("¿Eliminar el informe ${informe.numeroInforme}? Esta acción no se puede deshacer.") },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { dao.eliminarInformeCascada(informe.id) }
                        informeAEliminar = null
                        cargar()
                    }
                }) { Text("Sí, eliminar") }
            },
            dismissButton = { OutlinedButton(onClick = { informeAEliminar = null }) { Text("Cancelar") } }
        )
    }
}

// ---------- Configuración SMTP ----------

@Composable
private fun ConfiguracionSmtpPaso(context: Context, onGuardado: () -> Unit, onCancelar: () -> Unit) {
    val scope = rememberCoroutineScope()
    var host by remember { mutableStateOf("") }
    var puerto by remember { mutableStateOf("") }
    var usuario by remember { mutableStateOf("") }
    var contraseña by remember { mutableStateOf("") }
    var ssl by remember { mutableStateOf(true) }
    var dest1 by remember { mutableStateOf("") }
    var dest2 by remember { mutableStateOf("") }
    var dest3 by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val config = withContext(Dispatchers.IO) { SmtpConfigService.obtener(context) }
        host = config.host
        puerto = if (config.puerto > 0) config.puerto.toString() else ""
        usuario = config.usuario
        contraseña = config.contraseña
        ssl = config.usarSsl
        dest1 = config.destinatario1
        dest2 = config.destinatario2
        dest3 = config.destinatario3
    }

    Column(
        modifier = Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Configurar correo", style = MaterialTheme.typography.titleLarge)
        Text("Datos de la cuenta que va a enviar los informes", style = MaterialTheme.typography.bodySmall)

        OutlinedTextField(value = host, onValueChange = { host = it }, label = { Text("Servidor SMTP (ej: smtp.gmail.com)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            value = puerto, onValueChange = { puerto = it }, label = { Text("Puerto (ej: 465 o 587)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(value = usuario, onValueChange = { usuario = it }, label = { Text("Usuario / mail") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            value = contraseña, onValueChange = { contraseña = it }, label = { Text("Contraseña (o contraseña de aplicación)") },
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()
        )

        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Usar SSL directo (puerto 465)")
            Switch(checked = ssl, onCheckedChange = { ssl = it })
        }

        Text(
            "Si usás Gmail, el servidor es smtp.gmail.com, puerto 465 con SSL, y necesitás generar una contraseña de aplicación en la configuración de seguridad de tu cuenta de Google (no funciona con tu contraseña normal).",
            style = MaterialTheme.typography.bodySmall
        )

        Text("Destinatarios fijos", style = MaterialTheme.typography.titleSmall)
        Text("Se van a incluir automáticamente en el mail cada vez que generes un informe. Dejá vacío el que no uses.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(value = dest1, onValueChange = { dest1 = it }, label = { Text("Destinatario 1") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = dest2, onValueChange = { dest2 = it }, label = { Text("Destinatario 2 (opcional)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = dest3, onValueChange = { dest3 = it }, label = { Text("Destinatario 3 (opcional)") }, modifier = Modifier.fillMaxWidth())

        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                if (host.isBlank() || usuario.isBlank()) {
                    error = "Servidor y usuario son obligatorios."
                    return@Button
                }
                val puertoInt = puerto.toIntOrNull()
                if (puertoInt == null) {
                    error = "Ingresá un número de puerto válido (ej: 465)."
                    return@Button
                }
                scope.launch {
                    withContext(Dispatchers.IO) {
                        SmtpConfigService.guardar(context, host.trim(), puertoInt, usuario.trim(), contraseña, ssl, dest1, dest2, dest3)
                    }
                    onGuardado()
                }
            }) { Text("Guardar configuración") }
            OutlinedButton(onClick = onCancelar) { Text("Cancelar") }
        }
    }
}

// ---------- Configuración de sincronización ----------

@Composable
private fun ConfiguracionSyncPaso(context: Context, onGuardado: () -> Unit, onCancelar: () -> Unit) {
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var anonKey by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmarUrlSospechosa by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val (u, k) = withContext(Dispatchers.IO) { SupabaseConfigService.obtener(context) }
        url = u
        anonKey = k
    }

    suspend fun guardar(urlFinal: String) {
        withContext(Dispatchers.IO) { SupabaseConfigService.guardar(context, urlFinal, anonKey.trim()) }
        SupabaseService.reiniciar()
        onGuardado()
    }

    Column(
        modifier = Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Configurar sincronización", style = MaterialTheme.typography.titleLarge)
        Text("Datos de conexión a Supabase (Project Settings → API en el panel de Supabase)", style = MaterialTheme.typography.bodySmall)

        OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("https://abcdefghijk.supabase.co") }, modifier = Modifier.fillMaxWidth())
        Text("Solo el dominio: sin /rest/v1, sin barra al final, sin nada más agregado", style = MaterialTheme.typography.bodySmall)

        OutlinedTextField(
            value = anonKey, onValueChange = { anonKey = it }, label = { Text("anon public key") },
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()
        )

        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                if (url.isBlank() || anonKey.isBlank()) {
                    error = "La URL y la clave son obligatorias."
                    return@Button
                }
                val urlLimpia = url.trim().trimEnd('/')
                if (!urlLimpia.startsWith("https://", ignoreCase = true)) {
                    error = "La Project URL debe empezar con https:// y ser solo el dominio, ej: https://abcdefghijk.supabase.co (sin /rest/v1, sin barra al final)."
                    return@Button
                }
                if (urlLimpia.contains("/rest/") || urlLimpia.contains("/auth/") || urlLimpia.contains("/storage/")) {
                    confirmarUrlSospechosa = urlLimpia
                    return@Button
                }
                scope.launch { guardar(urlLimpia) }
            }) { Text("Guardar configuración") }
            OutlinedButton(onClick = onCancelar) { Text("Cancelar") }
        }
    }

    confirmarUrlSospechosa?.let { urlSospechosa ->
        AlertDialog(
            onDismissRequest = { confirmarUrlSospechosa = null },
            title = { Text("URL sospechosa") },
            text = { Text("La URL tiene /rest/, /auth/ o /storage/ en el medio — la Project URL no debería incluir eso, solo el dominio base. ¿Guardar de todas formas?") },
            confirmButton = { Button(onClick = { scope.launch { guardar(urlSospechosa) }; confirmarUrlSospechosa = null }) { Text("Guardar igual") } },
            dismissButton = { OutlinedButton(onClick = { confirmarUrlSospechosa = null }) { Text("Corregir") } }
        )
    }
}
