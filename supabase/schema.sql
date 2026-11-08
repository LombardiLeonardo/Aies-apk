-- ============================================================
-- AIES Super App — esquema consolidado de Supabase (Postgres)
-- ============================================================
-- Junta, en un solo script idempotente, todas las migraciones que se
-- fueron corriendo a mano en el SQL Editor a lo largo del proyecto:
--   001 esquema inicial · 002 esquema GLP · corrección vehiculos_glp ·
--   nota_trabajo_url · políticas de Storage (v1 y su corrección) ·
--   Calibrado R14-03 + Planilla de Tapas R11-01 · columna detalle +
--   numeración de Reporte de Observaciones.
--
-- Es seguro de correr TANTO contra un proyecto Supabase nuevo (deja
-- todo el esquema armado de una) COMO contra el proyecto ya en
-- producción (todo usa IF NOT EXISTS / CREATE OR REPLACE / DROP
-- POLICY IF EXISTS + CREATE, así que si algo ya existe no rompe,
-- simplemente no hace nada en esa línea).
--
-- Envuelto en una transacción: si una línea falla, no queda nada
-- aplicado a medias -- se puede corregir y volver a correr entero.
-- ============================================================

begin;

-- ============================================================
-- 0. Extensiones
-- ============================================================

create extension if not exists "uuid-ossp";

-- ============================================================
-- 1. Tablas — núcleo compartido (core-data)
-- ============================================================

create table if not exists tipos_servicio (
    codigo text primary key,
    nombre text not null,
    modulo_origen text not null
);

create table if not exists unidades (
    id bigserial primary key,
    placa text not null unique,
    tipo_unidad text not null,
    cliente text not null,
    fecha_registro bigint not null,
    cantidad_cisternas integer not null default 1,
    -- "Último valor conocido" de la unidad -- se precarga la próxima vez que se busque la misma
    -- patente en Nueva Visita, en vez de tener que volver a tipear todo de cero.
    cuit text not null default '',
    patente_tractor text not null default '',
    marca_tractor text not null default '',
    anio_tractor text not null default '',
    modelo_tractor text not null default '',
    patente_semi text not null default '',
    marca_semi text not null default '',
    anio_semi text not null default '',
    modelo_semi text not null default ''
);

-- Catálogos de autocompletar de "Nueva Visita" -- antes eran Room puro (cada dispositivo con su
-- propia lista, sin enterarse de lo que tipeaba otro técnico en otro teléfono; bug real
-- reportado por el usuario). Mismo criterio de "catálogo chico, se reemplaza entero en cada
-- lectura" que tipos_servicio -- ver ClienteCatalogoDao/AuditorCatalogoDao en core-data.
create table if not exists clientes_catalogo (
    nombre text primary key,
    cuit text not null default ''
);

create table if not exists auditores_catalogo (
    nombre text primary key
);

create table if not exists historial_servicios (
    id bigserial primary key,
    unidad_id bigint not null references unidades(id),
    tipo_servicio_codigo text not null references tipos_servicio(codigo),
    fecha bigint not null,
    auditor text not null,
    documento_path text not null,       -- path local del dispositivo que lo generó
    documento_url text,                  -- URL en Supabase Storage (Fase F, nullable)
    -- Detalle puntual del servicio para la Nota de Trabajo (ej. total de precintos
    -- utilizados, qué repuesto se cambió en cada compartimiento, Nº de reporte).
    detalle text,
    creado_en timestamptz not null default now()
);

create table if not exists sesiones_servicio (
    id bigserial primary key,
    unidad_id bigint not null references unidades(id),
    fecha bigint not null,
    auditor text not null,
    cliente text not null,
    domicilio text not null,
    cuit text not null,
    estado text not null check (estado in ('EN_PROGRESO', 'CERRADA')),
    hora_inicio bigint not null,
    hora_cierre bigint,
    patente_tractor text not null,
    marca_tractor text not null,
    anio_tractor text not null,
    modelo_tractor text not null,
    patente_semi text not null,
    marca_semi text not null,
    anio_semi text not null,
    modelo_semi text not null,
    -- URL del PDF consolidado de la visita ("Finalizar visita") una vez subido a Storage.
    nota_trabajo_url text,
    creado_en timestamptz not null default now()
);

