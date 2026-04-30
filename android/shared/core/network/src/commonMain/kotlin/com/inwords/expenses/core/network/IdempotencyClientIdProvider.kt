package com.inwords.expenses.core.network

internal interface IdempotencyClientIdProvider {

    suspend fun getOrCreateClientId(): String
}
