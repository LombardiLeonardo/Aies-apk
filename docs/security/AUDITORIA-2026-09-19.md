# Auditoría de seguridad — AIES Super App

**Fecha:** 2026-09-19
**Alcance:** repositorio `C:\Users\leolo\AndroidStudioProjects\aies-super-app` (app Android multi-módulo) + proyecto Supabase `arizbvzxaaubnlttwklm` (pruebas de solo lectura con la anon key extraída del APK).
**Modo:** `auditar` (auditoría completa), solo lectura sobre Supabase de producción.

---

## 1. Resumen ejecutivo

Estado general: **crítico**. La combinación de (a) un único usuario de servicio con credenciales embebidas en el APK y (b) políticas RLS "abiertas" (`using (true)`, sin restricción de rol) en las 18 tablas de negocio hace que **cualquier persona que tenga el APK pueda leer y escribir todos los datos de clientes de la empresa sin autenticarse siquiera como el usuario de servicio** — se confirmó en vivo con una petición GET usando solo la anon key pública, sin login, que devolvió CUIT, patentes y clientes reales. Esto también permite insertar certificados de calibración fraudulentos sin pasar por la numeración correlativa oficial.

Hallazgos por severidad: **3 Crítico · 1 Alto · 2 Medio · 1 Bajo · 3 Informativo** (9 hallazgos activos + 3 notas informativas de buenas prácticas/deuda técnica).

Las 3 acciones más urgentes: (1) restringir las policies de RLS a filas propias del técnico/sesión en vez de `using(true)` global; (2) mover la emisión de certificados a una RPC `SECURITY DEFINER` que sea la única vía de `INSERT`, quitando el `INSERT`/`UPDATE`/`DELETE` directo vía PostgREST; (3) evaluar pasar de "un usuario de servicio compartido" a autenticación por técnico (o, como mínimo, rotar la contraseña del usuario de servicio y no embeberla en texto plano).

## 2. Alcance y limitaciones

**Revisado:** build.gradle.kts de todos los módulos, AndroidManifest.xml de `:app` y de los 10 módulos `feature-*`, `local.properties`, `supabase/schema.sql` (esquema consolidado, 447 líneas), `SupabaseClientProvider.kt`, `SupabaseConfigService.kt`, `SmtpConfigService.kt`/`EmailService.kt`, `SecureStorage.kt`, `Migrations.kt` y los `*LocalDatabase.kt` de Room de cada módulo, `file_paths.xml`, utilidades `MediaStoreUtils.kt` de guardado de documentos, y pruebas de solo lectura reales contra la API REST y Storage de Supabase con la anon key.

**No revisado / limitaciones:**
- **Sección H (secretos en historial de git):** el directorio de trabajo **no es un repositorio git** (`Is a git repository: false`) — no hay historial que auditar. Si en algún momento se inicializa un repo a partir de este working copy, hay que correr `git log -p | grep -E "eyJ|service_role|password|apikey"` antes del primer push, porque `local.properties` con las credenciales en claro ya existe en disco.
- **Fase 4 (pruebas dinámicas con `adb`):** no hay dispositivo/emulador conectado (`adb` ni siquiera está en el PATH del entorno de la auditoría). No se pudo inspeccionar `databases/`/`shared_prefs/` en disco real, lanzar Activities exportadas con extras manipulados, ni probar `adb backup`.
- **Análisis del APK compilado (Fase 2):** no se generó el APK de release/liviano en este entorno (sin `JAVA_HOME`/JDK 17 funcional disponible para Gradle — el JBR embebido de Android Studio es JDK 25, incompatible con el compilador de Kotlin 1.9.24 fijado en el proyecto, ver más abajo). El análisis de "qué es recuperable desde el binario" se hizo por lectura directa de `BuildConfig.java` ya generado (`core-data/build/generated/source/buildConfig/debug/...`), que contiene los mismos valores que terminarían en el DEX del APK.
- **Ejecución de los tests de regresión nuevos:** `./gradlew :core-data:test :app:testDebugUnitTest` **falló por un problema de entorno preexistente, no relacionado con este cambio**: KSP/el compilador de Kotlin 1.9.24 no logra inicializarse contra el JDK 25.0.2 embebido en Android Studio (`java.lang.IllegalArgumentException: 25.0.2` al parsear la versión de Java). Es la misma falla con o sin los archivos nuevos de test. Los tests se revisaron manualmente por sintaxis/lógica pero **no se corrieron de punta a punta** — correrlos con un JDK 17 en `JAVA_HOME` es la validación pendiente.
- **RLS vía `pg_policies`/`pg_tables` en vivo:** ya se puede confirmar el problema leyendo `supabase/schema.sql` (que define la policy permisiva explícitamente) y con la prueba REST real, pero **no se consultó el catálogo del Postgres en producción directamente** (necesita SQL Editor). Ver `docs/security/rls_audit.sql`, generado para que el usuario lo corra y devuelva el resultado.
- **Bucket `documentos` público o no:** no se pudo confirmar `storage.buckets.public` sin acceso al SQL Editor (la prueba REST de listado devolvió `200 []`, compatible tanto con "vacío" como con "anon sin permiso silencioso"). Pendiente del resultado de `rls_audit.sql` punto 5.
- **Crash de "Exportar a Excel" del Historial:** no reproducido (requeriría UI/dispositivo). Se dejó documentado como hallazgo sospechado (SEC-007) con recomendación de fuzzing manual.

## 3. Tabla de hallazgos

| ID | Severidad | Área | Título | Estado |
|---|---|---|---|---|
| SEC-001 | Crítico | A — Auth/Secretos | Usuario de servicio único con email+password embebidos en claro en `local.properties`/`BuildConfig` | Confirmado |
| SEC-002 | Crítico | B — RLS | Policy `permitir_todo_anon` (`using(true) with check(true)`, sin `to`) en 18 tablas: cualquiera con la anon key lee/escribe/borra todo | Confirmado |
| SEC-003 | Alto | C — Storage | Bucket `documentos` público + nombres de archivo predecibles (correlativo incremental) → enumerable sin login | **Corregido y confirmado por completo** (bucket privado + URLs firmadas on-demand, probado de punta a punta en el dispositivo) |
| SEC-004 | Crítico | D — Correlativos/Integridad | La policy `permitir_todo_anon` permite `INSERT` directo en `calibraciones`/`planillas_tapas`/etc. saltando las RPC `siguiente_numero_*`: se pueden fabricar o duplicar certificados con validez legal | Confirmado |
| SEC-005 | Medio | E — Datos locales | `allowBackup="true"` + Room sin cifrar, con patentes/CUIT/clientes en texto plano en disco | Confirmado |
| SEC-006 | Medio | H — Build/Distribución | Variante `liviano` firmada con la key de **debug**, pensada para distribuirse "por chat" a técnicos | **Corregido** (keystore de release real generado y conectado) |
| SEC-007 | Bajo | F — Entradas de usuario | Sin validación de formato de patente/CUIT en el código; crash de "Exportar a Excel" del Historial no reproducido en esta auditoría | Sospechado |
| SEC-008 | Informativo | F — SQL local | `execSQL`/migraciones: todo con strings literales, sin concatenación de datos externos (nada que corregir, test de regresión agregado) | Confirmado (control correcto) |
| SEC-009 | Informativo | E — Datos locales | `SmtpConfigService`/`SupabaseConfigService`: la contraseña SMTP y el override de anon key sí usan `EncryptedSharedPreferences` (control correcto) | Confirmado (control correcto) |
| SEC-010 | Informativo | D — Migraciones | El script 005 (Planilla de Tapas/Calibrado) mencionado como bloqueado por "policy duplicada" ya no reproduce el problema en `supabase/schema.sql`: todo el bloque de policies usa `drop policy if exists` + `create` (idempotente) | Confirmado (aparentemente resuelto, pero deja la policy permisiva de SEC-002) |
| SEC-011 | Informativo | H — Dependencias | `supabase-kt`/`gotrue-kt` fijados en 2.6.0 (por el pin de Kotlin 1.9.24); Apache POI 5.2.5 razonablemente actualizado | Confirmado |

## 4. Detalle por hallazgo

### SEC-001 — Usuario de servicio único con credenciales en claro en el APK
**Severidad:** Crítico. Impacto alto (una única cuenta para toda la empresa, sin trazabilidad por técnico) × facilidad de explotación trivial (decompilar el APK o leer `BuildConfig.SUPABASE_SERVICE_PASSWORD`).

