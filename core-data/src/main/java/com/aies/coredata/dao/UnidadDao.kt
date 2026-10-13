package com.aies.coredata.dao

import android.util.Log
import com.aies.coredata.entities.Unidad
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val TAG = "UnidadDao"

/**
 * Fase D, paso 1 de 5 del pase a base de datos compartida: Supabase es la fuente de verdad,
 * Room (ver [UnidadLocalDao]) es cache local para que la UI no dependa de la latencia de red en
 * cada lectura. Lectura: Supabase primero; si responde, se refresca la fila local con lo remoto
 * (por si otro dispositivo la cambió) y se devuelve eso. Si Supabase no responde (falla de red),
 * fallback silencioso a la cache local -- se loguea con [TAG] para poder detectar si pasa
 * seguido. Escritura: Supabase primero; solo si confirma éxito se refleja en la cache local. Si
 * la escritura remota falla, la excepción se propaga sin tocar la cache local -- mejor un error
 * visible en el momento que un dato que "parece guardado" pero nunca llegó a otros dispositivos
 * (ver el try/catch que el llamador en Nueva Visita necesita agregar).
 */
class UnidadDao internal constructor(
    private val local: UnidadLocalDao,
    private val remoto: SupabaseClient
) {
    @Serializable
    private data class UnidadRemota(
        val id: Long = 0,
        val placa: String,
        @SerialName("tipo_unidad") val tipoUnidad: String,
        val cliente: String,
        @SerialName("fecha_registro") val fechaRegistro: Long,
        @SerialName("cantidad_cisternas") val cantidadCisternas: Int = 1,
        val cuit: String = "",
        @SerialName("patente_tractor") val patenteTractor: String = "",
        @SerialName("marca_tractor") val marcaTractor: String = "",
        @SerialName("anio_tractor") val anioTractor: String = "",
        @SerialName("modelo_tractor") val modeloTractor: String = "",
        @SerialName("patente_semi") val patenteSemi: String = "",
        @SerialName("marca_semi") val marcaSemi: String = "",
        @SerialName("anio_semi") val anioSemi: String = "",
        @SerialName("modelo_semi") val modeloSemi: String = ""
    )

    private fun UnidadRemota.aLocal() = Unidad(
        id, placa, tipoUnidad, cliente, fechaRegistro, cantidadCisternas,
        cuit, patenteTractor, marcaTractor, anioTractor, modeloTractor,
        patenteSemi, marcaSemi, anioSemi, modeloSemi
    )

    private fun Unidad.aRemota() = UnidadRemota(
        id, placa, tipoUnidad, cliente, fechaRegistro, cantidadCisternas,
        cuit, patenteTractor, marcaTractor, anioTractor, modeloTractor,
        patenteSemi, marcaSemi, anioSemi, modeloSemi
    )

    suspend fun getPorPlaca(placa: String): Unidad? {
        return try {
            val remota = remoto.from("unidades").select { filter { eq("placa", placa) } }.decodeList<UnidadRemota>().firstOrNull()
            remota?.aLocal()?.also { local.insertar(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Fallback a cache local: Supabase no respondió (getPorPlaca)", e)
            local.getPorPlaca(placa)
        }
    }

    suspend fun getPorId(id: Long): Unidad? {
        return try {
            val remota = remoto.from("unidades").select { filter { eq("id", id) } }.decodeList<UnidadRemota>().firstOrNull()
            remota?.aLocal()?.also { local.insertar(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Fallback a cache local: Supabase no respondió (getPorId)", e)
            local.getPorId(id)
        }
    }

    /** Sin try/catch a propósito -- ver doc de la clase: si Supabase falla, esto tira y el
     *  llamador (Nueva Visita) tiene que mostrarle el error al técnico, no guardar a medias. */
    suspend fun insertar(unidad: Unidad): Long {
        val remota = remoto.from("unidades").insert(unidad.aRemota()) { select() }.decodeSingle<UnidadRemota>()
        local.insertar(remota.aLocal())
        return remota.id
    }

    suspend fun actualizarCantidadCisternas(id: Long, cantidad: Int) {
        remoto.from("unidades").update({ set("cantidad_cisternas", cantidad) }) { filter { eq("id", id) } }
        local.actualizarCantidadCisternas(id, cantidad)
    }
}
