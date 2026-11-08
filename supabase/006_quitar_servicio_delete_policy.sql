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
