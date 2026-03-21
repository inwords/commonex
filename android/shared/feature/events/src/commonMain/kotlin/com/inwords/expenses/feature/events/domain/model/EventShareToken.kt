package com.inwords.expenses.feature.events.domain.model

import kotlin.time.Instant

data class EventShareToken(
    val token: String,
    val expiresAt: Instant,
)
