# Campos a extraer

Versión 1 — definida el 2026-11-18.

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
| 3 | Modelo | `modelo` | texto | Mayúsculas, tal como figura en el documento | `R450 A6X2NA` | Modelo tractor / semirremolque |
| 4 | Año de fabricación | `anio_fabricacion` | número | 4 dígitos, entre 1950 y el año actual | `2019` | Año tractor / semirremolque |
| 5 | Configuración de ejes | `configuracion_ejes` | texto | *Pendiente de definir (ver abajo)* | — | *Pendiente: hoy no existe en el formulario* |

## Reglas generales

- Si un campo no figura o no se lee con seguridad, se devuelve **vacío**. Nunca se completa por deducción.
- Cada campo devuelve además un **nivel de confianza** (0 a 1), para que la app marque en la revisión los
  datos dudosos.
- Si en el documento aparecen dos vehículos (por ejemplo, tractor y semi en la misma hoja), se extrae el
  vehículo inspeccionado.

## Preguntas abiertas

1. **Configuración de ejes:** ¿cómo figura en los documentos? (por ejemplo `1-2`, `1.1.22`, `S-D-D`,
   o una cantidad de ejes). ¿Se copia tal cual o se lleva a un formato único? ¿Dónde se guarda en la app?
2. **Año:** algunos documentos traen *año modelo* en vez de *año de fabricación*. Si solo figura el año
   modelo, ¿se usa ese o se deja vacío?
3. **Modelo:** ¿se copia el texto completo (ej. `R450 A6X2NA`) o solo la parte principal (`R450`)?
