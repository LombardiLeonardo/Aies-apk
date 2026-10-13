package com.aies.feature.inspecciones.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ObservacionDao {

    @Query("SELECT * FROM observaciones_sugeridas ORDER BY esPrecargada DESC, texto ASC")
    fun getAll(): Flow<List<ObservacionSugerida>>

    @Query("SELECT * FROM observaciones_sugeridas ORDER BY esPrecargada DESC, texto ASC")
    suspend fun getAllOnce(): List<ObservacionSugerida>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(obs: ObservacionSugerida)

    @Delete
    suspend fun delete(obs: ObservacionSugerida)

    @Query("SELECT COUNT(*) FROM observaciones_sugeridas WHERE esPrecargada = 1")
    suspend fun countPrecargadas(): Int
}
