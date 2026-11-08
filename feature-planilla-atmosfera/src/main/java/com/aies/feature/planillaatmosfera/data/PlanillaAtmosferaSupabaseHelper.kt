package com.aies.feature.planillaatmosfera.data

import com.aies.coredata.remote.SupabaseClientProvider
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.Serializable

/**
 * Escritura directa de `planillas_atmosfera` (padre) y `planilla_atmosfera_cisternas` (hija),
 * mismo criterio que feature-planilla-tapas.PlanillaTapasSupabaseHelper: reusa el cliente
 * autenticado de core-data y no atrapa excepciones (el llamador ya lo hace).
 */
object PlanillaAtmosferaSupabaseHelper {

    @Serializable
    private data class PlanillaInsert(
        val unidad_id: Long,
        val fecha: Long,
        val tipo_estanqueidad: Boolean,
        val tipo_integridad_dispositivos: Boolean,
        val tipo_espacios_confinados: Boolean,
        val tipo_otros: Boolean,
        val tipo_otros_detalle: String?,
        val producto_diesel: Boolean,
        val producto_nafta: Boolean,
        val producto_fame: Boolean,
        val equipo_utilizado: String?,
        val observaciones: String?,
        val firma_responsable: String?
    )

    @Serializable
    private data class PlanillaRow(val id: Long)

    @Serializable
    private data class CisternaInsert(
        val planilla_id: Long,
        val cisterna_numero: Int,
        val lel: Double?,
        val o2: Double?,
        val co: Double?,
        val h2s: Double?
    )

    suspend fun registrar(unidadId: Long, fecha: Long, r: PlanillaAtmosferaRegistro, cisternas: List<AtmosferaCisterna>) {
        val planillaId = SupabaseClientProvider.client.from("planillas_atmosfera")
            .insert(
                PlanillaInsert(
                    unidad_id = unidadId, fecha = fecha,
                    tipo_estanqueidad = r.tipoEstanqueidad,
                    tipo_integridad_dispositivos = r.tipoIntegridadDispositivos,
                    tipo_espacios_confinados = r.tipoEspaciosConfinados,
                    tipo_otros = r.tipoOtros,
                    tipo_otros_detalle = r.tipoOtrosDetalle.ifBlank { null },
                    producto_diesel = r.productoDiesel, producto_nafta = r.productoNafta, producto_fame = r.productoFame,
                    equipo_utilizado = r.equipoUtilizado.ifBlank { null },
                    observaciones = r.observaciones.ifBlank { null },
                    firma_responsable = r.firmaResponsable.ifBlank { null }
                )
            ) { select() }
            .decodeSingle<PlanillaRow>().id
        if (cisternas.isEmpty()) return
        SupabaseClientProvider.client.from("planilla_atmosfera_cisternas").insert(
            cisternas.map { CisternaInsert(planillaId, it.cisternaNumero, it.lel, it.o2, it.co, it.h2s) }
        )
    }
}
