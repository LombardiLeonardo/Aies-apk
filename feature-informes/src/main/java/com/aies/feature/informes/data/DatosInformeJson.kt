package com.aies.feature.informes.data

import org.json.JSONArray
import org.json.JSONObject

/** Serializa vacioData/trazData/posData a JSON, igual que Certificado.datos_json en la app Flask original. */
fun serializarDatosInforme(
    vacioData: List<CisternaVacio>,
    trazData: List<CisternaTrazabilidad>,
    posData: List<CisternaPosicion>
): String {
    val root = JSONObject()

    val vacioArr = JSONArray()
    vacioData.forEach {
        vacioArr.put(JSONObject().apply {
            put("volumen", it.volumen); put("vacioReq", it.vacioRequerido); put("vacioMed", it.vacioMedido)
            put("accion", it.accion); put("volFinal", it.volumenFinal); put("cumple", it.cumple)
        })
    }
    root.put("vacioData", vacioArr)

    val trazArr = JSONArray()
    trazData.forEach {
        trazArr.put(JSONObject().apply {
            put("marca", it.marca); put("nroSerie", it.nroSerie); put("cumple", it.cumple)
        })
    }
    root.put("trazData", trazArr)

    val posArr = JSONArray()
    posData.forEach {
        posArr.put(JSONObject().apply {
            put("litros", it.litros); put("observacion", it.observacion); put("cumple", it.cumple)
        })
    }
    root.put("posData", posArr)

    return root.toString()
}
