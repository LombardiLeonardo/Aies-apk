-- ============================================================
-- AIES Super App — auditoría de RLS y Storage en Supabase
-- Generado por la auditoría de seguridad del 2026-09-19.
-- ============================================================
-- Correr TODO este archivo en el SQL Editor del proyecto Supabase
-- (arizbvzxaaubnlttwklm) y pegar el resultado completo de vuelta en la
-- conversación con el auditor. Es de SOLO LECTURA (no modifica nada).
-- ============================================================

-- 1. Tablas del esquema public sin RLS habilitado en absoluto.
--    (esperado: vacío -- schema.sql habilita RLS en las 18 tablas de negocio)
select schemaname, tablename, rowsecurity
from pg_tables
where schemaname = 'public' and not rowsecurity;

-- 2. Todas las policies de public y storage -- el auditor va a revisar
--    especialmente qual/with_check = 'true' y roles = {public}/{anon}.
--    HALLAZGO YA CONFIRMADO por lectura de supabase/schema.sql sección 7:
--    la policy "permitir_todo_anon" en las 18 tablas usa
--    `for all using (true) with check (true)`, sin restricción de rol
--    (aplica a public, que incluye anon) -- confirmado también con una
--    petición GET real usando solo la anon key (sin login), que devolvió
--    filas de sesiones_servicio y calibraciones con CUIT/patente reales.
select schemaname, tablename, policyname, roles, cmd, qual, with_check
from pg_policies
where schemaname in ('public', 'storage')
order by 1, 2, 3;

-- 3. Funciones SECURITY DEFINER expuestas (las 4 RPC de numeración
--    correlativa deberían aparecer acá; confirmar que no haya ninguna
--    otra sin querer).
select n.nspname, p.proname, p.prosecdef, pg_get_userbyid(p.proowner) as owner
from pg_proc p
join pg_namespace n on n.oid = p.pronamespace
where n.nspname = 'public' and p.prosecdef;

-- 4. Quién puede usar directamente las secuencias de correlativos
--    (deberían usarse SOLO a través de las funciones RPC
--    siguiente_numero_*, nunca con USAGE directo desde el cliente --
--    si anon/authenticated tienen USAGE=true acá, cualquiera podría
--    saltear/reservar números de certificado llamando nextval() directo
--    vía RPC genérica o gaps intencionales).
select c.relname,
       has_sequence_privilege('anon', c.oid, 'USAGE') as anon_usage,
       has_sequence_privilege('authenticated', c.oid, 'USAGE') as auth_usage
from pg_class c
join pg_namespace n on n.oid = c.relnamespace
where c.relkind = 'S' and n.nspname = 'public';

-- 5. Buckets de Storage y si son públicos (certificados/informes/notas
--    de trabajo con datos de clientes reales viven acá -- ver
--    documento_url / documento_interno_url / nota_trabajo_url en
--    schema.sql).
select id, name, public, file_size_limit, allowed_mime_types
from storage.buckets;

-- 6. Objetos ya subidos al bucket "documentos" -- para evaluar si los
--    nombres son predecibles (correlativo/patente/fecha) y por lo tanto
--    enumerables, aunque la policy de SELECT esté limitada a
--    "authenticated" (ver hallazgo SEC-001: el login "authenticated" es
--    un usuario de servicio único embebido en el APK, no una barrera
--    real).
select id, name, bucket_id, created_at, updated_at, metadata
from storage.objects
where bucket_id = 'documentos'
order by created_at desc
limit 50;

-- 7. Confirmar cantidad de filas expuestas por tabla con la anon key,
--    para dimensionar el volumen de datos de clientes ya filtrado.
select 'unidades' as tabla, count(*) from unidades
union all select 'sesiones_servicio', count(*) from sesiones_servicio
union all select 'historial_servicios', count(*) from historial_servicios
union all select 'calibraciones', count(*) from calibraciones
union all select 'planillas_tapas', count(*) from planillas_tapas
union all select 'vehiculos_glp', count(*) from vehiculos_glp
union all select 'clientes_glp', count(*) from clientes_glp;
