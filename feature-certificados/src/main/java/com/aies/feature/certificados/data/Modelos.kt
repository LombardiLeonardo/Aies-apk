package com.aies.feature.certificados.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "empresas_transportistas")
data class EmpresaTransportista(@PrimaryKey val nombre: String)

/**
 * Un registro por certificado (o acta de precintos) emitido. Fase D paso 5: `nroCorrelativo` ya
 * no lo calcula un mecanismo local ("renglón INICIAL" + `ForzarCorrelativoInicial`, como en la
 * app original) sino que viene de las RPC de Postgres `siguiente_numero_precinto`/
 * `siguiente_numero_tapa_hombre` (numeración correlativa real, compartida entre dispositivos).
 * `transportista == "INICIAL"` puede seguir apareciendo en dispositivos que ya tengan esa fila
 * vieja guardada localmente de antes de este cambio -- ver el filtro en CertificadosDao.
 */
@Entity(tableName = "certificados")
data class CertificadoRegistro(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nroCorrelativo: String,
    val transportista: String,
    val patenteChasis: String,
    val patenteAcoplado: String,
    val sistemaTapas: String,
    val fechaEmisionTimestamp: Long
)
