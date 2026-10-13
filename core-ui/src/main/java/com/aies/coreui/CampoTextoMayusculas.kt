package com.aies.coreui

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Wrapper de OutlinedTextField para campos de texto libre (patente, cliente, domicilio, marca,
 * modelo, transportista, chofer, nombre, observaciones, etc.) que fuerza mayúsculas sobre el
 * VALOR en sí (no solo el hint del teclado — KeyboardCapitalization.Characters no bloquea pegar
 * texto en minúscula ni la entrada de un teclado físico). Úsese SOLO en campos de texto libre:
 * no para numéricos, CUIT, fechas de solo lectura, ni anclas de ExposedDropdownMenuBox.
 *
 * También centraliza el contraste de texto (Cambio 5: "texto gris" reportado por el usuario) —
 * fuerza onSurface en vez de heredar cualquier color por defecto del tema.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampoTextoMayusculas(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    singleLine: Boolean = false,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.uppercase()) },
        modifier = modifier,
        label = label,
        trailingIcon = trailingIcon,
        leadingIcon = leadingIcon,
        singleLine = singleLine,
        isError = isError,
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
        )
    )
}
