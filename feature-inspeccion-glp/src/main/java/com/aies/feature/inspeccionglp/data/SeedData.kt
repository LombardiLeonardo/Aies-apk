package com.aies.feature.inspeccionglp.data

/**
 * Datos iniciales transcriptos de "R 21-00 Rev.04 - Planilla para control de camiones
 * tanque - Producto GLP" (SeedData.cs del original). FOB = unidad blanca, CIF = unidad de
 * color. La numeración salta 16/19/24 en la planilla original (no son errores de transcripción,
 * así está en la fuente) — el resultado son 73 filas, no 76, porque esos 3 números no se usan.
 */
object SeedData {

    private val OBS = TipoIncumplimiento.Observacion
    private val REC = TipoIncumplimiento.Recomendacion

    private data class Fila(
        val numero: Int, val cat: String, val sub: String?, val desc: String,
        val tipo: TipoIncumplimiento, val fob: Boolean, val cif: Boolean
    )

    private val filas = listOf(
        // DOCUMENTACIÓN
        Fila(1, "DOCUMENTACIÓN", null, "Certificado de habilitación o rehabilitación de la unidad completa emitido por una Empresa Auditora de Seguridad debidamente registrada ante la Secretaría de Energía según Res. SEN 800/04 - Comprobar la veracidad de la documentación", OBS, true, true),
        Fila(2, "DOCUMENTACIÓN", null, "Certificado de calibración válvulas de seguridad", OBS, true, true),
        Fila(3, "DOCUMENTACIÓN", null, "Certificado de control válvulas de exceso de flujo", OBS, true, true),
        Fila(4, "DOCUMENTACIÓN", null, "Certificado Inspección Técnica anual según Dec. 875/94 Res. S/T 417/92 - Res. N°75/97", OBS, true, true),
        Fila(5, "DOCUMENTACIÓN", null, "Certificado de prueba de mangueras", OBS, true, true),
        Fila(6, "DOCUMENTACIÓN", null, "Cédula de identificación del automotor", REC, true, true),
        Fila(7, "DOCUMENTACIÓN", null, "Póliza de seguro vehicular tractor y tanque", REC, true, true),
        Fila(8, "DOCUMENTACIÓN", null, "Certificados de carga de matafuegos vigente", REC, true, true),
        Fila(9, "DOCUMENTACIÓN", null, "Certificados ruta tractor y tanque", REC, true, true),
        Fila(10, "DOCUMENTACIÓN", null, "Certificado de ejecución de \"Protocolo para verificación de acoplamientos mecánicos y rodamientos en ejes de semirremolques\" - Procedimiento DSL 1131 SHELL CAPSA", OBS, true, true),

        // TANQUE - SISTEMA ELECTRICO
        Fila(11, "TANQUE", "SISTEMA ELECTRICO", "No debe poseer elementos utilizados como descarga electrostática: cadenas, banda, flexibles de goma", REC, true, true),
        Fila(12, "TANQUE", "SISTEMA ELECTRICO", "Control visual general de luces, indicadores y cables visibles; informar sobre lámparas o vidrios de indicadores rotos, cables sueltos o rotos en circuitos fácilmente visibles", REC, true, true),
        Fila(13, "TANQUE", "SISTEMA ELECTRICO", "En los artefactos eléctricos adheridos al tanque, controlar que los cables conductores se hallen entubados en toda su extensión en caño o flexible metálico", REC, true, true),

        // TANQUE - ESTADO DE PINTURA Y GENERAL VISIBLE
        Fila(14, "TANQUE", "ESTADO DE PINTURA Y GENERAL VISIBLE", "Controlar si el vehículo está pintado correctamente, color claro reflectante de la luz solar", REC, true, true),
        Fila(15, "TANQUE", "ESTADO DE PINTURA Y GENERAL VISIBLE", "Leyendas: deberá llevar las palabras \"peligro explosivo\", gas inflamable, gas licuado de petróleo o butano-propano pintadas en ambos cabezales y a cada lado del tanque", REC, true, true),
        Fila(17, "TANQUE", "ESTADO DE PINTURA Y GENERAL VISIBLE", "Verificar correcto estado de tren retráctil", REC, true, true),

        // TANQUE - FRENOS
        Fila(18, "TANQUE", "FRENOS", "Frenos de aire: controlar pérdidas o daños en las cañerías, depósito de aire y acoples. Funciones y parámetros controlados mediante scanner homologado con licencia vigente", REC, true, true),

        // TANQUE - ESTADO DE ENGANCHE
        Fila(20, "TANQUE", "ESTADO DE ENGANCHE", "Efectuar protocolo de control de enganche y rodamientos", REC, false, true),
        Fila(21, "TANQUE", "ESTADO DE ENGANCHE", "Efectuar protocolo de control de enganche", REC, true, false),

        // TANQUE - TANQUE MONTAJE (aplica a camiones chasis para despacho a granel)
        Fila(22, "TANQUE", "TANQUE MONTAJE", "Controlar que el tanque esté ubicado correctamente sobre eje trasero, que los bulones que lo sujetan estén bien ajustados y que no falte ninguno", REC, true, true),
        Fila(23, "TANQUE", "TANQUE MONTAJE", "Controlar que los montajes del tanque y el bastidor del chasis no presenten roturas", REC, true, true),

        // TANQUE - RUEDAS Y CUBIERTAS
        Fila(25, "TANQUE", "RUEDAS Y CUBIERTAS", "Control visual de todas las ruedas: pernos rotos, tuercas extraviadas, fisuras, roturas o corrosión avanzada en el aro que sujeta la cubierta", REC, true, true),
        Fila(26, "TANQUE", "RUEDAS Y CUBIERTAS", "Cubiertas de eje direccional no deben ser recapadas", REC, true, true),
        Fila(27, "TANQUE", "RUEDAS Y CUBIERTAS", "Cubiertas del eje motriz deben ser radiales con descarga de agua lateral", REC, false, true),
        Fila(28, "TANQUE", "RUEDAS Y CUBIERTAS", "Cubiertas: el estado de las mismas no debe mostrar señas peligrosas. El dibujo del rodado no debe ser menor a 3 mm", REC, false, true),
        Fila(29, "TANQUE", "RUEDAS Y CUBIERTAS", "Cubiertas: el estado de las mismas no debe mostrar señas peligrosas. El dibujo del rodado no debe ser menor a 2 mm", REC, true, false),

        // TANQUE - ELEMENTOS CONTRA INCENDIO
        Fila(30, "TANQUE", "ELEMENTOS CONTRA INCENDIO", "Controlar que la unidad esté dotada con un extintor de 10 kg a base de polvo, con elementos de sujeción adecuados, colocado en lugar accesible y de rápida disponibilidad", REC, true, false),
        Fila(31, "TANQUE", "ELEMENTOS CONTRA INCENDIO", "Controlar que la unidad esté dotada con dos extintores de 10 kg a base de polvo, con elementos de sujeción adecuados, colocados en lugar accesible y de rápida disponibilidad", REC, false, true),

        // TANQUE - MANGUERAS
        Fila(32, "TANQUE", "MANGUERAS", "Cada vehículo tanque deberá llevar permanentemente una manguera para trasvase de LPG líquido y otra para vapores, con sus respectivas válvulas de bloqueo esféricas. Verificar estado de superficie exterior y de acoplamientos. Verificar chapas acuñadas con fecha de ensayo", OBS, true, true),
        Fila(33, "TANQUE", "MANGUERAS", "Prueba hidráulica: deberá cumplirse con periodicidad obligatoria semestral, presentando certificados de aprobación", OBS, true, true),

        // TANQUE - VALVULAS
        Fila(34, "TANQUE", "VALVULAS", "Válvulas de bloqueo: verificar que no tengan pérdidas, sean totalmente operables y cierren totalmente. Verificar traba de seguridad (dos válvulas de líquido y una de vapor) y marcado reglamentario (presión de trabajo, diámetro, marca)", OBS, true, true),
        Fila(35, "TANQUE", "VALVULAS", "Válvulas de bloqueo de control remoto: verificar mecanismo de cierre automático (mecánico y térmico) en extremos opuestos del tanque, y fusibles disparadores para cierre automático en caso de incendio", OBS, true, true),
        Fila(36, "TANQUE", "VALVULAS", "Válvulas de exceso de flujo: verificar cierre total automático ante apertura/cierre instantáneo. Solicitar certificado de calibración (periodicidad anual) y verificar chapa acuñada con fecha de ensayo y marcado reglamentario", OBS, true, true),
        Fila(37, "TANQUE", "VALVULAS", "Válvulas de seguridad/alivio de presión: solicitar certificado de calibración vigente por dos años. Verificar tapas de protección, ausencia de suciedad/corrosión/agua, vida útil máxima de 10 años, y etiquetado reglamentario", OBS, true, true),
        Fila(38, "TANQUE", "VALVULAS", "Válvulas de purga - venteos: verificar que las válvulas esféricas de 1/2\" entre bloqueo y conexiones rápidas no tengan pérdidas", OBS, true, true),

        // TANQUE - CONEXIONES PARA BRAZOS - TAPAS
        Fila(39, "TANQUE", "CONEXIONES PARA BRAZOS – TAPAS", "Las salidas de líquido y vapor terminan en conexiones rápidas: verificar manijas de acoplamiento sin desgaste, espigas de fijación y juntas de goma", OBS, true, true),
        Fila(40, "TANQUE", "CONEXIONES PARA BRAZOS – TAPAS", "Tapas: verificar su estado; se deben colocar las tapas ciegas en las conexiones rápidas", OBS, true, true),

        // TANQUE - MANOMETRO Y TERMOMETRO
        Fila(41, "TANQUE", "MANOMETRO Y TERMOMETRO", "Manómetro: rango de 0-20 kg/cm2, controlar estado de aguja y vidrio", OBS, true, true),
        Fila(42, "TANQUE", "MANOMETRO Y TERMOMETRO", "Termómetro: rango de 0-60°C, división 0,5°C, exactitud 0,25°C en cualquier punto; controlar estado de aguja y vidrio", OBS, true, true),

        // TANQUE - NIVELES
        Fila(43, "TANQUE", "NIVELES", "Tubo rotativo: verificar lectura en ambos sentidos del dial calibrado en porcentaje; cerrar válvula de venteo y volver la manija indicadora a la posición del 100%", OBS, true, true),
        Fila(44, "TANQUE", "NIVELES", "Niveles fijos: verificar que estén correctamente identificados en 74%, 85% y 90%, y que las válvulas de venteo abran y cierren en forma manual", OBS, true, true),

        // TANQUE - CONEXION DE DESCARGA A TIERRA
        Fila(45, "TANQUE", "CONEXION DE DESCARGA A TIERRA", "Bulón con tuerca preferentemente de bronce para la conexión estática: verificar su existencia fijada al tanque y que no esté cubierto con pintura", OBS, true, true),

        // TANQUE - CALZAS
        Fila(46, "TANQUE", "CALZAS", "Calzas: cada vehículo tanque debe llevar dos, colocadas separadas aprox. 5 cm de las ruedas, con dimensiones apropiadas al peso del camión y diámetro de las ruedas", REC, true, true),

        // TANQUE - BANDEROLAS
        Fila(47, "TANQUE", "BANDEROLAS", "Dos señales de carga peligrosa: dos triángulos metálicos rojos montados en la parte superior lateral izquierda del tanque; verificar su correcta posición", REC, true, true),

        // TANQUE - PERDIDAS CONEXIONES ACCESORIOS
        Fila(48, "TANQUE", "PERDIDAS CONEXIONES ACCESORIOS", "Controlar que las conexiones y accesorios no tengan pérdidas después de la carga", OBS, true, true),

        // TRACTOR - SISTEMA DE ESCAPE
        Fila(49, "TRACTOR", "SISTEMA DE ESCAPE", "El sistema de escape (incluyendo silenciador y caño) debe descargar a distancia del tanque y fuera de la estructura persistente a la unidad, alejado de zonas de maniobra de LPG, sin arresta llamas, con protección tipo media caña donde pase debajo de tanques de combustible", REC, true, true),

        // TRACTOR - SISTEMA ELECTRICO
        Fila(50, "TRACTOR", "SISTEMA ELECTRICO", "No debe portar elementos utilizados como descarga electrostática: cadenas, banda, flexibles de goma", REC, true, true),
        Fila(51, "TRACTOR", "SISTEMA ELECTRICO", "Control visual general de luces, indicadores y cables visibles; controlar funcionamiento de llave de corte de batería, tapa cubre batería, alarma sonora e indicación de marcha atrás", REC, true, true),
        Fila(52, "TRACTOR", "SISTEMA ELECTRICO", "En los artefactos eléctricos adheridos al tanque, controlar que los cables conductores se hallen entubados en toda su extensión en caño o flexible metálico", REC, true, true),
        Fila(53, "TRACTOR", "SISTEMA ELECTRICO", "Levantavidrios y espejos retrovisores de accionamiento eléctrico desde el puesto de conducción", REC, false, true),
        Fila(54, "TRACTOR", "SISTEMA ELECTRICO", "Encendido automático de luces bajas al poner en contacto la unidad", REC, false, true),
        Fila(55, "TRACTOR", "SISTEMA ELECTRICO", "Luces antiniebla delanteras color blanco", REC, false, true),
        Fila(56, "TRACTOR", "SISTEMA ELECTRICO", "Funcionamiento de bocina", REC, true, true),

        // TRACTOR - RUEDAS Y CUBIERTAS
        Fila(57, "TRACTOR", "RUEDAS Y CUBIERTAS", "Control visual de todas las ruedas: pernos rotos, tuercas extraviadas, fisuras, roturas o corrosión avanzada en el disco o en el aro que sujeta la cubierta", REC, true, true),
        Fila(58, "TRACTOR", "RUEDAS Y CUBIERTAS", "Cubiertas: el estado no debe mostrar señas peligrosas; el dibujo del rodado no debe ser menor a 3 mm", REC, false, true),
        Fila(59, "TRACTOR", "RUEDAS Y CUBIERTAS", "Cubiertas: el estado no debe mostrar señas peligrosas; el dibujo del rodado no debe ser menor a 2 mm", REC, true, false),

        // TRACTOR - FRENOS
        Fila(60, "TRACTOR", "FRENOS", "Frenos de aire: controlar pérdidas o daños en cañerías, depósito de aire y acoples; controlar celeridad de presurización/despresurización y alarma de baja presión. Funciones controladas mediante scanner homologado", REC, true, true),
        Fila(61, "TRACTOR", "FRENOS", "Freno de emergencia: verificar su correcto funcionamiento", REC, true, true),

        // TRACTOR - LEYENDAS REGLAMENTARIAS
        Fila(62, "TRACTOR", "LEYENDAS REGLAMENTARIAS", "Leyenda \"peligro explosivo\" en la parte frontal del tractor o chasis, con código de producto ONU y pictograma de riesgo", REC, true, true),
        Fila(63, "TRACTOR", "LEYENDAS REGLAMENTARIAS", "Leyendas con indicaciones para situaciones de emergencia, si las hubiera", REC, true, true),

        // TRACTOR - ELEMENTOS DE SEGURIDAD
        Fila(64, "TRACTOR", "ELEMENTOS DE SEGURIDAD", "Cinturón de seguridad: verificar su estado, si es combinado, y su numeración", REC, true, true),
        Fila(65, "TRACTOR", "ELEMENTOS DE SEGURIDAD", "Cinturones de seguridad de tres puntos de anclaje", REC, true, false),
        Fila(66, "TRACTOR", "ELEMENTOS DE SEGURIDAD", "Cinturones de seguridad inerciales y apoyacabezas en ambos asientos", REC, false, true),
        Fila(67, "TRACTOR", "ELEMENTOS DE SEGURIDAD", "Parasol", REC, false, true),
        Fila(68, "TRACTOR", "ELEMENTOS DE SEGURIDAD", "Obligatoriedad de uso de elementos de seguridad personal: casco, guantes, calzado de seguridad y protectores visuales. No deben tenerse calentadores o elementos similares", REC, true, true),
        Fila(69, "TRACTOR", "ELEMENTOS DE SEGURIDAD", "Espejos retrovisores convexos en ambos laterales", REC, true, true),
        Fila(70, "TRACTOR", "ELEMENTOS DE SEGURIDAD", "Espejo retrovisor lateral clase V sobre puerta del lado del acompañante", REC, false, true),

        // TRACTOR - ESTADO DE ENGANCHE
        Fila(71, "TRACTOR", "ESTADO DE ENGANCHE", "Efectuar protocolo de control de enganche y rodamientos", OBS, false, true),
        Fila(72, "TRACTOR", "ESTADO DE ENGANCHE", "Efectuar protocolo de control de enganche", OBS, true, false),

        // TRACTOR - ELEMENTO CONTRA INCENDIO
        Fila(73, "TRACTOR", "ELEMENTO CONTRA INCENDIO", "Verificar que la unidad cuente con un matafuego de cabina de por lo menos 2 kg de capacidad, polvo químico seco ABC", REC, true, false),
        Fila(74, "TRACTOR", "ELEMENTO CONTRA INCENDIO", "Controlar que la unidad esté equipada con un extintor de 5 kg de polvo químico seco en la cabina; verificar integridad de manómetro", REC, false, true),

        // TRACTOR - ALTAS DE UNIDADES NUEVAS O PROVENIENTES DE INTERVENCION O REPRUEBA QUINQUENAL
        Fila(75, "TRACTOR", "ALTAS DE UNIDADES NUEVAS O PROVENIENTES DE UNA INTERVENCIÓN O REPRUEBA QUINQUENAL", "Verificar que el transportista presente el legajo técnico de la unidad aprobado por una empresa auditora registrada en la Secretaría de Energía de la Nación", OBS, true, true),
        Fila(76, "TRACTOR", "ALTAS DE UNIDADES NUEVAS O PROVENIENTES DE UNA INTERVENCIÓN O REPRUEBA QUINQUENAL", "Verificar que hayan sido reemplazadas las válvulas de bloqueo y dispositivos de medición de nivel de líquido", OBS, true, true)
    )

    fun itemsGlp(plantillaId: String): List<ItemChecklist> =
        filas.mapIndexed { orden, f ->
            ItemChecklist(
                plantillaId = plantillaId,
                numero = f.numero,
                categoria = f.cat,
                subcategoria = f.sub,
                descripcion = f.desc,
                tipoIncumplimiento = f.tipo,
                aplicaFob = f.fob,
                aplicaCif = f.cif,
                orden = orden
            )
        }
}
