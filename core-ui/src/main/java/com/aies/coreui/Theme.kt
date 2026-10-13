package com.aies.coreui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// Tema único que usan los ocho feature modules, así toda la super app se ve consistente sin
// importar de qué app vieja vino cada pantalla.
//
// Deliberadamente SIEMPRE claro, sin seguir isSystemInDarkTheme(): el tema de la Activity en
// AndroidManifest.xml está fijo en Theme.Material.Light (la ventana nunca pinta un fondo
// oscuro), y ningún Composable de la super app envuelve su contenido en un Surface que sí lo
// haga. Antes, con el teléfono en modo oscuro del sistema, esta función elegía la paleta
// darkColorScheme() (pensada para texto claro sobre fondo oscuro) mientras el fondo real seguía
// siendo claro, así que el texto quedaba en un gris/lavanda lavado e ilegible — el bug real
// detrás del "texto gris" reportado en Nueva Visita, que forzar onSurface por sí solo no arregla
// porque ese onSurface era el del esquema oscuro.
private val LightColors = lightColorScheme()

@Composable
fun AiesSuperAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightColors, content = content)
}
