package com.aies.coredata.dao

import android.util.Log
import com.aies.coredata.entities.Calibracion
import com.aies.coredata.entities.CalibracionCisterna
import com.aies.coredata.entities.CalibracionDescargaPatron
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val TAG = "CalibracionDao"

/**
 * Fase D, paso 4 de 5 del pase a base de datos compartida: Supabase es la fuente de verdad,
 * Room (ver [CalibracionLocalDao]) es cache local. Lectura: Supabase primero -- si responde, se
 * intenta cachear en Room dentro del mismo try (un fallo al cachear cae al mismo fallback en vez
 * de crashear). Si Supabase no responde, fallback a la cache local (con log). Escritura:
 * Supabase primero, sin try/catch -- si falla, la excepción se propaga al llamador (que ya la
 * maneja con un Toast, ver ControlCalibradoScreen.generarCertificado()).
 *
 * `numero_certificado` es NOT NULL en la tabla remota `calibraciones`. SEC-004 (docs/security/
 * AUDITORIA-2026-09-19.md): un trigger server-side (`trg_calibraciones_asignar_numero`, ver
 * schema.sql) pisa esta columna con `nextval('seq_certificado_calibracion')` en cada INSERT, sin
 * importar qué valor mande el cliente -- así que [insertarCalibracion] ya NO recibe el número
 * como parámetro (no tendría efecto real): manda un valor provisorio (0) y devuelve el número
 * REAL que el servidor asignó, leído de la fila que devuelve el propio INSERT (`{ select() }`).
 * El llamador (ControlCalibradoScreen.generarCertificado()) usa ese valor de vuelta para el PDF,
 * no un número "adivinado" de antemano -- así el número impreso siempre coincide con el guardado
 * en la base. Esa columna no se mapea a la entidad local [Calibracion] (que no la tiene) ni se
 * usa en ningún otro lado.
 */
