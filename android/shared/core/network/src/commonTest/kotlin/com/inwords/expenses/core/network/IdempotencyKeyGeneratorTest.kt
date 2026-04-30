package com.inwords.expenses.core.network

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

internal class IdempotencyKeyGeneratorTest {

    @Test
    fun `mobileIdempotencyKey includes stored client id, operation, and parts`() = runTest {
        val generator = IdempotencyKeyGenerator(
            idempotencyClientIdProvider = FakeIdempotencyClientIdProvider("client-id"),
        )

        val key = generator.mobileIdempotencyKey(
            operation = "event.expense.add",
            "server-event-id",
            "local-expense-id",
        )

        assertEquals("mobile:client-id:event.expense.add:server-event-id:local-expense-id", key)
    }

    @Test
    fun `mobileIdempotencyKey keeps operation name as namespace`() = runTest {
        val generator = IdempotencyKeyGenerator(
            idempotencyClientIdProvider = FakeIdempotencyClientIdProvider("client-id"),
        )

        val key = generator.mobileIdempotencyKey(
            operation = "event.create",
            "event-client-create-id",
        )

        assertEquals(
            "mobile:client-id:event.create:event-client-create-id",
            key,
        )
    }

    @Test
    fun `mobileIdempotencyKey preserves durable id order`() = runTest {
        val generator = IdempotencyKeyGenerator(
            idempotencyClientIdProvider = FakeIdempotencyClientIdProvider("client-id"),
        )

        val key = generator.mobileIdempotencyKey(
            operation = "event.persons.add",
            "srv-event",
            "person-a",
            "person-b",
        )

        assertEquals(
            "mobile:client-id:event.persons.add:srv-event:person-a:person-b",
            key,
        )
    }

    @Test
    fun `mobileIdempotencyKey hashes payload when projected key is too long`() = runTest {
        val generator = IdempotencyKeyGenerator(
            idempotencyClientIdProvider = FakeIdempotencyClientIdProvider("client-id"),
        )
        val longIds = Array(10) { index -> "person-$index-" + "x".repeat(50) }

        val key = generator.mobileIdempotencyKey(
            operation = "event.persons.add",
            *longIds,
        )
        val sameKey = generator.mobileIdempotencyKey(
            operation = "event.persons.add",
            *longIds,
        )
        val differentOrderKey = generator.mobileIdempotencyKey(
            operation = "event.persons.add",
            *longIds.reversed().toTypedArray(),
        )

        assertTrue(key.startsWith("mobile:client-id:event.persons.add:h:"))
        assertEquals("mobile:client-id:event.persons.add:h:".length + 64, key.length)
        assertEquals(key, sameKey)
        assertNotEquals(key, differentOrderKey)
    }

    private class FakeIdempotencyClientIdProvider(
        private val clientId: String,
    ) : IdempotencyClientIdProvider {

        override suspend fun getOrCreateClientId(): String = clientId
    }
}
