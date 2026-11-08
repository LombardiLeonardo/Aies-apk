package com.aies.coredata.dao

import android.util.Log
import com.aies.coredata.entities.SesionServicio
import com.aies.coredata.entities.SesionServicioItem
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val TAG = "SesionServicioDao"

/**
 * Fase D, paso 3 de 5 del pase a base de datos compartida: Supabase es la fuente de verdad,
 * Room (ver [SesionServicioLocalDao]) es cache local. Lectura: Supabase primero -- si responde,
 * se intenta cachear lo remoto en Room dentro del mismo try (un fallo al cachear también cae al
 * fallback en vez de crashear). Si Supabase no responde, fallback a la cache local (con log).
 * Escritura: Supabase primero, sin try/catch -- si falla, la excepción se propaga al llamador
 * (que la maneja con un Toast, ver los gaps arreglados en feature-sesion/SesionScreen.kt).
 * [marcarItemCompleto] es un UPDATE por id, naturalmente idempotente: llamarlo dos veces con el
 * mismo historialServicioId no duplica ni rompe nada, ni local ni remoto.
 */
class SesionServicioDao internal constructor(
    private val local: SesionServicioLocalDao,
    private val remoto: SupabaseClient
) {
    @Serializable
    private data class SesionServicioRemota(
        val id: Long = 0,
        @SerialName("unidad_id") val unidadId: Long,
        val fecha: Long,
        val auditor: String,
        val cliente: String,
        val domicilio: String,
        val cuit: String,
        @SerialName("patente_tractor") val patenteTractor: String,
        @SerialName("marca_tractor") val marcaTractor: String,
        @SerialName("anio_tractor") val anioTractor: Int,
        @SerialName("modelo_tractor") val modeloTractor: String,
        @SerialName("patente_semi") val patenteSemi: String,
        @SerialName("marca_semi") val marcaSemi: String,
        @SerialName("anio_semi") val anioSemi: Int,
        @SerialName("modelo_semi") val modeloSemi: String,
        val estado: String,
        @SerialName("hora_inicio") val horaInicio: Long,
        @SerialName("hora_cierre") val horaCierre: Long? = null,
        // Nullable a propósito: la columna remota es NULLABLE (null hasta que "Finalizar visita"
        // termina de subir la nota a Storage en segundo plano, Fase F) -- kotlinx.serialization
        // rechaza `null` para un campo `String` aunque tenga default (JsonDecodingException:
        // "Expected string literal but 'null' literal was found"). BUG REAL encontrado en logcat:
        // esto hacía fallar crearSesion() (decodificando la fila recién insertada, con
        // nota_trabajo_url todavía null) y sesionesAbiertas() (CUALQUIER sesión sin nota subida
        // todavía) — exactamente la causa del "sin conexión" al iniciar visita y de no ver
        // sesiones abiertas creadas por otro dispositivo.
        @SerialName("nota_trabajo_url") val notaTrabajoUrl: String? = null
    )

    @Serializable
    private data class SesionServicioItemRemota(
        val id: Long = 0,
        @SerialName("sesion_id") val sesionId: Long,
        @SerialName("tipo_servicio_codigo") val tipoServicioCodigo: String,
        val estado: String,
        @SerialName("historial_servicio_id") val historialServicioId: Long? = null
    )

    @Serializable
    private data class TipoServicioNombreAnidado(val nombre: String)

    @Serializable
    private data class ItemDeSesionConNombreRemoto(
        val id: Long,
        @SerialName("sesion_id") val sesionId: Long,
        @SerialName("tipo_servicio_codigo") val tipoServicioCodigo: String,
        val estado: String,
        @SerialName("historial_servicio_id") val historialServicioId: Long?,
        @SerialName("tipos_servicio") val tipoServicio: TipoServicioNombreAnidado
    )

    @Serializable
    private data class IdRemoto(val id: Long)

    private fun SesionServicioRemota.aLocal() = SesionServicio(
        id = id, unidadId = unidadId, fecha = fecha, auditor = auditor, cliente = cliente,
        domicilio = domicilio, cuit = cuit, patenteTractor = patenteTractor, marcaTractor = marcaTractor,
        anioTractor = anioTractor, modeloTractor = modeloTractor, patenteSemi = patenteSemi,
        marcaSemi = marcaSemi, anioSemi = anioSemi, modeloSemi = modeloSemi, estado = estado,
        horaInicio = horaInicio, horaCierre = horaCierre, notaTrabajoUrl = notaTrabajoUrl ?: ""
    )

    private fun SesionServicio.aRemota() = SesionServicioRemota(
        id = id, unidadId = unidadId, fecha = fecha, auditor = auditor, cliente = cliente,
        domicilio = domicilio, cuit = cuit, patenteTractor = patenteTractor, marcaTractor = marcaTractor,
        anioTractor = anioTractor, modeloTractor = modeloTractor, patenteSemi = patenteSemi,
        marcaSemi = marcaSemi, anioSemi = anioSemi, modeloSemi = modeloSemi, estado = estado,
        horaInicio = horaInicio, horaCierre = horaCierre, notaTrabajoUrl = notaTrabajoUrl.ifBlank { null }
    )

    private fun SesionServicioItem.aRemota() = SesionServicioItemRemota(
        id = id, sesionId = sesionId, tipoServicioCodigo = tipoServicioCodigo,
        estado = estado, historialServicioId = historialServicioId
    )

    private fun SesionServicioItemRemota.aLocal() = SesionServicioItem(
        id = id, sesionId = sesionId, tipoServicioCodigo = tipoServicioCodigo,
        estado = estado, historialServicioId = historialServicioId
    )

    private fun ItemDeSesionConNombreRemoto.aItemDeSesion() = ItemDeSesion(
        id = id, sesionId = sesionId, tipoServicioCodigo = tipoServicioCodigo,
        tipoServicioNombre = tipoServicio.nombre, estado = estado, historialServicioId = historialServicioId
    )

    private fun ItemDeSesionConNombreRemoto.aLocal() = SesionServicioItem(
        id = id, sesionId = sesionId, tipoServicioCodigo = tipoServicioCodigo,
        estado = estado, historialServicioId = historialServicioId
    )

    /** Sin try/catch a propósito -- ver doc de la clase. */
    suspend fun crearSesion(sesion: SesionServicio): Long {
        val remota = remoto.from("sesiones_servicio").insert(sesion.aRemota()) { select() }.decodeSingle<SesionServicioRemota>()
        local.crearSesion(remota.aLocal())
        return remota.id
    }

    /** Sin try/catch a propósito -- ver doc de la clase. */
    suspend fun agregarItems(items: List<SesionServicioItem>) {
        val remotas = remoto.from("sesion_servicio_items")
            .insert(items.map { it.aRemota() }) { select() }
            .decodeList<SesionServicioItemRemota>()
        local.agregarItems(remotas.map { it.aLocal() })
    }

    suspend fun sesionesAbiertas(): List<SesionServicio> {
        return try {
            val remotas = remoto.from("sesiones_servicio")
                .select { filter { eq("estado", "EN_PROGRESO") } }
                .decodeList<SesionServicioRemota>()
                .map { it.aLocal() }
            remotas.forEach { local.crearSesion(it) }
            remotas
        } catch (e: Exception) {
            Log.w(TAG, "Fallback a cache local: Supabase no respondió (sesionesAbiertas)", e)
            local.sesionesAbiertas()
        }
    }

    suspend fun getPorId(sesionId: Long): SesionServicio? {
        return try {
            val remota = remoto.from("sesiones_servicio").select { filter { eq("id", sesionId) } }.decodeList<SesionServicioRemota>().firstOrNull()
            remota?.aLocal()?.also { local.crearSesion(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Fallback a cache local: Supabase no respondió (getPorId)", e)
            local.getPorId(sesionId)
        }
    }

    /** Ver doc de [SesionServicioLocalDao.sesionDeUnidadEnElDia]. */
    suspend fun sesionDeUnidadEnElDia(unidadId: Long, inicioDelDia: Long, finDelDia: Long): SesionServicio? {
        return try {
            remoto.from("sesiones_servicio").select {
                filter {
                    eq("unidad_id", unidadId)
                    gte("fecha", inicioDelDia)
                    lte("fecha", finDelDia)
                }
                order("fecha", Order.DESCENDING)
                limit(1)
            }.decodeList<SesionServicioRemota>().firstOrNull()?.aLocal()?.also { local.crearSesion(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Fallback a cache local: Supabase no respondió (sesionDeUnidadEnElDia)", e)
            local.sesionDeUnidadEnElDia(unidadId, inicioDelDia, finDelDia)
        }
    }

    /** Ver doc de [SesionServicioLocalDao.sesionesDelDia]. */
    suspend fun sesionesDelDia(inicioDelDia: Long, finDelDia: Long): List<SesionServicio> {
        return try {
            val remotas = remoto.from("sesiones_servicio").select {
                filter {
                    gte("fecha", inicioDelDia)
                    lte("fecha", finDelDia)
                }
            }.decodeList<SesionServicioRemota>().map { it.aLocal() }
            remotas.forEach { local.crearSesion(it) }
            remotas
        } catch (e: Exception) {
            Log.w(TAG, "Fallback a cache local: Supabase no respondió (sesionesDelDia)", e)
            local.sesionesDelDia(inicioDelDia, finDelDia)
        }
    }

    suspend fun itemsDeSesion(sesionId: Long): List<ItemDeSesion> {
        return try {
            val remotas = remoto.from("sesion_servicio_items").select(
                columns = Columns.raw("id,sesion_id,tipo_servicio_codigo,estado,historial_servicio_id,tipos_servicio(nombre)")
            ) {
                filter { eq("sesion_id", sesionId) }
            }.decodeList<ItemDeSesionConNombreRemoto>()
            remotas.forEach { local.agregarItems(listOf(it.aLocal())) }
            remotas.map { it.aItemDeSesion() }
        } catch (e: Exception) {
            Log.w(TAG, "Fallback a cache local: Supabase no respondió (itemsDeSesion)", e)
            local.itemsDeSesion(sesionId)
        }
    }

    /** Sin try/catch a propósito -- ver doc de la clase. UPDATE por id, idempotente. */
    suspend fun marcarItemCompleto(itemId: Long, historialServicioId: Long) {
        remoto.from("sesion_servicio_items").update({
            set("estado", "COMPLETO")
            set("historial_servicio_id", historialServicioId)
        }) { filter { eq("id", itemId) } }
        local.marcarItemCompleto(itemId, historialServicioId)
    }

    /** Sin try/catch a propósito -- ver doc de la clase. */
    suspend fun eliminarItem(itemId: Long) {
        // select() hace que Postgrest devuelva las filas borradas: con RLS sin policy de DELETE el
        // request responde 200 pero borra 0 filas -- sin este chequeo el "Quitar" parecía andar
        // (se borraba solo el espejo local) y el item reaparecía al recargar desde Supabase.
        val borradas = remoto.from("sesion_servicio_items").delete {
            select(Columns.raw("id"))
            filter { eq("id", itemId) }
        }.decodeList<IdRemoto>()
        check(borradas.isNotEmpty()) { "Supabase no borró el item $itemId (¿falta la policy de DELETE?)" }
        local.eliminarItem(itemId)
    }

    suspend fun cantidadItemsPendientes(sesionId: Long): Int {
        return try {
            remoto.from("sesion_servicio_items").select(columns = Columns.raw("id")) {
                filter {
                    eq("sesion_id", sesionId)
                    neq("estado", "COMPLETO")
                }
            }.decodeList<IdRemoto>().size
        } catch (e: Exception) {
            Log.w(TAG, "Fallback a cache local: Supabase no respondió (cantidadItemsPendientes)", e)
            local.cantidadItemsPendientes(sesionId)
        }
    }

    suspend fun todosLosItemsCompletos(sesionId: Long): Boolean = cantidadItemsPendientes(sesionId) == 0

    /** Sin try/catch a propósito -- ver doc de la clase. */
    suspend fun cerrarSesion(sesionId: Long, horaCierre: Long) {
        remoto.from("sesiones_servicio").update({
            set("estado", "CERRADA")
            set("hora_cierre", horaCierre)
        }) { filter { eq("id", sesionId) } }
        local.cerrarSesion(sesionId, horaCierre)
    }

    /** Sin try/catch a propósito -- ver doc de la clase. UPDATE por id, idempotente. Se llama
     *  desde el scope de corrutina desacoplado de DocumentoStorageHelper, después de que la
     *  subida a Supabase Storage de la nota de trabajo termina en segundo plano (ver "Finalizar
     *  visita" en SesionScreen.kt, Fase F). */
    suspend fun actualizarNotaTrabajoUrl(sesionId: Long, url: String) {
        remoto.from("sesiones_servicio").update({ set("nota_trabajo_url", url) }) { filter { eq("id", sesionId) } }
        local.actualizarNotaTrabajoUrl(sesionId, url)
    }
}
