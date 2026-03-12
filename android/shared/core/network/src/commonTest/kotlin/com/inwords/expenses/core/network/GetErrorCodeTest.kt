package com.inwords.expenses.core.network

import com.inwords.expenses.core.network.dto.ErrorResponseDto
import com.inwords.expenses.core.utils.SuspendLazy
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Validates that [getErrorCode] correctly deserializes error response bodies to [ErrorResponseDto]
 * via kotlinx.serialization (requires the serialization compiler plugin on this module).
 */
internal class GetErrorCodeTest {

    @Test
    fun `ErrorResponseDto deserializes from JSON when serializer is generated`() {
        val json = """{"statusCode":404,"code":"EVENT_NOT_FOUND","message":"Event not found"}"""

        @Suppress("JSON_FORMAT_REDUNDANT")
        val dto = Json { ignoreUnknownKeys = true }.decodeFromString<ErrorResponseDto>(json)
        assertEquals(404, dto.statusCode)
        assertEquals("EVENT_NOT_FOUND", dto.code)
        assertEquals("Event not found", dto.message)
    }

    @Test
    fun `getErrorCode returns code when response is JSON matching ErrorResponseDto`() = runTest {
        val client = createKtor(MockEngine) {
            engine {
                addHandler {
                    respond(
                        content = """{"statusCode":404,"code":"EVENT_NOT_FOUND","message":"Event not found"}""",
                        status = HttpStatusCode.NotFound,
                        headers = headersOf(
                            HttpHeaders.ContentType to listOf(ContentType.Application.Json.toString()),
                        ),
                    )
                }
            }
        }

        client.use { httpClient ->
            val result = SuspendLazy { httpClient }.requestWithExceptionHandling {
                httpClient.get("https://commonex.test/api/event/by-token").bodyAsText()
            }
            val clientError = assertIs<NetworkResult.Error.Http.Client>(result)
            assertEquals("EVENT_NOT_FOUND", clientError.getErrorCode())
        }
    }

    @Test
    fun `getErrorCode returns null when response body is not JSON`() = runTest {
        val client = createKtor(MockEngine) {
            engine {
                addHandler {
                    respond(
                        content = "plain text error",
                        status = HttpStatusCode.BadRequest,
                        headers = headersOf(HttpHeaders.ContentType to listOf(ContentType.Text.Plain.toString())),
                    )
                }
            }
        }

        client.use { httpClient ->
            val result = SuspendLazy { httpClient }.requestWithExceptionHandling {
                httpClient.get("https://commonex.test/api/fail").bodyAsText()
            }
            val clientError = assertIs<NetworkResult.Error.Http.Client>(result)
            assertNull(clientError.getErrorCode())
        }
    }

    @Test
    fun `getErrorCode returns code when JSON has only code field`() = runTest {
        val client = createKtor(MockEngine) {
            engine {
                addHandler {
                    respond(
                        content = """{"statusCode":410,"code":"TOKEN_EXPIRED","message":"Token expired"}""",
                        status = HttpStatusCode.Gone,
                        headers = headersOf(
                            HttpHeaders.ContentType to listOf(ContentType.Application.Json.toString()),
                        ),
                    )
                }
            }
        }

        client.use { httpClient ->
            val result = SuspendLazy { httpClient }.requestWithExceptionHandling {
                httpClient.get("https://commonex.test/api/event/token").bodyAsText()
            }
            val clientError = assertIs<NetworkResult.Error.Http.Client>(result)
            assertEquals("TOKEN_EXPIRED", clientError.getErrorCode())
        }
    }
}