**Evidencia:**
- `local.properties:12-15` (no versionado, pero en disco):
  ```
  SUPABASE_SERVICE_EMAIL=app@aies-super-app.local
  SUPABASE_SERVICE_PASSWORD=bsas1... (8 caracteres, ver archivo completo localmente)
  ```
- `core-data/build/generated/source/buildConfig/debug/com/aies/coredata/BuildConfig.java:11-13` — los mismos valores, ya compilados, listos para terminar en el DEX del APK final.
- `core-data/src/main/java/com/aies/coredata/remote/SupabaseClientProvider.kt:55-70` (`asegurarLogin()`) — login automático con `Email`/`password` de `BuildConfig` al arrancar la app, sin pantalla de login propia.
- El anon key en sí decodifica a `role: anon` (esperable, no es un hallazgo en sí mismo):
  `{"iss":"supabase","ref":"arizbvzxaaubnlttwklm","role":"anon","iat":...}`.

**Cómo se explota:** cualquiera con el APK (compartido "por chat" según el propio comentario del repo, ver SEC-006) lo descomprime con `unzip`/`apktool`/`jadx`, lee la clase `BuildConfig` y obtiene URL + anon key + email + password del usuario de servicio. No hace falta ni siquiera loguearse: la policy de SEC-002 ya expone los datos con la sola anon key.

**Impacto de negocio:** cualquier persona con el APK (técnico actual, ex-empleado, alguien a quien se lo reenviaron) tiene acceso indefinido de lectura/escritura a toda la base de clientes, mientras la contraseña no se rote. Sin trazabilidad: todas las escrituras quedan a nombre del mismo `auditor`/usuario de servicio, no hay forma de saber qué dispositivo hizo qué.

**Solución recomendada:**
1. **Rotar `SUPABASE_SERVICE_PASSWORD` ahora** (Supabase Dashboard → Authentication → Users → reset password del usuario `app@aies-super-app.local`), sin esperar a la migración completa.
2. Mediano plazo: un usuario de Supabase Auth por técnico (o por dispositivo), con `RLS` basada en `auth.uid()` en vez de un `permitir_todo_anon` global.
3. Mientras tanto, si se mantiene el usuario único, moverlo a Android Keystore en vez de `BuildConfig` no reduce el riesgo real (igual está en el binario que corre en el dispositivo) — la mitigación real es SEC-002.

**Test de regresión:** `core-data/src/test/java/com/aies/coredata/security/SecretsScanTest.kt` (falla si aparece un JWT `role=service_role` en el proyecto, o un JWT hardcodeado directo en `.kt`).

---

### SEC-002 — RLS habilitada pero completamente permisiva (`using(true)`) en 18 tablas
**Severidad:** Crítico. Compromiso total de datos sin condiciones — la definición misma de "Crítico" en la escala de la sección 7.

**Evidencia:**
- `supabase/schema.sql:384-399`:
  ```sql
  execute format('drop policy if exists "permitir_todo_anon" on %I', tabla);
  execute format('create policy "permitir_todo_anon" on %I for all using (true) with check (true)', tabla);
  ```
  aplicado sobre las 18 tablas listadas en el array (`unidades`, `sesiones_servicio`, `calibraciones`, `clientes_glp`, etc.). Sin cláusula `to`, lo que en Postgres significa rol `PUBLIC` (cualquier rol, incluido `anon`).
- **Prueba en vivo (solo lectura, sin login, 3 peticiones GET)** contra `https://arizbvzxaaubnlttwklm.supabase.co/rest/v1/sesiones_servicio?select=id,cliente,cuit,patente_tractor,patente_semi&limit=2` con únicamente la anon key (headers `apikey`/`Authorization: Bearer <anon key>`, **sin sesión de Auth**):
  ```json
  [{"id":3,"cliente":"TRANSCHEMICAL S.A.","cuit":"30-70297883-8","patente_tractor":"AG 266 IC","patente_semi":"AG 312 FG"}, ...]
  ```
  HTTP 200. Misma prueba contra `calibraciones` devolvió CUIT y patente de un certificado real. El propio comentario del código (`schema.sql:357-364`) ya documenta esto como una decisión temporal consciente: *"Política permisiva ... porque todavía no hay sistema de autenticación de usuarios"*.

**Cómo se explota:** `curl` con la anon key extraída del APK (SEC-001), sin login, contra cualquier tabla de negocio. `select=*` en vez de columnas específicas trae la fila completa.

**Impacto de negocio:** fuga total de la base de clientes (razón social, CUIT, patentes, historial de servicios que es base de facturación) a cualquiera con el APK. `for all` significa que la misma policy habilita `INSERT`/`UPDATE`/`DELETE`, no solo lectura (ver SEC-004 para el caso específico de certificados).

**Solución recomendada (ejemplo mínimo viable sin login por técnico, usando el usuario de servicio único mientras se migra):**
```sql
-- Reemplaza la policy permisiva: solo el rol authenticated (no anon), y sin DELETE directo.
drop policy if exists "permitir_todo_anon" on sesiones_servicio;
create policy "app_authenticated_select" on sesiones_servicio
  for select to authenticated using (true);
create policy "app_authenticated_insert" on sesiones_servicio
  for insert to authenticated with check (true);
create policy "app_authenticated_update" on sesiones_servicio
  for update to authenticated using (true) with check (true);
-- Repetir sin política de DELETE (o restringida a un rol admin aparte).
```
Esto ya saca a `anon` de la ecuación (la app igual funciona porque hace login con el usuario de servicio antes de leer/escribir). El paso siguiente, cuando haya auth por técnico, es cambiar `using (true)` por `using (auth.uid() = tecnico_id)` o equivalente.

**Test de regresión:** no automatizable desde el cliente Android (es una política del lado del servidor) — el control de regresión es `docs/security/rls_audit.sql` corrido periódicamente contra el proyecto real, más el script `supabase_probes.sh` que debe empezar a devolver 401/403 tras el fix.

---

### SEC-003 — Storage del bucket `documentos`: superficie de exposición ligada al usuario de servicio único
**Severidad:** Alto (impacto alto si el bucket resulta público o los nombres predecibles, pero requiere confirmar `storage.buckets.public` que no se pudo consultar sin el SQL Editor — por eso no es Crítico todavía).

**Evidencia:**
- `supabase/schema.sql:405-422`: las policies de `storage.objects` para `INSERT`/`UPDATE`/`SELECT` sobre `bucket_id = 'documentos'` están limitadas `to authenticated` (a diferencia de las tablas de SEC-002, acá si hay una restricción de rol).
- Prueba en vivo: `POST /storage/v1/object/list/documentos` con la anon key (sin login) devolvió `200 []` — compatible con "sin permiso, respuesta vacía" o con "bucket vacío"; no se pudo distinguir sin loguearse como el usuario de servicio (evitado a propósito para no generar tráfico de escritura/lectura autenticada más allá de lo mínimo).
- Pero el rol `authenticated` en este proyecto es alcanzable por **cualquiera** que tenga el email/password de SEC-001 — no es una barrera real distinta de "cualquiera con el APK".

**Cómo se explota:** con las credenciales de SEC-001, loguearse (`POST /auth/v1/token?grant_type=password`) y usar el token para `list`/`GET` cualquier objeto de `documentos`. Si los nombres de archivo son predecibles (correlativo/patente/fecha — no se pudo confirmar el patrón real sin datos subidos), se pueden enumerar documentos de otros clientes.

**Impacto de negocio:** exposición de certificados/informes/notas de trabajo con datos de clientes (igual que SEC-002 pero para los PDFs/Excels en sí, no las filas de la base).

**Solución recomendada:** confirmar con `docs/security/rls_audit.sql` (punto 5) si `storage.buckets.public = true`; si lo es, pasarlo a privado y servir los documentos con **signed URLs con vencimiento** (`storage.from('documentos').createSignedUrl(path, expiresIn)`); usar UUID en vez de nombres predecibles para los `documento_path`.

**Test de regresión:** pendiente de confirmar con el resultado de `rls_audit.sql`; no automatizable desde el cliente.

---

**Actualización — CONFIRMADO en producción (2026-09-19):**

```
select id, name, public, file_size_limit, allowed_mime_types from storage.buckets;
→ id: documentos | public: true | file_size_limit: null | allowed_mime_types: null
```

