package com.aies.feature.inspecciones.ui

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LifecycleCoroutineScope
import com.aies.feature.inspecciones.data.InspeccionesLocalDatabase
import com.aies.feature.inspecciones.data.ObservacionSugerida
import com.aies.feature.inspecciones.data.ObservacionesHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Flujo compartido para mostrar el diálogo de observaciones de un ítem:
 * busca sugerencias en Room (con fallback a la lista precargada), muestra
 * [ObservacionesDialog] y persiste la observación nueva si el usuario la agregó.
 */
object ObservacionFlow {

    fun mostrarDialogo(
        activity: AppCompatActivity,
        scope: LifecycleCoroutineScope,
        descripcionItem: String,
        textoExistente: String,
        onGuardado: (textoFinal: String) -> Unit
    ) {
        scope.launch(Dispatchers.IO) {
            val db = InspeccionesLocalDatabase.getDatabase(activity, scope)
            val dbObs = db.observacionDao().getAllOnce()
            // Si la BD está vacía (ej: primer inicio), usar la lista estática como fallback
            val todasObs = if (dbObs.isEmpty()) ObservacionesHelper.LISTA_PRECARGADA else dbObs
            val sugerencias = ObservacionesHelper.sugerenciasParaItem(descripcionItem, todasObs)

            withContext(Dispatchers.Main) {
                ObservacionesDialog(
                    context = activity,
                    sugerencias = sugerencias,
                    textoActual = textoExistente
                ) { textoFinal, nuevaObservacion ->
                    // BUGFIX (reportado por el usuario: "al cerrarlas y volverlas a abrir se
                    // duplica lo que ya estaba escrito"): ObservacionesDialog ya precarga el
                    // campo con textoExistente y deja el cursor al final (ver
                    // ObservacionesDialog.kt:56-57) -- el usuario edita/extiende ese mismo texto
                    // in situ, así que textoFinal YA es el texto completo correcto. Antes acá se
                    // sumaba textoExistente de nuevo como línea aparte cada vez que textoFinal
                    // difería del original, aunque esa diferencia fuera solo la extensión normal
                    // del mismo texto -- el resultado quedaba con el texto viejo duplicado.
                    onGuardado(textoFinal)

                    if (nuevaObservacion != null) {
                        scope.launch(Dispatchers.IO) {
                            db.observacionDao().insert(
                                ObservacionSugerida(
                                    texto = nuevaObservacion,
                                    palabrasClave = "",
                                    esPrecargada = false
                                )
                            )
                        }
                    }
                }.show()
            }
        }
    }
}
