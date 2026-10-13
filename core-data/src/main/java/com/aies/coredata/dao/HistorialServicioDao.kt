package com.aies.coredata.dao

import android.util.Log
import com.aies.coredata.entities.HistorialServicio
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val TAG = "HistorialServicioDao"

/**
 * Fase D, paso 2 de 5 del pase a base de datos compartida: Supabase es la fuente de verdad,
 * Room (ver [HistorialServicioLocalDao]) es cache local. Lectura: Supabase primero -- si
 * responde, se intenta cachear cada fila localmente (dentro del mismo try, para que un fallo al
 * cachear, ej. FK de Unidad/TipoServicio que todavía no está en cache local, caiga al mismo
 * fallback en vez de crashear) y se devuelve lo remoto. Si Supabase no responde, fallback a la
 * cache local (con log). Escritura: Supabase primero, sin try/catch -- si falla, la excepción se
 * propaga al llamador (que ya la maneja con un Toast, ver los call sites de
 * HistorialCompartidoHelper.registrar()). Postgrest no soporta GROUP BY ni SELECT DISTINCT, así
 * que [serviciosDeVariasUnidadesEnElDia] y [unidadesConServicioEnElDia] traen las filas remotas
 * "crudas" y reproducen esa lógica en Kotlin.
 */
