package com.aies.feature.inspeccionejes.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

enum class Lado { Izquierdo, Derecho }

enum class ArandelaSeguridadEstado { Correcta, Deteriorada, MalColocada, Inexistente }

enum class LubricanteEstado {
    Correcto,
    ContaminacionParticulasMetalicas,
    ContaminacionParticulasExternasOLiquidos,
    DeterioroPropiedades,
    EscasaCantidad
}

enum class RodamientoEstado { Correcto, DefectosEnRodillos, PistasConPicaduraODesgaste, JaulaRota }

enum class FrenoEstado { Correcta, DesgasteIrregular, FisuraVisible, NoAplica }

enum class RetenesEstado { OK, SeReemplazoLaPieza }

enum class ResultadoEstado { Apto, NoApto }

enum class TipoFoto { Infografia, LiquidosPenetrantes }

@Entity(tableName = "clientes")
data class Cliente(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val nombre: String
)

/**
 * Catálogo propio del módulo (dominio tractor/semi + marcas) — independiente de la
 * `Unidad` compartida de core-data, igual que en el resto de los feature modules
 * (ver HistorialCompartidoHelper para el reflejo hacia el historial compartido).
 */
@Entity(tableName = "unidades", indices = [Index("clienteId")])
data class Unidad(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val clienteId: String,
    val dominioTractor: String?,
    val dominioSemirremolque: String?,
    val marcaTractor: String?,
    val marcaSemirremolque: String?
)

/**
 * Root aggregate: snapshot desnormalizado de cliente/unidad al momento de crear el
 * control (igual que el original), para que el informe y el listado no dependan de que
 * esos datos no cambien después en el catálogo. No incluye Sincronizado/FechaModificacion
 * ni un campo de Estado: el original nunca transiciona su EstadoControl fuera de
 * EnProgreso (Finalizado no se asigna en ningún flujo), así que el campo no aporta
 * información real y se omite en vez de portar un enum de un solo valor útil.
 */
@Entity(tableName = "controles")
data class Control(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val clienteId: String,
    val unidadId: String,
    val cliente: String,
    val dominioTractor: String?,
    val dominioSemirremolque: String?,
    val marcaTractor: String?,
    val marcaSemirremolque: String?,
    val fecha: Long,
    val responsable: String?,
    val cantidadEjes: Int
)

@Entity(tableName = "checklist_lados", indices = [Index("controlId")])
data class ChecklistLado(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val controlId: String,
    val numeroEje: Int,
    val lado: Lado,
    // "OK" o texto libre con la descripción del problema, igual que el original.
    val holguraRodamientos: String = "OK",
    val ajusteTuercaCentral: String = "OK",
    val desgasteEjeAlojamientoPistaInterior: String = "OK",
    val desgasteAlojamientoPistasExterioresMaza: String = "OK",
    val fisurasPunteraEje: String = "OK",
    val holguraBujesTensor: String = "OK",
    val arandelaSeguridad: ArandelaSeguridadEstado = ArandelaSeguridadEstado.Correcta,
    val lubricanteRodamientos: LubricanteEstado = LubricanteEstado.Correcto,
    val estadoRodamiento: RodamientoEstado = RodamientoEstado.Correcto,
    val retenes: RetenesEstado = RetenesEstado.OK,
    val campanaFreno: FrenoEstado = FrenoEstado.Correcta,
    val discoFreno: FrenoEstado = FrenoEstado.Correcta,
    val resultado: ResultadoEstado = ResultadoEstado.Apto,
    val observaciones: String? = null,
    val revisado: Boolean = false
)

/** Uno solo por control, igual que el original. */
@Entity(tableName = "informes_lpt", indices = [Index("controlId", unique = true)])
data class InformeLiquidosPenetrantes(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val controlId: String,
    val fechaCreacion: Long = System.currentTimeMillis()
)

@Entity(tableName = "puntas_de_eje", indices = [Index("informeId")])
data class PuntaDeEje(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val informeId: String,
    val numeroEje: Int,
    val lado: Lado,
    val resultado: ResultadoEstado = ResultadoEstado.Apto,
    val observaciones: String? = null,
    val revisado: Boolean = false
)

/** `parentId` es el id de un ChecklistLado o de una PuntaDeEje. Sin UrlRemota: no hay nube en este módulo. */
@Entity(tableName = "fotos", indices = [Index("parentId")])
data class Foto(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val parentId: String,
    val tipo: TipoFoto,
    val rutaLocal: String,
    val descripcion: String? = null
)
