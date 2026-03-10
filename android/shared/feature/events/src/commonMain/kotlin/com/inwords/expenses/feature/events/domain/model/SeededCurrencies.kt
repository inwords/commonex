package com.inwords.expenses.feature.events.domain.model

import com.ionspin.kotlin.bignum.decimal.BigDecimal

object SeededCurrencies {

    val USD = Currency(
        id = 2L,
        serverId = null,
        code = "USD",
        name = "US Dollar",
        rate = BigDecimal.ONE,
    )

    val all: List<Currency> = listOf(
        Currency(
            id = 1L,
            serverId = null,
            code = "EUR",
            name = "Euro",
            rate = BigDecimal.parseString("0.8677"),
        ),
        USD,
        Currency(
            id = 3L,
            serverId = null,
            code = "RUB",
            name = "Russian Ruble",
            rate = BigDecimal.parseString("79.9489"),
        ),
        Currency(
            id = 4L,
            serverId = null,
            code = "JPY",
            name = "Japanese Yen",
            rate = BigDecimal.parseString("158.4072"),
        ),
        Currency(
            id = 5L,
            serverId = null,
            code = "TRY",
            name = "Turkish Lira",
            rate = BigDecimal.parseString("44.0858"),
        ),
        Currency(
            id = 6L,
            serverId = null,
            code = "AED",
            name = "UAE Dirham",
            rate = BigDecimal.parseString("3.6726"),
        ),
    )

    // How many units of each currency equals 1 USD
    val usdToOtherRates: Map<String, BigDecimal> = buildMap {
        all.forEach { currency ->
            put(currency.code, currency.rate)
        }
    }

    private val currencyNamesByCode: Map<String, String> = buildMap {
        all.forEach { currency ->
            put(currency.code, currency.name)
        }
    }

    fun nameForCode(code: String): String {
        return currencyNamesByCode[code] ?: code // FIXME: non-fatal
    }
}