create table if not exists sesion_servicio_items (
    id bigserial primary key,
    sesion_id bigint not null references sesiones_servicio(id) on delete cascade,
    tipo_servicio_codigo text not null references tipos_servicio(codigo),
    estado text not null check (estado in ('PENDIENTE', 'COMPLETO')),
    historial_servicio_id bigint references historial_servicios(id)
);

create table if not exists calibraciones (
    id bigserial primary key,
    numero_certificado bigint not null unique, -- asignado desde seq_certificado_calibracion
    unidad_id bigint not null references unidades(id),
    fecha bigint not null,
    vigencia_anios integer not null check (vigencia_anios in (1, 2, 4)),
    fecha_vencimiento bigint not null,
    transportista text not null,
    cuit text not null,
    patente_tractor text not null,
    marca_tractor text not null,
    tipo_ejes_tractor text,
    rodado_tractor text,
    tara numeric,
    maximo_peso_permitido numeric,
    patente_semi text not null,
    marca_semi text not null,
    modelo_semi text,
    tipo_ejes_semi text,
    rodado_semi text,
    cota_anterior numeric,
    cota_posterior numeric,
    volumen_semi numeric,
    -- R 14-03: Planilla de Calibración de Uso Interno.
    hora_inicio bigint,
    hora_fin bigint,
    documento_interno_path text,
    documento_interno_url text,
    creado_en timestamptz not null default now()
);

create table if not exists calibracion_cisternas (
    id bigserial primary key,
    calibracion_id bigint not null references calibraciones(id) on delete cascade,
    cisterna_numero integer not null,
    capacidad numeric,
    enrase_mm numeric,
    vacio_mm numeric,
    vacio_lts numeric,
    rebalse_mm numeric,
    precinto_enrase text,
    precinto_boca text,
    precinto_soporte text,
    -- R 14-03: trazabilidad y posición de sensores.
    sensor_marca text,
    sensor_numero_serie text,
    sensor_cumple boolean,
    posicion_cumple boolean,
    posicion_observacion text,
    litros_sensor_rebalse numeric
);

create table if not exists calibracion_descarga_patron (
    id bigserial primary key,
    calibracion_cisterna_id bigint not null references calibracion_cisternas(id) on delete cascade,
    punto_volumen_lts integer not null check (punto_volumen_lts in (3000, 1000, 500, 100, 20)),
    numero_lectura integer not null default 1, -- por si se toma más de una lectura en el mismo punto
    valor_medido numeric
);

-- ============================================================
-- 2. Tablas — Planilla de Control de Tapas Cisternas (R 11-01)
-- ============================================================

create table if not exists planillas_tapas (
    id bigserial primary key,
    unidad_id bigint not null references unidades(id),
    fecha bigint not null,
    hora_inicio bigint,
    hora_fin bigint,
    fabricante_tapa_ph text,
    creado_en timestamptz not null default now()
);

create table if not exists planilla_tapas_compartimentos (
    id bigserial primary key,
    planilla_id bigint not null references planillas_tapas(id) on delete cascade,
    cisterna_numero integer not null,
    ultimo_producto_transportado text,
    medicion_gases_lel numeric
);

-- Checklist flat de ~30 puntos; item_codigo es un identificador estable definido en
-- el código de la app (mismo patrón que Control Visual Externa), no un catálogo aparte.
create table if not exists planilla_tapas_items (
    id bigserial primary key,
    planilla_id bigint not null references planillas_tapas(id) on delete cascade,
    item_codigo text not null,
    cumple boolean,
    observacion text
);

-- ============================================================
-- 3. Tablas — feature-inspeccion-glp
-- ============================================================

create table if not exists checklist_plantillas_glp (
    id uuid primary key default uuid_generate_v4(),
    nombre text not null,
    descripcion text
);

create table if not exists items_checklist_glp (
    id uuid primary key default uuid_generate_v4(),
    plantilla_id uuid not null references checklist_plantillas_glp(id),
    categoria text,
    subcategoria text,
    numero integer not null,
    descripcion text not null,
    tipo_incumplimiento text not null,
    aplica_fob boolean not null,
    aplica_cif boolean not null,
    orden integer not null
);

