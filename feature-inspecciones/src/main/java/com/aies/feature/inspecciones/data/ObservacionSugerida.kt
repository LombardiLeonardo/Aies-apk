package com.aies.feature.inspecciones.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Observación predefinida para el diálogo de observaciones.
 * @param palabrasClave  Palabras separadas por coma que activan esta sugerencia
 *                       al coincidir con la descripción del ítem inspeccionado.
 *                       Ej: "neumatico,llanta,rueda"
 * @param esPrecargada   true = viene de fábrica, false = agregada por el auditor
 */
@Entity(tableName = "observaciones_sugeridas")
data class ObservacionSugerida(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val texto: String,
    val palabrasClave: String = "",   // coma-separated
    val esPrecargada: Boolean = false
)
