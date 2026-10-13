package com.aies.feature.certificados.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Exportación a "Excel" tal cual la app original: en realidad genera un .csv separado por
 * punto y coma (para que Excel en español acomode las columnas automáticamente), no un
 * .xlsx real — se replica el mismo formato, no se lo "mejora" a XLSX.
 */
object CsvExporter {
    private val fmt = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())

    fun generarCsv(registros: List<CertificadoRegistro>): String {
        val lineas = mutableListOf("Nro Correlativo;Fecha Emision;Transportista;Patente Chasis;Patente Acoplado;Sistema Tapas")
        for (r in registros) {
            lineas.add(
                "${r.nroCorrelativo};${fmt.format(Date(r.fechaEmisionTimestamp))};${r.transportista};${r.patenteChasis};${r.patenteAcoplado};${r.sistemaTapas}"
            )
        }
        return lineas.joinToString("\n")
    }
}
