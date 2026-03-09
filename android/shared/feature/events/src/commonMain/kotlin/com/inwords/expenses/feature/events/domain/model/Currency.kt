package com.inwords.expenses.feature.events.domain.model

import com.ionspin.kotlin.bignum.decimal.BigDecimal

data class Currency(
    val id: Long,
    val serverId: String?,
    val code: String,
    val name: String,
    val rate: BigDecimal,
)
