package com.aies.coredata.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Fila por cisterna de una [Calibracion], asociada por [calibracionId]. */
@Entity(
    tableName = "calibracion_cisternas",
    indices = [Index(value = ["calibracionId"])]
)
data class CalibracionCisterna(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val calibracionId: Long,

    val cisternaNumero: Int,
    val capacidad: Double?,
    val enraseMm: Double?,
    val vacioMm: Double?,
    val vacioLts: Double?,
    val rebalseMm: Double?,
    val precintoEnrase: String,
    val precintoBoca: String,
    val precintoSoporte: String,

    // Planilla de Calibración de Uso Interno (R 14-03): trazabilidad y posición de sensores de
    // Informe de Carga Segura (según corresponda -- todos nullable).
    val sensorMarca: String? = null,
    val sensorNumeroSerie: String? = null,
    val sensorCumple: Boolean? = null,
    val posicionCumple: Boolean? = null,
    val posicionObservacion: String? = null,
    val litrosSensorRebalse: Double? = null
)