create table if not exists clientes_glp (
    id uuid primary key default uuid_generate_v4(),
    razon_social text not null,
    direccion text,
    contacto text,
    fecha_creacion timestamptz not null default now(),
    fecha_modificacion timestamptz not null default now()
);

-- Cada vehículo GLP se compone de 2 filas de `unidades` (TRACTOR y SEMI, mismo
-- patrón que Calibrado/Informes/Ejes), más los campos propios de GLP.
create table if not exists vehiculos_glp (
    id uuid primary key default uuid_generate_v4(),
    cliente_id uuid not null references clientes_glp(id),
    unidad_tractor_id bigint not null references unidades(id),
    unidad_semi_id bigint not null references unidades(id),
    marca_tractor text,
    modelo_tractor text,
    tara_semirremolque double precision,
    capacidad_semirremolque double precision,
    marca_semirremolque text,
    modelo_semirremolque text,
    fabricante_semirremolque text,
    bandera text,
    numero_matricula text,
    fecha_creacion timestamptz not null default now(),
    fecha_modificacion timestamptz not null default now()
);

create table if not exists informes_glp (
    id uuid primary key default uuid_generate_v4(),
    numero_informe text not null,
    vehiculo_glp_id uuid not null references vehiculos_glp(id),
    plantilla_id uuid not null references checklist_plantillas_glp(id),
    fecha timestamptz not null,
    estado text not null,
    ruta_docx text,
    historial_servicio_id bigint references historial_servicios(id),
    fecha_creacion timestamptz not null default now(),
    fecha_modificacion timestamptz not null default now()
);

create table if not exists item_resultados_glp (
    id uuid primary key default uuid_generate_v4(),
    informe_id uuid not null references informes_glp(id) on delete cascade,
    item_checklist_id uuid not null references items_checklist_glp(id),
    estado text not null,
    comentario text,
    fecha_creacion timestamptz not null default now(),
    fecha_modificacion timestamptz not null default now()
);

create table if not exists fotos_glp (
    id uuid primary key default uuid_generate_v4(),
    item_resultado_id uuid not null references item_resultados_glp(id) on delete cascade,
    ruta_archivo text,
    fecha_creacion timestamptz not null default now()
);

-- ============================================================
-- 4. Red de seguridad — por si este script corre contra un proyecto
-- que ya tiene las tablas de arriba pero sin las columnas agregadas
-- después (CREATE TABLE IF NOT EXISTS no las agrega solo). No hace
-- nada si ya están.
-- ============================================================

alter table historial_servicios add column if not exists detalle text;
alter table sesiones_servicio add column if not exists nota_trabajo_url text;

alter table unidades add column if not exists cuit text not null default '';
alter table unidades add column if not exists patente_tractor text not null default '';
alter table unidades add column if not exists marca_tractor text not null default '';
alter table unidades add column if not exists anio_tractor text not null default '';
alter table unidades add column if not exists modelo_tractor text not null default '';
alter table unidades add column if not exists patente_semi text not null default '';
alter table unidades add column if not exists marca_semi text not null default '';
alter table unidades add column if not exists anio_semi text not null default '';
alter table unidades add column if not exists modelo_semi text not null default '';

alter table calibraciones add column if not exists hora_inicio bigint;
alter table calibraciones add column if not exists hora_fin bigint;
alter table calibraciones add column if not exists documento_interno_path text;
alter table calibraciones add column if not exists documento_interno_url text;

alter table calibracion_cisternas add column if not exists sensor_marca text;
alter table calibracion_cisternas add column if not exists sensor_numero_serie text;
alter table calibracion_cisternas add column if not exists sensor_cumple boolean;
alter table calibracion_cisternas add column if not exists posicion_cumple boolean;
alter table calibracion_cisternas add column if not exists posicion_observacion text;
alter table calibracion_cisternas add column if not exists litros_sensor_rebalse numeric;

alter table vehiculos_glp add column if not exists modelo_tractor text;
alter table vehiculos_glp add column if not exists marca_semirremolque text;
alter table vehiculos_glp add column if not exists modelo_semirremolque text;
alter table vehiculos_glp add column if not exists fabricante_semirremolque text;

-- ============================================================
-- 5. Índices
-- ============================================================

create index if not exists idx_historial_unidad on historial_servicios(unidad_id);
create index if not exists idx_historial_tipo on historial_servicios(tipo_servicio_codigo);
create index if not exists idx_historial_unidad_fecha on historial_servicios(unidad_id, fecha);

