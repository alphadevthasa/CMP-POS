package com.pos.app

import com.pos.app.util.toRupiahShort
import kotlin.test.Test
import kotlin.test.assertEquals

// Tes tersembunyi untuk eval `rupiah-short-format`. Disalin ke commonTest SETELAH agen selesai.
class RupiahShortHiddenTest {

    @Test
    fun belowThousandIsUnchanged() {
        assertEquals("Rp 900", 900L.toRupiahShort())
    }

    @Test
    fun thousandsUseRb() {
        assertEquals("Rp 25rb", 25_000L.toRupiahShort())
        assertEquals("Rp 1,5rb", 1_500L.toRupiahShort())
    }

    @Test
    fun millionsUseJt() {
        assertEquals("Rp 2jt", 2_000_000L.toRupiahShort())
        assertEquals("Rp 1,5jt", 1_500_000L.toRupiahShort())
    }
}
