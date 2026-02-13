package com.pos.app

import com.pos.app.util.grouped
import com.pos.app.util.parseRupiahInput
import kotlin.test.Test
import kotlin.test.assertEquals

class MoneyInputTest {

    @Test
    fun parseStripsGroupingAndDecimalDecoration() {
        assertEquals(1_320_000L, parseRupiahInput("1.320.000"))
        assertEquals(1_320_000L, parseRupiahInput("1.320.000,00"))
        assertEquals(0L, parseRupiahInput(""))
        assertEquals(5L, parseRupiahInput("5"))
    }

    @Test
    fun typingDigitByDigitDoesNotInflate() {
        // Regression: ",00" suffix used to be re-parsed as digits, exploding 1 -> 1.320.000...
        var value = 0L
        for (ch in "1320000") {
            value = parseRupiahInput(value.grouped() + ch)
        }
        assertEquals(1_320_000L, value)
    }

    @Test
    fun groupedUsesDotThousands() {
        assertEquals("1.320.000", 1_320_000L.grouped())
        assertEquals("999", 999L.grouped())
    }
}
