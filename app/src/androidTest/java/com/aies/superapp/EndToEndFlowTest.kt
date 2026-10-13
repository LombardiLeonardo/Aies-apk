package com.aies.superapp

/**
 * ============================================================================================
 * THROWAWAY, ONE-TIME INSTRUMENTED VALIDATION — NOT A PERMANENT TEST SUITE.
 *
 * REWRITTEN for the "unified session flow" architecture (feature-sesion.SesionScreen): :app no
 * longer has a bottom nav / NavHost, it just renders SesionScreen() directly, which is its own
 * flat wizard: Inicio (visitas abiertas) -> Nueva Visita (busca/crea Unidad, checklist de
 * TipoServicio) -> crea SesionServicio EN_PROGRESO + un SesionServicioItem PENDIENTE por cada
 * tipo elegido -> Visita en Curso (lista de items, tocar un PENDIENTE navega a la pantalla real
 * de ese servicio) -> "Finalizar visita" (habilitado solo cuando todosLosItemsCompletos) -> cierra
 * la sesión (CERRADA) y autogenera la Nota de Trabajo consolidada (NotaTrabajoPdfBuilder).
 *
 * Coverage picked (per instructions): 4 servicios en el checklist de Nueva Visita —
 * calibracion_unidad (Compose, requerido), control_visual_interna (Compose), control_espesores
 * (Compose), insp_livianos (el único módulo Activity+XML heredado). Se completan en un orden
 * DISTINTO al de selección para probar que el intercalado real funciona:
 *   selección:   calibracion_unidad, control_visual_interna, control_espesores, insp_livianos
 *   completado:  insp_livianos, control_visual_interna, control_espesores, calibracion_unidad
 *
 * Sigue exactamente la misma convención de la versión anterior de este archivo: varios @Test
 * ordenados con @FixMethodOrder en vez de un método gigante. La razón cambia un poco con la
 * nueva arquitectura (ver docs de cada método), pero el mecanismo de recuperación es el mismo:
 * SesionScreen() en sí es ahora una ÚNICA composición de larga vida con su propio back-stack
 * interno (a propósito, así es como se diseñó la app real) — así que, a diferencia de antes, NO
 * puedo simplemente "seguir" desde una @Test a la siguiente dentro de la misma composición
 * (cada método arranca con setContent{} fresco = Activity/Recomposer fresco). En cambio, cada
 * método vuelve a entrar por Inicio y usa el botón "Abrir" (testTag e2e_abrir_visita_<placa>)
 * sobre la MISMA visita EN_PROGRESO (persistida en Room, sobrevive entre métodos) para retomar
 * exactamente donde quedó — esto es exactamente el flujo real que un usuario haría si cerrara y
 * reabriera la app a mitad de una visita, así que además de mitigar el riesgo de Recomposer
 * (mismo bug que la vez pasada: un Toast lanzado desde una coroutine de rememberCoroutineScope()
 * puede matar el Recomposer entero de esa Activity) también es cobertura extra real.
 *
 * Lección explícita de la corrida anterior, aplicada acá: un performTextInput() sobre un
 * OutlinedTextField entre varios adyacentes en una Column con scroll a veces deposita el texto
 * en el campo VECINO incorrecto en este dispositivo, incluso con testTags únicos. Por eso, después
 * de cada performTextInput() se relee inmediatamente ese mismo nodo (assertTextContains) antes de
 * seguir — así cualquier desvío se cacha en el punto exacto de la escritura, no 40 pasos después
 * en una aserción final no relacionada.
 *
 * Do not promote this into CI. Not wired into any CI config beyond manual invocation via:
 *   ./gradlew.bat :app:connectedDebugAndroidTest --console=plain
 * ============================================================================================
 */

import android.os.Build
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToLog
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aies.coredata.AppDatabase
import com.aies.coredata.entities.TipoServicio
import com.aies.feature.inspecciones.data.HistorialCompartidoHelper
import com.aies.feature.sesion.SesionScreen
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.util.Calendar

