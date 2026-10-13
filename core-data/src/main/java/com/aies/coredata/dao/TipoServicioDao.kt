package com.aies.coredata.dao

import android.util.Log
import com.aies.coredata.entities.TipoServicio
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val TAG = "TipoServicioDao"

/**
 * Fase D, paso 1 de 5 del pase a base de datos compartida: Supabase es la fuente de verdad,
 * Room (ver [TipoServicioLocalDao]) es cache local. `tipos_servicio` es un catálogo chico
 * (~15 filas), así que en vez de diffear fila por fila, [getAll] simplemente reemplaza toda la
 * cache local con lo que trajo Supabase en cada lectura exitosa -- más simple y sin riesgo de
 * dejar filas viejas huérfanas. Si Supabase no responde, fallback a la cache local (con log).
 * [insertar] usa upsert remoto (no insert) porque `CatalogoServiciosSeed`
 * (feature-sesion/SesionScreen.kt) lo llama repetidamente esperando semántica idempotente
 * "insertar si no existe", que el `OnConflictStrategy.IGNORE` local le daba antes.
 */
class TipoServicioDao internal constructor(
    private val local: TipoServicioLocalDao,
    private val remoto: SupabaseClient
) {
    @Serializable
    private data class TipoServicioRemoto(
        val codigo: String,
        val nombre: String,
        @SerialName("modulo_origen") val moduloOrigen: String
    )

    private fun TipoServicioRemoto.aLocal() = TipoServicio(codigo, nombre, moduloOrigen)
    private fun TipoServicio.aRemota() = TipoServicioRemoto(codigo, nombre, moduloOrigen)

    suspend fun getAll(): List<TipoServicio> {
        return try {
            val remotos = remoto.from("tipos_servicio").select().decodeList<TipoServicioRemoto>().map { it.aLocal() }
            remotos.forEach { local.insertar(it) }
            remotos
        } catch (e: Exception) {
            Log.w(TAG, "Fallback a cache local: Supabase no respondió (getAll)", e)
            local.getAll()
        }
    }

    suspend fun insertar(tipo: TipoServicio) {
        remoto.from("tipos_servicio").upsert(tipo.aRemota())
        local.insertar(tipo)
    }
}
