package com.aies.feature.controlesvisuales.data

/**
 * Checklist de Control Visual Externa transcripto verbatim de la planilla "R 16-02" (real,
 * texto regulatorio). Los ítems 8, 9, 13, 16, 17, 18, 19, 20, 21, 30, 32 y 40 son solo el
 * enunciado introductorio de sus sub-ítems con letra y no generan fila propia — únicamente
 * sus hijos (8a/8b, 13a..13d, etc.) son filas chequeables. 52 filas en total.
 */
object SeedData {

    private data class Fila(val codigo: String, val seccion: String, val desc: String)

    private val filas = listOf(
        Fila("1", "CAÑO DE ESCAPE", "Los soportes poseen material antivibratorio."),
        Fila("2", "CAÑO DE ESCAPE", "La salida del caño de escape está orientada hacia el lado opuesto a las bocas de descarga."),
        Fila("3", "CAÑO DE ESCAPE", "La distancia a las bocas de descarga están en su recorrido por lo menos a un metro."),
        Fila("4", "CAÑO DE ESCAPE", "Está un mínimo de 0,20 metros del depósito de combustible propio del camión."),
        Fila("5", "CAÑO DE ESCAPE", "La parte que sobresale de la cabina posee protección metálica (Media caña)."),
        Fila("6", "CAÑO DE ESCAPE", "Posee aislamiento térmico que evite que caigan inflamables sobre el mismo."),
        Fila("7", "CAÑO DE ESCAPE", "Posee arrestallamas con malla Nº 40."),

        Fila("8a", "COMPARTIMENTO", "No presenta signos incipientes o cambios notables que afectan la identificación (corrosión externa)"),
        Fila("8b", "COMPARTIMENTO", "No es necesario efectuar control de espesores."),
        Fila("9", "COMPARTIMENTO", "No presenta abolladuras, rayones o globos que comprometen su comportamiento."),
        Fila("9a", "COMPARTIMENTO", "No es necesario realizar ensayos no destructivos."),
        Fila("10", "COMPARTIMENTO", "Está en buen estado la marca del nivel de llenado para carga ventral."),
        Fila("11", "COMPARTIMENTO", "En caso de no tratarse de unidades autoportantes, es correcto el montaje de la cisterna sobre el chasis"),
        Fila("12", "COMPARTIMENTO", "Posee bulones y/o abrazaderas de fijación del sistema de amortiguación de vibraciones."),

        Fila("13a", "BOCAS DE CARGA", "Cuenta con protección antivuelco."),
        Fila("13b", "BOCAS DE CARGA", "El material tiene el mismo espesor que el tanque."),
        Fila("13c", "BOCAS DE CARGA", "La soldadura a la estructura del tanque, es de cordones sin repelar."),
        Fila("13d", "BOCAS DE CARGA", "Es eficiente y está en buen estado la protección antivuelco."),
        Fila("14", "BOCAS DE CARGA", "Es seguro el acceso a las bocas de la cisterna."),
        Fila("14a", "BOCAS DE CARGA", "La escalera se encuentra en buenas condiciones"),
        Fila("15", "BOCAS DE CARGA", "Existencia y material antideslizante."),
        Fila("15a", "BOCAS DE CARGA", "En toda el área de circulación del tanque."),
        Fila("15b", "BOCAS DE CARGA", "En los peldaños de la escalera."),
        Fila("16a", "BOCAS DE CARGA", "Tapa superior de las bocas de carga: impiden la salida del producto en caso de vuelco"),
        Fila("16b", "BOCAS DE CARGA", "Tapa superior de las bocas de carga: no deben tener tapas roscadas."),
        Fila("17a", "BOCAS DE CARGA", "Válvulas de descarga: están ubicadas en la parte trasera."),
        Fila("17b", "BOCAS DE CARGA", "Válvulas de descarga: están dispuestas para carga ventral."),
        Fila("17c", "BOCAS DE CARGA", "Válvulas de descarga: son herméticas."),
        Fila("17d", "BOCAS DE CARGA", "Válvulas de descarga: están en buen estado las juntas y las tapas."),
        Fila("17e", "BOCAS DE CARGA", "Válvulas de descarga: es correcta la posición."),
        Fila("17f", "BOCAS DE CARGA", "Válvulas de descarga: es adecuada la protección estructural contra choques."),
        Fila("17g", "BOCAS DE CARGA", "Válvulas de descarga: es eficiente la protección estructural contra vuelcos o choques laterales."),
        Fila("18a", "BOCAS DE CARGA", "Válvula de emergencia: cada compartimento de la cisterna está equipado con una de ellas."),
        Fila("18b", "BOCAS DE CARGA", "Válvula de emergencia: están en buen estado y funcionan correctamente para ser accionadas en caso de emergencia."),
        Fila("18c", "BOCAS DE CARGA", "Válvula de emergencia: están libres de pérdidas en cada compartimento."),
        Fila("19a", "BOCAS DE CARGA", "Válvulas de venteo: están colocadas."),
        Fila("19b", "BOCAS DE CARGA", "Válvulas de venteo: funcionan correctamente."),
        Fila("20a", "BOCAS DE CARGA", "Mangueras: están en buen estado, sin presentar cortes o desgastes."),
        Fila("20b", "BOCAS DE CARGA", "Mangueras: está en buen estado el codo visor de acople."),
        Fila("20c", "BOCAS DE CARGA", "Mangueras: tiene continuidad eléctrica entre ambos acoples."),
        Fila("20d", "BOCAS DE CARGA", "Mangueras: tiene doble abrazadera en ambos extremos y están colocadas firmemente."),

        Fila("21a", "SISTEMA ELÉCTRICO", "Tiene descarga a tierra: tiene la cisterna planchuelas para conectar la descarga estática a tierra y están en buen estado."),
        Fila("22", "SISTEMA ELÉCTRICO", "Mantienen las partes metálicas continuidad eléctrica mediante cables flexibles, conectados eléctricamente, atornillados y conectados a puntos metálicos limpios y pulidos, que evidencian buena continuidad eléctrica."),
        Fila("23", "SISTEMA ELÉCTRICO", "Las mangas entre el tractor y el remolque o chasis y acoplado son de cable envainado y soportado elásticamente."),
        Fila("24", "SISTEMA ELÉCTRICO", "Está cubierto el colector del impulsor de arranque."),
        Fila("25", "SISTEMA ELÉCTRICO", "Están las baterías cubiertas mediante caja con su tapa protegida para evitar todo contacto con el exterior."),
        Fila("26", "SISTEMA ELÉCTRICO", "Posee la unidad dispositivo de corte rápido de corriente y de fácil acceso."),
        Fila("27", "SISTEMA ELÉCTRICO", "El cortacorriente permite bloquear todos los circuitos eléctricos."),
        Fila("28", "SISTEMA ELÉCTRICO", "Tiene cables conectados directamente a la batería."),
        Fila("29", "SISTEMA ELÉCTRICO", "Tiene la unidad cadena o colas para descarga estática."),
        Fila("30a", "SISTEMA ELÉCTRICO", "Instalación eléctrica estanca: es resistente a la corrosión, aislada y protegida contra posibles daños físicos."),
        Fila("30b", "SISTEMA ELÉCTRICO", "Instalación eléctrica estanca: las cajas de la instalación son de fundición de aluminio y estancas con juntas incombustibles."),
        Fila("30c", "SISTEMA ELÉCTRICO", "Instalación eléctrica estanca: las conexiones tienen terminales soldados y los tornillos con arandelas planas y de presión."),
        Fila("30d", "SISTEMA ELÉCTRICO", "Instalación eléctrica estanca: los cables de la instalación son del tipo antillamas y las uniones son soldadas."),

        Fila("31", "TOMA DE AIRE DEL MOTOR", "Posee una cubierta que evita la entrada de líquidos durante la carga."),

        Fila("32a", "ELEMENTOS DE LUCHA CONTRA INCENDIO", "Cuentan, chasis y acoplado, con dos (2) matafuegos de 10 Kg. (20 BC)"),
        Fila("32b", "ELEMENTOS DE LUCHA CONTRA INCENDIO", "Cuenta el chasis con un matafuegos de 2,5 Kg. (5BC)."),
        Fila("32c", "ELEMENTOS DE LUCHA CONTRA INCENDIO", "Cuenta con dos baldes con tapa conteniendo 10 Kg. de arena cada uno."),

        Fila("33", "ELEMENTOS DE PROTECCIÓN", "Dispone de una maza de madera dura o material antichispa."),
        Fila("34", "ELEMENTOS DE PROTECCIÓN", "Dispone de dos tacos de material antichispas."),
        Fila("35", "ELEMENTOS DE PROTECCIÓN", "Dispone de una linterna blindada contra explosión."),
        Fila("36", "ELEMENTOS DE PROTECCIÓN", "Dispone de un juego de balizas reglamentarias."),
        Fila("37", "ELEMENTOS DE PROTECCIÓN", "Dispone de zapatos, guantes y casco de seguridad."),

        Fila("38", "LEYENDAS", "Cuenta, la unidad, con la leyenda \"PELIGRO INFLAMABLE\" en el frente, costados y parte posterior."),
        Fila("39", "LEYENDAS", "Está indicada la capacidad de cada compartimento en ambos lados de la cisterna y en las bocas de carga."),

        Fila("40a", "EMBLEMAS Y FICHAS", "Identificación de riesgo y Nº de ONU (Resoluciones SST Nº 233 y 720)."),
        Fila("40b", "EMBLEMAS Y FICHAS", "Dispone de la Ficha de Intervención Correspondiente.")
    )

    fun itemsChecklistExterna(controlId: Long): List<ItemChecklistExterna> =
        filas.mapIndexed { orden, f ->
            ItemChecklistExterna(controlId = controlId, codigo = f.codigo, seccion = f.seccion, descripcion = f.desc, orden = orden)
        }
}
