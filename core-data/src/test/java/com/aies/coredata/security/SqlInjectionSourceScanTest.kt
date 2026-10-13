package com.aies.coredata.security

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Test de regresión de seguridad — ver docs/security/AUDITORIA-2026-09-19.md, hallazgo
 * SEC-008 ("SQL injection local -- estado actual: no confirmado, controles como prevención").
 *
 * Auditoría 2026-09-19: todos los `execSQL(...)` encontrados en el repo (Migrations.kt,
 * InspeccionesLocalDatabase.kt) usan strings LITERALES fijos (DDL de migraciones), no
 * concatenación ni interpolación con datos de entrada del usuario. No hay `rawQuery(` ni
 * `@RawQuery` en el proyecto. Esto se documenta como "bien" en el informe (sección 5), pero
 * queda este test para que, si en el futuro alguien arma un `execSQL`/`rawQuery` con datos de
 * runtime (patente, CUIT, observaciones de texto libre), el análisis de texto lo marque en vez
 * de descubrirse recién en producción.
 *
 * Heurística: cualquier llamada a `execSQL(` o `rawQuery(` cuyo argumento use interpolación de
 * Kotlin (`$variable` / `${...}`) o el operador `+` de concatenación, en vez de un string
 * literal fijo o un `SupportSQLiteQuery` con bind args (`?`), se reporta como sospechosa.
 */
class SqlInjectionSourceScanTest {

    private fun raizProyecto(): File {
        val candidatos = listOf(File("."), File(".."), File("../.."))
        return candidatos.firstOrNull { File(it, "settings.gradle.kts").exists() }
            ?: fail("No se pudo ubicar la raíz del proyecto desde ${File(".").absolutePath}").let { File(".") }
    }

    @Test
    fun `execSQL y rawQuery no usan interpolacion o concatenacion con datos externos`() {
        val raiz = raizProyecto()
        val llamadaRegex = Regex("(execSQL|rawQuery)\\s*\\(\\s*\"\"\"?([^)]*)")

        val sospechosos = mutableListOf<String>()
        raiz.walkTopDown()
            .onEnter { dir -> dir.name != "build" && dir.name != ".git" && dir.name != ".gradle" }
            .filter { it.isFile && it.extension == "kt" }
            .forEach { archivo ->
                val texto = archivo.readText()
                for (match in llamadaRegex.findAll(texto)) {
                    val argumento = match.groupValues[2]
                    val tieneInterpolacion = Regex("\\$\\{[^}]*\\w[^}]*\\}|\\$[a-zA-Z_][a-zA-Z0-9_]*")
                        .containsMatchIn(argumento)
                    val tieneConcatenacion = argumento.contains("\" +") || argumento.contains("+ \"")
                    if (tieneInterpolacion || tieneConcatenacion) {
                        sospechosos += "${archivo.path}: ${match.value.take(120).replace('\n', ' ')}"
                    }
                }
            }

        assertTrue(
            "execSQL/rawQuery con interpolación o concatenación (posible SQL injection local): " +
                sospechosos.joinToString("\n"),
            sospechosos.isEmpty()
        )
    }
}
