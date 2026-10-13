package com.aies.feature.planillatapas.data

/**
 * Checklist de la "Planilla de Control de Tapas de Cisternas" (R 11-01), transcripto del .docx
 * original. El documento original NUMERA MAL sus propios ítems: "13"/"14" aparecen dos veces
 * (una en "BOCA DE LLENADO 250 MM", otra -distinta- en "MONTAJE") y "LIBERACIÓN FINAL DE LA
 * UNIDAD" reinicia la numeración en "1". Para no arrastrar esa ambigüedad, cada fila tiene acá
 * un [Fila.codigo] propio, ESTABLE y ÚNICO (prefijado por sección: doc/elec/neum/tapa/boca/
 * montaje/valvgas/estanq/final), separado de [Fila.numeroVisible] -- el número/letra que
 * aparece impreso en el papel real (ej. "8", "(a)"), que sí puede repetirse entre secciones sin
 * problema porque no se usa como clave.
 *
 * Los enunciados numéricos que son solo la introducción de sus sub-ítems con letra (8, 9, 10,
 * 11, 12, 13 y 14 de "TAPA PASO DE HOMBRE"/"BOCA DE LLENADO 250 MM") no generan fila propia --
 * mismo criterio que SeedData de feature-controles-visuales -- únicamente sus hijos con letra
 * son filas chequeables. 38 filas en total.
 *
 * La nota "REF.01" ("Marcar con una X el reemplazo de junta", referencia cruzada del ítem
 * 11(a)) se omite a propósito: es una aclaración, no un ítem de checklist en sí.
 */
object SeedData {

    data class Fila(val codigo: String, val seccion: String, val numeroVisible: String, val desc: String)

