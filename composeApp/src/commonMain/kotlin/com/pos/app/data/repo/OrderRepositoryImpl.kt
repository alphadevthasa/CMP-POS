package com.pos.app.data.repo

import com.pos.app.db.PosDatabase
import com.pos.app.domain.model.Order
import com.pos.app.domain.model.OrderDetail
import com.pos.app.domain.model.OrderLine
import com.pos.app.domain.model.OrderStatus
import com.pos.app.domain.model.Payment
import com.pos.app.domain.model.PaymentMethod
import com.pos.app.domain.repository.OrderRepository
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class OrderRepositoryImpl(private val db: PosDatabase) : OrderRepository {

    override fun nextOrderNo(): String {
        val now = Clock.System.now().toEpochMilliseconds()
        val date = Instant.fromEpochMilliseconds(now)
            .toLocalDateTime(TimeZone.currentSystemDefault()).date
        val dd = date.dayOfMonth.toString().padStart(2, '0')
        val mm = date.monthNumber.toString().padStart(2, '0')
        val prefix = "INV-${date.year}$mm$dd"
        val count = db.posQueries.selectOrders().executeAsList()
            .count { it.order_no.startsWith(prefix) } + 1
        return "$prefix-${count.toString().padStart(4, '0')}"
    }

    override fun createOrder(
        orderNo: String,
        items: List<OrderLine>,
        subtotal: Long,
        tax: Long,
        discount: Long,
        total: Long,
        status: OrderStatus,
        note: String,
        nowEpochMs: Long,
    ): Long {
        db.posQueries.insertOrder(
            order_no = orderNo,
            status = status.name,
            note = note,
            subtotal = subtotal,
            tax = tax,
            discount = discount,
            total = total,
            paid_total = 0L,
            change_due = 0L,
            created_at = nowEpochMs,
            updated_at = nowEpochMs,
        )
        val id = db.posQueries.lastInsertRowId().executeAsOne()
        items.forEach { insertLine(id, it) }
        return id
    }

    private fun insertLine(orderId: Long, line: OrderLine) {
        db.posQueries.insertOrderLine(
            order_id = orderId,
            product_id = line.productId,
            name_snapshot = line.nameSnapshot,
            price_snapshot = line.priceSnapshot,
            qty = line.qty.toLong(),
            line_total = line.lineTotal,
        )
    }

    override fun replaceLines(orderId: Long, items: List<OrderLine>) {
        db.posQueries.deleteLinesByOrder(orderId)
        items.forEach { insertLine(orderId, it) }
    }

    override fun updateStatus(orderId: Long, status: OrderStatus, nowEpochMs: Long) {
        db.posQueries.updateOrderStatus(status.name, nowEpochMs, orderId)
    }

    override fun updateTotals(orderId: Long, subtotal: Long, tax: Long, discount: Long, total: Long, now: Long) {
        db.posQueries.updateOrderTotals(subtotal, tax, discount, total, now, orderId)
    }

    override fun recordPayment(orderId: Long, payment: Payment, paidTotal: Long, changeDue: Long, now: Long) {
        db.posQueries.insertPayment(
            order_id = orderId,
            method = payment.method.name,
            amount = payment.amount,
            tendered = payment.tendered,
            change_given = payment.changeGiven,
            created_at = payment.createdAt,
        )
        db.posQueries.updateOrderPaid(paidTotal, changeDue, now, orderId)
        db.posQueries.updateOrderStatus(OrderStatus.PAID.name, now, orderId)
    }

    override fun orders(): List<Order> =
        db.posQueries.selectOrders().executeAsList().map { it.toDomain() }

    override fun order(id: Long): Order? =
        db.posQueries.selectOrderById(id).executeAsOneOrNull()?.toDomain()

    override fun lines(orderId: Long): List<OrderLine> =
        db.posQueries.selectLinesByOrder(orderId).executeAsList().map {
            OrderLine(
                id = it.id,
                orderId = it.order_id,
                productId = it.product_id,
                nameSnapshot = it.name_snapshot,
                priceSnapshot = it.price_snapshot,
                qty = it.qty.toInt(),
                lineTotal = it.line_total,
            )
        }

    override fun payments(orderId: Long): List<Payment> =
        db.posQueries.selectPaymentsByOrder(orderId).executeAsList().map {
            Payment(
                id = it.id,
                orderId = it.order_id,
                method = PaymentMethod.valueOf(it.method),
                amount = it.amount,
                tendered = it.tendered,
                changeGiven = it.change_given,
                createdAt = it.created_at,
            )
        }

    override fun detail(orderId: Long): OrderDetail? {
        val order = order(orderId) ?: return null
        return OrderDetail(order, lines(orderId), payments(orderId))
    }

    private fun com.pos.app.db.Pos_order.toDomain() = Order(
        id = id,
        orderNo = order_no,
        status = OrderStatus.valueOf(status),
        note = note,
        subtotal = subtotal,
        tax = tax,
        discount = discount,
        total = total,
        paidTotal = paid_total,
        changeDue = change_due,
        createdAt = created_at,
        updatedAt = updated_at,
    )
}
