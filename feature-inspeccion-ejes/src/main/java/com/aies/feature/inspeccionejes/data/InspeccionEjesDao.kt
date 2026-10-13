package com.aies.feature.inspeccionejes.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import java.io.File

@Dao
interface InspeccionEjesDao {

    // ---------- CLIENTES ----------

    @Query("SELECT * FROM clientes ORDER BY nombre ASC")
    fun getClientes(): Flow<List<Cliente>>

    @Query("SELECT * FROM clientes WHERE id = :id LIMIT 1")
    suspend fun getCliente(id: String): Cliente?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardarCliente(cliente: Cliente)

    @Query("DELETE FROM clientes WHERE id = :id")
    suspend fun eliminarCliente(id: String)

    // ---------- UNIDADES ----------

    @Query("SELECT * FROM unidades WHERE clienteId = :clienteId")
    suspend fun getUnidadesPorCliente(clienteId: String): List<Unidad>

    @Query("SELECT * FROM unidades WHERE id = :id LIMIT 1")
    suspend fun getUnidad(id: String): Unidad?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardarUnidad(unidad: Unidad)

    // ---------- CONTROLES ----------

    @Query("SELECT * FROM controles ORDER BY fecha DESC")
    suspend fun getControles(): List<Control>

    @Query("SELECT * FROM controles WHERE id = :id LIMIT 1")
    suspend fun getControl(id: String): Control?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardarControl(control: Control)

    @Query("DELETE FROM controles WHERE id = :id")
    suspend fun eliminarControlPorId(id: String)

    @Query("DELETE FROM checklist_lados WHERE controlId = :controlId")
    suspend fun eliminarChecklistsPorControl(controlId: String)

    @Query("DELETE FROM puntas_de_eje WHERE informeId = :informeId")
    suspend fun eliminarPuntasPorInforme(informeId: String)

    @Query("DELETE FROM informes_lpt WHERE id = :id")
    suspend fun eliminarInformePorId(id: String)

    @Query("DELETE FROM fotos WHERE parentId IN (:parentIds)")
    suspend fun eliminarFotosPorParents(parentIds: List<String>)

    /**
     * Mejora deliberada sobre el original: `EliminarControlAsync` en la app fuente borra
     * Control/ChecklistLado/InformeLiquidosPenetrantes/PuntaDeEje pero deja huérfanas las
     * filas de Foto y sus archivos en disco. Acá se recolectan y borran también, tanto la
     * fila como el archivo físico, antes de borrar el control.
     */
    @Transaction
    suspend fun eliminarControlCascada(controlId: String) {
        val checklists = getChecklistsPorControl(controlId)
        val informe = getInformePorControl(controlId)
        val puntas = informe?.let { getPuntasDeEje(it.id) } ?: emptyList()

        val parentIds = checklists.map { it.id } + puntas.map { it.id }
        if (parentIds.isNotEmpty()) {
            val fotos = getFotosPorParents(parentIds)
            fotos.forEach { foto ->
                val archivo = File(foto.rutaLocal)
                if (archivo.exists()) archivo.delete()
            }
            eliminarFotosPorParents(parentIds)
        }

        eliminarChecklistsPorControl(controlId)
        if (informe != null) {
            eliminarPuntasPorInforme(informe.id)
            eliminarInformePorId(informe.id)
        }
        eliminarControlPorId(controlId)
    }

    // ---------- CHECKLIST LADO ----------

    @Query("SELECT * FROM checklist_lados WHERE controlId = :controlId")
    suspend fun getChecklistsPorControl(controlId: String): List<ChecklistLado>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardarChecklistLado(checklist: ChecklistLado)

    // ---------- INFORME LÍQUIDOS PENETRANTES ----------

    @Query("SELECT * FROM informes_lpt WHERE controlId = :controlId LIMIT 1")
    suspend fun getInformePorControl(controlId: String): InformeLiquidosPenetrantes?

    @Insert
    suspend fun insertarInforme(informe: InformeLiquidosPenetrantes)

    @Transaction
    suspend fun obtenerOCrearInforme(controlId: String): InformeLiquidosPenetrantes {
        return getInformePorControl(controlId) ?: InformeLiquidosPenetrantes(controlId = controlId).also { insertarInforme(it) }
    }

    // ---------- PUNTA DE EJE ----------

    @Query("SELECT * FROM puntas_de_eje WHERE informeId = :informeId")
    suspend fun getPuntasDeEje(informeId: String): List<PuntaDeEje>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardarPuntaDeEje(punta: PuntaDeEje)

    // ---------- FOTOS ----------

    @Query("SELECT * FROM fotos WHERE parentId = :parentId")
    suspend fun getFotosPorParent(parentId: String): List<Foto>

    @Query("SELECT * FROM fotos WHERE parentId IN (:parentIds)")
    suspend fun getFotosPorParents(parentIds: List<String>): List<Foto>

    @Insert
    suspend fun insertarFoto(foto: Foto)

    @Query("DELETE FROM fotos WHERE id = :id")
    suspend fun eliminarFotoPorId(id: String)
}
