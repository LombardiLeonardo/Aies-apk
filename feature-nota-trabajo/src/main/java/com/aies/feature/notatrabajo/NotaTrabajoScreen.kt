package com.aies.feature.notatrabajo

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.aies.coredata.AppDatabase
import com.aies.coredata.dao.ServicioDelDia
import com.aies.coredata.entities.Unidad
import com.aies.coredata.remote.DocumentoStorageHelper
import com.aies.feature.notatrabajo.pdf.NotaTrabajoPdfBuilder
import com.aies.feature.notatrabajo.util.MediaStoreUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val FMT_FECHA = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
private val FMT_HORA = SimpleDateFormat("HH:mm", Locale.getDefault())

/** Inicio y fin (millis) del día calendario que contiene [fechaMillis], en el huso horario local. */
private fun rangoDelDia(fechaMillis: Long): Pair<Long, Long> {
    val zona = ZoneId.systemDefault()
    val fecha = Instant.ofEpochMilli(fechaMillis).atZone(zona).toLocalDate()
    val inicio = fecha.atStartOfDay(zona).toInstant().toEpochMilli()
    val fin = fecha.plusDays(1).atStartOfDay(zona).toInstant().toEpochMilli() - 1
    return inicio to fin
}

/**
 * Una "unidad" para el técnico es el par tractor+semirremolque de una misma visita, pero
 * HistorialServicio guarda una fila por cada pata por separado (Calibrado/Certificados/Informes
 * escriben tanto la Unidad del tractor como la del semi) -- sin esto, la lista de "Unidades con
 * servicio ese día" mostraba el tractor y el semi de una misma visita como dos entradas sueltas.
 * Se agrupan acá usando la SesionServicio que originó la visita (mismo criterio que ya usa
 * "Finalizar visita" en feature-sesion para resolver las 2 patas antes de armar la nota).
 */
private data class GrupoUnidadDelDia(
    val unidadPrincipal: Unidad, // la del semi si se pudo resolver -- es la que espera NotaTrabajoPdfBuilder
    val unidadIds: List<Long>,
    val patenteTractor: String?,
    val patenteSemi: String?
) {
    val etiqueta: String
        get() = if (patenteTractor != null && patenteSemi != null && patenteTractor != patenteSemi) {
            "$patenteTractor / $patenteSemi"
        } else {
            "${unidadPrincipal.placa} — ${unidadPrincipal.tipoUnidad}"
        }
}

