package com.aies.feature.controlesvisuales.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/** Base de datos propia de feature-controles-visuales: los 3 controles (interna/externa/
 *  espesores) y sus tablas hijas. Independiente de core-data — ver HistorialCompartidoHelper. */
@Database(
    entities = [
        ControlVisualInterna::class, DetalleCompartimentoInterna::class, VerificacionCisternaInterna::class,
        ControlVisualExterna::class, DetalleCompartimentoExterna::class, ItemChecklistExterna::class,
        ControlEspesores::class, DetalleCompartimentoEspesores::class, MedicionEspesor::class
    ],
    version = 1,
    exportSchema = false
)
abstract class ControlesVisualesLocalDatabase : RoomDatabase() {
    abstract fun controlesVisualesDao(): ControlesVisualesDao

    companion object {
        @Volatile
        private var INSTANCE: ControlesVisualesLocalDatabase? = null

        fun getDatabase(context: Context): ControlesVisualesLocalDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ControlesVisualesLocalDatabase::class.java,
                    "controles_visuales_db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
