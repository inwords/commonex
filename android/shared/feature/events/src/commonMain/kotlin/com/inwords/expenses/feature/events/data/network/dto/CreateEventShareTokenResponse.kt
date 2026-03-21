package com.inwords.expenses.feature.events.data.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
internal data class CreateEventShareTokenResponse(
    @SerialName("token")
    val token: String,

    @SerialName("expiresAt")
    val expiresAt: Instant,
)
