package com.aies.feature.inspeccionglp.data

import android.content.Context
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
// El módulo Maven se llama "gotrue-kt" a esta versión (2.6.0) pero la clase/paquete público
// interno ya es `Auth` en `io.github.jan.supabase.gotrue` — ver justificación de versión en
// build.gradle.kts.
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage

/**
 * Equivalente de `SupabaseService.cs`: crea el cliente con la URL/anon key guardadas
 * (`SupabaseConfigService`) e instala Auth/Postgrest/Storage. Hay que llamar a
 * `inicializar()` antes de usar `cliente`.
 */
object SupabaseService {

    private var _cliente: SupabaseClient? = null

    val cliente: SupabaseClient
        get() = _cliente ?: throw IllegalStateException("Supabase no está inicializado. Llamá a inicializar primero.")

    val estaInicializado: Boolean
        get() = _cliente != null

    fun haySesionActiva(): Boolean = _cliente?.auth?.currentSessionOrNull() != null

    fun emailUsuarioActual(): String? = _cliente?.auth?.currentUserOrNull()?.email

    suspend fun inicializar(context: Context): Boolean {
        if (_cliente != null) return true
        if (!SupabaseConfigService.estaConfigurado(context)) return false

        val (url, anonKey) = SupabaseConfigService.obtener(context)
        if (url.isBlank() || anonKey.isBlank()) return false

        _cliente = createSupabaseClient(supabaseUrl = url, supabaseKey = anonKey) {
            install(Auth)
            install(Postgrest)
            install(Storage)
        }
        // Verificación Fase D (login automático a nivel de toda la app, ver
        // SupabaseClientProvider en core-data): este cliente es una instancia separada de la de
        // core-data, pero apunta a la misma Project URL y no customiza sessionManager, así que
        // gotrue-kt debería encontrar la sesión ya persistida sin loguearse de nuevo acá.
        // awaitInitialization() solo evita loguear un falso "sin sesión" mientras gotrue-kt
        // todavía está leyendo el storage -- no cambia qué devuelve haySesionActiva() en sí.
        _cliente?.auth?.awaitInitialization()
        android.util.Log.d("SupabaseServiceGlp", "Cliente inicializado, haySesionActiva=${haySesionActiva()}")
        return true
    }

    suspend fun iniciarSesion(email: String, contraseña: String) {
        val c = cliente
        c.auth.signInWith(Email) {
            this.email = email
            this.password = contraseña
        }
    }

    suspend fun cerrarSesion() {
        _cliente?.auth?.signOut()
    }

    /** Solo para pruebas/reinicio manual — vuelve a exigir `inicializar()` con la config guardada. */
    fun reiniciar() {
        _cliente = null
    }
}
