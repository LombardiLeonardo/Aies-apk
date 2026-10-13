package com.aies.feature.repuestos.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/** Base de datos propia de feature-repuestos: el registro de cambio de repuestos y su tabla
 *  hija de detalle por compartimiento. Independiente de core-data — ver HistorialCompartidoHelper. */
@Database(
    entities = [RepuestoRegistro::class, DetalleRepuestoCisterna::class],
    version = 1,
    exportSchema = false
)
abstract class RepuestosLocalDatabase : RoomDatabase() {
    abstract fun repuestosDao(): RepuestosDao

    companion object {
        @Volatile
        private var INSTANCE: RepuestosLocalDatabase? = null

        fun getDatabase(context: Context): RepuestosLocalDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    RepuestosLocalDatabase::class.java,
                    "repuestos_db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
