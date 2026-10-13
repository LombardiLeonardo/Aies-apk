package com.aies.feature.certificados.data

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
 * Refleja cada certificado/acta emitido en el HistorialServicio compartido de core-data
 * (mismo patrón que feature-inspecciones.HistorialCompartidoHelper). La app original de
 * certificados no tiene concepto de "auditor" — todo se firma como AIES S.A. — por eso se
 * usa ese valor fijo por defecto. Si viene un [auditor] real (feature-sesion, ver
 * CertificadosScreen), se usa ese en su lugar — es la única forma de tener un auditor
 * verdadero acá, ya que el formulario original nunca tuvo ese campo para pedirlo standalone.
 */
object HistorialCompartidoHelper {

    private const val RESPONSABLE_TECNICO = "AIES S.A."

    /** @return el id del HistorialServicio insertado para la pata CHASIS (la unidad que el
     *  flujo unificado de sesión trata como "la unidad" del servicio), o -1 si patenteChasis
     *  vino vacía. Lo necesita el llamador para SesionServicioDao.marcarItemCompleto. */
    suspend fun registrar(
        context: Context,
        tipoServicioCodigo: String,
        nombreServicio: String,
        transportista: String,
        patenteChasis: String,
        patenteAcoplado: String,
        documentoUri: Uri?,
        auditor: String? = null,
        detalle: String? = null
    ): Long {
        val db = AppDatabase.getInstance(context)
        val unidadDao = db.unidadDao()
        val tipoServicioDao = db.tipoServicioDao()
        val historialDao = db.historialServicioDao()

        tipoServicioDao.insertar(
            TipoServicio(codigo = tipoServicioCodigo, nombre = nombreServicio, moduloOrigen = "feature-certificados")
        )

        val ahora = System.currentTimeMillis()
        val documentoPath = documentoUri?.toString() ?: ""
        val auditorFinal = auditor?.takeIf { it.isNotBlank() } ?: RESPONSABLE_TECNICO

        var historialServicioIdChasis = -1L
        val idsDelDocumento = mutableListOf<Long>()
        listOf(patenteChasis to "CHASIS", patenteAcoplado to "ACOPLADO").forEach { (placa, tipoUnidad) ->
            if (placa.isBlank()) return@forEach

            var unidad = unidadDao.getPorPlaca(placa)
            if (unidad == null) {
                unidadDao.insertar(
                    Unidad(placa = placa, tipoUnidad = tipoUnidad, cliente = transportista, fechaRegistro = ahora)
                )
                unidad = unidadDao.getPorPlaca(placa)
            }
            val unidadId = unidad?.id ?: return@forEach

            val historialId = historialDao.insertar(
                HistorialServicio(
                    unidadId = unidadId,
                    tipoServicioCodigo = tipoServicioCodigo,
                    fecha = ahora,
                    auditor = auditorFinal,
                    documentoPath = documentoPath,
                    detalle = detalle ?: ""
                )
            )
            idsDelDocumento.add(historialId)
            if (tipoUnidad == "CHASIS") historialServicioIdChasis = historialId
        }

        // Fase F: subida a Supabase Storage en un scope DESACOPLADO del llamador -- ver el mismo
        // comentario en feature-informes.HistorialCompartidoHelper. registrar() no espera a que
        // termine.
        if (documentoUri != null && idsDelDocumento.isNotEmpty()) {
            CoroutineScope(Dispatchers.IO).launch {
                val url = DocumentoStorageHelper.subirYObtenerUrlPublica(context, documentoUri, tipoServicioCodigo, "${idsDelDocumento.first()}")
                if (url != null) idsDelDocumento.forEach { id -> historialDao.actualizarDocumentoUrl(id, url) }
            }
        }
        return historialServicioIdChasis
    }
}
