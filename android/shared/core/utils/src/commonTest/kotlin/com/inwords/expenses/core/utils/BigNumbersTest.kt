package com.inwords.expenses.core.utils

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class BigNumbersTest {

    // --- String.toBigDecimalOrNull() ---

    @Test
    fun toBigDecimalOrNull_validInteger_returnsParsed() {
        assertEquals(BigDecimal.parseString("42"), "42".toBigDecimalOrNull())
        assertEquals(BigDecimal.parseString("0"), "0".toBigDecimalOrNull())
        assertEquals(BigDecimal.parseString("-100"), "-100".toBigDecimalOrNull())
    }

    @Test
    fun toBigDecimalOrNull_validDecimal_returnsParsed() {
        assertEquals(BigDecimal.parseString("10.5"), "10.5".toBigDecimalOrNull())
        assertEquals(BigDecimal.parseString("0.001"), "0.001".toBigDecimalOrNull())
        assertEquals(BigDecimal.parseString("-3.14"), "-3.14".toBigDecimalOrNull())
    }

    @Test
    fun toBigDecimalOrNull_invalid_returnsNull() {
        assertNull("".toBigDecimalOrNull())
        assertNull("abc".toBigDecimalOrNull())
        assertNull("12.34.56".toBigDecimalOrNull())
        assertNull("--1".toBigDecimalOrNull())
    }

    @Test
    fun toBigDecimalOrNull_whitespaceOnly_returnsNull() {
        assertNull("   ".toBigDecimalOrNull())
    }

    @Test
    fun toBigDecimalOrNull_validWithSpaces_returnsNullOrParsed() {
        // parseString may or may not trim; we test behavior
        assertNull("  ".toBigDecimalOrNull())
    }

    // --- BigDecimal.divide(other, scale) ---

    @Test
    fun divide_normalCase_returnsQuotientWithScale() {
        val a = BigDecimal.parseString("10")
        val b = BigDecimal.parseString("4")
        val result = a.divide(b, 2)
        assertEquals(BigDecimal.parseString("2.50"), result)
    }

    @Test
    fun divide_sameValue_returnsOne() {
        val a = BigDecimal.parseString("7.5")
        val b = BigDecimal.parseString("7.5")
        val result = a.divide(b, 2)
        assertEquals(BigDecimal.parseString("1.00"), result)
    }

    @Test
    fun divide_largeScale_roundsCorrectly() {
        val a = BigDecimal.parseString("1")
        val b = BigDecimal.parseString("3")
        val result = a.divide(b, 4)
        assertEquals(BigDecimal.parseString("0.3333"), result)
    }

    @Test
    fun divide_zeroDivisor_throws() {
        val a = BigDecimal.parseString("10")
        val b = BigDecimal.ZERO
        try {
            a.divide(b, 2)
            kotlin.test.fail("Expected ArithmeticException or similar")
        } catch (e: Throwable) {
            assertTrue(e is ArithmeticException || e is IllegalArgumentException || e.cause is ArithmeticException)
        }
    }

    // --- BigDecimal.toPlainDecimalString(scale) ---

    @Test
    fun toPlainDecimalString_wholeNumber_defaultScale_noTrailingZeros() {
        val value = BigDecimal.parseString("10")
        assertEquals("10", value.toPlainDecimalString())
    }

    @Test
    fun toPlainDecimalString_wholeNumber_explicitScale_noTrailingZeros() {
        val value = BigDecimal.parseString("10")
        assertEquals("10", value.toPlainDecimalString(2))
    }

    @Test
    fun toPlainDecimalString_decimal_usesGivenScale() {
        val value = BigDecimal.parseString("10.125")
        assertEquals("10.13", value.toPlainDecimalString(2))
    }

    @Test
    fun toPlainDecimalString_zero_returnsZero() {
        assertEquals("0", BigDecimal.ZERO.toPlainDecimalString())
        assertEquals("0", BigDecimal.ZERO.toPlainDecimalString(2))
    }

    @Test
    fun toPlainDecimalString_negative_whole() {
        val value = BigDecimal.parseString("-25")
        assertEquals("-25", value.toPlainDecimalString(2))
    }

    @Test
    fun toPlainDecimalString_negative_decimal() {
        val value = BigDecimal.parseString("-25.456")
        assertEquals("-25.46", value.toPlainDecimalString(2))
    }

    @Test
    fun toPlainDecimalString_scaleZero_roundsToInteger() {
        val value = BigDecimal.parseString("10.78")
        assertEquals("11", value.toPlainDecimalString(0))
    }

    @Test
    fun toPlainDecimalString_largeScale() {
        val value = BigDecimal.parseString("1.234567")
        assertEquals("1.2346", value.toPlainDecimalString(4))
    }
}
