package com.aies.feature.inspecciones.data

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
 * Refleja cada control finalizado en el HistorialServicio compartido de core-data,
 * tal como pide el TODO original de InspeccionesScreen ("al terminar una inspección,
 * insertar un HistorialServicio"). Se llama además de (no en reemplazo de) el guardado
 * en [InspeccionesLocalDatabase], que sigue siendo la fuente de autocompletado por
 * patente y del historial detallado para facturación de este módulo.
 *
 * Un control cubre dos unidades (tractor y semi), así que se registra un
 * HistorialServicio por cada patente no vacía. Y como `documentoPath` es una sola URI por
 * fila, un control que genera DOS documentos (el .xls del checklist Y el PDF del reporte de
 * observaciones, ver InspeccionActivity/AxionActivity) necesita DOS filas por unidad, no una
 * sola con `documentoUriPdf ?: documentoUriExcel` — eso era lo que hacía que "Finalizar visita"
 * mandara únicamente el reporte y nunca el .xls al mail consolidado (el .xls se generaba y
 * guardaba bien, solo que nunca quedaba registrado en el historial compartido). Enganche no
 * tiene este problema porque solo genera un documento (el .xls), no un PDF de reporte aparte.
 */
object HistorialCompartidoHelper {

    suspend fun registrar(
        context: Context,
        tipoInspeccion: String,
        empresa: String,
        auditor: String,
        patenteTractor: String,
        patenteSemi: String,
        documentoUriExcel: Uri?,
        documentoUriPdf: Uri?,
        detalle: String? = null
    ): Long {
        val db = AppDatabase.getInstance(context)
        val unidadDao = db.unidadDao()
        val tipoServicioDao = db.tipoServicioDao()
        val historialDao = db.historialServicioDao()

        val codigo = "insp_" + tipoInspeccion.lowercase()
        tipoServicioDao.insertar(
            TipoServicio(
                codigo = codigo,
                nombre = when (tipoInspeccion.uppercase()) {
                    "LIVIANOS" -> "Spotcheck Livianos"
                    "AXION" -> "CheckList Livianos"
                    "ENGANCHE" -> "Control de rodamientos"
                    else -> "Inspección $tipoInspeccion"
                },
                moduloOrigen = "feature-inspecciones"
            )
        )

        val ahora = System.currentTimeMillis()
        // Cada documento real (Excel y/o PDF) es una URI DISTINTA que se sube por separado a
        // Storage -- ver idsPorUri más abajo, que agrupa los ids de HistorialServicio (tractor Y
        // semi) que comparten cada una de esas URIs, para subir una sola vez por documento y
        // aplicar la misma URL pública a todas las filas que lo referencian.
        val documentos = listOfNotNull(documentoUriExcel, documentoUriPdf).ifEmpty { listOf(null) }
        val idsPorUri = mutableMapOf<Uri, MutableList<Long>>()

        var historialServicioIdSemi = -1L
        listOf(patenteTractor to "TRACTOR", patenteSemi to "SEMI").forEach { (placa, tipoUnidad) ->
            if (placa.isBlank()) return@forEach

            var unidad = unidadDao.getPorPlaca(placa)
            if (unidad == null) {
                unidadDao.insertar(
                    Unidad(placa = placa, tipoUnidad = tipoUnidad, cliente = empresa, fechaRegistro = ahora)
                )
                unidad = unidadDao.getPorPlaca(placa)
            }
            val unidadId = unidad?.id ?: return@forEach

            documentos.forEach { documentoUri ->
                val historialId = historialDao.insertar(
                    HistorialServicio(
                        unidadId = unidadId,
                        tipoServicioCodigo = codigo,
                        fecha = ahora,
                        auditor = auditor,
                        documentoPath = documentoUri?.toString() ?: "",
                        detalle = detalle ?: ""
                    )
                )
                if (documentoUri != null) idsPorUri.getOrPut(documentoUri) { mutableListOf() }.add(historialId)
                if (tipoUnidad == "SEMI") historialServicioIdSemi = historialId
            }
        }

        // Fase F: subida a Supabase Storage en un scope DESACOPLADO del llamador -- ver el mismo
        // comentario en feature-informes.HistorialCompartidoHelper. Un control con Excel Y PDF
        // sube LAS DOS URIs (una llamada por cada una, cada una con su propio nombre de archivo
        // basado en el primer id que la referencia), cada una aplicando su URL resultante solo a
        // las filas (tractor y semi) que comparten esa URI puntual. registrar() no espera a que
        // ninguna termine.
        idsPorUri.forEach { (uri, ids) ->
            CoroutineScope(Dispatchers.IO).launch {
                val url = DocumentoStorageHelper.subirYObtenerUrlPublica(context, uri, codigo, "${ids.first()}")
                if (url != null) ids.forEach { id -> historialDao.actualizarDocumentoUrl(id, url) }
            }
        }
        return historialServicioIdSemi
    }
}