class CalibracionDao internal constructor(
    private val local: CalibracionLocalDao,
    private val remoto: SupabaseClient
) {
    @Serializable
    private data class CalibracionRemota(
        val id: Long = 0,
        @SerialName("unidad_id") val unidadId: Long,
        @SerialName("numero_certificado") val numeroCertificado: Long = 0,
        val transportista: String, val cuit: String,
        val fecha: Long, @SerialName("vigencia_anios") val vigenciaAnios: Int,
        @SerialName("fecha_vencimiento") val fechaVencimiento: Long,
        @SerialName("patente_tractor") val patenteTractor: String,
        @SerialName("marca_tractor") val marcaTractor: String,
        @SerialName("tipo_ejes_tractor") val tipoEjesTractor: String,
        @SerialName("rodado_tractor") val rodadoTractor: String,
        val tara: Double?, @SerialName("maximo_peso_permitido") val maximoPesoPermitido: Double?,
        @SerialName("patente_semi") val patenteSemi: String,
        @SerialName("marca_semi") val marcaSemi: String,
        @SerialName("modelo_semi") val modeloSemi: String,
        @SerialName("tipo_ejes_semi") val tipoEjesSemi: String,
        @SerialName("rodado_semi") val rodadoSemi: String,
        @SerialName("cota_anterior") val cotaAnterior: Double?,
        @SerialName("cota_posterior") val cotaPosterior: Double?,
        @SerialName("volumen_semi") val volumenSemi: Double?,
        @SerialName("hora_inicio") val horaInicio: Long? = null,
        @SerialName("hora_fin") val horaFin: Long? = null
    )

    @Serializable
    private data class CalibracionCisternaRemota(
        val id: Long = 0,
        @SerialName("calibracion_id") val calibracionId: Long,
        @SerialName("cisterna_numero") val cisternaNumero: Int,
        val capacidad: Double?, @SerialName("enrase_mm") val enraseMm: Double?,
        @SerialName("vacio_mm") val vacioMm: Double?, @SerialName("vacio_lts") val vacioLts: Double?,
        @SerialName("rebalse_mm") val rebalseMm: Double?,
        @SerialName("precinto_enrase") val precintoEnrase: String,
        @SerialName("precinto_boca") val precintoBoca: String,
        @SerialName("precinto_soporte") val precintoSoporte: String,
        @SerialName("sensor_marca") val sensorMarca: String? = null,
        @SerialName("sensor_numero_serie") val sensorNumeroSerie: String? = null,
        @SerialName("sensor_cumple") val sensorCumple: Boolean? = null,
        @SerialName("posicion_cumple") val posicionCumple: Boolean? = null,
        @SerialName("posicion_observacion") val posicionObservacion: String? = null,
        @SerialName("litros_sensor_rebalse") val litrosSensorRebalse: Double? = null
    )

    @Serializable
    private data class CalibracionDescargaPatronRemota(
        val id: Long = 0,
        @SerialName("calibracion_cisterna_id") val calibracionCisternaId: Long,
        @SerialName("punto_volumen_lts") val puntoVolumenLts: Double,
        @SerialName("numero_lectura") val numeroLectura: Int? = null,
        @SerialName("valor_medido") val valorMedido: Double? = null
    )

    /** Solo para la lectura embebida (join reverso) -- separado del DTO de insert/update de
     *  arriba para no mezclar la lista anidada de cisternas con el payload plano. */
    @Serializable
    private data class CalibracionConCisternasRemota(
        val id: Long,
        @SerialName("unidad_id") val unidadId: Long,
        val transportista: String, val cuit: String,
        val fecha: Long, @SerialName("vigencia_anios") val vigenciaAnios: Int,
        @SerialName("fecha_vencimiento") val fechaVencimiento: Long,
        @SerialName("patente_tractor") val patenteTractor: String,
        @SerialName("marca_tractor") val marcaTractor: String,
        @SerialName("tipo_ejes_tractor") val tipoEjesTractor: String,
        @SerialName("rodado_tractor") val rodadoTractor: String,
        val tara: Double?, @SerialName("maximo_peso_permitido") val maximoPesoPermitido: Double?,
        @SerialName("patente_semi") val patenteSemi: String,
        @SerialName("marca_semi") val marcaSemi: String,
        @SerialName("modelo_semi") val modeloSemi: String,
        @SerialName("tipo_ejes_semi") val tipoEjesSemi: String,
        @SerialName("rodado_semi") val rodadoSemi: String,
        @SerialName("cota_anterior") val cotaAnterior: Double?,
        @SerialName("cota_posterior") val cotaPosterior: Double?,
        @SerialName("volumen_semi") val volumenSemi: Double?,
        @SerialName("hora_inicio") val horaInicio: Long? = null,
        @SerialName("hora_fin") val horaFin: Long? = null,
        @SerialName("documento_interno_path") val documentoInternoPath: String? = null,
        @SerialName("documento_interno_url") val documentoInternoUrl: String? = null,
        @SerialName("calibracion_cisternas") val cisternas: List<CalibracionCisternaRemota> = emptyList()
    )

    private fun Calibracion.aRemota() = CalibracionRemota(
        id = id, unidadId = unidadId, transportista = transportista, cuit = cuit,
        fecha = fecha, vigenciaAnios = vigenciaAnios, fechaVencimiento = fechaVencimiento,
        patenteTractor = patenteTractor, marcaTractor = marcaTractor,
        tipoEjesTractor = tipoEjesTractor, rodadoTractor = rodadoTractor,
        tara = tara, maximoPesoPermitido = maximoPesoPermitido,
        patenteSemi = patenteSemi, marcaSemi = marcaSemi, modeloSemi = modeloSemi,
        tipoEjesSemi = tipoEjesSemi, rodadoSemi = rodadoSemi,
        cotaAnterior = cotaAnterior, cotaPosterior = cotaPosterior, volumenSemi = volumenSemi,
        horaInicio = horaInicio, horaFin = horaFin
    )

    private fun CalibracionCisterna.aRemota() = CalibracionCisternaRemota(
        id = id, calibracionId = calibracionId, cisternaNumero = cisternaNumero,
        capacidad = capacidad, enraseMm = enraseMm, vacioMm = vacioMm, vacioLts = vacioLts,
        rebalseMm = rebalseMm, precintoEnrase = precintoEnrase, precintoBoca = precintoBoca,
        precintoSoporte = precintoSoporte,
        sensorMarca = sensorMarca, sensorNumeroSerie = sensorNumeroSerie, sensorCumple = sensorCumple,
        posicionCumple = posicionCumple, posicionObservacion = posicionObservacion,
        litrosSensorRebalse = litrosSensorRebalse
    )

    private fun CalibracionCisternaRemota.aLocal() = CalibracionCisterna(
        id = id, calibracionId = calibracionId, cisternaNumero = cisternaNumero,
        capacidad = capacidad, enraseMm = enraseMm, vacioMm = vacioMm, vacioLts = vacioLts,
        rebalseMm = rebalseMm, precintoEnrase = precintoEnrase, precintoBoca = precintoBoca,
        precintoSoporte = precintoSoporte,
        sensorMarca = sensorMarca, sensorNumeroSerie = sensorNumeroSerie, sensorCumple = sensorCumple,
        posicionCumple = posicionCumple, posicionObservacion = posicionObservacion,
        litrosSensorRebalse = litrosSensorRebalse
    )

    private fun CalibracionDescargaPatron.aRemota() = CalibracionDescargaPatronRemota(
        id = id, calibracionCisternaId = calibracionCisternaId, puntoVolumenLts = puntoVolumenLts,
        numeroLectura = numeroLectura, valorMedido = valorMedido
    )

    private fun CalibracionDescargaPatronRemota.aLocal() = CalibracionDescargaPatron(
        id = id, calibracionCisternaId = calibracionCisternaId, puntoVolumenLts = puntoVolumenLts,
        numeroLectura = numeroLectura, valorMedido = valorMedido
    )

    private fun CalibracionConCisternasRemota.aLocal() = Calibracion(
        id = id, unidadId = unidadId, transportista = transportista, cuit = cuit,
        fecha = fecha, vigenciaAnios = vigenciaAnios, fechaVencimiento = fechaVencimiento,
        patenteTractor = patenteTractor, marcaTractor = marcaTractor,
        tipoEjesTractor = tipoEjesTractor, rodadoTractor = rodadoTractor,
        tara = tara, maximoPesoPermitido = maximoPesoPermitido,
        patenteSemi = patenteSemi, marcaSemi = marcaSemi, modeloSemi = modeloSemi,
        tipoEjesSemi = tipoEjesSemi, rodadoSemi = rodadoSemi,
        cotaAnterior = cotaAnterior, cotaPosterior = cotaPosterior, volumenSemi = volumenSemi,
        horaInicio = horaInicio, horaFin = horaFin,
        documentoInternoPath = documentoInternoPath, documentoInternoUrl = documentoInternoUrl
    )

    /** Id real + número de certificado REAL (asignado por el trigger server-side, ver doc de la
     *  clase -- SEC-004), no el que mandó el cliente. */
    data class CalibracionInsertada(val id: Long, val numeroCertificado: Long)

    /** Sin try/catch a propósito -- ver doc de la clase: si Supabase falla, esto tira y el
     *  llamador (ControlCalibradoScreen.generarCertificado()) tiene que mostrarle el error al
     *  técnico. El `numero_certificado` que se manda acá (0) es puramente para satisfacer el
     *  NOT NULL del DTO -- el trigger `trg_calibraciones_asignar_numero` lo pisa siempre; el
     *  valor real vuelve en [CalibracionInsertada.numeroCertificado], leído de la fila que
     *  devuelve el propio INSERT. */
    suspend fun insertarCalibracion(c: Calibracion): CalibracionInsertada {
        val remota = remoto.from("calibraciones")
            .insert(c.aRemota()) { select() }
            .decodeSingle<CalibracionRemota>()
        local.insertarCalibracion(c.copy(id = remota.id))
        return CalibracionInsertada(remota.id, remota.numeroCertificado)
    }

    /** Sin try/catch a propósito -- ver doc de la clase. Devuelve las cisternas ya insertadas
     *  CON el id real asignado por Supabase -- el llamador lo necesita para asociar cada lectura
     *  de [CalibracionDescargaPatron] a su `calibracion_cisterna_id` (ver
     *  ControlCalibradoScreen.generarCertificado()). */
    suspend fun insertarCisternas(list: List<CalibracionCisterna>): List<CalibracionCisterna> {
        val remotas = remoto.from("calibracion_cisternas")
            .insert(list.map { it.aRemota() }) { select() }
            .decodeList<CalibracionCisternaRemota>()
        val locales = remotas.map { it.aLocal() }
        local.insertarCisternas(locales)
        return locales
    }

    /** Sin try/catch a propósito -- ver doc de la clase. */
    suspend fun insertarDescargaPatron(lecturas: List<CalibracionDescargaPatron>) {
        val remotas = remoto.from("calibracion_descarga_patron")
            .insert(lecturas.map { it.aRemota() }) { select() }
            .decodeList<CalibracionDescargaPatronRemota>()
        local.insertarDescargaPatron(remotas.map { it.aLocal() })
    }

    /** Sin try/catch a propósito -- ver doc de la clase. UPDATE por id, idempotente -- mismo
     *  patrón que HistorialServicioDao.actualizarDocumentoUrl. [path] y [url] se actualizan de
     *  forma independiente: un valor null deja la columna remota tal cual está (no la pisa con
     *  null), para poder llamarla dos veces (una con path recién generado localmente, otra más
     *  tarde con la url pública una vez que termina la subida a Storage en segundo plano). */
    suspend fun actualizarDocumentoInterno(id: Long, path: String?, url: String?) {
        remoto.from("calibraciones").update({
            path?.let { set("documento_interno_path", it) }
            url?.let { set("documento_interno_url", it) }
        }) { filter { eq("id", id) } }
        local.actualizarDocumentoInterno(id, path, url)
    }

    suspend fun calibracionVigenteDeUnidad(unidadId: Long, fechaActual: Long): CalibracionConCisternas? {
        return try {
            val remota = remoto.from("calibraciones").select(
                columns = Columns.raw("*,calibracion_cisternas(*)")
            ) {
                filter {
                    eq("unidad_id", unidadId)
                    gt("fecha_vencimiento", fechaActual)
                }
                order("fecha", Order.DESCENDING)
                limit(1)
            }.decodeList<CalibracionConCisternasRemota>().firstOrNull() ?: return null

            val calibracion = remota.aLocal()
            val cisternas = remota.cisternas.map { it.aLocal() }
            local.insertarCalibracion(calibracion)
            local.insertarCisternas(cisternas)
            CalibracionConCisternas(calibracion, cisternas)
        } catch (e: Exception) {
            Log.w(TAG, "Fallback a cache local: Supabase no respondió (calibracionVigenteDeUnidad)", e)
            local.calibracionVigenteDeUnidad(unidadId, fechaActual)
        }
    }
}
