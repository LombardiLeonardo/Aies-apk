package com.aies.feature.inspeccionglp.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.smtpDataStore by preferencesDataStore(name = "inspeccion_glp_smtp_config")

data class ConfiguracionSmtp(
    val host: String,
    val puerto: Int,
    val usuario: String,
    val contraseña: String,
    val usarSsl: Boolean,
    val destinatario1: String,
    val destinatario2: String,
    val destinatario3: String
) {
    /** Los destinatarios pregrabados que efectivamente tienen algo cargado. */
    val destinatariosConfigurados: List<String>
        get() = listOf(destinatario1, destinatario2, destinatario3)
            .filter { it.isNotBlank() }
            .map { it.trim() }
}

/** Equivalente de `SmtpConfigService.cs`: todo en DataStore salvo la contraseña, en almacenamiento cifrado. */
object SmtpConfigService {

    private val CLAVE_HOST = stringPreferencesKey("smtp_host")
    private val CLAVE_PUERTO = intPreferencesKey("smtp_puerto")
    private val CLAVE_USUARIO = stringPreferencesKey("smtp_usuario")
    private val CLAVE_SSL = booleanPreferencesKey("smtp_ssl")
    private val CLAVE_DEST1 = stringPreferencesKey("smtp_destinatario1")
    private val CLAVE_DEST2 = stringPreferencesKey("smtp_destinatario2")
    private val CLAVE_DEST3 = stringPreferencesKey("smtp_destinatario3")
    private const val CLAVE_CONTRASEÑA = "smtp_password"

    suspend fun guardar(
        context: Context,
        host: String, puerto: Int, usuario: String, contraseña: String, usarSsl: Boolean,
        destinatario1: String, destinatario2: String, destinatario3: String
    ) {
        context.smtpDataStore.edit {
            it[CLAVE_HOST] = host
            it[CLAVE_PUERTO] = puerto
            it[CLAVE_USUARIO] = usuario
            it[CLAVE_SSL] = usarSsl
            it[CLAVE_DEST1] = destinatario1
            it[CLAVE_DEST2] = destinatario2
            it[CLAVE_DEST3] = destinatario3
        }
        SecureStorage.get(context).edit().putString(CLAVE_CONTRASEÑA, contraseña).apply()
    }

    suspend fun obtener(context: Context): ConfiguracionSmtp {
        val prefs = context.smtpDataStore.data.first()
        val contraseña = SecureStorage.get(context).getString(CLAVE_CONTRASEÑA, "") ?: ""
        return ConfiguracionSmtp(
            host = prefs[CLAVE_HOST] ?: "",
            puerto = prefs[CLAVE_PUERTO] ?: 587,
            usuario = prefs[CLAVE_USUARIO] ?: "",
            contraseña = contraseña,
            usarSsl = prefs[CLAVE_SSL] ?: true,
            destinatario1 = prefs[CLAVE_DEST1] ?: "",
            destinatario2 = prefs[CLAVE_DEST2] ?: "",
            destinatario3 = prefs[CLAVE_DEST3] ?: ""
        )
    }

    suspend fun estaConfigurado(context: Context): Boolean =
        (context.smtpDataStore.data.first()[CLAVE_HOST] ?: "").isNotBlank()
}
