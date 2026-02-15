package com.pos.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.pos.app.domain.model.PaymentMethod
import com.pos.app.print.label
import com.pos.app.util.toRupiah

private val PayGreen = Color(0xFF274A3E)
private val PayGreenLight = Color(0xFFDCE8E1)
private val PayAmber = Color(0xFFB45309)
private val PayAmberLight = Color(0xFFFBEFE2)
private val PayMint = Color(0xFFDCE8E1)

@Composable
fun PaymentDialog(
    itemCount: Int,
    itemSummary: String,
    total: Long,
    onDismiss: () -> Unit,
    onConfirm: (PaymentMethod, Long) -> Unit,
) {
    var method by remember { mutableStateOf(PaymentMethod.CASH) }
    var tendered by remember { mutableStateOf(0L) }
    val change = (tendered - total).coerceAtLeast(0)
    val canPay = method != PaymentMethod.CASH || tendered >= total

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = Color.White) {
            Column(Modifier.widthIn(max = 500.dp).padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "METODE PEMBAYARAN",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text("Pembayaran", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    }
                    Box(Modifier.size(10.dp).background(Color(0xFF16A34A), CircleShape))
                }

                Spacer(Modifier.height(16.dp))
                MethodTabs(method) { method = it }
                Spacer(Modifier.height(16.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("$itemCount produk", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(itemSummary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Total tagihan", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(total.toRupiah(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))

                when (method) {
                    PaymentMethod.CASH -> CashPanel(total, tendered, change) { tendered = it }
                    PaymentMethod.CARD -> CardPanel()
                    PaymentMethod.QRIS -> QrisPanel(total)
                }

                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = { onConfirm(method, if (method == PaymentMethod.CASH) tendered else total) },
                    enabled = canPay,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PayGreen, contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text(
                        when (method) {
                            PaymentMethod.CASH -> "Terima pembayaran"
                            PaymentMethod.CARD -> "Konfirmasi pembayaran"
                            PaymentMethod.QRIS -> "Cek pembayaran"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Batal", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CheckMark()
                    Spacer(Modifier.width(6.dp))
                    Text(
                        when (method) {
                            PaymentMethod.CASH -> "Periksa nominal sebelum menyelesaikan transaksi"
                            PaymentMethod.CARD -> "Tidak perlu memasukkan data kartu di aplikasi"
                            PaymentMethod.QRIS -> "Jangan tandai lunas sebelum status berhasil"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun MethodTabs(selected: PaymentMethod, onSelect: (PaymentMethod) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(12.dp))
            .padding(4.dp),
    ) {
        PaymentMethod.entries.forEach { m ->
            val active = m == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (active) PayGreen else Color.Transparent)
                    .clickable { onSelect(m) }
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    m.label(),
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CashPanel(total: Long, tendered: Long, change: Long, onChange: (Long) -> Unit) {
    Text("Terima uang tunai", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Text(
        "Masukkan jumlah uang dari pelanggan. Kembalian dihitung otomatis.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
    RupiahTextField(value = tendered, onValueChange = onChange, label = "Uang diterima", modifier = Modifier.fillMaxWidth())

    Spacer(Modifier.height(12.dp))
    Text("Nominal cepat", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        QuickChip("Uang pas", selected = tendered == total) { onChange(total) }
        QuickChip("20rb", selected = tendered == 20_000L) { onChange(20_000) }
        QuickChip("50rb", selected = tendered == 50_000L) { onChange(50_000) }
        QuickChip("100rb", selected = tendered == 100_000L) { onChange(100_000) }
    }

    Spacer(Modifier.height(16.dp))
    Surface(color = Color(0xFFF1F4F2), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Kembalian pelanggan", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(change.toRupiah(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            Box(
                Modifier.size(40.dp).background(PayMint, CircleShape),
                contentAlignment = Alignment.Center,
            ) { ArrowUpRight() }
        }
    }
}

@Composable
private fun QuickChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) PayMint else Color.White,
        border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            fontWeight = FontWeight.SemiBold,
            color = if (selected) PayGreen else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun CardPanel() {
    Text("Bayar dengan kartu", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Text(
        "Proses pembayaran di mesin EDC, lalu cocokkan status transaksi.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))

    Box(
        Modifier
            .fillMaxWidth()
            .height(190.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(PayGreen, Color(0xFF3C6353)))),
    ) {
        Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text("POS Multiplatform", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.width(42.dp).height(30.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFFD8B24E)))
                Text("•••• •••• •••• ••••", color = Color.White.copy(alpha = 0.9f), fontSize = 18.sp)
            }
        }
    }

    Spacer(Modifier.height(16.dp))
    StepRow(1, "Minta pelanggan menempelkan atau memasukkan kartu di mesin EDC.")
    Spacer(Modifier.height(10.dp))
    StepRow(2, "Pastikan mesin menampilkan transaksi berhasil.")
    Spacer(Modifier.height(12.dp))
    StatusPill("Menunggu konfirmasi dari mesin EDC", dotColor = PayAmber, bg = PayAmberLight)
}

@Composable
private fun QrisPanel(total: Long) {
    Text("Pindai untuk membayar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Text(
        "Tunjukkan kode QR kepada pelanggan dan tunggu pembayaran berhasil.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(120.dp).clip(RoundedCornerShape(12.dp)).background(Color.White)) {
            QrisPattern(Modifier.fillMaxSize().padding(8.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("QRIS", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "Pelanggan dapat membayar melalui aplikasi pembayaran pilihan mereka.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Ilustrasi · bukan kode aktif",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }

    Spacer(Modifier.height(16.dp))
    InfoRow("Nominal QRIS", total.toRupiah())
    Spacer(Modifier.height(8.dp))
    InfoRow("Status", "Menunggu pembayaran")
    Spacer(Modifier.height(12.dp))
    StatusPill("Pesanan selesai setelah pembayaran terkonfirmasi.", dotColor = Color(0xFF16A34A), bg = PayMint)
}

@Composable
private fun StepRow(n: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(24.dp).background(PayMint, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text("$n", fontWeight = FontWeight.Bold, color = PayGreen, fontSize = 13.sp) }
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StatusPill(text: String, dotColor: Color, bg: Color) {
    Surface(shape = RoundedCornerShape(10.dp), color = bg, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(8.dp).background(dotColor, CircleShape))
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun CheckMark() {
    Canvas(Modifier.size(14.dp)) {
        val w = size.width
        val h = size.height
        drawLine(
            color = PayGreen,
            start = Offset(w * 0.15f, h * 0.52f),
            end = Offset(w * 0.42f, h * 0.78f),
            strokeWidth = 2.5f,
        )
        drawLine(
            color = PayGreen,
            start = Offset(w * 0.42f, h * 0.78f),
            end = Offset(w * 0.86f, h * 0.24f),
            strokeWidth = 2.5f,
        )
    }
}

@Composable
private fun ArrowUpRight() {
    Canvas(Modifier.size(18.dp)) {
        val w = size.width
        val h = size.height
        drawLine(PayGreen, Offset(w * 0.25f, h * 0.75f), Offset(w * 0.75f, h * 0.25f), strokeWidth = 2.5f)
        drawLine(PayGreen, Offset(w * 0.75f, h * 0.25f), Offset(w * 0.45f, h * 0.25f), strokeWidth = 2.5f)
        drawLine(PayGreen, Offset(w * 0.75f, h * 0.25f), Offset(w * 0.75f, h * 0.55f), strokeWidth = 2.5f)
    }
}

/** Stylized QR illustration (decorative, not a scannable code). */
@Composable
private fun QrisPattern(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val n = 21
        val cell = size.width / n
        fun dot(col: Int, row: Int) {
            drawRect(PayGreen, topLeft = Offset(col * cell, row * cell), size = Size(cell * 0.9f, cell * 0.9f))
        }
        fun finder(c0: Int, r0: Int) {
            for (r in 0 until 7) for (c in 0 until 7) {
                val border = r == 0 || r == 6 || c == 0 || c == 6
                val inner = r in 2..4 && c in 2..4
                if (border || inner) dot(c0 + c, r0 + r)
            }
        }
        finder(0, 0)
        finder(14, 0)
        finder(0, 14)
        for (r in 0 until n) for (c in 0 until n) {
            val inFinder = (r < 8 && c < 8) || (r < 8 && c > 12) || (r > 12 && c < 8)
            if (!inFinder && ((r * 7 + c * 13 + r * c) % 3 == 0)) dot(c, r)
        }
    }
}
