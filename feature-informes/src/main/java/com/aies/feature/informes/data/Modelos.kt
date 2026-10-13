package com.aies.feature.informes.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Guarda los datos básicos del transportista para reutilizar en futuros informes (models.py::Cliente). */
@Entity(
    tableName = "clientes",
    indices = [Index(value = ["cuit"], unique = true), Index(value = ["razonSocial"], unique = true)]
)
data class Cliente(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val razonSocial: String,
    val cuit: String, // formato XX-XXXXXXXX-X
    val fechaRegistradoTimestamp: Long,
    val activo: Boolean = true
)

/** Un renglón de la sección "Control Vacío" para una cisterna (vacioData[i] del JSON original). */
data class CisternaVacio(
    val volumen: String, val vacioRequerido: String, val vacioMedido: String,
    val accion: String, val volumenFinal: String, val cumple: String
)

/** Un renglón de "Trazabilidad" (trazData[i]). */
data class CisternaTrazabilidad(val marca: String, val nroSerie: String, val cumple: String)

/** Un renglón de "Posición de Sensores" (posData[i]). */
data class CisternaPosicion(val litros: String, val observacion: String, val cumple: String)

/**
 * Historial de todos los informes generados (models.py::Certificado). El nombre se
 * mantiene "InformeRegistro" para no confundirlo con CertificadoRegistro de
 * feature-certificados, que es un dominio completamente distinto.
 */
@Entity(
    tableName = "informes",
    foreignKeys = [ForeignKey(entity = Cliente::class, parentColumns = ["id"], childColumns = ["clienteId"])],
    indices = [Index(value = ["clienteId"]), Index(value = ["nroInforme"], unique = true)]
)
data class InformeRegistro(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clienteId: Long,
    val nroInforme: String, // == calibradoNro
    val patenteTractor: String,
    val patenteSemirremolque: String,
    val calibradoNro: String,
    val fechaEmisionTimestamp: Long,
    val fechaVencimientoTimestamp: Long,
    val cantCisternas: Int,
    val resultadoFinal: String, // "SI" | "NO"
    val cvCumple: String,
    val tdCumple: String,
    val psCumple: String,
    val datosJson: String, // vacioData/trazData/posData completos, para trazabilidad/auditoría
    val fechaCreadoTimestamp: Long,
    val archivoPdf: String? = null,
    val notas: String? = null
)

/** Proyección plana (informe + cliente ya unidos) para exportar a Excel, equivalente al
 *  acceso `certificado.cliente.razon_social` vía backref de SQLAlchemy en exportar_excel.py. */
data class InformeExportRow(
    val nroInforme: String,
    val clienteRazonSocial: String,
    val clienteCuit: String,
    val patenteTractor: String,
    val patenteSemirremolque: String,
    val calibradoNro: String,
    val fechaEmisionTimestamp: Long,
    val fechaVencimientoTimestamp: Long,
    val cantCisternas: Int,
    val resultadoFinal: String,
    val cvCumple: String,
    val tdCumple: String,
    val psCumple: String,
    val fechaCreadoTimestamp: Long,
    val notas: String?
)
