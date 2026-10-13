package com.aies.feature.inspeccionglp.data.cloud

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Fase D del pase a base de datos compartida: estos DTOs ya NO reflejan el proyecto MAUI
 * original (ver [ClienteCloud]/[VehiculoCloud]/[InformeCloud] para las diferencias puntuales)
 * sino el esquema `_glp` del proyecto Supabase compartido, aplicado en Fase B — confirmado
 * columna por columna contra el OpenAPI de PostgREST antes de escribir esto, no supuesto.
 * `fecha`/`fechaCreacion`/`fechaModificacion` viajan como timestamps ISO-8601 (`String`) porque
 * Postgrest-kt serializa `timestamptz` como texto, no como epoch millis.
 */
@Serializable
data class ChecklistPlantillaCloud(
    val id: String,
    val nombre: String,
    val descripcion: String? = null
)

@Serializable
data class ClienteCloud(
    val id: String,
    @SerialName("razon_social") val razonSocial: String,
    val direccion: String? = null,
    val contacto: String? = null,
    @SerialName("fecha_creacion") val fechaCreacion: String,
    @SerialName("fecha_modificacion") val fechaModificacion: String
)

/**
 * `vehiculos_glp` en el esquema compartido YA NO tiene patente/modelo/marca-semi/fabricante como
 * columnas propias: la patente de cada pata vive en la `unidades` compartida
 * ([unidad_tractor_id]/[unidad_semi_id], resueltas/creadas ahí por [SyncService] antes de subir
 * este DTO). `marcaTractor`/`taraSemirremolque`/`capacidadSemirremolque`/`bandera`/
 * `numeroMatricula` son los únicos campos específicos de GLP que sobrevivieron en la nube —
 * `modeloTractor`, `modeloSemirremolque`, `marcaSemirremolque` y `fabricanteSemirremolque`
 * quedaron SIN equivalente remoto (ni en `vehiculos_glp` ni en `unidades`): siguen existiendo en
 * el `Vehiculo` local de Room, pero ya no viajan a Supabase. Si hace falta conservarlos en la
 * nube hay que agregar esas columnas al esquema — no es algo que se pueda resolver solo del
 * lado de la app.
 */
@Serializable
data class VehiculoCloud(
    val id: String,
    @SerialName("cliente_id") val clienteId: String,
    @SerialName("unidad_tractor_id") val unidadTractorId: Long,
    @SerialName("unidad_semi_id") val unidadSemiId: Long,
    @SerialName("marca_tractor") val marcaTractor: String? = null,
    @SerialName("tara_semirremolque") val taraSemirremolque: Double? = null,
    @SerialName("capacidad_semirremolque") val capacidadSemirremolque: Double? = null,
    val bandera: String? = null,
    @SerialName("numero_matricula") val numeroMatricula: String? = null,
    @SerialName("fecha_creacion") val fechaCreacion: String,
    @SerialName("fecha_modificacion") val fechaModificacion: String
)

/** Fila de la `unidades` compartida (misma tabla que usan Calibrado/Informes/Ejes/Inspecciones
 *  para TRACTOR/SEMI) — acá solo para resolver/crear la unidad de cada pata de un vehículo GLP. */
@Serializable
data class UnidadRemotaCloud(
    val id: Long = 0,
    val placa: String,
    @SerialName("tipo_unidad") val tipoUnidad: String,
    val cliente: String,
    @SerialName("fecha_registro") val fechaRegistro: Long,
    @SerialName("cantidad_cisternas") val cantidadCisternas: Int = 1
)

@Serializable
data class ItemChecklistCloud(
    val id: String,
    @SerialName("plantilla_id") val plantillaId: String,
    val categoria: String,
    val subcategoria: String? = null,
    val numero: Int,
    val descripcion: String,
    @SerialName("tipo_incumplimiento") val tipoIncumplimiento: String,
    @SerialName("aplica_fob") val aplicaFob: Boolean,
    @SerialName("aplica_cif") val aplicaCif: Boolean,
    val orden: Int
)

/**
 * `informes_glp` ya no tiene `cliente_id` propio (se resuelve indirecto vía
 * `vehiculo_glp_id` → `vehiculos_glp.cliente_id`) — `vehiculoId` local pasa directo a
 * [vehiculoGlpId] porque `vehiculos_glp.id` es el mismo UUID que ya usa el `Vehiculo` local
 * (no hace falta re-resolverlo). `historialServicioId` es una columna nueva (FK opcional a
 * `historial_servicios`, la tabla que alimenta la Nota de Trabajo del flujo de sesión) que esta
 * fase deja sin usar a propósito: escribir ahí de verdad implica que GLP registre un
 * HistorialServicio remoto como hacen los demás módulos, que es un alcance mayor (ligar GLP al
 * flujo de sesión/Nota de Trabajo) no pedido en esta tarea — queda null.
 */
@Serializable
data class InformeCloud(
    val id: String,
    @SerialName("numero_informe") val numeroInforme: String,
    @SerialName("vehiculo_glp_id") val vehiculoGlpId: String,
    @SerialName("plantilla_id") val plantillaId: String,
    val fecha: String,
    val estado: String,
    @SerialName("ruta_docx") val rutaDocx: String? = null,
    @SerialName("historial_servicio_id") val historialServicioId: Long? = null,
    @SerialName("fecha_creacion") val fechaCreacion: String,
    @SerialName("fecha_modificacion") val fechaModificacion: String
)

@Serializable
data class ItemResultadoCloud(
    val id: String,
    @SerialName("informe_id") val informeId: String,
    @SerialName("item_checklist_id") val itemChecklistId: String,
    val estado: String,
    val comentario: String? = null,
    @SerialName("fecha_creacion") val fechaCreacion: String,
    @SerialName("fecha_modificacion") val fechaModificacion: String
)

@Serializable
data class FotoCloud(
    val id: String,
    @SerialName("item_resultado_id") val itemResultadoId: String,
    @SerialName("ruta_archivo") val rutaArchivo: String, // URL pública del Storage, no la ruta local del dispositivo
    @SerialName("fecha_creacion") val fechaCreacion: String
)
