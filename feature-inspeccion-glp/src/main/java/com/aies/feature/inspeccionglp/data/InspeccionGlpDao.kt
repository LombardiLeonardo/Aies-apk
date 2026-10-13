package com.aies.feature.inspeccionglp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import java.io.File
import java.util.Calendar

@Dao
interface InspeccionGlpDao {

    // ---------- CLIENTES ----------

    @Query("SELECT * FROM clientes ORDER BY razonSocial ASC")
    suspend fun getClientes(): List<Cliente>

    @Query("SELECT * FROM clientes WHERE id = :id LIMIT 1")
    suspend fun getCliente(id: String): Cliente?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardarCliente(cliente: Cliente)

    @Query("DELETE FROM clientes WHERE id = :id")
    suspend fun eliminarClientePorId(id: String)

    @Transaction
    suspend fun eliminarClienteCascada(clienteId: String) {
        getVehiculosPorCliente(clienteId).forEach { eliminarVehiculoCascada(it.id) }
        eliminarClientePorId(clienteId)
    }

    @Query("DELETE FROM checklist_plantillas")
    suspend fun eliminarTodasLasPlantillas()

    @Query("DELETE FROM items_checklist")
    suspend fun eliminarTodosLosItems()

    /** Equivalente de `BorrarTodosLosDatosAsync`: borra clientes/vehículos/informes/fotos en cascada y la plantilla (se resiembra sola después). */
    @Transaction
    suspend fun borrarTodosLosDatos() {
        getClientes().forEach { eliminarClienteCascada(it.id) }
        eliminarTodosLosItems()
        eliminarTodasLasPlantillas()
    }

    // ---------- VEHICULOS ----------

    @Query("SELECT * FROM vehiculos WHERE clienteId = :clienteId")
    suspend fun getVehiculosPorCliente(clienteId: String): List<Vehiculo>

    @Query("SELECT * FROM vehiculos WHERE id = :id LIMIT 1")
    suspend fun getVehiculo(id: String): Vehiculo?

    @Query("SELECT * FROM vehiculos")
    suspend fun getTodosLosVehiculos(): List<Vehiculo>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardarVehiculo(vehiculo: Vehiculo)

    @Query("DELETE FROM vehiculos WHERE id = :id")
    suspend fun eliminarVehiculoPorId(id: String)

    @Query("SELECT * FROM informes WHERE vehiculoId = :vehiculoId")
    suspend fun getInformesPorVehiculo(vehiculoId: String): List<Informe>

    @Transaction
    suspend fun eliminarVehiculoCascada(vehiculoId: String) {
        getInformesPorVehiculo(vehiculoId).forEach { eliminarInformeCascada(it.id) }
        eliminarVehiculoPorId(vehiculoId)
    }

    // ---------- PLANTILLAS / ITEMS ----------

    @Query("SELECT * FROM checklist_plantillas")
    suspend fun getPlantillas(): List<ChecklistPlantilla>

    @Query("SELECT * FROM checklist_plantillas WHERE nombre = :nombre LIMIT 1")
    suspend fun getPlantillaPorNombre(nombre: String): ChecklistPlantilla?

    @Insert
    suspend fun insertarPlantilla(plantilla: ChecklistPlantilla)

    @Insert
    suspend fun insertarItems(items: List<ItemChecklist>)

    /** Inserta solo si no existe ya ese Id — usado al traer la plantilla oficial desde Supabase. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun guardarPlantillaSiNoExiste(plantilla: ChecklistPlantilla)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun guardarItemChecklistSiNoExiste(item: ItemChecklist)

    @Query("SELECT * FROM checklist_plantillas WHERE nombre = :nombre AND id != :idDeNube")
    suspend fun getPlantillasDuplicadas(nombre: String, idDeNube: String): List<ChecklistPlantilla>

    @Query("DELETE FROM items_checklist WHERE plantillaId = :plantillaId")
    suspend fun eliminarItemsDePlantilla(plantillaId: String)

    @Query("DELETE FROM checklist_plantillas WHERE id = :id")
    suspend fun eliminarPlantillaPorId(id: String)

    /**
     * Borra cualquier plantilla local con el mismo nombre pero un Id DISTINTO al que llegó de
     * la nube (típicamente, la que la app generó sola la primera vez que arrancó sin conexión).
     */
    @Transaction
    suspend fun reemplazarPlantillaLocalPorLaDeNube(nombrePlantilla: String, idDeNube: String) {
        getPlantillasDuplicadas(nombrePlantilla, idDeNube).forEach { vieja ->
            eliminarItemsDePlantilla(vieja.id)
            eliminarPlantillaPorId(vieja.id)
        }
    }

    @Query("SELECT * FROM items_checklist WHERE id = :id LIMIT 1")
    suspend fun getItemChecklist(id: String): ItemChecklist?