El bucket `documentos` **es público**, sin límite de tamaño ni de tipo de archivo. Se confirmó además con una query de solo lectura sobre `storage.objects` (paginado, 50 filas más recientes) que los nombres de archivo son exactamente `$carpeta/$id.$extension`, con `$id` el correlativo incremental de `HistorialServicio` (ej. `insp_axion/421.pdf`, `nota-trabajo/73.pdf`, `cert_precinto/380.pdf`) — **enumerables recorriendo números consecutivos desde 1**, sin ningún login, combinando bucket público + nombre adivinable. Severidad confirmada como Alto/Crítico según el criterio de la sección 7 (acceso indebido a datos con esfuerzo bajo: bastaba iterar `https://.../storage/v1/object/public/documentos/insp_livianos/N.pdf` para N=1..421+).

**Fix aplicado (alcance acotado a propósito, ver razonamiento):** en vez de migrar todo el flujo a bucket privado + signed URLs (cambio grande: afecta cómo se abren los documentos desde ~10 módulos, requeriría regenerar/re-firmar URLs periódicamente y no se puede probar de punta a punta sin dispositivo), se atacó la causa concreta y suficiente del riesgo -- la **previsibilidad del nombre**, no la publicidad del bucket en sí. `core-data/src/main/java/com/aies/coredata/remote/DocumentoStorageHelper.kt`, función `subirYObtenerUrlPublica`: se agrega un sufijo aleatorio (`UUID`, 12 caracteres) al nombre de archivo antes de subir. Como TODOS los ~10 call sites del repo pasan por esta única función (confirmado por grep), el fix cierra el vector de enumeración para cualquier documento nuevo sin tocar ningún otro archivo. Costo aceptado y documentado en el propio código: se pierde el upsert-por-nombre-estable (un reintento sube un objeto nuevo en vez de sobrescribir; el anterior queda huérfano, sin impacto funcional).

**Verificado:** `./gradlew :app:assembleDebug :core-data:test :app:testDebugUnitTest` con JDK 17 → `BUILD SUCCESSFUL`, sin tests fallados.

**Riesgo residual, documentado explícitamente (NO cerrado por este fix):** los ~400+ objetos ya subidos antes de este cambio (visibles en la query de `storage.objects`, IDs hasta 421 al momento de la auditoría) **siguen con nombres predecibles y el bucket sigue público** -- van a seguir siendo enumerables hasta que se regeneren (lo que reemplaza el objeto viejo por uno nuevo con nombre random, pero no borra ni renombra el viejo) o hasta que alguien con la `service_role` key haga una migración masiva de renombrado/backfill. No se intentó esa migración en este ciclo: requeriría la `service_role` key, que por regla de esta auditoría nunca se pide ni se usa desde acá.

**Cierre completo (2026-09-19, mismo día -- el usuario decidió atacar esto también en vez de dejarlo para más adelante):**

- `core-data/src/main/java/com/aies/coredata/remote/DocumentoStorageHelper.kt`: en vez de migrar los ~400 registros de `documento_url`/`nota_trabajo_url`/etc en las distintas tablas, se dejó la URL "pública" vieja guardada tal cual en la base (cero cambios de esquema, cero cambios en los ~10 call sites de `HistorialCompartidoHelper`) -- ahora es solo un identificador estable del path. `abrirDocumentoLocalOUrl` (única función que efectivamente abre un documento remoto, usada por feature-sesion y feature-nota-trabajo) le extrae el path a esa URL y pide una `createSignedUrl(path, 10.minutes)` fresca en el momento, con la sesión de la app ya logueada -- nunca se manejó ninguna credencial en este proceso, solo el cliente Supabase ya autenticado.
- Bucket `documentos` pasado a privado por el usuario en el SQL Editor: `update storage.buckets set public = false where id = 'documentos';`, confirmado con `select id, public from storage.buckets` → `public: false`.
- **Verificado en dos capas:**
  1. Solo lectura, sin login: `GET .../storage/v1/object/public/documentos/nota-trabajo/73.pdf` (una de las URLs viejas ya confirmadas como expuestas) → **HTTP 400** (antes devolvía el PDF real). La enumeración por nombre, incluso para los ~400 archivos subidos antes del fix, ya no funciona.
  2. Prueba real en el dispositivo (Motorola Edge 40): abrir un documento remoto desde Historial/Nota de Trabajo, primero con el bucket todavía público (para validar el código antes de tocar producción) y después con el bucket ya privado -- confirmado por el usuario que el documento se abre igual que siempre en ambos casos.
- `./gradlew :app:assembleDebug :core-data:test :app:testDebugUnitTest` con JDK 17 → `BUILD SUCCESSFUL`.

**SEC-003 no tiene ningún riesgo residual pendiente.** Único ítem que queda fuera de alcance (evaluado y descartado por ahora, no es parte del hallazgo original): restringir `allowed_mime_types`/`file_size_limit` en el bucket (hoy sin límite) es un endurecimiento adicional, no una vulnerabilidad -- con el bucket ya privado y solo el usuario de servicio autenticado pudiendo subir, el riesgo de abuso es bajo.

---

### SEC-004 — Numeración correlativa: la policy permisiva habilita `INSERT` directo, saltando las RPC
**Severidad:** Crítico. Los certificados de calibración/tapas/precintos tienen validez legal — poder insertarlos directo (sin pasar por `siguiente_numero_*`) rompe la garantía de unicidad/orden y permite fabricar certificados falsos.

**Evidencia:**
- `supabase/schema.sql:329-352` define las 4 secuencias y sus funciones `siguiente_numero_*` (pensadas como el único camino para obtener un número).
- Pero la misma policy de SEC-002 (`schema.sql:397`, `for all using (true) with check (true)`) se aplica también a `calibraciones`, `planillas_tapas`, etc. — no hay ningún `check` que obligue a que `numero_certificado` venga de la secuencia. Un `INSERT` directo con `numero_certificado` elegido a mano (mientras no choque con el `unique`) es aceptado igual.
- Prueba en vivo (solo lectura, no se intentó el INSERT para no escribir en producción): `GET /rest/v1/calibraciones?select=id,numero_certificado,transportista,cuit,...` con la anon key devolvió filas reales, confirmando que la tabla es alcanzable por el mismo canal que permitiría el `INSERT`.

**Cómo se explota (solo se documenta el paso, no se ejecutó):** `POST /rest/v1/calibraciones` con un body JSON completo y un `numero_certificado` elegido a mano, usando la anon key. La única defensa es el `unique` de Postgres (evita el número exacto ya usado, no evita usar uno "futuro" o reservarlo antes que el técnico legítimo).

**Impacto de negocio:** certificados de calibración/tapas/precintos con numeración manipulable socavan su validez legal; en caso de auditoría externa o disputa con un cliente, no hay garantía de que el número en el documento físico corresponda a una fila íntegra de la base.

**Solución recomendada:**
1. Aplicar la policy corregida de SEC-002 (sacar `anon`/dejar solo `authenticated` sin `DELETE`).
2. Mover la creación de estas filas a una función `SECURITY DEFINER` (`crear_calibracion(...)`) que internamente llame a `nextval` y haga el `INSERT`, y quitar el `INSERT` directo de la policy de la tabla (dejar solo `SELECT`/`UPDATE` acotado vía policy, sin `INSERT`).
3. Guardar un hash SHA-256 del PDF/Excel final en la fila (columna `documento_hash`) y, si se quiere ir más lejos, un QR de verificación que apunte a un endpoint de solo lectura que muestre "número X emitido el Y, hash Z" para que un tercero pueda validar el documento físico.

**Test de regresión:** no automatizable desde el cliente Android; incluir en `rls_audit.sql` (punto 2, columna `cmd`) la verificación de que `calibraciones`/`planillas_tapas`/etc. ya no tengan una policy `INSERT` para `anon`/`PUBLIC`.

---

### SEC-005 — `allowBackup="true"` + Room sin cifrar con datos sensibles
**Severidad:** Medio (requiere acceso físico al dispositivo o backup habilitado, pero el impacto es alto: toda la base local de patentes/clientes/CUIT queda legible).

