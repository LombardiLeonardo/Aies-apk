package com.aies.feature.controlesvisuales.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Lista fija de productos transportados (pie de página de las 3 planillas R 16-0x). */
object ProductoOptions {
    const val OTROS = "Otros (Especificar)"

    val lista = listOf(
        "Nafta súper (NS)", "Diésel Oil (DO)", "Aceites", "Gas Oil (GO)", "IFOS",
        "Nafta común (NC)", "Kerosene (K)", "Solventes alifáticos", "Nafta súper alto octanaje (ULTRA)",
        "JP1", "Solventes aromáticos", "Asfalto", "Aeronafta", "Aguarrás", "Crudo",
        "Fuel Oil (FUEL)", "DBI: Base insecticida", OTROS
    )
}

/** Tipo de unidad de las 3 planillas: "Tipo: Semi/acoplado/chasis". */
object TipoUnidadOptions {
    val lista = listOf("Semi", "Acoplado", "Chasis")
}

object TipoCargaOptions {
    val lista = listOf("Ventral", "Superior")
}

/**
 * Encabezado común a los 3 formularios (R 16-01/02/03), armado en la UI antes de aplanarlo
 * en las columnas @Embedded de cada entidad raíz — Room no soporta un objeto polimórfico
 * compartido entre entidades, así que esto es un plain Kotlin object, no una @Entity.
 */
data class EncabezadoControl(
    val fecha: Long = System.currentTimeMillis(),
    val razonSocial: String = "",
    val domicilio: String = "",
    val tipoActividad: String = "",
    val cuit: String = "",
    val tipoUnidad: String = TipoUnidadOptions.lista[0],
    val dominio: String = "",
    val numeroRuta: String = "",
    val marca: String = "",
    val modelo: String = "",
    val fabricante: String = "",
    val fechaFabricacionCisterna: Long? = null,
    val antiguedad: String = "",
    val fechaVencimientoVisualInterna: Long? = null,
    val fechaVencimientoVisualExterna: Long? = null,
    val fechaVencimientoEstanqueidadEspesores: Long? = null,
    val tipoCarga: String = TipoCargaOptions.lista[0]
)

/** Columnas @Embedded reutilizadas por las 3 entidades raíz — un solo lugar para declarar
 *  los campos del encabezado, sin repetirlos 3 veces. */
data class EncabezadoEmbed(
    val fecha: Long,
    val razonSocial: String,
    val domicilio: String,
    val tipoActividad: String,
    val cuit: String,
    val tipoUnidad: String,
    val dominio: String,
    val numeroRuta: String,
    val marca: String,
    val modelo: String,
    val fabricante: String,
    val fechaFabricacionCisterna: Long?,
    val antiguedad: String,
    val fechaVencimientoVisualInterna: Long?,
    val fechaVencimientoVisualExterna: Long?,
    val fechaVencimientoEstanqueidadEspesores: Long?,
    val tipoCarga: String
)

fun EncabezadoControl.toEmbed() = EncabezadoEmbed(
    fecha, razonSocial, domicilio, tipoActividad, cuit, tipoUnidad, dominio, numeroRuta,
    marca, modelo, fabricante, fechaFabricacionCisterna, antiguedad,
    fechaVencimientoVisualInterna, fechaVencimientoVisualExterna, fechaVencimientoEstanqueidadEspesores, tipoCarga
)

/** Un renglón de la tabla "Detalle de compartimentos", plain object usado en la UI antes de
 *  volcarse a la tabla hija de cada control (una fila por compartimento y por control). */
data class DetalleCompartimento(
    val numero: Int,
    val capacidad: String = "",
    val producto: String = ProductoOptions.lista[0],
    val productoOtro: String = "",
    val estado: String = "Apto"
)

// ───────────────────────── 1. Control Visual Interna ─────────────────────────

@Entity(tableName = "control_visual_interna")
data class ControlVisualInterna(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @androidx.room.Embedded val encabezado: EncabezadoEmbed,
    val fechaCreadoTimestamp: Long,
    val archivoPdf: String? = null
)

