package com.aies.feature.planillatapas.data

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
 * Refleja cada Planilla de Control de Tapas de Cisternas generada en el HistorialServicio
 * compartido de core-data, mismo patrón que feature-repuestos: una sola Unidad por registro (la
 * cisterna/semirremolque, no un par tractor+semi — la planilla es de LA CISTERNA), con auditor
 * real (viene de la sesión). La data estructurada propia de esta planilla (fabricante de tapa,
 * horario, detalle por compartimiento y checklist) NO vive acá -- ver PlanillaTapasSupabaseHelper
 * para las 3 tablas remotas nuevas (planillas_tapas y sus 2 hijas), que no tienen FK hacia
 * HistorialServicio (quedan asociadas implícitamente por unidad+fecha).
 */
object HistorialCompartidoHelper {

    const val TIPO_SERVICIO_CODIGO = "planilla_tapas"

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
        documentoUri: Uri?
    ): Long {
        if (dominio.isBlank()) return -1

        val db = AppDatabase.getInstance(context)
        val unidadDao = db.unidadDao()
        val tipoServicioDao = db.tipoServicioDao()
        val historialDao = db.historialServicioDao()

        tipoServicioDao.insertar(
            TipoServicio(codigo = TIPO_SERVICIO_CODIGO, nombre = "Planilla de Control de Tapas de Cisternas", moduloOrigen = "feature-planilla-tapas")
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
                documentoPath = documentoPath
            )
        )

        // Fase F: subida a Supabase Storage en un scope DESACOPLADO del llamador -- ver el mismo
        // comentario en feature-repuestos.HistorialCompartidoHelper. registrar() no espera a que
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