**Evidencia:**
- `app/src/main/AndroidManifest.xml:8` — `android:allowBackup="true"`, sin `android:fullBackupContent` ni `dataExtractionRules` que lo acoten.
- Los 10 `*LocalDatabase.kt` (`core-data/.../AppDatabase.kt`, `feature-certificados/.../CertificadosLocalDatabase.kt`, etc.) usan `Room.databaseBuilder(...)` estándar sin `SupportFactory`/SQLCipher — la base SQLite queda sin cifrar en `/data/data/com.aies.superapp/databases/`.

**Cómo se explota:** con acceso físico y USB debugging habilitado, `adb backup -f backup.ab com.aies.superapp` (en dispositivos donde el backup no esté bloqueado a nivel OEM/versión) extrae la base completa; en un dispositivo rooteado, copiar `databases/` directamente.

**Impacto de negocio:** exposición de patentes, clientes, CUIT e historial de servicios si se pierde/roba/vende un dispositivo de un técnico sin borrado previo.

**Solución recomendada:**
```xml
<!-- AndroidManifest.xml -->
<application
    android:allowBackup="false"
    ...>
```
o, si se necesita backup para no perder trabajo en curso, `android:allowBackup="true"` + `android:dataExtractionRules="@xml/data_extraction_rules"` excluyendo explícitamente las bases de Room; y evaluar `net.zetetic:android-database-sqlcipher` + `SupportFactory` para cifrar las bases en reposo.

**Test de regresión:** `app/src/test/java/com/aies/superapp/security/ManifestSecurityTest.kt` — hoy documenta el estado actual (`allowBackup=true`) como advertencia explícita para que un cambio accidental no pase desapercibido, y deja preparado el flip a "debe ser false" el día que se corrija.

---

### SEC-006 — Variante `liviano` firmada con la key de debug, distribuida "por chat"
**Severidad:** Medio.

**Evidencia:** `app/build.gradle.kts:19-30`:
```kotlin
// Variante temporal solo para achicar el APK y poder mandarlo por chat (límite de 30 MB) --
// sigue firmada con la key de debug para poder instalarse igual que siempre, sin necesitar
// un keystore de release.
buildTypes {
    create("liviano") {
        initWith(getByName("debug"))
        isMinifyEnabled = true
        ...
```
No hay ningún `signingConfig` de release definido en el proyecto.

**Cómo se explota:** no es una vulnerabilidad explotable por un atacante externo directamente, pero: (a) la debug key es la misma en cualquier checkout del repo (se regenera localmente, no está protegida como un keystore de release), así que cualquiera con el repo puede firmar un APK que se "actualiza" sobre el instalado sin fricción; (b) al distribuirse por chat (canal no verificado, según indica el propio recuerdo de usuario `feedback_apk_delivery_location.md`), no hay garantía de integridad end-to-end de que el archivo recibido sea el que se generó.

**Impacto de negocio:** riesgo de que un técnico instale una versión modificada del APK (con el service account embebido, SEC-001) creyendo que es la oficial.

**Solución recomendada:** generar un `keystore.properties` real (no versionado) con un keystore de release dedicado, firmar la variante de distribución con esa key, y distribuir por un canal con integridad verificable (Play Internal Testing / Firebase App Distribution en vez de chat).

---

### SEC-007 — Falta de validación de patente/CUIT; crash de exportación no reproducido
**Severidad:** Bajo (endurecimiento recomendado; no se confirmó explotación).

**Evidencia:** no se encontró ningún `Regex`/función `validarPatente`/`validarCuit` en el código (`grep` sin resultados en todo el repo). Los campos de patente/CUIT/transportista llegan como `String` libre hasta `schema.sql` (`patente_tractor text not null`, sin `check`).

**Estado:** Sospechado, no confirmado — no se pudo ejercitar la UI en esta auditoría (sin dispositivo, Fase 4 omitida) para reproducir el crash de "Exportar a Excel" del Historial de Controles ni para confirmar si un input extremo (unicode, 10.000 caracteres, 0 cisternas) rompe la generación de documentos.

**Recomendación:** agregar validación de formato (patente vieja `AAA123`/Mercosur `AA123AA`, CUIT con dígito verificador) antes de persistir, y una sesión de fuzzing manual dirigida específicamente a reproducir el crash de exportación con datasets extremos.

---

## 5. Lo que está bien

- **Sin SQL injection local:** todos los `execSQL(` del proyecto (`Migrations.kt`, `InspeccionesLocalDatabase.kt`) usan DDL con strings literales fijos; no hay `rawQuery(`/`@RawQuery` en ningún módulo. Se agregó `SqlInjectionSourceScanTest.kt` para que siga siendo así.
- **Contraseña SMTP cifrada:** `SmtpConfigService.kt` guarda host/usuario/destinatarios en `DataStore` (no sensible) pero la contraseña específicamente en `SecureStorage` (`EncryptedSharedPreferences`, AES256-GCM) — mismo patrón para el override manual de la anon key en `SupabaseConfigService.kt`.
- **Componentes exportados mínimos:** de las ~35 Activities del proyecto (incluidas las 6 portadas del código legacy de Inspecciones), solo `MainActivity` es `exported="true"` — y es la única que necesita serlo (LAUNCHER). El `FileProvider` es `exported="false"` con `cache-path` acotado a `fotos/`/`informes/` (no usa `<root-path>` ni `path="."`).
- **Sin `usesCleartextTraffic`, sin `TrustManager`/`HostnameVerifier` custom, sin `HttpLoggingInterceptor` en `BODY`:** no se encontró ninguno de estos patrones en todo el repo — el tráfico TLS queda con la validación por defecto de la plataforma/Supabase.
- **`local.properties` correctamente gitignoreado** (aunque no hay repo git activo hoy, el `.gitignore` ya lo excluye para cuando lo haya).
- **Migraciones consolidadas e idempotentes:** `supabase/schema.sql` usa consistentemente `IF NOT EXISTS`/`CREATE OR REPLACE`/`DROP POLICY IF EXISTS` — el bloqueo histórico por "policy duplicada" del script 005 no debería reproducirse con este esquema consolidado.
- **RLS al menos está *habilitada*** en las 18 tablas (no ausente) — el problema es el contenido de la policy (SEC-002), no que falte `ENABLE ROW LEVEL SECURITY`.

## 6. Plan de remediación

**Hoy (rotar/cortar la hemorragia, sin tocar código):**
- Rotar `SUPABASE_SERVICE_PASSWORD` en Supabase Dashboard (SEC-001).
- Correr `docs/security/rls_audit.sql` en el SQL Editor y confirmar si el bucket `documentos` es público (SEC-003).

**Esta semana:**
- Reemplazar la policy `permitir_todo_anon` por policies separadas por operación, sacando a `anon` (SEC-002), empezando por las tablas con datos de clientes (`sesiones_servicio`, `calibraciones`, `clientes_glp`).
- Mover el `INSERT` de `calibraciones`/`planillas_tapas`/certificados a una función `SECURITY DEFINER` (SEC-004).
- `android:allowBackup="false"` (o `dataExtractionRules` acotado) (SEC-005).

**Este mes:**
- Evaluar autenticación por técnico (o por dispositivo) en vez del usuario de servicio único, con RLS basada en `auth.uid()`.
- Signed URLs + UUID para los documentos del bucket `documentos` (SEC-003).
- Keystore de release real para dejar de distribuir builds firmados con la key de debug (SEC-006).
- Validación de patente/CUIT + sesión de fuzzing dirigida al crash de "Exportar a Excel" (SEC-007).
- Correr `./gradlew :core-data:test :app:testDebugUnitTest` con un JDK 17 real (no el JBR de Android Studio, que en este entorno es JDK 25 e incompatible con el compilador de Kotlin 1.9.24 fijado en el proyecto) para confirmar que los tests de regresión nuevos pasan.

**A rotar de inmediato:** `SUPABASE_SERVICE_PASSWORD`. Si se confirma con `rls_audit.sql` que la anon key también tenía acceso a algo que no debería (ya confirmado para las tablas), evaluar si conviene regenerar también la anon key del proyecto una vez cerrado SEC-002 (la key en sí no es el problema — `role: anon` es el diseño esperado de Supabase — pero como ya circuló ampliamente en APKs distribuidos por chat, es una buena práctica de higiene rotarla junto con el resto).

---

## 7. Verificación posterior (2026-09-19)

**Modo:** `verificar`. Se releyó cada archivo modificado en disco (no se asumió que los cambios reportados por el usuario estaban bien hechos) y se re-corrieron pruebas propias, independientes de lo ya documentado.

