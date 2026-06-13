package com.inwords.expenses.feature.expenses.data.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class CreateExpenseRequest(

    @SerialName("description")
    val description: String,

    @SerialName("userWhoPaidId")
    val userWhoPaidId: String,

    @SerialName("currencyId")
    val currencyId: String,

    @SerialName("expenseType")
    val expenseType: String,

    @SerialName("splitInformation")
    val splitInformation: List<SplitInformationRequest>,

    @SerialName("pinCode")
    val pinCode: String,

    @SerialName("isCustomRate")
    val isCustomRate: Boolean,

    @SerialName("revertsExpenseId")
    val revertsExpenseId: String? = null,

    @SerialName("replacesExpenseId")
    val replacesExpenseId: String? = null,
)
