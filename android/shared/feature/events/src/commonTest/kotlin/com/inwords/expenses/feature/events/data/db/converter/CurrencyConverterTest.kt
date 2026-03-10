package com.inwords.expenses.feature.events.data.db.converter

import com.inwords.expenses.feature.events.domain.model.Currency
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals

internal class CurrencyConverterTest {

    @Test
    fun `currency round trips rate via unscaled value and exponent`() {
        val currency = Currency(
            id = 7L,
            serverId = "srv-eur",
            code = "EUR",
            name = "Euro",
            rate = BigDecimal.parseString("0.867713"),
        )

        val entity = currency.toEntity()
        val restored = entity.toDomain()

        assertEquals(currency.id, restored.id)
        assertEquals(currency.serverId, restored.serverId)
        assertEquals(currency.code, restored.code)
        assertEquals(currency.name, restored.name)
        assertEquals(BigDecimal.parseString("0.8677"), restored.rate)
    }
}
