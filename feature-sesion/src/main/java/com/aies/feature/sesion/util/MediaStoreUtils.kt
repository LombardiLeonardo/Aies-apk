package com.aies.feature.sesion.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.IOException
import java.io.OutputStream

/** Guarda un archivo en Descargas usando MediaStore y devuelve su Uri (igual criterio que los otros feature modules). */
object MediaStoreUtils {

    fun guardarEnDescargas(
        context: Context,
        nombreArchivo: String,
        mimeType: String,
        escribir: (OutputStream) -> Unit
    ): Uri? {
        val valores = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, nombreArchivo)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
        }
        val coleccion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            MediaStore.Downloads.EXTERNAL_CONTENT_URI
        else MediaStore.Files.getContentUri("external")

        val uri = context.contentResolver.insert(coleccion, valores) ?: return null
        try {
            val stream = context.contentResolver.openOutputStream(uri)
                ?: throw IOException("openOutputStream devolvió null para $uri -- MediaStore no pudo abrir el archivo recién insertado")
            stream.use(escribir)
        } catch (e: Exception) {
            // Sin este catch, un openOutputStream() que devuelve null dejaba una fila de
            // MediaStore ya insertada pero sin contenido (0 bytes) -- el llamador la trataba como
            // éxito (URI no nula), la guardaba en HistorialServicio.documentoPath y mostraba
            // "generado", pero el archivo real nunca se escribió: no aparece bien en Descargas y
            // falla o se adjunta vacío al mail consolidado de "Finalizar visita". Se borra la fila
            // fantasma y se propaga el error para que el try/catch del llamador SÍ le avise al
            // técnico con un Toast, en vez de reportar éxito con un documento que no existe.
            try { context.contentResolver.delete(uri, null, null) } catch (_: Exception) { /* mejor esfuerzo */ }
            throw e
        }
        return uri
    }
}
