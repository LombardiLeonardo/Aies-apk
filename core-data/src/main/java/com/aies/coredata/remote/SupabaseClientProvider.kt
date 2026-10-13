package com.aies.coredata.remote

import com.aies.coredata.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.storage.Storage
import kotlinx.serialization.Serializable

private const val TAG = "SupabaseClientProvider"

/**
 * Fase C/D del pase a base de datos compartida: inicializa el cliente Supabase una sola vez
 * (URL + anon key desde BuildConfig, ver core-data/build.gradle.kts) y lo expone al resto de
 * core-data.
 */
object SupabaseClientProvider {

    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY
        ) {
            install(Auth)
            install(Postgrest)
            install(Storage)
        }
    }

    /**
     * Login automático con el usuario de servicio (una única cuenta compartida para que la app
     * se autentique como aplicación, no un login por técnico — ver credenciales en
     * local.properties/BuildConfig). Hay que llamarlo una vez al arrancar, antes de que
     * cualquier módulo intente sincronizar o leer/escribir contra Supabase (ver el
     * LaunchedEffect de Inicio en feature-sesion). Nunca tira excepción: si falla (sin conexión,
     * credenciales mal cargadas, etc.) loguea el error y devuelve false — los módulos que
     * dependen de la sesión simplemente no van a poder sincronizar hasta el próximo intento, en
     * vez de crashear la app.
     *
     * gotrue-kt persiste la sesión (incluido el refresh token) en almacenamiento local
     * (`SettingsSessionManager`, con clave derivada de la Project URL — confirmado leyendo el
     * código fuente de la librería, no solo la documentación) apenas el login es exitoso, y la
     * recarga sola la próxima vez que se inicializa un cliente Auth contra esa misma URL. Como
     * `feature-inspeccion-glp` crea su PROPIO `SupabaseClient` (con su propio `install(Auth)`)
     * apuntando por defecto a esta misma Project URL, encuentra esa sesión ya cargada sin
     * ningún cambio de su lado — `awaitInitialization()` es justamente para no consultar
     * `currentSessionOrNull()` mientras todavía está cargando esa sesión desde el storage.
     */
    suspend fun asegurarLogin(): Boolean {
        return try {
            client.auth.awaitInitialization()
            if (client.auth.currentSessionOrNull() == null) {
                client.auth.signInWith(Email) {
                    email = BuildConfig.SUPABASE_SERVICE_EMAIL
                    password = BuildConfig.SUPABASE_SERVICE_PASSWORD
                }
            }
            android.util.Log.d(TAG, "Sesión de Supabase Auth activa (${BuildConfig.SUPABASE_URL})")
            true
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Falló el login automático contra Supabase Auth -- los módulos que dependen de esta sesión no van a sincronizar hasta el próximo intento", e)
            false
        }
    }

    @Serializable
    private data class FilaTipoServicio(val codigo: String)

    /**
     * Prueba de conexión (Fase C): cuenta las filas de `tipos_servicio` en Supabase. Se compara
     * en el llamador contra las 15 que ya sembró CatalogoServiciosSeed localmente -- si el
     * esquema remoto está bien aplicado y las credenciales son correctas, tienen que coincidir.
     */
    suspend fun contarTiposServicioRemoto(): Int {
        return client.from("tipos_servicio").select().decodeList<FilaTipoServicio>().size
    }

    /**
     * Fase D paso 5: las 3 funciones RPC de Postgres que resuelven la numeración correlativa
     * compartida entre dispositivos (ya existen del lado de Supabase, no se crean acá). Cada una
     * hace un `nextval()` sobre una secuencia real y devuelve un bigint incremental -- PostgREST
     * expone el resultado de una función escalar como un valor JSON plano (no una lista), así que
     * se decodifica directo con `decodeAs<Long>()` en vez de `decodeSingle`/`decodeList` (esos son
     * para filas de tabla). Confirmado leyendo las fuentes decompiladas de postgrest-kt 2.6.0:
     * `Postgrest.rpc(function: String, method = RpcMethod.POST, request = {}): PostgrestResult`
     * (PostgrestRpc.kt) y `PostgrestResult.decodeAs<T>(): T = postgrest.serializer.decode(data)`
     * (PostgrestResult.kt) -- data es el JSON crudo del body de la respuesta.
     *
     * Sin try/catch a propósito: si la RPC falla, el llamador (que ya tiene su propio try/catch)
     * debe abortar la generación del documento sin escribir nada.
     */
    suspend fun siguienteNumeroCalibracion(): Long = client.postgrest.rpc("siguiente_numero_calibracion").decodeAs()

    suspend fun siguienteNumeroPrecinto(): Long = client.postgrest.rpc("siguiente_numero_precinto").decodeAs()

    suspend fun siguienteNumeroTapaHombre(): Long = client.postgrest.rpc("siguiente_numero_tapa_hombre").decodeAs()

    /** Numeración correlativa del "Reporte de Observaciones" (feature-inspecciones: Livianos/
     *  Pesados/Coque/Quimicos/Axion) -- para poder identificar cada hoja impresa por número y no
     *  confundir la de una unidad con la de otra. Misma RPC ya creada del lado de Supabase que
     *  las 3 de arriba. */
    suspend fun siguienteNumeroReporte(): Long = client.postgrest.rpc("siguiente_numero_reporte").decodeAs()
}
