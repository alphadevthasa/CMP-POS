package com.pos.app.util

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

fun nowEpochMs(): Long = Clock.System.now().toEpochMilliseconds()

fun Long.toDateTimeString(): String {
    val dt = Instant.fromEpochMilliseconds(this)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    fun p(n: Int) = n.toString().padStart(2, '0')
    return "${p(dt.dayOfMonth)}/${p(dt.monthNumber)}/${dt.year} ${p(dt.hour)}:${p(dt.minute)}"
}

fun Long.toDateString(): String {
    val d = Instant.fromEpochMilliseconds(this)
        .toLocalDateTime(TimeZone.currentSystemDefault()).date
    fun p(n: Int) = n.toString().padStart(2, '0')
    return "${p(d.dayOfMonth)}/${p(d.monthNumber)}/${d.year}"
}

fun todayLongLabel(now: Long = nowEpochMs()): String {
    val d = Instant.fromEpochMilliseconds(now)
        .toLocalDateTime(TimeZone.currentSystemDefault()).date
    val dow = when (d.dayOfWeek) {
        kotlinx.datetime.DayOfWeek.MONDAY -> "Senin"
        kotlinx.datetime.DayOfWeek.TUESDAY -> "Selasa"
        kotlinx.datetime.DayOfWeek.WEDNESDAY -> "Rabu"
        kotlinx.datetime.DayOfWeek.THURSDAY -> "Kamis"
        kotlinx.datetime.DayOfWeek.FRIDAY -> "Jumat"
        kotlinx.datetime.DayOfWeek.SATURDAY -> "Sabtu"
        kotlinx.datetime.DayOfWeek.SUNDAY -> "Minggu"
    }
    val months = listOf(
        "Januari", "Februari", "Maret", "April", "Mei", "Juni",
        "Juli", "Agustus", "September", "Oktober", "November", "Desember",
    )
    return "$dow, ${d.dayOfMonth} ${months[d.monthNumber - 1]}"
}
