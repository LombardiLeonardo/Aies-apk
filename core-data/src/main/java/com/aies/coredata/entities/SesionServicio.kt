package com.aies.coredata.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Una visita a una Unidad: agrupa todos los servicios marcados para atenderse ese día.
 * Fase 1 del flujo unificado de sesión — todavía sin consumidores (ningún feature module
 * ni :app la usa aún), solo el esquema.
 */
@Entity(
    tableName = "sesiones_servicio",
    indices = [Index(value = ["unidadId"]), Index(value = ["estado"])]
)
data class SesionServicio(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val unidadId: Long,
    val fecha: Long, // epoch millis, día de la visita

    val auditor: String,
    val cliente: String,
    val domicilio: String,
    val cuit: String,

    // Datos del tractor/semirremolque de ESTA visita puntual (no de la Unidad): el tractor que
    // arrastra una cisterna puede cambiar de una visita a otra aunque la Unidad (cisterna) sea
    // la misma, así que viven acá y no en Unidad.
    val patenteTractor: String,
    val marcaTractor: String,
    val anioTractor: Int,
    val modeloTractor: String,
    val patenteSemi: String,
    val marcaSemi: String,
    val anioSemi: Int,
    val modeloSemi: String,

    val estado: String, // "EN_PROGRESO" | "CERRADA"

    val horaInicio: Long,
    val horaCierre: Long?,

    // Fase F: URL pública en Supabase Storage del PDF de nota de trabajo generado al finalizar
    // esta visita -- "" cuando todavía no se subió o la subida falló.
    val notaTrabajoUrl: String = ""
)
