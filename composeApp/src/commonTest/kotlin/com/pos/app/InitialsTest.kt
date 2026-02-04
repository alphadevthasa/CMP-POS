package com.pos.app

import com.pos.app.util.productInitials
import kotlin.test.Test
import kotlin.test.assertEquals

class InitialsTest {

    @Test
    fun twoWordsTakeFirstLetterEach() {
        assertEquals("EJ", productInitials("Es Jeruk"))
        assertEquals("AM", productInitials("Air Mineral"))
        assertEquals("TB", productInitials("Teh Botol"))
    }

    @Test
    fun singleWordTakesFirstTwoLetters() {
        assertEquals("AM", productInitials("Americano"))
        assertEquals("A", productInitials("A"))
    }

    @Test
    fun ignoresPunctuationAndBlankFallback() {
        assertEquals("KG", productInitials("Kopi/Gula - Extra"))
        assertEquals("?", productInitials("   "))
    }
}
