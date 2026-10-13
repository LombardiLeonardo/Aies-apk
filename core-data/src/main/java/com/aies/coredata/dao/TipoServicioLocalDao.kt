package com.aies.coredata.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aies.coredata.entities.TipoServicio

/**
 * Fase D, paso 1 de 5 del pase a base de datos compartida: esta interfaz Room es ahora solo la
 * cache local -- ver [TipoServicioDao] (misma carpeta) para la clase pública que el resto de la
 * app sigue llamando, que habla contra Supabase primero y usa esta cache como fallback/espejo.
 */
@Dao
interface TipoServicioLocalDao {

    // REPLACE (antes IGNORE): esta cache ahora solo refleja lo que Supabase confirmó como
    // verdad -- si el catálogo remoto cambió (ej. nombre corregido), tiene que pisar lo local.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertar(tipo: TipoServicio)

    @Query("SELECT * FROM tipos_servicio")
    suspend fun getAll(): List<TipoServicio>
}
