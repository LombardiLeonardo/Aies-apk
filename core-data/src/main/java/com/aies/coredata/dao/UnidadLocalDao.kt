package com.aies.coredata.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aies.coredata.entities.Unidad

/**
 * Fase D, paso 1 de 5 del pase a base de datos compartida: esta interfaz Room es ahora solo la
 * cache local -- ver [UnidadDao] (misma carpeta) para la clase pública que el resto de la app
 * sigue llamando, que habla contra Supabase primero y usa esta cache como fallback/espejo.
 */
@Dao
interface UnidadLocalDao {

    @Query("SELECT * FROM unidades WHERE placa = :placa LIMIT 1")
    suspend fun getPorPlaca(placa: String): Unidad?

    @Query("SELECT * FROM unidades WHERE id = :id LIMIT 1")
    suspend fun getPorId(id: Long): Unidad?

    // REPLACE (antes IGNORE): esta cache ahora solo refleja lo que Supabase confirmó como
    // verdad -- si otro dispositivo cambió la fila, tiene que pisar lo que había local.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertar(unidad: Unidad): Long

    @Query("UPDATE unidades SET cantidadCisternas = :cantidad WHERE id = :id")
    suspend fun actualizarCantidadCisternas(id: Long, cantidad: Int)
}
