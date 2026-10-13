package com.aies.feature.certificados.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Base de datos propia de feature-certificados: transportistas, correlativo y las
 * últimas emisiones (equivalente Room de certificados.db / ConexionBaseDatos.cs de la
 * app de escritorio original). Independiente de la base compartida de core-data — ver
 * HistorialCompartidoHelper para el reflejo en el HistorialServicio compartido.
 */
@Database(entities = [EmpresaTransportista::class, CertificadoRegistro::class], version = 1, exportSchema = false)
abstract class CertificadosLocalDatabase : RoomDatabase() {
    abstract fun certificadosDao(): CertificadosDao

    companion object {
        @Volatile
        private var INSTANCE: CertificadosLocalDatabase? = null

        fun getDatabase(context: Context): CertificadosLocalDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    CertificadosLocalDatabase::class.java,
                    "certificados_db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
