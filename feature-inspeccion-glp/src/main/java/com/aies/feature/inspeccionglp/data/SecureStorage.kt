package com.aies.feature.inspeccionglp.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Almacén cifrado compartido por SupabaseConfigService (anon key) y SmtpConfigService
 * (contraseña) — equivalente Android de `SecureStorage.Default` del original. Los valores
 * no sensibles (URL, host, puerto, destinatarios) van en DataStore por separado.
 */
internal object SecureStorage {

    private const val ARCHIVO = "inspeccion_glp_secrets"

    /**
     * Bug real encontrado en logcat: `EncryptedSharedPreferences.create()` tira
     * `AEADBadTagException`/`KeyStoreException: Signature/MAC verification failed` sin este
     * catch, cuando la clave de Android Keystore que cifró `inspeccion_glp_secrets` ya no
     * coincide con la que hay ahora (pasa sobre todo tras desinstalar y reinstalar la app: la
     * Keystore se regenera, pero puede quedar dando vueltas un archivo cifrado con la clave
     * vieja). Sin atrapar esto, cualquier pantalla que llame a `get()` -- config de Supabase o
     * de SMTP -- crasheaba la app entera en vez de simplemente no encontrar nada guardado. Se
     * borra el archivo corrupto y se crea uno nuevo vacío: el usuario solo tiene que volver a
     * cargar esa configuración una vez.
     */
    fun get(context: Context): SharedPreferences {
        return try {
            crear(context)
        } catch (e: Exception) {
            context.applicationContext.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE).edit().clear().apply()
            crear(context)
        }
    }

    private fun crear(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context.applicationContext,
            ARCHIVO,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
}
