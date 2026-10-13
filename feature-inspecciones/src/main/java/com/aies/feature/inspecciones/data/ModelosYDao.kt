package com.aies.feature.inspecciones.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

// ==========================================
// DEFINICIÓN DE TABLAS (ENTIDADES)
// ==========================================

@Entity(tableName = "empresas")
data class Empresa(@PrimaryKey val nombre: String)

@Entity(tableName = "marcas_tractor")
data class MarcaTractor(@PrimaryKey val nombre: String)

@Entity(tableName = "marcas_semi")
data class MarcaSemi(@PrimaryKey val nombre: String)

@Entity(tableName = "auditores")
data class Auditor(@PrimaryKey val nombre: String)

// ==========================================
// ACCESO A DATOS (DAOs)
// ==========================================

@Dao
interface InspeccionDao {
    // Auditores
    @Query("SELECT * FROM auditores ORDER BY nombre ASC")
    fun getAuditores(): Flow<List<Auditor>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAuditor(auditor: Auditor)

    // Empresas
    @Query("SELECT * FROM empresas ORDER BY nombre ASC")
    fun getEmpresas(): Flow<List<Empresa>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEmpresa(empresa: Empresa)

    // Marcas Tractor
    @Query("SELECT * FROM marcas_tractor ORDER BY nombre ASC")
    fun getMarcasTractor(): Flow<List<MarcaTractor>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMarcaTractor(marca: MarcaTractor)

    // Marcas Semirremolque
    @Query("SELECT * FROM marcas_semi ORDER BY nombre ASC")
    fun getMarcasSemi(): Flow<List<MarcaSemi>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMarcaSemi(marca: MarcaSemi)
}
