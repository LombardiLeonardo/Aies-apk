package com.aies.coredata

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Agrega `cantidadCisternas` a `unidades` (feature-controles-visuales). Default 1 para preservar
 *  el comportamiento previo de las unidades ya registradas por los demás módulos. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE unidades ADD COLUMN cantidadCisternas INTEGER NOT NULL DEFAULT 1")
    }
}

/** Agrega `calibraciones` y `calibracion_cisternas` (feature-calibrado). */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `calibraciones` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `unidadId` INTEGER NOT NULL,
                `transportista` TEXT NOT NULL,
                `cuit` TEXT NOT NULL,
                `fecha` INTEGER NOT NULL,
                `vigenciaAnios` INTEGER NOT NULL,
                `fechaVencimiento` INTEGER NOT NULL,
                `patenteTractor` TEXT NOT NULL,
                `marcaTractor` TEXT NOT NULL,
                `tipoEjesTractor` TEXT NOT NULL,
                `rodadoTractor` TEXT NOT NULL,
                `tara` REAL,
                `maximoPesoPermitido` REAL,
                `patenteSemi` TEXT NOT NULL,
                `marcaSemi` TEXT NOT NULL,
                `modeloSemi` TEXT NOT NULL,
                `tipoEjesSemi` TEXT NOT NULL,
                `rodadoSemi` TEXT NOT NULL,
                `cotaAnterior` REAL,
                `cotaPosterior` REAL,
                `volumenSemi` REAL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_calibraciones_unidadId` ON `calibraciones` (`unidadId`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `calibracion_cisternas` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `calibracionId` INTEGER NOT NULL,
                `cisternaNumero` INTEGER NOT NULL,
                `capacidad` REAL,
                `enraseMm` REAL,
                `vacioMm` REAL,
                `vacioLts` REAL,
                `rebalseMm` REAL,
                `precintoEnrase` TEXT NOT NULL,
                `precintoBoca` TEXT NOT NULL,
                `precintoSoporte` TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_calibracion_cisternas_calibracionId` ON `calibracion_cisternas` (`calibracionId`)")
    }
}

/** Agrega `sesiones_servicio` y `sesion_servicio_items` (Fase 1 del flujo unificado de sesión).
 *  Tablas nuevas, no alteran ninguna existente — sin impacto en los datos de los 6 módulos ya en uso. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `sesiones_servicio` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `unidadId` INTEGER NOT NULL,
                `fecha` INTEGER NOT NULL,
                `auditor` TEXT NOT NULL,
                `cliente` TEXT NOT NULL,
                `domicilio` TEXT NOT NULL,
                `cuit` TEXT NOT NULL,
                `estado` TEXT NOT NULL,
                `horaInicio` INTEGER NOT NULL,
                `horaCierre` INTEGER
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sesiones_servicio_unidadId` ON `sesiones_servicio` (`unidadId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sesiones_servicio_estado` ON `sesiones_servicio` (`estado`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `sesion_servicio_items` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `sesionId` INTEGER NOT NULL,
                `tipoServicioCodigo` TEXT NOT NULL,
                `estado` TEXT NOT NULL,
                `historialServicioId` INTEGER
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sesion_servicio_items_sesionId` ON `sesion_servicio_items` (`sesionId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sesion_servicio_items_tipoServicioCodigo` ON `sesion_servicio_items` (`tipoServicioCodigo`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sesion_servicio_items_historialServicioId` ON `sesion_servicio_items` (`historialServicioId`)")
    }
}

/** Agrega `auditores_catalogo` y `clientes_catalogo` — listas de nombres ya usados en alguna
 *  visita para elegir en Nueva Visita en vez de re-tipear. Tablas nuevas, sin impacto en datos
 *  existentes. */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `auditores_catalogo` (`nombre` TEXT NOT NULL, PRIMARY KEY(`nombre`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `clientes_catalogo` (`nombre` TEXT NOT NULL, PRIMARY KEY(`nombre`))")
    }
}

