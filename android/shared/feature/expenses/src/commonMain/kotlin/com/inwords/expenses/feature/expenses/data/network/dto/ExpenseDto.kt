package com.inwords.expenses.feature.expenses.data.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
internal data class ExpenseDto(
    @SerialName("id")
    val id: String,

    @SerialName("eventId")
    val eventId: String,

    @SerialName("description")
    val description: String,

    @SerialName("userWhoPaidId")
    val userWhoPaidId: String,

    @SerialName("currencyId")
    val currencyId: String,

    @SerialName("expenseType")
    val expenseType: String,

    @SerialName("splitInformation")
    val splitInformation: List<SplitInformationDto>,

    @SerialName("isCustomRate")
    val isCustomRate: Boolean,

    @SerialName("createdAt")
    val createdAt: Instant
)
