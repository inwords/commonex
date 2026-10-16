package com.inwords.expenses.feature.events.domain.model

import kotlin.time.Instant

data class EventInvitation(
    val url: String,
    val expiresAt: Instant?,
)
