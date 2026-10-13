package com.aies.coredata.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Una lectura de la tabla "CONTROL DE DESCARGA PATRON" de la Planilla de Calibración de Uso
 * Interno (R 14-03), asociada a una [CalibracionCisterna] por [calibracionCisternaId].
 * [puntoVolumenLts] es uno de los 5 puntos fijos de la planilla (3000/1000/500/100/20).
 * [numeroLectura] permite más de una lectura por punto (1, 2, 3...) -- nullable porque no es
 * obligatorio distinguir cuando hay una sola lectura.
 */
@Entity(
    tableName = "calibracion_descarga_patron",
    indices = [Index(value = ["calibracionCisternaId"])]
)
data class CalibracionDescargaPatron(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val calibracionCisternaId: Long,
    val puntoVolumenLts: Double,
    val numeroLectura: Int? = null,
    val valorMedido: Double? = null
)
