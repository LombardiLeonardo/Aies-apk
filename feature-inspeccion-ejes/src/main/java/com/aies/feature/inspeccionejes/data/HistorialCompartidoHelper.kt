package com.aies.feature.inspeccionejes.data

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
 * Refleja cada control finalizado en el HistorialServicio compartido de core-data (mismo
 * patrón que feature-certificados/feature-informes). A diferencia de esos módulos, acá el
 * `Control.Responsable` es un campo real del formulario original (el auditor que hizo la
 * inspección), así que se usa ese valor en vez de un "AIES S.A." fijo.
 */
object HistorialCompartidoHelper {

    const val TIPO_SERVICIO_CODIGO = "control_ejes"

    /** @return el id del HistorialServicio insertado para la pata SEMI (la Unidad que el flujo
     *  unificado de sesión trata como "la unidad" del servicio, mismo criterio que
     *  feature-calibrado/feature-informes), o -1 si dominioSemirremolque vino vacío. Lo necesita
     *  el llamador para SesionServicioDao.marcarItemCompleto cuando esta pantalla se abre en
     *  modo sesión. */
    suspend fun registrar(
        context: Context,
        cliente: String,
        dominioTractor: String?,
        dominioSemirremolque: String?,
        responsable: String?,
        documentoUri: Uri?
    ): Long {
        val db = AppDatabase.getInstance(context)
        val unidadDao = db.unidadDao()
        val tipoServicioDao = db.tipoServicioDao()
        val historialDao = db.historialServicioDao()

        tipoServicioDao.insertar(
            TipoServicio(codigo = TIPO_SERVICIO_CODIGO, nombre = "Control de Puntas de Eje", moduloOrigen = "feature-inspeccion-ejes")
        )

        val ahora = System.currentTimeMillis()
        val documentoPath = documentoUri?.toString() ?: ""
        val auditor = responsable?.takeIf { it.isNotBlank() } ?: "-"

        var historialServicioIdSemi = -1L
        val idsDelDocumento = mutableListOf<Long>()
        listOf(dominioTractor to "TRACTOR", dominioSemirremolque to "SEMI").forEach { (placa, tipoUnidad) ->
            if (placa.isNullOrBlank()) return@forEach

            var unidad = unidadDao.getPorPlaca(placa)
            if (unidad == null) {
                unidadDao.insertar(Unidad(placa = placa, tipoUnidad = tipoUnidad, cliente = cliente, fechaRegistro = ahora))
                unidad = unidadDao.getPorPlaca(placa)
            }
            val unidadId = unidad?.id ?: return@forEach

            val historialId = historialDao.insertar(
                HistorialServicio(
                    unidadId = unidadId,
                    tipoServicioCodigo = TIPO_SERVICIO_CODIGO,
                    fecha = ahora,
                    auditor = auditor,
                    documentoPath = documentoPath
                )
            )
            idsDelDocumento.add(historialId)
            if (tipoUnidad == "SEMI") historialServicioIdSemi = historialId
        }

        // Fase F: subida a Supabase Storage en un scope DESACOPLADO del llamador -- ver el mismo
        // comentario en feature-informes.HistorialCompartidoHelper. registrar() no espera a que
        // termine.
        if (documentoUri != null && idsDelDocumento.isNotEmpty()) {
            CoroutineScope(Dispatchers.IO).launch {
                val url = DocumentoStorageHelper.subirYObtenerUrlPublica(context, documentoUri, TIPO_SERVICIO_CODIGO, "${idsDelDocumento.first()}")
                if (url != null) idsDelDocumento.forEach { id -> historialDao.actualizarDocumentoUrl(id, url) }
            }
        }
        return historialServicioIdSemi
    }
}
