package com.aies.feature.calibrado

import android.net.Uri
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.aies.coredata.AppDatabase
import com.aies.coredata.entities.Calibracion
import com.aies.coredata.entities.CalibracionCisterna
import androidx.compose.runtime.mutableStateMapOf
import com.aies.coreui.CampoTextoMayusculas
import com.aies.coredata.entities.CalibracionDescargaPatron
import com.aies.feature.calibrado.data.HistorialCompartidoHelper
import com.aies.feature.calibrado.pdf.CalibradoInternoPdfBuilder
import com.aies.feature.calibrado.pdf.CalibradoPdfBuilder
import com.aies.feature.calibrado.util.MediaStoreUtils
import com.aies.coredata.remote.DocumentoStorageHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val FMT_DDMMYYYY = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

/** Puntos fijos de volumen de la tabla "CONTROL DE DESCARGA PATRON" (R 14-03). */
private val PUNTOS_DESCARGA_PATRON = listOf(3000, 1000, 500, 100, 20)

/** Estado editable de una cisterna dentro del formulario. */
private class CisternaFormState {
    var capacidad by mutableStateOf("")
    var enraseMm by mutableStateOf("")
    var vacioMm by mutableStateOf("")
    var vacioLts by mutableStateOf("")
    var rebalseMm by mutableStateOf("")
    var precintoEnrase by mutableStateOf("")
    var precintoBoca by mutableStateOf("")
    var precintoSoporte by mutableStateOf("")

    // Planilla de Calibración de Uso Interno (R 14-03): trazabilidad/posición de sensores de
    // Informe de Carga Segura.
    var sensorMarca by mutableStateOf("")
    var sensorNumeroSerie by mutableStateOf("")
    var sensorCumple by mutableStateOf(false)
    var posicionCumple by mutableStateOf(false)
    var posicionObservacion by mutableStateOf("")
    var litrosSensorRebalse by mutableStateOf("")

    // CONTROL DE DESCARGA PATRON: una lista de lecturas (texto) por cada punto fijo, ampliable
    // con "+ Agregar lectura".
    val descargaPatron = mutableStateMapOf<Int, MutableList<String>>().apply {
        PUNTOS_DESCARGA_PATRON.forEach { put(it, mutableStateListOf("")) }
    }
}

