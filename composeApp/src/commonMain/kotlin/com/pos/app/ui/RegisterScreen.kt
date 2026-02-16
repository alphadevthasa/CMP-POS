package com.pos.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pos.app.domain.model.CartItem
import com.pos.app.domain.model.Product
import com.pos.app.print.RLine
import com.pos.app.util.productInitials
import com.pos.app.util.toRupiah
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

private val StatusOrange = Color(0xFFB45309)

@Composable
fun RegisterScreen(scope: CoroutineScope, snackbar: SnackbarHostState) {
    val store = LocalStore.current
    val state by store.state.collectAsState()

    var showPayment by remember { mutableStateOf(false) }
    var printLines by remember { mutableStateOf<List<RLine>?>(null) }
    var printName by remember { mutableStateOf("struk") }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val twoPane = maxWidth >= 700.dp
        val cart: @Composable (Modifier) -> Unit = { mod ->
            CartPane(
                mod,
                scope = scope,
                snackbar = snackbar,
                onPay = { showPayment = true },
                onPrint = { lines, name -> printLines = lines; printName = name },
            )
        }
        if (twoPane) {
            Row(Modifier.fillMaxSize()) {
                ProductPane(Modifier.weight(1f).fillMaxHeight())
                cart(Modifier.width(420.dp).fillMaxHeight())
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                ProductPane(Modifier.weight(1f).fillMaxWidth())
                cart(Modifier.fillMaxWidth().heightIn(max = 380.dp))
            }
        }
    }

    if (showPayment) {
        PaymentDialog(
            itemCount = state.cart.sumOf { it.qty },
            itemSummary = state.cart.joinToString(", ") { it.product.name },
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
private fun ProductPane(modifier: Modifier) {
    val store = LocalStore.current
    val state by store.state.collectAsState()
    var query by remember { mutableStateOf("") }

    val products = state.visibleProducts.filter {
        query.isBlank() || it.name.contains(query, ignoreCase = true)
    }

    Column(modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    "MEJA KASIR",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Pilih menu favorit",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Ketuk produk untuk menambahkan ke pesanan.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Text(
                    "${state.products.size} produk tersedia",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CategoryChip("Semua", state.selectedCategoryId == null) { store.selectCategory(null) }
                state.categories.forEach { category ->
                    CategoryChip(category.name, state.selectedCategoryId == category.id) {
                        store.selectCategory(category.id)
                    }
                }
            }
            Spacer(Modifier.width(16.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Cari menu...") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.width(260.dp),
            )
        }

        Spacer(Modifier.height(16.dp))

        LazyVerticalGrid(
            columns = GridCells.Adaptive(155.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(products, key = { it.id }) { product ->
                val category = state.categories.firstOrNull { it.id == product.categoryId }?.name ?: ""
                ProductCard(product, category) { store.addToCart(product) }
            }
        }
    }
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            containerColor = MaterialTheme.colorScheme.surface,
            labelColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}

@Composable
private fun ProductCard(product: Product, category: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth().height(236.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            val base = productAvatarColor(product.name)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(116.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(base.copy(alpha = 0.65f), base.copy(alpha = 0.3f)),
                        ),
                    ),
            ) {
                val image = productImage(product.name)
                if (image != null) {
                    Image(
                        painter = painterResource(image),
                        contentDescription = product.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .offset(x = 28.dp, y = -14.dp)
                            .size(70.dp)
                            .background(Color.White.copy(alpha = 0.35f), CircleShape),
                    )
                    Text(
                        productInitials(product.name),
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = AvatarText,
                        letterSpacing = 1.sp,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                category.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                product.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    product.price.toRupiah(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Box(
                    Modifier
                        .size(32.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .clickable(onClick = onClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("+", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
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
    val itemCount = state.cart.sumOf { it.qty }

    Column(
        modifier
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Pesanan saat ini", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Text(
                    "$itemCount item",
                    Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "Siap untuk transaksi",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                state.savedOrderNo?.let { "Tersimpan · $it" } ?: "Belum disimpan",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = if (state.savedOrderNo == null) StatusOrange else MaterialTheme.colorScheme.primary,
            )
        }

        Spacer(Modifier.height(12.dp))

        if (state.cart.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Keranjang kosong", color = MaterialTheme.colorScheme.onSurfaceVariant)
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

        Text(
            "Diskon pesanan",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        RupiahTextField(
            value = state.discount,
            onValueChange = { store.setDiscount(it) },
            label = "Diskon",
            placeholder = "Masukkan nominal diskon",
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))

        Surface(
            color = MaterialTheme.colorScheme.background,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryRow("Subtotal", totals.subtotal.toRupiah())
                SummaryRow("Diskon", "– " + totals.discount.toRupiah())
                HorizontalDivider()
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Total bayar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        totals.total.toRupiah(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = {
                    store.saveOrder()
                    store.clearCart()
                    scope.launch { snackbar.showSnackbar("Pesanan tersimpan") }
                },
                enabled = state.cart.isNotEmpty(),
                modifier = Modifier.weight(1f).height(46.dp),
            ) { Text("Simpan") }
            OutlinedButton(
                onClick = {
                    onPrint(store.currentBillLines(), "bill-${state.savedOrderNo ?: "draft"}")
                },
                enabled = state.cart.isNotEmpty(),
                modifier = Modifier.weight(1f).height(46.dp),
            ) { Text("Lihat bill") }
        }

        Spacer(Modifier.height(10.dp))

        Button(
            onClick = onPay,
            enabled = state.cart.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text("Bayar sekarang", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun CartRow(
    item: CartItem,
    onInc: () -> Unit,
    onDec: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(productAvatarColor(item.product.name)),
                    contentAlignment = Alignment.Center,
                ) {
                    val thumb = productImage(item.product.name)
                    if (thumb != null) {
                        Image(
                            painter = painterResource(thumb),
                            contentDescription = item.product.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Text(
                            productInitials(item.product.name),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = AvatarText,
                        )
                    }
                }
                Column {
                    Text(item.product.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "${item.product.price.toRupiah()} / item",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(item.lineTotal.toRupiah(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepButton("−", onDec)
            Text(
                "${item.qty}",
                Modifier.width(34.dp),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
            )
            StepButton("+", onInc)
            Spacer(Modifier.weight(1f))
            Text(
                "Hapus",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = StatusOrange,
                modifier = Modifier.clickable(onClick = onRemove),
            )
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider()
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(32.dp)
            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}
