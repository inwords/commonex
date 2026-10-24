package com.inwords.expenses.core.network

import com.inwords.expenses.core.utils.IoResult
import com.inwords.expenses.core.utils.SuspendLazy
import io.ktor.client.call.body
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.request
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.ContentConvertException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

internal class RequestRetryTest {

    @Test
    fun `requestWithExceptionHandling retries 409 twice and succeeds on third attempt`() = runTest {
        val (result, attemptsCount) = executeRequestWithStatuses(
            statuses = arrayOf(
                HttpStatusCode.Conflict,
                HttpStatusCode.Conflict,
                HttpStatusCode.OK
            )
        )

        val success = assertIs<NetworkResult.Ok<String>>(result)
        assertEquals("ok", success.data)
        assertEquals(3, attemptsCount)
    }

    @Test
    fun `requestWithExceptionHandling does not retry 400 and returns failure`() = runTest {
        val (result, attemptsCount) = executeRequestWithStatuses(
            statuses = arrayOf(
                HttpStatusCode.BadRequest,
                HttpStatusCode.OK
            )
        )

        val clientError = assertIs<NetworkResult.Error.Http.Client>(result)
        assertEquals(HttpStatusCode.BadRequest, clientError.exception.response.status)
        assertEquals(1, attemptsCount)
        assertEquals(IoResult.Error.Failure, result.toIoResult())
    }

    @Test
    fun `persistent 409 maps to IoResult Retry after retry budget is exhausted`() = runTest {
        val (result, attemptsCount) = executeRequestWithStatuses(
            statuses = arrayOf(
                HttpStatusCode.Conflict,
                HttpStatusCode.Conflict,
                HttpStatusCode.Conflict,
                HttpStatusCode.OK
            )
        )

        val clientError = assertIs<NetworkResult.Error.Http.Client>(result)
        assertEquals(HttpStatusCode.Conflict, clientError.exception.response.status)
        assertEquals(3, attemptsCount)
        assertEquals(IoResult.Error.Retry, result.toIoResult())
    }

    @Test
    fun `requestWithExceptionHandling does not retry 409 for POST by default`() = runTest {
        val (result, attemptsCount) = executeRequestWithStatuses(
            method = HttpMethod.Post,
            statuses = arrayOf(
                HttpStatusCode.Conflict,
                HttpStatusCode.OK
            )
        )

        val clientError = assertIs<NetworkResult.Error.Http.Client>(result)
        assertEquals(HttpStatusCode.Conflict, clientError.exception.response.status)
        assertEquals(1, attemptsCount)
        assertEquals(IoResult.Error.Retry, result.toIoResult())
    }

    @Test
    fun `non-409 client request exception maps to IoResult Failure`() = runTest {
        val error = executeClientRequestExceptionWithStatus(HttpStatusCode.NotFound)

        assertEquals(HttpStatusCode.NotFound, error.response.status)
        assertEquals(IoResult.Error.Failure, NetworkResult.Error.Http.Client(error).toIoResult())
    }

    @Test
    fun `requestWithExceptionHandling propagates cancellation instead of mapping it to an error`() = runTest {
        unusedClient().use { client ->
            val exception = assertFailsWith<CancellationException> {
                SuspendLazy { client }.requestWithExceptionHandling<String> {
                    throw CancellationException("Request cancelled")
                }
            }

            assertEquals("Request cancelled", exception.message)
        }
    }

    @Test
    fun `requestWithExceptionHandling maps transport failures to retryable results`() = runTest {
        unusedClient().use { client ->
            val result = SuspendLazy { client }.requestWithExceptionHandling<String> {
                throw IOException("Connection reset")
            }

            assertEquals("Connection reset", assertIs<NetworkResult.Error.Transport>(result).exception.message)
            assertEquals(IoResult.Error.Retry, result.toIoResult())
        }
    }

    @Test
    fun `requestWithExceptionHandling maps missing and unconvertible content to permanent failure`() = runTest {
        unusedClient().use { client ->
            val results = listOf(
                SuspendLazy { client }.requestWithExceptionHandling<String> { null },
                SuspendLazy { client }.requestWithExceptionHandling<String> {
                    throw ContentConvertException("Invalid response content")
                },
            )

            results.forEach { result ->
                assertIs<NetworkResult.Error.Parse>(result)
                assertEquals(IoResult.Error.Failure, result.toIoResult())
            }
        }
    }

    @Test
    fun `requestWithExceptionHandling reports a missing transformation for an HTML response without retrying`() = runTest {
        var attempts = 0
        val client = createKtor(MockEngine) {
            engine {
                addHandler {
                    attempts++
                    respond(
                        content = "<html>Maintenance page</html>",
                        status = HttpStatusCode.OK,
                        headers = headersOf("Content-Type", "text/html"),
                    )
                }
            }
        }

        client.use {
            val result = SuspendLazy { client }.requestWithExceptionHandling {
                request("https://commonex.test/response").body<JsonObject>()
            }

            assertIs<NetworkResult.Error.NoTransformation>(result)
            assertEquals(IoResult.Error.Failure, result.toIoResult())
            assertEquals(1, attempts)
        }
    }

    private fun unusedClient() = createKtor(MockEngine) {
        engine {
            addHandler { error("This test must not send an HTTP request") }
        }
    }

    private suspend fun executeRequestWithStatuses(
        method: HttpMethod = HttpMethod.Get,
        statuses: Array<HttpStatusCode>
    ): Pair<NetworkResult<String>, Int> {
        var attempts = 0
        val client = createKtor(
            httpClientEngine = MockEngine
        ) {
            engine {
                addHandler {
                    val status = statuses.getOrElse(attempts) { statuses.last() }
                    attempts += 1
                    respond(
                        content = if (status == HttpStatusCode.OK) {
                            "ok"
                        } else {
                            "error"
                        },
                        status = status,
                        headers = headersOf("Content-Type", "text/plain")
                    )
                }
            }
        }

        return try {
            val result = SuspendLazy { client }.requestWithExceptionHandling {
                request("https://commonex.test/retry") {
                    this.method = method
                }.bodyAsText()
            }
            result to attempts
        } finally {
            client.close()
        }
    }

    private suspend fun executeClientRequestExceptionWithStatus(status: HttpStatusCode): ClientRequestException {
        val client = createKtor(
            httpClientEngine = MockEngine
        ) {
            engine {
                addHandler {
                    respond(
                        content = "error",
                        status = status,
                        headers = headersOf("Content-Type", "text/plain")
                    )
                }
            }
        }

        return try {
            SuspendLazy { client }.requestWithExceptionHandling {
                request("https://commonex.test/failure") {
                    method = HttpMethod.Get
                }.bodyAsText()
            }.let { result ->
                assertIs<NetworkResult.Error.Http.Client>(result).exception
            }
        } finally {
            client.close()
        }
    }
}
