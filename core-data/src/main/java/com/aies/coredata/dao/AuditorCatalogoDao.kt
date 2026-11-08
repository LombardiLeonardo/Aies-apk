package com.aies.coredata.dao

import android.util.Log
import com.aies.coredata.entities.AuditorCatalogo
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.Serializable

private const val TAG = "AuditorCatalogoDao"

/** Mismo criterio y mismo bug real que [ClienteCatalogoDao] -- ver su doc. */
class AuditorCatalogoDao internal constructor(
    private val local: AuditorCatalogoLocalDao,
    private val remoto: SupabaseClient
) {
    @Serializable
    private data class AuditorCatalogoRemoto(val nombre: String)

    private fun AuditorCatalogoRemoto.aLocal() = AuditorCatalogo(nombre)
    private fun AuditorCatalogo.aRemota() = AuditorCatalogoRemoto(nombre)

    suspend fun getAll(): List<AuditorCatalogo> {
        return try {
            val remotos = remoto.from("auditores_catalogo").select().decodeList<AuditorCatalogoRemoto>().map { it.aLocal() }.sortedBy { it.nombre.lowercase() }
            remotos.forEach { local.insertar(it) }
            remotos
        } catch (e: Exception) {
            Log.w(TAG, "Fallback a cache local: Supabase no respondió (getAll)", e)
            local.getAll().sortedBy { it.nombre.lowercase() }
        }
    }

    suspend fun insertar(item: AuditorCatalogo) {
        remoto.from("auditores_catalogo").upsert(item.aRemota())
        local.insertar(item)
    }
}
