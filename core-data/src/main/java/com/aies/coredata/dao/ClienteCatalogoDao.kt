package com.aies.coredata.dao

import android.util.Log
import com.aies.coredata.entities.ClienteCatalogo
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.Serializable

private const val TAG = "ClienteCatalogoDao"

/**
 * Antes era Room puro, sin ninguna tabla equivalente en Supabase -- cada dispositivo armaba su
 * propia lista de clientes según lo que ESE técnico hubiera tipeado en Nueva Visita, sin
 * enterarse nunca de lo que tipeó otro técnico en otro teléfono (bug real reportado por el
 * usuario). Mismo patrón que [TipoServicioDao]: Supabase es la fuente de verdad, Room (ver
 * [ClienteCatalogoLocalDao]) es cache local. Catálogo chico, así que [getAll] reemplaza toda la
 * cache local con lo que trajo Supabase en cada lectura exitosa, en vez de diffear fila por fila.
 * Si Supabase no responde, fallback a la cache local (con log) -- así el autocompletar sigue
 * funcionando sin conexión, aunque desactualizado.
 */
class ClienteCatalogoDao internal constructor(
    private val local: ClienteCatalogoLocalDao,
    private val remoto: SupabaseClient
) {
    @Serializable
    private data class ClienteCatalogoRemoto(val nombre: String, val cuit: String = "")

    private fun ClienteCatalogoRemoto.aLocal() = ClienteCatalogo(nombre, cuit)
    private fun ClienteCatalogo.aRemota() = ClienteCatalogoRemoto(nombre, cuit)

    suspend fun getAll(): List<ClienteCatalogo> {
        return try {
            val remotos = remoto.from("clientes_catalogo").select().decodeList<ClienteCatalogoRemoto>().map { it.aLocal() }
            remotos.forEach { local.insertar(it) }
            remotos
        } catch (e: Exception) {
            Log.w(TAG, "Fallback a cache local: Supabase no respondió (getAll)", e)
            local.getAll()
        }
    }

    suspend fun insertar(item: ClienteCatalogo) {
        remoto.from("clientes_catalogo").upsert(item.aRemota())
        local.insertar(item)
    }
}
