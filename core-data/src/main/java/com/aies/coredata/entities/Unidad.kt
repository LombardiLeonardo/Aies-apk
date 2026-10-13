package com.aies.coredata.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Representa un vehículo/cisterna sobre el que se prestan servicios.
 * Es la entidad "ancla": todos los módulos (inspecciones, certificados,
 * informes, y los que vengan después) apuntan a una Unidad.
 */
@Entity(
    tableName = "unidades",
    indices = [Index(value = ["placa"], unique = true)]
)
data class Unidad(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val placa: String,

    // Tipo de unidad, reutiliza las categorías ya usadas en la app de inspecciones
    // (LIVIANOS, PESADOS, COQUE, QUIMICOS, GLP)
    val tipoUnidad: String,

    val cliente: String,

    val fechaRegistro: Long, // epoch millis

    // Cantidad de compartimentos/cisternas de la unidad, usada por feature-controles-visuales
    // para precompletar la tabla de "Detalle de compartimentos" de sus 3 formularios.
    val cantidadCisternas: Int = 1,

    // Pedido del usuario: al crear una unidad nueva en "Nueva Visita" ya se tenían estos datos
    // tipeados en el mismo formulario (para la SesionServicio) pero se perdían -- cada visita
    // nueva a la misma unidad había que volver a tipearlos de cero. Se guardan acá como "último
    // valor conocido" de la unidad (no un historial), así "Nueva Visita" los puede precargar la
    // próxima vez que se busque la misma patente. Default "" (no null) para que la migración de
    // Room sea un simple ADD COLUMN sin tener que tocar filas viejas.
    val cuit: String = "",
    val patenteTractor: String = "",
    val marcaTractor: String = "",
    val anioTractor: String = "",
    val modeloTractor: String = "",
    val patenteSemi: String = "",
    val marcaSemi: String = "",
    val anioSemi: String = "",
    val modeloSemi: String = ""
)
