package com.aies.feature.planillatapas.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface PlanillaTapasDao {

    @Insert
    suspend fun insertRegistro(registro: PlanillaTapasRegistro): Long

    @Insert
    suspend fun insertDetalles(detalles: List<DetalleCompartimentoTapas>)

    @Insert
    suspend fun insertItemsChecklist(items: List<ItemChecklistTapas>)

    @Query("SELECT * FROM planilla_tapas_registro ORDER BY fechaCreadoTimestamp DESC LIMIT :limite")
    suspend fun listarUltimos(limite: Int = 20): List<PlanillaTapasRegistro>

    @Query("SELECT * FROM detalle_compartimento_tapas WHERE registroId = :registroId ORDER BY cisternaNumero ASC")
    suspend fun listarDetalles(registroId: Long): List<DetalleCompartimentoTapas>

    @Query("SELECT * FROM item_checklist_tapas WHERE registroId = :registroId ORDER BY orden ASC")
    suspend fun listarItemsChecklist(registroId: Long): List<ItemChecklistTapas>
}
