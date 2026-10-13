package com.aies.feature.inspeccionejes

import com.aies.coreui.CampoTextoMayusculas
import android.Manifest
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.aies.feature.inspeccionejes.data.ArandelaSeguridadEstado
import com.aies.feature.inspeccionejes.data.ChecklistLado
import com.aies.feature.inspeccionejes.data.Foto
import com.aies.feature.inspeccionejes.data.FrenoEstado
import com.aies.feature.inspeccionejes.data.InspeccionEjesDao
import com.aies.feature.inspeccionejes.data.Lado
import com.aies.feature.inspeccionejes.data.LubricanteEstado
import com.aies.feature.inspeccionejes.data.PuntaDeEje
import com.aies.feature.inspeccionejes.data.ResultadoEstado
import com.aies.feature.inspeccionejes.data.RetenesEstado
import com.aies.feature.inspeccionejes.data.RodamientoEstado
import com.aies.feature.inspeccionejes.data.TipoFoto
import com.aies.feature.inspeccionejes.data.aTexto
import com.aies.feature.inspeccionejes.util.ArchivoUtils
import kotlinx.coroutines.launch
import java.io.File

/**
 * Cámara con permiso en runtime (la app declara CAMERA en el manifest, así que Android
 * exige el pedido explícito aunque ACTION_IMAGE_CAPTURE por sí solo no lo requeriría).
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
 * Componente genérico de pestañas Eje × Lado con autosave al cambiar de pestaña y cache en
 * memoria — ChecklistEjePage e InformeLiquidosPenetrantesPage del original son
 * estructuralmente idénticas salvo por el tipo de dato que editan, así que acá se
 * parametriza sobre ese tipo en vez de duplicar el scaffold de pestañas dos veces.
 */
@Composable
fun <T> TabsEjeLado(
    cantidadEjes: Int,
    obtenerNumeroEje: (T) -> Int,
    obtenerLado: (T) -> Lado,
    obtenerRevisado: (T) -> Boolean,
    cargarOCrear: suspend (eje: Int, lado: Lado) -> T,
    guardar: suspend (T) -> Unit,
    tituloBotonFinal: String,
    onFinalizar: suspend () -> Unit,
    contenido: @Composable (item: T, actualizar: ((T) -> T) -> Unit) -> Unit
) {
    val scope = rememberCoroutineScope()
    var ejeSeleccionado by remember { mutableIntStateOf(1) }
    var ladoSeleccionado by remember { mutableStateOf(Lado.Izquierdo) }
    val cache = remember { mutableStateMapOf<Pair<Int, Lado>, T>() }
    var itemActual by remember { mutableStateOf<T?>(null) }
    var cargando by remember { mutableStateOf(true) }
    var enviando by remember { mutableStateOf(false) }

    suspend fun cargar(eje: Int, lado: Lado) {
        val clave = eje to lado
        val item = cache[clave] ?: cargarOCrear(eje, lado).also { cache[clave] = it }
        itemActual = item
    }

    suspend fun guardarActual() {
        val item = itemActual ?: return
        guardar(item)
        cache[obtenerNumeroEje(item) to obtenerLado(item)] = item
    }

    LaunchedEffect(Unit) {
        cargar(ejeSeleccionado, ladoSeleccionado)
        cargando = false
    }

    fun revisadoDe(eje: Int, lado: Lado): Boolean = cache[eje to lado]?.let(obtenerRevisado) ?: false

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (eje in 1..cantidadEjes) {
                val seleccionado = eje == ejeSeleccionado
                val marca = if (revisadoDe(eje, Lado.Izquierdo) && revisadoDe(eje, Lado.Derecho)) " ✓" else ""
                Button(
                    onClick = {
                        scope.launch {
                            guardarActual()
                            ejeSeleccionado = eje
                            cargar(eje, ladoSeleccionado)
                        }
                    },
                    colors = if (seleccionado) androidx.compose.material3.ButtonDefaults.buttonColors()
                    else androidx.compose.material3.ButtonDefaults.outlinedButtonColors()
                ) { Text("Eje $eje$marca") }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(Lado.Izquierdo to "Izquierdo", Lado.Derecho to "Derecho").forEach { (lado, etiqueta) ->
                val seleccionado = lado == ladoSeleccionado
                val marca = if (revisadoDe(ejeSeleccionado, lado)) " ✓" else ""
                Button(
                    onClick = {
                        scope.launch {
                            guardarActual()
                            ladoSeleccionado = lado
                            cargar(ejeSeleccionado, lado)
                        }
                    },
                    colors = if (seleccionado) androidx.compose.material3.ButtonDefaults.buttonColors()
                    else androidx.compose.material3.ButtonDefaults.outlinedButtonColors()
                ) { Text("$etiqueta$marca") }
            }
        }

        if (!cargando) {
            Box(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
                itemActual?.let { item -> contenido(item) { transform -> itemActual = transform(item) } }
            }
        } else {
            Box(modifier = Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) { Text("Cargando…") }
        }

        Button(
            enabled = !enviando,
            onClick = {
                enviando = true
                scope.launch {
                    guardarActual()
                    onFinalizar()
                    enviando = false
                }
            },
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) { Text(if (enviando) "Guardando…" else tituloBotonFinal) }
    }
}