/**
 * Generador de certificados de calibración de tanque cisterna. A diferencia de los demás
 * módulos, esta pantalla no tiene base de datos local propia: escribe directo en las
 * tablas compartidas de core-data (Calibracion/CalibracionCisterna) porque feature-informes
 * necesita leerlas para precargar su wizard (ver HistorialCompartidoHelper y
 * CalibracionDao.calibracionVigenteDeUnidad).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlCalibradoScreen(
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
    // domicilioPrecargado/auditorPrecargado se aceptan por uniformidad con la firma que usa
    // feature-sesion para los otros módulos, pero este formulario no tiene esos campos (no hay
    // "domicilio" ni un "auditor" por-inspección acá, el certificado lo firma un responsable
    // técnico fijo — ver HistorialCompartidoHelper) así que quedan sin usar a propósito.
    // anioTractorPrecargado/modeloTractorPrecargado/anioSemiPrecargado se aceptan por
    // uniformidad con Cambio 1, pero este formulario no tiene campos de "año" ni "modelo de
    // tractor" (solo patente/marca/tipoEjes/rodado) — quedan sin usar a propósito.
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getInstance(context) }
    val unidadDao = remember { db.unidadDao() }
    val calibracionDao = remember { db.calibracionDao() }
    val sesionServicioDao = remember { db.sesionServicioDao() }

    var transportista by remember { mutableStateOf(clientePrecargado ?: "") }
    var cuit by remember { mutableStateOf(cuitPrecargado ?: "") }

    var patenteTractor by remember { mutableStateOf("") }
    var marcaTractor by remember { mutableStateOf("") }
    var tipoEjesTractor by remember { mutableStateOf("") }
    var rodadoTractor by remember { mutableStateOf("") }
    var tara by remember { mutableStateOf("") }
    var maximoPesoPermitido by remember { mutableStateOf("") }

    var patenteSemi by remember { mutableStateOf("") }
    var marcaSemi by remember { mutableStateOf("") }
    var modeloSemi by remember { mutableStateOf("") }
    var tipoEjesSemi by remember { mutableStateOf("") }
    var rodadoSemi by remember { mutableStateOf("") }
    var cotaAnterior by remember { mutableStateOf("") }
    var cotaPosterior by remember { mutableStateOf("") }
    var volumenSemi by remember { mutableStateOf("") }

    // Planilla de uso interno (R 14-03): horario de inicio/fin del servicio, formato "HH:mm".
    var horaInicioTexto by remember { mutableStateOf("") }
    var horaFinTexto by remember { mutableStateOf("") }

    var vigenciaAnios by remember { mutableIntStateOf(1) }
    var cantidadCisternas by remember { mutableIntStateOf(1) }
    val cisternas = remember { mutableStateListOf<CisternaFormState>().apply { repeat(30) { add(CisternaFormState()) } } }

    var generando by remember { mutableStateOf(false) }

    // BUGFIX/hardening encontrado por EndToEndFlowTest: ver doc completa en
    // ControlVisualInternaScreen.kt#safeToast -- misma causa (una continuación de coroutine
    // puede resumir sin Looper bajo ComposeTestRule), mismo fix (no-op en uso real de la app).
    fun toast(msg: String) {
        if (Looper.myLooper() == null) {
            android.util.Log.w("ControlCalibrado", "Toast omitido (sin Looper en este hilo): $msg")
            return
        }
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
    }

    /** Combina [horaTexto] ("HH:mm") con la fecha de [baseMillis] para armar un epoch millis
     *  completo -- consistente con que el resto del esquema usa epoch millis, no minutos-desde-
     *  medianoche. Devuelve null si [horaTexto] está vacío o no tiene el formato esperado. */
    fun combinarFechaYHora(baseMillis: Long, horaTexto: String): Long? {
        val partes = horaTexto.trim().split(":")
        if (partes.size != 2) return null
        val h = partes[0].toIntOrNull() ?: return null
        val m = partes[1].toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return Calendar.getInstance().apply {
            timeInMillis = baseMillis
            set(Calendar.HOUR_OF_DAY, h)
            set(Calendar.MINUTE, m)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    suspend fun buscarUnidadPorSemi() {
        if (patenteSemi.isBlank()) return
        val unidad = withContext(Dispatchers.IO) { unidadDao.getPorPlaca(patenteSemi.trim()) }
        if (unidad != null) cantidadCisternas = unidad.cantidadCisternas.coerceIn(1, cisternas.size)
    }

    // Modo sesión: la unidad ya viene resuelta desde feature-sesion, no hace falta buscarla por
    // patente — igual queda editable después (el usuario puede volver a tocar "Buscar").
    LaunchedEffect(unidadIdPrecargada) {
        val id = unidadIdPrecargada ?: return@LaunchedEffect
        val unidad = withContext(Dispatchers.IO) { unidadDao.getPorId(id) }
        if (unidad != null) {
            patenteSemi = unidad.placa
            // Cambio 2: se prefiere el valor recién confirmado en Nueva Visita (puede haber
            // corregido lo guardado en la Unidad) sobre releer unidad.cantidadCisternas.
            cantidadCisternas = (cantidadCisternasPrecargada ?: unidad.cantidadCisternas).coerceIn(1, cisternas.size)
        }
        // Cambio 1: precarga los campos de tractor/semirremolque de esta visita puntual — solo
        // si el usuario todavía no los tocó (no pisa ediciones manuales).
        if (patenteTractor.isBlank()) patenteTractorPrecargado?.let { patenteTractor = it }
        if (marcaTractor.isBlank()) marcaTractorPrecargado?.let { marcaTractor = it }
        if (marcaSemi.isBlank()) marcaSemiPrecargado?.let { marcaSemi = it }
        if (modeloSemi.isBlank()) modeloSemiPrecargado?.let { modeloSemi = it }
        patenteSemiPrecargado?.let { if (patenteSemi.isBlank()) patenteSemi = it }
    }

    fun validar(): Boolean {
        if (patenteTractor.isBlank() || patenteSemi.isBlank()) {
            toast("⚠️ Completá patente tractor y patente semirremolque"); return false
        }
        if (cantidadCisternas < 1) { toast("⚠️ Debe haber al menos 1 cisterna"); return false }
        if (cisternas[0].capacidad.toDoubleOrNull() == null) {
            toast("⚠️ Ingresá la capacidad de la cisterna N° 1"); return false
        }
        return true
    }

    fun generarCertificado() {
        if (!validar()) return
        generando = true
        scope.launch {
            try {
                // SEC-004 (docs/security/AUDITORIA-2026-09-19.md): el número correlativo real ya
                // NO se pide de antemano -- lo asigna un trigger server-side en el INSERT mismo
                // (ver CalibracionDao.insertarCalibracion / schema.sql), así queda garantizado
                // que el número impreso en el PDF es el mismo que quedó guardado en la base, sin
                // depender de que el cliente haya pedido el número "correcto" antes de insertar.
                var numeroCertificado = 0L

                val ahora = System.currentTimeMillis()
                val fechaVencimiento = Calendar.getInstance().apply {
                    timeInMillis = ahora
                    add(Calendar.YEAR, vigenciaAnios)
                }.timeInMillis

                val horaInicioMillis = combinarFechaYHora(ahora, horaInicioTexto)
                val horaFinMillis = combinarFechaYHora(ahora, horaFinTexto)

                var idGenerado = 0L
                var cisternasGuardadas: List<CalibracionCisterna> = emptyList()

                withContext(Dispatchers.IO) {
                    val calibracion = Calibracion(
                        unidadId = 0, // se resuelve abajo antes de insertar
                        transportista = transportista, cuit = cuit,
                        fecha = ahora, vigenciaAnios = vigenciaAnios, fechaVencimiento = fechaVencimiento,
                        patenteTractor = patenteTractor, marcaTractor = marcaTractor,
                        tipoEjesTractor = tipoEjesTractor, rodadoTractor = rodadoTractor,
                        tara = tara.toDoubleOrNull(), maximoPesoPermitido = maximoPesoPermitido.toDoubleOrNull(),
                        patenteSemi = patenteSemi, marcaSemi = marcaSemi, modeloSemi = modeloSemi,
                        tipoEjesSemi = tipoEjesSemi, rodadoSemi = rodadoSemi,
                        cotaAnterior = cotaAnterior.toDoubleOrNull(), cotaPosterior = cotaPosterior.toDoubleOrNull(),
                        volumenSemi = volumenSemi.toDoubleOrNull(),
                        horaInicio = horaInicioMillis, horaFin = horaFinMillis
                    )

                    // Resuelve/crea la Unidad semirremolque (misma que HistorialCompartidoHelper
                    // termina registrando) para poder asociar unidadId antes de insertar.
                    var unidad = unidadDao.getPorPlaca(patenteSemi)
                    if (unidad == null) {
                        unidadDao.insertar(com.aies.coredata.entities.Unidad(placa = patenteSemi, tipoUnidad = "SEMI", cliente = transportista, fechaRegistro = ahora))
                        unidad = unidadDao.getPorPlaca(patenteSemi)
                    }
                    val unidadId = unidad?.id ?: 0

                    val insertada = calibracionDao.insertarCalibracion(calibracion.copy(unidadId = unidadId))
                    idGenerado = insertada.id
                    numeroCertificado = insertada.numeroCertificado

                    val cisternasEntidad = (0 until cantidadCisternas).map { i ->
                        val ci = cisternas[i]
                        CalibracionCisterna(
                            calibracionId = idGenerado,
                            cisternaNumero = i + 1,
                            capacidad = ci.capacidad.toDoubleOrNull(),
                            enraseMm = ci.enraseMm.toDoubleOrNull(),
                            vacioMm = ci.vacioMm.toDoubleOrNull(),
                            vacioLts = ci.vacioLts.toDoubleOrNull(),
                            rebalseMm = ci.rebalseMm.toDoubleOrNull(),
                            precintoEnrase = ci.precintoEnrase,
                            precintoBoca = ci.precintoBoca,
                            precintoSoporte = ci.precintoSoporte,
                            sensorMarca = ci.sensorMarca.ifBlank { null },
                            sensorNumeroSerie = ci.sensorNumeroSerie.ifBlank { null },
                            sensorCumple = ci.sensorCumple,
                            posicionCumple = ci.posicionCumple,
                            posicionObservacion = ci.posicionObservacion.ifBlank { null },
                            litrosSensorRebalse = ci.litrosSensorRebalse.toDoubleOrNull()
                        )
                    }
                    // insertarCisternas devuelve las cisternas YA con el id real asignado por
                    // Supabase -- se necesita para asociar cada lectura de descarga patrón a su
                    // calibracion_cisterna_id (la entidad local de arriba todavía tiene id = 0).
                    cisternasGuardadas = calibracionDao.insertarCisternas(cisternasEntidad)

                    val lecturas = (0 until cantidadCisternas).flatMap { i ->
                        val cisternaId = cisternasGuardadas.getOrNull(i)?.id ?: return@flatMap emptyList<CalibracionDescargaPatron>()
                        cisternas[i].descargaPatron.flatMap { (punto, valores) ->
                            valores.mapIndexedNotNull { idx, texto ->
                                val valor = texto.toDoubleOrNull() ?: return@mapIndexedNotNull null
                                CalibracionDescargaPatron(
                                    calibracionCisternaId = cisternaId,
                                    puntoVolumenLts = punto.toDouble(),
                                    numeroLectura = idx + 1,
                                    valorMedido = valor
                                )
                            }
                        }
                    }
                    if (lecturas.isNotEmpty()) calibracionDao.insertarDescargaPatron(lecturas)
                }

                val datosPdf = CalibradoPdfBuilder.DatosCalibracion(
                    numero = numeroCertificado.toString(),
                    fechaEmision = FMT_DDMMYYYY.format(Date(ahora)),
                    fechaVencimiento = FMT_DDMMYYYY.format(Date(fechaVencimiento)),
                    transportista = transportista, cuit = cuit,
                    patenteTractor = patenteTractor, marcaTractor = marcaTractor,
                    tipoEjesTractor = tipoEjesTractor, rodadoTractor = rodadoTractor,
                    tara = tara, maximoPesoPermitido = maximoPesoPermitido,
                    patenteSemi = patenteSemi, marcaSemi = marcaSemi, modeloSemi = modeloSemi,
                    tipoEjesSemi = tipoEjesSemi, rodadoSemi = rodadoSemi,
                    cotaAnterior = cotaAnterior, cotaPosterior = cotaPosterior, volumenSemi = volumenSemi,
                    cisternas = cisternasGuardadas.map {
                        CalibradoPdfBuilder.DatosCisterna(
                            cisternaNumero = it.cisternaNumero,
                            capacidad = it.capacidad?.toString() ?: "",
                            enraseMm = it.enraseMm?.toString() ?: "",
                            vacioMm = it.vacioMm?.toString() ?: "",
                            vacioLts = it.vacioLts?.toString() ?: "",
                            rebalseMm = it.rebalseMm?.toString() ?: "",
                            precintoEnrase = it.precintoEnrase,
                            precintoBoca = it.precintoBoca,
                            precintoSoporte = it.precintoSoporte
                        )
                    }
                )

                val datosPdfInterno = CalibradoInternoPdfBuilder.DatosCalibracionInterno(
                    numero = numeroCertificado.toString(),
                    fecha = FMT_DDMMYYYY.format(Date(ahora)),
                    horaInicio = horaInicioTexto, horaFin = horaFinTexto,
                    transportista = transportista,
                    patenteTractor = patenteTractor, marcaTractor = marcaTractor,
                    tipoEjesTractor = tipoEjesTractor, rodadoTractor = rodadoTractor,
                    tara = tara, maximoPesoPermitido = maximoPesoPermitido,
                    patenteSemi = patenteSemi, marcaSemi = marcaSemi, modeloSemi = modeloSemi,
                    tipoEjesSemi = tipoEjesSemi, rodadoSemi = rodadoSemi,
                    cotaAnterior = cotaAnterior, cotaPosterior = cotaPosterior, volumenSemi = volumenSemi,
                    cisternas = datosPdf.cisternas,
                    sensores = (0 until cantidadCisternas).map { i ->
                        val ci = cisternas[i]
                        CalibradoInternoPdfBuilder.DatosSensorCisterna(
                            cisternaNumero = i + 1,
                            sensorMarca = ci.sensorMarca,
                            sensorNumeroSerie = ci.sensorNumeroSerie,
                            sensorCumple = if (ci.sensorMarca.isBlank() && ci.sensorNumeroSerie.isBlank()) "" else if (ci.sensorCumple) "SI" else "NO",
                            posicionCumple = if (ci.posicionObservacion.isBlank() && ci.litrosSensorRebalse.isBlank()) "" else if (ci.posicionCumple) "SI" else "NO",
                            posicionObservacion = ci.posicionObservacion,
                            litrosSensorRebalse = ci.litrosSensorRebalse
                        )
                    },
                    descargaPatron = (0 until cantidadCisternas).associate { i ->
                        (i + 1) to cisternas[i].descargaPatron.mapValues { (_, lecturas) -> lecturas.toList() }
                    }
                )

                var uri: Uri? = null
                var uriInterno: Uri? = null
                var historialServicioId = -1L
                withContext(Dispatchers.IO) {
                    val pdf = CalibradoPdfBuilder.generar(context.assets, datosPdf)
                    val nombreArchivo = "calibrado_${idGenerado}_${patenteSemi.replace(" ", "")}.pdf"
                    uri = MediaStoreUtils.guardarEnDescargas(context, nombreArchivo, "application/pdf") { pdf.writeTo(it) }
                    pdf.close()
                    historialServicioId = HistorialCompartidoHelper.registrar(context, transportista, patenteTractor, patenteSemi, uri)

                    val pdfInterno = CalibradoInternoPdfBuilder.generar(datosPdfInterno)
                    val nombreArchivoInterno = "calibrado_interno_${idGenerado}_${patenteSemi.replace(" ", "")}.pdf"
                    uriInterno = MediaStoreUtils.guardarEnDescargas(context, nombreArchivoInterno, "application/pdf") { pdfInterno.writeTo(it) }
                    pdfInterno.close()
                    if (uriInterno != null) {
                        calibracionDao.actualizarDocumentoInterno(idGenerado, path = uriInterno.toString(), url = null)
                    }
                }

                // Fase F, mismo criterio que HistorialCompartidoHelper: subida a Storage del
                // documento interno en un scope desacoplado del llamador, no se espera a que
                // termine para no demorarle el flujo al técnico.
                uriInterno?.let { uriInternoNoNulo ->
                    CoroutineScope(Dispatchers.IO).launch {
                        val url = DocumentoStorageHelper.subirYObtenerUrlPublica(context, uriInternoNoNulo, "calibracion-interna", "$idGenerado")
                        if (url != null) calibracionDao.actualizarDocumentoInterno(idGenerado, path = null, url = url)
                    }
                }

                if (sesionId != null && sesionItemId != null) {
                    if (historialServicioId > 0) {
                        withContext(Dispatchers.IO) { sesionServicioDao.marcarItemCompleto(sesionItemId, historialServicioId) }
                    }
                    toast("✅ Certificado de calibración generado en Descargas")
                    onVolverASesion?.invoke()
                } else {
                    toast("✅ Certificado de calibración generado en Descargas")
                }
            } catch (e: Exception) {
                toast("❌ Error: ${e.message}")
            } finally {
                generando = false
            }
        }
    }

    Column(modifier = modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Calibrado de Unidad", style = MaterialTheme.typography.titleLarge)

        Text("Datos Generales", style = MaterialTheme.typography.titleSmall)
        CampoTextoMayusculas(value = transportista, onValueChange = { transportista = it }, label = { Text("Transportista") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = cuit, onValueChange = { cuit = it }, label = { Text("CUIT") }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = horaInicioTexto, onValueChange = { horaInicioTexto = it }, label = { Text("Horario Inicio (HH:mm)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
            OutlinedTextField(value = horaFinTexto, onValueChange = { horaFinTexto = it }, label = { Text("Horario Fin (HH:mm)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
        }

        Text("Vigencia del certificado", style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            listOf(1, 2, 4).forEachIndexed { idx, anios ->
                SegmentedButton(
                    selected = vigenciaAnios == anios,
                    onClick = { vigenciaAnios = anios },
                    shape = SegmentedButtonDefaults.itemShape(index = idx, count = 3)
                ) { Text("$anios año${if (anios > 1) "s" else ""}") }
            }
        }

        Text("Tractor", style = MaterialTheme.typography.titleSmall)
        CampoTextoMayusculas(value = patenteTractor, onValueChange = { patenteTractor = it }, label = { Text("Patente Tractor *") }, modifier = Modifier.fillMaxWidth().testTag("e2e_calibrado_patente_tractor"))
        CampoTextoMayusculas(value = marcaTractor, onValueChange = { marcaTractor = it }, label = { Text("Marca") }, modifier = Modifier.fillMaxWidth())
        CampoTextoMayusculas(value = tipoEjesTractor, onValueChange = { tipoEjesTractor = it }, label = { Text("Tipo de Ejes") }, modifier = Modifier.fillMaxWidth())
        CampoTextoMayusculas(value = rodadoTractor, onValueChange = { rodadoTractor = it }, label = { Text("Rodado") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = tara, onValueChange = { tara = it }, label = { Text("Tara (kg)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = maximoPesoPermitido, onValueChange = { maximoPesoPermitido = it }, label = { Text("Máximo Peso Permitido (kg)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())

        Text("Semirremolque", style = MaterialTheme.typography.titleSmall)
        CampoTextoMayusculas(
            value = patenteSemi,
            onValueChange = { patenteSemi = it },
            label = { Text("Patente Semirremolque *") },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = { OutlinedButton(onClick = { scope.launch { buscarUnidadPorSemi() } }) { Text("Buscar") } }
        )
        CampoTextoMayusculas(value = marcaSemi, onValueChange = { marcaSemi = it }, label = { Text("Marca") }, modifier = Modifier.fillMaxWidth())
        CampoTextoMayusculas(value = modeloSemi, onValueChange = { modeloSemi = it }, label = { Text("Modelo") }, modifier = Modifier.fillMaxWidth())
        CampoTextoMayusculas(value = tipoEjesSemi, onValueChange = { tipoEjesSemi = it }, label = { Text("Tipo de Ejes") }, modifier = Modifier.fillMaxWidth())
        CampoTextoMayusculas(value = rodadoSemi, onValueChange = { rodadoSemi = it }, label = { Text("Rodado") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = cotaAnterior, onValueChange = { cotaAnterior = it }, label = { Text("Cota Anterior") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = cotaPosterior, onValueChange = { cotaPosterior = it }, label = { Text("Cota Posterior") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = volumenSemi, onValueChange = { volumenSemi = it }, label = { Text("Volumen") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())

        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Cisternas: $cantidadCisternas", style = MaterialTheme.typography.labelLarge)
            OutlinedButton(onClick = { if (cantidadCisternas > 1) cantidadCisternas-- }) { Text("−") }
            OutlinedButton(onClick = { if (cantidadCisternas < cisternas.size) cantidadCisternas++ }) { Text("+") }
        }

        for (i in 0 until cantidadCisternas) {
            CisternaCard(i, cisternas[i])
        }

        Button(enabled = !generando, onClick = { generarCertificado() }, modifier = Modifier.fillMaxWidth()) {
            Text(if (generando) "Generando…" else "Generar certificado")
        }
    }
}

@Composable
private fun CisternaCard(indice: Int, c: CisternaFormState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Cisterna N° ${indice + 1}", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(value = c.capacidad, onValueChange = { c.capacidad = it }, label = { Text("Capacidad") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().testTag("e2e_cisterna_${indice}_capacidad"))
            OutlinedTextField(value = c.enraseMm, onValueChange = { c.enraseMm = it }, label = { Text("Enrase (mm)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = c.vacioMm, onValueChange = { c.vacioMm = it }, label = { Text("Vacío (mm)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().testTag("e2e_cisterna_${indice}_vacioMm"))
            OutlinedTextField(value = c.vacioLts, onValueChange = { c.vacioLts = it }, label = { Text("Vacío (lts)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().testTag("e2e_cisterna_${indice}_vacioLts"))
            OutlinedTextField(value = c.rebalseMm, onValueChange = { c.rebalseMm = it }, label = { Text("Rebalse (mm)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            CampoTextoMayusculas(value = c.precintoEnrase, onValueChange = { c.precintoEnrase = it }, label = { Text("Precinto Enrase") }, modifier = Modifier.fillMaxWidth())
            CampoTextoMayusculas(value = c.precintoBoca, onValueChange = { c.precintoBoca = it }, label = { Text("Precinto de Boca") }, modifier = Modifier.fillMaxWidth())
            CampoTextoMayusculas(value = c.precintoSoporte, onValueChange = { c.precintoSoporte = it }, label = { Text("Precinto de Soporte") }, modifier = Modifier.fillMaxWidth())

            // Planilla de Calibración de Uso Interno (R 14-03): trazabilidad/posición de
            // sensores de Informe de Carga Segura (según corresponda) -- misma tarjeta de
            // cisterna, no un paso separado.
            Text("Trazabilidad Sensor", style = MaterialTheme.typography.labelLarge)
            CampoTextoMayusculas(value = c.sensorMarca, onValueChange = { c.sensorMarca = it }, label = { Text("Marca del Sensor") }, modifier = Modifier.fillMaxWidth())
            CampoTextoMayusculas(value = c.sensorNumeroSerie, onValueChange = { c.sensorNumeroSerie = it }, label = { Text("N° de Serie") }, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = c.sensorCumple, onCheckedChange = { c.sensorCumple = it })
                Text("Cumple")
            }

            Text("Posición Sensor", style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = c.posicionCumple, onCheckedChange = { c.posicionCumple = it })
                Text("Cumple")
            }
            CampoTextoMayusculas(value = c.posicionObservacion, onValueChange = { c.posicionObservacion = it }, label = { Text("Observación") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = c.litrosSensorRebalse, onValueChange = { c.litrosSensorRebalse = it }, label = { Text("Litros Sensor/Rebalse") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())

            Text("Control de Descarga Patrón", style = MaterialTheme.typography.labelLarge)
            PUNTOS_DESCARGA_PATRON.forEach { punto ->
                Text("$punto lts", style = MaterialTheme.typography.bodySmall)
                val lecturas = c.descargaPatron.getValue(punto)
                lecturas.forEachIndexed { idx, valor ->
                    OutlinedTextField(
                        value = valor,
                        onValueChange = { lecturas[idx] = it },
                        label = { Text("Lectura ${idx + 1}") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedButton(onClick = { lecturas.add("") }) { Text("+ Agregar lectura") }
            }
        }
    }
}
