package com.aies.feature.sesion

import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Divider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.aies.coredata.AppDatabase
import com.aies.coredata.dao.ItemDeSesion
import com.aies.coredata.dao.ServicioDelDia
import com.aies.feature.repuestos.RepuestosScreen
import com.aies.feature.planillatapas.PlanillaTapasScreen
import com.aies.feature.planillaatmosfera.PlanillaAtmosferaScreen
import com.aies.coredata.entities.AuditorCatalogo
import com.aies.coredata.entities.ClienteCatalogo
import com.aies.coredata.entities.SesionServicio
import com.aies.coredata.entities.SesionServicioItem
import com.aies.coredata.entities.TipoServicio
import com.aies.coredata.entities.Unidad
import com.aies.coredata.remote.DocumentoStorageHelper
import com.aies.coredata.validacion.Validaciones
import com.aies.coreui.CampoTextoMayusculas
import com.aies.feature.calibrado.ControlCalibradoScreen
import com.aies.feature.certificados.CertificadosScreen
import com.aies.feature.certificados.NotaPrecintosScreen
import com.aies.feature.controlesvisuales.ControlEspesoresScreen
import com.aies.feature.controlesvisuales.ControlVisualExternaScreen
import com.aies.feature.controlesvisuales.ControlVisualInternaScreen
import com.aies.feature.informes.InformesScreen
import com.aies.feature.inspecciones.ui.HistorialActivity
import com.aies.feature.inspecciones.ui.InspeccionesMenuActivity
import com.aies.feature.inspeccionejes.InspeccionEjesScreen
import com.aies.feature.inspeccionglp.InspeccionGlpScreen
import com.aies.feature.notatrabajo.NotaTrabajoScreen
import com.aies.feature.notatrabajo.pdf.NotaTrabajoPdfBuilder
import com.aies.feature.sesion.util.MediaStoreUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val FMT_DDMMYYYY = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

/** Inicio y fin (millis) del día calendario que contiene [fechaMillis], en el huso horario local
 *  (copia de feature-nota-trabajo.rangoDelDia — misma convención de duplicación por módulo). */
private fun rangoDelDia(fechaMillis: Long): Pair<Long, Long> {
    val zona = ZoneId.systemDefault()
    val fecha = Instant.ofEpochMilli(fechaMillis).atZone(zona).toLocalDate()
    val inicio = fecha.atStartOfDay(zona).toInstant().toEpochMilli()
    val fin = fecha.plusDays(1).atStartOfDay(zona).toInstant().toEpochMilli() - 1
    return inicio to fin
}

/** Igual idea que TipoUnidadOptions de feature-controles-visuales, pero con las categorías
 *  que ya documenta Unidad.tipoUnidad (heredadas de la app de inspecciones). */
private object TipoUnidadOptions {
    val lista = listOf("LIVIANOS", "PESADOS", "COQUE", "QUIMICOS", "GLP")
}

private sealed class Paso {
    data object InicioSesion : Paso()
    data object NuevaVisita : Paso()
    data class VisitaEnCurso(val sesionId: Long) : Paso()

    /** Fase 3 (1/6): destino real para el ítem "calibracion_unidad". Los datos de la sesión
     *  viajan en el propio Paso para no tener que releerlos al volver de Calibrado. */
    data class Calibrado(
        val sesionId: Long,
        val sesionItemId: Long,
        val unidadId: Long,
        val cliente: String,
        val domicilio: String,
        val cuit: String,
        val auditor: String,
        val patenteTractor: String,
        val marcaTractor: String,
        val anioTractor: Int,
        val modeloTractor: String,
        val patenteSemi: String,
        val marcaSemi: String,
        val anioSemi: Int,
        val modeloSemi: String,
        val cantidadCisternas: Int
    ) : Paso()

    /** Fase 3 (2/6): un único caso parametrizado por tipoServicioCodigo para los 3 controles de
     *  feature-controles-visuales — misma firma de precarga que Calibrado, distinta pantalla
     *  según el código ("control_visual_interna" | "control_visual_externa" | "control_espesores"). */
    data class ControlVisual(
        val tipoServicioCodigo: String,
        val sesionId: Long,
        val sesionItemId: Long,
        val unidadId: Long,
        val cliente: String,
        val domicilio: String,
        val cuit: String,
        val auditor: String,
        val patenteTractor: String,
        val marcaTractor: String,
        val anioTractor: Int,
        val modeloTractor: String,
        val patenteSemi: String,
        val marcaSemi: String,
        val anioSemi: Int,
        val modeloSemi: String,
        val cantidadCisternas: Int
    ) : Paso()

    /** Servicio "repuestos_cisterna": un único código, una única pantalla (sin wizard) — mismo
     *  patrón de firma que Calibrado, no el de ControlVisual (no comparte pantalla con otros
     *  códigos, así que no hace falta el campo tipoServicioCodigo). */
    data class Repuestos(
        val sesionId: Long,
        val sesionItemId: Long,
        val unidadId: Long,
        val cliente: String,
        val domicilio: String,
        val cuit: String,
        val auditor: String,
        val patenteTractor: String,
        val marcaTractor: String,
        val anioTractor: Int,
        val modeloTractor: String,
        val patenteSemi: String,
        val marcaSemi: String,
        val anioSemi: Int,
        val modeloSemi: String,
        val cantidadCisternas: Int
    ) : Paso()

    /** Fase 3 (3/6): destino para "cert_tapa_hombre" y "cert_precinto". Mismo patrón que
     *  ControlVisual: un único Paso, dos composables separados (CertificadosScreen/
     *  NotaPrecintosScreen) elegidos por tipoServicioCodigo al renderizar. */
    /** Servicio "planilla_tapas": un único código, una única pantalla (sin wizard) — mismo
     *  patrón de firma que Repuestos/Calibrado. */
    data class PlanillaTapas(
        val sesionId: Long,
        val sesionItemId: Long,
        val unidadId: Long,
        val cliente: String,
        val domicilio: String,
        val cuit: String,
        val auditor: String,
        val patenteTractor: String,
        val marcaTractor: String,
        val anioTractor: Int,
        val modeloTractor: String,
        val patenteSemi: String,
        val marcaSemi: String,
        val anioSemi: Int,
        val modeloSemi: String,
        val cantidadCisternas: Int
    ) : Paso()

    /** Servicio "planilla_atmosfera" (R 12-01): una sola pantalla, mismo patrón que PlanillaTapas. */
    data class PlanillaAtmosfera(
        val sesionId: Long,
        val sesionItemId: Long,
        val unidadId: Long,
        val cliente: String,
        val auditor: String,
        val patenteTractor: String,
        val patenteSemi: String,
        val cantidadCisternas: Int
    ) : Paso()

    data class Certificado(
        val tipoServicioCodigo: String,
        val sesionId: Long,
        val sesionItemId: Long,
        val unidadId: Long,
        val cliente: String,
        val domicilio: String,
        val cuit: String,
        val auditor: String,
        val patenteTractor: String,
        val marcaTractor: String,
        val anioTractor: Int,
        val modeloTractor: String,
        val patenteSemi: String,
        val marcaSemi: String,
        val anioSemi: Int,
        val modeloSemi: String,
        val cantidadCisternas: Int
    ) : Paso()

    /** Fase 3 (4/6): destino para "informe_carga_segura" — wizard de 5 pasos, un único código
     *  de servicio (a diferencia de Certificados no hace falta distinguir sub-tipos). */
    data class Informe(
        val sesionId: Long,
        val sesionItemId: Long,
        val unidadId: Long,
        val cliente: String,
        val domicilio: String,
        val cuit: String,
        val auditor: String,
        val patenteTractor: String,
        val marcaTractor: String,
        val anioTractor: Int,
        val modeloTractor: String,
        val patenteSemi: String,
        val marcaSemi: String,
        val anioSemi: Int,
        val modeloSemi: String,
        val cantidadCisternas: Int
    ) : Paso()

    /** Fase 3 (5/6): destino para los 5 tipos de inspección visibles ("insp_livianos",
     *  "insp_pesados", "insp_coque", "insp_quimicos", "insp_enganche" — ver
     *  HistorialCompartidoHelper.registrar de feature-inspecciones, que arma un código por
     *  tipo, no uno solo). A diferencia de los otros 4 módulos, este no se renderiza como
     *  Composable acá: feature-inspecciones es el único módulo del proyecto con
     *  Activities+XML heredadas de la app standalone (formularios calcados celda a celda de
     *  planillas Excel), así que este Paso solo transporta los datos para lanzar
     *  InspeccionesMenuActivity por Intent (ver AbrirInspeccionesPaso). AXION queda afuera:
     *  su botón está oculto (asset faltante), no es una opción seleccionable.
     */
    data class Inspeccion(
        val tipoServicioCodigo: String,
        val sesionId: Long,
        val sesionItemId: Long,
        val unidadId: Long,
        val cliente: String,
        val domicilio: String,
        val cuit: String,
        val auditor: String,
        val patenteTractor: String,
        val marcaTractor: String,
        val anioTractor: Int,
        val modeloTractor: String,
        val patenteSemi: String,
        val marcaSemi: String,
        val anioSemi: Int,
        val modeloSemi: String
    ) : Paso()

    /** Fase 4: acceso secundario desde InicioSesionPaso — Nota de trabajo nunca fue parte del
     *  flujo de visita, pero ahora que :app perdió el bottom nav necesita quedar alcanzable
     *  desde algún lado. Ejes/Glp tuvieron su propio botón acá en esta misma fase, pero pasaron
     *  a ser destinos reales del checklist de visita (ver Paso.InspeccionEjes/InspeccionGlp más
     *  abajo) — ya no se acceden como secundarios. */
    data object NotaTrabajo : Paso()

    /** Fase 5 (7/8): destino para "control_ejes" — a diferencia de los otros módulos wizard
     *  (Inspeccion), este SÍ se renderiza como Composable: feature-inspeccion-ejes ya es
     *  Compose puro (sin Activities+XML heredadas), así que sigue el mismo patrón de
     *  Calibrado/Informe en vez del puente por Intent. */
    data class InspeccionEjes(
        val sesionId: Long,
        val sesionItemId: Long,
        val unidadId: Long,
        val cliente: String,
        val domicilio: String,
        val cuit: String,
        val auditor: String,
        val patenteTractor: String,
        val marcaTractor: String,
        val anioTractor: Int,
        val modeloTractor: String,
        val patenteSemi: String,
        val marcaSemi: String,
        val anioSemi: Int,
        val modeloSemi: String
    ) : Paso()

    /** Fase 5 (8/8): destino para "inspeccion_glp" — mismo criterio que InspeccionEjes. */
    data class InspeccionGlp(
        val sesionId: Long,
        val sesionItemId: Long,
        val unidadId: Long,
        val cliente: String,
        val domicilio: String,
        val cuit: String,
        val auditor: String,
        val patenteTractor: String,
        val marcaTractor: String,
        val anioTractor: Int,
        val modeloTractor: String,
        val patenteSemi: String,
        val marcaSemi: String,
        val anioSemi: Int,
        val modeloSemi: String
    ) : Paso()