/** Galería simple de fotos (agregar por cámara + eliminar) compartida por los dos formularios. */
@Composable
private fun GaleriaFotos(
    fotos: List<Foto>,
    onAgregar: (descripcion: String, archivo: File) -> Unit,
    onEliminar: (Foto) -> Unit
) {
    var archivoPendienteDescripcion by remember { mutableStateOf<File?>(null) }
    val lanzarCaptura = rememberCapturaFoto { archivo -> archivoPendienteDescripcion = archivo }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(onClick = { lanzarCaptura() }) { Text("Sacar foto") }
        fotos.forEach { foto ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(foto.descripcion?.takeIf { it.isNotBlank() } ?: "(sin descripción)", modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { onEliminar(foto) }) { Text("Eliminar") }
            }
        }
    }

    val archivo = archivoPendienteDescripcion
    if (archivo != null) {
        var descripcion by remember(archivo) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { archivoPendienteDescripcion = null },
            title = { Text("Descripción de la foto") },
            text = {
                CampoTextoMayusculas(
                    value = descripcion, onValueChange = { descripcion = it },
                    label = { Text("Describí brevemente qué muestra") }, modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = { onAgregar(descripcion, archivo); archivoPendienteDescripcion = null }) { Text("Guardar") }
            },
            dismissButton = { OutlinedButton(onClick = { archivoPendienteDescripcion = null }) { Text("Descartar") } }
        )
    }
}

// ---------- Formulario de ChecklistLado ----------

