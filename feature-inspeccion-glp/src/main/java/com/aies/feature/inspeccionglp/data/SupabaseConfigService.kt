package com.aies.feature.inspeccionglp.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.inspeccionGlpDataStore by preferencesDataStore(name = "inspeccion_glp_config")

/**
 * Equivalente de `SupabaseConfigService.cs`: URL en DataStore, anon key en almacenamiento cifrado.
 *
 * Fase D del pase a base de datos compartida: el valor por defecto/precargado ya no es vacío —
 * si no hay nada guardado explícitamente, [obtener] cae al mismo proyecto Supabase que ya usa
 * `core-data` desde la Fase C (`BuildConfig.SUPABASE_URL`/`SUPABASE_ANON_KEY`). La pantalla de
 * configuración sigue existiendo tal cual, por si hace falta apuntar a otro proyecto a futuro —
 * simplemente ya no arranca en blanco.
 */
object SupabaseConfigService {

    private val CLAVE_URL = stringPreferencesKey("supabase_url")
    private const val CLAVE_ANON_KEY = "supabase_anon_key"

    suspend fun guardar(context: Context, url: String, anonKey: String) {
        context.inspeccionGlpDataStore.edit { it[CLAVE_URL] = url }
        SecureStorage.get(context).edit().putString(CLAVE_ANON_KEY, anonKey).apply()
    }

    suspend fun obtener(context: Context): Pair<String, String> {
        val url = context.inspeccionGlpDataStore.data.first()[CLAVE_URL] ?: ""
        val anonKey = SecureStorage.get(context).getString(CLAVE_ANON_KEY, "") ?: ""
        if (url.isNotBlank() && anonKey.isNotBlank()) return url to anonKey
        return com.aies.coredata.BuildConfig.SUPABASE_URL to com.aies.coredata.BuildConfig.SUPABASE_ANON_KEY
    }

    // Siempre true: aunque nunca se haya guardado nada explícito, [obtener] ya devuelve un
    // proyecto válido por defecto.
    suspend fun estaConfigurado(context: Context): Boolean = true
}
