package com.aies.coredata.remote

import android.content.Context
import android.net.Uri
import android.util.Log
import io.github.jan.supabase.storage.storage
import java.util.UUID
import kotlin.time.Duration.Companion.minutes

private const val TAG = "DocumentoStorageHelper"
private const val BUCKET = "documentos"

/**
 * Fase F del pase a base de datos compartida: sube a Supabase Storage (bucket "documentos")
 * cada documento que un HistorialCompartidoHelper (o "Finalizar visita" en feature-sesion) ya
 * generó y guardó localmente, para que cualquier dispositivo pueda abrirlo más tarde -- no solo
 * el que lo generó.
 *
 * SEC-003 (docs/security/AUDITORIA-2026-09-19.md): el bucket pasó de público a privado. Para no
 * tener que migrar ni una fila de `documento_url`/`nota_trabajo_url`/etc en ninguna de las ~10
 * tablas que ya tienen guardada la URL "pública" vieja (`.../object/public/documentos/...`),
 * esa URL se sigue guardando tal cual (ver [subirYObtenerUrlPublica], sin cambios) -- ahora es
 * solo un IDENTIFICADOR ESTABLE del path del archivo, no algo que se abra directo. Recién al
 * abrir el documento ([abrirDocumentoLocalOUrl]) se le extrae el path y se pide una URL firmada
 * fresca, de corta duración, con la sesión ya logueada de la app. Esto también termina de cerrar
 * el riesgo residual documentado en SEC-003 para los ~400 archivos ya subidos antes del fix de
 * nombres aleatorios: con el bucket privado, ni siquiera un nombre adivinado sirve sin una URL
 * firmada válida.
 *
 * Sintaxis de storage-kt 2.6.0 confirmada DECOMPILANDO
 * storage-kt-android-debug-2.6.0-sources.jar (BucketApi.kt): `client.storage.from(bucketId)`
 * devuelve un `BucketApi`, que expone `suspend fun upload(path: String, data: ByteArray,
 * upsert: Boolean = false): FileUploadResponse`, `fun publicUrl(path: String): String`
 * (no-suspend, arma la URL localmente sin pegarle a la red -- implementación confirmada en
 * BucketApiImpl.kt: `storage.resolveUrl("object/public/$bucketId/$path")`) y
 * `suspend fun createSignedUrl(path: String, expiresIn: Duration): String`.
 */
object DocumentoStorageHelper {

