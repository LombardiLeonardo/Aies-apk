package com.aies.feature.controlesvisuales.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ControlesVisualesDao {

    @Insert
    suspend fun insertInterna(control: ControlVisualInterna): Long

    @Insert
    suspend fun insertDetalleInterna(detalles: List<DetalleCompartimentoInterna>)

    @Insert
    suspend fun insertVerificacionesInterna(verificaciones: List<VerificacionCisternaInterna>)

    @Insert
    suspend fun insertExterna(control: ControlVisualExterna): Long

    @Insert
    suspend fun insertDetalleExterna(detalles: List<DetalleCompartimentoExterna>)

    @Insert
    suspend fun insertItemsChecklistExterna(items: List<ItemChecklistExterna>)

    @Insert
    suspend fun insertEspesores(control: ControlEspesores): Long

    @Insert
    suspend fun insertDetalleEspesores(detalles: List<DetalleCompartimentoEspesores>)

    @Insert
    suspend fun insertMedicionesEspesor(mediciones: List<MedicionEspesor>)

    @Query("SELECT * FROM control_visual_interna ORDER BY fechaCreadoTimestamp DESC")
    suspend fun listarInterna(): List<ControlVisualInterna>

    @Query("SELECT * FROM control_visual_externa ORDER BY fechaCreadoTimestamp DESC")
    suspend fun listarExterna(): List<ControlVisualExterna>

    @Query("SELECT * FROM control_espesores ORDER BY fechaCreadoTimestamp DESC")
    suspend fun listarEspesores(): List<ControlEspesores>
}
