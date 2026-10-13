package com.aies.coredata.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Catálogo de servicios que la super app puede prestar.
 * Sumar un servicio nuevo = agregar una fila acá, no tocar el esquema.
 */
@Entity(tableName = "tipos_servicio")
data class TipoServicio(
    @PrimaryKey
    val codigo: String, // id legible y estable, ej. "cert_tapa_hombre"

    val nombre: String,

    val moduloOrigen: String // qué feature module lo genera
)