class HistorialServicioDao internal constructor(
    private val local: HistorialServicioLocalDao,
    private val remoto: SupabaseClient
) {
    @Serializable
    private data class HistorialServicioRemota(
        val id: Long = 0,
        @SerialName("unidad_id") val unidadId: Long,
        @SerialName("tipo_servicio_codigo") val tipoServicioCodigo: String,
        val fecha: Long,
        val auditor: String,
        @SerialName("documento_path") val documentoPath: String,
        // Nullable a propósito: la columna remota es NULLABLE (queda null hasta que la subida a
        // Storage en segundo plano de Fase F la completa) -- kotlinx.serialization NO acepta
        // `null` para un campo declarado `String` aunque tenga default, tira JsonDecodingException
        // ("Expected string literal but 'null' literal was found"). Bug real encontrado en
        // logcat: esto hacía fallar sesionesAbiertas()/crearSesion() (ver notaTrabajoUrl en
        // SesionServicioDao.kt, mismo bug) para CUALQUIER fila sin documento/nota subida todavía.
        @SerialName("documento_url") val documentoUrl: String? = null,
        // Nullable a propósito, mismo criterio que documentoUrl -- la columna remota es
        // NULLABLE (la mayoría de los módulos nunca la completan, ver HistorialServicio.detalle).
        val detalle: String? = null
    )

    @Serializable
    private data class TipoServicioNombreAnidado(val nombre: String)

    @Serializable
    private data class HistorialServicioConNombreRemoto(
        val id: Long,
        @SerialName("unidad_id") val unidadId: Long,
        @SerialName("tipo_servicio_codigo") val tipoServicioCodigo: String,
        val fecha: Long,
        val auditor: String,
        @SerialName("documento_path") val documentoPath: String,
        @SerialName("documento_url") val documentoUrl: String? = null,
        val detalle: String? = null,
        @SerialName("tipos_servicio") val tipoServicio: TipoServicioNombreAnidado
    )

    @Serializable
    private data class UnidadIdRemota(@SerialName("unidad_id") val unidadId: Long)

    private fun HistorialServicio.aRemota() = HistorialServicioRemota(
        unidadId = unidadId,
        tipoServicioCodigo = tipoServicioCodigo,
        fecha = fecha,
        auditor = auditor,
        documentoPath = documentoPath,
        documentoUrl = documentoUrl.ifBlank { null },
        detalle = detalle.ifBlank { null }
    )

    private fun HistorialServicioConNombreRemoto.aServicioDelDia() = ServicioDelDia(
        tipoServicioCodigo = tipoServicioCodigo,
        tipoServicioNombre = tipoServicio.nombre,
        auditor = auditor,
        documentoPath = documentoPath,
        documentoUrl = documentoUrl ?: "",
        fecha = fecha,
        detalle = detalle ?: ""
    )

    private fun HistorialServicioConNombreRemoto.aHistorialServicio() = HistorialServicio(
        unidadId = unidadId,
        tipoServicioCodigo = tipoServicioCodigo,
        fecha = fecha,
        auditor = auditor,
        documentoPath = documentoPath,
        documentoUrl = documentoUrl ?: "",
        detalle = detalle ?: ""
    )

    /** Sin try/catch a propósito -- ver doc de la clase: si Supabase falla, esto tira y el
     *  llamador (HistorialCompartidoHelper.registrar()) tiene que mostrarle el error al técnico. */
    suspend fun insertar(servicio: HistorialServicio): Long {
        val remota = remoto.from("historial_servicios").insert(servicio.aRemota()) { select() }.decodeSingle<HistorialServicioRemota>()
        local.insertar(
            HistorialServicio(
                id = remota.id,
                unidadId = remota.unidadId,
                tipoServicioCodigo = remota.tipoServicioCodigo,
                fecha = remota.fecha,
                auditor = remota.auditor,
                documentoPath = remota.documentoPath,
                documentoUrl = remota.documentoUrl ?: "",
                detalle = remota.detalle ?: ""
            )
        )
        return remota.id
    }

    /** Sin try/catch a propósito -- ver doc de la clase. UPDATE por id, idempotente. Se llama
     *  desde el scope de corrutina desacoplado de DocumentoStorageHelper, después de que la
     *  subida a Supabase Storage termina en segundo plano (ver los HistorialCompartidoHelper de
     *  cada módulo, Fase F). */
    suspend fun actualizarDocumentoUrl(id: Long, url: String) {
        remoto.from("historial_servicios").update({ set("documento_url", url) }) { filter { eq("id", id) } }
        local.actualizarDocumentoUrl(id, url)
    }

    suspend fun serviciosDeLaUnidadEnElDia(unidadId: Long, inicioDelDia: Long, finDelDia: Long): List<ServicioDelDia> {
        return try {
            val remotas = remoto.from("historial_servicios").select(
                columns = Columns.raw("id,unidad_id,tipo_servicio_codigo,fecha,auditor,documento_path,documento_url,detalle,tipos_servicio(nombre)")
            ) {
                filter {
                    eq("unidad_id", unidadId)
                    gte("fecha", inicioDelDia)
                    lte("fecha", finDelDia)
                }
                order("fecha", Order.ASCENDING)
            }.decodeList<HistorialServicioConNombreRemoto>()
            remotas.forEach { local.insertar(it.aHistorialServicio()) }
            remotas.map { it.aServicioDelDia() }
        } catch (e: Exception) {
            Log.w(TAG, "Fallback a cache local: Supabase no respondió (serviciosDeLaUnidadEnElDia)", e)
            local.serviciosDeLaUnidadEnElDia(unidadId, inicioDelDia, finDelDia)
        }
    }

    suspend fun serviciosDeVariasUnidadesEnElDia(unidadIds: List<Long>, inicioDelDia: Long, finDelDia: Long): List<ServicioDelDia> {
        return try {
            val remotas = remoto.from("historial_servicios").select(
                columns = Columns.raw("id,unidad_id,tipo_servicio_codigo,fecha,auditor,documento_path,documento_url,detalle,tipos_servicio(nombre)")
            ) {
                filter {
                    isIn("unidad_id", unidadIds)
                    gte("fecha", inicioDelDia)
                    lte("fecha", finDelDia)
                }
            }.decodeList<HistorialServicioConNombreRemoto>()
            remotas.forEach { local.insertar(it.aHistorialServicio()) }
            // Postgrest no soporta GROUP BY -- se reproduce acá: MIN(fecha) por
            // (documentoPath, tipoServicioCodigo, tipoServicioNombre, auditor).
            remotas.map { it.aServicioDelDia() }
                .groupBy { listOf(it.documentoPath, it.tipoServicioCodigo, it.tipoServicioNombre, it.auditor) }
                .map { (_, filas) -> filas.minBy { it.fecha } }
                .sortedBy { it.fecha }
        } catch (e: Exception) {
            Log.w(TAG, "Fallback a cache local: Supabase no respondió (serviciosDeVariasUnidadesEnElDia)", e)
            local.serviciosDeVariasUnidadesEnElDia(unidadIds, inicioDelDia, finDelDia)
        }
    }

    suspend fun unidadesConServicioEnElDia(inicioDelDia: Long, finDelDia: Long): List<Long> {
        return try {
            // No cachea nada acá -- solo trae unidad_id, no alcanza para reconstruir una fila
            // completa de HistorialServicio.
            remoto.from("historial_servicios").select(columns = Columns.raw("unidad_id")) {
                filter {
                    gte("fecha", inicioDelDia)
                    lte("fecha", finDelDia)
                }
            }.decodeList<UnidadIdRemota>().map { it.unidadId }.distinct()
        } catch (e: Exception) {
            Log.w(TAG, "Fallback a cache local: Supabase no respondió (unidadesConServicioEnElDia)", e)
            local.unidadesConServicioEnElDia(inicioDelDia, finDelDia)
        }
    }
}