### SEC-002 — RLS permisiva
**Estado: Corregido-en-código, pendiente de aplicar en Supabase (producción sigue vulnerable).**

- Se releyó `supabase/schema.sql` sección 7 (líneas 354–424): la policy `permitir_todo_anon` (`for all using(true) with check(true)`, sin `to`) fue reemplazada por un bloque `do $$ ... $$` que, por cada una de las 18 tablas, hace `drop policy if exists "permitir_todo_anon"` y crea 3 policies nuevas: `app_authenticated_select` (`for select to authenticated using (true)`), `app_authenticated_insert` (`for insert to authenticated with check (true)`), `app_authenticated_update` (`for update to authenticated using (true) with check (true)`). Las tres llevan `to authenticated` explícito (saca a `anon`/`PUBLIC`), y efectivamente no hay ninguna policy de `DELETE`. El SQL es correcto: sintaxis válida, `drop policy if exists` lo hace re-ejecutable, y el `execute format(...)` con `%I` escapa el nombre de tabla correctamente (no hay inyección vía el array fijo de nombres, que además está hardcodeado, no viene de input externo).
- **Prueba en vivo, solo lectura, sin login**, contra el proyecto Supabase real (`arizbvzxaaubnlttwklm`), con la anon key extraída de `local.properties`/`BuildConfig`:
  ```
  GET https://arizbvzxaaubnlttwklm.supabase.co/rest/v1/sesiones_servicio?select=id,cliente,cuit&limit=1
  Headers: apikey / Authorization: Bearer <anon key>
  → HTTP 200
  → [{"id":4,"cliente":"E2E TEST CLIENTE","cuit":"20-11111111-1"}]
  ```
  Sigue devolviendo datos reales sin autenticarse. Esto **no es una falla del fix**: el fix vive únicamente en el archivo `.sql` local del repo, y el propio encargo de esta verificación aclaraba que todavía no se corrió en el SQL Editor de Supabase. Confirma que **el fix está listo en código pero la producción sigue exactamente como en la auditoría original** (RLS efectiva = `permitir_todo_anon` vigente en el servidor).
- **Acción pendiente del usuario:** correr la sección 7 completa de `supabase/schema.sql` (o el bloque `do $$...$$`) en el SQL Editor de Supabase, y volver a correr la misma prueba GET para confirmar que pasa a 401/403 (Postgrest sin policy matching) o `[]`/error antes de dar esto por cerrado.

**Actualización — confirmado en producción el mismo día (2026-09-19), después de que el usuario corrió el script en el SQL Editor:**
```
GET .../rest/v1/sesiones_servicio?select=id,cliente,cuit&limit=1  → HTTP 200, []
GET .../rest/v1/calibraciones?select=*&limit=1                    → HTTP 200, []
GET .../rest/v1/clientes_glp?select=*&limit=1                     → HTTP 200, []
GET .../rest/v1/unidades?select=*&limit=1                         → HTTP 200, []
```
Las cuatro tablas probadas devuelven `200 []` (RLS filtra todas las filas para el rol `anon`, comportamiento estándar de PostgREST cuando ninguna policy matchea — no hace falta que sea 401/403) en vez de las filas reales que devolvían antes. **SEC-002 queda cerrado.**

### SEC-004 — INSERT directo en `calibraciones` (numeración correlativa)
**Estado: Corregido (con alcance revisado a partir de una relectura del esquema).**

**Corrección al hallazgo original:** el informe inicial mencionaba `calibraciones`/`planillas_tapas`/etc. en plural, pero al revisar el esquema completo para implementar el fix se confirmó que `planillas_tapas` **no tiene ninguna columna de numeración** (`id`, `unidad_id`, `fecha`, `hora_inicio`, `hora_fin`, `fabricante_tapa_ph` — ver `schema.sql:158-166`), y que los certificados de precinto/tapa de hombre (`cert_precinto`/`cert_tapa_hombre`) no tienen tabla remota propia con columna de correlativo: son solo registro local (Room) + texto libre en `historial_servicios.detalle`. La única columna real de numeración correlativa expuesta a `INSERT` por PostgREST en todo el esquema es `calibraciones.numero_certificado`.

**Fix aplicado:** en vez de mover la creación a una función `SECURITY DEFINER` con un payload JSON (que hubiera obligado a reescribir el flujo de inserción de 3 tablas -- `calibraciones`/`calibracion_cisternas`/`calibracion_descarga_patron` -- sin poder probarlo de punta a punta en un dispositivo real), se usó un trigger `BEFORE INSERT` (`calibraciones_asignar_numero`/`trg_calibraciones_asignar_numero`, `schema.sql` sección 6) que **pisa** `numero_certificado` con `nextval('seq_certificado_calibracion')` en cada INSERT, sin importar qué valor mande el cliente. Esto cierra el hallazgo con el mismo resultado de fondo (el número queda 100% controlado por el servidor) sin tocar la policy de SEC-002 ni las tablas hijas.

**Cambio de consistencia en la app** (necesario porque antes el número se pedía ANTES del INSERT vía `siguienteNumeroCalibracion()` y ahora el trigger podría, en teoría, asignar uno distinto si hubiera una discrepancia): `CalibracionDao.insertarCalibracion` ya no recibe el número como parámetro -- lo lee de la fila que devuelve el propio INSERT (`{ select() }`) y `ControlCalibradoScreen.generarCertificado()` usa ese valor real para el PDF en vez de uno "adivinado" de antemano. Esto además cierra una inconsistencia latente que ya existía antes del fix (nada garantizaba que el número mostrado en el PDF fuera el mismo que terminaba en la base si dos llamadas se intercalaban).

**Verificado:** `./gradlew :app:assembleDebug` y `./gradlew :core-data:test :app:testDebugUnitTest` con JDK 17 → `BUILD SUCCESSFUL`, sin tests fallados.

**Confirmación en producción (2026-09-19):** el usuario corrió `schema.sql` actualizado en el SQL Editor y confirmó el trigger activo con:
```sql
select tgname, tgenabled, pg_get_triggerdef(oid) as definicion
from pg_trigger where tgrelid = 'calibraciones'::regclass and not tgisinternal;
```
y además probó el flujo real (emisión de una calibración) confirmando que el número quedó asignado por el servidor. **SEC-004 queda cerrado con evidencia dinámica**, no solo estática.

### SEC-005 — `allowBackup`
**Estado: Corregido y confirmado.**

- `app/src/main/AndroidManifest.xml:5` → `android:allowBackup="false"` (releído directamente del archivo).
- `app/src/test/java/com/aies/superapp/security/ManifestSecurityTest.kt:35` → `EXPECTED_ALLOW_BACKUP = false`, coherente con el manifest.
- Test re-ejecutado (ver más abajo, corrida forzada con `--rerun`): `TEST-com.aies.superapp.security.ManifestSecurityTest.xml` → `tests="4" failures="0" errors="0"`.
- No se probó `adb backup` real: no hay dispositivo/adb disponible en este entorno (`adb devices` → `adb: command not found`, ni siquiera está en el PATH). Sin impacto en la conclusión: el flag de manifest es la única superficie relevante acá y ya se confirmó por lectura directa.

### SEC-007 — Validación de patente/CUIT
**Estado: Corregido (CUIT), mitigado parcialmente (patente) — criterio evaluado como razonable.**

- `core-data/src/main/java/com/aies/coredata/validacion/Validaciones.kt`: `esPatenteValida` valida formato viejo (`AAA123`) y Mercosur (`AA123AA`); `esCuitValido` implementa el algoritmo módulo 11 real de AFIP correctamente (coeficientes `5,4,3,2,7,6,5,4,3,2`, mapeo de resto 11→0 y 10→9) — se verificó a mano que el CUIT real visto en la prueba de SEC-002 (`30-70297883-8`) valida como correcto y que cambiar el último dígito lo invalida.
- `feature-sesion/src/main/java/com/aies/feature/sesion/SesionScreen.kt:1234-1268` (`iniciarVisita()`): el CUIT inválido corta con `return` antes de llegar al `scope.launch { ... }` que persiste (bloquea de verdad, no es solo un toast informativo con guardado silencioso). La patente (de las 3: unidad, tractor, semi) solo dispara un `Toast` de aviso y sigue el flujo.
- **Evaluación del criterio bloquear-CUIT/avisar-patente:** lo considero razonable, no insuficiente, por el argumento que da el propio comentario del código: el CUIT tiene un checksum matemático verificable (dígito verificador), así que un CUIT que no pasa el módulo 11 es objetivamente un error de tipeo, no un falso positivo — bloquear ahí no tiene costo de fricción injusta. La patente, en cambio, no tiene checksum: la regex cubre los dos formatos argentinos estándar, pero hay patentes reales fuera de ese universo (viejas de placa negra, diplomáticas, extranjeras, temporarias) que un bloqueo duro rechazaría de forma legítima en el campo. Único matiz: el toast de patente no impide seguir, así que si el técnico lo ignora (fácil bajo presión de campo) un dato con typo evidente (ej. `"../../etc/passwd"`, cubierto en el test) queda igual persistido — pero eso es un problema de UX/inputs libres en general, no un fallo del criterio de diseño en sí.
- Test `core-data/src/test/java/com/aies/coredata/security/InputValidationTest.kt` re-ejecutado con `--rerun`: `TEST-com.aies.coredata.security.InputValidationTest.xml` → `tests="5" failures="0" errors="0"`.