/** Agrega los 8 campos obligatorios de tractor/semirremolque de la visita a `sesiones_servicio`
 *  (Cambio 1 de "8 campos obligatorios nuevos en Nueva Visita"). DEFAULT '' / DEFAULT 0 porque
 *  SQLite exige un default para ADD COLUMN NOT NULL y ya hay filas de sesiones_servicio
 *  cargadas por pruebas anteriores. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE sesiones_servicio ADD COLUMN patenteTractor TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE sesiones_servicio ADD COLUMN marcaTractor TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE sesiones_servicio ADD COLUMN anioTractor INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE sesiones_servicio ADD COLUMN modeloTractor TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE sesiones_servicio ADD COLUMN patenteSemi TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE sesiones_servicio ADD COLUMN marcaSemi TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE sesiones_servicio ADD COLUMN anioSemi INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE sesiones_servicio ADD COLUMN modeloSemi TEXT NOT NULL DEFAULT ''")
    }
}

/** Asocia el CUIT al nombre de cliente en `clientes_catalogo` — elegir un cliente ya cargado
 *  precarga su CUIT en vez de tener que volver a tipearlo. */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE clientes_catalogo ADD COLUMN cuit TEXT NOT NULL DEFAULT ''")
    }
}

/** Fase F del pase a base de datos compartida: agrega las URLs públicas de Supabase Storage
 *  de los documentos generados, para poder abrirlos desde cualquier dispositivo aunque no
 *  hayan sido generados localmente. DEFAULT '' -- "" significa "todavía no subido". */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE historial_servicios ADD COLUMN documentoUrl TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE sesiones_servicio ADD COLUMN notaTrabajoUrl TEXT NOT NULL DEFAULT ''")
    }
}

/** Extiende `calibraciones`/`calibracion_cisternas` y agrega `calibracion_descarga_patron` para
 *  poder capturar la Planilla de Calibración de Uso Interno (R 14-03) completa, además de la
 *  Tarjeta de Calibración externa que ya se generaba. Todo aditivo/nullable -- sin impacto en
 *  los certificados de calibración ya guardados. */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE calibraciones ADD COLUMN horaInicio INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE calibraciones ADD COLUMN horaFin INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE calibraciones ADD COLUMN documentoInternoPath TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE calibraciones ADD COLUMN documentoInternoUrl TEXT DEFAULT NULL")

        db.execSQL("ALTER TABLE calibracion_cisternas ADD COLUMN sensorMarca TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE calibracion_cisternas ADD COLUMN sensorNumeroSerie TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE calibracion_cisternas ADD COLUMN sensorCumple INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE calibracion_cisternas ADD COLUMN posicionCumple INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE calibracion_cisternas ADD COLUMN posicionObservacion TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE calibracion_cisternas ADD COLUMN litrosSensorRebalse REAL DEFAULT NULL")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `calibracion_descarga_patron` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `calibracionCisternaId` INTEGER NOT NULL,
                `puntoVolumenLts` REAL NOT NULL,
                `numeroLectura` INTEGER,
                `valorMedido` REAL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_calibracion_descarga_patron_calibracionCisternaId` ON `calibracion_descarga_patron` (`calibracionCisternaId`)")
    }
}

/** Agrega `detalle` a `historial_servicios`: texto libre opcional (ej. "Total precintos: 14",
 *  "Comp. 1: Kit de bulones") que la Nota de Trabajo imprime debajo de la fila del servicio.
 *  DEFAULT '' -- "" significa "sin detalle puntual", mismo criterio que documentoUrl. */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE historial_servicios ADD COLUMN detalle TEXT NOT NULL DEFAULT ''")
    }
}

/** Pedido del usuario: "Nueva Visita" ya tipeaba CUIT + patente/marca/modelo/año de tractor y
 *  semi en el mismo formulario que crea la Unidad, pero esos datos se perdían -- ahora quedan
 *  guardados en la unidad como "último valor conocido", para precargarlos la próxima vez que se
 *  busque la misma patente. DEFAULT '' en las 9 columnas, mismo criterio que `detalle` arriba. */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE unidades ADD COLUMN cuit TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE unidades ADD COLUMN patenteTractor TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE unidades ADD COLUMN marcaTractor TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE unidades ADD COLUMN anioTractor TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE unidades ADD COLUMN modeloTractor TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE unidades ADD COLUMN patenteSemi TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE unidades ADD COLUMN marcaSemi TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE unidades ADD COLUMN anioSemi TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE unidades ADD COLUMN modeloSemi TEXT NOT NULL DEFAULT ''")
    }
}