    /** Fase 4: confirmación tras "Finalizar visita" — muestra el resultado de la nota de
     *  trabajo autogenerada y permite abrirla antes de volver al inicio. */
    data class NotaTrabajoGenerada(
        val placa: String,
        val fecha: Long,
        val cantidadServicios: Int,
        val documentoUri: String?,
        // Fase F: URL pública en Supabase Storage de la nota de trabajo -- se puebla con lo que
        // haya en sesion.notaTrabajoUrl en el momento de construir este Paso, que recién se
        // escribe en segundo plano (ver "Finalizar visita" más abajo), así que acá probablemente
        // esté vacía/null recién generada la visita. Queda para cuando SÍ esté disponible (una
        // visita futura, o si el usuario vuelve a esta pantalla más tarde con datos ya
        // sincronizados).
        val notaTrabajoUrl: String? = null
    ) : Paso()
}

private data class VisitaAbierta(val sesion: SesionServicio, val placa: String)

/**
 * Cada TipoServicio se auto-registra en el catálogo compartido recién la primera vez que ese
 * módulo completa un servicio real (ver los `HistorialCompartidoHelper` de cada uno) — así que
 * en una instalación donde todavía no se completó alguno, el checklist de "Nueva visita" no lo
 * lista, aunque el módulo esté completamente enganchado. Esto siembra el catálogo completo (los
 * 14 códigos reales, uno por cada `HistorialCompartidoHelper` de los 8 módulos de servicio) para
 * que el checklist siempre muestre todo, sin depender de qué se haya usado antes en el
 * dispositivo. Usa el mismo `insertar` con OnConflictStrategy.IGNORE que cada módulo ya usa, así
 * que no pisa nombres reales si un módulo ya se auto-registró primero.
 */
private object CatalogoServiciosSeed {
    suspend fun asegurarCatalogoCompleto(db: AppDatabase) {
        val dao = db.tipoServicioDao()
        val seed = listOf(
            TipoServicio("cert_tapa_hombre", "Certificado de Tapas Paso de Hombre", "feature-certificados"),
            TipoServicio("cert_precinto", "Acta de Reemplazo de Precinto", "feature-certificados"),
            TipoServicio("informe_carga_segura", "Informe de Carga Segura", "feature-informes"),
            TipoServicio("control_ejes", "Control de Puntas de Eje", "feature-inspeccion-ejes"),
            TipoServicio("inspeccion_glp", "Inspección GLP", "feature-inspeccion-glp"),
            TipoServicio("control_visual_interna", "Control Visual Interna", "feature-controles-visuales"),
            TipoServicio("control_visual_externa", "Control Visual Externa", "feature-controles-visuales"),
            TipoServicio("control_espesores", "Control de Espesores", "feature-controles-visuales"),
            TipoServicio("calibracion_unidad", "Calibración de Unidad", "feature-calibrado"),
            TipoServicio("repuestos_cisterna", "Cambio de Repuestos", "feature-repuestos"),
            TipoServicio("planilla_atmosfera", "Planilla de Control de Atmósfera de Tanques Cisterna", "feature-planilla-atmosfera"),
            TipoServicio("insp_livianos", "Spotcheck Livianos", "feature-inspecciones"),
            TipoServicio("insp_pesados", "Inspección PESADOS", "feature-inspecciones"),
            TipoServicio("insp_coque", "Inspección COQUE", "feature-inspecciones"),
            TipoServicio("insp_quimicos", "Inspección QUIMICOS", "feature-inspecciones"),
            TipoServicio("insp_enganche", "Control de rodamientos", "feature-inspecciones"),
            TipoServicio("insp_axion", "CheckList Livianos", "feature-inspecciones")
        )
        // Fase D, paso 1 de 5: con UnidadDao/TipoServicioDao ahora hablando contra Supabase,
        // esto ya no es un insert local barato -- son hasta 15 round-trips de red. Esta pantalla
        // (NuevaVisitaPaso/VisitaEnCursoPaso) recompone y vuelve a llamar a este seed cada vez
        // que se abre, así que solo vale la pena sembrar si el catálogo remoto todavía no tiene
        // todo lo que necesitamos -- no incondicionalmente en cada apertura de pantalla.
        try {
            if (dao.getAll().size >= seed.size) return
            seed.forEach { dao.insertar(it) }
        } catch (e: Exception) {
            // Sembrado automático en segundo plano, no una acción del técnico: si Supabase no
            // responde (getAll sin red, o un insertar a mitad del loop) no vale la pena
            // crashear NuevaVisitaPaso/VisitaEnCursoPaso por esto -- simplemente se reintenta
            // la próxima vez que se abra la pantalla.
            android.util.Log.w("CatalogoServiciosSeed", "No se pudo sembrar/verificar el catálogo de servicios", e)
        }
    }
}

/**
 * Fase 2 del flujo unificado de sesión: pantallas de inicio/alta/seguimiento de una visita,
 * consumiendo el esquema de core-data agregado en Fase 1. Wizard flat con back-stack propio,
 * misma convención que feature-inspeccion-ejes/feature-calibrado/feature-controles-visuales —
 * todavía no está montado como destino de :app (eso es Fase 3/4).
 */
