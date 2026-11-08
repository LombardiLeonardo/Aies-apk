package com.aies.feature.planillaatmosfera.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface PlanillaAtmosferaDao {

    @Insert
    suspend fun insertRegistro(registro: PlanillaAtmosferaRegistro): Long

    @Insert
    suspend fun insertCisternas(cisternas: List<AtmosferaCisterna>)

    @Query("SELECT * FROM planilla_atmosfera_registro ORDER BY fechaCreadoTimestamp DESC LIMIT :limite")
    suspend fun listarUltimos(limite: Int = 20): List<PlanillaAtmosferaRegistro>

    @Query("SELECT * FROM atmosfera_cisterna WHERE registroId = :registroId ORDER BY cisternaNumero ASC")
    suspend fun listarCisternas(registroId: Long): List<AtmosferaCisterna>
}
