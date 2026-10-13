package com.aies.feature.controlesvisuales

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private enum class FlujoControl { MENU, INTERNA, EXTERNA, ESPESORES }

/**
 * Entry point del módulo, con navegación interna propia (sin ViewModel, flat Compose — mismo
 * criterio que el resto de la super app). Cada uno de los 3 controles es independiente: pick
 * de dominio -> encabezado -> formulario específico -> generar PDF.
 */
@Composable
fun ControlesVisualesScreen(modifier: Modifier = Modifier) {
    var flujo by remember { mutableStateOf(FlujoControl.MENU) }

    when (flujo) {
        FlujoControl.MENU -> Column(
            modifier = modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Controles Visuales", style = MaterialTheme.typography.titleLarge)
            Text("Elegí el control a realizar", style = MaterialTheme.typography.bodyMedium)
            Button(onClick = { flujo = FlujoControl.INTERNA }, modifier = Modifier.fillMaxWidth()) { Text("Inspección Visual Interna") }
            Button(onClick = { flujo = FlujoControl.EXTERNA }, modifier = Modifier.fillMaxWidth()) { Text("Inspección Visual Externa") }
            Button(onClick = { flujo = FlujoControl.ESPESORES }, modifier = Modifier.fillMaxWidth()) { Text("Control de Espesores") }
        }
        FlujoControl.INTERNA -> ControlVisualInternaScreen(onVolver = { flujo = FlujoControl.MENU })
        FlujoControl.EXTERNA -> ControlVisualExternaScreen(onVolver = { flujo = FlujoControl.MENU })
        FlujoControl.ESPESORES -> ControlEspesoresScreen(onVolver = { flujo = FlujoControl.MENU })
    }
}
