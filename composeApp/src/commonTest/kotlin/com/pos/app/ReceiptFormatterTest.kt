package com.pos.app

import com.pos.app.domain.model.CartTotals
import com.pos.app.domain.model.OrderLine
import com.pos.app.domain.model.PaymentMethod
import com.pos.app.domain.model.StoreSettings
import com.pos.app.print.ReceiptFormatter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReceiptFormatterTest {

    private val settings = StoreSettings(storeName = "Toko Uji", printerWidthChars = 32)
    private val lines = listOf(
        OrderLine(0, 1, 1, "Kopi Susu", 18_000, 2, 36_000),
    )
    private val totals = CartTotals(subtotal = 36_000, discount = 0, tax = 3_600, total = 39_600)

    @Test
    fun billRendersHeaderTotalsAndUnpaidMarker() {
        val rendered = ReceiptFormatter.bill(settings, "INV-1", 0L, lines, totals, note = "")
        val text = ReceiptFormatter.renderText(rendered, 32)

        assertTrue(text.contains("Toko Uji"))
        assertTrue(text.contains("BILL / TAGIHAN"))
        assertTrue(text.contains("Kopi Susu"))
        assertTrue(text.contains("Rp 39.600"))
        assertTrue(text.contains("BELUM DIBAYAR"))
    }

    @Test
    fun receiptRendersPaymentAndPaidMarker() {
        val rendered = ReceiptFormatter.receipt(
            settings, "INV-1", 0L, lines, totals,
            method = PaymentMethod.CASH, tendered = 50_000, change = 10_400,
        )
        val text = ReceiptFormatter.renderText(rendered, 32)

        assertTrue(text.contains("RECEIPT / STRUK"))
        assertTrue(text.contains("Tunai"))
        assertTrue(text.contains("Kembali"))
        assertTrue(text.contains("LUNAS / PAID"))
    }

    @Test
    fun escPosStartsWithInitAndEndsWithCut() {
        val rendered = ReceiptFormatter.bill(settings, "INV-1", 0L, lines, totals, note = "")
        val bytes = ReceiptFormatter.renderEscPos(rendered, 32)

        assertEquals(0x1B, bytes[0].toInt())
        assertEquals(0x40, bytes[1].toInt())
        assertEquals(0x1D, bytes[bytes.size - 3].toInt())
        assertEquals(0x56, bytes[bytes.size - 2].toInt())
    }

    @Test
    fun pairLineAlignsRightWithinWidth() {
        val rendered = listOf(com.pos.app.print.RLine.Pair("Total", "Rp 1.000"))
        val text = ReceiptFormatter.renderText(rendered, 20)
        assertEquals(20, text.trimEnd('\n').length)
    }
}
