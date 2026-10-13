package com.aies.feature.inspecciones.data

/**
 * Lista precargada de observaciones con sus palabras clave.
 * Se inserta una sola vez en la base de datos al iniciar la app.
 */
object ObservacionesHelper {

    val LISTA_PRECARGADA = listOf(

        // ── Neumáticos / Llantas ────────────────────────────────────────
        ObservacionSugerida(texto = "Reemplazar neumáticos del primer eje del semirremolque",
            palabrasClave = "neumatico,llanta,rueda", esPrecargada = true),
        ObservacionSugerida(texto = "Reemplazar neumáticos del segundo eje del semirremolque",
            palabrasClave = "neumatico,llanta,rueda", esPrecargada = true),
        ObservacionSugerida(texto = "Reemplazar neumáticos del tractor (eje delantero)",
            palabrasClave = "neumatico,llanta,rueda", esPrecargada = true),
        ObservacionSugerida(texto = "Reemplazar neumáticos del tractor (eje trasero)",
            palabrasClave = "neumatico,llanta,rueda", esPrecargada = true),
        ObservacionSugerida(texto = "Neumático con corte en banda de rodamiento",
            palabrasClave = "neumatico,llanta,rueda", esPrecargada = true),
        ObservacionSugerida(texto = "Neumático con desgaste irregular — verificar alineación",
            palabrasClave = "neumatico,llanta,rueda", esPrecargada = true),
        ObservacionSugerida(texto = "Verificar testigo de torque en bujes",
            palabrasClave = "neumatico,llanta,rueda", esPrecargada = true),

        // ── Luces / Señalizaciones ──────────────────────────────────────
        ObservacionSugerida(texto = "Reemplazar acrílicos de faros del tractor",
            palabrasClave = "luz,foco,señalizacion,señalización,señal,lampara,lámpara,farol,faro,antiniebla,giro,stop,retroceso,maria", esPrecargada = true),
        ObservacionSugerida(texto = "Reparar el funcionamiento de las 3 marías delanteras",
            palabrasClave = "luz,foco,señalizacion,señalización,señal,lampara,lámpara,farol,faro,antiniebla,giro,stop,retroceso,maria", esPrecargada = true),
        ObservacionSugerida(texto = "Foco quemado — reemplazar",
            palabrasClave = "luz,foco,señalizacion,señalización,señal,lampara,lámpara,farol,faro,antiniebla,giro,stop,retroceso,maria", esPrecargada = true),
        ObservacionSugerida(texto = "Luz de retroceso sin funcionamiento",
            palabrasClave = "luz,foco,señalizacion,señalización,señal,lampara,lámpara,farol,faro,antiniebla,giro,stop,retroceso,maria", esPrecargada = true),
        ObservacionSugerida(texto = "Luz antiniebla sin funcionamiento",
            palabrasClave = "luz,foco,señalizacion,señalización,señal,lampara,lámpara,farol,faro,antiniebla,giro,stop,retroceso,maria", esPrecargada = true),
        ObservacionSugerida(texto = "Alarma de giro sin funcionamiento",
            palabrasClave = "alarma", esPrecargada = true),

        // ── Sensores de sobrellenado ────────────────────────────────────
        ObservacionSugerida(texto = "Posicionar sensores a una distancia del enrase de 10 mm a 20 mm",
            palabrasClave = "sobrellenado", esPrecargada = true),
        ObservacionSugerida(texto = "Sensor sin continuidad eléctrica — reemplazar",
            palabrasClave = "sensor,sobrellenado", esPrecargada = true),
        ObservacionSugerida(texto = "Sensor fuera de posición — ajustar",
            palabrasClave = "sensor,sobrellenado", esPrecargada = true),

        // ── Válvulas ────────────────────────────────────────────────────
        ObservacionSugerida(texto = "Válvula API con pérdida — reparar o reemplazar",
            palabrasClave = "válvula de fondo", esPrecargada = true),
        ObservacionSugerida(texto = "Aro de acople fuera de tolerancia — reemplazar",
            palabrasClave = "api", esPrecargada = true),
        ObservacionSugerida(texto = "Tapa y/o junta en mal estado — reemplazar",
            palabrasClave = "api,tapa", esPrecargada = true),
        ObservacionSugerida(texto = "Válvula de fondo con pérdida — reparar",
            palabrasClave = "valvula,válvula,fondo", esPrecargada = true),
        ObservacionSugerida(texto = "Colocar las válvula P/V de las cisternas:",
            palabrasClave = "valvula,válvula,p/v,presion,presión", esPrecargada = true),
        ObservacionSugerida(texto = "Colocar la válvula P/V de la cisterna:",
            palabrasClave = "p/v", esPrecargada = true),
        ObservacionSugerida(texto = "Válvula Recuperadora de gases sin tapa",
            palabrasClave = "gases", esPrecargada = true),

        // ── Extintores ──────────────────────────────────────────────────
        ObservacionSugerida(texto = "Extintor vencido — reemplazar",
            palabrasClave = "extintor", esPrecargada = true),
        ObservacionSugerida(texto = "Extintor sin PH vigente",
            palabrasClave = "extintor", esPrecargada = true),
        ObservacionSugerida(texto = "Extintor sin marbete",
            palabrasClave = "extintor", esPrecargada = true),
        ObservacionSugerida(texto = "Falta extintor de 2.5 kg en cabina",
            palabrasClave = "extintor", esPrecargada = true),
        ObservacionSugerida(texto = "Falta extintor de 10 kg en tanque",
            palabrasClave = "extintor", esPrecargada = true),

        // ── Documentación / Certificados ────────────────────────────────
        ObservacionSugerida(texto = "Certificado VTV vencido — renovar",
            palabrasClave = "certificado,vtv,rto,lcm", esPrecargada = true),
        ObservacionSugerida(texto = "Certificado de inspección interna vencido",
            palabrasClave = "certificado,inspeccion,inspección,interna,externa,estanqueidad", esPrecargada = true),
        ObservacionSugerida(texto = "Tabla de calibración vencida",
            palabrasClave = "calibracion,calibración,tabla", esPrecargada = true),
        ObservacionSugerida(texto = "Falta hoja de seguridad del producto",
            palabrasClave = "hoja,seguridad,intervencion,intervención", esPrecargada = true),

        // ── Calcos / Señalización ───────────────────────────────────────
        ObservacionSugerida(texto = "Calco ONU incompleto o ilegible — reemplazar",
            palabrasClave = "calco,señalizacion,señalización,onu", esPrecargada = true),
        ObservacionSugerida(texto = "Falta calco de teléfono de emergencias",
            palabrasClave = "calco,señalizacion,señalización,telefono,teléfono,emergencia", esPrecargada = true),
        ObservacionSugerida(texto = "Calco con logo o palabra Raizen — retirar",
            palabrasClave = "calco,señalizacion,señalización", esPrecargada = true),

        // ── Parabrisas / Espejos ────────────────────────────────────────
        ObservacionSugerida(texto = "Parabrisas con rajadura — reemplazar",
            palabrasClave = "parabrisas,espejo,vidrio,ventanilla", esPrecargada = true),
        ObservacionSugerida(texto = "Espejo panorámico faltante o roto",
            palabrasClave = "parabrisas,espejo,vidrio,ventanilla", esPrecargada = true),
        ObservacionSugerida(texto = "Ventanilla con polarizado no permitido — retirar",
            palabrasClave = "parabrisas,espejo,vidrio,ventanilla,polarizado", esPrecargada = true),

        // ── Acoplamientos / Conexiones ──────────────────────────────────
        ObservacionSugerida(texto = "Manguera espiralada en mal estado — reemplazar",
            palabrasClave = "Tractor a semi", esPrecargada = true),
        ObservacionSugerida(texto = "Ficha eléctrica con contactos oxidados — limpiar o reemplazar",
            palabrasClave = "Tractor a semi", esPrecargada = true),
        ObservacionSugerida(texto = "Ficha neumática con pérdida — reemplazar",
            palabrasClave = "Tractor a semi", esPrecargada = true),

        // ── EPP ─────────────────────────────────────────────────────────
        ObservacionSugerida(texto = "Falta casco de seguridad",
            palabrasClave = "epp", esPrecargada = true),
        ObservacionSugerida(texto = "Ronovar los elementos de protección personal",
            palabrasClave = "epp,guante", esPrecargada = true),
        ObservacionSugerida(texto = "Ropa sin bandas reflectivas antiestáticas",
            palabrasClave = "epp,ropa,antiflama,reflectiva", esPrecargada = true),
        ObservacionSugerida(texto = "Falta arnés de seguridad",
            palabrasClave = "epp,arnes,arnés", esPrecargada = true),
        ObservacionSugerida(texto = "Falta antiparras (no se aceptan lentes)",
            palabrasClave = "epp,antiparras,lentes", esPrecargada = true),

        // ── Frenos / Sistema neumático ──────────────────────────────────
        ObservacionSugerida(texto = "Sistema neumático con pérdida — identificar y reparar",
            palabrasClave = "frl", esPrecargada = true),
        ObservacionSugerida(texto = "Reponer el liquido lubricante del FRL",
            palabrasClave = "frl", esPrecargada = true),
        ObservacionSugerida(texto = "Reparar el funcionamiento del cortacorriente neumatico",
            palabrasClave = "corta corriente", esPrecargada = true),
        ObservacionSugerida(texto = "Señalizar el cortacorriente neumatico",
            palabrasClave = "corta corriente", esPrecargada = true),

        // ── Puesta a tierra / Eléctrico ─────────────────────────────────
        ObservacionSugerida(texto = "Agregar el bulón de puesta",
            palabrasClave = "PAT, puesta a tierra", esPrecargada = true),
        ObservacionSugerida(texto = "Caja estanca con ingreso de agua — sellar",
            palabrasClave = "caja,estanca,cañeria,cañería,conexionado", esPrecargada = true),

        // ── Genéricas ───────────────────────────────────────────────────
        ObservacionSugerida(texto = "Elemento en mal estado general — reparar o reemplazar",
            palabrasClave = "", esPrecargada = true),
        ObservacionSugerida(texto = "Documentación faltante — presentar antes del próximo control",
            palabrasClave = "", esPrecargada = true),
        ObservacionSugerida(texto = "Requiere revisión por taller especializado",
            palabrasClave = "", esPrecargada = true)
    )

    /**
     * Dado el texto de descripción de un ítem, devuelve las observaciones
     * cuyas palabras clave aparecen en esa descripción.
     * Siempre incluye también las observaciones genéricas (sin palabras clave).
     */
    fun sugerenciasParaItem(
        descripcion: String,
        todasLasObservaciones: List<ObservacionSugerida>
    ): List<ObservacionSugerida> {
        val descNorm = normalizar(descripcion)

        return todasLasObservaciones.filter { obs ->
            if (obs.palabrasClave.isBlank()) return@filter true  // genéricas: siempre

            val claves = obs.palabrasClave.split(",")
                .map { normalizar(it) }
                .filter { it.isNotEmpty() }

            claves.any { clave -> descNorm.contains(clave) }
        }
            // Las que matchean por palabra clave van primero; las genéricas al final,
            // así el auditor ve lo más relevante para este ítem sin tener que scrollear.
            .sortedBy { it.palabrasClave.isBlank() }
    }

    private fun normalizar(texto: String): String {
        return texto.lowercase()
            .replace("á", "a").replace("é", "e").replace("í", "i")
            .replace("ó", "o").replace("ú", "u")
            .replace("ñ", "n")
            .trim()
    }
}
