package com.aies.coredata.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Catálogo de nombres de auditor ya usados en alguna visita, para elegir de una lista en vez
 * de re-tipear (y evitar variantes del mismo nombre que después no coinciden con la firma en
 * los reportes). Se completa solo: cualquier nombre nuevo escrito en Nueva Visita queda
 * guardado acá para las próximas visitas.
 */
@Entity(tableName = "auditores_catalogo")
data class AuditorCatalogo(
    @PrimaryKey
    val nombre: String
)
