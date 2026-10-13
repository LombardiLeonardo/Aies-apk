package com.aies.feature.inspecciones.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle

object GestorReportes {
    private var inspeccionesPendientes = mutableListOf<String>()
    private val urisAdjuntosGenerados = arrayListOf<Uri>()
    private var datosVehiculo = Bundle()

    // 1. Recibe la lista de inspecciones y los datos (patente, empresa, etc.)
    fun iniciar(context: Context, tipos: List<String>, datos: Bundle) {
        inspeccionesPendientes.clear()
        inspeccionesPendientes.addAll(tipos)
        urisAdjuntosGenerados.clear()
        datosVehiculo = datos

        siguienteInspeccion(context)
    }

    // 2. Las Activities llaman a esto cuando terminan de generar sus archivos
    fun agregarAdjuntosYContinuar(context: Context, nuevosUris: List<Uri>) {
        urisAdjuntosGenerados.addAll(nuevosUris)
        siguienteInspeccion(context)
    }

    // 3. Decide si abre la siguiente pantalla o si ya manda el mail
    private fun siguienteInspeccion(context: Context) {
        if (inspeccionesPendientes.isEmpty()) {
            enviarCorreo(context)
            return
        }

        val proxima = inspeccionesPendientes.removeAt(0)
        val intent = when (proxima) {
            "ENGANCHE" -> Intent(context, EngancheActivity::class.java)
            "AXION" -> Intent(context, AxionActivity::class.java)
            else -> Intent(context, InspeccionActivity::class.java).apply {
                putExtra("TIPO_INSPECCION", proxima)
            }
        }

        // Le volvemos a inyectar todos los datos del camión a la pantalla que se abre
        intent.putExtras(datosVehiculo)
        context.startActivity(intent)
    }

    // 4. Junta todos los archivos de la mochila y abre Gmail — pero no si viene de una sesión
    // del flujo unificado (sesionId != -1L): ahí el envío se consolida una sola vez al final
    // de la visita entera (ver "Finalizar visita" en feature-sesion), no por cada servicio.
    private fun enviarCorreo(context: Context) {
        if (datosVehiculo.getLong("sesionId", -1L) != -1L) return

        val patente = datosVehiculo.getString("PATENTE_TRACTOR", "")
        val emailIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_EMAIL, arrayOf("info@aies.com.ar"))
            putExtra(Intent.EXTRA_SUBJECT, "Controles Múltiples - Patente: $patente")
            putExtra(Intent.EXTRA_TEXT, "Adjuntamos todos los reportes y planillas generados para la unidad $patente.")
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, urisAdjuntosGenerados)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        // Ver el mismo bugfix en feature-sesion.SesionScreen.compartirDocumentosDeVisita:
        // los adjuntos pueden venir de autoridades content:// distintas (FileProvider de cada
        // módulo), y el flag del intent solo no siempre alcanza para que la app de mail
        // elegida pueda leerlos todos -- se otorga el permiso explícito a cada candidata.
        for (info in context.packageManager.queryIntentActivities(emailIntent, 0)) {
            val paquete = info.activityInfo?.packageName ?: continue
            urisAdjuntosGenerados.forEach { uri ->
                try { context.grantUriPermission(paquete, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            }
        }
        // Ver el mismo comentario en feature-sesion.SesionScreen.compartirDocumentosDeVisita --
        // sin este try/catch, si nada puede resolver el chooser, el técnico no se enteraba de
        // que el correo nunca se armó.
        try {
            context.startActivity(Intent.createChooser(emailIntent, "Enviando reportes..."))
        } catch (e: Exception) {
            android.widget.Toast.makeText(context, "No se pudo abrir el selector para enviar: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }
    }
}
