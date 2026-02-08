package com.pos.app.ui

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.pos.app.util.grouped

/** Rupiah input: shows "12.000,00" while typing, reports integer rupiah via [onValueChange]. */
@Composable
fun RupiahTextField(
    value: Long,
    onValueChange: (Long) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
) {
    var text by remember(value) {
        mutableStateOf(if (value == 0L) "" else "${value.grouped()},00")
    }

    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            val digits = raw.filter { it.isDigit() }
            val parsed = digits.toLongOrNull() ?: 0L
            text = if (digits.isEmpty()) "" else "${parsed.grouped()},00"
            onValueChange(parsed)
        },
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        prefix = { Text("Rp ") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}
