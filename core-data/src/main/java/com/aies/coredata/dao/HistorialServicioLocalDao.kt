package com.aies.coredata.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aies.coredata.entities.HistorialServicio

data class ServicioDelDia(
    val tipoServicioCodigo: String,
    val tipoServicioNombre: String,
    val auditor: String,
    val documentoPath: String,
    val documentoUrl: String,
    val fecha: Long,
    val detalle: String = ""
)

/**
 * Fase D, paso 2 de 5 del pase a base de datos compartida: esta interfaz Room es ahora solo la
 * cache local -- ver [HistorialServicioDao] (misma carpeta) para la clase pública que el resto de
 * la app sigue llamando, que habla contra Supabase primero y usa esta cache como fallback/espejo.
 */
@Dao
interface HistorialServicioLocalDao {

    // REPLACE (antes ABORT): esta cache ahora también se puebla en cada lectura remota exitosa
    // (no solo en el insert original), así que un mismo id remoto puede volver a insertarse.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertar(servicio: HistorialServicio): Long

    @Query(
        """
        SELECT hs.tipoServicioCodigo AS tipoServicioCodigo,
               ts.nombre AS tipoServicioNombre,
               hs.auditor AS auditor,
               hs.documentoPath AS documentoPath,
               hs.documentoUrl AS documentoUrl,
               hs.fecha AS fecha,
               hs.detalle AS detalle
        FROM historial_servicios hs
        INNER JOIN tipos_servicio ts ON ts.codigo = hs.tipoServicioCodigo
        WHERE hs.unidadId = :unidadId
          AND hs.fecha BETWEEN :inicioDelDia AND :finDelDia
        ORDER BY hs.fecha ASC
        """
    )
    suspend fun serviciosDeLaUnidadEnElDia(unidadId: Long, inicioDelDia: Long, finDelDia: Long): List<ServicioDelDia>

    /**
     * Mismo criterio que [serviciosDeLaUnidadEnElDia] pero contra VARIAS unidades a la vez —
     * cada módulo que maneja tractor+semi (Calibrado/Certificados/Informes/Ejes/GLP) escribe el
     * mismo documento dos veces en historial_servicios, una fila por unidadId (tractor y semi),
     * no necesariamente la misma Unidad que la sesión resolvió con su propio campo "Patente".
     * Buscar contra las 3 unidades de la visita (la de sesión + tractor + semi) en vez de una
     * sola es lo que evita que "Finalizar visita" se quede corto con la Nota de Trabajo y el
     * envío consolidado. `GROUP BY documentoPath` deduplica el mismo documento cuando aparece
     * bajo más de una de esas unidades.
     */
    @Query(
        """
        SELECT hs.tipoServicioCodigo AS tipoServicioCodigo,
               ts.nombre AS tipoServicioNombre,
               hs.auditor AS auditor,
               hs.documentoPath AS documentoPath,
               hs.documentoUrl AS documentoUrl,
               MIN(hs.fecha) AS fecha,
               hs.detalle AS detalle
        FROM historial_servicios hs
        INNER JOIN tipos_servicio ts ON ts.codigo = hs.tipoServicioCodigo
        WHERE hs.unidadId IN (:unidadIds)
          AND hs.fecha BETWEEN :inicioDelDia AND :finDelDia
        GROUP BY hs.documentoPath, hs.tipoServicioCodigo, ts.nombre, hs.auditor
        ORDER BY fecha ASC
        """
    )
    suspend fun serviciosDeVariasUnidadesEnElDia(unidadIds: List<Long>, inicioDelDia: Long, finDelDia: Long): List<ServicioDelDia>

    @Query(
        """
        SELECT DISTINCT unidadId FROM historial_servicios
        WHERE fecha BETWEEN :inicioDelDia AND :finDelDia
        """
    )
    suspend fun unidadesConServicioEnElDia(inicioDelDia: Long, finDelDia: Long): List<Long>

    @Query("UPDATE historial_servicios SET documentoUrl = :url WHERE id = :id")
    suspend fun actualizarDocumentoUrl(id: Long, url: String)
}
