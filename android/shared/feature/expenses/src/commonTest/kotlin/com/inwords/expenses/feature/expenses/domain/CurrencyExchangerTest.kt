package com.inwords.expenses.feature.expenses.domain

import com.inwords.expenses.core.utils.divide
import com.inwords.expenses.feature.events.domain.model.Currency
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

internal class CurrencyExchangerTest {

    // Define our own test rates for consistent testing
    private val testUsdToOtherRates = listOf(
        currency(id = 1L, code = "USD", rate = BigDecimal.fromDouble(1.0)),
        currency(id = 2L, code = "EUR", rate = BigDecimal.fromDouble(0.85)),
        currency(id = 3L, code = "GBP", rate = BigDecimal.fromDouble(0.75)),
        currency(id = 4L, code = "JPY", rate = BigDecimal.fromDouble(150.0)),
        currency(id = 5L, code = "CAD", rate = BigDecimal.fromDouble(1.35)),
    )

    private val currencyExchanger = exchangerWithRates(testUsdToOtherRates.toDirectPairs())

    @Test
    fun `exchange same currency returns same amount`() {
        val amount = BigDecimal.fromDouble(100.0)

        val result = currencyExchanger.exchange(amount, "USD", "USD")

        assertEquals(amount, result)
    }

    @Test
    fun `exchange USD to EUR with test rates`() {
        val amount = BigDecimal.fromDouble(100.0)

        val result = currencyExchanger.exchange(amount, "USD", "EUR")

        // 100 USD * 0.85 = 85 EUR
        assertEquals(BigDecimal.fromDouble(85.0), result)
    }

    @Test
    fun `exchange EUR to USD with test rates`() {
        val amount = BigDecimal.fromDouble(85.0)

        val result = currencyExchanger.exchange(amount, "EUR", "USD")

        // 85 EUR * (1.0 / 0.85) = 100 USD
        val expectedRate = BigDecimal.fromDouble(1.0).divide(BigDecimal.fromDouble(0.85), 4L)
        val expected = amount * expectedRate
        assertEquals(expected, result)
    }

    @Test
    fun `exchange USD to JPY with test rates`() {
        val amount = BigDecimal.fromDouble(100.0)

        val result = currencyExchanger.exchange(amount, "USD", "JPY")

        // 100 USD * 150.0 = 15000 JPY
        assertEquals(BigDecimal.fromDouble(15000.0), result)
    }

    @Test
    fun `exchange JPY to USD with test rates`() {
        val amount = BigDecimal.fromDouble(15000.0)

        val result = currencyExchanger.exchange(amount, "JPY", "USD")

        // 15000 JPY * (1.0 / 150.0) = 100 USD
        val expectedRate = BigDecimal.fromDouble(1.0).divide(BigDecimal.fromDouble(150.0), 4L)
        val expected = amount * expectedRate
        assertEquals(expected, result)
    }

    @Test
    fun `exchange between non-USD currencies`() {
        val amount = BigDecimal.fromDouble(100.0)

        val result = currencyExchanger.exchange(amount, "EUR", "GBP")

        // 100 EUR -> USD: 100 * (1.0 / 0.85) = 117.647...
        // 117.647... USD -> GBP: 117.647... * 0.75 = 88.235...
        // Direct rate: 0.75 / 0.85 = 0.8824 (rounded to 4 decimal places)
        val expectedDirectRate = BigDecimal.fromDouble(0.75).divide(BigDecimal.fromDouble(0.85), 4L)
        val expected = amount * expectedDirectRate
        assertEquals(expected, result)
    }

    @Test
    fun `exchange GBP to CAD`() {
        val amount = BigDecimal.fromDouble(100.0)

        val result = currencyExchanger.exchange(amount, "GBP", "CAD")

        // Direct rate: CAD_rate / GBP_rate = 1.35 / 0.75 = 1.8
        val expectedDirectRate = BigDecimal.fromDouble(1.35).divide(BigDecimal.fromDouble(0.75), 4L)
        val expected = amount * expectedDirectRate
        assertEquals(expected, result)
    }

    @Test
    fun `exchange with fractional amounts`() {
        val amount = BigDecimal.fromDouble(25.99)

        val result = currencyExchanger.exchange(amount, "USD", "EUR")

        // 25.99 USD * 0.85 = 22.0915 EUR
        val expected = amount * BigDecimal.fromDouble(0.85)
        assertEquals(expected, result)
    }

    @Test
    fun `exchange with zero amount`() {
        val amount = BigDecimal.fromDouble(0.0)

        val result = currencyExchanger.exchange(amount, "USD", "EUR")

        assertEquals(BigDecimal.fromDouble(0.0), result)
    }

    @Test
    fun `exchange throws exception for unknown source currency`() {
        val amount = BigDecimal.fromDouble(100.0)

        val exception = assertFailsWith<IllegalArgumentException> {
            currencyExchanger.exchange(amount, "UNKNOWN", "USD")
        }

        assertEquals("No exchange rate for UNKNOWN -> USD", exception.message)
    }

