package com.inwords.expenses.feature.expenses.ui.list.bottom_sheet.item

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal class ExchangeRateCalculatorTest {

    @Test
    fun `calculateExchangeRateValue handles very small ratio`() {
        val result = calculateExchangeRateValue(
            totalOriginalAmount = BigDecimal.parseString("1000000000000"),
            totalExchangedAmount = BigDecimal.ONE,
        )

        assertEquals("0.00", result)
    }

    @Test
    fun `calculateExchangeRateValue handles very large ratio`() {
        val result = calculateExchangeRateValue(
            totalOriginalAmount = BigDecimal.ONE,
            totalExchangedAmount = BigDecimal.parseString("1000000000000"),
        )

        assertEquals("1000000000000.00", result)
    }

    @Test
    fun `calculateExchangeRateValue returns null when original total is zero`() {
        val result = calculateExchangeRateValue(
            totalOriginalAmount = BigDecimal.ZERO,
            totalExchangedAmount = BigDecimal.parseString("42"),
        )

        assertNull(result)
    }
}
