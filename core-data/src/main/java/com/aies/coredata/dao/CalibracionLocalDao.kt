package com.aies.coredata.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import com.aies.coredata.entities.Calibracion
import com.aies.coredata.entities.CalibracionCisterna
import com.aies.coredata.entities.CalibracionDescargaPatron

data class CalibracionConCisternas(
    @Embedded val calibracion: Calibracion,
    @Relation(parentColumn = "id", entityColumn = "calibracionId")
    val cisternas: List<CalibracionCisterna>
)

/**
 * Fase D, paso 4 de 5 del pase a base de datos compartida: esta interfaz Room es ahora solo la
 * cache local -- ver [CalibracionDao] (misma carpeta) para la clase pública que el resto de la
 * app sigue llamando, que habla contra Supabase primero y usa esta cache como fallback/espejo.
 */
@Dao
interface CalibracionLocalDao {
    // REPLACE (antes sin especificar, o sea ABORT): esta cache ahora también se puebla en cada
    // lectura remota exitosa (no solo en el insert original), así que un mismo id remoto puede
    // volver a insertarse.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarCalibracion(c: Calibracion): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarCisternas(list: List<CalibracionCisterna>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarDescargaPatron(list: List<CalibracionDescargaPatron>)

    @Query("UPDATE calibraciones SET documentoInternoPath = COALESCE(:path, documentoInternoPath), documentoInternoUrl = COALESCE(:url, documentoInternoUrl) WHERE id = :id")
    suspend fun actualizarDocumentoInterno(id: Long, path: String?, url: String?)

    @Transaction
    @Query("""
        SELECT * FROM calibraciones
        WHERE unidadId = :unidadId AND fechaVencimiento > :fechaActual
        ORDER BY fecha DESC LIMIT 1
    """)
    suspend fun calibracionVigenteDeUnidad(unidadId: Long, fechaActual: Long): CalibracionConCisternas?
}
