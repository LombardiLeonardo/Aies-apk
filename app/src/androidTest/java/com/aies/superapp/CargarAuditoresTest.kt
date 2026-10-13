package com.aies.superapp

/**
 * ============================================================================================
 * THROWAWAY, ONE-TIME -- carga inicial de los 5 auditores pedidos por el usuario en el catálogo
 * compartido (Supabase, ver ClienteCatalogoDao/AuditorCatalogoDao en core-data, recién
 * conectado). Corre en el proceso de la app instalada, usando la sesión ya logueada de
 * SupabaseClientProvider -- no maneja ninguna credencial acá.
 *
 * Manual invocation:
 *   adb shell am instrument -w -e class com.aies.superapp.CargarAuditoresTest com.aies.superapp.test/androidx.test.runner.AndroidJUnitRunner
 * (NO usar ./gradlew connectedDebugAndroidTest -- desinstala la app entera al terminar).
 * ============================================================================================
 */

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aies.coredata.AppDatabase
import com.aies.coredata.entities.AuditorCatalogo
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CargarAuditoresTest {

    @Test
    fun cargarAuditores(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = AppDatabase.getInstance(context)

        val auditores = listOf(
            "Leonardo Lombardi",
            "Agustin Moran",
            "Pablo Gonzalez",
            "Gabriel Scabone",
            "Matias Ramirez"
        )

        auditores.forEach { nombre ->
            db.auditorCatalogoDao().insertar(AuditorCatalogo(nombre))
        }
    }
}