create index if not exists idx_sesion_estado on sesiones_servicio(estado);
create index if not exists idx_sesion_unidad_fecha on sesiones_servicio(unidad_id, fecha);

create index if not exists idx_item_sesion on sesion_servicio_items(sesion_id);

create index if not exists idx_calibracion_unidad on calibraciones(unidad_id);
create index if not exists idx_calibracion_vigencia on calibraciones(unidad_id, fecha_vencimiento);
create index if not exists idx_calcist_calibracion on calibracion_cisternas(calibracion_id);
create index if not exists idx_descarga_patron_cisterna on calibracion_descarga_patron(calibracion_cisterna_id);

create index if not exists idx_tapas_comp_planilla on planilla_tapas_compartimentos(planilla_id);
create index if not exists idx_tapas_items_planilla on planilla_tapas_items(planilla_id);

create index if not exists idx_items_glp_plantilla on items_checklist_glp(plantilla_id);
create index if not exists idx_vehiculos_glp_cliente on vehiculos_glp(cliente_id);
create index if not exists idx_informes_glp_vehiculo on informes_glp(vehiculo_glp_id);
create index if not exists idx_item_resultados_glp_informe on item_resultados_glp(informe_id);

-- ============================================================
-- 6. Numeración correlativa compartida entre dispositivos
-- ============================================================
-- Cada secuencia garantiza atomicidad real: dos técnicos pidiendo "el
-- número siguiente" al mismo tiempo nunca reciben el mismo valor, sin
-- importar desde qué teléfono. Las funciones evitan exponer las
-- secuencias directamente vía la API REST.

create sequence if not exists seq_certificado_calibracion start 1;
create sequence if not exists seq_certificado_precinto start 1;
create sequence if not exists seq_certificado_tapa_hombre start 1;
create sequence if not exists reporte_observaciones_seq start 1;

create or replace function siguiente_numero_calibracion()
returns bigint language sql as $$
    select nextval('seq_certificado_calibracion');
$$;

-- SEC-004 (docs/security/AUDITORIA-2026-09-19.md): `calibraciones.numero_certificado` es la
-- única columna de correlativo real en todo el esquema que un INSERT directo por PostgREST
-- podía fijar a mano (cert_precinto/cert_tapa_hombre/reporte_observaciones no tienen tabla
-- remota propia -- son solo local + texto libre en historial_servicios.detalle, sin este
-- problema; planillas_tapas no tiene columna de numeración). Antes de este trigger, el cliente
-- (la app, vía `siguienteNumeroCalibracion()` + INSERT en dos pasos) elegía el valor de
-- `numero_certificado` -- SEC-002 ya restringió el INSERT a `authenticated`, pero seguía sin
-- haber ninguna garantía server-side de que ese número viniera realmente de la secuencia. Este
-- trigger pisa `numero_certificado` con `nextval(...)` SIEMPRE, sea lo que sea que mande el
-- cliente (incluido el valor "provisorio" 0 que manda hoy CalibracionDao.aRemota()) -- así el
-- número impreso en el certificado queda garantizado por el servidor, no por confianza en el
-- cliente. La app fue actualizada (CalibracionDao.insertarCalibracion/ControlCalibradoScreen)
-- para leer el número real desde la fila devuelta por el INSERT en vez de pedirlo aparte.
create or replace function calibraciones_asignar_numero()
returns trigger language plpgsql as $$
begin
    new.numero_certificado := nextval('seq_certificado_calibracion');
    return new;
end;
$$;

drop trigger if exists trg_calibraciones_asignar_numero on calibraciones;
create trigger trg_calibraciones_asignar_numero
before insert on calibraciones
for each row execute function calibraciones_asignar_numero();

create or replace function siguiente_numero_precinto()
returns bigint language sql as $$
    select nextval('seq_certificado_precinto');
$$;

create or replace function siguiente_numero_tapa_hombre()
returns bigint language sql as $$
    select nextval('seq_certificado_tapa_hombre');
$$;

create or replace function siguiente_numero_reporte()
returns bigint language sql as $$
    select nextval('reporte_observaciones_seq');
$$;

