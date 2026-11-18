# Campos a extraer

Versión 2 — actualizada el 2026-11-18.

## Alcance

- **Documentos:** todas las variantes de RTO y VTV (RTO nacional CNRT, VTV de Provincia de Buenos Aires,
  VTV de CABA y RTO de las demás provincias).
- **Formato de entrada:** foto (cámara o galería) o PDF.
- **Fuera de alcance:** observaciones y defectos, fechas, resultado, n° de certificado/oblea, chasis,
  motor y titular. No se extraen.

## Campos

| # | Campo | Clave | Tipo | Formato de salida | Ejemplo | Destino en la app |
|---|-------|-------|------|-------------------|---------|-------------------|
| 1 | Dominio (patente) | `dominio` | texto | Mayúsculas, sin espacios, puntos ni guiones. Formato viejo `AAA999` o Mercosur `AA999AA` | `AB123CD` | Patente tractor / semirremolque |
| 2 | Marca | `marca` | texto | Mayúsculas, tal como figura en el documento | `SCANIA` | Marca tractor / semirremolque |
| 3 | Modelo | `modelo` | texto | Mayúsculas; solo los **primeros 9 caracteres alfanuméricos** del campo modelo (ver regla abajo) | `R450 A6X2N` | Modelo tractor / semirremolque |
| 4 | Año de fabricación | `anio_fabricacion` | número | 4 dígitos, entre 1950 y el año actual. **Obligatorio** | `2019` | Año tractor / semirremolque |
| 5 | Configuración de ejes | `configuracion_ejes` | texto | Se toma del **campo modelo** del documento. Mayúsculas, sin espacios | `1S-2D` | *Pendiente: hoy no existe en el formulario* |

## Reglas por campo

### Modelo
1. Se lee el texto completo del campo modelo del documento.
2. Se cuentan solo letras y números: los espacios, guiones, puntos y barras no cuentan.
3. Se corta después del carácter alfanumérico número 9. Los separadores que quedan antes del corte se mantienen.

| Texto en el documento | Resultado |
|-----------------------|-----------|
| `R450 A6X2NA` | `R450 A6X2N` |
| `G 420 LA4X2HNA` | `G 420 LA4X2` |
| `FH 460` | `FH 460` (tiene menos de 9) |

### Configuración de ejes
- Está dentro del campo modelo del documento, con forma `<cantidad><tipo>-<cantidad><tipo>`. Por ejemplo,
  `1S-2D` sería 1 eje simple y 2 ejes dobles.
- Se extrae del texto **completo** del modelo, antes de cortarlo a 9 caracteres, así no se pierde si
  está al final.
- Hay otras variantes además de `1S-2D` (a relevar con documentos reales).

### Año de fabricación
- **Siempre** se extrae: es un campo obligatorio.
- Pendiente: en qué parte del documento se encuentra (se indicará más adelante).

## Reglas generales

- Si un campo no figura o no se lee con seguridad, se devuelve **vacío**. Nunca se completa por deducción.
  Excepción: el año de fabricación es obligatorio; si no se lee con seguridad, se devuelve igual con
  confianza baja para que el técnico lo revise.
- Cada campo devuelve además un **nivel de confianza** (0 a 1), para que la app marque en la revisión los
  datos dudosos.
- Si en el documento aparecen dos vehículos (por ejemplo, tractor y semi en la misma hoja), se extrae el
  vehículo inspeccionado.

## Preguntas abiertas

1. **Configuración de ejes:**
   - ¿Qué otras variantes hay además de `1S-2D`? Por ejemplo `1S-1D`, `1S-1T`, `2S-2D`. ¿Qué significan S, D y T?
   - ¿Dónde se guarda en la app? Hoy el formulario "Nueva visita" no tiene ese campo.
2. **Año de fabricación:** en qué parte de cada documento se encuentra.
3. **Modelo:** confirmar que los espacios y guiones no cuentan para el máximo de 9 (ver ejemplos).
