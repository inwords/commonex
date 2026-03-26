package com.inwords.expenses.feature.expenses.data.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class SplitInformationRequest(
    @SerialName("amount")
    val amount: Double, // TODO pass as String

    @SerialName("userId")
    val userId: String,

    @SerialName("exchangedAmount")
    val exchangedAmount: Double? = null,
)