@Composable
fun SesionScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val backStack = remember { mutableListOf(Paso.InicioSesion as Paso) }
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

    fun irAInicio() {
        backStack.clear()
        backStack.add(Paso.InicioSesion)
        version++
    }

    BackHandler(enabled = backStack.size > 1) { volver() }

    Box(modifier = modifier.fillMaxSize()) {
        androidx.compose.runtime.key(version) {
            when (val paso = pasoActual) {
                is Paso.InicioSesion -> InicioSesionPaso(
                    db = db,
                    onNuevaVisita = { navegarA(Paso.NuevaVisita) },
                    onAbrirVisita = { navegarA(Paso.VisitaEnCurso(it)) },
                    onNotaTrabajo = { navegarA(Paso.NotaTrabajo) }
                )
                is Paso.NuevaVisita -> NuevaVisitaPaso(
                    db = db,
                    onIniciada = { navegarA(Paso.VisitaEnCurso(it)) },
                    onCancelar = { volver() }
                )
                is Paso.VisitaEnCurso -> VisitaEnCursoPaso(
                    db = db,
                    sesionId = paso.sesionId,
                    onFinalizada = { navegarA(it) },
                    onAbrirItem = { sesion, unidad, item ->
                        when (item.tipoServicioCodigo) {
                            "calibracion_unidad" -> navegarA(
                                Paso.Calibrado(
                                    sesionId = paso.sesionId,
                                    sesionItemId = item.id,
                                    unidadId = unidad.id,
                                    cliente = sesion.cliente,
                                    domicilio = sesion.domicilio,
                                    cuit = sesion.cuit,
                                    auditor = sesion.auditor,
                                    patenteTractor = sesion.patenteTractor,
                                    marcaTractor = sesion.marcaTractor,
                                    anioTractor = sesion.anioTractor,
                                    modeloTractor = sesion.modeloTractor,
                                    patenteSemi = sesion.patenteSemi,
                                    marcaSemi = sesion.marcaSemi,
                                    anioSemi = sesion.anioSemi,
                                    modeloSemi = sesion.modeloSemi,
                                    cantidadCisternas = unidad.cantidadCisternas
                                )
                            )
                            "repuestos_cisterna" -> navegarA(
                                Paso.Repuestos(
                                    sesionId = paso.sesionId,
                                    sesionItemId = item.id,
                                    unidadId = unidad.id,
                                    cliente = sesion.cliente,
                                    domicilio = sesion.domicilio,
                                    cuit = sesion.cuit,
                                    auditor = sesion.auditor,
                                    patenteTractor = sesion.patenteTractor,
                                    marcaTractor = sesion.marcaTractor,
                                    anioTractor = sesion.anioTractor,
                                    modeloTractor = sesion.modeloTractor,
                                    patenteSemi = sesion.patenteSemi,
                                    marcaSemi = sesion.marcaSemi,
                                    anioSemi = sesion.anioSemi,
                                    modeloSemi = sesion.modeloSemi,
                                    cantidadCisternas = unidad.cantidadCisternas
                                )
                            )
                            "planilla_tapas" -> navegarA(
                                Paso.PlanillaTapas(
                                    sesionId = paso.sesionId,
                                    sesionItemId = item.id,
                                    unidadId = unidad.id,
                                    cliente = sesion.cliente,
                                    domicilio = sesion.domicilio,
                                    cuit = sesion.cuit,
                                    auditor = sesion.auditor,
                                    patenteTractor = sesion.patenteTractor,
                                    marcaTractor = sesion.marcaTractor,
                                    anioTractor = sesion.anioTractor,
                                    modeloTractor = sesion.modeloTractor,
                                    patenteSemi = sesion.patenteSemi,
                                    marcaSemi = sesion.marcaSemi,
                                    anioSemi = sesion.anioSemi,
                                    modeloSemi = sesion.modeloSemi,
                                    cantidadCisternas = unidad.cantidadCisternas
                                )
                            )
                            "planilla_atmosfera" -> navegarA(
                                Paso.PlanillaAtmosfera(
                                    sesionId = paso.sesionId,
                                    sesionItemId = item.id,
                                    unidadId = unidad.id,
                                    cliente = sesion.cliente,
                                    auditor = sesion.auditor,
                                    patenteTractor = sesion.patenteTractor,
                                    patenteSemi = sesion.patenteSemi,
                                    cantidadCisternas = unidad.cantidadCisternas
                                )
                            )
                            "control_visual_interna", "control_visual_externa", "control_espesores" -> navegarA(
                                Paso.ControlVisual(
                                    tipoServicioCodigo = item.tipoServicioCodigo,
                                    sesionId = paso.sesionId,
                                    sesionItemId = item.id,
                                    unidadId = unidad.id,
                                    cliente = sesion.cliente,
                                    domicilio = sesion.domicilio,
                                    cuit = sesion.cuit,
                                    auditor = sesion.auditor,
                                    patenteTractor = sesion.patenteTractor,
                                    marcaTractor = sesion.marcaTractor,
                                    anioTractor = sesion.anioTractor,
                                    modeloTractor = sesion.modeloTractor,
                                    patenteSemi = sesion.patenteSemi,
                                    marcaSemi = sesion.marcaSemi,
                                    anioSemi = sesion.anioSemi,
                                    modeloSemi = sesion.modeloSemi,
                                    cantidadCisternas = unidad.cantidadCisternas
                                )
                            )
                            "cert_tapa_hombre", "cert_precinto" -> navegarA(
                                Paso.Certificado(
                                    tipoServicioCodigo = item.tipoServicioCodigo,
                                    sesionId = paso.sesionId,
                                    sesionItemId = item.id,
                                    unidadId = unidad.id,
                                    cliente = sesion.cliente,
                                    domicilio = sesion.domicilio,
                                    cuit = sesion.cuit,
                                    auditor = sesion.auditor,
                                    patenteTractor = sesion.patenteTractor,
                                    marcaTractor = sesion.marcaTractor,
                                    anioTractor = sesion.anioTractor,
                                    modeloTractor = sesion.modeloTractor,
                                    patenteSemi = sesion.patenteSemi,
                                    marcaSemi = sesion.marcaSemi,
                                    anioSemi = sesion.anioSemi,
                                    modeloSemi = sesion.modeloSemi,
                                    cantidadCisternas = unidad.cantidadCisternas
                                )
                            )
                            "informe_carga_segura" -> navegarA(
                                Paso.Informe(
                                    sesionId = paso.sesionId,
                                    sesionItemId = item.id,
                                    unidadId = unidad.id,
                                    cliente = sesion.cliente,
                                    domicilio = sesion.domicilio,
                                    cuit = sesion.cuit,
                                    auditor = sesion.auditor,
                                    patenteTractor = sesion.patenteTractor,
                                    marcaTractor = sesion.marcaTractor,
                                    anioTractor = sesion.anioTractor,
                                    modeloTractor = sesion.modeloTractor,
                                    patenteSemi = sesion.patenteSemi,
                                    marcaSemi = sesion.marcaSemi,
                                    anioSemi = sesion.anioSemi,
                                    modeloSemi = sesion.modeloSemi,
                                    cantidadCisternas = unidad.cantidadCisternas
                                )
                            )
                            "insp_livianos", "insp_pesados", "insp_coque", "insp_quimicos", "insp_enganche", "insp_axion" -> navegarA(
                                Paso.Inspeccion(
                                    tipoServicioCodigo = item.tipoServicioCodigo,
                                    sesionId = paso.sesionId,
                                    sesionItemId = item.id,
                                    unidadId = unidad.id,
                                    cliente = sesion.cliente,
                                    domicilio = sesion.domicilio,
                                    cuit = sesion.cuit,
                                    auditor = sesion.auditor,
                                    patenteTractor = sesion.patenteTractor,
                                    marcaTractor = sesion.marcaTractor,
                                    anioTractor = sesion.anioTractor,
                                    modeloTractor = sesion.modeloTractor,
                                    patenteSemi = sesion.patenteSemi,
                                    marcaSemi = sesion.marcaSemi,
                                    anioSemi = sesion.anioSemi,
                                    modeloSemi = sesion.modeloSemi
                                )
                            )
                            "control_ejes" -> navegarA(
                                Paso.InspeccionEjes(
                                    sesionId = paso.sesionId,
                                    sesionItemId = item.id,
                                    unidadId = unidad.id,
                                    cliente = sesion.cliente,
                                    domicilio = sesion.domicilio,
                                    cuit = sesion.cuit,
                                    auditor = sesion.auditor,
                                    patenteTractor = sesion.patenteTractor,
                                    marcaTractor = sesion.marcaTractor,
                                    anioTractor = sesion.anioTractor,
                                    modeloTractor = sesion.modeloTractor,
                                    patenteSemi = sesion.patenteSemi,
                                    marcaSemi = sesion.marcaSemi,
                                    anioSemi = sesion.anioSemi,
                                    modeloSemi = sesion.modeloSemi
                                )
                            )
                            "inspeccion_glp" -> navegarA(
                                Paso.InspeccionGlp(
                                    sesionId = paso.sesionId,
                                    sesionItemId = item.id,
                                    unidadId = unidad.id,
                                    cliente = sesion.cliente,
                                    domicilio = sesion.domicilio,
                                    cuit = sesion.cuit,
                                    auditor = sesion.auditor,
                                    patenteTractor = sesion.patenteTractor,
                                    marcaTractor = sesion.marcaTractor,
                                    anioTractor = sesion.anioTractor,
                                    modeloTractor = sesion.modeloTractor,
                                    patenteSemi = sesion.patenteSemi,
                                    marcaSemi = sesion.marcaSemi,
                                    anioSemi = sesion.anioSemi,
                                    modeloSemi = sesion.modeloSemi
                                )
                            )
                            // TODO Fase 3 (módulo 6/6): sumar acá nota_trabajo
                            else -> Toast.makeText(context, "Este servicio todavía no está conectado a la sesión", Toast.LENGTH_LONG).show()
                        }
                    }
                )
                is Paso.Calibrado -> ControlCalibradoScreen(
                    sesionId = paso.sesionId,
                    sesionItemId = paso.sesionItemId,
                    unidadIdPrecargada = paso.unidadId,
                    clientePrecargado = paso.cliente,
                    domicilioPrecargado = paso.domicilio,
                    cuitPrecargado = paso.cuit,
                    auditorPrecargado = paso.auditor,
                    patenteTractorPrecargado = paso.patenteTractor,
                    marcaTractorPrecargado = paso.marcaTractor,
                    anioTractorPrecargado = paso.anioTractor,
                    modeloTractorPrecargado = paso.modeloTractor,
                    patenteSemiPrecargado = paso.patenteSemi,
                    marcaSemiPrecargado = paso.marcaSemi,
                    anioSemiPrecargado = paso.anioSemi,
                    modeloSemiPrecargado = paso.modeloSemi,
                    cantidadCisternasPrecargada = paso.cantidadCisternas,
                    onVolverASesion = { volver() }
                )
                // Mismo criterio que ControlVisual/ControlEspesoresScreen: un único paso, sin
                // wizard, onVolver cumple el rol de "salir de esta pantalla" tanto al tocar
                // "← Volver" como al terminar de generar el PDF.
                is Paso.Repuestos -> RepuestosScreen(
                    onVolver = { volver() }, sesionId = paso.sesionId, sesionItemId = paso.sesionItemId,
                    unidadIdPrecargada = paso.unidadId, clientePrecargado = paso.cliente,
                    domicilioPrecargado = paso.domicilio, cuitPrecargado = paso.cuit, auditorPrecargado = paso.auditor,
                    patenteTractorPrecargado = paso.patenteTractor, marcaTractorPrecargado = paso.marcaTractor,
                    anioTractorPrecargado = paso.anioTractor, modeloTractorPrecargado = paso.modeloTractor,
                    patenteSemiPrecargado = paso.patenteSemi, marcaSemiPrecargado = paso.marcaSemi,
                    anioSemiPrecargado = paso.anioSemi, modeloSemiPrecargado = paso.modeloSemi,
                    cantidadCisternasPrecargada = paso.cantidadCisternas
                )
                // Mismo criterio que Repuestos: un único paso, sin wizard, onVolver cumple el
                // rol de "salir de esta pantalla" tanto al tocar "← Volver" como al terminar de
                // generar el PDF.
                is Paso.PlanillaTapas -> PlanillaTapasScreen(
                    onVolver = { volver() }, sesionId = paso.sesionId, sesionItemId = paso.sesionItemId,
                    unidadIdPrecargada = paso.unidadId, clientePrecargado = paso.cliente,
                    domicilioPrecargado = paso.domicilio, cuitPrecargado = paso.cuit, auditorPrecargado = paso.auditor,
                    patenteTractorPrecargado = paso.patenteTractor, marcaTractorPrecargado = paso.marcaTractor,
                    anioTractorPrecargado = paso.anioTractor, modeloTractorPrecargado = paso.modeloTractor,
                    patenteSemiPrecargado = paso.patenteSemi, marcaSemiPrecargado = paso.marcaSemi,
                    anioSemiPrecargado = paso.anioSemi, modeloSemiPrecargado = paso.modeloSemi,
                    cantidadCisternasPrecargada = paso.cantidadCisternas
                )
                // onVolver ya cumple el rol de "salir de esta pantalla" en las 3 planillas (se
                // llama tanto al tocar "← Volver" en el paso 0 como al terminar de generar el
                // PDF) — a diferencia de Calibrado no hace falta un callback aparte.
                is Paso.PlanillaAtmosfera -> PlanillaAtmosferaScreen(
                    onVolver = { volver() }, sesionId = paso.sesionId, sesionItemId = paso.sesionItemId,
                    unidadIdPrecargada = paso.unidadId, clientePrecargado = paso.cliente, auditorPrecargado = paso.auditor,
                    patenteTractorPrecargado = paso.patenteTractor, patenteSemiPrecargado = paso.patenteSemi,
                    cantidadCisternasPrecargada = paso.cantidadCisternas
                )
                is Paso.ControlVisual -> when (paso.tipoServicioCodigo) {
                    "control_visual_interna" -> ControlVisualInternaScreen(
                        onVolver = { volver() }, sesionId = paso.sesionId, sesionItemId = paso.sesionItemId,
                        unidadIdPrecargada = paso.unidadId, clientePrecargado = paso.cliente,
                        domicilioPrecargado = paso.domicilio, cuitPrecargado = paso.cuit, auditorPrecargado = paso.auditor,
                        patenteTractorPrecargado = paso.patenteTractor, marcaTractorPrecargado = paso.marcaTractor,
                        anioTractorPrecargado = paso.anioTractor, modeloTractorPrecargado = paso.modeloTractor,
                        patenteSemiPrecargado = paso.patenteSemi, marcaSemiPrecargado = paso.marcaSemi,
                        anioSemiPrecargado = paso.anioSemi, modeloSemiPrecargado = paso.modeloSemi,
                        cantidadCisternasPrecargada = paso.cantidadCisternas
                    )
                    "control_visual_externa" -> ControlVisualExternaScreen(
                        onVolver = { volver() }, sesionId = paso.sesionId, sesionItemId = paso.sesionItemId,
                        unidadIdPrecargada = paso.unidadId, clientePrecargado = paso.cliente,
                        domicilioPrecargado = paso.domicilio, cuitPrecargado = paso.cuit, auditorPrecargado = paso.auditor,
                        patenteTractorPrecargado = paso.patenteTractor, marcaTractorPrecargado = paso.marcaTractor,
                        anioTractorPrecargado = paso.anioTractor, modeloTractorPrecargado = paso.modeloTractor,
                        patenteSemiPrecargado = paso.patenteSemi, marcaSemiPrecargado = paso.marcaSemi,
                        anioSemiPrecargado = paso.anioSemi, modeloSemiPrecargado = paso.modeloSemi,
                        cantidadCisternasPrecargada = paso.cantidadCisternas
                    )
                    else -> ControlEspesoresScreen(
                        onVolver = { volver() }, sesionId = paso.sesionId, sesionItemId = paso.sesionItemId,
                        unidadIdPrecargada = paso.unidadId, clientePrecargado = paso.cliente,
                        domicilioPrecargado = paso.domicilio, cuitPrecargado = paso.cuit, auditorPrecargado = paso.auditor,
                        patenteTractorPrecargado = paso.patenteTractor, marcaTractorPrecargado = paso.marcaTractor,
                        anioTractorPrecargado = paso.anioTractor, modeloTractorPrecargado = paso.modeloTractor,
                        patenteSemiPrecargado = paso.patenteSemi, marcaSemiPrecargado = paso.marcaSemi,
                        anioSemiPrecargado = paso.anioSemi, modeloSemiPrecargado = paso.modeloSemi,
                        cantidadCisternasPrecargada = paso.cantidadCisternas
                    )
                }
                // "cert_precinto" y "cert_tapa_hombre" comparten el mismo Paso (misma unidad/
                // sesión) pero, a pedido del usuario, ya no comparten pantalla/formulario -- se
                // separó la Nota de Reemplazo de Precintos a su propio flujo independiente
                // (NotaPrecintosScreen), igual que ControlVisual arriba.
                is Paso.Certificado -> when (paso.tipoServicioCodigo) {
                    "cert_precinto" -> NotaPrecintosScreen(
                        sesionId = paso.sesionId,
                        sesionItemId = paso.sesionItemId,
                        unidadIdPrecargada = paso.unidadId,
                        clientePrecargado = paso.cliente,
                        domicilioPrecargado = paso.domicilio,
                        cuitPrecargado = paso.cuit,
                        auditorPrecargado = paso.auditor,
                        patenteTractorPrecargado = paso.patenteTractor,
                        marcaTractorPrecargado = paso.marcaTractor,
                        anioTractorPrecargado = paso.anioTractor,
                        modeloTractorPrecargado = paso.modeloTractor,
                        patenteSemiPrecargado = paso.patenteSemi,
                        marcaSemiPrecargado = paso.marcaSemi,
                        anioSemiPrecargado = paso.anioSemi,
                        modeloSemiPrecargado = paso.modeloSemi,
                        cantidadCisternasPrecargada = paso.cantidadCisternas,
                        onVolverASesion = { volver() }
                    )
                    else -> CertificadosScreen(
                        sesionId = paso.sesionId,
                        sesionItemId = paso.sesionItemId,
                        unidadIdPrecargada = paso.unidadId,
                        clientePrecargado = paso.cliente,
                        domicilioPrecargado = paso.domicilio,
                        cuitPrecargado = paso.cuit,
                        auditorPrecargado = paso.auditor,
                        patenteTractorPrecargado = paso.patenteTractor,
                        marcaTractorPrecargado = paso.marcaTractor,
                        anioTractorPrecargado = paso.anioTractor,
                        modeloTractorPrecargado = paso.modeloTractor,
                        patenteSemiPrecargado = paso.patenteSemi,
                        marcaSemiPrecargado = paso.marcaSemi,
                        anioSemiPrecargado = paso.anioSemi,
                        modeloSemiPrecargado = paso.modeloSemi,
                        cantidadCisternasPrecargada = paso.cantidadCisternas,
                        onVolverASesion = { volver() }
                    )
                }
                is Paso.Informe -> InformesScreen(
                    sesionId = paso.sesionId,
                    sesionItemId = paso.sesionItemId,
                    unidadIdPrecargada = paso.unidadId,
                    clientePrecargado = paso.cliente,
                    domicilioPrecargado = paso.domicilio,
                    cuitPrecargado = paso.cuit,
                    auditorPrecargado = paso.auditor,
                    patenteTractorPrecargado = paso.patenteTractor,
                    marcaTractorPrecargado = paso.marcaTractor,
                    anioTractorPrecargado = paso.anioTractor,
                    modeloTractorPrecargado = paso.modeloTractor,
                    patenteSemiPrecargado = paso.patenteSemi,
                    marcaSemiPrecargado = paso.marcaSemi,
                    anioSemiPrecargado = paso.anioSemi,
                    modeloSemiPrecargado = paso.modeloSemi,
                    cantidadCisternasPrecargada = paso.cantidadCisternas,
                    onVolverASesion = { volver() }
                )
                is Paso.Inspeccion -> AbrirInspeccionesPaso(
                    paso = paso,
                    onVolverASesion = { volver() }
                )
                is Paso.NotaTrabajo -> AccesoSecundarioConVolver(titulo = "Historial / Notas de trabajo", onVolver = { volver() }) {
                    NotaTrabajoScreen()
                }
                is Paso.InspeccionEjes -> InspeccionEjesScreen(
                    sesionId = paso.sesionId,
                    sesionItemId = paso.sesionItemId,
                    unidadIdPrecargada = paso.unidadId,
                    clientePrecargado = paso.cliente,
                    domicilioPrecargado = paso.domicilio,
                    cuitPrecargado = paso.cuit,
                    auditorPrecargado = paso.auditor,
                    patenteTractorPrecargado = paso.patenteTractor,
                    marcaTractorPrecargado = paso.marcaTractor,
                    anioTractorPrecargado = paso.anioTractor,
                    modeloTractorPrecargado = paso.modeloTractor,
                    patenteSemiPrecargado = paso.patenteSemi,
                    marcaSemiPrecargado = paso.marcaSemi,
                    anioSemiPrecargado = paso.anioSemi,
                    modeloSemiPrecargado = paso.modeloSemi,
                    onVolverASesion = { volver() }
                )
                is Paso.InspeccionGlp -> InspeccionGlpScreen(
                    sesionId = paso.sesionId,
                    sesionItemId = paso.sesionItemId,
                    unidadIdPrecargada = paso.unidadId,
                    clientePrecargado = paso.cliente,
                    domicilioPrecargado = paso.domicilio,
                    cuitPrecargado = paso.cuit,
                    auditorPrecargado = paso.auditor,
                    patenteTractorPrecargado = paso.patenteTractor,
                    marcaTractorPrecargado = paso.marcaTractor,
                    anioTractorPrecargado = paso.anioTractor,
                    modeloTractorPrecargado = paso.modeloTractor,
                    patenteSemiPrecargado = paso.patenteSemi,
                    marcaSemiPrecargado = paso.marcaSemi,
                    anioSemiPrecargado = paso.anioSemi,
                    modeloSemiPrecargado = paso.modeloSemi,
                    onVolverASesion = { volver() }
                )
                is Paso.NotaTrabajoGenerada -> NotaTrabajoGeneradaPaso(paso = paso, onVolverAlInicio = { irAInicio() })
            }
        }
    }
}

