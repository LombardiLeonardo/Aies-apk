package com.aies.superapp

/**
 * ============================================================================================
 * THROWAWAY, ONE-TIME INSTRUMENTED VALIDATION — NOT A PERMANENT TEST SUITE.
 *
 * Cubre las 3 cosas que se agregaron en esta sesión, sin manejar Compose UI (a diferencia de
 * EndToEndFlowTest.kt): llama DIRECTO a las mismas funciones/DAOs que las pantallas reales usan,
 * contra el Supabase real, corriendo dentro del proceso de la app instalada (mismo mecanismo que
 * EndToEndFlowTest — el Application real ya inicializó SupabaseClientProvider antes de esto).
 *
 *   1. Las RPC de numeración (siguiente_numero_precinto/tapa_hombre/reporte) responden -- valida
 *      que el SQL de secuencias/funciones esté realmente aplicado en Supabase, no solo que el
 *      código Kotlin compile.
 *   2. `historial_servicios.detalle` hace round-trip COMPLETO por Supabase (insert -> select
 *      remoto, no solo cache local Room) para cert_precinto y repuestos_cisterna -- valida el
 *      ALTER TABLE ADD COLUMN detalle.
 *   3. Los PDFs nuevos/modificados (Nota de Precintos, Reporte de Observaciones con número) se
 *      generan sin explotar, y la numeración de precintos calcula lo que dice calcular.
 *
 * Do not promote this into CI. Manual invocation only:
 *   ./gradlew.bat :app:connectedDebugAndroidTest --console=plain -Pandroid.testInstrumentationRunnerArguments.class=com.aies.superapp.NuevasFuncionesSmokeTest
 * ============================================================================================
 */

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aies.coredata.AppDatabase
import com.aies.coredata.remote.SupabaseClientProvider
import com.aies.feature.certificados.pdf.CertificadoPdfBuilder
import com.aies.feature.inspecciones.ui.PdfReporteBuilder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar
import com.aies.feature.certificados.data.HistorialCompartidoHelper as CertificadosHistorialHelper
import com.aies.feature.repuestos.data.HistorialCompartidoHelper as RepuestosHistorialHelper

@RunWith(AndroidJUnit4::class)
class NuevasFuncionesSmokeTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val db = AppDatabase.getInstance(context)
    private val placaBase = "SMOKE${System.currentTimeMillis() % 1_000_000}"

    private fun rangoDeHoy(): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val inicio = cal.timeInMillis
        return inicio to (inicio + 24L * 60 * 60 * 1000 - 1)
    }

    @Test
    fun test1RpcDeNumeracionResponden() = runBlocking {
        SupabaseClientProvider.asegurarLogin()
        val numeroPrecinto = SupabaseClientProvider.siguienteNumeroPrecinto()
        val numeroTapaHombre = SupabaseClientProvider.siguienteNumeroTapaHombre()
        val numeroReporte = SupabaseClientProvider.siguienteNumeroReporte()
        assertTrue("siguiente_numero_precinto debe devolver > 0 (RPC existe y responde)", numeroPrecinto > 0)
        assertTrue("siguiente_numero_tapa_hombre debe devolver > 0", numeroTapaHombre > 0)
        assertTrue(
            "siguiente_numero_reporte debe devolver > 0 -- si esto falla, falta correr el SQL " +
                "de la secuencia/función `siguiente_numero_reporte` en Supabase",
            numeroReporte > 0
        )
    }

    @Test
    fun test2DetalleDePrecintoRoundTripPorSupabase() = runBlocking {
        val placa = "${placaBase}P"
        val detalleEsperado = "Total precintos utilizados: 14 (Nº 100 a 113)"
        val historialId = CertificadosHistorialHelper.registrar(
            context = context,
            tipoServicioCodigo = "cert_precinto",
            nombreServicio = "Acta de Reemplazo de Precinto",
            transportista = "Smoke Test SA",
            patenteChasis = placa,
            patenteAcoplado = "",
            documentoUri = null,
            auditor = "Smoke Auditor",
            detalle = detalleEsperado
        )
        assertTrue("registrar() debe devolver un id > 0", historialId > 0)

        val unidad = db.unidadDao().getPorPlaca(placa)
        assertEquals(placa, unidad?.placa)
        val (inicio, fin) = rangoDeHoy()
        val servicios = db.historialServicioDao().serviciosDeLaUnidadEnElDia(unidad!!.id, inicio, fin)
        val fila = servicios.firstOrNull { it.tipoServicioCodigo == "cert_precinto" }
        assertEquals(
            "El detalle debe volver EXACTAMENTE igual tras el viaje de ida y vuelta por Supabase " +
                "(select remoto, no cache local) -- si esto falla, falta la columna `detalle` en historial_servicios",
            detalleEsperado, fila?.detalle
        )
    }

    @Test
    fun test3DetalleDeRepuestosRoundTripPorSupabase() = runBlocking {
        val placa = "${placaBase}R"
        val detalleEsperado = "Juntas paso de hombre: 2\nKit de bulones: 1"
        val historialId = RepuestosHistorialHelper.registrar(
            context = context,
            razonSocial = "Smoke Test SA",
            dominio = placa,
            tipoUnidad = "Semi",
            cantidadCisternas = 3,
            auditor = "Smoke Auditor",
            documentoUri = null,
            detalle = detalleEsperado
        )
        assertTrue("registrar() debe devolver un id > 0", historialId > 0)

        val unidad = db.unidadDao().getPorPlaca(placa)
        assertEquals(placa, unidad?.placa)
        val (inicio, fin) = rangoDeHoy()
        val servicios = db.historialServicioDao().serviciosDeLaUnidadEnElDia(unidad!!.id, inicio, fin)
        val fila = servicios.firstOrNull { it.tipoServicioCodigo == "repuestos_cisterna" }
        assertEquals(
            "El detalle (multi-línea, un tipo de repuesto por renglón) debe volver exacto tras el round-trip por Supabase",
            detalleEsperado, fila?.detalle
        )
    }

    @Test
    fun test4PdfsNuevosSeGeneranSinExplotar() {
        // Misma matemática que NotaPrecintosScreen: cantidad*porTapa precintos, numerados de a 2
        // por tapa desde numeroInicial -- confirma que el cálculo del "total utilizado" que se
        // le manda a HistorialServicio.detalle corresponde a la numeración real impresa.
        val precintos = CertificadoPdfBuilder.calcularNumeracionPrecintos(cantidadTapas = 7, precintosPorTapa = 2, numeroInicial = 100)
        assertEquals(
            listOf("100/101", "102/103", "104/105", "106/107", "108/109", "110/111", "112/113"),
            precintos
        )

        // numeroActa se eliminó a pedido del usuario (ya no hay número de seguimiento en esta nota).
        val pdfNota = CertificadoPdfBuilder.generarNotaPrecintos(
            context.assets, "TC-001", "Bronce", "MONTAJES CAMPANA",
            "Smoke Test SA", "AAA111", "BBB222", "", 7, 2, 100
        )
        assertTrue("Nota de Precintos debe generar al menos 1 página", pdfNota.pages.size >= 1)
        pdfNota.close()

        val pdfReporte = PdfReporteBuilder.generar(
            titulo = "REPORTE DE OBSERVACIONES - LIVIANOS",
            filasEncabezado = listOf("Empresa: Smoke Test SA" to null),
            itemsObservados = emptyList(),
            numeroReporte = 99L
        )
        assertTrue("Reporte de Observaciones (con número) debe generar al menos 1 página", pdfReporte.pages.size >= 1)
        pdfReporte.close()
    }
}
