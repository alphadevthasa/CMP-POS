package com.pos.app.print

import com.pos.app.domain.model.CartTotals
import com.pos.app.domain.model.OrderLine
import com.pos.app.domain.model.PaymentMethod
import com.pos.app.domain.model.StoreSettings
import com.pos.app.util.toDateTimeString
import com.pos.app.util.toRupiah

enum class Align { LEFT, CENTER, RIGHT }

/** A renderer-agnostic line of a printed document. */
sealed interface RLine {
    data class T(
        val text: String,
        val align: Align = Align.LEFT,
        val bold: Boolean = false,
        val big: Boolean = false,
    ) : RLine

    data class Divider(val ch: Char = '-') : RLine
    data class Pair(val left: String, val right: String) : RLine
    data object Feed : RLine
    data object Cut : RLine
}

/** A plain document the platform can turn into a PDF. */
data class PdfDoc(val fileName: String, val text: String)

object ReceiptFormatter {

    fun bill(
        settings: StoreSettings,
        orderNo: String,
        createdAt: Long,
        lines: List<OrderLine>,
        totals: CartTotals,
        note: String,
    ): List<RLine> = build(settings, orderNo, createdAt, lines, totals, note, payment = null)

    fun receipt(
        settings: StoreSettings,
        orderNo: String,
        createdAt: Long,
        lines: List<OrderLine>,
        totals: CartTotals,
        method: PaymentMethod,
        tendered: Long,
        change: Long,
    ): List<RLine> = build(
        settings, orderNo, createdAt, lines, totals, note = "",
        payment = ReceiptPayment(method, tendered, change),
    )

    private data class ReceiptPayment(val method: PaymentMethod, val tendered: Long, val change: Long)

    private fun build(
        settings: StoreSettings,
        orderNo: String,
        createdAt: Long,
        lines: List<OrderLine>,
        totals: CartTotals,
        note: String,
        payment: ReceiptPayment?,
    ): List<RLine> {
        val out = mutableListOf<RLine>()
        out += RLine.T(settings.storeName, Align.CENTER, bold = true, big = true)
        if (settings.address.isNotBlank()) out += RLine.T(settings.address, Align.CENTER)
        if (settings.phone.isNotBlank()) out += RLine.T(settings.phone, Align.CENTER)
        out += RLine.Divider('=')

        val title = if (payment == null) "BILL / TAGIHAN" else "RECEIPT / STRUK"
        out += RLine.T(title, Align.CENTER, bold = true)
        out += RLine.T("No   : $orderNo")
        out += RLine.T("Tgl  : ${createdAt.toDateTimeString()}")
        out += RLine.Divider()

        lines.forEach { line ->
            out += RLine.T(line.nameSnapshot)
            out += RLine.Pair("  ${line.qty} x ${line.priceSnapshot.toRupiah()}", line.lineTotal.toRupiah())
        }

        out += RLine.Divider()
        out += RLine.Pair("Subtotal", totals.subtotal.toRupiah())
        if (totals.discount > 0) out += RLine.Pair("Diskon", "-" + totals.discount.toRupiah())
        if (totals.tax > 0) out += RLine.Pair("Pajak", totals.tax.toRupiah())
        out += RLine.Pair("TOTAL", totals.total.toRupiah())

        if (note.isNotBlank()) {
            out += RLine.Divider()
            out += RLine.T("Catatan: $note")
        }

        if (payment != null) {
            out += RLine.Divider()
            out += RLine.Pair("Bayar (${payment.method.label()})", payment.tendered.toRupiah())
            out += RLine.Pair("Kembali", payment.change.toRupiah())
        }

        out += RLine.Divider('=')
        out += RLine.T(if (payment == null) "BELUM DIBAYAR" else "LUNAS / PAID", Align.CENTER, bold = true)
        out += RLine.Feed
        out += RLine.T(settings.footerText, Align.CENTER)
        out += RLine.Feed
        out += RLine.Cut
        return out
    }

    fun renderText(lines: List<RLine>, width: Int): String = buildString {
        lines.forEach { line ->
            append(
                when (line) {
                    is RLine.T -> when (line.align) {
                        Align.LEFT -> line.text
                        Align.CENTER -> center(line.text, width)
                        Align.RIGHT -> right(line.text, width)
                    }
                    is RLine.Divider -> line.ch.toString().repeat(width)
                    is RLine.Pair -> pair(line.left, line.right, width)
                    RLine.Feed -> ""
                    RLine.Cut -> ""
                },
            )
            append('\n')
        }
    }

    fun renderEscPos(lines: List<RLine>, width: Int): ByteArray {
        val buf = ByteBuf()
        buf.b(0x1B, 0x40) // init
        lines.forEach { line ->
            when (line) {
                is RLine.T -> {
                    buf.b(0x1B, 0x61, line.align.code())
                    if (line.bold) buf.b(0x1B, 0x45, 0x01)
                    buf.b(0x1D, 0x21, if (line.big) 0x11 else 0x00)
                    buf.s(line.text)
                    buf.b(0x1D, 0x21, 0x00)
                    if (line.bold) buf.b(0x1B, 0x45, 0x00)
                    buf.nl()
                }
                is RLine.Divider -> {
                    buf.b(0x1B, 0x61, 0x00)
                    buf.s(line.ch.toString().repeat(width)); buf.nl()
                }
                is RLine.Pair -> {
                    buf.b(0x1B, 0x61, 0x00)
                    buf.s(pair(line.left, line.right, width)); buf.nl()
                }
                RLine.Feed -> buf.nl()
                RLine.Cut -> buf.b(0x1D, 0x56, 0x00)
            }
        }
        return buf.toByteArray()
    }

    private fun Align.code(): Int = when (this) {
        Align.LEFT -> 0x00
        Align.CENTER -> 0x01
        Align.RIGHT -> 0x02
    }

    private fun center(s: String, width: Int): String {
        if (s.length >= width) return s
        return " ".repeat((width - s.length) / 2) + s
    }

    private fun right(s: String, width: Int): String {
        if (s.length >= width) return s
        return " ".repeat(width - s.length) + s
    }

    private fun pair(left: String, right: String, width: Int): String {
        val gap = width - left.length - right.length
        return if (gap >= 1) left + " ".repeat(gap) + right else "$left $right"
    }
}

fun PaymentMethod.label(): String = when (this) {
    PaymentMethod.CASH -> "Tunai"
    PaymentMethod.CARD -> "Kartu"
    PaymentMethod.QRIS -> "QRIS"
}

private class ByteBuf(initial: Int = 512) {
    private val out = ArrayList<Byte>(initial)
    fun b(vararg v: Int) { v.forEach { out.add(it.toByte()) } }
    fun s(text: String) { text.encodeToByteArray().forEach { out.add(it) } }
    fun nl() { out.add(0x0A) }
    fun toByteArray(): ByteArray = ByteArray(out.size) { out[it] }
}
