package com.aies.feature.planillatapas.data

import com.aies.coredata.remote.SupabaseClientProvider
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.Serializable

/**
 * Escritura directa (sin DAO en core-data) de las 3 tablas nuevas de Supabase que respaldan la
 * planilla R 11-01: `planillas_tapas` (padre) y sus 2 hijas `planilla_tapas_compartimentos` /
 * `planilla_tapas_items`. Reusa el cliente singleton ya autenticado
 * [SupabaseClientProvider.client] -- NO crea un SupabaseClient propio (a diferencia de
 * feature-inspeccion-glp). Esquema confirmado por curl contra el proyecto real:
 * - planillas_tapas: id (identity), unidad_id, fecha, fabricante_tapa_ph, hora_inicio, hora_fin.
 *   Sin columna `auditor` ni `documento_path`/`documento_url` propios (esos viven en
 *   HistorialServicio, ver HistorialCompartidoHelper) y sin FK hacia historial_servicios --
 *   quedan asociadas implícitamente por unidad+fecha.
 * - planilla_tapas_compartimentos: id (identity), planilla_id (FK), cisterna_numero,
 *   ultimo_producto_transportado, medicion_gases_lel.
 * - planilla_tapas_items: id (identity), planilla_id (FK), item_codigo, cumple, observacion.
 *
 * Sin try/catch propio a propósito -- el llamador (PlanillaTapasScreen.generarPdf) ya envuelve
 * toda la generación en un try/catch, mismo criterio que el resto del repo.
 */
object PlanillaTapasSupabaseHelper {

    @Serializable
    private data class PlanillaTapaInsert(
        val unidad_id: Long,
        val fecha: Long,
        val fabricante_tapa_ph: String?,
        val hora_inicio: Long?,
        val hora_fin: Long?
    )

    @Serializable
    private data class PlanillaTapaRow(val id: Long)

    @Serializable
    private data class CompartimentoInsert(
        val planilla_id: Long,
        val cisterna_numero: Int,
        val ultimo_producto_transportado: String?,
        val medicion_gases_lel: Double?
    )

    @Serializable
    private data class ItemInsert(
        val planilla_id: Long,
        val item_codigo: String,
        val cumple: Boolean,
        val observacion: String?
    )

    /**
     * Inserta la fila padre en `planillas_tapas` y devuelve su id real (necesario como
     * `planilla_id` para las 2 tablas hijas).
     */
    private suspend fun insertarPlanilla(
        unidadId: Long,
        fecha: Long,
        fabricanteTapaPh: String,
        horaInicio: Long?,
        horaFin: Long?
    ): Long {
        val fila = PlanillaTapaInsert(
            unidad_id = unidadId, fecha = fecha,
            fabricante_tapa_ph = fabricanteTapaPh.ifBlank { null },
            hora_inicio = horaInicio, hora_fin = horaFin
        )
        return SupabaseClientProvider.client.from("planillas_tapas")
            .insert(fila) { select() }
            .decodeSingle<PlanillaTapaRow>()
            .id
    }

    private suspend fun insertarCompartimentos(planillaId: Long, detalles: List<DetalleCompartimentoTapas>) {
        if (detalles.isEmpty()) return
        val filas = detalles.map {
            CompartimentoInsert(
                planilla_id = planillaId, cisterna_numero = it.cisternaNumero,
                ultimo_producto_transportado = it.ultimoProductoTransportado.ifBlank { null },
                medicion_gases_lel = it.medicionGasesLel
            )
        }
        SupabaseClientProvider.client.from("planilla_tapas_compartimentos").insert(filas)
    }

    private suspend fun insertarItems(planillaId: Long, items: List<ItemChecklistTapas>) {
        if (items.isEmpty()) return
        val filas = items.map {
            ItemInsert(
                planilla_id = planillaId, item_codigo = it.codigo,
                cumple = it.cumple, observacion = it.observacion.ifBlank { null }
            )
        }
        SupabaseClientProvider.client.from("planilla_tapas_items").insert(filas)
    }

    /** Escribe las 3 tablas en orden (padre primero, para tener el id real antes de las hijas). */
    suspend fun registrar(
        unidadId: Long,
        fecha: Long,
        fabricanteTapaPh: String,
        horaInicio: Long?,
        horaFin: Long?,
        detalles: List<DetalleCompartimentoTapas>,
        items: List<ItemChecklistTapas>
    ) {
        val planillaId = insertarPlanilla(unidadId, fecha, fabricanteTapaPh, horaInicio, horaFin)
        insertarCompartimentos(planillaId, detalles)
        insertarItems(planillaId, items)
    }
}
