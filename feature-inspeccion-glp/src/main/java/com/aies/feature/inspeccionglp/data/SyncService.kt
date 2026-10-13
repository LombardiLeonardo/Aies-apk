package com.aies.feature.inspeccionglp.data

import android.content.Context
import com.aies.feature.inspeccionglp.data.cloud.ChecklistPlantillaCloud
import com.aies.feature.inspeccionglp.data.cloud.ClienteCloud
import com.aies.feature.inspeccionglp.data.cloud.FotoCloud
import com.aies.feature.inspeccionglp.data.cloud.InformeCloud
import com.aies.feature.inspeccionglp.data.cloud.ItemChecklistCloud
import com.aies.feature.inspeccionglp.data.cloud.ItemResultadoCloud
import com.aies.feature.inspeccionglp.data.cloud.UnidadRemotaCloud
import com.aies.feature.inspeccionglp.data.cloud.VehiculoCloud
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.storage.storage
import java.io.File
import java.time.Instant

private fun Long.aIso(): String = Instant.ofEpochMilli(this).toString()
private fun String.aMillis(): Long = Instant.parse(this).toEpochMilli()

// Fase D del pase a base de datos compartida: nombres reales de las tablas GLP en el proyecto
// Supabase compartido (Fase B), todas con sufijo _glp salvo la `unidades` genérica que ya usan
// Calibrado/Informes/Ejes/Inspecciones para TRACTOR/SEMI.
private const val TABLA_PLANTILLAS = "checklist_plantillas_glp"
private const val TABLA_ITEMS_CHECKLIST = "items_checklist_glp"
private const val TABLA_CLIENTES = "clientes_glp"
private const val TABLA_VEHICULOS = "vehiculos_glp"
private const val TABLA_INFORMES = "informes_glp"
private const val TABLA_RESULTADOS = "item_resultados_glp"
private const val TABLA_FOTOS = "fotos_glp"
private const val TABLA_UNIDADES = "unidades"

/**
 * Equivalente de `SyncService.cs`: sync manual (botón), sin WorkManager ni sync en
 * background, igual que el original. `SincronizarPlantillasAsync` es pull-only (reconcilia
 * la plantilla oficial de la nube); `SubirCambiosAsync`/`BajarCambiosAsync` hacen push/pull
 * de datos reales con resolución "last-write-wins" por fecha de modificación.
 */
class SyncService(private val context: Context, private val dao: InspeccionGlpDao) {

    /**
     * Resuelve la `Unidad` compartida de una patente (TRACTOR o SEMI) contra el proyecto
     * Supabase nuevo, creándola si no existe — mismo patrón que usan
     * `HistorialCompartidoHelper` de los demás módulos, pero contra la tabla remota en vez de
     * la Room local (acá se está subiendo un Vehiculo de GLP, no registrando un servicio).
     */
    private suspend fun resolverOCrearUnidadRemota(cliente: SupabaseClient, placa: String, tipoUnidad: String, nombreCliente: String): Long {
        val existentes = cliente.from(TABLA_UNIDADES).select(columns = Columns.list("id", "placa", "tipo_unidad", "cliente", "fecha_registro", "cantidad_cisternas")) {
            filter { eq("placa", placa) }
        }.decodeList<UnidadRemotaCloud>()
        existentes.firstOrNull()?.let { return it.id }

        val nueva = cliente.from(TABLA_UNIDADES).insert(
            UnidadRemotaCloud(placa = placa, tipoUnidad = tipoUnidad, cliente = nombreCliente, fechaRegistro = System.currentTimeMillis())
        ) { select() }.decodeSingle<UnidadRemotaCloud>()
        return nueva.id
    }

    /** Inverso de [resolverOCrearUnidadRemota]: para reconstruir la patente al bajar un
     *  vehículo de la nube. Null si la unidad ya no existe (no debería pasar, pero no vale la
     *  pena romper el resto del pull por una fila huérfana). */
    private suspend fun obtenerPlacaUnidadRemota(cliente: SupabaseClient, unidadId: Long): String? {
        return cliente.from(TABLA_UNIDADES).select(columns = Columns.list("id", "placa", "tipo_unidad", "cliente", "fecha_registro", "cantidad_cisternas")) {
            filter { eq("id", unidadId) }
        }.decodeList<UnidadRemotaCloud>().firstOrNull()?.placa
    }

