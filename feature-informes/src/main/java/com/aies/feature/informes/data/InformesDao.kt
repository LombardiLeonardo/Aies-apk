package com.aies.feature.informes.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface InformesDao {

    @Query("SELECT * FROM clientes WHERE activo = 1 ORDER BY razonSocial ASC")
    suspend fun getClientesActivos(): List<Cliente>

    @Query("SELECT * FROM clientes WHERE cuit = :cuit LIMIT 1")
    suspend fun getClientePorCuit(cuit: String): Cliente?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCliente(cliente: Cliente): Long

    @Update
    suspend fun actualizarCliente(cliente: Cliente)

    // REPLACE: volver a generar el PDF para el mismo Calibrado N° (nroInforme, único) es un
    // caso real de uso -- corrección de datos, reintento tras un error -- y antes rompía con
    // "UNIQUE constraint failed: informes.nroInforme" en vez de simplemente reemplazar el
    // informe anterior por el nuevo.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInforme(informe: InformeRegistro)

    @Query(
        """
        SELECT * FROM informes
        WHERE (:clienteId IS NULL OR clienteId = :clienteId)
          AND (:desde IS NULL OR fechaEmisionTimestamp >= :desde)
          AND (:hasta IS NULL OR fechaEmisionTimestamp <= :hasta)
        ORDER BY fechaEmisionTimestamp DESC
        """
    )
    suspend fun listarInformes(clienteId: Long?, desde: Long?, hasta: Long?): List<InformeRegistro>

    @Query(
        """
        SELECT i.nroInforme as nroInforme, c.razonSocial as clienteRazonSocial, c.cuit as clienteCuit,
               i.patenteTractor as patenteTractor, i.patenteSemirremolque as patenteSemirremolque,
               i.calibradoNro as calibradoNro, i.fechaEmisionTimestamp as fechaEmisionTimestamp,
               i.fechaVencimientoTimestamp as fechaVencimientoTimestamp, i.cantCisternas as cantCisternas,
               i.resultadoFinal as resultadoFinal, i.cvCumple as cvCumple, i.tdCumple as tdCumple, i.psCumple as psCumple,
               i.fechaCreadoTimestamp as fechaCreadoTimestamp, i.notas as notas
        FROM informes i
        INNER JOIN clientes c ON c.id = i.clienteId
        WHERE (:clienteId IS NULL OR i.clienteId = :clienteId)
          AND (:desde IS NULL OR i.fechaEmisionTimestamp >= :desde)
          AND (:hasta IS NULL OR i.fechaEmisionTimestamp <= :hasta)
        ORDER BY i.fechaEmisionTimestamp DESC
        """
    )
    suspend fun listarParaExportar(clienteId: Long?, desde: Long?, hasta: Long?): List<InformeExportRow>
}
