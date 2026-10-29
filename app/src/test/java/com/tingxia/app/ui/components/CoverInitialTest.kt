package com.tingxia.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoverInitialTest {

    @Test
    fun skipsLeadingBookPunctuationAndExtractsFirstCharacter() {
        assertEquals("三", extractCoverInitial("《三体》"))
        assertEquals("凡", extractCoverInitial("  【精校有声】" .replace("精校有声", "") + "凡人修仙传"))
        assertEquals("1", extractCoverInitial("《1984》"))
        assertEquals("D", extractCoverInitial("\"Dune\""))
    }

    @Test
    fun returnsNullForBlankOrPunctuationOnlyTitles() {
        assertNull(extractCoverInitial(""))
        assertNull(extractCoverInitial("   "))
        assertNull(extractCoverInitial("《》——…"))
    }
}
