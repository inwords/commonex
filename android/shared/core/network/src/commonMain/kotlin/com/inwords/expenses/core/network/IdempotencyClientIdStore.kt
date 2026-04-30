package com.inwords.expenses.core.network

import com.inwords.expenses.core.storage.utils.fileSystemSystem
import com.inwords.expenses.core.utils.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import kotlin.concurrent.Volatile
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

internal class IdempotencyClientIdStore(
    directory: Path,
    private val fileSystem: FileSystem = fileSystemSystem,
) : IdempotencyClientIdProvider {

    private val mutex = Mutex()

    private val path = directory / IDEMPOTENCY_CLIENT_ID_FILE_NAME

    @Volatile
    private var cachedClientId: String? = null

    override suspend fun getOrCreateClientId(): String {
        cachedClientId?.let { return it }

        return withContext(IO) {
            mutex.withLock {
                cachedClientId?.let { return@withContext it }

                val existingClientId = readClientId()?.trim()?.takeIf { it.isNotBlank() }
                val clientId = existingClientId ?: generateClientUuid().also(::writeClientId)
                cachedClientId = clientId
                clientId
            }
        }
    }

    private fun readClientId(): String? {
        return if (fileSystem.exists(path)) {
            fileSystem.read(path) { readUtf8() }
        } else {
            null
        }
    }

    private fun writeClientId(value: String) {
        path.parent?.let(fileSystem::createDirectories)
        fileSystem.write(path) {
            writeUtf8(value)
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun generateClientUuid(): String = Uuid.random().toString()
}

internal const val IDEMPOTENCY_CLIENT_ID_FILE_NAME = "idempotency_client_id"
