package com.aies.corepdf

/**
 * Contrato único de generación de PDF para toda la super app.
 * Cada feature module implementa acá su plantilla (certificado, informe,
 * hoja de inspección, nota de trabajo) en lugar de traer su propia
 * librería como hacían las apps viejas (QuestPDF, ReportLab).
 */
interface PdfGenerator {
    fun generar(datos: Map<String, Any?>, outputFileName: String): DocumentoGenerado
}
