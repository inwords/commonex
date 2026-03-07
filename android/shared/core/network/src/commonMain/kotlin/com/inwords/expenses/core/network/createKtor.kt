package com.inwords.expenses.core.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

private val IDEMPOTENT_METHODS = setOf(
    HttpMethod.Get,
    HttpMethod.Head,
    HttpMethod.Put,
    HttpMethod.Delete,
    HttpMethod.Options,
)

internal fun <T : HttpClientEngineConfig> createKtor(
    httpClientEngine: HttpClientEngineFactory<T>,
    enableLogging: Boolean = false,
    block: HttpClientConfig<T>.() -> Unit
): HttpClient {
    return HttpClient(httpClientEngine) {
        block.invoke(this)

        expectSuccess = true

        followRedirects = false

        install(HttpRequestRetry) {
            noRetry()
            retryIf(maxRetries = 2) { request, response ->
                response.status == HttpStatusCode.Conflict && request.method in IDEMPOTENT_METHODS
            }
            retryOnExceptionIf(maxRetries = 2) { request, cause ->
                val exception = cause as? ClientRequestException ?: return@retryOnExceptionIf false
                exception.response.status == HttpStatusCode.Conflict && request.method in IDEMPOTENT_METHODS
            }
            exponentialDelay(
                baseDelayMs = 200,
                maxDelayMs = 2_000,
                randomizationMs = 100,
                respectRetryAfterHeader = true
            )
        }

        install(ContentNegotiation) {
            json(
                Json { ignoreUnknownKeys = true }
            )
        }

        install(Logging) {
            logger = getLogger()
            level = if (enableLogging) {
                LogLevel.ALL
            } else {
                LogLevel.NONE
            }
        }
    }
}
