package com.pos.app.ui

import com.pos.app.data.AppContainer
import com.pos.app.domain.model.CartItem
import com.pos.app.domain.model.CartTotals
import com.pos.app.domain.model.Category
import com.pos.app.domain.model.Order
import com.pos.app.domain.model.OrderLine
import com.pos.app.domain.model.OrderStatus
import com.pos.app.domain.model.Payment
import com.pos.app.domain.model.PaymentMethod
import com.pos.app.domain.model.Product
import com.pos.app.domain.model.StoreSettings
import com.pos.app.domain.model.computeTotals
import com.pos.app.print.RLine
import com.pos.app.print.ReceiptFormatter
import com.pos.app.util.nowEpochMs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UiState(
    val products: List<Product> = emptyList(),
    val categories: List<Category> = emptyList(),
    val orders: List<Order> = emptyList(),
    val settings: StoreSettings = StoreSettings(),
    val cart: List<CartItem> = emptyList(),
    val selectedCategoryId: Long? = null,
    val discount: Long = 0,
    val savedOrderId: Long? = null,
    val savedOrderNo: String? = null,
) {
    val totals: CartTotals get() = computeTotals(cart, discount, settings.taxPercent)

    val visibleProducts: List<Product>
        get() = if (selectedCategoryId == null) products
        else products.filter { it.categoryId == selectedCategoryId }
}

/** A snapshot of what was paid, captured before the cart is cleared. */
data class PaidSnapshot(
    val orderNo: String,
    val createdAt: Long,
    val lines: List<OrderLine>,
    val totals: CartTotals,
    val method: PaymentMethod,
    val tendered: Long,
    val change: Long,
)

