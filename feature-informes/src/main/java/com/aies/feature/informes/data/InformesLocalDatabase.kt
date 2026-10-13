package com.aies.feature.informes.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Base de datos propia de feature-informes: clientes (transportistas) e historial de
 * informes de carga segura (equivalente Room de aies_informe.db / models.py de la app
 * Flask original). Independiente de core-data — ver HistorialCompartidoHelper.
 */
@Database(entities = [Cliente::class, InformeRegistro::class], version = 1, exportSchema = false)
abstract class InformesLocalDatabase : RoomDatabase() {
    abstract fun informesDao(): InformesDao

    companion object {
        @Volatile
        private var INSTANCE: InformesLocalDatabase? = null

        fun getDatabase(context: Context): InformesLocalDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    InformesLocalDatabase::class.java,
                    "informes_db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
