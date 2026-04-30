package com.inwords.expenses.core.network

import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class IdempotencyClientIdStoreTest {

    @Test
    fun `getOrCreateClientId returns existing value from file`() = runTest {
        val fileSystem = FakeFileSystem()
        val directory = "/app/files".toPath()
        val path = directory / IDEMPOTENCY_CLIENT_ID_FILE_NAME
        fileSystem.createDirectories(directory)
        fileSystem.write(path) {
            writeUtf8("existing-client-id")
        }
        val store = IdempotencyClientIdStore(directory, fileSystem)

        val result = store.getOrCreateClientId()

        assertEquals("existing-client-id", result)
        assertEquals("existing-client-id", fileSystem.read(path) { readUtf8() })
    }

    @Test
    fun `getOrCreateClientId writes generated UUID when file is missing`() = runTest {
        val fileSystem = FakeFileSystem()
        val directory = "/app/files".toPath()
        val store = IdempotencyClientIdStore(directory, fileSystem)

        val result = store.getOrCreateClientId()

        assertTrue(result.isNotBlank())
        assertEquals(36, result.length)
        assertEquals(result, fileSystem.read(directory / IDEMPOTENCY_CLIENT_ID_FILE_NAME) { readUtf8() })
    }

    @Test
    fun `getOrCreateClientId caches generated UUID`() = runTest {
        val fileSystem = FakeFileSystem()
        val directory = "/app/files".toPath()
        val path = directory / IDEMPOTENCY_CLIENT_ID_FILE_NAME
        val store = IdempotencyClientIdStore(directory, fileSystem)

        val firstResult = store.getOrCreateClientId()
        fileSystem.write(path) {
            writeUtf8("different-client-id")
        }

        assertEquals(firstResult, store.getOrCreateClientId())
    }
}