@Composable
fun ChecklistLadoForm(
    item: ChecklistLado,
    actualizar: ((ChecklistLado) -> ChecklistLado) -> Unit,
    dao: InspeccionEjesDao
) {
    var fotos by remember(item.id) { mutableStateOf(listOf<Foto>()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(item.id) { fotos = dao.getFotosPorParent(item.id) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        CampoOkOTexto("Holgura en rodamientos de rueda armada", item.holguraRodamientos) { actualizar { c -> c.copy(holguraRodamientos = it) } }
        CampoOkOTexto("Ajuste de tuerca central", item.ajusteTuercaCentral) { actualizar { c -> c.copy(ajusteTuercaCentral = it) } }

        Text("Arandela de seguridad", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        DropdownEnum(ArandelaSeguridadEstado.entries, item.arandelaSeguridad, { it.aTexto() }) { actualizar { c -> c.copy(arandelaSeguridad = it) } }

        Text("Lubricante de rodamientos", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        DropdownEnum(LubricanteEstado.entries, item.lubricanteRodamientos, { it.aTexto() }) { actualizar { c -> c.copy(lubricanteRodamientos = it) } }

        Text("Estado de rodamiento", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        DropdownEnum(RodamientoEstado.entries, item.estadoRodamiento, { it.aTexto() }) { actualizar { c -> c.copy(estadoRodamiento = it) } }

        CampoOkOTexto("Desgaste de eje en alojamiento de pista interior", item.desgasteEjeAlojamientoPistaInterior) { actualizar { c -> c.copy(desgasteEjeAlojamientoPistaInterior = it) } }
        CampoOkOTexto("Desgaste de alojamiento de pistas exteriores en maza", item.desgasteAlojamientoPistasExterioresMaza) { actualizar { c -> c.copy(desgasteAlojamientoPistasExterioresMaza = it) } }
        CampoOkOTexto("Fisuras en puntera de eje", item.fisurasPunteraEje) { actualizar { c -> c.copy(fisurasPunteraEje = it) } }
        CampoOkOTexto("Holgura de bujes de tensor / brazo de suspensión", item.holguraBujesTensor) { actualizar { c -> c.copy(holguraBujesTensor = it) } }

        Text("Retenes", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        DropdownEnum(RetenesEstado.entries, item.retenes, { it.aTexto() }) { actualizar { c -> c.copy(retenes = it) } }

        Text("Campana de freno", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        DropdownEnum(FrenoEstado.entries, item.campanaFreno, { it.aTexto() }) { actualizar { c -> c.copy(campanaFreno = it) } }

        Text("Disco de freno", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        DropdownEnum(FrenoEstado.entries, item.discoFreno, { it.aTexto() }) { actualizar { c -> c.copy(discoFreno = it) } }

        Text("Resultado", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        DropdownEnum(ResultadoEstado.entries, item.resultado, { it.aTexto() }) { actualizar { c -> c.copy(resultado = it) } }

        CampoTextoMayusculas(
            value = item.observaciones ?: "", onValueChange = { actualizar { c -> c.copy(observaciones = it) } },
            label = { Text("Observaciones") }, modifier = Modifier.fillMaxWidth()
        )

        Text("Infografía (fotos de las piezas)", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        GaleriaFotos(
            fotos = fotos,
            onAgregar = { descripcion, archivo ->
                scope.launch {
                    val foto = Foto(parentId = item.id, tipo = TipoFoto.Infografia, rutaLocal = archivo.absolutePath, descripcion = descripcion)
                    dao.insertarFoto(foto)
                    fotos = dao.getFotosPorParent(item.id)
                }
            },
            onEliminar = { foto ->
                scope.launch {
                    dao.eliminarFotoPorId(foto.id)
                    File(foto.rutaLocal).let { if (it.exists()) it.delete() }
                    fotos = dao.getFotosPorParent(item.id)
                }
            }
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = item.revisado, onCheckedChange = { actualizar { c -> c.copy(revisado = it) } })
            Text("Marcar este lado como revisado")
        }
    }
}

// ---------- Formulario de PuntaDeEje ----------

@Composable
fun PuntaDeEjeForm(
    item: PuntaDeEje,
    actualizar: ((PuntaDeEje) -> PuntaDeEje) -> Unit,
    dao: InspeccionEjesDao
) {
    var fotos by remember(item.id) { mutableStateOf(listOf<Foto>()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(item.id) { fotos = dao.getFotosPorParent(item.id) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Resultado", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        DropdownEnum(ResultadoEstado.entries, item.resultado, { it.aTexto() }) { actualizar { c -> c.copy(resultado = it) } }

        CampoTextoMayusculas(
            value = item.observaciones ?: "", onValueChange = { actualizar { c -> c.copy(observaciones = it) } },
            label = { Text("Observaciones generales") }, modifier = Modifier.fillMaxWidth()
        )

        Text("Fotos", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        GaleriaFotos(
            fotos = fotos,
            onAgregar = { descripcion, archivo ->
                scope.launch {
                    val foto = Foto(parentId = item.id, tipo = TipoFoto.LiquidosPenetrantes, rutaLocal = archivo.absolutePath, descripcion = descripcion)
                    dao.insertarFoto(foto)
                    fotos = dao.getFotosPorParent(item.id)
                }
            },
            onEliminar = { foto ->
                scope.launch {
                    dao.eliminarFotoPorId(foto.id)
                    File(foto.rutaLocal).let { if (it.exists()) it.delete() }
                    fotos = dao.getFotosPorParent(item.id)
                }
            }
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = item.revisado, onCheckedChange = { actualizar { c -> c.copy(revisado = it) } })
            Text("Marcar esta punta de eje como revisada")
        }
    }
}

// ---------- Helpers de formulario compartidos ----------

@Composable
private fun CampoOkOTexto(etiqueta: String, valor: String, onCambio: (String) -> Unit) {
    val esOk = valor == "OK"
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(etiqueta, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (esOk) "OK" else "Mal", color = if (esOk) androidx.compose.ui.graphics.Color(0xFF2E7D32) else androidx.compose.ui.graphics.Color(0xFFC62828))
            Switch(checked = esOk, onCheckedChange = { onCambio(if (it) "OK" else "") })
        }
        if (!esOk) {
            CampoTextoMayusculas(
                value = valor.takeIf { it != "OK" } ?: "", onValueChange = onCambio,
                label = { Text("Descripción del problema") }, modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <E> DropdownEnum(opciones: List<E>, seleccionado: E, texto: (E) -> String, onSeleccionar: (E) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = texto(seleccionado), onValueChange = {}, readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            opciones.forEach { opcion ->
                DropdownMenuItem(text = { Text(texto(opcion)) }, onClick = { onSeleccionar(opcion); expanded = false })
            }
        }
    }
}
