package com.aies.feature.controlesvisuales

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aies.feature.controlesvisuales.data.DetalleCompartimento
import com.aies.feature.controlesvisuales.data.EncabezadoControl
import com.aies.feature.controlesvisuales.data.ProductoOptions
import com.aies.feature.controlesvisuales.data.TipoCargaOptions
import com.aies.feature.controlesvisuales.data.TipoUnidadOptions
import com.aies.feature.controlesvisuales.pdf.ControlPdfCommon
import com.aies.coreui.CampoTextoMayusculas
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val FMT_DDMMYYYY = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

/** Estado editable del encabezado común a las 3 planillas — clase mutable (no data class) para
 *  poder usar `by mutableStateOf` por campo, mismo patrón que CisternaState de feature-informes. */
class EncabezadoState {
    var fechaMillis by mutableStateOf<Long?>(System.currentTimeMillis())
    var razonSocial by mutableStateOf("")
    var domicilio by mutableStateOf("")
    var tipoActividad by mutableStateOf("")
    var cuit by mutableStateOf("")
    var tipoUnidad by mutableStateOf(TipoUnidadOptions.lista[0])
    var dominio by mutableStateOf("")
    var numeroRuta by mutableStateOf("")
    var marca by mutableStateOf("")
    var modelo by mutableStateOf("")
    var fabricante by mutableStateOf("")
    var fechaFabricacionCisternaMillis by mutableStateOf<Long?>(null)
    var antiguedad by mutableStateOf("")
    var fechaVencimientoVisualInternaMillis by mutableStateOf<Long?>(null)
    var fechaVencimientoVisualExternaMillis by mutableStateOf<Long?>(null)
    var fechaVencimientoEstanqueidadEspesoresMillis by mutableStateOf<Long?>(null)
    var tipoCarga by mutableStateOf(TipoCargaOptions.lista[0])
}

fun EncabezadoState.aEncabezadoControl() = EncabezadoControl(
    fecha = fechaMillis ?: System.currentTimeMillis(),
    razonSocial = razonSocial, domicilio = domicilio, tipoActividad = tipoActividad, cuit = cuit,
    tipoUnidad = tipoUnidad, dominio = dominio, numeroRuta = numeroRuta, marca = marca, modelo = modelo,
    fabricante = fabricante, fechaFabricacionCisterna = fechaFabricacionCisternaMillis, antiguedad = antiguedad,
    fechaVencimientoVisualInterna = fechaVencimientoVisualInternaMillis,
    fechaVencimientoVisualExterna = fechaVencimientoVisualExternaMillis,
    fechaVencimientoEstanqueidadEspesores = fechaVencimientoEstanqueidadEspesoresMillis,
    tipoCarga = tipoCarga
)

class DetalleCompartimentoState(val numero: Int) {
    var capacidad by mutableStateOf("")
    var producto by mutableStateOf(ProductoOptions.lista[0])
    var productoOtro by mutableStateOf("")
    var estado by mutableStateOf("Apto")
}

/** Aplana producto+productoOtro a un solo string para persistir/imprimir. */
fun DetalleCompartimentoState.productoFinal(): String =
    if (producto == ProductoOptions.OTROS && productoOtro.isNotBlank()) "${ProductoOptions.OTROS}: $productoOtro" else producto

fun DetalleCompartimentoState.aFila() = ControlPdfCommon.DetalleRow(numero, capacidad, productoFinal(), estado)

/** Cada pantalla de control solo pide/guarda su propia fecha de vencimiento. */
enum class TipoControlVisual { INTERNA, EXTERNA, ESPESORES }

private fun calcularAntiguedadAnios(fechaFabricacionMillis: Long): Int {
    val fab = Calendar.getInstance().apply { timeInMillis = fechaFabricacionMillis }
    val hoy = Calendar.getInstance()
    var anios = hoy.get(Calendar.YEAR) - fab.get(Calendar.YEAR)
    if (hoy.get(Calendar.DAY_OF_YEAR) < fab.get(Calendar.DAY_OF_YEAR)) anios--
    return anios.coerceAtLeast(0)
}

// ── Paso: dominio + cantidad de compartimentos ─────────────────────────────

@Composable
fun PasoDominio(
    dominio: String, onDominioChange: (String) -> Unit,
    cantidad: Int, onCantidadChange: (Int) -> Unit,
    onBuscar: () -> Unit,
    mensajeBusqueda: String?
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Dominio de la unidad", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CampoTextoMayusculas(value = dominio, onValueChange = onDominioChange, label = { Text("Dominio *") }, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onBuscar) { Text("Buscar") }
        }
        mensajeBusqueda?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

        Text("Cantidad de compartimentos (cisternas): $cantidad", style = MaterialTheme.typography.labelLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { onCantidadChange((cantidad - 1).coerceAtLeast(1)) }) { Text("−") }
            Text("  $cantidad  ", modifier = Modifier.padding(horizontal = 8.dp))
            OutlinedButton(onClick = { onCantidadChange((cantidad + 1).coerceAtMost(20)) }) { Text("+") }
        }
    }
}

