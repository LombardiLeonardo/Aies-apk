package com.aies.feature.repuestos.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface RepuestosDao {

    @Insert
    suspend fun insertRegistro(registro: RepuestoRegistro): Long

    @Insert
    suspend fun insertDetalles(detalles: List<DetalleRepuestoCisterna>)

    @Query("SELECT * FROM repuesto_registro ORDER BY fechaCreadoTimestamp DESC LIMIT :limite")
    suspend fun listarUltimos(limite: Int = 20): List<RepuestoRegistro>

    @Query("SELECT * FROM detalle_repuesto_cisterna WHERE registroId = :registroId ORDER BY cisternaNumero ASC")
    suspend fun listarDetalles(registroId: Long): List<DetalleRepuestoCisterna>
}
