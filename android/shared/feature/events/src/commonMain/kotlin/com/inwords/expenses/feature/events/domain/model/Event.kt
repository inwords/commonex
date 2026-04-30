package com.inwords.expenses.feature.events.domain.model

data class Event(
    val id: Long,
    val serverId: String?,
    val name: String,
    val pinCode: String,
    val primaryCurrencyId: Long,
    val clientCreateId: String,
)