    suspend fun sincronizarTodo(): String {
        if (!SupabaseService.estaInicializado) {
            val ok = SupabaseService.inicializar(context)
            if (!ok) throw IllegalStateException("Supabase no está configurado todavía (Configurar conexión).")
        }
        if (!SupabaseService.haySesionActiva()) {
            throw IllegalStateException("No hay una sesión iniciada. Cerrá sesión y volvé a entrar.")
        }

        sincronizarPlantillas()
        val subidos = subirCambios()
        val bajados = bajarCambios()
        return "Subidos: $subidos. Bajados/actualizados: $bajados."
    }

    /**
     * Trae la(s) plantilla(s) de checklist y sus items desde Supabase, y reemplaza cualquier
     * versión generada localmente (con otro Id) por la oficial de la nube.
     */
    suspend fun sincronizarPlantillas() {
        if (!SupabaseService.estaInicializado) {
            val ok = SupabaseService.inicializar(context)
            if (!ok) return
        }
        if (!SupabaseService.haySesionActiva()) return

        val plantillasRemotas: List<ChecklistPlantillaCloud>
        val itemsRemotos: List<ItemChecklistCloud>
        try {
            plantillasRemotas = SupabaseService.cliente.from(TABLA_PLANTILLAS).select().decodeList()
            itemsRemotos = SupabaseService.cliente.from(TABLA_ITEMS_CHECKLIST).select().decodeList()
        } catch (e: Exception) {
            return // sin internet en este momento: seguimos con lo local, se reintenta en el próximo sync
        }

        plantillasRemotas.forEach { p ->
            dao.reemplazarPlantillaLocalPorLaDeNube(p.nombre, p.id)
            dao.guardarPlantillaSiNoExiste(ChecklistPlantilla(id = p.id, nombre = p.nombre, descripcion = p.descripcion))
        }

        itemsRemotos.forEach { it ->
            dao.guardarItemChecklistSiNoExiste(
                ItemChecklist(
                    id = it.id,
                    plantillaId = it.plantillaId,
                    categoria = it.categoria,
                    subcategoria = it.subcategoria,
                    numero = it.numero,
                    descripcion = it.descripcion,
                    tipoIncumplimiento = runCatching { TipoIncumplimiento.valueOf(it.tipoIncumplimiento) }
                        .getOrDefault(TipoIncumplimiento.Observacion),
                    aplicaFob = it.aplicaFob,
                    aplicaCif = it.aplicaCif,
                    orden = it.orden
                )
            )
        }
    }

    // ---------- Subir: local -> Supabase ----------

