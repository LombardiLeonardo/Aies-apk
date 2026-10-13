package com.aies.feature.calibrado.data

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
 * Refleja cada certificado de calibración generado en el HistorialServicio compartido de
 * core-data, con la misma pareja TRACTOR+SEMI que feature-informes (una calibración
 * cubre a ambas unidades). El certificado lo firma un responsable técnico fijo
 * ("Lic. Pablo González, Director Técnico"), no hay auditor por-inspección como en otros
 * módulos, así que se usa ese valor fijo como auditor (mismo criterio que
 * feature-informes/feature-certificados con "AIES S.A.").
 */
object HistorialCompartidoHelper {

    private const val RESPONSABLE_TECNICO = "Lic. Pablo González"
    const val TIPO_SERVICIO_CODIGO = "calibracion_unidad"

    /**
     * @return el id del HistorialServicio insertado para la pata SEMI (la Unidad que el flujo
     * unificado de sesión trata como "la unidad" del servicio) — o -1 si patenteSemi vino vacía.
     * Lo necesita el llamador para SesionServicioDao.marcarItemCompleto cuando esta pantalla se
     * abre en modo sesión.
     */
    suspend fun registrar(
        context: Context,
        cliente: String,
        patenteTractor: String,
        patenteSemi: String,
        documentoUri: Uri?
    ): Long {
        val db = AppDatabase.getInstance(context)
        val unidadDao = db.unidadDao()
        val tipoServicioDao = db.tipoServicioDao()
        val historialDao = db.historialServicioDao()

        tipoServicioDao.insertar(
            TipoServicio(codigo = TIPO_SERVICIO_CODIGO, nombre = "Calibración de Unidad", moduloOrigen = "feature-calibrado")
        )

        val ahora = System.currentTimeMillis()
        val documentoPath = documentoUri?.toString() ?: ""

        var historialServicioIdSemi = -1L
        val idsDelDocumento = mutableListOf<Long>()
        listOf(patenteTractor to "TRACTOR", patenteSemi to "SEMI").forEach { (placa, tipoUnidad) ->
            if (placa.isBlank()) return@forEach

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
                    auditor = RESPONSABLE_TECNICO,
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
