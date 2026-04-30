package com.inwords.expenses.core.network

import com.inwords.expenses.core.utils.Component
import io.ktor.client.HttpClient
import io.ktor.http.URLProtocol
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile

class NetworkComponent internal constructor(
    private val httpClientFactory: HttpClientFactory,
    val idempotencyKeyGenerator: IdempotencyKeyGenerator,
) : Component {

    private val mutex = Mutex()

    @Volatile
    private var httpClient: HttpClient? = null

    suspend fun getHttpClient(): HttpClient {
        httpClient?.let { return it }

        return mutex.withLock {
            httpClient ?: httpClientFactory.createHttpClient().also {
                httpClient = it
            }
        }
    }

    val hostConfig: HostConfig = HostConfig(URLProtocol.HTTPS, "dev-api.commonex.ru")

}
