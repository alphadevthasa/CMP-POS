package com.pos.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pos.app.domain.model.PaymentMethod
import com.pos.app.print.label
import com.pos.app.util.toRupiah

@Composable
fun PaymentDialog(
    total: Long,
    onDismiss: () -> Unit,
    onConfirm: (PaymentMethod, Long) -> Unit,
) {
    var method by remember { mutableStateOf(PaymentMethod.CASH) }
    var tendered by remember { mutableStateOf(0L) }
    val change = (tendered - total).coerceAtLeast(0)
    val canPay = method != PaymentMethod.CASH || tendered >= total

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pembayaran") },
        text = {
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PaymentMethod.entries.forEach { m ->
                        FilterChip(
                            selected = method == m,
                            onClick = { method = m },
                            label = { Text(m.label()) },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                TotalLine("Total", total.toRupiah())

                if (method == PaymentMethod.CASH) {
                    Spacer(Modifier.height(8.dp))
                    RupiahTextField(
                        value = tendered,
                        onValueChange = { tendered = it },
                        label = "Uang diterima",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(onClick = { tendered = total }, label = { Text("Uang pas") })
                        AssistChip(onClick = { tendered = 50_000 }, label = { Text("50rb") })
                        AssistChip(onClick = { tendered = 100_000 }, label = { Text("100rb") })
                        AssistChip(onClick = { tendered = 200_000 }, label = { Text("200rb") })
                    }
                    Spacer(Modifier.height(8.dp))
                    TotalLine("Kembalian", change.toRupiah())
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(method, if (method == PaymentMethod.CASH) tendered else total) },
                enabled = canPay,
            ) { Text("Bayar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

@Composable
private fun TotalLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(value, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}