    @Query(
        "SELECT * FROM items_checklist WHERE plantillaId = :plantillaId " +
            "AND (CASE WHEN :esUnidadDeColor THEN aplicaCif ELSE aplicaFob END) " +
            "ORDER BY orden ASC"
    )
    suspend fun getItemsDePlantilla(plantillaId: String, esUnidadDeColor: Boolean): List<ItemChecklist>

    @Transaction
    suspend fun seedPlantillaGlpSiNoExiste() {
        val nombre = "Control camión tanque GLP"
        if (getPlantillaPorNombre(nombre) != null) return

        val plantilla = ChecklistPlantilla(
            nombre = nombre,
            descripcion = "R 21-00 Rev. 04 - Planilla para control de camiones tanque, producto GLP"
        )
        insertarPlantilla(plantilla)
        insertarItems(SeedData.itemsGlp(plantilla.id))
    }

    // ---------- INFORMES ----------

    @Query("SELECT * FROM informes ORDER BY fecha DESC")
    suspend fun getInformes(): List<Informe>

    @Query("SELECT * FROM informes WHERE id = :id LIMIT 1")
    suspend fun getInforme(id: String): Informe?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardarInforme(informe: Informe)

    @Query("DELETE FROM informes WHERE id = :id")
    suspend fun eliminarInformePorId(id: String)

    @Query("SELECT * FROM informes WHERE fecha >= :inicioAnio AND fecha < :finAnio")
    suspend fun getInformesDelAnio(inicioAnio: Long, finAnio: Long): List<Informe>

    /**
     * `INF-{año}-{correlativo}` calculado como el máximo número ya usado ese año + 1 (no la
     * cantidad de filas): si se borró un informe, contar filas generaría un número repetido.
     */
    suspend fun generarNumeroInforme(): String {
        val cal = Calendar.getInstance()
        val anio = cal.get(Calendar.YEAR)
        cal.set(anio, Calendar.JANUARY, 1, 0, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val inicioAnio = cal.timeInMillis
        cal.add(Calendar.YEAR, 1)
        val finAnio = cal.timeInMillis

        val maxNumero = getInformesDelAnio(inicioAnio, finAnio)
            .mapNotNull { informe ->
                val partes = informe.numeroInforme.split("-")
                if (partes.size == 3) partes[2].toIntOrNull() else null
            }
            .maxOrNull() ?: 0

        return "INF-%d-%04d".format(anio, maxNumero + 1)
    }

    @Transaction
    suspend fun eliminarInformeCascada(informeId: String) {
        getResultadosDeInforme(informeId).forEach { resultado ->
            getFotosDeResultado(resultado.id).forEach { foto ->
                File(foto.rutaArchivo).let { if (it.exists()) it.delete() }
                eliminarFotoPorId(foto.id)
            }
            eliminarResultadoPorId(resultado.id)
        }

        getInforme(informeId)?.let { informe ->
            informe.rutaDocx?.let { ruta -> File(ruta).let { if (it.exists()) it.delete() } }
        }
        eliminarInformePorId(informeId)
    }

    // ---------- RESULTADOS ----------

    @Query("SELECT * FROM item_resultados WHERE informeId = :informeId")
    suspend fun getResultadosDeInforme(informeId: String): List<ItemResultado>

    @Query("SELECT * FROM item_resultados")
    suspend fun getTodosLosResultados(): List<ItemResultado>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardarResultado(resultado: ItemResultado)

    @Query("DELETE FROM item_resultados WHERE id = :id")
    suspend fun eliminarResultadoPorId(id: String)

    @Transaction
    suspend fun getResultadosCompletosDeInforme(informeId: String): List<ResultadoCompleto> =
        getResultadosDeInforme(informeId)
            .mapNotNull { resultado -> getItemChecklist(resultado.itemChecklistId)?.let { ResultadoCompleto(it, resultado) } }
            .sortedBy { it.item.orden }

    // ---------- FOTOS ----------

    @Insert
    suspend fun guardarFoto(foto: Foto)

    @Query("SELECT * FROM fotos WHERE itemResultadoId = :itemResultadoId")
    suspend fun getFotosDeResultado(itemResultadoId: String): List<Foto>

    @Query("SELECT * FROM fotos")
    suspend fun getTodasLasFotos(): List<Foto>

    @Query("DELETE FROM fotos WHERE id = :id")
    suspend fun eliminarFotoPorId(id: String)

    // ---------- Marcado de sincronización ----------

    @Query("UPDATE clientes SET sincronizado = 1 WHERE id = :id")
    suspend fun marcarClienteSincronizado(id: String)

    @Query("UPDATE vehiculos SET sincronizado = 1 WHERE id = :id")
    suspend fun marcarVehiculoSincronizado(id: String)

    @Query("UPDATE informes SET sincronizado = 1 WHERE id = :id")
    suspend fun marcarInformeSincronizado(id: String)

    @Query("UPDATE item_resultados SET sincronizado = 1 WHERE id = :id")
    suspend fun marcarResultadoSincronizado(id: String)

    @Query("UPDATE fotos SET sincronizado = 1 WHERE id = :id")
    suspend fun marcarFotoSincronizada(id: String)
}