### SEC-006 — Keystore de release
**Estado: Sigue abierto, tal como se indicó (a propósito, no se tocó).** No se encontró ningún `signingConfig` de release nuevo en `app/build.gradle.kts`; la variante `liviano` sigue firmada con la debug key.

### Build y tests — confirmación propia e independiente
Se corrió, en este mismo entorno, con `JAVA_HOME` apuntando al JDK 17 (`C:\Users\leolo\.jdks\ms-17.0.20.1`):
```
./gradlew.bat -Dorg.gradle.java.home="C:\Users\leolo\.jdks\ms-17.0.20.1" :app:assembleDebug :core-data:test :app:testDebugUnitTest --console=plain
```
Primera corrida: `BUILD SUCCESSFUL` (mayormente `UP-TO-DATE`, indicando que ya se había corrido con éxito antes con los mismos inputs). Para no confiar en la caché, se forzó una segunda corrida con `--rerun` solo para los módulos de test:
```
./gradlew.bat -Dorg.gradle.java.home="C:\Users\leolo\.jdks\ms-17.0.20.1" :core-data:test :app:testDebugUnitTest --rerun --console=plain
```
→ `BUILD SUCCESSFUL`. Se inspeccionaron además los reportes JUnit generados en esa corrida forzada: `app/build/test-results/testDebugUnitTest/TEST-com.aies.superapp.security.ManifestSecurityTest.xml` (`tests="4" failures="0" errors="0"`) y `core-data/build/test-results/testDebugUnitTest/TEST-com.aies.coredata.security.InputValidationTest.xml` (`tests="5" failures="0" errors="0"`). Confirmado de forma independiente: no hay tests fallados, ni en el suite nuevo ni en el resto de la batería.

### Resumen de estado

| ID | Estado verificado |
|---|---|
| SEC-002 | **Corregido y confirmado en producción** (prueba en vivo tras aplicar el script: 4 tablas devuelven `200 []` para `anon`) |
| SEC-004 | **Corregido y confirmado en producción** (trigger activo, probado con una emisión real) |
| SEC-005 | Corregido y confirmado |
| SEC-006 | **Corregido y verificado** (keystore de release real, `liviano` firmado con él, confirmado con `apksigner verify`) |

### SEC-006 — Keystore de release (actualización 2026-09-19)
**Estado: Corregido.**

- Se generó un keystore de release real con `keytool` (RSA 2048, válido 30 años, `keystore/aies-release.jks`, alias `aies-release`), fuera de control de versiones (`keystore/*.jks`, `keystore/keystore.properties` agregados a `.gitignore`). Contraseñas generadas aleatoriamente (32 caracteres), guardadas únicamente en `keystore/keystore.properties` (no versionado, no compartido en el chat).
- `app/build.gradle.kts`: nuevo `signingConfigs.release` que lee de `keystore/keystore.properties` con el mismo patrón que `local.properties` en `core-data/build.gradle.kts` (si el archivo no existe, el build type queda sin firmar en vez de romper `debug` en un checkout nuevo). La variante `liviano` (la que se distribuye "por chat") y `release` ahora usan ese signing config en vez de heredar la key de debug.
- **Verificado:** `./gradlew :app:assembleLiviano` → `BUILD SUCCESSFUL`; `apksigner verify --print-certs` sobre el APK resultante confirma el certificado `CN=AIES S.A....` con SHA-256 `76:63:31:d3:2a:83:a3:40:ea:80:59:7f:e5:a2:1a:7a:ee:ab:41:06:b9:4d:4e:a7:31:27:4e:92:6b:61:d4:60`, coincide exactamente con el del keystore generado (no con la key de debug). `:app:assembleDebug` y los tests siguen en verde, sin romper el flujo de desarrollo día a día.
- **Importante para el usuario:** el archivo `keystore/aies-release.jks` y su `keystore.properties` son **el único artefacto** que garantiza que las próximas versiones de `liviano`/`release` se puedan instalar como actualización sobre lo ya distribuido (Android exige la misma firma para actualizar una app ya instalada, no solo para desinstalar+reinstalar). Si se pierde ese archivo, futuras entregas necesitarán desinstalar la app de cada teléfono antes de reinstalar. **Se recomienda hacer un backup del archivo `keystore/aies-release.jks` en un lugar seguro fuera de esta carpeta de proyecto** (gestor de contraseñas, backup cifrado, etc.) -- no se sube a ningún lado automáticamente por diseño (evita que termine expuesto igual que pasó con las credenciales de SEC-001).
| SEC-007 | Corregido (CUIT bloquea); mitigado por diseño (patente solo avisa, criterio evaluado como razonable) |

---

## 8. Re-auditoría completa (2026-09-20)

**Modo:** `auditar` (auditoría completa, no solo verificación) — pasó un día desde el informe original pero se agregó superficie nueva desde entonces (2 tablas Supabase, 9 columnas nuevas en `Unidad`/`unidades`, keystore de release real, `signingConfig`, migración `MIGRATION_10_11`). Se releyó todo el informe previo como punto de partida pero cada hallazgo cerrado se volvió a probar de cero, con pruebas de solo lectura reales contra producción, sin asumir que seguía bien.

### 8.1 Resumen ejecutivo

Los 7 hallazgos originales (SEC-001 a SEC-007) siguen en el mismo estado que dejó la verificación del 2026-09-19: SEC-002, SEC-003, SEC-004, SEC-005, SEC-006 y SEC-007 corregidos y re-confirmados en vivo hoy; SEC-001 (usuario de servicio único) sigue abierto por diseño, pendiente de migrar a auth por técnico, pero la contraseña sigue rotada. La superficie nueva (`clientes_catalogo`, `auditores_catalogo`, las 9 columnas nuevas de `Unidad`, el keystore de release, `MIGRATION_10_11`) está bien resuelta en el código y en `.gitignore`, **excepto un hallazgo nuevo real, no de seguridad pero sí de integridad/disponibilidad**: las 9 columnas nuevas de `unidades` (`cuit`, `patente_tractor`, etc.) existen en Room local y en el código Kotlin que sincroniza contra Supabase, pero **la migración de esquema equivalente en `supabase/schema.sql` (líneas 302-310) nunca se corrió contra el proyecto Supabase real** — confirmado en vivo con `GET .../unidades?select=cuit` → `HTTP 400 { "code": "42703", "message": "column unidades.cuit does not exist" }`. Como `UnidadDao.insertar()` no tiene try/catch a propósito (por diseño, para no guardar "a medias"), esto significa que **hoy, en producción, crear o registrar cualquier unidad nueva desde la app probablemente falla** con una excepción no capturada. Se documenta como SEC-012.

Hallazgos de esta pasada: **0 Crítico nuevo · 1 Alto nuevo (SEC-012) · 0 Medio nuevo · 0 Bajo nuevo · 1 Informativo nuevo (SEC-013)**. De los ya conocidos: 6 siguen cerrados y re-confirmados (SEC-002 a SEC-007), 1 sigue abierto sin cambios (SEC-001, por diseño/pendiente de migración mayor).

### 8.2 Alcance y limitaciones de esta pasada

