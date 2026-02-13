package com.pos.app.util

fun Long.grouped(): String {
    val negative = this < 0
    val digits = (if (negative) -this else this).toString()
    val grouped = digits.reversed().chunked(3).joinToString(".").reversed()
    return if (negative) "-$grouped" else grouped
}

fun Long.toRupiah(): String = "Rp " + grouped()

/**
 * Strip grouping/decimal decoration; parse the integer rupiah amount.
 * The part after "," (decimal) is dropped first so a ",00" suffix can never inflate the value.
 */
fun parseRupiahInput(raw: String): Long =
    raw.substringBefore(',')
        .filter { it.isDigit() }
        .toLongOrNull() ?: 0L
