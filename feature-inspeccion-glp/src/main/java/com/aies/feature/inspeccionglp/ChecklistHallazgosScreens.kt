package com.aies.feature.inspeccionglp

import com.aies.coreui.CampoTextoMayusculas
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.core.content.ContextCompat
import com.aies.feature.inspeccionglp.data.Cliente
import com.aies.feature.inspeccionglp.data.EstadoInforme
import com.aies.feature.inspeccionglp.data.EstadoItemResultado
import com.aies.feature.inspeccionglp.data.EmailService
import com.aies.feature.inspeccionglp.data.esUnidadDeColor
import com.aies.feature.inspeccionglp.data.Foto
import com.aies.feature.inspeccionglp.data.HistorialCompartidoHelper
import com.aies.feature.inspeccionglp.data.Informe
import com.aies.feature.inspeccionglp.data.InspeccionGlpDao
import com.aies.feature.inspeccionglp.data.ItemChecklist
import com.aies.feature.inspeccionglp.data.ItemResultado
import com.aies.feature.inspeccionglp.data.SmtpConfigService
import com.aies.feature.inspeccionglp.data.Vehiculo
import com.aies.feature.inspeccionglp.docx.WordReportBuilder
import com.aies.feature.inspeccionglp.util.ArchivoUtils
import com.aies.feature.inspeccionglp.util.MediaStoreUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private data class ItemChecklistEstado(val item: ItemChecklist, val esMal: Boolean, val comentario: String)

/**
 * Checklist agrupado por categoría/subcategoría, filtrado FOB/CIF según `Vehiculo.esUnidadDeColor()`.
 * Crea el Informe en Borrador apenas se entra (igual que `ChecklistPage.xaml.cs.OnAppearing`), y al
 * finalizar valida que todo ítem marcado Mal tenga comentario antes de continuar.
 */
