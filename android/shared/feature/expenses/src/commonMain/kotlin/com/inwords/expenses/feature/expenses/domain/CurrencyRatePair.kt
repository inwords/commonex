package com.inwords.expenses.feature.expenses.domain

import com.inwords.expenses.core.utils.currencyRateScale
import com.inwords.expenses.core.utils.divide
import com.inwords.expenses.feature.events.domain.model.Currency
import com.ionspin.kotlin.bignum.decimal.BigDecimal

data class CurrencyRatePair(
    val fromCurrencyCode: String,
    val toCurrencyCode: String,
)

internal fun List<Currency>.toDirectPairs(): Map<CurrencyRatePair, BigDecimal> {
    return buildMap {
        for (fromCurrency in this@toDirectPairs) {
            for (toCurrency in this@toDirectPairs) {
                if (fromCurrency.code == toCurrency.code) {
                    continue
                }

                put(
                    CurrencyRatePair(fromCurrencyCode = fromCurrency.code, toCurrencyCode = toCurrency.code),
                    toCurrency.rate.divide(other = fromCurrency.rate, scale = currencyRateScale),
                )
            }
        }
    }
}
