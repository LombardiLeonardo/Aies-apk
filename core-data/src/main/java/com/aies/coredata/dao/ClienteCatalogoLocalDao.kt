package com.aies.coredata.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aies.coredata.entities.ClienteCatalogo

@Dao
interface ClienteCatalogoLocalDao {

    @Query("SELECT * FROM clientes_catalogo ORDER BY nombre ASC")
    suspend fun getAll(): List<ClienteCatalogo>

    // REPLACE (no IGNORE): guardar de nuevo un cliente ya conocido debe actualizar su CUIT
    // asociado si cambió, no descartar el dato nuevo.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertar(item: ClienteCatalogo)
}