@Composable
fun ChecklistPaso(
    dao: InspeccionGlpDao,
    clienteId: String,
    vehiculoId: String,
    informeIdExistente: String?,
    onSinHallazgos: () -> Unit,
    onConHallazgos: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var cliente by remember { mutableStateOf<Cliente?>(null) }
    var vehiculo by remember { mutableStateOf<Vehiculo?>(null) }
    var informe by remember { mutableStateOf<Informe?>(null) }
    val items = remember { mutableStateListOf<ItemChecklistEstado>() }
    var cargando by remember { mutableStateOf(true) }
    var errorCarga by remember { mutableStateOf<String?>(null) }
    var errorValidacion by remember { mutableStateOf<String?>(null) }
    var enviando by remember { mutableStateOf(false) }

    LaunchedEffect(clienteId, vehiculoId, informeIdExistente) {
        withContext(Dispatchers.IO) {
            val c = dao.getCliente(clienteId)
            val v = dao.getVehiculo(vehiculoId)
            cliente = c
            vehiculo = v
            if (c == null || v == null) return@withContext

            val plantillas = dao.getPlantillas()
            var informeActual = informeIdExistente?.let { dao.getInforme(it) }
            val resultadosPorItemId = informeIdExistente
                ?.let { dao.getResultadosDeInforme(it).associateBy { r -> r.itemChecklistId } }
                ?: emptyMap()

            val plantilla = if (informeActual != null) plantillas.firstOrNull { it.id == informeActual!!.plantillaId } else plantillas.firstOrNull()
            if (plantilla == null) {
                errorCarga = "No hay ninguna plantilla de checklist cargada todavía."
                return@withContext
            }

            if (informeActual == null) {
                informeActual = Informe(
                    clienteId = clienteId, vehiculoId = vehiculoId, plantillaId = plantilla.id,
                    numeroInforme = dao.generarNumeroInforme(), fecha = System.currentTimeMillis(), estado = EstadoInforme.Borrador
                )
                dao.guardarInforme(informeActual)
            }
            informe = informeActual

            val itemsDePlantilla = dao.getItemsDePlantilla(plantilla.id, v.esUnidadDeColor())
            items.clear()
            items.addAll(itemsDePlantilla.map { item ->
                val previo = resultadosPorItemId[item.id]
                ItemChecklistEstado(item, previo?.estado == EstadoItemResultado.Mal, previo?.comentario ?: "")
            })
        }
        cargando = false
    }

    if (cargando) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Cargando…") }
        return
    }
    if (errorCarga != null) {
        Column(Modifier.fillMaxSize().padding(16.dp)) { Text(errorCarga ?: "", color = MaterialTheme.colorScheme.error) }
        return
    }

    val c = cliente ?: return
    val v = vehiculo ?: return
    val inf = informe ?: return

    val grupos = items.groupBy { it.item.categoria to it.item.subcategoria }
        .toList()
        .sortedBy { (_, lista) -> lista.minOf { it.item.orden } }

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        Text(
            "${c.razonSocial} · ${v.patenteTractor} / ${v.patenteSemirremolque}",
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp)
        )

        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            grupos.forEach { (clave, lista) ->
                val (categoria, subcategoria) = clave
                val titulo = if (subcategoria == null) categoria else "$categoria · $subcategoria"
                Text(titulo, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                lista.sortedBy { it.item.orden }.forEach { estado ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("${estado.item.numero}", color = Color.Gray)
                                Text(estado.item.descripcion, modifier = Modifier.weight(1f))
                                Switch(checked = estado.esMal, onCheckedChange = { checked ->
                                    val idx = items.indexOf(estado)
                                    if (idx >= 0) items[idx] = estado.copy(esMal = checked)
                                })
                            }
                            if (estado.esMal) {
                                CampoTextoMayusculas(
                                    value = estado.comentario,
                                    onValueChange = { texto ->
                                        val idx = items.indexOf(estado)
                                        if (idx >= 0) items[idx] = estado.copy(comentario = texto)
                                    },
                                    label = { Text("¿Qué está mal? (comentario)") }, modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }

        Button(
            enabled = !enviando,
            onClick = {
                val sinComentario = items.filter { it.esMal && it.comentario.isBlank() }
                if (sinComentario.isNotEmpty()) {
                    errorValidacion = "Hay ${sinComentario.size} ítem(s) marcados como Mal sin comentario. Completalos antes de continuar."
                    return@Button
                }
                enviando = true
                scope.launch {
                    withContext(Dispatchers.IO) {
                        items.forEach { estado ->
                            dao.guardarResultado(
                                ItemResultado(
                                    informeId = inf.id, itemChecklistId = estado.item.id,
                                    estado = if (estado.esMal) EstadoItemResultado.Mal else EstadoItemResultado.OK,
                                    comentario = if (estado.esMal) estado.comentario.trim() else null
                                )
                            )
                        }
                    }
                    val cantidadHallazgos = items.count { it.esMal }
                    enviando = false
                    if (cantidadHallazgos == 0) onSinHallazgos() else onConHallazgos(inf.id)
                }
            },
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) { Text(if (enviando) "Guardando…" else "Finalizar checklist") }
    }

    errorValidacion?.let { msg ->
        AlertDialog(
            onDismissRequest = { errorValidacion = null },
            title = { Text("Faltan comentarios") },
            text = { Text(msg) },
            confirmButton = { Button(onClick = { errorValidacion = null }) { Text("OK") } }
        )
    }
}

// ---------- Hallazgos ----------

private data class HallazgoUi(val resultado: ItemResultado, val item: ItemChecklist, val fotos: List<Foto>)

/**
 * Cámara con permiso en runtime (misma técnica que feature-inspeccion-ejes.ChecklistLptTabs).
 */
@Composable
private fun rememberCapturaFoto(onFotoLista: (File) -> Unit): () -> Unit {
    val context = LocalContext.current
    var archivoPendiente by remember { mutableStateOf<File?>(null) }

    val capturaLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { exito ->
        val archivo = archivoPendiente
        archivoPendiente = null
        if (exito && archivo != null) onFotoLista(archivo)
    }

    fun lanzarCaptura() {
        val archivo = ArchivoUtils.crearArchivoFoto(context)
        archivoPendiente = archivo
        capturaLauncher.launch(ArchivoUtils.uriParaArchivo(context, archivo))
    }

    val permisoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
        if (concedido) lanzarCaptura()
    }

    return {
        val yaConcedido = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (yaConcedido) lanzarCaptura() else permisoLauncher.launch(Manifest.permission.CAMERA)
    }
}

/**
 * Un hallazgo (ítem marcado Mal) por vez, con sus fotos; "Generar informe" arma el .docx,
 * lo manda por mail, y hace la transición de estado Borrador -> Finalizado -> Enviado. Si el
 * mail falla, el informe queda en Finalizado (el Word ya está generado en el dispositivo) —
 * mismo comportamiento que `HallazgosPage.xaml.cs.GenerarInformeButton_Clicked`. El dual-write
 * al historial compartido se dispara apenas el .docx queda generado (Finalizado), sin esperar
 * a que el mail también tenga éxito.
 */
