#!/usr/bin/env bash
# ============================================================
# AIES Super App — pruebas de solo lectura contra Supabase (REST).
# Generado por la auditoría de seguridad del 2026-09-19.
# ============================================================
# Uso:
#   export SUPABASE_URL="https://arizbvzxaaubnlttwklm.supabase.co"
#   export SUPABASE_ANON_KEY="<la anon key extraída de local.properties/BuildConfig>"
#   bash docs/security/supabase_probes.sh
#
# Este script NUNCA escribe secretos en disco: las credenciales se leen
# de variables de entorno. Todas las peticiones son GET / POST de
# *lectura* (SELECT, listados) -- nunca INSERT/UPDATE/DELETE reales.
# No hace fuerza bruta: como mucho hace ~10 peticiones GET.
# ============================================================

set -euo pipefail

: "${SUPABASE_URL:?Falta SUPABASE_URL. export SUPABASE_URL=https://<project>.supabase.co}"
: "${SUPABASE_ANON_KEY:?Falta SUPABASE_ANON_KEY. export SUPABASE_ANON_KEY=<anon key>}"

hdr_apikey=(-H "apikey: ${SUPABASE_ANON_KEY}")
hdr_auth=(-H "Authorization: Bearer ${SUPABASE_ANON_KEY}")

linea() { printf '\n=== %s ===\n' "$1"; }

linea "1. OpenAPI root (lista tablas expuestas por PostgREST)"
curl -s -w '\nHTTP:%{http_code}\n' "${SUPABASE_URL}/rest/v1/" "${hdr_apikey[@]}" "${hdr_auth[@]}" --max-time 10

# Tablas de negocio con datos de clientes -- 1 fila cada una, solo para
# confirmar si responden SIN sesión/login (RLS efectiva vs. permisiva).
tablas=(
  "tipos_servicio"
  "unidades"
  "sesiones_servicio"
  "historial_servicios"
  "calibraciones"
  "planillas_tapas"
  "vehiculos_glp"
  "clientes_glp"
)

for t in "${tablas[@]}"; do
  linea "2. GET /rest/v1/${t}?select=*&limit=1 (anon, sin login)"
  curl -s -w '\nHTTP:%{http_code}\n' "${SUPABASE_URL}/rest/v1/${t}?select=*&limit=1" \
    "${hdr_apikey[@]}" "${hdr_auth[@]}" --max-time 10
done

linea "3. Listado del bucket 'documentos' (lectura, sin login)"
curl -s -w '\nHTTP:%{http_code}\n' -X POST "${SUPABASE_URL}/storage/v1/object/list/documentos" \
  "${hdr_apikey[@]}" "${hdr_auth[@]}" -H "Content-Type: application/json" \
  -d '{"limit":10,"prefix":""}' --max-time 10

linea "Fin de las pruebas de solo lectura."
echo "Resultado esperado tras el fix de RLS: los pasos 2 deben devolver 401/403 sin un"
echo "usuario autenticado válido (o filas vacías/propias si se agrega auth.uid())."