-- ============================================================
-- 7. RLS — restringida a "authenticated" (SEC-002 de la auditoría de
--    seguridad 2026-09-19, docs/security/AUDITORIA-2026-09-19.md).
-- ============================================================
-- Hasta acá la policy "permitir_todo_anon" no tenía cláusula `to`, lo
-- que en Postgres significa rol PUBLIC (incluye `anon`): CUALQUIERA
-- con la sola anon key del APK -- sin loguearse siquiera como el
-- usuario de servicio -- podía leer/escribir/borrar las 18 tablas de
-- negocio. Se confirmó en vivo con un GET real sin sesión que devolvió
-- CUIT y patentes de clientes reales.
--
-- La app SIEMPRE hace `asegurarLogin()` (SupabaseClientProvider.kt)
-- antes de tocar estas tablas, así que restringir a `authenticated`
-- no rompe nada -- solo saca a `anon` de la ecuación. No es la
-- solución definitiva (sigue siendo un único usuario de servicio
-- compartido por todos los técnicos, sin `auth.uid()` por fila -- ver
-- SEC-001/plan de remediación "este mes" en la auditoría), pero cierra
-- el acceso sin autenticación que era la parte crítica.
--
-- También se saca el `DELETE` de la policy: ningún flujo de la app
-- borra filas de estas tablas (son historial con validez legal/
-- contable), así que no hace falta exponerlo -- si algún día hace
-- falta un borrado administrativo, se hace desde el SQL Editor con
-- la service_role key, no desde el cliente Android.
--
-- `drop policy if exists` + `create` en vez de solo `create` porque
-- Postgres no tiene `create policy if not exists` -- así este bloque
-- es seguro de re-correr.

alter table tipos_servicio enable row level security;
alter table unidades enable row level security;
alter table clientes_catalogo enable row level security;
alter table auditores_catalogo enable row level security;
alter table historial_servicios enable row level security;
alter table sesiones_servicio enable row level security;
alter table sesion_servicio_items enable row level security;
alter table calibraciones enable row level security;
alter table calibracion_cisternas enable row level security;
alter table calibracion_descarga_patron enable row level security;
alter table planillas_tapas enable row level security;
alter table planilla_tapas_compartimentos enable row level security;
alter table planilla_tapas_items enable row level security;
alter table checklist_plantillas_glp enable row level security;
alter table items_checklist_glp enable row level security;
alter table clientes_glp enable row level security;
alter table vehiculos_glp enable row level security;
alter table informes_glp enable row level security;
alter table item_resultados_glp enable row level security;
alter table fotos_glp enable row level security;

do $$
declare
    tabla text;
begin
    foreach tabla in array array[
        'tipos_servicio', 'unidades', 'clientes_catalogo', 'auditores_catalogo',
        'historial_servicios', 'sesiones_servicio',
        'sesion_servicio_items', 'calibraciones', 'calibracion_cisternas',
        'calibracion_descarga_patron', 'planillas_tapas', 'planilla_tapas_compartimentos',
        'planilla_tapas_items', 'checklist_plantillas_glp', 'items_checklist_glp',
        'clientes_glp', 'vehiculos_glp', 'informes_glp', 'item_resultados_glp', 'fotos_glp'
    ]
    loop
        -- Policy vieja (permisiva, PUBLIC/anon incluido) -- se retira si quedó de una corrida anterior.
        execute format('drop policy if exists "permitir_todo_anon" on %I', tabla);
        -- Policies nuevas, una por operación, todas acotadas a `to authenticated`.
        execute format('drop policy if exists "app_authenticated_select" on %I', tabla);
        execute format('create policy "app_authenticated_select" on %I for select to authenticated using (true)', tabla);
        execute format('drop policy if exists "app_authenticated_insert" on %I', tabla);
        execute format('create policy "app_authenticated_insert" on %I for insert to authenticated with check (true)', tabla);
        execute format('drop policy if exists "app_authenticated_update" on %I', tabla);
        execute format('create policy "app_authenticated_update" on %I for update to authenticated using (true) with check (true)', tabla);
    end loop;
end $$;

-- ============================================================
-- 8. Storage — bucket "documentos" (versión final, ya corregida)
-- ============================================================

drop policy if exists "app puede subir documentos" on storage.objects;
create policy "app puede subir documentos"
on storage.objects for insert
to authenticated
with check (bucket_id = 'documentos');