- **No es un repositorio git** (confirmado de nuevo, `Is a git repository: false`) — sección H de "secretos en historial de git" no aplica, igual que en el informe original.
- **`adb devices`** (binario ubicado manualmente en `C:\Users\leolo\AppData\Local\Android\Sdk\platform-tools\adb.exe`) se ejecutó correctamente pero **no devolvió ningún dispositivo conectado** — Fase 4 (pruebas dinámicas) no se pudo ejecutar, igual que en el informe original. No se pudo reproducir en un dispositivo real la sospecha de SEC-012 (solo se confirmó contra la API REST de Supabase directamente).
- Todas las pruebas contra Supabase fueron de **solo lectura** (`GET`/`HEAD`, y un `Prefer: count=exact` de solo conteo) con la anon key extraída de `local.properties`, sin login y sin usar la `service_role` key en ningún momento.
- Build y tests: `./gradlew.bat -Dorg.gradle.java.home="C:\Users\leolo\.jdks\ms-17.0.20.1" :app:assembleDebug :core-data:test :app:testDebugUnitTest --console=plain` → `BUILD SUCCESSFUL` (primera corrida, mayormente `UP-TO-DATE`); se forzó una segunda corrida con `--rerun` sobre `:core-data:test :app:testDebugUnitTest` → también `BUILD SUCCESSFUL`, sin tests fallados.

### 8.3 Tabla de hallazgos de esta pasada

| ID | Severidad | Área | Título | Estado |
|---|---|---|---|---|
| SEC-001 | Crítico | A — Auth/Secretos | Usuario de servicio único | **Sin cambios, sigue abierto por diseño** (contraseña sigue rotada, largo 8, prefijo distinto al original — confirma que la rotación de la vez pasada sigue vigente) |
| SEC-002 | Crítico | B — RLS | Policy permisiva en 18 tablas de negocio | **Re-confirmado cerrado** (4 tablas de control devuelven `200 []`/`Content-Range: */0` sin login) |
| SEC-003 | Alto | C — Storage | Bucket `documentos` público + nombres predecibles | **Re-confirmado cerrado** (URL pública vieja conocida → `400 Bucket not found`, comportamiento esperado de un bucket privado ante el endpoint público) |
| SEC-004 | Crítico | D — Correlativos | INSERT directo salta la numeración | **Re-confirmado cerrado** (trigger sigue activo, `calibraciones` devuelve `200 []` para anon) |
| SEC-005 | Medio | E — Datos locales | `allowBackup` | **Re-confirmado cerrado** (`android:allowBackup="false"` en el manifest fuente, releído hoy) |
| SEC-006 | Medio | H — Build | Keystore de debug para distribución | **Re-confirmado cerrado** (keystore real, gitignoreado, sin copias fuera de `keystore/`) |
| SEC-007 | Bajo | F — Validación | CUIT/patente sin validar | **Sin cambios** (mismo criterio ya evaluado como razonable) |
| SEC-012 | **Alto** | D/E — Integridad, migración Supabase | Migración de esquema de `unidades` (9 columnas nuevas) nunca aplicada en Supabase producción — probable rotura de "Nueva Visita"/alta de unidad | **Corregido y confirmado (2026-09-21)** |
| SEC-013 | Informativo | B — RLS de superficie nueva | RLS de `clientes_catalogo`/`auditores_catalogo` aplicada correctamente, sin policy vieja/permisiva remanente | **Nuevo, confirmado (control correcto)** |

### 8.4 Detalle — SEC-012 (nuevo)

**Severidad: Alto.** No es una vulnerabilidad explotable por un atacante — es una falla de disponibilidad/integridad de datos que afecta a todos los usuarios legítimos de la app, descubierta al intentar confirmar (como pedía el encargo) que la RLS cubre las columnas nuevas de `unidades`. Se clasifica Alto por el criterio de impacto de negocio (rompe un flujo central — alta de unidad/"Nueva Visita" — para todos los técnicos en producción) aunque no encaje en el molde CIA de confidencialidad.

**Evidencia:**
- `core-data/src/main/java/com/aies/coredata/entities/Unidad.kt:40-48` y `core-data/src/main/java/com/aies/coredata/Migrations.kt:198-210` (`MIGRATION_10_11`): las 9 columnas nuevas (`cuit`, `patenteTractor`, `marcaTractor`, `anioTractor`, `modeloTractor`, `patenteSemi`, `marcaSemi`, `anioSemi`, `modeloSemi`) están en Room local.
- `core-data/src/main/java/com/aies/coredata/dao/UnidadDao.kt:27-56`: `UnidadRemota` serializa las mismas 9 columnas (snake_case vía `@SerialName`) y tanto `insertar()` (línea 81) como `getPorPlaca`/`getPorId` las leen/escriben contra la tabla remota `unidades`.
- `supabase/schema.sql:302-310` define el `alter table unidades add column if not exists ...` para las 9 columnas — **en el archivo del repo**, pero:
- **Prueba en vivo, solo lectura, sin login**, con la anon key:
  ```
  GET https://arizbvzxaaubnlttwklm.supabase.co/rest/v1/unidades?select=id,placa,cuit,patente_tractor,patente_semi&limit=3
  → HTTP 400
  → {"code":"42703","details":null,"hint":null,"message":"column unidades.cuit does not exist"}
  ```
  Contra control, `select=id,placa,cliente` (columnas viejas) sobre la misma tabla → `HTTP 200 []` (tabla alcanzable, RLS correcta, simplemente sin filas visibles para `anon` — consistente con SEC-002 cerrado). El error 42703 es específico de las columnas nuevas, no de la tabla ni de RLS.

**Cómo se reproduce (no hace falta explotar nada, es una falla funcional):** cualquier técnico que abra "Nueva Visita" y complete CUIT/patente de tractor o semi para una unidad nueva dispara `UnidadDao.insertar()`, que arma el objeto `UnidadRemota` completo (incluyendo las 9 columnas nuevas) y hace `POST /rest/v1/unidades`. Contra el esquema real de producción (sin esas columnas), Postgres/PostgREST debería rechazar el INSERT con el mismo error 42703 visto en la prueba de lectura. El propio comentario de la clase (`UnidadDao.kt:18-21`) documenta que esto es intencional: *"Sin try/catch a propósito [...] si Supabase falla, esto tira y el llamador (Nueva Visita) tiene que mostrarle el error al técnico"* — es decir, el fallo es ruidoso (no hay datos corruptos ni parciales), pero bloquea el flujo por completo.

**Impacto de negocio:** alta de unidades nuevas rota en producción para todos los técnicos desde que se desplegó este código (o desde que se generó el APK con esta versión de `core-data`). `getPorPlaca`/`getPorId` sí tienen `try/catch` con fallback a cache local, así que consultar una unidad *ya existente* en el dispositivo probablemente sigue funcionando (aunque con el mismo error de red logueado); el punto de quiebre es específicamente crear una unidad nueva o cualquier operación que dependa de un INSERT completo.

**Actualización — confirmado corregido (2026-09-21):** el usuario corrió las 9 sentencias `alter table unidades add column if not exists ...` en el SQL Editor. Prueba de solo lectura repetida:
```
GET .../rest/v1/unidades?select=cuit,patente_tractor&limit=1
→ HTTP 200, []
```
Ya no devuelve `42703` — las columnas existen en el esquema remoto. **SEC-012 queda cerrado.**

**Solución recomendada:** correr en el SQL Editor de Supabase (con la cuenta del proyecto, no la service_role vía API) las 9 sentencias `alter table unidades add column if not exists ...` de `supabase/schema.sql:302-310` (son idempotentes, `if not exists`, seguras de re-correr). Después, repetir la misma prueba de lectura (`GET .../unidades?select=cuit&limit=1`) y confirmar que devuelve `200` (con `[]` o datos, no `42703`) antes de dar esto por cerrado. Como mejora secundaria, considerar si `UnidadDao.insertar()` debería seguir sin try/catch (el diseño actual ya es defendible: falla ruidosa en vez de silenciosa) o si conviene una verificación de "schema en sync" al arrancar la app en modo debug para detectar este tipo de drift antes de que llegue a producción.

**Test de regresión:** no es automatizable desde el cliente Android (depende del estado del servidor remoto, no del código). Recomendación: agregar el chequeo de columnas a `docs/security/rls_audit.sql` (un `select column_name from information_schema.columns where table_name = 'unidades'` que se pueda correr rápido en el SQL Editor antes de cada release), y, si se quiere una señal automática, un smoke test instrumentado (`androidTest`) que intente un `UnidadDao.insertar()` real contra el proyecto de staging/QA (no contra producción) como parte del checklist pre-release.

