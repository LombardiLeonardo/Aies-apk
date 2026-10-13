package com.aies.feature.inspecciones

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aies.feature.inspecciones.ui.InspeccionesMenuActivity

/**
 * Entrada del destino "Inspecciones" del bottom nav de la super app.
 * El flujo en sí (selección de empresa/auditor, datos del equipo, los seis
 * checklists LIVIANOS/PESADOS/COQUE/QUIMICOS/ENGANCHE/AXION, generación de
 * Excel/PDF e historial) se portó tal cual de la app standalone como
 * Activities + XML — ver el paquete `ui` — porque reescribirlo en Compose
 * hoy no aportaba nada y multiplicaba el riesgo de romper las plantillas
 * .xls celda-por-celda. Esta pantalla solo abre ese flujo.
 */
@Composable
fun InspeccionesScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Box(
        modifier = modifier.fillMaxSize().padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Button(onClick = {
            context.startActivity(Intent(context, InspeccionesMenuActivity::class.java))
        }) {
            Text("Iniciar inspección")
        }
    }
}
