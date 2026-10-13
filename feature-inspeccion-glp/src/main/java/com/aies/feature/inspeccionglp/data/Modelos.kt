package com.aies.feature.inspeccionglp.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

enum class EstadoItemResultado { OK, Mal }

enum class TipoIncumplimiento { Observacion, Recomendacion }

enum class EstadoInforme { Borrador, Finalizado, Enviado }

@Entity(tableName = "clientes")
data class Cliente(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val razonSocial: String,
    val direccion: String? = null,
    val contacto: String? = null,
    val fechaCreacion: Long = System.currentTimeMillis(),
    val fechaModificacion: Long = System.currentTimeMillis(),
    val sincronizado: Boolean = false
)

/**
 * `bandera` nula o vacía = unidad blanca (FOB); con valor = unidad de color / petrolera (CIF).
 * `esUnidadDeColor()` reemplaza el `[Ignore] EsUnidadDeColor` calculado del original: se
 * expone como función de extensión en vez de una propiedad de la data class para no tener
 * que lidiar con anotaciones @Ignore de Room sobre una propiedad sin campo respaldado.
 */
@Entity(tableName = "vehiculos", indices = [Index("clienteId")])
data class Vehiculo(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val clienteId: String,
    val patenteTractor: String,
    val marcaTractor: String? = null,
    val modeloTractor: String? = null,
    val patenteSemirremolque: String,
    val marcaSemirremolque: String? = null,
    val modeloSemirremolque: String? = null,
    val fabricanteSemirremolque: String? = null,
    val numeroMatricula: String? = null,
    val taraSemirremolque: Double? = null,
    val capacidadSemirremolque: Double? = null,
    val bandera: String? = null,
    val fechaCreacion: Long = System.currentTimeMillis(),
    val fechaModificacion: Long = System.currentTimeMillis(),
    val sincronizado: Boolean = false
)

fun Vehiculo.esUnidadDeColor(): Boolean = !bandera.isNullOrBlank()

@Entity(tableName = "checklist_plantillas")
data class ChecklistPlantilla(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val nombre: String,
    val descripcion: String? = null
)

@Entity(tableName = "items_checklist", indices = [Index("plantillaId")])
data class ItemChecklist(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val plantillaId: String,
    val categoria: String,
    val subcategoria: String? = null,
    val numero: Int,
    val descripcion: String,
    val tipoIncumplimiento: TipoIncumplimiento,
    val aplicaFob: Boolean = true,
    val aplicaCif: Boolean = true,
    val orden: Int
)

@Entity(tableName = "item_resultados", indices = [Index("informeId"), Index("itemChecklistId")])
data class ItemResultado(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val informeId: String,
    val itemChecklistId: String,
    val estado: EstadoItemResultado,
    val comentario: String? = null,
    val fechaCreacion: Long = System.currentTimeMillis(),
    val fechaModificacion: Long = System.currentTimeMillis(),
    val sincronizado: Boolean = false
)

/** Sin HoraInicio/HoraFin: declarados en el original pero nunca asignados ni leídos (campos muertos). */
@Entity(tableName = "informes", indices = [Index("clienteId"), Index("vehiculoId"), Index("plantillaId")])
data class Informe(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val numeroInforme: String,
    val clienteId: String,
    val vehiculoId: String,
    val plantillaId: String,
    val fecha: Long = System.currentTimeMillis(),
    val estado: EstadoInforme = EstadoInforme.Borrador,
    val rutaDocx: String? = null,
    val fechaCreacion: Long = System.currentTimeMillis(),
    val fechaModificacion: Long = System.currentTimeMillis(),
    val sincronizado: Boolean = false
)

@Entity(tableName = "fotos", indices = [Index("itemResultadoId")])
data class Foto(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val itemResultadoId: String,
    val rutaArchivo: String,
    val fechaCreacion: Long = System.currentTimeMillis(),
    val sincronizado: Boolean = false
)

/** Combina un ItemChecklist con su ItemResultado para el informe Word y la pantalla de hallazgos. */
data class ResultadoCompleto(val item: ItemChecklist, val resultado: ItemResultado)
