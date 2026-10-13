package com.aies.superapp.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Test de regresión de seguridad — ver docs/security/AUDITORIA-2026-09-19.md (hallazgo
 * SEC-005, "allowBackup=true con datos sensibles sin cifrar").
 *
 * Parsea el AndroidManifest.xml FUENTE de :app como texto (no usa Robolectric ni el manifest
 * mergeado) para no depender de un build completo. Si algún día se agrega un componente
 * `exported="true"` nuevo, este test lo va a marcar como error hasta que se agregue
 * explícitamente a la lista blanca de abajo -- así un exported accidental (p.ej. copiar/pegar
 * una Activity portada del código legacy de Inspecciones sin sacarle el atributo) no pasa
 * desapercibido.
 *
 * SEC-005 corregido: `allowBackup` pasó a `false` en AndroidManifest.xml. Este test ahora
 * protege contra una regresión accidental a `true`.
 */
class ManifestSecurityTest {

    /** Componentes que SÍ pueden declarar android:exported="true", con su justificación. */
    private val exportedComponentesPermitidos = setOf(
        // Única Activity LAUNCHER de la app: un exported="true" acá es obligatorio para que el
        // sistema pueda lanzarla desde el ícono del launcher.
        ".MainActivity"
    )

    private val manifestAppPath = "src/main/AndroidManifest.xml"

    /** SEC-005 corregido -- ver comentario de clase. */
    private val EXPECTED_ALLOW_BACKUP = false

    private fun leerManifestApp(): String {
        val candidatos = listOf(
            File(manifestAppPath),
            File("app/$manifestAppPath"),
            File("../app/$manifestAppPath")
        )
        val archivo = candidatos.firstOrNull { it.exists() }
            ?: fail(
                "No se encontró AndroidManifest.xml de :app. Probado: " +
                    candidatos.joinToString { it.path }
            ).let { null }
        return archivo!!.readText()
    }

    @Test
    fun `debuggable no debe estar forzado a true en el manifest fuente`() {
        val manifest = leerManifestApp()
        assertFalse(
            "android:debuggable=\"true\" no debe quedar hardcodeado en el manifest " +
                "(debe venir solo del build type debug, nunca de un valor fijo en XML).",
            Regex("android:debuggable\\s*=\\s*\"true\"").containsMatchIn(manifest)
        )
    }

    @Test
    fun `usesCleartextTraffic no debe estar habilitado`() {
        val manifest = leerManifestApp()
        assertFalse(
            "android:usesCleartextTraffic=\"true\" expondría el tráfico a Supabase/SMTP a " +
                "downgrade a HTTP plano.",
            Regex("android:usesCleartextTraffic\\s*=\\s*\"true\"").containsMatchIn(manifest)
        )
    }

    @Test
    fun `solo los componentes en la lista blanca pueden ser exported`() {
        val manifest = leerManifestApp()
        // Busca cada bloque <activity .../> o <activity ...>...</activity> con exported="true"
        // y extrae su android:name.
        val regexComponente = Regex(
            "<(activity|service|receiver|provider)\\b[^>]*android:name=\"([^\"]+)\"[^>]*?/?>",
            RegexOption.DOT_MATCHES_ALL
        )
        val exportedEncontrados = regexComponente.findAll(manifest)
            .filter { match -> match.value.contains("android:exported=\"true\"") }
            .map { it.groupValues[2] }
            .toList()

        val noAutorizados = exportedEncontrados.filterNot { it in exportedComponentesPermitidos }
        assertTrue(
            "Componentes exported=\"true\" no autorizados (agregar a la lista blanca del test " +
                "si es intencional y documentar el motivo): $noAutorizados",
            noAutorizados.isEmpty()
        )
    }

    @Test
    fun `allowBackup coincide con el estado documentado en el informe de auditoria`() {
        val manifest = leerManifestApp()
        val tieneAllowBackupTrue = Regex("android:allowBackup\\s*=\\s*\"true\"").containsMatchIn(manifest)
        assertTrue(
            "El estado de allowBackup cambió respecto de lo documentado en " +
                "docs/security/AUDITORIA-2026-09-19.md (hallazgo SEC-005). Si se corrigió a " +
                "false, actualizar EXPECTED_ALLOW_BACKUP=false en este test y cerrar el hallazgo " +
                "en el próximo informe.",
            tieneAllowBackupTrue == EXPECTED_ALLOW_BACKUP
        )
    }
}
