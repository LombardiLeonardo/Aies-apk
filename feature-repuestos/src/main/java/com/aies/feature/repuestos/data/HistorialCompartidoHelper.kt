package com.aies.feature.repuestos.data

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
 * Refleja cada cambio de repuestos generado en el HistorialServicio compartido de core-data,
 * mismo patrón que feature-controles-visuales: una sola Unidad por registro (la cisterna/
 * semirremolque — dominio + tipoUnidad — no un par tractor+semi), porque el pedido es "cada
 * compartimiento DE LA CISTERNA". A diferencia de controles-visuales, acá SÍ hay un auditor
 * real (viene de la sesión), así que se persiste el que llega por parámetro en vez de un
 * responsable técnico fijo.
 */
object HistorialCompartidoHelper {

    const val TIPO_SERVICIO_CODIGO = "repuestos_cisterna"

    /** @return el id del HistorialServicio insertado, o -1 si dominio vino vacío (no se pudo
     *  resolver ninguna Unidad). Lo necesita el llamador para SesionServicioDao.marcarItemCompleto
     *  cuando la pantalla se abre en modo sesión (feature-sesion). */
    suspend fun registrar(
        context: Context,
        razonSocial: String,
        dominio: String,
        tipoUnidad: String,
        cantidadCisternas: Int,
        auditor: String,
        documentoUri: Uri?,
        detalle: String? = null
    ): Long {
        if (dominio.isBlank()) return -1

        val db = AppDatabase.getInstance(context)
        val unidadDao = db.unidadDao()
        val tipoServicioDao = db.tipoServicioDao()
        val historialDao = db.historialServicioDao()

        tipoServicioDao.insertar(
            TipoServicio(codigo = TIPO_SERVICIO_CODIGO, nombre = "Cambio de Repuestos", moduloOrigen = "feature-repuestos")
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
                tipoServicioCodigo = TIPO_SERVICIO_CODIGO,
                fecha = ahora,
                auditor = auditor.ifBlank { "-" },
                documentoPath = documentoPath,
                detalle = detalle ?: ""
            )
        )

        // Fase F: subida a Supabase Storage en un scope DESACOPLADO del llamador -- ver el mismo
        // comentario en feature-informes.HistorialCompartidoHelper. registrar() no espera a que
        // termine.
        if (documentoUri != null) {
            CoroutineScope(Dispatchers.IO).launch {
                val url = DocumentoStorageHelper.subirYObtenerUrlPublica(context, documentoUri, TIPO_SERVICIO_CODIGO, "$historialId")
                if (url != null) historialDao.actualizarDocumentoUrl(historialId, url)
            }
        }
        return historialId
    }
}