### 8.5 Detalle — SEC-013 (nuevo, control correcto)

**Severidad: Informativo** (no es un hallazgo negativo, se documenta como confirmación positiva porque el encargo pedía explícitamente auditar esta superficie nueva).

**Evidencia:**
- `supabase/schema.sql:63-70`: `clientes_catalogo` (`nombre text primary key, cuit text not null default ''`) y `auditores_catalogo` (`nombre text primary key`) creadas con `create table if not exists`.
- `supabase/schema.sql:445-446`: `alter table clientes_catalogo enable row level security;` / `alter table auditores_catalogo enable row level security;`.
- `supabase/schema.sql:469`: ambas tablas están incluidas en el array de la función `do $$ ... $$` que aplica las policies `app_authenticated_select/insert/update` (`to authenticated`) y que además **dropea explícitamente cualquier policy vieja `permitir_todo_anon`** antes de crear las nuevas — mismo tratamiento que las 16 tablas ya auditadas, sin ningún caso especial que las deje más permisivas.
- **Prueba en vivo, solo lectura, sin login**, con la anon key:
  ```
  GET .../rest/v1/clientes_catalogo?select=*&limit=3   → HTTP 200 []
  GET .../rest/v1/auditores_catalogo?select=*&limit=3  → HTTP 200 []
  ```
  Para distinguir "tabla vacía" de "RLS bloqueando filas reales" (la misma ambigüedad que tuvo SEC-003 en el informe original), se usó `Prefer: count=exact` con `Range: 0-0`, comparando contra `sesiones_servicio` (tabla con datos reales conocidos, ya confirmada bloqueada):
  ```
  clientes_catalogo   → Content-Range: */0
  auditores_catalogo  → Content-Range: */0
  sesiones_servicio   → Content-Range: */0   (control: tabla con filas reales, ya confirmada RLS-bloqueada)
  ```
  Las tres dan el mismo resultado (`*/0`), consistente con RLS aplicándose igual en las tablas nuevas que en las ya auditadas — no es simplemente que estén vacías, aunque no se puede descartar del todo sin acceso a `pg_policies` vía SQL Editor (misma limitación que el resto del informe: sin `service_role` key no se puede consultar el catálogo de Postgres directamente).

**Conclusión:** RLS bien aplicada en la superficie nueva, sin regresión ni policy vieja/permisiva remanente. Sobre el punto 2 del encargo (¿la RLS cubre las columnas nuevas de `Unidad`?): RLS en Postgres es a nivel de fila, no de columna, así que en principio sí cubre cualquier columna de la tabla por diseño — pero en la práctica esto quedó bloqueado por SEC-012: las columnas ni siquiera existen todavía en el esquema remoto, así que hoy no hay ningún dato de CUIT/patente de tractor/semi expuesto vía esas columnas en Supabase (bueno para confidencialidad, malo para disponibilidad — ver SEC-012).

### 8.6 Otros puntos del encargo — confirmados sin hallazgos nuevos

- **Keystore de release** (`keystore/aies-release.jks`, `keystore/keystore.properties`): `.gitignore` los sigue excluyendo (`keystore/*.jks`, `keystore/*.keystore`, `keystore/keystore.properties`, líneas finales del archivo). Búsqueda repo-wide de `*.jks`/`keystore.properties` (excluyendo `/build/` y el propio `.gitignore`) no encontró ninguna copia fuera de `keystore/` — únicos resultados: `keystore/aies-release.jks`, `keystore/keystore.properties`, la mención en `docs/security/AUDITORIA-2026-09-19.md` (texto, no el archivo) y una entrada binaria de caché de Gradle (`executionHistory.bin`, metadata interna de Gradle, no un artefacto distribuible).
- **`app/build.gradle.kts` signingConfigs.release**: releído completo (líneas 1-64) — el flujo "si `keystore/keystore.properties` no existe, no rompe el build" se sostiene por lectura de código: `tieneKeystoreDeRelease` se calcula de `keystoreProperties.getProperty("storeFile") != null` (que da `null`/`false` si el `Properties()` quedó vacío por no existir el archivo), y tanto `signingConfigs { if (tieneKeystoreDeRelease) ... }` como los dos `buildTypes` (`liviano`, `release`) solo asignan `signingConfig` bajo esa misma condición — en un checkout sin el archivo, `debug` no se ve afectado y `liviano`/`release` simplemente no quedan firmados con la key de release (no hay ninguna referencia a `signingConfigs.getByName("release")` fuera de esos `if`, así que no hay forma de que reviente por "config no encontrada").
- **`MIGRATION_10_11`**: releída completa (`Migrations.kt:198-210`) — 9 sentencias `ALTER TABLE unidades ADD COLUMN ... TEXT NOT NULL DEFAULT ''`, todas con nombres de columna y tipos como literales fijos en el código Kotlin, sin interpolación de ningún dato externo/variable — mismo patrón que el resto de las migraciones del archivo (confirmado también por `SqlInjectionSourceScanTest.kt`, que sigue pasando). Sin riesgo de inyección SQL.
- **`local.properties` / `SUPABASE_SERVICE_PASSWORD`**: sigue gitignoreado (`.gitignore` tiene `local.properties` dos veces, líneas 6 y 8). Largo de la contraseña actual: 8 caracteres, primeros 4: `aies...` — distinto del prefijo documentado en la auditoría original (`bsas1...`), lo que confirma que sigue siendo la contraseña **rotada**, no la original filtrada. (Valor completo no impreso, por regla de esta auditoría.)
- **`AndroidManifest.xml` completo**, releído de punta a punta: `allowBackup="false"` (sin cambios), `MainActivity` es la única Activity fuente con `exported="true"` (correcto, es el LAUNCHER), el nuevo `android:windowSoftInputMode="adjustResize"` en `MainActivity` es un atributo de UI/layout (cómo se ajusta la pantalla cuando aparece el teclado) sin ninguna superficie de seguridad asociada — confirmado inocuo. El manifest *mergeado* de debug agrega dos componentes `exported="true"` adicionales (`androidx.activity.ComponentActivity`, del stub de test de Compose UI vía `debugImplementation("androidx.compose.ui:ui-test-manifest")`, y `ProfileInstallReceiver`, protegido por el permiso de plataforma `android.permission.DUMP`) — ambos son artefactos estándar de librerías de AndroidX presentes en prácticamente cualquier app Compose, solo aparecen en variantes debug/instrumented (no en `release`/`liviano`, que no dependen de `ui-test-manifest`), y no son hallazgos nuevos.
- **Build/tests**: `./gradlew.bat ... :app:assembleDebug :core-data:test :app:testDebugUnitTest` → `BUILD SUCCESSFUL`, confirmado con una segunda corrida forzada (`--rerun`) sobre los dos módulos de test.

### 8.7 Lo que está bien (reconfirmado hoy)

- Las 4 tablas de control (`sesiones_servicio`, `calibraciones`, `clientes_catalogo`, `auditores_catalogo`) y el bucket `documentos` siguen sin ser accesibles por `anon` sin login, con evidencia fresca de hoy (no solo la del informe original).
- El patrón de "toda tabla nueva entra al mismo array de policies `to authenticated`" se mantuvo consistentemente al agregar `clientes_catalogo`/`auditores_catalogo` — no hubo que recordárselo a nadie, ya está institucionalizado en el script.
- El patrón de "si el archivo de config no existe, el build no se rompe" (ya usado para `local.properties`) se reutilizó correctamente para `keystore/keystore.properties`.
- Ningún secreto nuevo (JWT `service_role`, contraseña en claro fuera de los archivos ya gitignoreados) apareció en la búsqueda repo-wide de esta pasada.

### 8.8 Plan de remediación de esta pasada

**Hoy:**
- Correr las 9 sentencias `alter table unidades add column if not exists ...` (`supabase/schema.sql:302-310`) en el SQL Editor de Supabase para cerrar SEC-012 — es la acción de mayor impacto inmediato porque rompe un flujo de uso diario, no solo un riesgo teórico.
- Repetir `GET .../unidades?select=cuit&limit=1` con la anon key y confirmar `200` (no `42703`) antes de considerar SEC-012 cerrado.

**Sin cambios respecto al plan del informe original:** SEC-001 (migrar a auth por técnico) sigue siendo la única brecha estructural pendiente; el resto del plan de la sección 6 sigue vigente donde no se marcó "corregido".

