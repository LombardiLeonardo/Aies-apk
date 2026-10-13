package com.aies.feature.controlesvisuales.data

import android.content.Context
import android.net.Uri
import com.aies.coredata.AppDatabase
import com.aies.coredata.entities.HistorialServicio
import com.aies.coredata.entities.TipoServicio
import com.aies.coredata.entities.Unidad
import com.aies.coredata.remote.DocumentoStorageHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Refleja cada control generado en el HistorialServicio compartido de core-data (mismo
 * patrón que los demás módulos). A diferencia de feature-informes/feature-inspecciones, acá
 * hay una sola unidad por control (dominio + tipo Semi/Acoplado/Chasis), no un par
 * tractor+semi, así que se registra una única Unidad. Las 3 planillas no tienen un campo de
 * "responsable"/auditor en el encabezado, así que se usa el mismo valor fijo que
 * feature-certificados/feature-informes. Se inserta un único HistorialServicio por control
 * generado (no uno por cisterna/compartimento).
 */
object HistorialCompartidoHelper {

    private const val RESPONSABLE_TECNICO = "AIES S.A."

    const val TIPO_CONTROL_VISUAL_INTERNA = "control_visual_interna"
    const val TIPO_CONTROL_VISUAL_EXTERNA = "control_visual_externa"
    const val TIPO_CONTROL_ESPESORES = "control_espesores"

    /** @return el id del HistorialServicio insertado, o -1 si dominio vino vacío (no se pudo
     *  resolver ninguna Unidad). Lo necesita el llamador para SesionServicioDao.marcarItemCompleto
     *  cuando la pantalla se abre en modo sesión (feature-sesion). */
    suspend fun registrar(
        context: Context,
        tipoServicioCodigo: String,
        nombreServicio: String,
        razonSocial: String,
        dominio: String,
        tipoUnidad: String,
        cantidadCisternas: Int,
        documentoUri: Uri?
    ): Long {
        if (dominio.isBlank()) return -1

        val db = AppDatabase.getInstance(context)
        val unidadDao = db.unidadDao()
        val tipoServicioDao = db.tipoServicioDao()
        val historialDao = db.historialServicioDao()

        tipoServicioDao.insertar(
            TipoServicio(codigo = tipoServicioCodigo, nombre = nombreServicio, moduloOrigen = "feature-controles-visuales")
        )

        val ahora = System.currentTimeMillis()
        val documentoPath = documentoUri?.toString() ?: ""

        var unidad = unidadDao.getPorPlaca(dominio)
        if (unidad == null) {
            unidadDao.insertar(
                Unidad(placa = dominio, tipoUnidad = tipoUnidad, cliente = razonSocial, fechaRegistro = ahora, cantidadCisternas = cantidadCisternas)
            )
            unidad = unidadDao.getPorPlaca(dominio)
        }
        val unidadId = unidad?.id ?: return -1

        val historialId = historialDao.insertar(
            HistorialServicio(
                unidadId = unidadId,
                tipoServicioCodigo = tipoServicioCodigo,
                fecha = ahora,
                auditor = RESPONSABLE_TECNICO,
                documentoPath = documentoPath
            )
        )

        // Fase F: subida a Supabase Storage en un scope DESACOPLADO del llamador -- ver el mismo
        // comentario en feature-informes.HistorialCompartidoHelper. registrar() no espera a que
        // termine.
        if (documentoUri != null) {
            CoroutineScope(Dispatchers.IO).launch {
                val url = DocumentoStorageHelper.subirYObtenerUrlPublica(context, documentoUri, tipoServicioCodigo, "$historialId")
                if (url != null) historialDao.actualizarDocumentoUrl(historialId, url)
            }
        }
        return historialId
    }
}
