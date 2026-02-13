package com.pos.app.ui

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.pos.app.util.grouped
import com.pos.app.util.parseRupiahInput

/**
 * Rupiah input. Editable text holds only grouped integer digits ("1.320.000"); the ",00"
 * decimal part is a non-editable suffix decoration so it can never be re-parsed as digits.
 * Reports integer rupiah via [onValueChange]; syncs from [value] on external changes (chips).
 */
@Composable
fun RupiahTextField(
    value: Long,
    onValueChange: (Long) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
) {
    var text by remember { mutableStateOf(if (value == 0L) "" else value.grouped()) }

    LaunchedEffect(value) {
        if (parseRupiahInput(text) != value) {
            text = if (value == 0L) "" else value.grouped()
        }
    }

    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            val parsed = parseRupiahInput(raw)
            text = if (raw.filter { it.isDigit() }.isEmpty()) "" else parsed.grouped()
            onValueChange(parsed)
        },
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        prefix = { Text("Rp ") },
        suffix = { Text(",00") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}
