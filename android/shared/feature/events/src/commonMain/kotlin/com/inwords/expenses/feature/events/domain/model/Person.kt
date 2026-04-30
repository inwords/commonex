package com.inwords.expenses.feature.events.domain.model

data class Person(
    val id: Long,
    val serverId: String?,
    val name: String,
    val clientCreateId: String,
)
