package com.aies.coredata.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aies.coredata.entities.AuditorCatalogo

@Dao
interface AuditorCatalogoLocalDao {

    @Query("SELECT * FROM auditores_catalogo ORDER BY nombre ASC")
    suspend fun getAll(): List<AuditorCatalogo>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertar(item: AuditorCatalogo)
}
