package com.aies.feature.planillaatmosfera.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Registro local de la "Planilla de Control de Atmósfera de Tanques Cisterna" (R 12-01 Rev.01). */
@Entity(tableName = "planilla_atmosfera_registro")
data class PlanillaAtmosferaRegistro(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val unidadId: Long,
    val dominio: String,
    val cliente: String,
    val auditor: String,
    val fechaCreadoTimestamp: Long,
    val tipoEstanqueidad: Boolean,
    val tipoIntegridadDispositivos: Boolean,
    val tipoEspaciosConfinados: Boolean,
    val tipoOtros: Boolean,
    val tipoOtrosDetalle: String,
    val productoDiesel: Boolean,
    val productoNafta: Boolean,
    val productoFame: Boolean,
    val equipoUtilizado: String,
    val observaciones: String,
    val firmaResponsable: String,
    val archivoPdf: String? = null
)

/** Un renglón por cisterna con las 4 mediciones del equipo (nulas si no se cargaron). */
@Entity(
    tableName = "atmosfera_cisterna",
    foreignKeys = [ForeignKey(entity = PlanillaAtmosferaRegistro::class, parentColumns = ["id"], childColumns = ["registroId"])],
    indices = [Index("registroId")]
)
data class AtmosferaCisterna(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val registroId: Long,
    val cisternaNumero: Int,
    val lel: Double? = null,
    val o2: Double? = null,
    val co: Double? = null,
    val h2s: Double? = null
)
