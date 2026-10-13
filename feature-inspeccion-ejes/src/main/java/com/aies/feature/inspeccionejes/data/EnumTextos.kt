package com.aies.feature.inspeccionejes.data

/**
 * Texto en español para mostrar en la UI y en el informe Word — el original solo usaba
 * este mapeo en el informe (el Picker de MAUI mostraba el enum crudo, ej.
 * "DefectosEnRodillos", sin espacios); acá se usa también en los dropdowns de la app.
 */
fun ArandelaSeguridadEstado.aTexto(): String = when (this) {
    ArandelaSeguridadEstado.Correcta -> "OK"
    ArandelaSeguridadEstado.Deteriorada -> "Deteriorada"
    ArandelaSeguridadEstado.MalColocada -> "Mal colocada"
    ArandelaSeguridadEstado.Inexistente -> "Inexistente"
}

fun LubricanteEstado.aTexto(): String = when (this) {
    LubricanteEstado.Correcto -> "OK"
    LubricanteEstado.ContaminacionParticulasMetalicas -> "Contaminación por partículas metálicas"
    LubricanteEstado.ContaminacionParticulasExternasOLiquidos -> "Contaminación por partículas externas o líquidos"
    LubricanteEstado.DeterioroPropiedades -> "Deterioro de propiedades (viscosidad)"
    LubricanteEstado.EscasaCantidad -> "Escasa cantidad de lubricante"
}

fun RodamientoEstado.aTexto(): String = when (this) {
    RodamientoEstado.Correcto -> "OK"
    RodamientoEstado.DefectosEnRodillos -> "Defectos en rodillos (picadura o desgaste)"
    RodamientoEstado.PistasConPicaduraODesgaste -> "Pistas con picadura o desgaste"
    RodamientoEstado.JaulaRota -> "Jaula rota"
}

fun FrenoEstado.aTexto(): String = when (this) {
    FrenoEstado.Correcta -> "OK"
    FrenoEstado.DesgasteIrregular -> "Desgaste irregular"
    FrenoEstado.FisuraVisible -> "Fisura visible"
    FrenoEstado.NoAplica -> "N/A"
}

fun RetenesEstado.aTexto(): String = when (this) {
    RetenesEstado.OK -> "OK"
    RetenesEstado.SeReemplazoLaPieza -> "Se reemplazó la pieza"
}

fun ResultadoEstado.aTexto(): String = when (this) {
    ResultadoEstado.Apto -> "APTO"
    ResultadoEstado.NoApto -> "NO APTO"
}
