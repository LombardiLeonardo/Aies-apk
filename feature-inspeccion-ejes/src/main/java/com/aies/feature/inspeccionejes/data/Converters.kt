package com.aies.feature.inspeccionejes.data

import androidx.room.TypeConverter

/** Room persiste cada enum como su nombre en texto plano. */
class Converters {
    @TypeConverter fun ladoAString(v: Lado): String = v.name
    @TypeConverter fun stringALado(v: String): Lado = Lado.valueOf(v)

    @TypeConverter fun arandelaAString(v: ArandelaSeguridadEstado): String = v.name
    @TypeConverter fun stringAArandela(v: String): ArandelaSeguridadEstado = ArandelaSeguridadEstado.valueOf(v)

    @TypeConverter fun lubricanteAString(v: LubricanteEstado): String = v.name
    @TypeConverter fun stringALubricante(v: String): LubricanteEstado = LubricanteEstado.valueOf(v)

    @TypeConverter fun rodamientoAString(v: RodamientoEstado): String = v.name
    @TypeConverter fun stringARodamiento(v: String): RodamientoEstado = RodamientoEstado.valueOf(v)

    @TypeConverter fun frenoAString(v: FrenoEstado): String = v.name
    @TypeConverter fun stringAFreno(v: String): FrenoEstado = FrenoEstado.valueOf(v)

    @TypeConverter fun retenesAString(v: RetenesEstado): String = v.name
    @TypeConverter fun stringARetenes(v: String): RetenesEstado = RetenesEstado.valueOf(v)

    @TypeConverter fun resultadoAString(v: ResultadoEstado): String = v.name
    @TypeConverter fun stringAResultado(v: String): ResultadoEstado = ResultadoEstado.valueOf(v)

    @TypeConverter fun tipoFotoAString(v: TipoFoto): String = v.name
    @TypeConverter fun stringATipoFoto(v: String): TipoFoto = TipoFoto.valueOf(v)
}