// ---------- Fase 3 (5/6): puente hacia feature-inspecciones (Activities+XML) ----------

/** Extras que lee InspeccionesMenuActivity.onCreate — Long.MIN_VALUE/"" significan "no vino
 *  de una sesión", igual que hoy cuando se abre desde el bottom nav. */
private const val EXTRA_SESION_ID = "sesionId"
private const val EXTRA_SESION_ITEM_ID = "sesionItemId"
private const val EXTRA_UNIDAD_ID_PRECARGADA = "unidadIdPrecargada"
private const val EXTRA_CLIENTE_PRECARGADO = "clientePrecargado"
private const val EXTRA_DOMICILIO_PRECARGADO = "domicilioPrecargado"
private const val EXTRA_CUIT_PRECARGADO = "cuitPrecargado"
private const val EXTRA_AUDITOR_PRECARGADO = "auditorPrecargado"
private const val EXTRA_PATENTE_TRACTOR_PRECARGADO = "patenteTractorPrecargado"
private const val EXTRA_MARCA_TRACTOR_PRECARGADO = "marcaTractorPrecargado"
private const val EXTRA_ANIO_TRACTOR_PRECARGADO = "anioTractorPrecargado"
private const val EXTRA_MODELO_TRACTOR_PRECARGADO = "modeloTractorPrecargado"
private const val EXTRA_PATENTE_SEMI_PRECARGADO = "patenteSemiPrecargado"
private const val EXTRA_MARCA_SEMI_PRECARGADO = "marcaSemiPrecargado"
private const val EXTRA_ANIO_SEMI_PRECARGADO = "anioSemiPrecargado"
private const val EXTRA_MODELO_SEMI_PRECARGADO = "modeloSemiPrecargado"
/** Cambio 3: el tipo ya se eligió en el checklist de Nueva Visita — este extra le dice a
 *  InspeccionesMenuActivity que se salte el menú de botones y arranque el flujo directo. */
private const val EXTRA_TIPO_INSPECCION_PRECARGADO = "tipoInspeccionPrecargado"

private fun tipoInspeccionDesdeCodigo(codigo: String): String? = when (codigo) {
    "insp_livianos" -> "LIVIANOS"
    "insp_pesados" -> "PESADOS"
    "insp_coque" -> "COQUE"
    "insp_quimicos" -> "QUIMICOS"
    "insp_enganche" -> "ENGANCHE"
    "insp_axion" -> "AXION"
    else -> null
}

@Composable
private fun AbrirInspeccionesPaso(paso: Paso.Inspeccion, onVolverASesion: () -> Unit) {
    val context = LocalContext.current

    // No inspeccionamos resultCode: si el usuario volvió (haya terminado la planilla o
    // simplemente tocado atrás), recargamos el estado del ítem — VisitaEnCursoPaso hace su
    // propio LaunchedEffect de recarga al recomponerse (mismo mecanismo que usan Calibrado/
    // ControlVisual/Certificado/Informe al volver con onVolverASesion).
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        onVolverASesion()
    }

    LaunchedEffect(paso) {
        val intent = Intent(context, InspeccionesMenuActivity::class.java).apply {
            putExtra(EXTRA_SESION_ID, paso.sesionId)
            putExtra(EXTRA_SESION_ITEM_ID, paso.sesionItemId)
            putExtra(EXTRA_UNIDAD_ID_PRECARGADA, paso.unidadId)
            putExtra(EXTRA_CLIENTE_PRECARGADO, paso.cliente)
            putExtra(EXTRA_DOMICILIO_PRECARGADO, paso.domicilio)
            putExtra(EXTRA_CUIT_PRECARGADO, paso.cuit)
            putExtra(EXTRA_AUDITOR_PRECARGADO, paso.auditor)
            putExtra(EXTRA_PATENTE_TRACTOR_PRECARGADO, paso.patenteTractor)
            putExtra(EXTRA_MARCA_TRACTOR_PRECARGADO, paso.marcaTractor)
            putExtra(EXTRA_ANIO_TRACTOR_PRECARGADO, paso.anioTractor)
            putExtra(EXTRA_MODELO_TRACTOR_PRECARGADO, paso.modeloTractor)
            putExtra(EXTRA_PATENTE_SEMI_PRECARGADO, paso.patenteSemi)
            putExtra(EXTRA_MARCA_SEMI_PRECARGADO, paso.marcaSemi)
            putExtra(EXTRA_ANIO_SEMI_PRECARGADO, paso.anioSemi)
            putExtra(EXTRA_MODELO_SEMI_PRECARGADO, paso.modeloSemi)
            tipoInspeccionDesdeCodigo(paso.tipoServicioCodigo)?.let { putExtra(EXTRA_TIPO_INSPECCION_PRECARGADO, it) }
        }
        launcher.launch(intent)
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Abriendo planillas de inspección…")
    }
}

// ---------- Fase 4: accesos secundarios (Nota de trabajo/Ejes/GLP) ----------

/** Estas 3 pantallas se diseñaron para el bottom nav de :app (sin back propio, cambiar de tab
 *  hacía las veces de "salir"). Ahora viven en el back-stack interno de feature-sesion: el
 *  BackHandler de SesionScreen ya hace que el botón físico/gesto de atrás funcione solo, pero
 *  se agrega un "← Volver" visible arriba para que también sea descubrible tocando la pantalla. */
@Composable
private fun AccesoSecundarioConVolver(titulo: String, onVolver: () -> Unit, contenido: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(onClick = onVolver) { Text("← Volver") }
            Text(titulo, style = MaterialTheme.typography.titleMedium)
        }
        Box(modifier = Modifier.weight(1f)) { contenido() }
    }
}

// ---------- Fase 4: confirmación de nota de trabajo autogenerada ----------

