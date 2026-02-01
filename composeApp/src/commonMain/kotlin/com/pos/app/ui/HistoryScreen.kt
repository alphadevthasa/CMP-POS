package com.pos.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pos.app.domain.model.Order
import com.pos.app.domain.model.OrderStatus
import com.pos.app.print.RLine
import com.pos.app.print.label
import com.pos.app.util.toDateTimeString
import com.pos.app.util.toRupiah
import kotlinx.coroutines.CoroutineScope

@Composable
fun HistoryScreen(scope: CoroutineScope, snackbar: SnackbarHostState) {
    val store = LocalStore.current
    val state by store.state.collectAsState()
    var detailId by remember { mutableStateOf<Long?>(null) }
    var printLines by remember { mutableStateOf<List<RLine>?>(null) }
    var printName by remember { mutableStateOf("struk") }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Riwayat Transaksi", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            TextButton(onClick = { store.refreshOrders() }) { Text("Muat ulang") }
        }
        Spacer(Modifier.height(8.dp))
        if (state.orders.isEmpty()) {
            Text("Belum ada transaksi.", color = MaterialTheme.colorScheme.secondary)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.orders, key = { it.id }) { order ->
                    OrderRow(order) { detailId = order.id }
                }
            }
        }
    }

    detailId?.let { id ->
        OrderDetailDialog(
            orderId = id,
            onDismiss = { detailId = null },
            onPrintBill = { detailId = null; printLines = store.billLinesForOrder(id); printName = "bill-$id" },
            onPrintReceipt = { detailId = null; printLines = store.receiptLinesForOrder(id); printName = "struk-$id" },
        )
    }

    printLines?.let { lines ->
        ReceiptDialog(
            lines = lines,
            fileName = printName,
            scope = scope,
            snackbar = snackbar,
            onDismiss = { printLines = null },
        )
    }
}

@Composable
private fun OrderRow(order: Order, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(order.orderNo, fontWeight = FontWeight.SemiBold)
                Text(order.createdAt.toDateTimeString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                Text(order.status.label(), style = MaterialTheme.typography.labelMedium, color = statusColor(order.status))
            }
            Text(order.total.toRupiah(), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun OrderDetailDialog(
    orderId: Long,
    onDismiss: () -> Unit,
    onPrintBill: () -> Unit,
    onPrintReceipt: () -> Unit,
) {
    val store = LocalStore.current
    val detail = remember(orderId) { store.orderDetail(orderId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Detail ${detail?.order?.orderNo ?: ""}") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text("Status: ${detail?.order?.status?.label() ?: "-"}")
                Text("Waktu: ${detail?.order?.createdAt?.toDateTimeString() ?: "-"}")
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                detail?.lines?.forEach { line ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${line.qty} x ${line.nameSnapshot}", modifier = Modifier.weight(1f))
                        Text(line.lineTotal.toRupiah())
                    }
                }
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total", fontWeight = FontWeight.Bold)
                    Text(detail?.order?.total?.toRupiah() ?: "-", fontWeight = FontWeight.Bold)
                }
                detail?.payments?.lastOrNull()?.let { p ->
                    Spacer(Modifier.height(8.dp))
                    Text("Bayar (${p.method.label()}): ${p.tendered.toRupiah()}")
                    Text("Kembalian: ${p.changeGiven.toRupiah()}")
                }
            }
        },
        confirmButton = {
            if (detail?.order?.status == OrderStatus.PAID) {
                Button(onClick = onPrintReceipt) { Text("Cetak Struk") }
            } else {
                Button(onClick = onPrintBill) { Text("Cetak Bill") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Tutup") } },
    )
}

private fun statusColor(status: OrderStatus) = when (status) {
    OrderStatus.PAID -> androidx.compose.ui.graphics.Color(0xFF1F6F5C)
    OrderStatus.SAVED -> androidx.compose.ui.graphics.Color(0xFF9A6A00)
    OrderStatus.OPEN -> androidx.compose.ui.graphics.Color(0xFF4B635B)
    OrderStatus.VOID -> androidx.compose.ui.graphics.Color(0xFFBA1A1A)
}

private fun OrderStatus.label(): String = when (this) {
    OrderStatus.OPEN -> "Terbuka"
    OrderStatus.SAVED -> "Disimpan"
    OrderStatus.PAID -> "Lunas"
    OrderStatus.VOID -> "Dibatalkan"
}