    @Test
    fun `exchange throws exception for unknown target currency`() {
        val amount = BigDecimal.fromDouble(100.0)

        val exception = assertFailsWith<IllegalArgumentException> {
            currencyExchanger.exchange(amount, "USD", "UNKNOWN")
        }

        assertEquals("No exchange rate for USD -> UNKNOWN", exception.message)
    }

    @Test
    fun `exchange throws exception for both unknown currencies`() {
        val amount = BigDecimal.fromDouble(100.0)

        val exception = assertFailsWith<IllegalArgumentException> {
            currencyExchanger.exchange(amount, "UNKNOWN1", "UNKNOWN2")
        }

        assertEquals("No exchange rate for UNKNOWN1 -> UNKNOWN2", exception.message)
    }

    @Test
    fun `exchange with large amounts`() {
        val amount = BigDecimal.fromDouble(1_000_000.0)

        val result = currencyExchanger.exchange(amount, "USD", "JPY")

        // 1,000,000 USD * 150.0 = 150,000,000 JPY
        assertEquals(BigDecimal.fromDouble(150_000_000.0), result)
    }

    @Test
    fun `verify direct rate calculation precision`() {
        // Create exchanger with precise rates for testing
        val preciseRates = listOf(
            currency(id = 1L, code = "USD", rate = BigDecimal.fromDouble(1.0)),
            currency(id = 2L, code = "EUR", rate = BigDecimal.fromDouble(0.85)),
            currency(id = 3L, code = "GBP", rate = BigDecimal.fromDouble(0.75)),
        )
        val preciseExchanger = exchangerWithRates(preciseRates.toDirectPairs())

        val amount = BigDecimal.fromDouble(100.0)
        val result = preciseExchanger.exchange(amount, "EUR", "GBP")

        // Expected direct rate: 0.75 / 0.85 = 0.8824 (4 decimal places)
        // 100 EUR * 0.8824 = 88.24 GBP
        val expectedDirectRate = BigDecimal.fromDouble(0.75).divide(BigDecimal.fromDouble(0.85), 4L)
        val expected = amount * expectedDirectRate
        assertEquals(expected, result)
    }

    @Test
    fun `verify bidirectional conversion consistency`() {
        val originalAmount = BigDecimal.fromDouble(100.0)

        // Convert USD to EUR
        val usdToEur = currencyExchanger.exchange(originalAmount, "USD", "EUR")

        // Convert back EUR to USD
        val eurToUsd = currencyExchanger.exchange(usdToEur, "EUR", "USD")

        // Due to rounding in division, there might be small precision differences
        // Check if the difference is within acceptable bounds (less than 0.01)
        val difference = (originalAmount - eurToUsd).abs()
        val tolerance = BigDecimal.fromDouble(0.01)

        assertTrue(
            actual = difference < tolerance,
            message = "Bidirectional conversion should be consistent within tolerance. " +
                "Original: $originalAmount, Final: $eurToUsd, Difference: $difference"
        )
    }

    @Test
    fun `exchange with very small amounts`() {
        val amount = BigDecimal.fromDouble(0.01)

        val result = currencyExchanger.exchange(amount, "USD", "EUR")

        // 0.01 USD * 0.85 = 0.0085 EUR
        val expected = amount * BigDecimal.fromDouble(0.85)
        assertEquals(expected, result)
    }

    @Test
    fun `exchange with negative amounts`() {
        val amount = BigDecimal.fromDouble(-100.0)

        val result = currencyExchanger.exchange(amount, "USD", "EUR")

        // -100 USD * 0.85 = -85 EUR
        val expected = amount * BigDecimal.fromDouble(0.85)
        assertEquals(expected, result)
    }

    @Test
    fun `verify all currency pairs work with test rates`() {
        val testCurrencies = listOf("USD", "EUR", "GBP", "JPY", "CAD")
        val testAmount = BigDecimal.fromDouble(100.0)

        // Test every currency pair (except same-to-same)
        for (from in testCurrencies) {
            for (to in testCurrencies) {
                if (from != to) {
                    // Should not throw exception for any supported pair
                    val result = currencyExchanger.exchange(testAmount, from, to)
                    assertTrue(
                        actual = result > BigDecimal.fromDouble(0.0),
                        message = "Exchange from $from to $to should return positive result"
                    )
                }
            }
        }
    }

    @Test
    fun `verify rate calculation matches implementation logic`() {
        // Test that our manual rate calculation matches what the implementation does
        val amount = BigDecimal.fromDouble(100.0)

        // For EUR to GBP: implementation does GBP_rate.divide(EUR_rate, 4)
        // Which is: 0.75.divide(0.85, 4) 
        val expectedRate = BigDecimal.fromDouble(0.75).divide(BigDecimal.fromDouble(0.85), 4L)
        val actualResult = currencyExchanger.exchange(amount, "EUR", "GBP")
        val expectedResult = amount * expectedRate

        assertEquals(expectedResult, actualResult)
    }

    private fun exchangerWithRates(
        directPairs: Map<CurrencyRatePair, BigDecimal>,
    ): CurrencyExchanger = CurrencyExchanger { directPairs }

    private fun currency(
        id: Long,
        code: String,
        rate: BigDecimal,
    ): Currency {
        return Currency(
            id = id,
            serverId = null,
            code = code,
            name = code,
            rate = rate,
        )
    }
}
