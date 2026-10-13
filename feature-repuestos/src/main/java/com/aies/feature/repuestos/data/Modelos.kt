package com.aies.feature.repuestos.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Cada compartimiento de la cisterna lleva a lo sumo uno de estos repuestos (selección
 * excluyente, RadioButton — no checkboxes). NINGUNO es el valor por defecto cuando no se
 * cambió nada en ese compartimiento. El código (`valor`) es lo que se persiste en
 * [DetalleRepuestoCisterna.repuestoCambiado] y en la columna "Repuesto cambiado" del PDF.
 */
enum class RepuestoOptions(val valor: String, val etiqueta: String) {
    NINGUNO("NINGUNO", "Ninguno"),
    JUNTA_PASO_HOMBRE("JUNTA_PASO_HOMBRE", "Juntas paso de hombre"),
    JUNTA_SENSOR("JUNTA_SENSOR", "Juntas de sensor"),
    KIT_BULONES("KIT_BULONES", "Kit de bulones");

    companion object {
        fun porValor(valor: String): RepuestoOptions = entries.firstOrNull { it.valor == valor } ?: NINGUNO
    }
}

/**
 * Un registro de cambio de repuestos por unidad (dominio), análogo a los ControlXxx de
 * feature-controles-visuales: una sola Unidad (la cisterna/semirremolque, no tractor+semi,
 * porque el pedido es "cada compartimiento DE LA CISTERNA"), fecha y el PDF generado.
 */
@Entity(tableName = "repuesto_registro")
data class RepuestoRegistro(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dominio: String,
    val cliente: String,
    val auditor: String,
    val fechaCreadoTimestamp: Long,
    val archivoPdf: String? = null
)

/** Un renglón por compartimiento: qué repuesto (si alguno) se cambió en ese número de cisterna. */
@Entity(
    tableName = "detalle_repuesto_cisterna",
    foreignKeys = [ForeignKey(entity = RepuestoRegistro::class, parentColumns = ["id"], childColumns = ["registroId"])],
    indices = [Index("registroId")]
)
data class DetalleRepuestoCisterna(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val registroId: Long,
    val cisternaNumero: Int,
    val repuestoCambiado: String
)
