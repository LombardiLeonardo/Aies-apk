package com.aies.feature.certificados.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CertificadosDao {

    @Query("SELECT * FROM empresas_transportistas ORDER BY nombre ASC")
    fun getEmpresas(): Flow<List<EmpresaTransportista>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEmpresa(empresa: EmpresaTransportista)

    @Insert
    suspend fun insertCertificado(registro: CertificadoRegistro)

    // Fase D paso 5: el correlativo ya no vive en Room local (viene de las RPC de Postgres
    // `siguiente_numero_precinto`/`siguiente_numero_tapa_hombre`), así que `contarTodos()` y
    // `maxCorrelativo()` -- que servían solo al mecanismo local viejo -- se eliminaron. El
    // filtro `transportista != 'INICIAL'` se mantiene igual como red de seguridad barata: es
    // posible que algún dispositivo ya en campo tenga guardada localmente esa fila centinela de
    // antes de este cambio, y no hace falta migrar/limpiar datos históricos para que se siga
    // filtrando de la vista.
    @Query("SELECT * FROM certificados WHERE transportista != 'INICIAL' ORDER BY id DESC LIMIT 15")
    suspend fun historial(): List<CertificadoRegistro>

    @Query("SELECT * FROM certificados WHERE transportista != 'INICIAL' ORDER BY id ASC")
    suspend fun exportarDatos(): List<CertificadoRegistro>
}
