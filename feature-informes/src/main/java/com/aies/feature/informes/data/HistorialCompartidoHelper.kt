package com.aies.feature.informes.data

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
 * Refleja cada informe de carga segura generado en el HistorialServicio compartido de
 * core-data (mismo patrón que feature-inspecciones y feature-certificados). El
 * "responsable técnico"/auditor tampoco existe como campo en la app Flask original
 * (el informe lo firma AIES S.A.), así que se usa el mismo valor fijo que en certificados.
 */
object HistorialCompartidoHelper {

    private const val RESPONSABLE_TECNICO = "AIES S.A."
    const val TIPO_SERVICIO_CODIGO = "informe_carga_segura"

    /** @return el id del HistorialServicio insertado para la pata SEMI (la Unidad que el flujo
     *  unificado de sesión trata como "la unidad" del servicio), o -1 si patenteSemirremolque
     *  vino vacía. Lo necesita el llamador para SesionServicioDao.marcarItemCompleto. */
    suspend fun registrar(
        context: Context,
        transportista: String,
        patenteTractor: String,
        patenteSemirremolque: String,
        documentoUri: Uri?
    ): Long {
        val db = AppDatabase.getInstance(context)
        val unidadDao = db.unidadDao()
        val tipoServicioDao = db.tipoServicioDao()
        val historialDao = db.historialServicioDao()

        tipoServicioDao.insertar(
            TipoServicio(codigo = TIPO_SERVICIO_CODIGO, nombre = "Informe de Carga Segura", moduloOrigen = "feature-informes")
        )

        val ahora = System.currentTimeMillis()
        val documentoPath = documentoUri?.toString() ?: ""

        var historialServicioIdSemi = -1L
        val idsDelDocumento = mutableListOf<Long>()
        listOf(patenteTractor to "TRACTOR", patenteSemirremolque to "SEMI").forEach { (placa, tipoUnidad) ->
            if (placa.isBlank()) return@forEach

            var unidad = unidadDao.getPorPlaca(placa)
            if (unidad == null) {
                unidadDao.insertar(Unidad(placa = placa, tipoUnidad = tipoUnidad, cliente = transportista, fechaRegistro = ahora))
                unidad = unidadDao.getPorPlaca(placa)
            }
            val unidadId = unidad?.id ?: return@forEach

            val historialId = historialDao.insertar(
                HistorialServicio(
                    unidadId = unidadId,
                    tipoServicioCodigo = TIPO_SERVICIO_CODIGO,
                    fecha = ahora,
                    auditor = RESPONSABLE_TECNICO,
                    documentoPath = documentoPath
                )
            )
            idsDelDocumento.add(historialId)
            if (tipoUnidad == "SEMI") historialServicioIdSemi = historialId
        }

        // Fase F: subida a Supabase Storage en un scope DESACOPLADO del llamador -- no se usa el
        // scope/lifecycleScope de la Activity/Composable que invoca registrar(), porque ese scope
        // puede cancelarse en cuanto la pantalla navega o la Activity hace finish(), cortando la
        // subida a mitad de camino. registrar() sigue devolviendo el id de siempre SIN esperar a
        // que la subida termine -- sigue corriendo de fondo después de que esta función ya retornó.
        if (documentoUri != null && idsDelDocumento.isNotEmpty()) {
            CoroutineScope(Dispatchers.IO).launch {
                val url = DocumentoStorageHelper.subirYObtenerUrlPublica(context, documentoUri, TIPO_SERVICIO_CODIGO, "${idsDelDocumento.first()}")
                if (url != null) idsDelDocumento.forEach { id -> historialDao.actualizarDocumentoUrl(id, url) }
            }
        }
        return historialServicioIdSemi
    }
}