@Composable
private fun NotaTrabajoGeneradaPaso(paso: Paso.NotaTrabajoGenerada, onVolverAlInicio: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Visita finalizada", style = MaterialTheme.typography.titleLarge)
        Card {
            Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Unidad: ${paso.placa}")
                Text("Fecha: ${FMT_DDMMYYYY.format(Date(paso.fecha))}")
                Text("Servicios incluidos: ${paso.cantidadServicios}")
                Text(
                    if (paso.documentoUri != null) "Nota de trabajo generada en Descargas."
                    else "No se pudo generar la nota de trabajo automáticamente."
                )
            }
        }
        if (paso.documentoUri != null || paso.notaTrabajoUrl?.isNotBlank() == true) {
            Button(
                onClick = {
                    scope.launch {
                        // Fase F: intenta el archivo local primero (documentoUri); si no existe
                        // en este dispositivo, cae a la URL pública de Supabase Storage
                        // (notaTrabajoUrl) -- ver DocumentoStorageHelper.abrirDocumentoLocalOUrl
                        // en core-data, compartida con feature-nota-trabajo.
                        val abierto = DocumentoStorageHelper.abrirDocumentoLocalOUrl(
                            context, paso.documentoUri ?: "", paso.notaTrabajoUrl ?: ""
                        )
                        if (!abierto) {
                            Toast.makeText(context, "No se pudo abrir el documento", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Abrir documento") }
        }
        OutlinedButton(onClick = onVolverAlInicio, modifier = Modifier.fillMaxWidth()) { Text("Volver al inicio") }
    }
}

// ---------- Pantalla 1: Inicio de sesión ----------

@Composable
private fun InicioSesionPaso(
    db: AppDatabase,
    onNuevaVisita: () -> Unit,
    onAbrirVisita: (Long) -> Unit,
    onNotaTrabajo: () -> Unit
) {
    val context = LocalContext.current
    var visitas by remember { mutableStateOf<List<VisitaAbierta>>(emptyList()) }
    var cargando by remember { mutableStateOf(true) }

    // Fase C/D del pase a base de datos compartida: solo conexión/validación, sin tocar la UI --
    // confirma en logcat que Supabase responde, que hay sesión de Auth activa (login automático
    // con el usuario de servicio, antes de que cualquier módulo intente sincronizar) y que el
    // conteo remoto coincide con las 15 filas que CatalogoServiciosSeed ya siembra localmente.
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                com.aies.coredata.remote.SupabaseClientProvider.asegurarLogin()
                val cantidadRemota = com.aies.coredata.remote.SupabaseClientProvider.contarTiposServicioRemoto()
                if (cantidadRemota == 15) {
                    android.util.Log.d("SupabaseFaseC", "OK: tipos_servicio remoto trajo $cantidadRemota filas (esperadas: 15)")
                } else {
                    android.util.Log.w("SupabaseFaseC", "tipos_servicio remoto trajo $cantidadRemota filas, se esperaban 15")
                }
            } catch (e: Exception) {
                android.util.Log.e("SupabaseFaseC", "Fallo la conexión de prueba a Supabase", e)
            }
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val abiertas = db.sesionServicioDao().sesionesAbiertas()
            abiertas.map { sesion ->
                val placa = db.unidadDao().getPorId(sesion.unidadId)?.placa ?: "?"
                VisitaAbierta(sesion, placa)
            }
        }.let { visitas = it; cargando = false }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Visitas", style = MaterialTheme.typography.titleLarge)
        Button(onClick = onNuevaVisita, modifier = Modifier.fillMaxWidth()) { Text("+ Nueva visita") }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onNotaTrabajo, modifier = Modifier.weight(1f)) { Text("Historial / Notas de trabajo") }
            // Único acceso que quedaba colgando de "Sistema de Controles" (el menú de
            // Inspecciones que se eliminó) — ese botón vivía ahí, así que se reubica acá.
            OutlinedButton(
                onClick = { context.startActivity(Intent(context, HistorialActivity::class.java)) },
                modifier = Modifier.weight(1f)
            ) { Text("Historial de Inspecciones") }
        }

        if (!cargando && visitas.isEmpty()) {
            Text("Todavía no hay visitas abiertas.", style = MaterialTheme.typography.bodyMedium)
        }

        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            visitas.forEach { visita ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Text(visita.placa, style = MaterialTheme.typography.titleMedium)
                        Text(visita.sesion.cliente, style = MaterialTheme.typography.bodyMedium)
                        Text(FMT_DDMMYYYY.format(Date(visita.sesion.fecha)), style = MaterialTheme.typography.bodySmall)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            OutlinedButton(
                                onClick = { onAbrirVisita(visita.sesion.id) },
                                modifier = Modifier.testTag("e2e_abrir_visita_${visita.placa}")
                            ) { Text("Abrir") }
                        }
                    }
                }
            }
        }
    }
}

// ---------- Pantalla 2: Nueva visita ----------