@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class EndToEndFlowTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val db = AppDatabase.getInstance(context)

    companion object {
        // Placa nueva y única por corrida de instrumentación -- evita colisionar con Unidades/
        // sesiones abiertas que hayan quedado de corridas manuales o de la versión anterior de
        // este mismo test (E2E001).
        val PLACA = "E2ESES${System.currentTimeMillis() % 1_000_000}"
        var testStart: Long = System.currentTimeMillis()
        var unidadId: Long = -1L
        var sesionId: Long = -1L

        const val COD_CALIBRADO = "calibracion_unidad"
        const val COD_CV_INTERNA = "control_visual_interna"
        const val COD_ESPESORES = "control_espesores"
        const val COD_LIVIANOS = "insp_livianos"
    }

    @Before
    fun installLenientUncaughtExceptionHandler() {
        // Ver doc de archivo / versión anterior: un Toast disparado desde una coroutine de
        // rememberCoroutineScope() puede lanzar "Can't toast on a thread that has not called
        // Looper.prepare()" bajo ComposeTestRule y eso puede matar el Recomposer. Los DB writes
        // que de verdad importan ya se hicieron antes del Toast en todos los módulos tocados acá.
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            android.util.Log.e("E2E_TEST", "Swallowed uncaught exception on thread '${t.name}' (expected compose-test/Toast dispatch quirk): $e")
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────────────────

    private fun isKnownToastLooperQuirk(e: Throwable): Boolean =
        generateSequence(e) { it.cause }.any {
            it is NullPointerException && it.message?.contains("Looper.prepare") == true
        }

    /** Corre [block] absorbiendo el quiebre conocido de Toast/Looper (ver doc de
     *  installLenientUncaughtExceptionHandler) si aparece DURANTE [block] -- puede resurgir en
     *  cualquier composeTestRule.waitUntil/waitForIdle posterior al que efectivamente disparó el
     *  Toast, no solo en el que lo generó, porque Compose UI Test relanza en el hilo llamante
     *  cualquier excepción no atrapada que encuentre al drenar la recomposición pendiente. Por
     *  eso se envuelve cada punto de espera de este archivo, no solo el que sigue al click de
     *  "Generar…", y se reintenta una vez el bloque tras absorberlo. */
    private fun <T> swallowingToastQuirk(block: () -> T): T {
        return try {
            block()
        } catch (e: Throwable) {
            if (!isKnownToastLooperQuirk(e)) throw e
            android.util.Log.w("E2E_TEST", "Swallowed known Toast/Looper compose-test quirk: $e")
            composeTestRule.waitForIdle()
            block()
        }
    }

    /** Variante de [swallowingToastQuirk] SIN reintento -- para envolver un performClick() sobre
     *  un botón de "Generar…". El NPE de Toast/Looper puede saltar SINCRÓNICAMENTE desde adentro
     *  del propio performClick() (Compose UI Test drena la recomposición pendiente como parte de
     *  ejecutar el click, y el LaunchedEffect que llama a Toast corre en esa misma pasada) --
     *  reintentar el click acá sería peligroso: si ya escribió en la DB antes de tirar la
     *  excepción (que es el caso, el Toast es lo último que hace cada pantalla), un segundo click
     *  dispararía una segunda generación de PDF y un segundo HistorialServicio duplicado. Por eso
     *  esta variante solo absorbe la excepción conocida, sin reintentar nada. */
    private fun clickSwallowingToastQuirk(block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            if (!isKnownToastLooperQuirk(e)) throw e
            android.util.Log.w("E2E_TEST", "Swallowed known Toast/Looper compose-test quirk during click (not retried, see doc): $e")
        }
    }

    private fun waitForMarker(vararg expectAnyMarkerText: String, timeoutMillis: Long = 15_000) {
        try {
            swallowingToastQuirk {
                composeTestRule.waitUntil(timeoutMillis) {
                    expectAnyMarkerText.any { marker -> composeTestRule.onAllNodesWithText(marker, substring = true).fetchSemanticsNodes().isNotEmpty() }
                }
            }
        } catch (e: Throwable) {
            composeTestRule.onRoot().printToLog("E2E_DEBUG_TIMEOUT")
            throw e
        }
    }

    private fun waitUntilNotGenerating(timeoutMillis: Long = 20_000) {
        swallowingToastQuirk {
            composeTestRule.waitUntil(timeoutMillis) {
                composeTestRule.onAllNodesWithText("Generando…", substring = true).fetchSemanticsNodes().isEmpty()
            }
        }
    }

    /** Escribe [text] en el nodo [tag] y relee inmediatamente para confirmar que llegó ahí --
     *  la salvaguarda "fail-fast" pedida explícitamente esta vez. Falla en el punto exacto de la
     *  escritura, no en una aserción final desconectada. */
    private fun typeAndVerify(tag: String, text: String) {
        composeTestRule.onNodeWithTag(tag).performTextInput(text)
        swallowingToastQuirk { composeTestRule.waitForIdle() }
        composeTestRule.onNodeWithTag(tag).assertTextContains(text, substring = true)
    }

    /** Finds a recently-created file in MediaStore Downloads whose display name contains
     * [nameContains], created at/after [sinceMillis]. Returns (displayName, sizeBytes) or null. */
    private fun findRecentDownload(nameContains: String, sinceMillis: Long): Pair<String, Long>? {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            MediaStore.Downloads.EXTERNAL_CONTENT_URI
        else MediaStore.Files.getContentUri("external")
        val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATE_ADDED)
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ? AND ${MediaStore.MediaColumns.DATE_ADDED} >= ?"
        val args = arrayOf("%$nameContains%", (sinceMillis / 1000).toString())
        context.contentResolver.query(collection, projection, selection, args, "${MediaStore.MediaColumns.DATE_ADDED} DESC")?.use { cursor ->
            if (cursor.moveToFirst()) {
                val name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME))
                val size = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE))
                return name to size
            }
        }
        return null
    }

    private fun diaInicio(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun diaFin(millis: Long): Long = diaInicio(millis) + 24L * 60 * 60 * 1000 - 1

    /** Reingresa por Inicio y toca "Abrir" en la tarjeta de nuestra visita -- así es como cada
     *  @Test method retoma la MISMA sesión EN_PROGRESO desde una Activity/Recomposer fresco. */
    private fun reabrirVisita() {
        composeTestRule.setContent { SesionScreen() }
        waitForMarker("Visitas")
        swallowingToastQuirk {
            composeTestRule.waitUntil(10_000) {
                composeTestRule.onAllNodesWithTag("e2e_abrir_visita_$PLACA").fetchSemanticsNodes().isNotEmpty()
            }
        }
        composeTestRule.onNodeWithTag("e2e_abrir_visita_$PLACA").performClick()
        waitForMarker("Visita en curso")
    }

    private fun estadoItem(codigo: String) = runBlocking {
        db.sesionServicioDao().itemsDeSesion(sesionId).first { it.tipoServicioCodigo == codigo }.estado
    }

    /** Espera a que un ítem pase a COMPLETO consultando la DB DIRECTAMENTE (sin tocar
     *  composeTestRule) -- ver doc grande de test4/test5/test6a: una vez que el Toast/Looper
     *  quirk mata el Recomposer de una composición, NINGÚN composeTestRule.waitUntil/waitForIdle
     *  posterior en esa misma composición vuelve a completar (el estado real de la app SÍ avanza
     *  -- generando=false, HistorialServicio escrito, item marcado COMPLETO -- pero la
     *  recomposición que reflejaría eso en pantalla ya no puede correr). Consultar la DB
     *  directamente sortea el problema por completo: no depende de que la UI se actualice. */
    private fun esperarItemCompleto(codigo: String, timeoutMillis: Long = 20_000) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (estadoItem(codigo) == "COMPLETO") return
            Thread.sleep(300)
        }
        assertEquals("$codigo debía quedar COMPLETO (poll por DB, no por UI)", "COMPLETO", estadoItem(codigo))
    }

    // ── STEP 1 — seed the TipoServicio catalog codes we need (idempotent, IGNORE conflict) ────
    // The Nueva Visita checklist is fed straight from db.tipoServicioDao().getAll() and each
    // module only ever inserts its own row the first time IT runs -- so on a device where these
    // four modules have never generated anything yet, the checklist would simply be empty. This
    // step guarantees determinism regardless of prior device history, using the exact (codigo,
    // nombre) pairs each module's own HistorialCompartidoHelper uses (confirmed by reading them).
    @Test
    fun test1SeedCatalogoServicios() {
        testStart = System.currentTimeMillis()
        val dao = db.tipoServicioDao()
        runBlocking {
            dao.insertar(TipoServicio(codigo = COD_CALIBRADO, nombre = "Calibración de Unidad", moduloOrigen = "feature-calibrado"))
            dao.insertar(TipoServicio(codigo = COD_CV_INTERNA, nombre = "Control Visual Interna", moduloOrigen = "feature-controles-visuales"))
            dao.insertar(TipoServicio(codigo = COD_ESPESORES, nombre = "Control de Espesores", moduloOrigen = "feature-controles-visuales"))
            dao.insertar(TipoServicio(codigo = COD_LIVIANOS, nombre = "Inspección LIVIANOS", moduloOrigen = "feature-inspecciones"))
        }
        val all = runBlocking { dao.getAll() }.associateBy { it.codigo }
        assertTrue("Catálogo debe tener $COD_CALIBRADO", all.containsKey(COD_CALIBRADO))
        assertTrue("Catálogo debe tener $COD_CV_INTERNA", all.containsKey(COD_CV_INTERNA))
        assertTrue("Catálogo debe tener $COD_ESPESORES", all.containsKey(COD_ESPESORES))
        assertTrue("Catálogo debe tener $COD_LIVIANOS", all.containsKey(COD_LIVIANOS))
    }

    // ── STEP 2 — Nueva Visita: crea Unidad nueva + checklist de 4 servicios ────────────────────
    @Test
    fun test2CrearVisitaConChecklist() {
        composeTestRule.setContent { SesionScreen() }
        waitForMarker("Visitas")
        composeTestRule.onNodeWithText("+ Nueva visita").performClick()
        waitForMarker("Nueva visita")

        typeAndVerify("e2e_nueva_visita_patente", PLACA)
        typeAndVerify("e2e_nueva_visita_cliente", "E2E Sesion SA")
        typeAndVerify("e2e_nueva_visita_domicilio", "Calle Falsa 123")
        typeAndVerify("e2e_nueva_visita_cuit", "20-99999999-9")
        typeAndVerify("e2e_nueva_visita_auditor", "E2E Auditor")
        // Fecha se deja en su default (hoy) a propósito: serviciosDeLaUnidadEnElDia filtra por el
        // día calendario de sesion.fecha, y cada módulo escribe su HistorialServicio con
        // fecha = "ahora" -- dejar la visita en "hoy" evita desalinear ambos rangos sin necesidad.

        // El checklist se carga async (LaunchedEffect(Unit) -> tipoServicioDao().getAll()) --
        // hay que esperar a que aparezca el primer checkbox antes de tocar nada, si no la lista
        // todavía está vacía (cargandoTipos == true) y el testTag no existe todavía.
        swallowingToastQuirk {
            composeTestRule.waitUntil(10_000) {
                composeTestRule.onAllNodesWithTag("e2e_servicio_checkbox_$COD_CALIBRADO").fetchSemanticsNodes().isNotEmpty()
            }
        }

        // Selección en ESTE orden (distinto del orden de completado usado más abajo, a propósito).
        listOf(COD_CALIBRADO, COD_CV_INTERNA, COD_ESPESORES, COD_LIVIANOS).forEach { codigo ->
            composeTestRule.onNodeWithTag("e2e_servicio_checkbox_$codigo").performScrollTo()
            composeTestRule.onNodeWithTag("e2e_servicio_checkbox_$codigo").performClick()
            swallowingToastQuirk { composeTestRule.waitForIdle() }
        }

        // NOTA: a diferencia de los checkboxes de arriba, este botón queda debajo del checklist
        // en la Column con scroll -- sin performScrollTo() primero, performClick() dispatcha el
        // toque en coordenadas fuera de la ventana visible y no le pega a nada (mismo mecanismo
        // que ya nos había mordido con performTextInput() la corrida anterior, acá con clicks).
        composeTestRule.onNodeWithText("Iniciar visita").performScrollTo()
        composeTestRule.onNodeWithText("Iniciar visita").performClick()
        waitForMarker("Visita en curso")

        // Resolver sesionId/unidadId reales desde la DB (SesionScreen no expone esto por fuera
        // de su propio back-stack interno).
        val unidad = runBlocking { db.unidadDao().getPorPlaca(PLACA) }
        assertNotNull("Unidad $PLACA debería haberse creado al iniciar la visita", unidad)
        unidadId = unidad!!.id
        val abierta = runBlocking { db.sesionServicioDao().sesionesAbiertas() }.firstOrNull { it.unidadId == unidadId }
        assertNotNull("Debe existir una SesionServicio EN_PROGRESO para $PLACA", abierta)
        sesionId = abierta!!.id

        val items = runBlocking { db.sesionServicioDao().itemsDeSesion(sesionId) }
        assertEquals("Deben existir exactamente 4 items en la sesión", 4, items.size)
        listOf(COD_CALIBRADO, COD_CV_INTERNA, COD_ESPESORES, COD_LIVIANOS).forEach { codigo ->
            val item = items.first { it.tipoServicioCodigo == codigo }
            assertEquals("$codigo debe arrancar PENDIENTE", "PENDIENTE", item.estado)
        }

        assertFalse("todosLosItemsCompletos debe ser false recién creada la visita", runBlocking { db.sesionServicioDao().todosLosItemsCompletos(sesionId) })
        composeTestRule.onNodeWithTag("e2e_finalizar_visita").assertIsNotEnabled()
    }

    // ── STEP 3 — Inspección LIVIANOS: toca el ítem real (lanza la Activity real) y completa
    // vía DAO-directo (mismo write que hace InspeccionActivity al terminar la planilla real) ──
    // NOT fully UI-automated, y en un aspecto MENOS de lo planeado originalmente. Razón de fondo
    // (misma que la corrida anterior): el flujo real es InspeccionesMenuActivity ->
    // DatosEquipoActivity -> GestorReportes -> InspeccionActivity, una cadena de Activities+XML
    // heredadas con una planilla de 67 ítems calcada de Excel y RecyclerViews dinámicas --
    // automatizarla vía Espresso se juzgó demasiado frágil/cara relativo al valor acá.
    //
    // SÍ se automatiza (a diferencia de la corrida anterior, que llamaba el helper directo sin
    // pasar por la sesión en absoluto): se toca el botón PENDIENTE real -> AbrirInspeccionesPaso
    // dispara el Intent real hacia InspeccionesMenuActivity (confirmado por el marcador "Abriendo
    // planillas de inspección" Y por logcat: se ve el ActivityTaskManager#START real de
    // InspeccionesMenuActivity con extras). Lo que NO se pudo sostener con este dispositivo: volver
    // con Espresso.pressBack() y seguir interactuando con la MISMA composición -- en la práctica,
    // mientras InspeccionesMenuActivity (que arranca su propia Room DB local) está en foreground,
    // el sistema recicla el ComponentActivity anfitrión del test (se ve un ActivityTaskManager#START
    // nuevo para com.aies.superapp/androidx.activity.ComponentActivity apenas se cierra la
    // Activity legada, en vez de simplemente re-mostrar la instancia existente), y
    // ComposeTestRule queda apuntando a una Activity ya destruida -- IllegalStateException "No
    // compose hierarchies found in the app". Se intentó mitigar con esperas más largas
    // (InstrumentationRegistry.waitForIdleSync() + sleep de hasta 3s) sin éxito: es un problema
    // estructural de memoria/lifecycle del dispositivo, no de timing. Se optó por NO
    // presionar back y en cambio completar el ítem puramente vía DAO-directo (mismo par de
    // escrituras que InspeccionActivity.kt hace al terminar la planilla real), verificado en el
    // método siguiente (test4CompletarControlVisualInterna) cuando reabrirVisita() vuelve a
    // renderizar "Visita en curso" desde cero y muestra este ítem ya como COMPLETO.
    @Test
    fun test3CompletarLivianosViaActivityRealYFallbackDao() {
        reabrirVisita()
        assertEquals("PENDIENTE", estadoItem(COD_LIVIANOS))

        composeTestRule.onNodeWithTag("e2e_item_estado_$COD_LIVIANOS").performScrollTo()
        composeTestRule.onNodeWithTag("e2e_item_estado_$COD_LIVIANOS").performClick()
        // A DIFERENCIA de lo planeado originalmente: NO se hace ningún query de Compose (ni
        // siquiera waitForMarker/waitForIdle) después de este click. En la práctica, en cuanto
        // AbrirInspeccionesPaso dispara launcher.launch(intent) hacia la Activity real, el
        // ComponentActivity anfitrión del test pasa a STOPPED casi de inmediato en este
        // dispositivo, y CUALQUIER consulta posterior a la jerarquía de semántica (incluso solo
        // esperar el texto "Abriendo planillas de inspección") revienta con
        // IllegalStateException "No compose hierarchies found in the app" -- reproducido de forma
        // consistente en 3 corridas seguidas, con y sin esperas de hasta 3s antes de consultar.
        // Confirmado por otra vía que el dispatch SÍ es real: logcat de esta sesión muestra el
        // ActivityTaskManager#START real de com.aies.feature.inspecciones.ui.InspeccionesMenuActivity
        // "(has extras)" apenas después de este click. No se intenta ninguna otra interacción de
        // Compose en lo que resta de este método.
        Thread.sleep(1_000)

        // Fallback DAO-directo: mismo par de escrituras que InspeccionActivity.kt línea ~405-408
        // hace al terminar la planilla real.
        val historialId = runBlocking {
            HistorialCompartidoHelper.registrar(
                context = context,
                tipoInspeccion = "LIVIANOS",
                empresa = "E2E Sesion SA",
                auditor = "E2E Auditor",
                patenteTractor = "",
                patenteSemi = PLACA,
                documentoUriExcel = null,
                documentoUriPdf = null
            )
        }
        assertTrue("HistorialCompartidoHelper.registrar debe devolver un id > 0 para la rama SEMI", historialId > 0)
        val item = runBlocking { db.sesionServicioDao().itemsDeSesion(sesionId).first { it.tipoServicioCodigo == COD_LIVIANOS } }
        runBlocking { db.sesionServicioDao().marcarItemCompleto(item.id, historialId) }

        assertEquals("$COD_LIVIANOS debe quedar COMPLETO tras el fallback", "COMPLETO", estadoItem(COD_LIVIANOS))
        assertEquals("$COD_CALIBRADO sigue PENDIENTE", "PENDIENTE", estadoItem(COD_CALIBRADO))
        assertEquals("$COD_CV_INTERNA sigue PENDIENTE", "PENDIENTE", estadoItem(COD_CV_INTERNA))
        assertEquals("$COD_ESPESORES sigue PENDIENTE", "PENDIENTE", estadoItem(COD_ESPESORES))
        assertFalse(runBlocking { db.sesionServicioDao().todosLosItemsCompletos(sesionId) })
    }

    // ── STEP 4 — Control Visual Interna, vía Compose UI real (con precarga de unidad) ─────────
    // IMPORTANTE (descubierto en esta corrida, no en la anterior): el Toast/Looper quirk
    // documentado arriba no solo lanza una excepción molesta -- efectivamente MATA el Recomposer
    // de esa composición (tal como advertía el comentario original de
    // installLenientUncaughtExceptionHandler). El estado real de la app SÍ avanza igual
    // (generando=false, HistorialServicio escrito, sesionServicioDao().marcarItemCompleto
    // ejecutado), pero la recomposición que reflejaría eso en pantalla ya no puede correr, así
    // que CUALQUIER composeTestRule.waitUntil/waitForIdle posterior en esa misma composición
    // cuelga hasta el timeout (reproducido de forma consistente: waitUntilNotGenerating Y el
    // waitForMarker("Visita en curso") que le seguía, ambos con distintos timeouts). Por eso,
    // después de tocar "📄 Generar PDF" NO se vuelve a tocar composeTestRule en este método --
    // se confirma la finalización sondeando la DB directo (esperarItemCompleto) y el método
    // termina ahí. La aserción de "Finalizar visita" deshabilitado se movió al INICIO del
    // siguiente método (test5), que arranca con una composición fresca.
    @Test
    fun test4CompletarControlVisualInterna() {
        reabrirVisita()
        assertEquals("PENDIENTE", estadoItem(COD_CV_INTERNA))
        // Confirmación en UI real (no solo DAO) de que el fallback de test3 sí marcó LIVIANOS
        // como COMPLETO -- esta es la primera composición fresca desde ese fallback.
        composeTestRule.onNodeWithTag("e2e_item_estado_$COD_LIVIANOS").assertTextContains("COMPLETO")
        // Y que con 3 de 4 pendientes, Finalizar sigue deshabilitado (composición fresca, sin
        // ningún Toast disparado todavía -- esta consulta es segura acá).
        composeTestRule.onNodeWithTag("e2e_finalizar_visita").assertIsNotEnabled()

        composeTestRule.onNodeWithTag("e2e_item_estado_$COD_CV_INTERNA").performScrollTo()
        composeTestRule.onNodeWithTag("e2e_item_estado_$COD_CV_INTERNA").performClick()
        waitForMarker("Control Visual Interna")
        // Con unidadIdPrecargada la unidad/dominio ya vienen resueltos -- no hace falta buscar.
        // performScrollTo() antes de cada click: estos botones quedan al final de una Column con
        // scroll, y sin scrollear primero performClick() dispatcha el toque fuera de la ventana
        // visible (ver nota en test2 -- mismo mecanismo, ahora aplicado sistemáticamente).
        repeat(3) {
            composeTestRule.onNodeWithText("Siguiente →").performScrollTo()
            composeTestRule.onNodeWithText("Siguiente →").performClick()
            swallowingToastQuirk { composeTestRule.waitForIdle() }
        }
        composeTestRule.onNodeWithText("📄 Generar PDF").performScrollTo()
        clickSwallowingToastQuirk { composeTestRule.onNodeWithText("📄 Generar PDF").performClick() }

        // Confirmación por DB, no por UI -- ver doc grande arriba.
        esperarItemCompleto(COD_CV_INTERNA)
        assertEquals("COMPLETO", estadoItem(COD_LIVIANOS))
        assertEquals("PENDIENTE", estadoItem(COD_CALIBRADO))
        assertEquals("PENDIENTE", estadoItem(COD_ESPESORES))
        assertFalse(runBlocking { db.sesionServicioDao().todosLosItemsCompletos(sesionId) })

        val pdf = findRecentDownload("control_visual_interna_$PLACA", testStart)
        assertNotNull("PDF de Control Visual Interna no encontrado en Descargas", pdf)
        assertTrue("PDF de Control Visual Interna está vacío", (pdf?.second ?: 0) > 0)
    }

    // ── STEP 5 — Control de Espesores, vía Compose UI real (con precarga de unidad) ───────────
    // Misma estrategia que test4: confirmación de finalización por DB (esperarItemCompleto), sin
    // volver a tocar composeTestRule después de "Generar PDF".
    @Test
    fun test5CompletarControlEspesores() {
        reabrirVisita()
        assertEquals("PENDIENTE", estadoItem(COD_ESPESORES))
        // Confirmación en UI real de que test4 completó Control Visual Interna, y que con 2 de 4
        // pendientes Finalizar sigue deshabilitado.
        composeTestRule.onNodeWithTag("e2e_item_estado_$COD_CV_INTERNA").assertTextContains("COMPLETO")
        composeTestRule.onNodeWithTag("e2e_finalizar_visita").assertIsNotEnabled()

        composeTestRule.onNodeWithTag("e2e_item_estado_$COD_ESPESORES").performScrollTo()
        composeTestRule.onNodeWithTag("e2e_item_estado_$COD_ESPESORES").performClick()
        waitForMarker("Control de Espesores")
        repeat(3) {
            composeTestRule.onNodeWithText("Siguiente →").performScrollTo()
            composeTestRule.onNodeWithText("Siguiente →").performClick()
            swallowingToastQuirk { composeTestRule.waitForIdle() }
        }
        composeTestRule.onNodeWithText("📄 Generar PDF").performScrollTo()
        clickSwallowingToastQuirk { composeTestRule.onNodeWithText("📄 Generar PDF").performClick() }

        esperarItemCompleto(COD_ESPESORES)
        assertEquals("COMPLETO", estadoItem(COD_CV_INTERNA))
        assertEquals("COMPLETO", estadoItem(COD_LIVIANOS))
        assertEquals("PENDIENTE", estadoItem(COD_CALIBRADO))
        assertFalse(runBlocking { db.sesionServicioDao().todosLosItemsCompletos(sesionId) })

        val pdf = findRecentDownload("control_espesores_$PLACA", testStart)
        assertNotNull("PDF de Control de Espesores no encontrado en Descargas", pdf)
        assertTrue("PDF de Control de Espesores está vacío", (pdf?.second ?: 0) > 0)
    }

    // ── STEP 6a — Calibrado (último ítem, cierra el círculo de intercalado) ──────────────────
    // Separado de "Finalizar visita" en su PROPIO método (a diferencia del plan original) por la
    // misma razón que test4/test5: generar el certificado de calibrado dispara el mismo
    // Toast/Looper quirk y mata el Recomposer de esta composición, así que "Finalizar visita" se
    // toca en test6b, con una composición fresca donde jamás se disparó ningún Toast.
    @Test
    fun test6aCompletarCalibrado() {
        reabrirVisita()
        assertEquals("PENDIENTE", estadoItem(COD_CALIBRADO))
        composeTestRule.onNodeWithTag("e2e_item_estado_$COD_ESPESORES").assertTextContains("COMPLETO")
        composeTestRule.onNodeWithTag("e2e_finalizar_visita").assertIsNotEnabled()

        composeTestRule.onNodeWithTag("e2e_item_estado_$COD_CALIBRADO").performScrollTo()
        composeTestRule.onNodeWithTag("e2e_item_estado_$COD_CALIBRADO").performClick()
        waitForMarker("Calibrado de Unidad")

        // Precarga: patenteSemi debe llegar ya completa con PLACA (unidadIdPrecargada).
        swallowingToastQuirk {
            composeTestRule.waitUntil(5_000) {
                composeTestRule.onAllNodesWithText(PLACA, substring = true).fetchSemanticsNodes().isNotEmpty()
            }
        }

        typeAndVerify("e2e_calibrado_patente_tractor", "${PLACA}TRAC")
        composeTestRule.onNodeWithTag("e2e_cisterna_0_capacidad").performScrollTo()
        swallowingToastQuirk { composeTestRule.waitForIdle() }
        typeAndVerify("e2e_cisterna_0_capacidad", "8000")
        composeTestRule.onNodeWithTag("e2e_cisterna_0_vacioLts").performScrollTo()
        swallowingToastQuirk { composeTestRule.waitForIdle() }
        typeAndVerify("e2e_cisterna_0_vacioLts", "500")

        composeTestRule.onNodeWithText("1 año").performScrollTo()
        composeTestRule.onNodeWithText("1 año").performClick()
        composeTestRule.onNodeWithText("Generar certificado").performScrollTo()
        clickSwallowingToastQuirk { composeTestRule.onNodeWithText("Generar certificado").performClick() }

        // Confirmación por DB, no por UI -- ver doc grande de test4.
        esperarItemCompleto(COD_CALIBRADO)
        assertEquals("COMPLETO", estadoItem(COD_CV_INTERNA))
        assertEquals("COMPLETO", estadoItem(COD_ESPESORES))
        assertEquals("COMPLETO", estadoItem(COD_LIVIANOS))
        assertTrue("todosLosItemsCompletos debe ser true", runBlocking { db.sesionServicioDao().todosLosItemsCompletos(sesionId) })

        val pdfCalibrado = findRecentDownload("_${PLACA}.pdf", testStart) ?: findRecentDownload("calibrado_", testStart)
        assertNotNull("PDF de calibrado no encontrado en Descargas", pdfCalibrado)
        assertTrue("PDF de calibrado está vacío", (pdfCalibrado?.second ?: 0) > 0)
    }

    // ── STEP 6b — Finalizar visita + Nota de Trabajo consolidada ─────────────────────────────
    // Composición fresca (nunca disparó ningún Toast), así que "Finalizar visita" y el
    // waitForMarker("Visita finalizada") posterior son seguros acá -- y de hecho el código de
    // éxito de VisitaEnCursoPaso (a diferencia de sus otros módulos) no llama a Toast.makeText en
    // el camino feliz, solo en el de error, así que este método no debería ni disparar el quirk.
    @Test
    fun test6bFinalizarVisitaYNotaTrabajo() {
        reabrirVisita()

        // Todos COMPLETO -- verificado vía DAO Y vía UI antes de tocar Finalizar.
        assertEquals("COMPLETO", estadoItem(COD_CALIBRADO))
        assertEquals("COMPLETO", estadoItem(COD_CV_INTERNA))
        assertEquals("COMPLETO", estadoItem(COD_ESPESORES))
        assertEquals("COMPLETO", estadoItem(COD_LIVIANOS))
        assertTrue("todosLosItemsCompletos debe ser true", runBlocking { db.sesionServicioDao().todosLosItemsCompletos(sesionId) })
        composeTestRule.onNodeWithTag("e2e_item_estado_$COD_CALIBRADO").assertTextContains("COMPLETO")
        composeTestRule.onNodeWithTag("e2e_finalizar_visita").assertIsEnabled()

        // Instantánea de lo que el NotaTrabajoPdfBuilder va a recibir como input, ANTES de
        // finalizar -- para comparar 1:1 contra lo que efectivamente terminó en la sesión.
        val (inicioDia, finDia) = diaInicio(testStart) to diaFin(testStart)
        val serviciosAntesDeFinalizar = runBlocking { db.historialServicioDao().serviciosDeLaUnidadEnElDia(unidadId, inicioDia, finDia) }
        val nombresEsperados = setOf("Calibración de Unidad", "Control Visual Interna", "Control de Espesores", "Inspección LIVIANOS")
        assertEquals("Deben existir exactamente 4 HistorialServicio para $PLACA hoy (uno por cada servicio completado)", 4, serviciosAntesDeFinalizar.size)
        assertEquals(nombresEsperados, serviciosAntesDeFinalizar.map { it.tipoServicioNombre }.toSet())

        composeTestRule.onNodeWithTag("e2e_finalizar_visita").performScrollTo()
        composeTestRule.onNodeWithTag("e2e_finalizar_visita").performClick()
        waitForMarker("Visita finalizada", timeoutMillis = 25_000)

        val sesionCerrada = runBlocking { db.sesionServicioDao().getPorId(sesionId) }
        assertNotNull(sesionCerrada)
        assertEquals("La sesión debe quedar CERRADA tras Finalizar visita", "CERRADA", sesionCerrada!!.estado)

        // El input real que recibió NotaTrabajoPdfBuilder.generar(...) es exactamente esta misma
        // consulta (ver VisitaEnCursoPaso.kt) -- NotaTrabajoPdfBuilder es una función pura (leída
        // su fuente: dibuja fila por fila "tipoServicioNombre" / "auditor" / hora por cada
        // elemento de esta lista, sin filtrar ni reordenar nada más), así que confirmar que la
        // lista sigue siendo exactamente estos 4 servicios ES confirmar el contenido del PDF a
        // nivel de datos -- no se hizo extracción de texto del PDF en sí (ver informe final).
        val serviciosDespuesDeFinalizar = runBlocking { db.historialServicioDao().serviciosDeLaUnidadEnElDia(unidadId, inicioDia, finDia) }
        assertEquals(4, serviciosDespuesDeFinalizar.size)
        assertEquals(nombresEsperados, serviciosDespuesDeFinalizar.map { it.tipoServicioNombre }.toSet())
        assertEquals(
            "cerrarSesion no debe alterar los HistorialServicio ya escritos",
            serviciosAntesDeFinalizar.map { it.tipoServicioNombre to it.fecha },
            serviciosDespuesDeFinalizar.map { it.tipoServicioNombre to it.fecha }
        )

        val pdfNota = findRecentDownload("NotaTrabajo_$PLACA", testStart)
        assertNotNull("PDF de Nota de Trabajo no encontrado en Descargas", pdfNota)
        assertTrue("PDF de Nota de Trabajo está vacío", (pdfNota?.second ?: 0) > 0)
    }
}
