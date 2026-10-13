package com.aies.coredata

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.aies.coredata.dao.AuditorCatalogoDao
import com.aies.coredata.dao.AuditorCatalogoLocalDao
import com.aies.coredata.dao.CalibracionDao
import com.aies.coredata.dao.CalibracionLocalDao
import com.aies.coredata.dao.ClienteCatalogoDao
import com.aies.coredata.dao.ClienteCatalogoLocalDao
import com.aies.coredata.dao.HistorialServicioDao
import com.aies.coredata.dao.HistorialServicioLocalDao
import com.aies.coredata.dao.SesionServicioDao
import com.aies.coredata.dao.SesionServicioLocalDao
import com.aies.coredata.dao.TipoServicioDao
import com.aies.coredata.dao.TipoServicioLocalDao
import com.aies.coredata.dao.UnidadDao
import com.aies.coredata.dao.UnidadLocalDao
import com.aies.coredata.entities.AuditorCatalogo
import com.aies.coredata.entities.Calibracion
import com.aies.coredata.entities.CalibracionCisterna
import com.aies.coredata.entities.CalibracionDescargaPatron
import com.aies.coredata.entities.ClienteCatalogo
import com.aies.coredata.entities.HistorialServicio
import com.aies.coredata.entities.SesionServicio
import com.aies.coredata.entities.SesionServicioItem
import com.aies.coredata.entities.TipoServicio
import com.aies.coredata.entities.Unidad
import com.aies.coredata.remote.SupabaseClientProvider

@Database(
    entities = [
        Unidad::class, TipoServicio::class, HistorialServicio::class,
        Calibracion::class, CalibracionCisterna::class, CalibracionDescargaPatron::class,
        SesionServicio::class, SesionServicioItem::class,
        AuditorCatalogo::class, ClienteCatalogo::class
    ],
    version = 11,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historialServicioLocalDao(): HistorialServicioLocalDao
    abstract fun unidadLocalDao(): UnidadLocalDao
    abstract fun tipoServicioLocalDao(): TipoServicioLocalDao
    abstract fun calibracionLocalDao(): CalibracionLocalDao
    abstract fun sesionServicioLocalDao(): SesionServicioLocalDao
    abstract fun auditorCatalogoLocalDao(): AuditorCatalogoLocalDao
    abstract fun clienteCatalogoLocalDao(): ClienteCatalogoLocalDao

    // Fase D, paso 1 de 5 del pase a base de datos compartida: UnidadDao/TipoServicioDao dejaron
    // de ser @Dao de Room -- ahora son wrappers que hablan contra Supabase primero y usan
    // unidadLocalDao()/tipoServicioLocalDao() (arriba) como cache. Se instancian una sola vez
    // por instancia de AppDatabase (lazy manual, no hace falta nada más elaborado) para que
    // ningún call site existente (14 archivos en 8+ módulos) tenga que cambiar una sola línea.
    @Volatile private var unidadDaoInstance: UnidadDao? = null
    @Volatile private var tipoServicioDaoInstance: TipoServicioDao? = null
    @Volatile private var historialServicioDaoInstance: HistorialServicioDao? = null
    @Volatile private var sesionServicioDaoInstance: SesionServicioDao? = null
    @Volatile private var calibracionDaoInstance: CalibracionDao? = null
    @Volatile private var clienteCatalogoDaoInstance: ClienteCatalogoDao? = null
    @Volatile private var auditorCatalogoDaoInstance: AuditorCatalogoDao? = null

    fun unidadDao(): UnidadDao =
        unidadDaoInstance ?: synchronized(this) {
            unidadDaoInstance ?: UnidadDao(unidadLocalDao(), SupabaseClientProvider.client).also { unidadDaoInstance = it }
        }

    fun tipoServicioDao(): TipoServicioDao =
        tipoServicioDaoInstance ?: synchronized(this) {
            tipoServicioDaoInstance ?: TipoServicioDao(tipoServicioLocalDao(), SupabaseClientProvider.client).also { tipoServicioDaoInstance = it }
        }

    fun historialServicioDao(): HistorialServicioDao =
        historialServicioDaoInstance ?: synchronized(this) {
            historialServicioDaoInstance ?: HistorialServicioDao(historialServicioLocalDao(), SupabaseClientProvider.client).also { historialServicioDaoInstance = it }
        }

    fun sesionServicioDao(): SesionServicioDao =
        sesionServicioDaoInstance ?: synchronized(this) {
            sesionServicioDaoInstance ?: SesionServicioDao(sesionServicioLocalDao(), SupabaseClientProvider.client).also { sesionServicioDaoInstance = it }
        }

    fun calibracionDao(): CalibracionDao =
        calibracionDaoInstance ?: synchronized(this) {
            calibracionDaoInstance ?: CalibracionDao(calibracionLocalDao(), SupabaseClientProvider.client).also { calibracionDaoInstance = it }
        }

    // Clientes/auditores no se compartían entre dispositivos (bug real reportado por el usuario):
    // eran Room puro sin tabla en Supabase. Mismo wrapper que el resto de arriba.
    fun clienteCatalogoDao(): ClienteCatalogoDao =
        clienteCatalogoDaoInstance ?: synchronized(this) {
            clienteCatalogoDaoInstance ?: ClienteCatalogoDao(clienteCatalogoLocalDao(), SupabaseClientProvider.client).also { clienteCatalogoDaoInstance = it }
        }

    fun auditorCatalogoDao(): AuditorCatalogoDao =
        auditorCatalogoDaoInstance ?: synchronized(this) {
            auditorCatalogoDaoInstance ?: AuditorCatalogoDao(auditorCatalogoLocalDao(), SupabaseClientProvider.client).also { auditorCatalogoDaoInstance = it }
        }

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "aies_super_app_db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11).build().also { INSTANCE = it }
            }
        }
    }
}
