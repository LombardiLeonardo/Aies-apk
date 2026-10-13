package com.aies.feature.inspeccionglp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Base de datos propia de feature-inspeccion-glp: clientes/vehículos/checklist/informes/
 * resultados/fotos (equivalente Room de inspeccion.db3 / DatabaseService.cs de la app MAUI
 * original). Independiente de la base compartida de core-data — ver HistorialCompartidoHelper
 * para el reflejo en el HistorialServicio compartido.
 */
@Database(
    entities = [Cliente::class, Vehiculo::class, ChecklistPlantilla::class, ItemChecklist::class,
        Informe::class, ItemResultado::class, Foto::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class InspeccionGlpLocalDatabase : RoomDatabase() {
    abstract fun inspeccionGlpDao(): InspeccionGlpDao

    companion object {
        @Volatile
        private var INSTANCE: InspeccionGlpLocalDatabase? = null

        fun getDatabase(context: Context): InspeccionGlpLocalDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    InspeccionGlpLocalDatabase::class.java,
                    "inspeccion_glp_db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