    val filas = listOf(
        // DOCUMENTACIÓN
        Fila("doc1", "DOCUMENTACIÓN", "1", "Certificados de inspección visual interna, externa y estanqueidad y espesores"),
        Fila("doc2", "DOCUMENTACIÓN", "2", "Certificado de homologación de tapa paso de hombre (Según normativa ADR 2009 Sec.6.8.2.2. y 6.8.4.1. y Normas EN 13314 y 13317)"),

        // SISTEMA ELÉCTRICO
        Fila("elec3", "SISTEMA ELÉCTRICO", "3", "Cañerías estancas"),
        Fila("elec4", "SISTEMA ELÉCTRICO", "4", "Empalmes de cables con conexiones originales"),
        Fila("elec5", "SISTEMA ELÉCTRICO", "5", "Funcionamiento del sistema de sensores de sobrellenado y puesta a tierra"),

        // SISTEMA NEUMÁTICO
        Fila("neum6", "SISTEMA NEUMÁTICO", "6", "Estado de cañerías y conectores rápidos."),
        Fila("neum7", "SISTEMA NEUMÁTICO", "7", "Fugas de aire en todo el circuito"),

        // TAPA PASO DE HOMBRE
        Fila("tapa8a", "TAPA PASO DE HOMBRE", "8 (a)", "Estado de placa de 500 mm de aluminio: verificar planitud"),
        Fila("tapa8b", "TAPA PASO DE HOMBRE", "8 (b)", "Estado de placa de 500 mm de aluminio: estado general, no debe presentar corrosión."),
        Fila("tapa8c", "TAPA PASO DE HOMBRE", "8 (c)", "Estado de placa de 500 mm de aluminio: espesor mayor a 4mm."),
        Fila("tapa9a", "TAPA PASO DE HOMBRE", "9 (a)", "Base bridada de apoyo: verificar planitud"),
        Fila("tapa9b", "TAPA PASO DE HOMBRE", "9 (b)", "Base bridada de apoyo: estado general, no debe presentar corrosión."),
        Fila("tapa9c", "TAPA PASO DE HOMBRE", "9 (c)", "Base bridada de apoyo: espesor mayor a 4mm."),
        Fila("tapa10a", "TAPA PASO DE HOMBRE", "10 (a)", "Bulones de ajuste: deben ser tornillos de acero SAE grado 5 de ½ pulg. o equivalentes."),
        Fila("tapa10b", "TAPA PASO DE HOMBRE", "10 (b)", "Bulones de ajuste: no deben presentar signos de corrosión"),
        Fila("tapa10c", "TAPA PASO DE HOMBRE", "10 (c)", "Bulones de ajuste: verificar ajuste"),
        Fila("tapa11a", "TAPA PASO DE HOMBRE", "11 (a)", "Junta de tapa paso de hombre: verificar estado, no debe presentar signos de estiramiento, corte o desgrane. (En caso de reemplazo de junta, completar cuadro REF.01)"),
        Fila("tapa11b", "TAPA PASO DE HOMBRE", "11 (b)", "Junta de tapa paso de hombre: no debe presentar diferencia mayor a 4mm entre diámetro de placa y aro de entrada de hombre."),

        // BOCA DE LLENADO 250 MM
        Fila("boca12a", "BOCA DE LLENADO 250 MM", "12 (a)", "Sistema de cierre: verificar estado, sin golpes ni corrosión"),
        Fila("boca12b", "BOCA DE LLENADO 250 MM", "12 (b)", "Sistema de cierre: verificar el sistema de cierres por bisagra, pernos, chaveta y traba."),
        Fila("boca12c", "BOCA DE LLENADO 250 MM", "12 (c)", "Sistema de cierre: verificar estado de alojamiento de la junta"),
        Fila("boca12d", "BOCA DE LLENADO 250 MM", "12 (d)", "Sistema de cierre: verificar estado de la junta, no debe presentar signos de estiramiento, corte o desgrane."),
        Fila("boca13a", "BOCA DE LLENADO 250 MM", "13 (a)", "Válvula de alivio: estado de resortes y componentes solidarios"),
        Fila("boca13b", "BOCA DE LLENADO 250 MM", "13 (b)", "Válvula de alivio: apertura de la válvula en rangos tolerables."),
        Fila("boca14a", "BOCA DE LLENADO 250 MM", "14 (a)", "Válvula de alivio de presión y vacío: verificar estado, sin golpes ni corrosión"),
        Fila("boca14b", "BOCA DE LLENADO 250 MM", "14 (b)", "Válvula de alivio de presión y vacío: verificar limpieza"),

        // MONTAJE (el documento original reutiliza 13/14 y agrega 15/16 -- códigos propios evitan
        // la colisión con "BOCA DE LLENADO 250 MM" de arriba; numeroVisible respeta el papel).
        Fila("montaje13", "MONTAJE", "13", "Verificar montaje de la placa sobre la base bridada"),
        Fila("montaje14", "MONTAJE", "14", "Verificar correcto ajuste de los bulones de fijación"),
        Fila("montaje15", "MONTAJE", "15", "Verificar colocación de VPV en su alojamiento"),
        Fila("montaje16", "MONTAJE", "16", "Verificar funcionamiento del sistema de sensores de sobrellenado y puesta a tierra"),

        // VÁLVULA DE RECUPERACIÓN DE GASES
        Fila("valvgas17a", "VÁLVULA DE RECUPERACIÓN DE GASES", "17 (a)", "Válvula de recuperación de gases: verificar estado, sin golpes ni corrosión"),
        Fila("valvgas17b", "VÁLVULA DE RECUPERACIÓN DE GASES", "17 (b)", "Válvula de recuperación de gases: garantiza apertura en accionamiento de válvula de fondo"),
        Fila("valvgas17c", "VÁLVULA DE RECUPERACIÓN DE GASES", "17 (c)", "Válvula de recuperación de gases: verificar lubricación mediante FRL"),

        // ESTANQUEIDAD DEL CONJUNTO
        Fila("estanq18", "ESTANQUEIDAD DEL CONJUNTO", "18", "Estanqueidad del conjunto instalado en cisterna"),

        // LIBERACIÓN FINAL DE LA UNIDAD (el documento original reinicia su numeración en "1" acá
        // -- códigos propios evitan la colisión con "DOCUMENTACIÓN"/"SISTEMA ELÉCTRICO" etc.).
        Fila("final1", "LIBERACIÓN FINAL DE LA UNIDAD", "1", "Devolución de la documentación."),
        Fila("final2", "LIBERACIÓN FINAL DE LA UNIDAD", "2", "Retiro de tacos antideslizantes del tren trasero"),
        Fila("final3", "LIBERACIÓN FINAL DE LA UNIDAD", "3", "Verificación del precintado y limpieza del lomo de la cisterna"),
        Fila("final4", "LIBERACIÓN FINAL DE LA UNIDAD", "4", "Verificación de la reinstalación de los componentes involucrados en el procedimiento")
    )

    fun itemsChecklist(registroId: Long): List<ItemChecklistTapas> =
        filas.mapIndexed { orden, f ->
            ItemChecklistTapas(
                registroId = registroId, codigo = f.codigo, seccion = f.seccion,
                descripcion = f.desc, numeroVisible = f.numeroVisible, orden = orden
            )
        }
}
