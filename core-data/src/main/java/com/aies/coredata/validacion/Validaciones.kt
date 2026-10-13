package com.aies.coredata.validacion

/**
 * Validaciones de formato reutilizables entre módulos -- SEC-007 de la auditoría de seguridad
 * (docs/security/AUDITORIA-2026-09-19.md): hoy patente/CUIT llegan como String libre hasta
 * Supabase, sin ningún control de formato antes de persistir.
 *
 * A propósito quedan como funciones puras que devuelven Boolean, no excepciones: quien las use
 * decide si bloquea el guardado o solo avisa (ver el criterio distinto para CUIT vs. patente en
 * NuevaVisitaPaso de feature-sesion).
 */
object Validaciones {

    // Formato viejo (AAA123) y Mercosur (AA123AA), con o sin espacio entre letras y números.
    private val REGEX_PATENTE_VIEJA = Regex("^[A-Z]{3}\\d{3}$")
    private val REGEX_PATENTE_MERCOSUR = Regex("^[A-Z]{2}\\d{3}[A-Z]{2}$")

    fun esPatenteValida(patente: String): Boolean {
        val normalizada = patente.trim().uppercase().replace(" ", "")
        return REGEX_PATENTE_VIEJA.matches(normalizada) || REGEX_PATENTE_MERCOSUR.matches(normalizada)
    }

    /**
     * CUIT argentino: 11 dígitos (XX-XXXXXXXX-X), con dígito verificador módulo 11. Acepta con o
     * sin guiones. No es un chequeo contra AFIP (eso requeriría red) -- solo formato + checksum.
     */
    fun esCuitValido(cuit: String): Boolean {
        val digitos = cuit.trim().replace("-", "").replace(" ", "")
        if (digitos.length != 11 || digitos.any { !it.isDigit() }) return false

        val coeficientes = intArrayOf(5, 4, 3, 2, 7, 6, 5, 4, 3, 2)
        val suma = digitos.take(10).mapIndexed { i, c -> (c - '0') * coeficientes[i] }.sum()
        val resto = suma % 11
        val verificadorEsperado = when (val r = 11 - resto) {
            11 -> 0
            10 -> 9
            else -> r
        }
        return verificadorEsperado == digitos[10] - '0'
    }
}
