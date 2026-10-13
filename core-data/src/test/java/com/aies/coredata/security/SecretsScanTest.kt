package com.aies.coredata.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.util.Base64

/**
 * Test de regresión de seguridad — ver docs/security/AUDITORIA-2026-09-19.md, hallazgo
 * SEC-001 ("service account con auto-login; credenciales embebidas en BuildConfig").
 *
 * No decreta el diseño actual como error (el propio repo lo documenta como aceptable
 * temporalmente para uso interno, `supabase/schema.sql` sección 7), pero SÍ debe fallar si:
 *  1. Cualquier JWT encontrado en el proyecto (local.properties, código fuente) tiene
 *     `role=service_role` -- eso saltearía todo RLS y es un hallazgo crítico nuevo.
 *  2. Aparece un literal de JWT (`eyJ...`) hardcodeado directamente en un archivo .kt fuente
 *     (en vez de vivir solo en local.properties -> BuildConfig, que es el único canal
 *     documentado y gitignoreado) -- eso significaría un secreto commiteable por accidente.
 */
class SecretsScanTest {

    private fun raizProyecto(): File {
        val candidatos = listOf(File("."), File(".."), File("../.."))
        return candidatos.firstOrNull { File(it, "settings.gradle.kts").exists() }
            ?: fail("No se pudo ubicar la raíz del proyecto (settings.gradle.kts) desde ${File(".").absolutePath}").let { File(".") }
    }

    private fun decodificarPayloadJwt(jwt: String): String? {
        val partes = jwt.split(".")
        if (partes.size < 2) return null
        return try {
            val payload = partes[1]
            val normalizado = payload.replace('-', '+').replace('_', '/')
            val relleno = normalizado + "=".repeat((4 - normalizado.length % 4) % 4)
            String(Base64.getDecoder().decode(relleno))
        } catch (e: Exception) {
            null
        }
    }

    @Test
    fun `ningun JWT del proyecto tiene role service_role`() {
        val raiz = raizProyecto()
        val jwtRegex = Regex("eyJ[a-zA-Z0-9_-]+\\.[a-zA-Z0-9_-]+\\.[a-zA-Z0-9_-]+")

        val archivosARevisar = mutableListOf<File>()
        File(raiz, "local.properties").takeIf { it.exists() }?.let { archivosARevisar += it }

        val encontrados = mutableListOf<Pair<String, String>>() // (archivo, role)
        for (archivo in archivosARevisar) {
            val texto = archivo.readText()
            for (match in jwtRegex.findAll(texto)) {
                val payload = decodificarPayloadJwt(match.value) ?: continue
                val role = Regex("\"role\"\\s*:\\s*\"([^\"]+)\"").find(payload)?.groupValues?.get(1)
                if (role != null) encontrados += archivo.path to role
            }
        }

        val conServiceRole = encontrados.filter { it.second == "service_role" }
        assertTrue(
            "Se encontró un JWT con role=service_role en: ${conServiceRole.map { it.first }}. " +
                "Esto es CRÍTICO (salta toda RLS) -- rotar la key en Supabase Dashboard > Settings > " +
                "API inmediatamente y reemplazar por la anon key.",
            conServiceRole.isEmpty()
        )
    }

    @Test
    fun `no hay JWT literal hardcodeado en archivos kt fuente`() {
        val raiz = raizProyecto()
        val jwtRegex = Regex("eyJ[a-zA-Z0-9_-]+\\.[a-zA-Z0-9_-]+\\.[a-zA-Z0-9_-]+")

        val archivosKt = raiz.walkTopDown()
            .onEnter { dir -> dir.name != "build" && dir.name != ".git" && dir.name != ".gradle" }
            .filter { it.isFile && it.extension == "kt" }

        val conJwt = archivosKt
            .mapNotNull { f -> if (jwtRegex.containsMatchIn(f.readText())) f.path else null }
            .toList()

        assertTrue(
            "JWT hardcodeado directamente en código fuente .kt (debe venir de BuildConfig, " +
                "cargado desde local.properties, que está gitignoreado): $conJwt",
            conJwt.isEmpty()
        )
    }

    @Test
    fun `local properties sigue gitignoreado`() {
        val raiz = raizProyecto()
        val gitignore = File(raiz, ".gitignore")
        if (!gitignore.exists()) return // sin repo git en este entorno -- ver limitaciones del informe
        val contenido = gitignore.readText()
        assertFalse(
            "local.properties debe seguir en .gitignore (contiene SUPABASE_SERVICE_PASSWORD en " +
                "texto plano).",
            contenido.lines().none { it.trim() == "local.properties" || it.trim() == "/local.properties" }
        )
    }
}
