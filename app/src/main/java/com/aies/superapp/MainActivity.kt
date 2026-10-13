package com.aies.superapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import com.aies.coreui.AiesSuperAppTheme
import com.aies.feature.sesion.SesionScreen

/**
 * Shell único de la super app: fin del bottom nav de 8 destinos (Fase 4). Toda la navegación
 * ahora vive dentro de feature-sesion (su propio back-stack de Paso) — :app solo monta esa
 * pantalla. Nota de trabajo, Ejes y GLP se acceden desde la pantalla de inicio de sesión
 * como accesos secundarios, no como destinos de :app.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AiesSuperAppTheme {
                SuperAppShell()
            }
        }
    }
}

@Composable
fun SuperAppShell() {
    SesionScreen()
}
