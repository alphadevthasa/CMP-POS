package com.pos.app

import com.pos.app.domain.model.CartItem
import com.pos.app.domain.model.Product
import com.pos.app.domain.model.computeTotals
import kotlin.test.Test
import kotlin.test.assertEquals

class CartTest {

    private fun product(id: Long, price: Long) = Product(id, "P$id", "SKU$id", price, 1, true)

    @Test
    fun totalsWithTaxAndDiscount() {
        val items = listOf(CartItem(product(1, 10_000), 2), CartItem(product(2, 5_000), 1))
        val totals = computeTotals(items, discount = 5_000, taxPercent = 10.0)

        assertEquals(25_000, totals.subtotal)
        assertEquals(5_000, totals.discount)
        assertEquals(2_000, totals.tax)
        assertEquals(22_000, totals.total)
    }

    @Test
    fun noDiscountNoTax() {
        val items = listOf(CartItem(product(1, 3_000), 3))
        val totals = computeTotals(items, discount = 0, taxPercent = 0.0)

        assertEquals(9_000, totals.subtotal)
        assertEquals(9_000, totals.total)
    }

    @Test
    fun discountNeverPushesTotalNegative() {
        val items = listOf(CartItem(product(1, 1_000), 1))
        val totals = computeTotals(items, discount = 5_000, taxPercent = 10.0)

        assertEquals(0, totals.total)
    }
}