@Composable
fun HallazgosPaso(
    context: Context,
    dao: InspeccionGlpDao,
    informeId: String,
    sesionId: Long? = null,
    sesionItemId: Long? = null,
    onConfigurarSmtp: () -> Unit,
    onInformeResuelto: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val hallazgos = remember { mutableStateListOf<HallazgoUi>() }
    var cargando by remember { mutableStateOf(true) }
    var generando by remember { mutableStateOf(false) }
    var textoOverlay by remember { mutableStateOf("") }
    var mensaje by remember { mutableStateOf<String?>(null) }
    var pedirDestinatarioSuelto by remember { mutableStateOf(false) }
    var destinatarioSuelto by remember { mutableStateOf("") }

    suspend fun cargar() {
        hallazgos.clear()
        hallazgos.addAll(
            withContext(Dispatchers.IO) {
                dao.getResultadosDeInforme(informeId)
                    .filter { it.estado == EstadoItemResultado.Mal }
                    .mapNotNull { resultado ->
                        val item = dao.getItemChecklist(resultado.itemChecklistId) ?: return@mapNotNull null
                        HallazgoUi(resultado, item, dao.getFotosDeResultado(resultado.id))
                    }
            }
        )
    }

    LaunchedEffect(informeId) {
        cargar()
        cargando = false
    }

    fun agregarFotoA(hallazgo: HallazgoUi, archivo: File) {
        scope.launch {
            withContext(Dispatchers.IO) { dao.guardarFoto(Foto(itemResultadoId = hallazgo.resultado.id, rutaArchivo = archivo.absolutePath)) }
            cargar()
        }
    }

    suspend fun generarYEnviar(destinatariosOverride: List<String>?) {
        // Modo sesión: el envío por mail se consolida una sola vez al finalizar la visita entera
        // ("Finalizar visita" en feature-sesion arma un único ACTION_SEND_MULTIPLE con todos los
        // documentos de la visita) -- mismo criterio que GestorReportes.enviarCorreo (Livianos/
        // Pesados/etc.) y que el resto de los módulos, que tampoco abren su propio share
        // individual cuando sesionId != null. Acá alcanza con generar el .docx y registrarlo en
        // el historial compartido, sin pedir SMTP ni destinatarios.
        val enviarPorSmtp = sesionId == null
        val config = if (enviarPorSmtp) withContext(Dispatchers.IO) { SmtpConfigService.obtener(context) } else null
        val destinatarios = if (enviarPorSmtp) (destinatariosOverride ?: config!!.destinatariosConfigurados) else emptyList()
        if (enviarPorSmtp && destinatarios.isEmpty()) {
            pedirDestinatarioSuelto = true
            return
        }

        generando = true
        textoOverlay = "Generando informe…"

        val resultado = withContext(Dispatchers.IO) {
            val informe = dao.getInforme(informeId) ?: return@withContext Result.failure<Unit>(IllegalStateException("Informe no encontrado"))
            val cliente = dao.getCliente(informe.clienteId)
            val vehiculo = dao.getVehiculo(informe.vehiculoId)
            if (cliente == null || vehiculo == null) return@withContext Result.failure<Unit>(IllegalStateException("Cliente o vehículo no encontrado"))

            val resultadosCompletos = dao.getResultadosCompletosDeInforme(informeId)
            val fotosPorResultado = hallazgos.associate { it.resultado.id to dao.getFotosDeResultado(it.resultado.id) }

            val archivo = ArchivoUtils.crearArchivoInforme(context, informe.numeroInforme)
            try {
                WordReportBuilder.generarInformeWord(context.assets, informe, cliente, vehiculo, resultadosCompletos, fotosPorResultado, archivo)
            } catch (e: Exception) {
                return@withContext Result.failure<Unit>(e)
            }

            val informeFinalizado = informe.copy(rutaDocx = archivo.absolutePath, estado = EstadoInforme.Finalizado, fechaModificacion = System.currentTimeMillis())
            dao.guardarInforme(informeFinalizado)

            // Bug real: `archivo` vive en la cache privada de la app (ver ArchivoUtils) -- su
            // Uri de FileProvider sirve para compartir/adjuntar, pero el técnico nunca lo
            // encontraba buscando en Descargas como con cualquier otro módulo. Se copia también
            // a Descargas (mismo mecanismo que el resto de la app) y esa es la Uri que queda
            // registrada en el historial compartido -- así aparece en el explorador de archivos
            // Y sigue siendo adjuntable en el mail consolidado de "Finalizar visita".
            val uri = try {
                MediaStoreUtils.guardarEnDescargas(context, "${informe.numeroInforme.replace("/", "-")}.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document") { out ->
                    archivo.inputStream().use { it.copyTo(out) }
                }
            } catch (e: Exception) {
                return@withContext Result.failure<Unit>(e)
            }
            val historialServicioId = try {
                HistorialCompartidoHelper.registrar(context, cliente.razonSocial, vehiculo.patenteTractor, vehiculo.patenteSemirremolque, uri)
            } catch (e: Exception) {
                return@withContext Result.failure<Unit>(e)
            }
            // Se marca completo apenas el .docx queda generado, sin esperar a que el mail
            // también tenga éxito (mismo criterio que el dual-write de arriba) — así el ítem de
            // sesión no queda bloqueado si el envío de mail falla y el auditor solo cierra la
            // pantalla más tarde.
            if (sesionId != null && sesionItemId != null && historialServicioId > 0) {
                com.aies.coredata.AppDatabase.getInstance(context).sesionServicioDao().marcarItemCompleto(sesionItemId, historialServicioId)
            }

            if (!enviarPorSmtp) return@withContext Result.success(Unit)

            withContext(Dispatchers.Main) { textoOverlay = "Enviando mail…" }
            try {
                EmailService.enviarInforme(
                    config!!.host, config.puerto, config.usuario, config.contraseña, config.usarSsl,
                    destinatarios, "Informe de inspección ${informe.numeroInforme}",
                    "Se adjunta el informe de inspección generado por AIES Super App.", archivo.absolutePath
                )
                dao.guardarInforme(informeFinalizado.copy(estado = EstadoInforme.Enviado, fechaModificacion = System.currentTimeMillis()))
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        if (resultado.isSuccess) {
            textoOverlay = if (enviarPorSmtp) "¡Informe enviado!" else "¡Informe generado!"
            kotlinx.coroutines.delay(1200)
            generando = false
            onInformeResuelto()
        } else {
            generando = false
            mensaje = "El informe se generó y quedó guardado en el dispositivo, pero no se pudo completar: ${resultado.exceptionOrNull()?.message}"
        }
    }

    fun iniciarGeneracion() {
        scope.launch {
            // Fuera de sesión, sigue haciendo falta la config SMTP porque esta pantalla es la
            // que manda el mail. En sesión, ese chequeo no aplica (ver generarYEnviar).
            if (sesionId == null && !SmtpConfigService.estaConfigurado(context)) {
                mensaje = "Todavía no cargaste los datos de la cuenta que va a enviar los informes. Configuralo antes de generar el informe."
                return@launch
            }
            generarYEnviar(null)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().imePadding()) {
            Text(
                if (cargando) "Cargando…" else "${hallazgos.size} hallazgo(s) encontrados",
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp)
            )

            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!cargando && hallazgos.isEmpty()) Text("No se registraron hallazgos en este checklist.")
                hallazgos.forEach { hallazgo ->
                    val capturar = rememberCapturaFoto { archivo -> agregarFotoA(hallazgo, archivo) }
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("${hallazgo.item.numero}", color = Color.Gray)
                                Text(hallazgo.item.descripcion, modifier = Modifier.weight(1f))
                            }
                            Text(hallazgo.resultado.comentario ?: "", style = MaterialTheme.typography.bodyMedium)
                            Text("${hallazgo.fotos.size} foto(s) cargada(s)", style = MaterialTheme.typography.bodySmall)
                            OutlinedButton(onClick = { capturar() }) { Text("+ Agregar foto") }
                        }
                    }
                }
            }

            Button(enabled = !generando, onClick = { iniciarGeneracion() }, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Generar informe")
            }
        }

        if (generando) {
            Box(
                modifier = Modifier.fillMaxSize().padding(0.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator()
                    Text(textoOverlay, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }

    mensaje?.let { msg ->
        AlertDialog(
            onDismissRequest = { mensaje = null },
            title = { Text("Aviso") },
            text = { Text(msg) },
            confirmButton = { Button(onClick = { mensaje = null }) { Text("OK") } },
            dismissButton = if (msg.contains("Configuralo")) {
                { OutlinedButton(onClick = { mensaje = null; onConfigurarSmtp() }) { Text("Configurar ahora") } }
            } else null
        )
    }

    if (pedirDestinatarioSuelto) {
        AlertDialog(
            onDismissRequest = { pedirDestinatarioSuelto = false },
            title = { Text("Enviar informe") },
            text = {
                Column {
                    Text("No tenés destinatarios pregrabados. Ingresá un mail para esta vez (o cargá destinatarios fijos en Configurar correo):")
                    OutlinedTextField(value = destinatarioSuelto, onValueChange = { destinatarioSuelto = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    val mail = destinatarioSuelto.trim()
                    pedirDestinatarioSuelto = false
                    if (mail.isNotEmpty()) scope.launch { generarYEnviar(listOf(mail)) }
                }) { Text("Enviar") }
            },
            dismissButton = { OutlinedButton(onClick = { pedirDestinatarioSuelto = false }) { Text("Cancelar") } }
        )
    }
}