/**
 * Selector de un valor ya usado antes (cliente, auditor) — reemplaza los viejos diálogos
 * "Seleccionar Empresa"/"Seleccionar Auditor" de feature-inspecciones (que además completaban
 * el nombre para la firma en los reportes; acá cumple el mismo rol porque el valor elegido es
 * el mismo `cliente`/`auditor` que ya se propaga a todos los módulos). El campo en sí es de solo
 * lectura y se despliega como un menú al tocarlo (nada de tipear directamente ahí); el ícono "+"
 * al lado abre un diálogo aparte para cargar un nombre nuevo cuando no está en la lista, que
 * [onAgregarNuevo] persiste en el catálogo compartido.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CampoConCatalogo(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    opciones: List<String>,
    onAgregarNuevo: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var mostrarAgregar by remember { mutableStateOf(false) }
    var nuevoNombre by remember { mutableStateOf("") }

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = Modifier.weight(1f)) {
            OutlinedTextField(
                value = value,
                onValueChange = {},
                readOnly = true,
                label = { Text(label) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedTextColor = MaterialTheme.colorScheme.onSurface)
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                if (opciones.isEmpty()) {
                    DropdownMenuItem(text = { Text("Todavía no hay ninguno cargado") }, onClick = { expanded = false }, enabled = false)
                }
                opciones.forEach { opcion ->
                    DropdownMenuItem(text = { Text(opcion) }, onClick = { onValueChange(opcion); expanded = false })
                }
            }
        }
        IconButton(onClick = { nuevoNombre = ""; mostrarAgregar = true }) {
            Icon(Icons.Default.Add, contentDescription = "Agregar $label")
        }
    }

    if (mostrarAgregar) {
        AlertDialog(
            onDismissRequest = { mostrarAgregar = false },
            title = { Text("Agregar $label") },
            text = {
                CampoTextoMayusculas(
                    value = nuevoNombre,
                    onValueChange = { nuevoNombre = it },
                    label = { Text(label) },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    enabled = nuevoNombre.isNotBlank(),
                    onClick = {
                        val nombre = nuevoNombre.trim()
                        onAgregarNuevo(nombre)
                        onValueChange(nombre)
                        mostrarAgregar = false
                    }
                ) { Text("Agregar") }
            },
            dismissButton = { TextButton(onClick = { mostrarAgregar = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun NuevaVisitaPaso(
    db: AppDatabase,
    onIniciada: (Long) -> Unit,
    onCancelar: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var patente by remember { mutableStateOf("") }
    var buscando by remember { mutableStateOf(false) }
    var busquedaHecha by remember { mutableStateOf(false) }
    var unidadEncontrada by remember { mutableStateOf<Unidad?>(null) }

    var nuevoTipoUnidad by remember { mutableStateOf(TipoUnidadOptions.lista[0]) }
    var nuevaCantidadCisternas by remember { mutableStateOf("1") }
    // Cambio 2: cuando la unidad ya existe, se muestra/confirma su cantidadCisternas actual acá
    // (puede haber cambiado desde la última visita) en vez de reusar en silencio lo que ya
    // estaba guardado.
    var cantidadCisternasExistente by remember { mutableStateOf("1") }

    var cliente by remember { mutableStateOf("") }
    var domicilio by remember { mutableStateOf("") }
    var cuit by remember { mutableStateOf("") }
    var auditor by remember { mutableStateOf("") }
    var fechaMillis by remember { mutableStateOf<Long?>(System.currentTimeMillis()) }

    // Cambio 1: 8 campos obligatorios nuevos, a nivel visita (no de la Unidad) — el tractor
    // puede cambiar entre visitas aunque la cisterna sea la misma.
    var patenteTractor by remember { mutableStateOf("") }
    var marcaTractor by remember { mutableStateOf("") }
    var anioTractor by remember { mutableStateOf("") }
    var modeloTractor by remember { mutableStateOf("") }
    var patenteSemi by remember { mutableStateOf("") }
    var marcaSemi by remember { mutableStateOf("") }
    var anioSemi by remember { mutableStateOf("") }
    var modeloSemi by remember { mutableStateOf("") }

    var tipos by remember { mutableStateOf<List<TipoServicio>>(emptyList()) }
    val seleccionados = remember { mutableStateListOf<TipoServicio>() }
    var cargandoTipos by remember { mutableStateOf(true) }

    // Catálogos de cliente/auditor ya usados en visitas anteriores (reemplaza los viejos
    // diálogos "Seleccionar Empresa"/"Seleccionar Auditor" de feature-inspecciones) — ver
    // CampoConCatalogo. clientesCatalogo guarda el registro completo (no solo el nombre) para
    // poder precargar el CUIT asociado al elegir un cliente.
    var clientesCatalogo by remember { mutableStateOf<List<ClienteCatalogo>>(emptyList()) }
    var auditoresCatalogo by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { CatalogoServiciosSeed.asegurarCatalogoCompleto(db) }
        tipos = withContext(Dispatchers.IO) { db.tipoServicioDao().getAll() }
        cargandoTipos = false
        clientesCatalogo = withContext(Dispatchers.IO) { db.clienteCatalogoDao().getAll() }
        auditoresCatalogo = withContext(Dispatchers.IO) { db.auditorCatalogoDao().getAll().map { it.nombre } }
    }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

    fun buscar() {
        if (patente.isBlank()) return
        buscando = true
        scope.launch {
            val unidad = withContext(Dispatchers.IO) { db.unidadDao().getPorPlaca(patente.trim()) }
            unidadEncontrada = unidad
            if (unidad != null) {
                cliente = unidad.cliente
                cantidadCisternasExistente = unidad.cantidadCisternas.toString()
                // Precarga el "último valor conocido" de esta unidad -- ver resolverUnidadId().
                // Solo si el dato guardado no está vacío, así no pisa con "" algo que la unidad
                // todavía no tenía guardado (unidades creadas antes de este cambio).
                if (unidad.cuit.isNotBlank()) cuit = unidad.cuit
                if (unidad.patenteTractor.isNotBlank()) patenteTractor = unidad.patenteTractor
                if (unidad.marcaTractor.isNotBlank()) marcaTractor = unidad.marcaTractor
                if (unidad.anioTractor.isNotBlank()) anioTractor = unidad.anioTractor
                if (unidad.modeloTractor.isNotBlank()) modeloTractor = unidad.modeloTractor
                if (unidad.patenteSemi.isNotBlank()) patenteSemi = unidad.patenteSemi
                if (unidad.marcaSemi.isNotBlank()) marcaSemi = unidad.marcaSemi
                if (unidad.anioSemi.isNotBlank()) anioSemi = unidad.anioSemi
                if (unidad.modeloSemi.isNotBlank()) modeloSemi = unidad.modeloSemi
            }
            busquedaHecha = true
            buscando = false
        }
    }

    /** Cambio 2: resuelve/crea la Unidad y confirma (o actualiza) su cantidadCisternas —
     *  devuelve null si el número ingresado no es válido. */
    suspend fun resolverUnidadId(): Long? {
        val existente = unidadEncontrada
        if (existente != null) {
            val cantidadConfirmada = cantidadCisternasExistente.toIntOrNull()?.coerceAtLeast(1) ?: return null
            if (cantidadConfirmada != existente.cantidadCisternas) {
                withContext(Dispatchers.IO) { db.unidadDao().actualizarCantidadCisternas(existente.id, cantidadConfirmada) }
            }
            return existente.id
        }
        val cantidad = nuevaCantidadCisternas.toIntOrNull()?.coerceAtLeast(1) ?: return null
        return withContext(Dispatchers.IO) {
            val nueva = Unidad(
                placa = patente.trim(),
                tipoUnidad = nuevoTipoUnidad,
                cliente = cliente,
                fechaRegistro = System.currentTimeMillis(),
                cantidadCisternas = cantidad,
                // Pedido del usuario: guardar en la unidad los datos que ya se tipearon acá
                // mismo para la visita, como "último valor conocido" -- se precargan la próxima
                // vez que se busque esta misma patente (ver buscar() más abajo).
                cuit = cuit.trim(),
                patenteTractor = patenteTractor.trim(), marcaTractor = marcaTractor.trim(),
                anioTractor = anioTractor.trim(), modeloTractor = modeloTractor.trim(),
                patenteSemi = patenteSemi.trim(), marcaSemi = marcaSemi.trim(),
                anioSemi = anioSemi.trim(), modeloSemi = modeloSemi.trim()
            )
            db.unidadDao().insertar(nueva)
            db.unidadDao().getPorPlaca(patente.trim())?.id
        }
    }

    fun iniciarVisita() {
        if (patente.isBlank()) { toast("Ingresá una patente"); return }
        if (seleccionados.isEmpty()) { toast("Seleccioná al menos un servicio"); return }
        if (cliente.isBlank() || domicilio.isBlank() || cuit.isBlank() || auditor.isBlank()) {
            toast("Completá cliente, domicilio, CUIT y auditor"); return
        }
        if (patenteTractor.isBlank() || marcaTractor.isBlank() || modeloTractor.isBlank() ||
            patenteSemi.isBlank() || marcaSemi.isBlank() || modeloSemi.isBlank()
        ) { toast("Completá los datos de tractor y semirremolque"); return }
        val anioTractorInt = anioTractor.toIntOrNull()
        val anioSemiInt = anioSemi.toIntOrNull()
        if (anioTractorInt == null || anioSemiInt == null) { toast("El año de tractor y semirremolque debe ser un número"); return }
        val cantidadCisternasTexto = if (unidadEncontrada != null) cantidadCisternasExistente else nuevaCantidadCisternas
        if (cantidadCisternasTexto.toIntOrNull() == null || cantidadCisternasTexto.toInt() < 1) {
            toast("Indicá la cantidad de cisternas (al menos 1)"); return
        }
        // SEC-007 (docs/security/AUDITORIA-2026-09-19.md): el CUIT es obligatorio y tiene un
        // formato preciso con dígito verificador -- se bloquea si no cierra, porque un CUIT mal
        // tipeado hoy queda guardado tal cual y termina en facturación/certificados. La patente
        // en cambio solo se avisa (no bloquea): hay formatos legítimos fuera de lo común
        // (patentes viejas de otras provincias, temporarias, etc.) y frenar al técnico en el
        // campo por un falso positivo es peor que dejar pasar una patente rara.
        if (!Validaciones.esCuitValido(cuit.trim())) {
            toast("El CUIT \"$cuit\" no es válido (formato 20-12345678-9, con dígito verificador correcto)")
            return
        }
        listOf(
            patente to "de la unidad",
            patenteTractor to "del tractor",
            patenteSemi to "del semirremolque"
        ).forEach { (valor, descripcion) ->
            if (!Validaciones.esPatenteValida(valor)) {
                toast("Aviso: la patente $descripcion (\"$valor\") no tiene un formato reconocido -- revisá que esté bien escrita")
            }
        }
        scope.launch {
            // Fase D, paso 1 de 5: resolverUnidadId() ahora puede pegarle a Supabase (insertar/
            // actualizarCantidadCisternas de UnidadDao no atrapan la excepción a propósito -- ver
            // su doc). Antes esto era Room puro y no podía fallar por red; ahora sí, y sin este
            // try/catch una falla de conexión acá crashearía toda la pantalla en vez de
            // mostrarle un error al técnico.
            val unidadId = try {
                resolverUnidadId()
            } catch (e: Exception) {
                // DIAGNÓSTICO TEMPORAL: mostrar la excepción real en vez del mensaje genérico,
                // para poder ver la causa concreta sin acceso a logcat. Revertir una vez
                // resuelto el bug que originó este cambio.
                toast("Error al guardar la unidad: ${e::class.simpleName}: ${e.message}"); return@launch
            }
            if (unidadId == null) { toast("No se pudo resolver la unidad"); return@launch }
            val fecha = fechaMillis ?: System.currentTimeMillis()
            // Fase D, paso 3 de 5: crearSesion()/agregarItems() de SesionServicioDao ahora pegan
            // contra Supabase primero y no atrapan la excepción a propósito (ver su doc). Antes
            // esto era Room puro y no podía fallar por red; ahora sí, y sin este try/catch una
            // falla de conexión acá terminaría en un crash no capturado de la corrutina en vez
            // de mostrarle un error al técnico.
            val sesionId = try {
                withContext(Dispatchers.IO) {
                    // Cualquier nombre nuevo (no elegido de la lista de CampoConCatalogo) queda
                    // guardado acá para aparecer en la próxima visita — así "agregar más" no
                    // necesita un diálogo aparte.
                    db.clienteCatalogoDao().insertar(ClienteCatalogo(cliente.trim(), cuit.trim()))
                    db.auditorCatalogoDao().insertar(AuditorCatalogo(auditor.trim()))
                    val sesionId = db.sesionServicioDao().crearSesion(
                        SesionServicio(
                            unidadId = unidadId,
                            fecha = fecha,
                            auditor = auditor,
                            cliente = cliente,
                            domicilio = domicilio,
                            cuit = cuit,
                            patenteTractor = patenteTractor.trim(),
                            marcaTractor = marcaTractor.trim(),
                            anioTractor = anioTractorInt,
                            modeloTractor = modeloTractor.trim(),
                            patenteSemi = patenteSemi.trim(),
                            marcaSemi = marcaSemi.trim(),
                            anioSemi = anioSemiInt,
                            modeloSemi = modeloSemi.trim(),
                            estado = "EN_PROGRESO",
                            horaInicio = System.currentTimeMillis(),
                            horaCierre = null
                        )
                    )
                    db.sesionServicioDao().agregarItems(
                        seleccionados.map {
                            SesionServicioItem(sesionId = sesionId, tipoServicioCodigo = it.codigo, estado = "PENDIENTE", historialServicioId = null)
                        }
                    )
                    sesionId
                }
            } catch (e: Exception) {
                // DIAGNÓSTICO TEMPORAL: idem comentario de arriba.
                toast("Error al guardar la visita: ${e::class.simpleName}: ${e.message}"); return@launch
            }
            onIniciada(sesionId)
        }
    }

    // El teclado no dejaba espacio para el contenido de abajo (los botones "Cancelar"/"Iniciar
    // visita" quedaban tapados al editar un campo de texto) -- imePadding() reserva ese espacio
    // dentro del área scrolleable, así se puede llegar a los botones scrolleando por encima del
    // teclado en vez de que quede atrás.
    Column(modifier = Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Nueva visita", style = MaterialTheme.typography.titleLarge)

        CampoTextoMayusculas(
            value = patente,
            onValueChange = { patente = it; busquedaHecha = false; unidadEncontrada = null },
            label = { Text("Patente *") },
            modifier = Modifier.fillMaxWidth().testTag("e2e_nueva_visita_patente"),
            trailingIcon = { OutlinedButton(onClick = { buscar() }, enabled = !buscando) { Text("Buscar") } }
        )

        if (busquedaHecha && unidadEncontrada == null) {
            Divider()
            Text("Unidad no encontrada — crear una nueva", style = MaterialTheme.typography.titleSmall)
            Text("Tipo de unidad")
            // Columna en vez de una sola Row: con las 6 opciones (se sumó AXION) una Row sin
            // wrap desbordaba el ancho de pantalla y las últimas quedaban cortadas/inalcanzables.
            Column {
                TipoUnidadOptions.lista.forEach { opcion ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        RadioButton(selected = nuevoTipoUnidad == opcion, onClick = { nuevoTipoUnidad = opcion })
                        Text(opcion)
                    }
                }
            }
            OutlinedTextField(
                value = nuevaCantidadCisternas,
                onValueChange = { nuevaCantidadCisternas = it },
                label = { Text("Cantidad de cisternas *") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedTextColor = MaterialTheme.colorScheme.onSurface)
            )
            Divider()
        } else if (unidadEncontrada != null) {
            Text("Unidad encontrada: ${unidadEncontrada?.tipoUnidad}", style = MaterialTheme.typography.bodyMedium)
            // Cambio 2: la cantidad guardada puede haber quedado desactualizada — se confirma
            // (o corrige) acá en cada visita en vez de reusarla en silencio.
            OutlinedTextField(
                value = cantidadCisternasExistente,
                onValueChange = { cantidadCisternasExistente = it },
                label = { Text("Cantidad de cisternas *") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedTextColor = MaterialTheme.colorScheme.onSurface)
            )
        }

        Text("Datos de la visita", style = MaterialTheme.typography.titleSmall)
        CampoConCatalogo(
            label = "Cliente",
            value = cliente,
            // Cliente y CUIT quedan asociados: elegir un cliente ya cargado precarga su CUIT
            // recordado (si lo tiene) en vez de tener que volver a tipearlo.
            onValueChange = { nombre ->
                cliente = nombre
                clientesCatalogo.find { it.nombre == nombre }?.let { if (it.cuit.isNotBlank()) cuit = it.cuit }
            },
            opciones = clientesCatalogo.map { it.nombre },
            onAgregarNuevo = { nombre ->
                scope.launch {
                    withContext(Dispatchers.IO) { db.clienteCatalogoDao().insertar(ClienteCatalogo(nombre)) }
                    clientesCatalogo = (clientesCatalogo.filterNot { it.nombre == nombre } + ClienteCatalogo(nombre)).sortedBy { it.nombre }
                }
            },
            modifier = Modifier.fillMaxWidth().testTag("e2e_nueva_visita_cliente")
        )
        CampoTextoMayusculas(value = domicilio, onValueChange = { domicilio = it }, label = { Text("Domicilio") }, modifier = Modifier.fillMaxWidth().testTag("e2e_nueva_visita_domicilio"))
        OutlinedTextField(
            value = cuit, onValueChange = { cuit = it }, label = { Text("CUIT") },
            modifier = Modifier.fillMaxWidth().testTag("e2e_nueva_visita_cuit"),
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedTextColor = MaterialTheme.colorScheme.onSurface)
        )
        CampoConCatalogo(
            label = "Auditor", value = auditor, onValueChange = { auditor = it }, opciones = auditoresCatalogo,
            onAgregarNuevo = { nombre ->
                scope.launch {
                    withContext(Dispatchers.IO) { db.auditorCatalogoDao().insertar(AuditorCatalogo(nombre)) }
                    auditoresCatalogo = (auditoresCatalogo + nombre).distinct().sorted()
                }
            },
            modifier = Modifier.fillMaxWidth().testTag("e2e_nueva_visita_auditor")
        )
        DateFieldSimple("Fecha", fechaMillis) { fechaMillis = it }

        Text("Tractor", style = MaterialTheme.typography.titleSmall)
        CampoTextoMayusculas(value = patenteTractor, onValueChange = { patenteTractor = it }, label = { Text("Patente tractor *") }, modifier = Modifier.fillMaxWidth().testTag("e2e_nueva_visita_patente_tractor"))
        CampoTextoMayusculas(value = marcaTractor, onValueChange = { marcaTractor = it }, label = { Text("Marca tractor *") }, modifier = Modifier.fillMaxWidth())
        CampoTextoMayusculas(value = modeloTractor, onValueChange = { modeloTractor = it }, label = { Text("Modelo tractor *") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            value = anioTractor, onValueChange = { anioTractor = it }, label = { Text("Año tractor *") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedTextColor = MaterialTheme.colorScheme.onSurface)
        )

        Text("Semirremolque", style = MaterialTheme.typography.titleSmall)
        CampoTextoMayusculas(value = patenteSemi, onValueChange = { patenteSemi = it }, label = { Text("Patente semirremolque *") }, modifier = Modifier.fillMaxWidth().testTag("e2e_nueva_visita_patente_semi"))
        CampoTextoMayusculas(value = marcaSemi, onValueChange = { marcaSemi = it }, label = { Text("Marca semirremolque *") }, modifier = Modifier.fillMaxWidth())
        CampoTextoMayusculas(value = modeloSemi, onValueChange = { modeloSemi = it }, label = { Text("Modelo semirremolque *") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            value = anioSemi, onValueChange = { anioSemi = it }, label = { Text("Año semirremolque *") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedTextColor = MaterialTheme.colorScheme.onSurface)
        )

        Text("Servicios a realizar", style = MaterialTheme.typography.titleSmall)
        if (!cargandoTipos && tipos.isEmpty()) {
            Text(
                "Todavía no hay tipos de servicio en el catálogo (se registran la primera vez que corre cada módulo de servicio).",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        tipos.sortedBy { it.nombre }.forEach { tipo ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = seleccionados.contains(tipo),
                    onCheckedChange = { marcado -> if (marcado) seleccionados.add(tipo) else seleccionados.remove(tipo) },
                    modifier = Modifier.testTag("e2e_servicio_checkbox_${tipo.codigo}")
                )
                Text(tipo.nombre)
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // BUGFIX (encontrado por EndToEndFlowTest): los dos botones usaban
            // Modifier.fillMaxWidth() en vez de Modifier.weight(1f) -- dentro de un Row sin
            // peso, cada hijo se mide con el ancho disponible RESTANTE, así que "Cancelar"
            // (medido primero) se comía todo el ancho de la fila y a "Iniciar visita" le quedaba
            // 0px de ancho disponible: el nodo existía en el árbol de semántica (con su OnClick)
            // pero con bounds de ancho cero, así que ningún toque real (ni performClick() del
            // test, que despacha en el centro de esas bounds) podía llegar a activarlo. Con weight(1f)
            // ambos botones comparten la fila mitad y mitad, como visualmente ya parecía.
            OutlinedButton(onClick = onCancelar, modifier = Modifier.weight(1f)) { Text("Cancelar") }
            Button(onClick = { iniciarVisita() }, modifier = Modifier.weight(1f)) { Text("Iniciar visita") }
        }
    }
}

// ---------- Pantalla 3: Visita en curso ----------

@Composable
private fun VisitaEnCursoPaso(
    db: AppDatabase,
    sesionId: Long,
    onFinalizada: (Paso.NotaTrabajoGenerada) -> Unit,
    onAbrirItem: (sesion: SesionServicio, unidad: Unidad, item: ItemDeSesion) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var sesion by remember { mutableStateOf<SesionServicio?>(null) }
    var unidad by remember { mutableStateOf<Unidad?>(null) }
    var items by remember { mutableStateOf<List<ItemDeSesion>>(emptyList()) }
    var puedeFinalizar by remember { mutableStateOf(false) }
    var cargando by remember { mutableStateOf(true) }
    var finalizando by remember { mutableStateOf(false) }
    var itemAEliminar by remember { mutableStateOf<ItemDeSesion?>(null) }
    var tiposDisponibles by remember { mutableStateOf<List<TipoServicio>>(emptyList()) }
    var mostrarAgregarServicio by remember { mutableStateOf(false) }
    val seleccionadosNuevos = remember { mutableStateListOf<TipoServicio>() }

    suspend fun recargar() {
        withContext(Dispatchers.IO) {
            val s = db.sesionServicioDao().getPorId(sesionId)
            val u = s?.let { db.unidadDao().getPorId(it.unidadId) }
            val i = db.sesionServicioDao().itemsDeSesion(sesionId)
            val puede = db.sesionServicioDao().todosLosItemsCompletos(sesionId)
            Triple(s, u, i) to puede
        }.let { (datos, puede) ->
            sesion = datos.first
            unidad = datos.second
            items = datos.third
            puedeFinalizar = puede
            cargando = false
        }
    }

    LaunchedEffect(sesionId) { recargar() }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { CatalogoServiciosSeed.asegurarCatalogoCompleto(db) }
        tiposDisponibles = withContext(Dispatchers.IO) { db.tipoServicioDao().getAll() }
    }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Visita en curso", style = MaterialTheme.typography.titleLarge)

        if (!cargando) {
            Card {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(unidad?.placa ?: "?", style = MaterialTheme.typography.titleMedium)
                    Text("Cliente: ${sesion?.cliente ?: ""}")
                    Text("Auditor: ${sesion?.auditor ?: ""}")
                    Text("Fecha: ${sesion?.fecha?.let { FMT_DDMMYYYY.format(Date(it)) } ?: ""}")
                }
            }
        }

        Text("Servicios", style = MaterialTheme.typography.titleSmall)
        items.forEach { item ->
            val completo = item.estado == "COMPLETO"
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(item.tipoServicioNombre, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    if (!completo) {
                        OutlinedButton(onClick = { itemAEliminar = item }) { Text("Quitar") }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    OutlinedButton(
                        onClick = {
                            if (completo) {
                                toast("Ya completo")
                            } else {
                                val s = sesion; val u = unidad
                                if (s != null && u != null) onAbrirItem(s, u, item) else toast("Cargando datos de la visita, esperá un momento")
                            }
                        },
                        modifier = Modifier.testTag("e2e_item_estado_${item.tipoServicioCodigo}")
                    ) {
                        Text(if (completo) "COMPLETO" else "PENDIENTE")
                    }
                }
            }
        }

        OutlinedButton(
            onClick = { seleccionadosNuevos.clear(); mostrarAgregarServicio = true },
            modifier = Modifier.fillMaxWidth()
        ) { Text("+ Agregar servicio") }

        if (mostrarAgregarServicio) {
            val yaAgregados = items.map { it.tipoServicioCodigo }.toSet()
            val candidatos = tiposDisponibles.filter { it.codigo !in yaAgregados }.sortedBy { it.nombre }
            AlertDialog(
                onDismissRequest = { mostrarAgregarServicio = false },
                title = { Text("Agregar servicio") },
                text = {
                    // Sin scroll acá, con el catálogo ya en ~19 códigos, las últimas filas quedaban
                    // cortadas fuera del área del diálogo (que no crece más allá de la pantalla) --
                    // en vez de "faltar" servicios, simplemente no se podían ver ni scrollear.
                    Column(
                        modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (candidatos.isEmpty()) {
                            Text("Ya están todos los servicios del catálogo agregados a esta visita.")
                        }
                        candidatos.forEach { tipo ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = seleccionadosNuevos.contains(tipo),
                                    onCheckedChange = { marcado -> if (marcado) seleccionadosNuevos.add(tipo) else seleccionadosNuevos.remove(tipo) }
                                )
                                Text(tipo.nombre)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = seleccionadosNuevos.isNotEmpty(),
                        onClick = {
                            val nuevos = seleccionadosNuevos.toList()
                            mostrarAgregarServicio = false
                            scope.launch {
                                // Fase D, paso 3 de 5: agregarItems() ahora pega contra Supabase
                                // primero y no atrapa la excepción a propósito -- sin este
                                // try/catch una falla de conexión acá crashearía la corrutina.
                                try {
                                    withContext(Dispatchers.IO) {
                                        db.sesionServicioDao().agregarItems(
                                            nuevos.map { SesionServicioItem(sesionId = sesionId, tipoServicioCodigo = it.codigo, estado = "PENDIENTE", historialServicioId = null) }
                                        )
                                    }
                                } catch (e: Exception) {
                                    toast("No se pudo agregar el servicio (sin conexión). Probá de nuevo."); return@launch
                                }
                                recargar()
                            }
                        }
                    ) { Text("Agregar") }
                },
                dismissButton = { TextButton(onClick = { mostrarAgregarServicio = false }) { Text("Cancelar") } }
            )
        }

        itemAEliminar?.let { item ->
            AlertDialog(
                onDismissRequest = { itemAEliminar = null },
                title = { Text("Quitar servicio") },
                text = { Text("¿Quitar \"${item.tipoServicioNombre}\" de esta visita? Esta acción no se puede deshacer.") },
                confirmButton = {
                    TextButton(onClick = {
                        val id = item.id
                        itemAEliminar = null
                        scope.launch {
                            // Fase D, paso 3 de 5: eliminarItem() ahora pega contra Supabase
                            // primero y no atrapa la excepción a propósito -- sin este try/catch
                            // una falla de conexión acá crashearía la corrutina.
                            try {
                                withContext(Dispatchers.IO) { db.sesionServicioDao().eliminarItem(id) }
                            } catch (e: Exception) {
                                toast("No se pudo quitar el servicio. Probá de nuevo."); return@launch
                            }
                            recargar()
                        }
                    }) { Text("Quitar") }
                },
                dismissButton = {
                    TextButton(onClick = { itemAEliminar = null }) { Text("Cancelar") }
                }
            )
        }

        Button(
            onClick = {
                finalizando = true
                scope.launch {
                    val u = unidad; val s = sesion
                    // Fase D, paso 3 de 5: cerrarSesion() ahora pega contra Supabase primero y no
                    // atrapa la excepción a propósito -- sin este try/catch una falla de conexión
                    // acá crashearía la corrutina en vez de dejar la visita abierta para reintentar.
                    try {
                        withContext(Dispatchers.IO) { db.sesionServicioDao().cerrarSesion(sesionId, System.currentTimeMillis()) }
                    } catch (e: Exception) {
                        finalizando = false
                        toast("No se pudo finalizar la visita (sin conexión). Probá de nuevo.")
                        return@launch
                    }
                    val resultado = try {
                        withContext(Dispatchers.IO) {
                            if (u == null || s == null) return@withContext null
                            val (inicio, fin) = rangoDelDia(s.fecha)
                            // Cada módulo tractor+semi escribe su documento bajo la Unidad del
                            // tractor Y la del semi (no necesariamente la misma que esta sesión
                            // resolvió con su propio campo "Patente" de Nueva Visita) — buscar solo
                            // contra u.id se quedaba corto. Se buscan las 3 unidades de la visita.
                            val unidadIds = listOfNotNull(
                                u.id,
                                db.unidadDao().getPorPlaca(s.patenteTractor)?.id,
                                db.unidadDao().getPorPlaca(s.patenteSemi)?.id
                            ).distinct()
                            // (unidadId, día) solo alcanza para "qué unidad" — sin filtrar por tipo
                            // también trae servicios de OTRAS visitas hechas ese mismo día a la
                            // misma unidad (típico en pruebas repetidas), que no tienen nada que ver
                            // con esta sesión. Se acota a los tipos que esta sesión realmente eligió.
                            val codigosDeEstaSesion = items.map { it.tipoServicioCodigo }.toSet()
                            val servicios = db.historialServicioDao()
                                .serviciosDeVariasUnidadesEnElDia(unidadIds, inicio, fin)
                                .filter { it.tipoServicioCodigo in codigosDeEstaSesion }
                            // Capacidad total (suma de litros de todas las cisternas) según la calibración
                            // vigente de la unidad a la fecha de la visita — para el detalle del Informe de
                            // Carga Segura en la Nota de Trabajo. Null si no hay calibración vigente.
                            val capacidadTotal = db.calibracionDao().calibracionVigenteDeUnidad(u.id, s.fecha)
                                ?.cisternas?.sumOf { it.capacidad ?: 0.0 }
                            val pdf = NotaTrabajoPdfBuilder.generar(context, u, s.fecha, servicios, capacidadTotal, patenteTractor = s.patenteTractor)
                            val nombreArchivo = "NotaTrabajo_${u.placa}_${SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date(s.fecha))}.pdf".replace(" ", "")
                            val uri = try {
                                MediaStoreUtils.guardarEnDescargas(context, nombreArchivo, "application/pdf") { pdf.writeTo(it) }
                            } finally {
                                pdf.close()
                            }
                            Triple(u.placa, s.fecha, servicios) to uri
                        }
                    } catch (e: Exception) {
                        // MediaStoreUtils.guardarEnDescargas ahora tira si el archivo no se pudo
                        // escribir de verdad (antes devolvía una Uri "exitosa" con 0 bytes, que ni
                        // se veía bien en Descargas ni se podía adjuntar al mail -- ver doc de esa
                        // función). Antes esto no estaba contemplado acá y hubiera crasheado la
                        // corrutina; ahora cae al mismo mensaje que "no se pudo generar la nota".
                        Log.w("SesionScreen", "No se pudo generar/guardar la Nota de Trabajo", e)
                        null
                    }
                    finalizando = false
                    if (resultado == null) {
                        toast("Visita cerrada, pero no se pudo generar la nota de trabajo")
                        onFinalizada(Paso.NotaTrabajoGenerada(unidad?.placa ?: "?", sesion?.fecha ?: System.currentTimeMillis(), 0, null, sesion?.notaTrabajoUrl))
                    } else {
                        val (datos, uri) = resultado
                        val (placa, fecha, servicios) = datos
                        // Envío consolidado: un único ACTION_SEND_MULTIPLE con todos los
                        // documentos generados durante la visita (documentoPath de cada
                        // HistorialServicio, ya son URIs compartibles — MediaStore o FileProvider
                        // según el módulo) más la Nota de Trabajo recién generada. Reemplaza el
                        // compartir individual que cada módulo abría al completar su servicio
                        // (ahora suprimido cuando sesionId != null — ver cada módulo).
                        compartirDocumentosDeVisita(context, placa, fecha, servicios, uri)
                        // Fase F: subida a Supabase Storage en un scope DESACOPLADO del llamador
                        // -- no se usa `scope` (el rememberCoroutineScope de este Composable)
                        // porque esta pantalla navega a Paso.NotaTrabajoGenerada apenas
                        // onFinalizada() retorna, lo que puede cancelar ese scope a mitad de la
                        // subida. No se espera a que termine antes de continuar.
                        if (uri != null) {
                            CoroutineScope(Dispatchers.IO).launch {
                                val url = DocumentoStorageHelper.subirYObtenerUrlPublica(context, uri, "nota-trabajo", sesionId.toString())
                                if (url != null) db.sesionServicioDao().actualizarNotaTrabajoUrl(sesionId, url)
                            }
                        }
                        // El PDF de "reporte de observaciones" que insp_* genera junto al .xls no
                        // cuenta como un servicio aparte -- ver NotaTrabajoPdfBuilder.esReportePdfDuplicado.
                        val cantidadServiciosReal = servicios.count { !NotaTrabajoPdfBuilder.omitirEnNota(context, it) }
                        onFinalizada(Paso.NotaTrabajoGenerada(placa, fecha, cantidadServiciosReal, uri?.toString(), s?.notaTrabajoUrl))
                    }
                }
            },
            enabled = puedeFinalizar && !finalizando,
            modifier = Modifier.fillMaxWidth().testTag("e2e_finalizar_visita")
        ) { Text(if (finalizando) "Finalizando…" else "Finalizar visita") }
    }
}

