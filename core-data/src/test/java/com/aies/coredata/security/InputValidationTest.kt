package com.aies.coredata.security

import com.aies.coredata.validacion.Validaciones
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test de regresión de seguridad — ver docs/security/AUDITORIA-2026-09-19.md, hallazgo SEC-007
 * ("sin validación de formato de patente/CUIT en el código").
 */
class InputValidationTest {

    @Test
    fun `patentes validas -- formato viejo y Mercosur`() {
        listOf("AAA123", "aaa123", "AG 266 IC".replace(" ", ""), "AB123CD", "ab123cd").forEach {
            assertTrue("'$it' debería ser una patente válida", Validaciones.esPatenteValida(it))
        }
    }

    @Test
    fun `patentes invalidas`() {
        listOf("", "123", "AAAA123", "AA1234A", "AAA12", "AAA1234", "../../etc/passwd", "AAA-123").forEach {
            assertFalse("'$it' NO debería ser una patente válida", Validaciones.esPatenteValida(it))
        }
    }

    @Test
    fun `cuit valido con digito verificador correcto`() {
        // 30-70297883-8 -- CUIT real visto en la prueba de RLS de la auditoría (SEC-002), dígito
        // verificador verificado a mano con el algoritmo módulo 11.
        assertTrue(Validaciones.esCuitValido("30-70297883-8"))
        assertTrue(Validaciones.esCuitValido("30702978838"))
    }

    @Test
    fun `cuit invalido -- digito verificador incorrecto`() {
        assertFalse(Validaciones.esCuitValido("30-70297883-9"))
    }

    @Test
    fun `cuit invalido -- formato incorrecto`() {
        listOf("", "123", "30-70297883", "AB-70297883-8", "300-70297883-8").forEach {
            assertFalse("'$it' NO debería ser un CUIT válido", Validaciones.esCuitValido(it))
        }
    }
}