// ── Encabezado común ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EncabezadoControlForm(state: EncabezadoState, tipoControl: TipoControlVisual) {
    LaunchedEffect(state.fechaFabricacionCisternaMillis) {
        state.antiguedad = state.fechaFabricacionCisternaMillis?.let { "${calcularAntiguedadAnios(it)} años" } ?: ""
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Datos generales", style = MaterialTheme.typography.titleMedium)
        DateFieldSimple("Fecha *", state.fechaMillis) { state.fechaMillis = it }
        CampoTextoMayusculas(value = state.razonSocial, onValueChange = { state.razonSocial = it }, label = { Text("Razón social *") }, modifier = Modifier.fillMaxWidth())
        CampoTextoMayusculas(value = state.domicilio, onValueChange = { state.domicilio = it }, label = { Text("Domicilio") }, modifier = Modifier.fillMaxWidth())
        CampoTextoMayusculas(value = state.tipoActividad, onValueChange = { state.tipoActividad = it }, label = { Text("Tipo de actividad") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = state.cuit, onValueChange = { state.cuit = it }, label = { Text("CUIT") }, modifier = Modifier.fillMaxWidth())

        Text("Tipo: Semi/acoplado/chasis")
        Row(verticalAlignment = Alignment.CenterVertically) {
            TipoUnidadOptions.lista.forEach { opcion ->
                RadioButton(selected = state.tipoUnidad == opcion, onClick = { state.tipoUnidad = opcion })
                Text(opcion, modifier = Modifier.padding(end = 12.dp))
            }
        }

        CampoTextoMayusculas(value = state.dominio, onValueChange = { state.dominio = it }, label = { Text("Dominio *") }, modifier = Modifier.fillMaxWidth())
        CampoTextoMayusculas(value = state.numeroRuta, onValueChange = { state.numeroRuta = it }, label = { Text("N° de ruta") }, modifier = Modifier.fillMaxWidth())
        CampoTextoMayusculas(value = state.marca, onValueChange = { state.marca = it }, label = { Text("Marca") }, modifier = Modifier.fillMaxWidth())
        CampoTextoMayusculas(value = state.modelo, onValueChange = { state.modelo = it }, label = { Text("Modelo") }, modifier = Modifier.fillMaxWidth())
        CampoTextoMayusculas(value = state.fabricante, onValueChange = { state.fabricante = it }, label = { Text("Fabricante") }, modifier = Modifier.fillMaxWidth())
        DateFieldSimple("Fecha de fabricación de la cisterna", state.fechaFabricacionCisternaMillis) { state.fechaFabricacionCisternaMillis = it }
        OutlinedTextField(value = state.antiguedad, onValueChange = {}, readOnly = true, label = { Text("Antigüedad") }, modifier = Modifier.fillMaxWidth())
        when (tipoControl) {
            TipoControlVisual.INTERNA -> DateFieldSimple("Vencimiento visual interna", state.fechaVencimientoVisualInternaMillis) { state.fechaVencimientoVisualInternaMillis = it }
            TipoControlVisual.EXTERNA -> DateFieldSimple("Vencimiento visual externa", state.fechaVencimientoVisualExternaMillis) { state.fechaVencimientoVisualExternaMillis = it }
            TipoControlVisual.ESPESORES -> DateFieldSimple("Vencimiento estanqueidad/espesores", state.fechaVencimientoEstanqueidadEspesoresMillis) { state.fechaVencimientoEstanqueidadEspesoresMillis = it }
        }

        Text("Tipo de carga: Ventral o Superior")
        Row(verticalAlignment = Alignment.CenterVertically) {
            TipoCargaOptions.lista.forEach { opcion ->
                RadioButton(selected = state.tipoCarga == opcion, onClick = { state.tipoCarga = opcion })
                Text(opcion, modifier = Modifier.padding(end = 12.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateFieldSimple(label: String, millis: Long?, onChange: (Long?) -> Unit) {
    var mostrar by remember { mutableStateOf(false) }
    val texto = millis?.let { FMT_DDMMYYYY.format(Date(it)) } ?: ""
    OutlinedTextField(
        value = texto, onValueChange = {}, readOnly = true, label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        trailingIcon = { OutlinedButton(onClick = { mostrar = true }) { Text("📅") } }
    )
    if (mostrar) {
        val state = rememberDatePickerState(initialSelectedDateMillis = millis ?: Calendar.getInstance().timeInMillis)
        DatePickerDialog(
            onDismissRequest = { mostrar = false },
            confirmButton = { Button(onClick = { onChange(state.selectedDateMillis); mostrar = false }) { Text("Aceptar") } },
            dismissButton = { OutlinedButton(onClick = { mostrar = false }) { Text("Cancelar") } }
        ) { DatePicker(state = state) }
    }
}

// ── Detalle de compartimentos ────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetalleCompartimentosSection(detalles: List<DetalleCompartimentoState>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Detalle de compartimentos", style = MaterialTheme.typography.titleMedium)
        detalles.forEach { d ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Compartimento N° ${d.numero}", style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(value = d.capacidad, onValueChange = { d.capacidad = it }, label = { Text("Capacidad (lts)") }, modifier = Modifier.fillMaxWidth())

                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                        OutlinedTextField(
                            value = d.producto, onValueChange = {}, readOnly = true, label = { Text("Producto") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            ProductoOptions.lista.forEach { opcion ->
                                DropdownMenuItem(text = { Text(opcion) }, onClick = { d.producto = opcion; expanded = false })
                            }
                        }
                    }
                    if (d.producto == ProductoOptions.OTROS) {
                        CampoTextoMayusculas(value = d.productoOtro, onValueChange = { d.productoOtro = it }, label = { Text("Especificar producto") }, modifier = Modifier.fillMaxWidth())
                    }

                    Text("Estado")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = d.estado == "Apto", onClick = { d.estado = "Apto" })
                        Text("Apto", modifier = Modifier.padding(end = 16.dp))
                        RadioButton(selected = d.estado == "No apto", onClick = { d.estado = "No apto" })
                        Text("No apto")
                    }
                }
            }
        }
    }
}