/**
 * Envío consolidado al finalizar una visita: un único ACTION_SEND_MULTIPLE con todos los
 * documentos generados (uno por servicio completado, más la Nota de Trabajo), en vez del
 * compartir individual que cada módulo abría al terminar su propio servicio (suprimido cuando
 * viene de una sesión — ver cada módulo). `documentoPath` de cada [ServicioDelDia] ya es una URI
 * compartible (MediaStore o FileProvider según el módulo que la generó), así que alcanza con
 * parsearla — no hace falta re-derivarla módulo por módulo.
 */
/**
 * Mismo criterio que NotaTrabajoPdfBuilder.etiquetaServicio (duplicado a propósito: viven en
 * módulos distintos que no se dependen entre sí) — Livianos/Pesados/Coque/Quimicos/Enganche/
 * Axion generan un .xls (checklist) y un PDF (reporte de observaciones) por servicio, y ambos
 * comparten el mismo tipoServicioNombre; sin esto el cuerpo del mail repetía "Inspección
 * PESADOS" dos veces en vez de distinguir cuál es el checklist y cuál el reporte. Se distingue
 * por el tipo MIME real (ContentResolver), no por la URI como string — las URIs de MediaStore
 * son ids opacos que nunca terminan en ".pdf"/".xls".
 */
