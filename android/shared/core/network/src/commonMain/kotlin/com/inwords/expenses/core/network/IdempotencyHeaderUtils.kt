package com.inwords.expenses.core.network

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header

const val IDEMPOTENCY_KEY_HEADER = "Idempotency-Key"

fun HttpRequestBuilder.idempotencyKey(value: String) {
    header(IDEMPOTENCY_KEY_HEADER, value)
}
