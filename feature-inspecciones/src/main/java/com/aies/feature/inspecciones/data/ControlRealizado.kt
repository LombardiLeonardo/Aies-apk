package com.aies.feature.inspecciones.data

import android.content.Intent
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * Registro de un control finalizado. Se graba uno por cada inspección exportada
 * (LIVIANOS, PESADOS, COQUE, QUIMICOS, AXION, ENGANCHE) y sirve para dos cosas:
 *  - Exportar el historial de controles hechos a cada empresa (facturación).
 *  - Autocompletar los datos del equipo la próxima vez que se inspeccione la misma patente.
 */
@Entity(tableName = "controles_realizados")
data class ControlRealizado(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tipoInspeccion: String,
    val empresa: String,
    val auditor: String,
    val patenteTractor: String,
    val marcaTractor: String,
    val modeloTractor: String,
    val anioTractor: String,
    val combustible: String,
    val patenteSemi: String,
    val marcaSemi: String,
    val modeloSemi: String,
    val anioSemi: String,
    val fechaTimestamp: Long
)

/**
 * Arma un [ControlRealizado] a partir de las extras del Intent que GestorReportes
 * reenvía a cada pantalla de inspección (mismas keys que arma DatosEquipoActivity).
 */
fun controlRealizadoDesdeIntent(intent: Intent, tipoInspeccion: String): ControlRealizado {
    return ControlRealizado(
        tipoInspeccion = tipoInspeccion,
        empresa = intent.getStringExtra("EMPRESA") ?: "",
        auditor = intent.getStringExtra("AUDITOR") ?: "",
        patenteTractor = intent.getStringExtra("PATENTE_TRACTOR") ?: "",
        marcaTractor = intent.getStringExtra("MARCA_TRACTOR") ?: "",
        modeloTractor = intent.getStringExtra("MODELO_TRACTOR") ?: "",
        anioTractor = intent.getStringExtra("ANIO_TRACTOR") ?: "",
        combustible = intent.getStringExtra("COMBUSTIBLE") ?: "",
        patenteSemi = intent.getStringExtra("PATENTE_SEMI") ?: "",
        marcaSemi = intent.getStringExtra("MARCA_SEMI") ?: "",
        modeloSemi = intent.getStringExtra("MODELO_SEMI") ?: "",
        anioSemi = intent.getStringExtra("ANIO_SEMI") ?: "",
        fechaTimestamp = System.currentTimeMillis()
    )
}

@Dao
interface ControlRealizadoDao {

    @Insert
    suspend fun insert(control: ControlRealizado)

    @Query("SELECT * FROM controles_realizados WHERE patenteTractor = :patente ORDER BY fechaTimestamp DESC LIMIT 1")
    suspend fun getUltimoPorPatenteTractor(patente: String): ControlRealizado?

    @Query("SELECT * FROM controles_realizados WHERE patenteSemi = :patente ORDER BY fechaTimestamp DESC LIMIT 1")
    suspend fun getUltimoPorPatenteSemi(patente: String): ControlRealizado?

    @Query("SELECT * FROM controles_realizados WHERE fechaTimestamp BETWEEN :desde AND :hasta ORDER BY fechaTimestamp ASC")
    suspend fun getEnRango(desde: Long, hasta: Long): List<ControlRealizado>

    @Query("SELECT DISTINCT empresa FROM controles_realizados ORDER BY empresa ASC")
    suspend fun getEmpresasConControles(): List<String>
}
