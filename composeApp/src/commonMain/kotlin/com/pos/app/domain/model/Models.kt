package com.pos.app.domain.model

data class Category(
    val id: Long,
    val name: String,
)

data class Product(
    val id: Long,
    val name: String,
    val sku: String,
    val price: Long,
    val categoryId: Long,
    val active: Boolean = true,
)

data class CartItem(
    val product: Product,
    val qty: Int,
) {
    val lineTotal: Long get() = product.price * qty
}

enum class OrderStatus { OPEN, SAVED, PAID, VOID }

enum class PaymentMethod { CASH, CARD, QRIS }

data class Order(
    val id: Long,
    val orderNo: String,
    val status: OrderStatus,
    val note: String,
    val subtotal: Long,
    val tax: Long,
    val discount: Long,
    val total: Long,
    val paidTotal: Long,
    val changeDue: Long,
    val createdAt: Long,
    val updatedAt: Long,
)

data class OrderLine(
    val id: Long,
    val orderId: Long,
    val productId: Long,
    val nameSnapshot: String,
    val priceSnapshot: Long,
    val qty: Int,
    val lineTotal: Long,
)

data class Payment(
    val id: Long,
    val orderId: Long,
    val method: PaymentMethod,
    val amount: Long,
    val tendered: Long,
    val changeGiven: Long,
    val createdAt: Long,
)

/** Immutable snapshot of an order useful for printing / display. */
data class OrderDetail(
    val order: Order,
    val lines: List<OrderLine>,
    val payments: List<Payment>,
)

data class CartTotals(
    val subtotal: Long,
    val discount: Long,
    val tax: Long,
    val total: Long,
)

/** Pure calculation, unit-tested. taxPercent is e.g. 11.0 for 11%. */
fun computeTotals(items: List<CartItem>, discount: Long, taxPercent: Double): CartTotals {
    val subtotal = items.sumOf { it.lineTotal }
    val taxable = (subtotal - discount).coerceAtLeast(0L)
    val tax = (taxable * taxPercent / 100.0).toLong()
    return CartTotals(
        subtotal = subtotal,
        discount = discount,
        tax = tax,
        total = taxable + tax,
    )
}
