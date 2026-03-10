package com.inwords.expenses.feature.expenses.domain

import androidx.annotation.VisibleForTesting
import com.ionspin.kotlin.bignum.decimal.BigDecimal

internal class CurrencyExchanger @VisibleForTesting constructor(
    private val ratesProvider: () -> Map<CurrencyRatePair, BigDecimal>
) {
    constructor(currencyRatesCache: CurrencyRatesCache) : this(currencyRatesCache::getDirectPairs)

    fun exchange(amount: BigDecimal, fromCurrencyCode: String, toCurrencyCode: String): BigDecimal {
        if (fromCurrencyCode == toCurrencyCode) {
            return amount
        }

        val rate = ratesProvider()[CurrencyRatePair(fromCurrencyCode, toCurrencyCode)]
            ?: throw IllegalArgumentException("No exchange rate for $fromCurrencyCode -> $toCurrencyCode")
        return amount * rate
    }

}
