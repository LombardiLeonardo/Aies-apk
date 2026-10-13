package com.aies.coredata.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Un servicio marcado dentro de una SesionServicio. [historialServicioId] queda null hasta
 * que el servicio real se completa en su feature module correspondiente y escribe su propia
 * fila en HistorialServicio — ahí se enlaza con [SesionServicioDao.marcarItemCompleto].
 */
@Entity(
    tableName = "sesion_servicio_items",
    indices = [
        Index(value = ["sesionId"]),
        Index(value = ["tipoServicioCodigo"]),
        Index(value = ["historialServicioId"])
    ]
)
data class SesionServicioItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val sesionId: Long,
    val tipoServicioCodigo: String,

    val estado: String, // "PENDIENTE" | "COMPLETO"

    val historialServicioId: Long?
)
