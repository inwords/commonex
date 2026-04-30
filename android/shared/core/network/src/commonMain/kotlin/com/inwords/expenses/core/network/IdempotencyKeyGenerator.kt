package com.inwords.expenses.core.network

import okio.Buffer
import okio.HashingSink
import okio.buffer

class IdempotencyKeyGenerator internal constructor(
    private val idempotencyClientIdProvider: IdempotencyClientIdProvider,
) {

    suspend fun mobileIdempotencyKey(operation: String, vararg parts: String): String {
        val clientId = idempotencyClientIdProvider.getOrCreateClientId()
        val projectedLength = projectRawKeyLength(clientId = clientId, operation = operation, parts = parts)

        return if (projectedLength <= MAX_RAW_KEY_LENGTH) {
            buildRawKey(clientId = clientId, operation = operation, parts = parts)
        } else {
            buildHashedKey(clientId = clientId, operation = operation, parts = parts)
        }
    }

    private fun buildRawKey(clientId: String, operation: String, parts: Array<out String>): String = buildString {
        append(MOBILE_PREFIX)
        append(SEPARATOR)
        append(clientId)
        append(SEPARATOR)
        append(operation)
        parts.forEach { part ->
            append(SEPARATOR)
            append(part)
        }
    }

    private fun buildHashedKey(clientId: String, operation: String, parts: Array<out String>): String {
        val hashingSink = HashingSink.sha256(Buffer())
        val bufferedSink = hashingSink.buffer()
        bufferedSink.writeUtf8(MOBILE_PREFIX)
        bufferedSink.writeUtf8(SEPARATOR)
        bufferedSink.writeUtf8(clientId)
        bufferedSink.writeUtf8(SEPARATOR)
        bufferedSink.writeUtf8(operation)
        parts.forEach { part ->
            bufferedSink.writeUtf8(SEPARATOR)
            bufferedSink.writeUtf8(part)
        }
        bufferedSink.close()
        val digest = hashingSink.hash.hex()

        return buildString {
            append(MOBILE_PREFIX)
            append(SEPARATOR)
            append(clientId)
            append(SEPARATOR)
            append(operation)
            append(SEPARATOR)
            append(HASH_PREFIX)
            append(digest)
        }
    }

    private fun projectRawKeyLength(clientId: String, operation: String, parts: Array<out String>): Int {
        val baseLength = MOBILE_PREFIX.length + 1 + clientId.length + 1 + operation.length
        val partsLength = parts.sumOf { it.length + 1 }
        return baseLength + partsLength
    }

    private companion object {
        const val MOBILE_PREFIX = "mobile"
        const val HASH_PREFIX = "h:"
        const val SEPARATOR = ":"
        const val MAX_RAW_KEY_LENGTH = 255
    }
}
