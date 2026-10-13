package com.aies.coredata.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Cada fila = un servicio prestado a una Unidad en una fecha puntual.
 * La nota de trabajo diaria se arma agrupando estas filas por (unidadId, fecha).
 */
@Entity(
    tableName = "historial_servicios",
    foreignKeys = [
        ForeignKey(entity = Unidad::class, parentColumns = ["id"], childColumns = ["unidadId"]),
        ForeignKey(entity = TipoServicio::class, parentColumns = ["codigo"], childColumns = ["tipoServicioCodigo"])
    ],
    indices = [
        Index(value = ["unidadId"]),
        Index(value = ["tipoServicioCodigo"]),
        Index(value = ["unidadId", "fecha"])
    ]
)
data class HistorialServicio(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val unidadId: Long,
    val tipoServicioCodigo: String,
    val fecha: Long, // epoch millis, truncado a día
    val auditor: String,
    val documentoPath: String,
    // Fase F: URL pública en Supabase Storage del mismo documento que documentoPath referencia
    // localmente -- "" cuando todavía no se subió o la subida falló (nunca null, mismo criterio
    // que documentoPath, para no complicar la migración con nulls).
    val documentoUrl: String = "",
    // Texto libre opcional que un módulo puede completar con el detalle específico del servicio
    // (ej. feature-certificados: total de precintos utilizados; feature-repuestos: qué repuesto
    // se cambió en cada compartimiento) -- la Nota de Trabajo lo imprime debajo de la fila del
    // servicio en vez de (o además de) el detalle genérico de cisternas. "" cuando el módulo no
    // tiene nada puntual que agregar (mismo criterio "nunca null" que documentoPath/documentoUrl).
    val detalle: String = ""
)