    private suspend fun subirCambios(): Int {
        var contador = 0
        val cliente = SupabaseService.cliente

        dao.getClientes().filterNot { it.sincronizado }.forEach { c ->
            cliente.from(TABLA_CLIENTES).upsert(
                ClienteCloud(
                    id = c.id, razonSocial = c.razonSocial, direccion = c.direccion, contacto = c.contacto,
                    fechaCreacion = c.fechaCreacion.aIso(), fechaModificacion = c.fechaModificacion.aIso()
                )
            )
            dao.marcarClienteSincronizado(c.id)
            contador++
        }

        dao.getTodosLosVehiculos().filterNot { it.sincronizado }.forEach { v ->
            val nombreCliente = dao.getCliente(v.clienteId)?.razonSocial ?: ""
            val unidadTractorId = resolverOCrearUnidadRemota(cliente, v.patenteTractor, "TRACTOR", nombreCliente)
            val unidadSemiId = resolverOCrearUnidadRemota(cliente, v.patenteSemirremolque, "SEMI", nombreCliente)
            cliente.from(TABLA_VEHICULOS).upsert(
                VehiculoCloud(
                    id = v.id, clienteId = v.clienteId,
                    unidadTractorId = unidadTractorId, unidadSemiId = unidadSemiId,
                    marcaTractor = v.marcaTractor, numeroMatricula = v.numeroMatricula,
                    taraSemirremolque = v.taraSemirremolque, capacidadSemirremolque = v.capacidadSemirremolque,
                    bandera = v.bandera,
                    fechaCreacion = v.fechaCreacion.aIso(), fechaModificacion = v.fechaModificacion.aIso()
                )
            )
            dao.marcarVehiculoSincronizado(v.id)
            contador++
        }

        dao.getInformes().filterNot { it.sincronizado }.forEach { i ->
            cliente.from(TABLA_INFORMES).upsert(
                InformeCloud(
                    id = i.id, numeroInforme = i.numeroInforme, vehiculoGlpId = i.vehiculoId,
                    plantillaId = i.plantillaId, fecha = i.fecha.aIso(), estado = i.estado.name,
                    rutaDocx = null, // la ruta del .docx es local a cada dispositivo, no tiene sentido en la nube
                    historialServicioId = null, // ver comentario en InformeCloud -- fuera de alcance de esta fase
                    fechaCreacion = i.fechaCreacion.aIso(), fechaModificacion = i.fechaModificacion.aIso()
                )
            )
            dao.marcarInformeSincronizado(i.id)
            contador++
        }

        dao.getTodosLosResultados().filterNot { it.sincronizado }.forEach { r ->
            cliente.from(TABLA_RESULTADOS).upsert(
                ItemResultadoCloud(
                    id = r.id, informeId = r.informeId, itemChecklistId = r.itemChecklistId,
                    estado = r.estado.name, comentario = r.comentario,
                    fechaCreacion = r.fechaCreacion.aIso(), fechaModificacion = r.fechaModificacion.aIso()
                )
            )
            dao.marcarResultadoSincronizado(r.id)
            contador++
        }

        // Fotos: subir el archivo real a Storage, y solo después guardar la fila con la URL resultante
        dao.getTodasLasFotos().filterNot { it.sincronizado }.forEach { foto ->
            val archivo = File(foto.rutaArchivo)
            if (archivo.exists()) {
                val nombreArchivo = "${foto.id}.jpg"
                val bytes = archivo.readBytes()

                cliente.storage.from("fotos").upload(nombreArchivo, bytes, upsert = true)
                val url = cliente.storage.from("fotos").publicUrl(nombreArchivo)

                cliente.from(TABLA_FOTOS).upsert(
                    FotoCloud(id = foto.id, itemResultadoId = foto.itemResultadoId, rutaArchivo = url, fechaCreacion = foto.fechaCreacion.aIso())
                )
            }
            dao.marcarFotoSincronizada(foto.id)
            contador++
        }

        return contador
    }

    // ---------- Bajar: Supabase -> local ----------