/**
 * Nota de trabajo diaria: agrupa, para una fecha, qué unidades tuvieron servicio y el
 * detalle de cada uno (leyendo el HistorialServicio compartido que ya escriben
 * feature-inspecciones/certificados/informes), y permite generar un PDF consolidado por
 * unidad para pasarle a facturación. Es puramente un consumidor de core-data: no depende
 * de ninguno de los feature modules que generan esos registros.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotaTrabajoScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getInstance(context) }
    val unidadDao = remember { db.unidadDao() }
    val historialDao = remember { db.historialServicioDao() }
    val sesionServicioDao = remember { db.sesionServicioDao() }

    var fechaMillis by remember { mutableStateOf(Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis) }
    var mostrarDatePicker by remember { mutableStateOf(false) }

    var cargando by remember { mutableStateOf(true) }
    var unidadesDelDia by remember { mutableStateOf(listOf<GrupoUnidadDelDia>()) }
    var grupoSeleccionado by remember { mutableStateOf<GrupoUnidadDelDia?>(null) }
    var serviciosUnidad by remember { mutableStateOf(listOf<ServicioDelDia>()) }
    var generando by remember { mutableStateOf(false) }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

    suspend fun cargarUnidadesDelDia() {
        cargando = true
        grupoSeleccionado = null
        serviciosUnidad = emptyList()
        val (inicio, fin) = rangoDelDia(fechaMillis)
        unidadesDelDia = withContext(Dispatchers.IO) {
            val ids = historialDao.unidadesConServicioEnElDia(inicio, fin)
            val unidades = ids.mapNotNull { unidadDao.getPorId(it) }
            // BUGFIX (reportado: "aparecen siempre 2, es una sola unidad" -- ej. tractor+semi de
            // una misma visita listados en dos filas separadas): sesiones_servicio.unidadId solo
            // guarda el id de UNA de las dos patas (la elegida al arrancar "Nueva Visita"), la
            // otra pata queda identificada solo por patenteTractor/patenteSemi como texto. Antes
            // se buscaba la sesión por unidad_id de cada pata por separado, así que la pata que
            // no coincidía con unidadId nunca encontraba sesión y caía sola en su propio grupo.
            // Ahora se trae UNA vez todas las sesiones del día y se matchea por patente
            // normalizada (sin espacios) contra cada Unidad -- ambas patas encuentran la misma
            // sesión sin importar cuál haya quedado en unidadId.
            fun normalizada(placa: String) = placa.replace(" ", "").uppercase()
            val sesionesDelDia = sesionServicioDao.sesionesDelDia(inicio, fin)
            val sesionPorPlaca = linkedMapOf<String, com.aies.coredata.entities.SesionServicio>()
            sesionesDelDia.sortedBy { it.fecha }.forEach { sesion ->
                normalizada(sesion.patenteTractor).takeIf { it.isNotBlank() }?.let { sesionPorPlaca[it] = sesion }
                normalizada(sesion.patenteSemi).takeIf { it.isNotBlank() }?.let { sesionPorPlaca[it] = sesion }
            }
            // Agrupar por la sesión que originó cada visita, no por unidad_id suelto -- así el
            // tractor y el semi de una misma visita quedan como una sola entrada.
            val gruposPorClave = linkedMapOf<String, MutableList<Unidad>>()
            unidades.forEach { u ->
                val sesion = sesionPorPlaca[normalizada(u.placa)]
                val clave = if (sesion != null) "${sesion.patenteTractor}|${sesion.patenteSemi}" else "sola:${u.placa}"
                gruposPorClave.getOrPut(clave) { mutableListOf() }.add(u)
            }
            gruposPorClave.entries.map { (clave, lista) ->
                val (patenteTractor, patenteSemi) = if (clave.startsWith("sola:")) null to null
                else clave.substringBefore("|") to clave.substringAfter("|")
                // Completa el grupo con la pata que no haya tenido servicio ese día (ej. solo el
                // tractor generó documento) para que el detalle igual la incluya -- mismo criterio
                // que "Finalizar visita" en feature-sesion al resolver unidadIds.
                val idsCompletos = (
                    lista.map { it.id } +
                        listOfNotNull(
                            patenteTractor?.let { unidadDao.getPorPlaca(it)?.id },
                            patenteSemi?.let { unidadDao.getPorPlaca(it)?.id }
                        )
                    ).distinct()
                val principal = lista.firstOrNull { it.tipoUnidad == "SEMI" }
                    ?: patenteSemi?.let { unidadDao.getPorPlaca(it) }
                    ?: lista.first()
                GrupoUnidadDelDia(principal, idsCompletos, patenteTractor, patenteSemi)
            }
        }
        cargando = false
    }

    suspend fun seleccionarGrupo(grupo: GrupoUnidadDelDia) {
        grupoSeleccionado = grupo
        val (inicio, fin) = rangoDelDia(fechaMillis)
        serviciosUnidad = withContext(Dispatchers.IO) { historialDao.serviciosDeVariasUnidadesEnElDia(grupo.unidadIds, inicio, fin) }
    }

    LaunchedEffect(fechaMillis) { cargarUnidadesDelDia() }

    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Nota de Trabajo", style = MaterialTheme.typography.titleLarge)

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Fecha: ${FMT_FECHA.format(Date(fechaMillis))}", style = MaterialTheme.typography.bodyLarge)
            OutlinedButton(onClick = { mostrarDatePicker = true }) { Text("📅 Cambiar") }
        }

        if (mostrarDatePicker) {
            val state = rememberDatePickerState(initialSelectedDateMillis = fechaMillis)
            DatePickerDialog(
                onDismissRequest = { mostrarDatePicker = false },
                confirmButton = {
                    Button(onClick = {
                        state.selectedDateMillis?.let { fechaMillis = it }
                        mostrarDatePicker = false
                    }) { Text("Aceptar") }
                },
                dismissButton = { OutlinedButton(onClick = { mostrarDatePicker = false }) { Text("Cancelar") } }
            ) { DatePicker(state = state) }
        }

        when {
            cargando -> Text("Cargando…", style = MaterialTheme.typography.bodyMedium)
            unidadesDelDia.isEmpty() -> Text("No hubo unidades con servicios este día.", style = MaterialTheme.typography.bodyMedium)
            else -> {
                Text("Unidades con servicio ese día:", style = MaterialTheme.typography.titleSmall)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    unidadesDelDia.forEach { grupo ->
                        val seleccionado = grupo.unidadIds == grupoSeleccionado?.unidadIds
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { scope.launch { seleccionarGrupo(grupo) } }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (seleccionado) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(grupo.etiqueta, style = MaterialTheme.typography.bodyLarge)
                                Text(grupo.unidadPrincipal.cliente, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }

        val grupo = grupoSeleccionado
        val unidad = grupo?.unidadPrincipal
        if (grupo != null && unidad != null) {
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            Text("Detalle — ${grupo.etiqueta}", style = MaterialTheme.typography.titleMedium)

            if (serviciosUnidad.isEmpty()) {
                Text("Sin servicios registrados.", style = MaterialTheme.typography.bodyMedium)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    serviciosUnidad.forEach { servicio ->
                        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(6.dp)) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(servicio.tipoServicioNombre, style = MaterialTheme.typography.bodyLarge)
                                Text("Auditor: ${servicio.auditor} — ${FMT_HORA.format(Date(servicio.fecha))}", style = MaterialTheme.typography.bodySmall)
                                if (servicio.documentoPath.isNotBlank() || servicio.documentoUrl.isNotBlank()) {
                                    OutlinedButton(onClick = {
                                        scope.launch {
                                            // Fase F: intenta el archivo local primero; si no
                                            // existe en este dispositivo, cae a la URL pública de
                                            // Supabase Storage -- ver
                                            // DocumentoStorageHelper.abrirDocumentoLocalOUrl en
                                            // core-data, compartida con feature-sesion.
                                            val abierto = DocumentoStorageHelper.abrirDocumentoLocalOUrl(
                                                context, servicio.documentoPath, servicio.documentoUrl
                                            )
                                            if (!abierto) toast("No se pudo abrir el documento")
                                        }
                                    }) { Text("Abrir documento") }
                                }
                            }
                        }
                    }
                }
            }

            Button(
                enabled = !generando && serviciosUnidad.isNotEmpty(),
                onClick = {
                    generando = true
                    scope.launch {
                        try {
                            val pdf = withContext(Dispatchers.IO) { NotaTrabajoPdfBuilder.generar(context, unidad, fechaMillis, serviciosUnidad, patenteTractor = grupo.patenteTractor) }
                            val nombreArchivo = "NotaTrabajo_${unidad.placa}_${SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date(fechaMillis))}.pdf".replace(" ", "")
                            withContext(Dispatchers.IO) {
                                MediaStoreUtils.guardarEnDescargas(context, nombreArchivo, "application/pdf") { pdf.writeTo(it) }
                            }
                            pdf.close()
                            toast("✅ Nota de trabajo generada en Descargas")
                        } catch (e: Exception) {
                            toast("❌ Error: ${e.message}")
                        } finally {
                            generando = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (generando) "Generando…" else "Generar nota de trabajo") }
        }
    }
}