private fun etiquetaServicio(context: android.content.Context, s: ServicioDelDia): String {
    if (!s.tipoServicioCodigo.startsWith("insp_") || s.documentoPath.isBlank()) return s.tipoServicioNombre
    val mime = try { context.contentResolver.getType(Uri.parse(s.documentoPath)) } catch (_: Exception) { null }
    val esReportePdf = mime == "application/pdf"
    return if (esReportePdf) "Reporte de observaciones - ${s.tipoServicioNombre.removePrefix("Inspección ").trim()}"
    else s.tipoServicioNombre
}

private fun compartirDocumentosDeVisita(
    context: android.content.Context,
    placa: String,
    fechaMillis: Long,
    servicios: List<ServicioDelDia>,
    notaTrabajoUri: Uri?
) {
    val uris = ArrayList<Uri>()
    servicios.forEach { s ->
        if (s.documentoPath.isNotBlank()) {
            try { uris.add(Uri.parse(s.documentoPath)) } catch (_: Exception) { /* URI corrupta/vacía: se omite ese adjunto */ }
        }
    }
    notaTrabajoUri?.let { uris.add(it) }
    if (uris.isEmpty()) return

    // BUGFIX (reportado: "cuando aprieto abrir mail para enviar todos los archivos no lo
    // abre"): contentResolver.getType() puede tirar (URI con permiso ya revocado, o de un
    // FileProvider de otro módulo) -- sin este try/catch por-URI, esa excepción se escapaba
    // de esta función y moría en silencio dentro de la corrutina del botón "Finalizar
    // visita", sin toast ni crash visible: el chooser de mail nunca llegaba a abrirse.
    val tipos = uris.mapNotNull { try { context.contentResolver.getType(it) } catch (_: Exception) { null } }.toSet()
    val mimeType = if (tipos.size == 1) tipos.first() else "*/*"

    val nombresServicios = servicios.joinToString("\n") { "- ${etiquetaServicio(context, it)}" }
    val cuerpo = "Adjuntamos la Nota de Trabajo y los documentos generados en la visita a $placa:\n\n$nombresServicios\n- Nota de Trabajo"

    val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = mimeType
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        putExtra(Intent.EXTRA_SUBJECT, "Nota de trabajo - $placa - ${FMT_DDMMYYYY.format(Date(fechaMillis))}")
        putExtra(Intent.EXTRA_TEXT, cuerpo)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    // Los adjuntos vienen de autoridades content:// mezcladas (MediaStore Downloads para la
    // Nota de Trabajo, FileProvider de cada módulo insp_* para sus .xls/.pdf) -- el flag
    // FLAG_GRANT_READ_URI_PERMISSION del intent no siempre alcanza para que la app de mail
    // elegida pueda leerlas todas (Gmail y similares silenciosamente no abren el compose, o
    // lo abren sin poder adjuntar, si falta el permiso explícito sobre alguna). Se otorga el
    // permiso a mano a cada app candidata, igual que hace Android internamente para un solo
    // receptor pero que ACTION_SEND_MULTIPLE con URIs de distintos providers no garantiza.
    val candidatos = context.packageManager.queryIntentActivities(intent, 0)
    for (info in candidatos) {
        val paquete = info.activityInfo?.packageName ?: continue
        uris.forEach { uri ->
            try {
                context.grantUriPermission(paquete, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) { /* provider que no soporta grant explícito: se ignora, ya tiene el flag del intent */ }
        }
    }
    // Sin este try/catch, si ningún componente puede resolver el chooser (ActivityNotFoundException)
    // o el sistema lo rechaza por cualquier otro motivo, "Finalizar visita" terminaba en silencio
    // -- el técnico veía que no pasaba nada, sin ningún aviso de qué falló.
    try {
        context.startActivity(Intent.createChooser(intent, "Enviar documentos de la visita").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Toast.makeText(context, "No se pudo abrir el selector para compartir: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

// ---------- Utilidad de fecha compartida ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateFieldSimple(label: String, millis: Long?, onChange: (Long?) -> Unit) {
    var mostrar by remember { mutableStateOf(false) }
    val texto = millis?.let { FMT_DDMMYYYY.format(Date(it)) } ?: ""
    OutlinedTextField(
        value = texto, onValueChange = {}, readOnly = true, label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        trailingIcon = { OutlinedButton(onClick = { mostrar = true }) { Text("📅") } },
        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedTextColor = MaterialTheme.colorScheme.onSurface)
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