class PosStore(private val container: AppContainer) {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        _state.value = _state.value.copy(
            products = container.products.products(),
            categories = container.products.categories(),
            orders = container.orders.orders(),
            settings = container.settings.load(),
        )
    }

    fun selectCategory(id: Long?) {
        _state.value = _state.value.copy(selectedCategoryId = id)
    }

    fun addToCart(product: Product) {
        val cart = _state.value.cart.toMutableList()
        val index = cart.indexOfFirst { it.product.id == product.id }
        if (index >= 0) cart[index] = cart[index].copy(qty = cart[index].qty + 1)
        else cart += CartItem(product, 1)
        _state.value = _state.value.copy(cart = cart)
    }

    fun increment(productId: Long) {
        val cart = _state.value.cart.map {
            if (it.product.id == productId) it.copy(qty = it.qty + 1) else it
        }
        _state.value = _state.value.copy(cart = cart)
    }

    fun decrement(productId: Long) {
        val cart = _state.value.cart.mapNotNull {
            when {
                it.product.id != productId -> it
                it.qty > 1 -> it.copy(qty = it.qty - 1)
                else -> null
            }
        }
        _state.value = _state.value.copy(cart = cart)
    }

    fun remove(productId: Long) {
        _state.value = _state.value.copy(cart = _state.value.cart.filterNot { it.product.id == productId })
    }

    fun clearCart() {
        _state.value = _state.value.copy(cart = emptyList(), discount = 0, savedOrderId = null, savedOrderNo = null)
    }

    fun setDiscount(value: Long) {
        _state.value = _state.value.copy(discount = value.coerceAtLeast(0))
    }

    /** Persist the current cart as a SAVED order (or update the one already saved). */
    fun saveOrder(note: String = "") {
        val s = _state.value
        if (s.cart.isEmpty()) return
        val now = nowEpochMs()
        val id = s.savedOrderId
        if (id == null) {
            val orderNo = container.orders.nextOrderNo()
            val newId = container.orders.createOrder(
                orderNo = orderNo,
                items = cartLines(0),
                subtotal = s.totals.subtotal,
                tax = s.totals.tax,
                discount = s.totals.discount,
                total = s.totals.total,
                status = OrderStatus.SAVED,
                note = note,
                nowEpochMs = now,
            )
            _state.value = _state.value.copy(savedOrderId = newId, savedOrderNo = orderNo)
        } else {
            container.orders.replaceLines(id, cartLines(id))
            container.orders.updateTotals(id, s.totals.subtotal, s.totals.tax, s.totals.discount, s.totals.total, now)
            container.orders.updateStatus(id, OrderStatus.SAVED, now)
        }
        refreshOrders()
    }

    /** Charge the current cart and return the paid snapshot for printing. */
    fun pay(method: PaymentMethod, tendered: Long): PaidSnapshot {
        if (_state.value.savedOrderId == null) saveOrder()
        val s = _state.value
        val orderId = s.savedOrderId ?: return errorSnapshot(method, tendered)
        val now = nowEpochMs()
        val total = s.totals.total
        val effectiveTendered = if (method == PaymentMethod.CASH) tendered.coerceAtLeast(total) else total
        val changeWh = (effectiveTendered - total).coerceAtLeast(0)

        container.orders.recordPayment(
            orderId = orderId,
            payment = Payment(
                id = 0,
                orderId = orderId,
                method = method,
                amount = total,
                tendered = effectiveTendered,
                changeGiven = changeWh,
                createdAt = now,
            ),
            paidTotal = total,
            changeDue = changeWh,
            now = now,
        )

        val snapshot = PaidSnapshot(
            orderNo = s.savedOrderNo ?: orderId.toString(),
            createdAt = now,
            lines = cartLines(orderId),
            totals = s.totals,
            method = method,
            tendered = effectiveTendered,
            change = changeWh,
        )
        clearCart()
        refreshOrders()
        return snapshot
    }

    private fun errorSnapshot(method: PaymentMethod, tendered: Long) = PaidSnapshot(
        orderNo = "-", createdAt = nowEpochMs(), lines = emptyList(),
        totals = CartTotals(0, 0, 0, 0), method = method, tendered = tendered, change = 0,
    )

    fun refreshOrders() {
        _state.value = _state.value.copy(orders = container.orders.orders())
    }

    fun saveSettings(settings: StoreSettings) {
        container.settings.save(settings)
        _state.value = _state.value.copy(settings = settings)
    }

    fun orderDetail(orderId: Long) = container.orders.detail(orderId)

    private fun cartLines(orderId: Long): List<OrderLine> = _state.value.cart.map {
        OrderLine(
            id = 0,
            orderId = orderId,
            productId = it.product.id,
            nameSnapshot = it.product.name,
            priceSnapshot = it.product.price,
            qty = it.qty,
            lineTotal = it.lineTotal,
        )
    }

    // ---- printing ----

    fun currentBillLines(note: String = ""): List<RLine> {
        val s = _state.value
        return ReceiptFormatter.bill(
            settings = s.settings,
            orderNo = s.savedOrderNo ?: "DRAFT",
            createdAt = nowEpochMs(),
            lines = cartLines(0),
            totals = s.totals,
            note = note,
        )
    }

    fun paidReceiptLines(snapshot: PaidSnapshot): List<RLine> = ReceiptFormatter.receipt(
        settings = _state.value.settings,
        orderNo = snapshot.orderNo,
        createdAt = snapshot.createdAt,
        lines = snapshot.lines,
        totals = snapshot.totals,
        method = snapshot.method,
        tendered = snapshot.tendered,
        change = snapshot.change,
    )

    fun billLinesForOrder(orderId: Long): List<RLine>? {
        val detail = orderDetail(orderId) ?: return null
        return ReceiptFormatter.bill(
            settings = _state.value.settings,
            orderNo = detail.order.orderNo,
            createdAt = detail.order.createdAt,
            lines = detail.lines,
            totals = CartTotals(
                subtotal = detail.order.subtotal,
                discount = detail.order.discount,
                tax = detail.order.tax,
                total = detail.order.total,
            ),
            note = detail.order.note,
        )
    }

    fun receiptLinesForOrder(orderId: Long): List<RLine>? {
        val detail = orderDetail(orderId) ?: return null
        val p = detail.payments.lastOrNull() ?: return billLinesForOrder(orderId)
        return ReceiptFormatter.receipt(
            settings = _state.value.settings,
            orderNo = detail.order.orderNo,
            createdAt = detail.order.createdAt,
            lines = detail.lines,
            totals = CartTotals(
                subtotal = detail.order.subtotal,
                discount = detail.order.discount,
                tax = detail.order.tax,
                total = detail.order.total,
            ),
            method = p.method,
            tendered = p.tendered,
            change = p.changeGiven,
        )
    }

    fun printer() = container.printer
    fun settings() = _state.value.settings
}
