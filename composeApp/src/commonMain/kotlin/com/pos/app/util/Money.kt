package com.pos.app.util

fun Long.grouped(): String {
    val negative = this < 0
    val digits = (if (negative) -this else this).toString()
    val grouped = digits.reversed().chunked(3).joinToString(".").reversed()
    return if (negative) "-$grouped" else grouped
}

fun Long.toRupiah(): String = "Rp " + grouped()