drop policy if exists "app puede sobrescribir documentos (upsert)" on storage.objects;
create policy "app puede sobrescribir documentos (upsert)"
on storage.objects for update
to authenticated
using (bucket_id = 'documentos')
with check (bucket_id = 'documentos');

drop policy if exists "app puede leer documentos para upsert" on storage.objects;
create policy "app puede leer documentos para upsert"
on storage.objects for select
to authenticated
using (bucket_id = 'documentos');

-- ============================================================
-- 9. Seed — catálogo de tipos de servicio
-- ============================================================
-- 'control_ejes'/'inspeccion_glp' son los códigos canónicos reales (los que usa
-- CatalogoServiciosSeed y cada HistorialCompartidoHelper en el código Kotlin) -- este script
-- tenía sembrados por error los códigos viejos 'insp_ejes'/'insp_glp' de una versión anterior,
-- lo que dejaba DOS filas de catálogo por servicio en producción (confuso en el checklist de
-- "Nueva Visita", y una fuente real de bugs por comparación de string -- ver los alias que
-- existían en feature-sesion/SesionScreen.kt, ya simplificados). Las filas viejas y sus
-- referencias en sesion_servicio_items ya se migraron/borraron a mano en el proyecto de
-- producción; acá solo se corrige el seed para que un proyecto nuevo (o un reseed) no vuelva a
-- crear la confusión.

insert into tipos_servicio (codigo, nombre, modulo_origen) values
    ('insp_livianos', 'Spotcheck Livianos', 'feature-inspecciones'),
    ('insp_pesados', 'Inspección de Pesados', 'feature-inspecciones'),
    ('insp_coque', 'Inspección de Coque', 'feature-inspecciones'),
    ('insp_quimicos', 'Inspección de Químicos', 'feature-inspecciones'),
    ('insp_enganche', 'Control de rodamientos', 'feature-inspecciones'),
    ('insp_axion', 'CheckList Livianos', 'feature-inspecciones'),
    ('control_ejes', 'Control de Puntas de Eje', 'feature-inspeccion-ejes'),
    ('inspeccion_glp', 'Inspección GLP', 'feature-inspeccion-glp'),
    ('cert_tapa_hombre', 'Certificado Tapa de Boca de Hombre', 'feature-certificados'),
    ('cert_precinto', 'Acta de Reemplazo de Precinto', 'feature-certificados'),
    ('informe_carga_segura', 'Informe de Carga Segura', 'feature-informes'),
    ('control_visual_interna', 'Control Visual Interna', 'feature-controles-visuales'),
    ('control_visual_externa', 'Control Visual Externa', 'feature-controles-visuales'),
    ('control_espesores', 'Control de Espesores', 'feature-controles-visuales'),
    ('calibracion_unidad', 'Calibración de Unidad', 'feature-calibrado'),
    ('planilla_tapas', 'Planilla de Control de Tapas Cisternas', 'feature-planilla-tapas')
on conflict (codigo) do nothing;

commit;

-- ============================================================
-- 9. Quitar servicio de una visita: DELETE solo sobre items pendientes (supabase/006)
-- ============================================================

-- Corre esto UNA VEZ en el SQL Editor de Supabase.
--
-- Bug: el boton "Quitar" servicio de la visita no hacia nada. La auditoria de seguridad
-- (SEC-001/002) saco la policy de DELETE de todas las tablas, pero "Quitar" SI borra filas de
-- sesion_servicio_items. Postgres con RLS y sin policy de DELETE responde 200 y borra 0 filas,
-- sin error: la app borraba solo el espejo local y el item reaparecia al recargar desde Supabase.
--
-- Se habilita DELETE unicamente en esa tabla, solo para `authenticated`, y solo sobre items que
-- todavia NO estan completos (los COMPLETO tienen un HistorialServicio asociado y no se pueden
-- quitar -- la regla queda garantizada tambien del lado de la base).

drop policy if exists "app_authenticated_delete_pendientes" on sesion_servicio_items;
create policy "app_authenticated_delete_pendientes" on sesion_servicio_items
    for delete to authenticated
    using (estado <> 'COMPLETO');

-- ============================================================
-- 10. Planilla de Control de Atmosfera R 12-01 (supabase/007)
-- ============================================================

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
