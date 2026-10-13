package com.aies.feature.planillatapas.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Registro de la "Planilla de Control de Tapas de Cisternas" (R 11-01), análogo a
 * RepuestoRegistro/ControlVisualExterna: una sola Unidad (la cisterna, no tractor+semi — la
 * planilla es de LA CISTERNA), fecha, horario del servicio y el PDF generado.
 */
@Entity(tableName = "planilla_tapas_registro")
data class PlanillaTapasRegistro(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val unidadId: Long,
    val dominio: String,
    val cliente: String,
    val auditor: String,
    val fechaCreadoTimestamp: Long,
    val fabricanteTapaPh: String,
    val horaInicio: Long? = null,
    val horaFin: Long? = null,
    val archivoPdf: String? = null
)

/** Un renglón por compartimiento/cisterna: último producto transportado + medición de gases. */
@Entity(
    tableName = "detalle_compartimento_tapas",
    foreignKeys = [ForeignKey(entity = PlanillaTapasRegistro::class, parentColumns = ["id"], childColumns = ["registroId"])],
    indices = [Index("registroId")]
)
data class DetalleCompartimentoTapas(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val registroId: Long,
    val cisternaNumero: Int,
    val ultimoProductoTransportado: String = "",
    val medicionGasesLel: Double? = null
)

/**
 * Un renglón del checklist (~38 filas contando sub-ítems de letra). `codigo` es un identificador
 * ESTABLE Y ÚNICO propio del módulo (no el número crudo de la planilla original R 11-01, que
 * repite 13/14 entre "Boca de llenado 250mm" y "Montaje", y reinicia en 1 en "Liberación final
 * de la unidad" -- un error de numeración del documento original que NO se replica acá).
 * `numeroVisible` es el número/letra "de cara al usuario" tal como aparece en el papel real
 * (ej. "8", "(a)"), separado de `codigo` para poder imprimir la planilla igual al original sin
 * que eso genere colisiones internas.
 */
@Entity(
    tableName = "item_checklist_tapas",
    foreignKeys = [ForeignKey(entity = PlanillaTapasRegistro::class, parentColumns = ["id"], childColumns = ["registroId"])],
    indices = [Index("registroId")]
)
data class ItemChecklistTapas(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val registroId: Long,
    val codigo: String,
    val seccion: String,
    val descripcion: String,
    val numeroVisible: String,
    val orden: Int,
    val cumple: Boolean = true,
    val observacion: String = ""
)
