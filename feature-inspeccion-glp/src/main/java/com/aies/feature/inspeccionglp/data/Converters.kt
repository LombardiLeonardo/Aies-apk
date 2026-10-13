package com.aies.feature.inspeccionglp.data

import androidx.room.TypeConverter

/** Room persiste cada enum como su nombre en texto plano. */
class Converters {
    @TypeConverter fun estadoItemAString(v: EstadoItemResultado): String = v.name
    @TypeConverter fun stringAEstadoItem(v: String): EstadoItemResultado = EstadoItemResultado.valueOf(v)

    @TypeConverter fun tipoIncumplimientoAString(v: TipoIncumplimiento): String = v.name
    @TypeConverter fun stringATipoIncumplimiento(v: String): TipoIncumplimiento = TipoIncumplimiento.valueOf(v)

    @TypeConverter fun estadoInformeAString(v: EstadoInforme): String = v.name
    @TypeConverter fun stringAEstadoInforme(v: String): EstadoInforme = EstadoInforme.valueOf(v)
}
