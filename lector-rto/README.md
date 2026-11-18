# Lector RTO / VTV

Proyecto independiente para extraer datos de certificados de **RTO / VTV** a partir de una foto o un PDF.
Se desarrolla acá, aislado del resto de la app, y una vez terminado se integra a AIES Super App
(formulario *Nueva visita*: tractor y semirremolque).

> Esta carpeta **no** está incluida en `settings.gradle.kts`, así que no afecta el build del APK.

## Estructura

| Carpeta  | Contenido |
|----------|-----------|
| `docs/`  | Diseño, decisiones y especificación de los campos a extraer |
| `datos/` | Documentos de ejemplo y sus datos correctos (para entrenar y medir) — **no se versionan** |

## Estado

En diseño.
