package com.inwords.expenses.feature.events.data.db.converter

import com.inwords.expenses.core.utils.normalizeCurrencyRate
import com.inwords.expenses.feature.events.data.db.entity.CurrencyEntity
import com.inwords.expenses.feature.events.data.db.entity.EventEntity
import com.inwords.expenses.feature.events.data.db.entity.PersonEntity
import com.inwords.expenses.feature.events.domain.model.Currency
import com.inwords.expenses.feature.events.domain.model.EventDetails
import com.inwords.expenses.feature.events.domain.model.Person

internal fun EventDetails.toEntity() = EventEntity(
    eventId = event.id,
    eventServerId = event.serverId,
    name = event.name,
    pinCode = event.pinCode,
    primaryCurrencyId = primaryCurrency.id,
)

internal fun Person.toEntity(): PersonEntity {
    return PersonEntity(
        personId = id,
        personServerId = serverId,
        name = name,
    )
}

internal fun Currency.toEntity(): CurrencyEntity {
    val normalizedRate = rate.normalizeCurrencyRate()

    return CurrencyEntity(
        currencyId = id,
        currencyServerId = serverId,
        code = code,
        name = name,
        rateUnscaled = normalizedRate.significand,
        rateScale = normalizedRate.exponent,
    )
}
