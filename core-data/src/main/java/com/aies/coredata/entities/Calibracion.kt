package com.aies.coredata.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Un certificado de calibración de tanque cisterna (feature-calibrado), asociado a la
 * Unidad semirremolque por [unidadId]. [fechaVencimiento] se precalcula al insertar
 * (fecha + vigenciaAnios años) para poder consultar la calibración vigente sin recalcular
 * en cada lectura (ver [com.aies.coredata.dao.CalibracionDao.calibracionVigenteDeUnidad]).
 */
@Entity(
    tableName = "calibraciones",
    indices = [Index(value = ["unidadId"])]
)
data class Calibracion(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val unidadId: Long,

    // No estaban en el modelo original de esta entidad; se agregan porque el certificado
    // los necesita (TRANSPORTISTA/CUIT) y este módulo no tiene base de datos propia donde
    // guardarlos (a diferencia de feature-informes, que los persiste en su Cliente local).
    val transportista: String,
    val cuit: String,

    val fecha: Long, // epoch millis
    val vigenciaAnios: Int, // 1, 2 o 4 — validado en la UI, no acá
    val fechaVencimiento: Long, // epoch millis, precalculado = fecha + vigenciaAnios años

    val patenteTractor: String,
    val marcaTractor: String,
    val tipoEjesTractor: String,
    val rodadoTractor: String,
    val tara: Double?,
    val maximoPesoPermitido: Double?,

    val patenteSemi: String,
    val marcaSemi: String,
    val modeloSemi: String,
    val tipoEjesSemi: String,
    val rodadoSemi: String,
    val cotaAnterior: Double?,
    val cotaPosterior: Double?,
    val volumenSemi: Double?,

    // Planilla de Calibración de Uso Interno (R 14-03): horario de inicio/fin del servicio,
    // epoch millis completo (fecha + hora), no solo minutos-desde-medianoche. Documento interno
    // path/url: mismo patrón que documentoUrl en HistorialServicio, pero acá vive en la propia
    // Calibracion porque este PDF interno no se registra en HistorialServicio (ver
    // ControlCalibradoScreen.generarCertificado()).
    val horaInicio: Long? = null,
    val horaFin: Long? = null,
    val documentoInternoPath: String? = null,
    val documentoInternoUrl: String? = null
)
