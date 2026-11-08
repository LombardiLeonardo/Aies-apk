-- Planilla de Control de Atmosfera de Tanques Cisterna (R 12-01 Rev.01).
-- Corre esto UNA VEZ en el SQL Editor de Supabase. Es seguro de re-correr.
--
-- RLS: mismo esquema endurecido que el resto de las tablas (ver supabase/schema.sql, seccion de
-- RLS): solo `authenticated`, select/insert/update, SIN delete y SIN acceso `anon`. No se usa la
-- policy vieja "permitir_todo_anon" (for all, publica) porque la auditoria de seguridad la retiro
-- de todo el proyecto (SEC-001/002). Los hijos se borran igual por `on delete cascade`.

create table if not exists planillas_atmosfera (
    id bigserial primary key,
    unidad_id bigint not null references unidades(id),
    fecha bigint not null,
    tipo_estanqueidad boolean not null default false,
    tipo_integridad_dispositivos boolean not null default false,
    tipo_espacios_confinados boolean not null default false,
    tipo_otros boolean not null default false,
    tipo_otros_detalle text,
    producto_diesel boolean not null default false,
    producto_nafta boolean not null default false,
    producto_fame boolean not null default false,
    equipo_utilizado text,
    observaciones text,
    firma_responsable text,
    creado_en timestamptz not null default now()
);

create table if not exists planilla_atmosfera_cisternas (
    id bigserial primary key,
    planilla_id bigint not null references planillas_atmosfera(id) on delete cascade,
    cisterna_numero integer not null,
    lel numeric,
    o2 numeric,
    co numeric,
    h2s numeric
);

create index if not exists idx_atmosfera_cisternas_planilla on planilla_atmosfera_cisternas(planilla_id);

alter table planillas_atmosfera enable row level security;
alter table planilla_atmosfera_cisternas enable row level security;

do $$
declare
    tabla text;
begin
    foreach tabla in array array['planillas_atmosfera', 'planilla_atmosfera_cisternas']
    loop
        execute format('drop policy if exists "permitir_todo_anon" on %I', tabla);
        execute format('drop policy if exists "app_authenticated_select" on %I', tabla);
        execute format('create policy "app_authenticated_select" on %I for select to authenticated using (true)', tabla);
        execute format('drop policy if exists "app_authenticated_insert" on %I', tabla);
        execute format('create policy "app_authenticated_insert" on %I for insert to authenticated with check (true)', tabla);
        execute format('drop policy if exists "app_authenticated_update" on %I', tabla);
        execute format('create policy "app_authenticated_update" on %I for update to authenticated using (true) with check (true)', tabla);
    end loop;
end $$;

insert into tipos_servicio (codigo, nombre, modulo_origen) values
    ('planilla_atmosfera', 'Planilla de Control de Atmósfera de Tanques Cisterna', 'feature-planilla-atmosfera')
on conflict (codigo) do nothing;