@Entity(tableName = "detalle_compartimento_interna", indices = [Index("controlId")])
data class DetalleCompartimentoInterna(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val controlId: Long,
    val numero: Int,
    val capacidad: String,
    val producto: String,
    val estado: String
)

/** Las 4 verificaciones de la planilla R 16-01, fijas por compartimento — se guardan como
 *  4 pares (cumple/observación) en una sola fila por compartimento, más simple que una
 *  tabla nieta ya que la cantidad de ítems es fija y chica (a diferencia del checklist externo). */
@Entity(
    tableName = "verificacion_cisterna_interna",
    foreignKeys = [ForeignKey(entity = ControlVisualInterna::class, parentColumns = ["id"], childColumns = ["controlId"])],
    indices = [Index("controlId")]
)
data class VerificacionCisternaInterna(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val controlId: Long,
    val numeroCompartimento: Int,
    val item1Cumple: Boolean = true,
    val item1Obs: String = "",
    val item2Cumple: Boolean = true,
    val item2Obs: String = "",
    val item3Cumple: Boolean = true,
    val item3Obs: String = "",
    val item4Cumple: Boolean = true,
    val item4Obs: String = ""
)

object VerificacionInternaTextos {
    val items = listOf(
        "Las paredes están libres de áreas corroídas, gastadas o con rajaduras.",
        "Las soldaduras están libres de fisuras visibles.",
        "El fondo está libre de áreas corroídas, gastadas o con rajaduras.",
        "El fondo está libre de abolladuras importantes."
    )
}

// ───────────────────────── 2. Control Visual Externa ─────────────────────────

@Entity(tableName = "control_visual_externa")
data class ControlVisualExterna(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @androidx.room.Embedded val encabezado: EncabezadoEmbed,
    val fechaCreadoTimestamp: Long,
    val archivoPdf: String? = null
)

@Entity(tableName = "detalle_compartimento_externa", indices = [Index("controlId")])
data class DetalleCompartimentoExterna(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val controlId: Long,
    val numero: Int,
    val capacidad: String,
    val producto: String,
    val estado: String
)

/** Un renglón del checklist de ~52 ítems (R 16-02), no depende de compartimento — es para
 *  toda la unidad. `codigo` es el número/letra de la planilla (ej. "8a"), `seccion` agrupa
 *  visualmente igual que categoria/subcategoria en feature-inspeccion-glp. */
@Entity(
    tableName = "item_checklist_externa",
    foreignKeys = [ForeignKey(entity = ControlVisualExterna::class, parentColumns = ["id"], childColumns = ["controlId"])],
    indices = [Index("controlId")]
)
data class ItemChecklistExterna(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val controlId: Long,
    val codigo: String,
    val seccion: String,
    val descripcion: String,
    val orden: Int,
    val cumple: Boolean = true,
    val observaciones: String = ""
)

// ───────────────────────── 3. Control de Espesores ─────────────────────────

@Entity(tableName = "control_espesores")
data class ControlEspesores(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @androidx.room.Embedded val encabezado: EncabezadoEmbed,
    val fechaCreadoTimestamp: Long,
    val archivoPdf: String? = null
)

@Entity(tableName = "detalle_compartimento_espesores", indices = [Index("controlId")])
data class DetalleCompartimentoEspesores(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val controlId: Long,
    val numero: Int,
    val capacidad: String,
    val producto: String,
    val estado: String
)

object GrillaEspesores {
    const val FILAS = 6
    const val COLUMNAS = 8
    val grillas = listOf("Lateral Derecho", "Lateral Izquierdo", "Parte Anterior", "Parte Posterior")
}

@Entity(
    tableName = "medicion_espesor",
    foreignKeys = [ForeignKey(entity = ControlEspesores::class, parentColumns = ["id"], childColumns = ["controlId"])],
    indices = [Index("controlId")]
)
data class MedicionEspesor(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val controlId: Long,
    val grilla: String,
    val fila: Int,
    val columna: Int,
    val valorMm: Double? = null
)
