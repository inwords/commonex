package com.inwords.expenses.feature.events.data.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
internal data class CurrencyDto(
    @SerialName("id")
    val id: String,

    @SerialName("code")
    val code: String,
)

@Serializable
internal data class CurrenciesWithRatesResponseDto(
    @SerialName("currencies")
    val currencies: List<CurrencyDto>,

    @SerialName("exchangeRate")
    val exchangeRate: JsonObject,
)
