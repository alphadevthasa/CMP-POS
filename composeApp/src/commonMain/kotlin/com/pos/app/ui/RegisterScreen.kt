package com.pos.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pos.app.domain.model.CartItem
import com.pos.app.domain.model.Product
import com.pos.app.print.RLine
import com.pos.app.util.productInitials
import com.pos.app.util.toRupiah
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun RegisterScreen(scope: CoroutineScope, snackbar: SnackbarHostState) {
    val store = LocalStore.current
    val state by store.state.collectAsState()

    var showPayment by remember { mutableStateOf(false) }
    var printLines by remember { mutableStateOf<List<RLine>?>(null) }
    var printName by remember { mutableStateOf("struk") }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val twoPane = maxWidth >= 700.dp
        if (twoPane) {
            Row(Modifier.fillMaxSize()) {
                ProductPane(Modifier.weight(1f).fillMaxHeight(), scope = scope)
                CartPane(
                    Modifier.width(400.dp).fillMaxHeight(),
                    scope = scope,
                    snackbar = snackbar,
                    onPay = { showPayment = true },
                    onPrint = { lines, name -> printLines = lines; printName = name },
                )
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                ProductPane(Modifier.weight(1f).fillMaxWidth(), scope = scope)
                CartPane(
                    Modifier.fillMaxWidth().heightIn(max = 380.dp),
                    scope = scope,
                    snackbar = snackbar,
                    onPay = { showPayment = true },
                    onPrint = { lines, name -> printLines = lines; printName = name },
                )
            }
        }
    }

    if (showPayment) {
        PaymentDialog(
            total = state.totals.total,
            onDismiss = { showPayment = false },
            onConfirm = { method, tendered ->
                showPayment = false
                val snap = store.pay(method, tendered)
                printLines = store.paidReceiptLines(snap)
                printName = "struk-${snap.orderNo}"
            },
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
private fun ProductPane(modifier: Modifier, scope: CoroutineScope) {
    val store = LocalStore.current
    val state by store.state.collectAsState()

    Column(modifier.padding(12.dp)) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = state.selectedCategoryId == null,
                onClick = { store.selectCategory(null) },
                label = { Text("Semua") },
            )
            state.categories.forEach { category ->
                FilterChip(
                    selected = state.selectedCategoryId == category.id,
                    onClick = { store.selectCategory(category.id) },
                    label = { Text(category.name) },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(state.visibleProducts, key = { it.id }) { product ->
                ProductCard(product) { store.addToCart(product) }
            }
        }
    }
}

@Composable
private fun ProductCard(product: Product, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.height(120.dp).fillMaxWidth(),
    ) {
        Column(
            Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                Modifier
                    .size(42.dp)
                    .background(productAvatarColor(product.name), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    productInitials(product.name),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = AvatarText,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    product.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                )
                Text(
                    product.price.toRupiah(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun CartPane(
    modifier: Modifier,
    scope: CoroutineScope,
    snackbar: SnackbarHostState,
    onPay: () -> Unit,
    onPrint: (List<RLine>, String) -> Unit,
) {
    val store = LocalStore.current
    val state by store.state.collectAsState()
    val totals = state.totals

    Column(
        modifier
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Pesanan", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                state.savedOrderNo ?: "belum disimpan",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))

        if (state.cart.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Keranjang kosong", color = MaterialTheme.colorScheme.secondary)
            }
        } else {
            androidx.compose.foundation.lazy.LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                items(state.cart.size) { index ->
                    CartRow(
                        item = state.cart[index],
                        onInc = { store.increment(state.cart[index].product.id) },
                        onDec = { store.decrement(state.cart[index].product.id) },
                        onRemove = { store.remove(state.cart[index].product.id) },
                    )
                }
            }
        }

        HorizontalDivider()
        Spacer(Modifier.height(8.dp))
        RupiahTextField(
            value = state.discount,
            onValueChange = { store.setDiscount(it) },
            label = "Diskon",
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(12.dp)) {
                TotalRow("Subtotal", totals.subtotal.toRupiah())
                if (totals.discount > 0) TotalRow("Diskon", "-" + totals.discount.toRupiah())
                if (totals.tax > 0) TotalRow("Pajak", totals.tax.toRupiah())
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                TotalRow("Total", totals.total.toRupiah(), emphasized = true)
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    store.saveOrder()
                    scope.launch { snackbar.showSnackbar("Pesanan disimpan") }
                },
                enabled = state.cart.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) { Text("Simpan") }

            OutlinedButton(
                onClick = {
                    store.saveOrder()
                    onPrint(store.currentBillLines(), "bill-${state.savedOrderNo ?: "draft"}")
                },
                enabled = state.cart.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) { Text("Bill") }
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onPay,
            enabled = state.cart.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("Bayar", style = MaterialTheme.typography.titleMedium) }
    }
}

@Composable
private fun CartRow(
    item: CartItem,
    onInc: () -> Unit,
    onDec: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(item.product.name, modifier = Modifier.weight(1f), maxLines = 2)
            Text(item.lineTotal.toRupiah(), fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalButton(
                onClick = onDec,
                modifier = Modifier.size(34.dp),
                contentPadding = PaddingValues(0.dp),
            ) { Text("−", fontWeight = FontWeight.Bold) }
            Text(
                "${item.qty}",
                Modifier.width(34.dp),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
            )
            FilledTonalButton(
                onClick = onInc,
                modifier = Modifier.size(34.dp),
                contentPadding = PaddingValues(0.dp),
            ) { Text("+", fontWeight = FontWeight.Bold) }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onRemove) { Text("Hapus", color = MaterialTheme.colorScheme.error) }
        }
    }
    HorizontalDivider()
}

@Composable
private fun TotalRow(label: String, value: String, emphasized: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Normal)
        Text(
            value,
            fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Normal,
            color = if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}
