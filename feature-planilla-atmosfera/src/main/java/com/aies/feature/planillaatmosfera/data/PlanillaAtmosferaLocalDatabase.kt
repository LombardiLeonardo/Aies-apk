package com.aies.feature.planillaatmosfera.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/** Base de datos propia de feature-planilla-atmosfera, independiente de core-data. */
@Database(
    entities = [PlanillaAtmosferaRegistro::class, AtmosferaCisterna::class],
    version = 1,
    exportSchema = false
)
abstract class PlanillaAtmosferaLocalDatabase : RoomDatabase() {
    abstract fun planillaAtmosferaDao(): PlanillaAtmosferaDao

    companion object {
        @Volatile
        private var INSTANCE: PlanillaAtmosferaLocalDatabase? = null

        fun getDatabase(context: Context): PlanillaAtmosferaLocalDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PlanillaAtmosferaLocalDatabase::class.java,
                    "planilla_atmosfera_db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
