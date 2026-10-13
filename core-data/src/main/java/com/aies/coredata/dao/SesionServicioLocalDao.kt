package com.aies.coredata.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aies.coredata.entities.SesionServicio
import com.aies.coredata.entities.SesionServicioItem

/** Mismo patrón que ServicioDelDia (HistorialServicioDao): trae el nombre ya resuelto contra el catálogo. */
data class ItemDeSesion(
    val id: Long,
    val sesionId: Long,
    val tipoServicioCodigo: String,
    val tipoServicioNombre: String,
    val estado: String,
    val historialServicioId: Long?
)

/**
 * Fase D, paso 3 de 5 del pase a base de datos compartida: esta interfaz Room es ahora solo la
 * cache local -- ver [SesionServicioDao] (misma carpeta) para la clase pública que el resto de la
 * app sigue llamando, que habla contra Supabase primero y usa esta cache como fallback/espejo.
 */
@Dao
interface SesionServicioLocalDao {

    // REPLACE (antes sin especificar, o sea ABORT): esta cache ahora también se puebla en cada
    // lectura remota exitosa (no solo en el insert original), así que un mismo id remoto puede
    // volver a insertarse.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun crearSesion(sesion: SesionServicio): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun agregarItems(items: List<SesionServicioItem>)

    @Query("SELECT * FROM sesiones_servicio WHERE estado = 'EN_PROGRESO'")
    suspend fun sesionesAbiertas(): List<SesionServicio>

    @Query("SELECT * FROM sesiones_servicio WHERE id = :sesionId LIMIT 1")
    suspend fun getPorId(sesionId: Long): SesionServicio?

    /** Para precargar patenteTractor en pantallas que solo conocen la Unidad (semi) y una fecha,
     *  no la sesión que la originó -- ver NotaTrabajoScreen (feature-nota-trabajo). La más
     *  reciente por si hubo más de una visita a la misma unidad ese día. */
    @Query("SELECT * FROM sesiones_servicio WHERE unidadId = :unidadId AND fecha BETWEEN :inicioDelDia AND :finDelDia ORDER BY fecha DESC LIMIT 1")
    suspend fun sesionDeUnidadEnElDia(unidadId: Long, inicioDelDia: Long, finDelDia: Long): SesionServicio?

    /** Ver doc de [sesionDeUnidadEnElDia] -- misma necesidad, pero para agrupar TODAS las
     *  sesiones de un día de una sola vez (NotaTrabajoScreen), en vez de una consulta por
     *  unidad_id. `sesiones_servicio.unidadId` solo guarda el id de UNA de las dos patas (la
     *  que se eligió al arrancar "Nueva Visita", normalmente el tractor) -- la otra pata solo
     *  queda identificada por patenteTractor/patenteSemi como texto, así que agrupar por
     *  unidad_id deja a esa segunda pata sin sesión asociada. */
    @Query("SELECT * FROM sesiones_servicio WHERE fecha BETWEEN :inicioDelDia AND :finDelDia")
    suspend fun sesionesDelDia(inicioDelDia: Long, finDelDia: Long): List<SesionServicio>

    @Query(
        """
        SELECT si.id AS id, si.sesionId AS sesionId, si.tipoServicioCodigo AS tipoServicioCodigo,
               ts.nombre AS tipoServicioNombre, si.estado AS estado, si.historialServicioId AS historialServicioId
        FROM sesion_servicio_items si
        INNER JOIN tipos_servicio ts ON ts.codigo = si.tipoServicioCodigo
        WHERE si.sesionId = :sesionId
        """
    )
    suspend fun itemsDeSesion(sesionId: Long): List<ItemDeSesion>

    @Query("UPDATE sesion_servicio_items SET estado = 'COMPLETO', historialServicioId = :historialServicioId WHERE id = :itemId")
    suspend fun marcarItemCompleto(itemId: Long, historialServicioId: Long)

    @Query("DELETE FROM sesion_servicio_items WHERE id = :itemId")
    suspend fun eliminarItem(itemId: Long)

    @Query("SELECT COUNT(*) FROM sesion_servicio_items WHERE sesionId = :sesionId AND estado != 'COMPLETO'")
    suspend fun cantidadItemsPendientes(sesionId: Long): Int

    suspend fun todosLosItemsCompletos(sesionId: Long): Boolean = cantidadItemsPendientes(sesionId) == 0

    @Query("UPDATE sesiones_servicio SET estado = 'CERRADA', horaCierre = :horaCierre WHERE id = :sesionId")
    suspend fun cerrarSesion(sesionId: Long, horaCierre: Long)

    @Query("UPDATE sesiones_servicio SET notaTrabajoUrl = :url WHERE id = :sesionId")
    suspend fun actualizarNotaTrabajoUrl(sesionId: Long, url: String)
}
