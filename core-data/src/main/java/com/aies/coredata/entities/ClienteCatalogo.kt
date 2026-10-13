package com.aies.coredata.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Catálogo de nombres de cliente/transportista ya usados en alguna visita, mismo criterio que
 * [AuditorCatalogo] — elegir de una lista en vez de re-tipear, se completa solo. El CUIT queda
 * asociado al nombre: elegir un cliente de la lista precarga su CUIT automáticamente, y cada
 * visita guardada actualiza el CUIT recordado (por si cambió o se corrigió).
 */
@Entity(tableName = "clientes_catalogo")
data class ClienteCatalogo(
    @PrimaryKey
    val nombre: String,
    val cuit: String = ""
)
