package com.aies.feature.inspeccionejes.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Base de datos propia de feature-inspeccion-ejes: clientes/unidades/controles/checklists/
 * LPT/fotos (equivalente Room de inspeccion_ejes.db3 / DatabaseService.cs de la app MAUI
 * original). Independiente de la base compartida de core-data — ver HistorialCompartidoHelper
 * para el reflejo en el HistorialServicio compartido.
 */
@Database(
    entities = [Cliente::class, Unidad::class, Control::class, ChecklistLado::class,
        InformeLiquidosPenetrantes::class, PuntaDeEje::class, Foto::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class InspeccionEjesLocalDatabase : RoomDatabase() {
    abstract fun inspeccionEjesDao(): InspeccionEjesDao

    companion object {
        @Volatile
        private var INSTANCE: InspeccionEjesLocalDatabase? = null

        fun getDatabase(context: Context): InspeccionEjesLocalDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    InspeccionEjesLocalDatabase::class.java,
                    "inspeccion_ejes_db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
