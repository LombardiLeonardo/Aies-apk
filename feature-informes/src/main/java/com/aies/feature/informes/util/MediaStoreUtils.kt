package com.aies.feature.informes.util

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
            // Ver el mismo comentario en feature-sesion.util.MediaStoreUtils -- sin este catch, un
            // openOutputStream() que devuelve null dejaba una fila de MediaStore ya insertada
            // pero sin contenido (0 bytes), tratada como éxito río abajo.
            try { context.contentResolver.delete(uri, null, null) } catch (_: Exception) { /* mejor esfuerzo */ }
            throw e
        }
        return uri
    }
}
