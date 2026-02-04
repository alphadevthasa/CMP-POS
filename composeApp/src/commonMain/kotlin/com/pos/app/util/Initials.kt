package com.pos.app.util

/** "Es Jeruk" -> "EJ", "Americano" -> "AM", "Teh Botol" -> "TB". */
fun productInitials(name: String, max: Int = 2): String {
    val words = name
        .split(' ', '\t', '-', '/', ',')
        .map { word -> word.filter { it.isLetterOrDigit() } }
        .filter { it.isNotBlank() }

    val chars: List<Char> = if (words.size >= 2) {
        words.take(max).map { it.first() }
    } else {
        (words.firstOrNull() ?: "").take(max).toList()
    }

    return chars.joinToString("").uppercase().ifBlank { "?" }
}
