package com.pos.app.domain.repository

import com.pos.app.domain.model.Category
import com.pos.app.domain.model.Order
import com.pos.app.domain.model.OrderDetail
import com.pos.app.domain.model.OrderLine
import com.pos.app.domain.model.OrderStatus
import com.pos.app.domain.model.Payment
import com.pos.app.domain.model.Product
import com.pos.app.domain.model.StoreSettings

interface ProductRepository {
    fun categories(): List<Category>
    fun products(): List<Product>
    fun seedIfEmpty()
    fun addProduct(name: String, sku: String, price: Long, categoryId: Long): Long
    fun addCategory(name: String): Long
}

interface OrderRepository {
    fun nextOrderNo(): String
    fun createOrder(
        orderNo: String,
        items: List<OrderLine>,
        subtotal: Long,
        tax: Long,
        discount: Long,
        total: Long,
        status: OrderStatus,
        note: String,
        nowEpochMs: Long,
    ): Long

    fun replaceLines(orderId: Long, items: List<OrderLine>)
    fun updateStatus(orderId: Long, status: OrderStatus, nowEpochMs: Long)
    fun updateTotals(orderId: Long, subtotal: Long, tax: Long, discount: Long, total: Long, now: Long)
    fun recordPayment(orderId: Long, payment: Payment, paidTotal: Long, changeDue: Long, now: Long)
    fun orders(): List<Order>
    fun order(id: Long): Order?
    fun lines(orderId: Long): List<OrderLine>
    fun payments(orderId: Long): List<Payment>
    fun detail(orderId: Long): OrderDetail?
}

interface SettingsRepository {
    fun load(): StoreSettings
    fun save(settings: StoreSettings)
}