    /**
     * Lee [uri] (un content:// URI local, ya guardado por MediaStoreUtils/FileProvider en cada
     * módulo), lo sube a `documentos/$carpeta/$nombreBase-<sufijo>.<extension>` y devuelve la URL
     * pública. NUNCA tira excepción -- cualquier falla (no se pudo leer el archivo, sin conexión,
     * error de Supabase) se loguea con Log.w y devuelve null; el llamador trata null como "no se
     * pudo subir, seguimos sin URL remota" y no debe fallar por eso.
     *
     * SEC-003 (docs/security/AUDITORIA-2026-09-19.md): en TODOS los ~10 call sites del repo,
     * [nombreBase] es simplemente el id incremental de HistorialServicio (1, 2, 3...) -- con el
     * bucket público, esto hacía que cualquier documento fuera adivinable recorriendo números
     * consecutivos, sin login. Se le agrega un sufijo aleatorio (UUID) no derivable del id: la
     * URL pública deja de ser enumerable sin importar si el bucket sigue público o pasa a
     * privado. Costo aceptado: ya no hay upsert por nombre estable, así que un reintento sube un
     * objeto nuevo en vez de sobrescribir el anterior (el anterior queda huérfano en el bucket,
     * sin costo funcional -- nada lo referencia, solo espacio de Storage). El llamador siempre
     * guarda la URL más reciente devuelta acá, así que no afecta qué URL queda vigente.
     */
    suspend fun subirYObtenerUrlPublica(context: Context, uri: Uri, carpeta: String, nombreBase: String): String? {
        return try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes == null || bytes.isEmpty()) {
                Log.w(TAG, "No se pudo leer el archivo de $uri (stream nulo o vacío) -- no se sube")
                return null
            }
            val mime = context.contentResolver.getType(uri)
            val extension = when (mime) {
                "application/pdf" -> "pdf"
                "application/vnd.ms-excel" -> "xls"
                "text/csv" -> "csv"
                else -> "bin"
            }
            val sufijoAleatorio = UUID.randomUUID().toString().replace("-", "").take(12)
            val path = "$carpeta/$nombreBase-$sufijoAleatorio.$extension"
            val bucket = SupabaseClientProvider.client.storage.from(BUCKET)
            bucket.upload(path, bytes, upsert = true)
            bucket.publicUrl(path)
        } catch (e: Exception) {
            Log.w(TAG, "Falló la subida a Supabase Storage de $uri ($carpeta/$nombreBase) -- se sigue sin URL remota", e)
            null
        }
    }

    /** Extrae el path dentro del bucket de una URL "pública" ya guardada en la base, del estilo
     *  `.../storage/v1/object/public/documentos/<path>` (formato confirmado en
     *  BucketApiImpl.publicUrl, ver doc de la clase). Null si [urlRemota] no matchea ese patrón
     *  (defensivo: URLs con otro formato, de antes de este cambio de arquitectura o corruptas,
     *  no deberían romper el intento de apertura, solo hacer que se use tal cual). */
    private fun pathDesdeUrlPublica(urlRemota: String): String? {
        val marcador = "/object/public/$BUCKET/"
        val idx = urlRemota.indexOf(marcador)
        if (idx < 0) return null
        return urlRemota.substring(idx + marcador.length).ifBlank { null }
    }

    /**
     * Intenta abrir [pathLocal] (una URI local ya guardada en el dispositivo); si no existe en
     * ESTE dispositivo (falla al abrir el stream, o viene vacío) prueba con [urlRemota]. Comparte
     * esta lógica feature-nota-trabajo y feature-sesion (los dos botones "Abrir documento") --
     * vive acá en core-data porque feature-nota-trabajo deliberadamente no depende de
     * core-ui/feature-inspecciones/etc, y ambos módulos ya dependen de core-data.
     *
     * SEC-003: el bucket es privado -- [urlRemota] (la URL "pública" vieja, guardada tal cual en
     * la base, ver doc de la clase) ya no sirve para abrir el documento directo. Acá se le
     * extrae el path y se cambia por una URL firmada de corta duración (10 minutos, de sobra
     * para que la app de PDF/Excel externa termine de abrirla) pedida en el momento, con la
     * sesión ya logueada de la app -- nunca se maneja ninguna credencial acá, solo se usa el
     * cliente de Supabase ya autenticado por SupabaseClientProvider.
     *
     * @return true si se pudo lanzar un ACTION_VIEW (local o remoto), false si no había nada
     * que abrir o ambos intentos fallaron -- el llamador decide si mostrar un toast de error.
     */
    suspend fun abrirDocumentoLocalOUrl(context: Context, pathLocal: String, urlRemota: String): Boolean {
        val existeLocal = pathLocal.isNotBlank() && try {
            context.contentResolver.openInputStream(Uri.parse(pathLocal))?.use { }
            true
        } catch (e: Exception) {
            false
        }

        if (existeLocal) {
            return try {
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                    data = Uri.parse(pathLocal)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(intent)
                true
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo abrir el documento local $pathLocal", e)
                false
            }
        }

        if (urlRemota.isNotBlank()) {
            val urlParaAbrir = try {
                val path = pathDesdeUrlPublica(urlRemota)
                if (path != null) {
                    SupabaseClientProvider.client.storage.from(BUCKET).createSignedUrl(path, 10.minutes)
                } else {
                    urlRemota
                }
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo firmar la URL de $urlRemota -- se intenta abrir tal cual (probablemente falle si el bucket ya es privado)", e)
                urlRemota
            }
            return try {
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                    data = Uri.parse(urlParaAbrir)
                }
                context.startActivity(intent)
                true
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo abrir el documento remoto $urlRemota", e)
                false
            }
        }

        return false
    }
}
