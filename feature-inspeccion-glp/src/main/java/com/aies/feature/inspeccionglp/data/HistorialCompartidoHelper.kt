package com.aies.feature.inspeccionglp.data

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
 * Refleja cada informe GLP enviado (o generado sin poder enviarse) en el HistorialServicio
 * compartido de core-data (mismo patrón que feature-certificados/feature-informes). El
 * original no tiene un campo de auditor/usuario por informe, así que se usa el mismo valor
 * fijo "AIES S.A." que esos otros módulos.
 */
object HistorialCompartidoHelper {

    const val TIPO_SERVICIO_CODIGO = "inspeccion_glp"
    private const val RESPONSABLE_TECNICO = "AIES S.A."

    /** @return el id del HistorialServicio insertado para la pata TRACTOR — a diferencia de
     *  feature-calibrado/feature-informes/feature-inspeccion-ejes (que devuelven la pata SEMI),
     *  acá el `Vehiculo` local de GLP es la unidad que el flujo unificado de sesión resuelve
     *  como "la unidad" del servicio, y esa unidad se precarga sobre `patenteTractor` (ver
     *  InspeccionGlpScreen) — no hay una "SEMI" real proveniente de la sesión. Devuelve -1 si
     *  patenteTractor vino vacía. Lo necesita el llamador para
     *  SesionServicioDao.marcarItemCompleto cuando esta pantalla se abre en modo sesión. */
    suspend fun registrar(
        context: Context,
        cliente: String,
        patenteTractor: String?,
        patenteSemirremolque: String?,
        documentoUri: Uri?
    ): Long {
        val db = AppDatabase.getInstance(context)
        val unidadDao = db.unidadDao()
        val tipoServicioDao = db.tipoServicioDao()
        val historialDao = db.historialServicioDao()

        tipoServicioDao.insertar(
            TipoServicio(codigo = TIPO_SERVICIO_CODIGO, nombre = "Inspección GLP", moduloOrigen = "feature-inspeccion-glp")
        )

        val ahora = System.currentTimeMillis()
        val documentoPath = documentoUri?.toString() ?: ""

        var historialServicioIdTractor = -1L
        val idsDelDocumento = mutableListOf<Long>()
        listOf(patenteTractor to "TRACTOR", patenteSemirremolque to "SEMI").forEach { (placa, tipoUnidad) ->
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
                    auditor = RESPONSABLE_TECNICO,
                    documentoPath = documentoPath
                )
            )
            idsDelDocumento.add(historialId)
            if (tipoUnidad == "TRACTOR") historialServicioIdTractor = historialId
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
        return historialServicioIdTractor
    }
}
