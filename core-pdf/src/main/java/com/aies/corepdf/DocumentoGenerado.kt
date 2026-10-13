package com.aies.corepdf

/** Resultado de generar un documento: dónde quedó guardado y qué tipo es. */
data class DocumentoGenerado(
    val path: String,
    val tipo: TipoDocumento
)

enum class TipoDocumento { PDF, EXCEL }
