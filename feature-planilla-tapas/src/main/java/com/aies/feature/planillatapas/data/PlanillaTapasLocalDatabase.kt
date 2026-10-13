package com.aies.feature.planillatapas.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/** Base de datos propia de feature-planilla-tapas: el registro de planilla de tapas y sus
 *  tablas hijas de detalle por compartimiento y checklist. Independiente de core-data — ver
 *  HistorialCompartidoHelper (dual-write a HistorialServicio) y PlanillaTapasSupabaseHelper
 *  (escritura directa a las 3 tablas remotas nuevas de Supabase). */
@Database(
    entities = [PlanillaTapasRegistro::class, DetalleCompartimentoTapas::class, ItemChecklistTapas::class],
    version = 1,
    exportSchema = false
)
abstract class PlanillaTapasLocalDatabase : RoomDatabase() {
    abstract fun planillaTapasDao(): PlanillaTapasDao

    companion object {
        @Volatile
        private var INSTANCE: PlanillaTapasLocalDatabase? = null

        fun getDatabase(context: Context): PlanillaTapasLocalDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PlanillaTapasLocalDatabase::class.java,
                    "planilla_tapas_db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
