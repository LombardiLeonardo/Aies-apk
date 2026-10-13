package com.aies.feature.inspeccionglp.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

/**
 * Fotos y documentos generados quedan en almacenamiento interno de la app (cache), y se
 * exponen a la cámara/al share sheet vía el FileProvider único de :app (mismo patrón que
 * feature-inspeccion-ejes.util.ArchivoUtils — cada módulo tiene su propia copia de este
 * helper porque el authority es único a nivel app pero los directorios son por módulo).
 */
object ArchivoUtils {

    private fun authority(context: Context) = "${context.packageName}.fileprovider"

    fun crearArchivoFoto(context: Context): File {
        val carpeta = File(context.cacheDir, "fotos").apply { mkdirs() }
        return File(carpeta, "${UUID.randomUUID()}.jpg")
    }

    fun crearArchivoInforme(context: Context, numeroInforme: String): File {
        val carpeta = File(context.cacheDir, "informes").apply { mkdirs() }
        val limpio = numeroInforme.replace(Regex("[^A-Za-z0-9_\\-]"), "_")
        val archivo = File(carpeta, "$limpio.docx")
        if (archivo.exists()) archivo.delete()
        return archivo
    }

    fun uriParaArchivo(context: Context, archivo: File) =
        FileProvider.getUriForFile(context, authority(context), archivo)

    fun compartirDocumento(context: Context, archivo: File, mimeType: String, titulo: String) {
        val uri = uriParaArchivo(context, archivo)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, titulo).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    fun abrirDocumento(context: Context, archivo: File, mimeType: String) {
        val uri = uriParaArchivo(context, archivo)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
