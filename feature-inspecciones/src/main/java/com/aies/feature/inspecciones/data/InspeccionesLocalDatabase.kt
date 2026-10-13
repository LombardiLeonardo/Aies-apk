package com.aies.feature.inspecciones.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Base de datos propia de feature-inspecciones: catálogos (empresas, auditores,
 * marcas), observaciones sugeridas e historial detallado por patente para
 * autocompletado/facturación. Es independiente de la base compartida de
 * core-data (ver [com.aies.coredata.AppDatabase]) — cada control finalizado se
 * escribe acá Y, además, se refleja en el HistorialServicio compartido
 * (ver HistorialCompartidoHelper) para que otros módulos lo puedan consultar.
 */
@Database(
    entities = [
        Empresa::class,
        MarcaTractor::class,
        MarcaSemi::class,
        Auditor::class,
        ObservacionSugerida::class,
        ControlRealizado::class
    ],
    version = 4,
    exportSchema = false
)
abstract class InspeccionesLocalDatabase : RoomDatabase() {
    abstract fun inspeccionDao(): InspeccionDao
    abstract fun observacionDao(): ObservacionDao
    abstract fun controlRealizadoDao(): ControlRealizadoDao

    companion object {
        @Volatile
        private var INSTANCE: InspeccionesLocalDatabase? = null

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS observaciones_sugeridas (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        texto TEXT NOT NULL,
                        palabrasClave TEXT NOT NULL DEFAULT '',
                        esPrecargada INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS controles_realizados (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        tipoInspeccion TEXT NOT NULL,
                        empresa TEXT NOT NULL,
                        auditor TEXT NOT NULL,
                        patenteTractor TEXT NOT NULL,
                        marcaTractor TEXT NOT NULL,
                        modeloTractor TEXT NOT NULL,
                        anioTractor TEXT NOT NULL,
                        combustible TEXT NOT NULL,
                        patenteSemi TEXT NOT NULL,
                        marcaSemi TEXT NOT NULL,
                        modeloSemi TEXT NOT NULL,
                        anioSemi TEXT NOT NULL,
                        fechaTimestamp INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        fun getDatabase(context: Context, scope: CoroutineScope): InspeccionesLocalDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    InspeccionesLocalDatabase::class.java,
                    "inspecciones_db"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                    .addCallback(AppDatabaseCallback(scope))
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class AppDatabaseCallback(private val scope: CoroutineScope) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                scope.launch(Dispatchers.IO) {
                    val dao = database.inspeccionDao()

                    // Auditores por defecto
                    listOf("AGUSTIN MORAN","LEONARDO LOMBARDI","MATIAS RAMIREZ","GABRIEL SCABONE", "PABLO GONZALEZ", "AUDITOR GENERAL")
                        .forEach { dao.insertAuditor(Auditor(it)) }

                    // Empresas por defecto
                    listOf("Transchemical", "RUTAUNO MDQ", "New Conti Gruop S.A.", "La Colonia S.A.")
                        .forEach { dao.insertEmpresa(Empresa(it)) }

                    // Marcas de tractor por defecto
                    listOf("Scania", "Volvo", "Iveco", "Mercedes-Benz", "Ford", "Volkswagen")
                        .forEach { dao.insertMarcaTractor(MarcaTractor(it)) }

                    // Marcas de semi por defecto
                    listOf("Randon", "Metalurgica Belgrano", "Fangio", "Danes", "Heil", "Indecar")
                        .forEach { dao.insertMarcaSemi(MarcaSemi(it)) }

                    // Observaciones precargadas
                    val obsDao = database.observacionDao()
                    if (obsDao.countPrecargadas() == 0) {
                        ObservacionesHelper.LISTA_PRECARGADA.forEach { obsDao.insert(it) }
                    }
                }
            }
        }

        // Si la BD ya existía (upgrade), precargamos las observaciones igual
        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            INSTANCE?.let { database ->
                scope.launch(Dispatchers.IO) {
                    val obsDao = database.observacionDao()
                    if (obsDao.countPrecargadas() == 0) {
                        ObservacionesHelper.LISTA_PRECARGADA.forEach { obsDao.insert(it) }
                    }
                }
            }
        }
    }
}
