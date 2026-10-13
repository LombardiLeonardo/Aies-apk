package com.aies.superapp

/**
 * ============================================================================================
 * THROWAWAY, ONE-TIME -- crea filas de prueba en `controles_realizados` (Room local del
 * dispositivo, feature-inspecciones) para poder reproducir/diagnosticar el crash sin
 * diagnosticar de "Exportar a Excel" del Historial de Controles, cuando en el dispositivo no
 * hay controles reales en el rango de fechas que se está probando.
 *
 * Corre en el proceso de la app instalada (mismo mecanismo que NuevasFuncionesSmokeTest) --
 * inserta 3 filas variadas (tipos y empresas distintas, fechas en los últimos 10 días) y no
 * toca Supabase para nada: ControlRealizadoDao es Room puro, sin sync remoto.
 *
 * Manual invocation only:
 *   ./gradlew.bat :app:connectedDebugAndroidTest --console=plain -Pandroid.testInstrumentationRunnerArguments.class=com.aies.superapp.CrearControlesDePruebaTest
 * ============================================================================================
 */

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aies.feature.inspecciones.data.ControlRealizado
import com.aies.feature.inspecciones.data.InspeccionesLocalDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CrearControlesDePruebaTest {

    @Test
    fun crearControlesDePrueba(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = InspeccionesLocalDatabase.getDatabase(context, this)
        val ahora = System.currentTimeMillis()
        val unDia = 24L * 60 * 60 * 1000

        val controles = listOf(
            ControlRealizado(
                tipoInspeccion = "LIVIANOS", empresa = "PRUEBA EXCEL SA", auditor = "Auditor Prueba",
                patenteTractor = "AAA111", marcaTractor = "SCANIA", modeloTractor = "R450", anioTractor = "2020",
                combustible = "DIESEL",
                patenteSemi = "BBB222", marcaSemi = "HELVETICA", modeloSemi = "TRICISTERNA", anioSemi = "2019",
                fechaTimestamp = ahora - unDia
            ),
            ControlRealizado(
                tipoInspeccion = "PESADOS", empresa = "PRUEBA EXCEL SA", auditor = "Auditor Prueba",
                patenteTractor = "AC123CD", marcaTractor = "VOLVO", modeloTractor = "FH", anioTractor = "2018",
                combustible = "DIESEL",
                patenteSemi = "AD456EF", marcaSemi = "MONTENEGRO", modeloSemi = "BICISTERNA", anioSemi = "2017",
                fechaTimestamp = ahora - (unDia * 3)
            ),
            ControlRealizado(
                tipoInspeccion = "AXION", empresa = "OTRA EMPRESA SRL", auditor = "Auditor Prueba",
                patenteTractor = "CCC333", marcaTractor = "MERCEDES", modeloTractor = "ACTROS", anioTractor = "2021",
                combustible = "DIESEL",
                patenteSemi = "DDD444", marcaSemi = "RANDON", modeloSemi = "TRICISTERNA", anioSemi = "2020",
                fechaTimestamp = ahora - (unDia * 5)
            )
        )

        controles.forEach { db.controlRealizadoDao().insert(it) }

        android.util.Log.i("CrearControlesDePrueba", "Insertadas ${controles.size} filas de prueba en controles_realizados.")
    }
}