    private suspend fun bajarCambios(): Int {
        var contador = 0
        val cliente = SupabaseService.cliente

        val clientesRemotos: List<ClienteCloud> = cliente.from(TABLA_CLIENTES).select().decodeList()
        clientesRemotos.forEach { c ->
            val local = dao.getCliente(c.id)
            val fechaModRemota = c.fechaModificacion.aMillis()
            if (local != null && local.fechaModificacion >= fechaModRemota) return@forEach

            dao.guardarCliente(
                Cliente(
                    id = c.id, razonSocial = c.razonSocial, direccion = c.direccion, contacto = c.contacto,
                    fechaCreacion = c.fechaCreacion.aMillis(), fechaModificacion = fechaModRemota, sincronizado = true
                )
            )
            contador++
        }

        val vehiculosRemotos: List<VehiculoCloud> = cliente.from(TABLA_VEHICULOS).select().decodeList()
        val vehiculosLocales = dao.getTodosLosVehiculos()
        vehiculosRemotos.forEach { v ->
            val local = vehiculosLocales.firstOrNull { it.id == v.id }
            val fechaModRemota = v.fechaModificacion.aMillis()
            if (local != null && local.fechaModificacion >= fechaModRemota) return@forEach

            // patenteTractor/patenteSemirremolque ya no viajan en VehiculoCloud -- se resuelven
            // contra la `unidades` compartida por su id. modeloTractor/marcaSemirremolque/
            // modeloSemirremolque/fabricanteSemirremolque tampoco tienen columna remota (ver
            // VehiculoCloud): si ya había una fila local se preservan tal cual, si es la
            // primera vez que se ve este vehículo quedan vacíos hasta que alguien los cargue
            // a mano en este dispositivo.
            val placaTractor = obtenerPlacaUnidadRemota(cliente, v.unidadTractorId) ?: local?.patenteTractor ?: ""
            val placaSemi = obtenerPlacaUnidadRemota(cliente, v.unidadSemiId) ?: local?.patenteSemirremolque ?: ""

            dao.guardarVehiculo(
                Vehiculo(
                    id = v.id, clienteId = v.clienteId, patenteTractor = placaTractor,
                    marcaTractor = v.marcaTractor, modeloTractor = local?.modeloTractor,
                    patenteSemirremolque = placaSemi, marcaSemirremolque = local?.marcaSemirremolque,
                    modeloSemirremolque = local?.modeloSemirremolque, fabricanteSemirremolque = local?.fabricanteSemirremolque,
                    numeroMatricula = v.numeroMatricula, taraSemirremolque = v.taraSemirremolque,
                    capacidadSemirremolque = v.capacidadSemirremolque, bandera = v.bandera,
                    fechaCreacion = v.fechaCreacion.aMillis(), fechaModificacion = fechaModRemota, sincronizado = true
                )
            )
            contador++
        }

        val informesRemotos: List<InformeCloud> = cliente.from(TABLA_INFORMES).select().decodeList()
        informesRemotos.forEach { i ->
            val local = dao.getInforme(i.id)
            val fechaModRemota = i.fechaModificacion.aMillis()
            if (local != null && local.fechaModificacion >= fechaModRemota) return@forEach

            // clienteId ya no viaja en InformeCloud (informes_glp no lo tiene, se resuelve vía
            // vehiculo_glp_id -> vehiculos_glp.cliente_id) -- se preserva el local si ya existía,
            // y si es la primera vez que se ve este informe se resuelve contra el vehículo local.
            val clienteId = local?.clienteId ?: dao.getVehiculo(i.vehiculoGlpId)?.clienteId ?: ""

            dao.guardarInforme(
                Informe(
                    id = i.id, numeroInforme = i.numeroInforme, clienteId = clienteId, vehiculoId = i.vehiculoGlpId,
                    plantillaId = i.plantillaId, fecha = i.fecha.aMillis(),
                    estado = runCatching { EstadoInforme.valueOf(i.estado) }.getOrDefault(EstadoInforme.Borrador),
                    rutaDocx = local?.rutaDocx, // el .docx generado sigue siendo local a cada dispositivo
                    fechaCreacion = i.fechaCreacion.aMillis(), fechaModificacion = fechaModRemota, sincronizado = true
                )
            )
            contador++
        }

        val resultadosRemotos: List<ItemResultadoCloud> = cliente.from(TABLA_RESULTADOS).select().decodeList()
        val resultadosLocales = dao.getTodosLosResultados()
        resultadosRemotos.forEach { r ->
            val local = resultadosLocales.firstOrNull { it.id == r.id }
            val fechaModRemota = r.fechaModificacion.aMillis()
            if (local != null && local.fechaModificacion >= fechaModRemota) return@forEach

            dao.guardarResultado(
                ItemResultado(
                    id = r.id, informeId = r.informeId, itemChecklistId = r.itemChecklistId,
                    estado = runCatching { EstadoItemResultado.valueOf(r.estado) }.getOrDefault(EstadoItemResultado.OK),
                    comentario = r.comentario,
                    fechaCreacion = r.fechaCreacion.aMillis(), fechaModificacion = fechaModRemota, sincronizado = true
                )
            )
            contador++
        }

        // Fotos que existen en la nube pero no en este dispositivo: las descargamos
        val fotosRemotas: List<FotoCloud> = cliente.from(TABLA_FOTOS).select().decodeList()
        val fotosLocales = dao.getTodasLasFotos()
        fotosRemotas.forEach { f ->
            if (fotosLocales.any { it.id == f.id }) return@forEach
            if (f.rutaArchivo.isBlank()) return@forEach

            try {
                val carpetaFotos = File(context.cacheDir, "fotos").apply { mkdirs() }
                val rutaLocal = File(carpetaFotos, "${f.id}.jpg")

                val bytes = java.net.URL(f.rutaArchivo).openStream().use { it.readBytes() }
                rutaLocal.writeBytes(bytes)

                dao.guardarFoto(
                    Foto(id = f.id, itemResultadoId = f.itemResultadoId, rutaArchivo = rutaLocal.absolutePath,
                        fechaCreacion = f.fechaCreacion.aMillis(), sincronizado = true)
                )
                contador++
            } catch (e: Exception) {
                // si falla la descarga de una foto puntual no frenamos el resto de la sincronización
            }
        }

        return contador
    }
}
