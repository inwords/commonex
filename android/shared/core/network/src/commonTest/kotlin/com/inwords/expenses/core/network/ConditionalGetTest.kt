package com.inwords.expenses.core.network

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

internal class ConditionalGetTest {

    @Test
    fun `getConditional sends If-None-Match and parses 200 response`() = runTest {
        val client = createKtor(MockEngine) {
            engine {
                addHandler { request ->
                    assertEquals(HttpMethod.Get, request.method)
                    assertEquals("\"rates-v1\"", request.headers[HttpHeaders.IfNoneMatch])
                    respond(
                        content = """{"value":"ok"}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(
                            HttpHeaders.ContentType to listOf(ContentType.Application.Json.toString()),
                            HttpHeaders.ETag to listOf("\"rates-v2\""),
                        ),
                    )
                }
            }
        }

        client.use { httpClient ->
            val result = httpClient.getConditional<JsonObject>("\"rates-v1\"") {
                url {
                    protocol = URLProtocol.HTTPS
                    host = "commonex.test"
                    pathSegments = listOf("api", "v3", "user", "currencies", "all")
                }
            }

            val modified = assertIs<ConditionalGetResult.Modified<JsonObject>>(result)
            assertEquals("ok", modified.body["value"]?.jsonPrimitive?.content)
            assertEquals("\"rates-v2\"", modified.eTag)
        }
    }

    @Test
    fun `getConditional returns not modified for 304`() = runTest {
        val client = createKtor(MockEngine) {
            engine {
                addHandler {
                    respond(
                        content = "",
                        status = HttpStatusCode.NotModified,
                        headers = headersOf(HttpHeaders.ETag to listOf("\"rates-v1\"")),
                    )
                }
            }
        }

        client.use { httpClient ->
            val result = httpClient.getConditional<JsonObject>("\"rates-v1\"") {
                url {
                    protocol = URLProtocol.HTTPS
                    host = "commonex.test"
                    pathSegments = listOf("api", "v3", "user", "currencies", "all")
                }
            }

            val notModified = assertIs<ConditionalGetResult.NotModified<JsonObject>>(result)
            assertEquals("\"rates-v1\"", notModified.eTag)
        }
    }

    @Test
    fun `getConditional preserves request etag when 304 omits etag`() = runTest {
        val client = createKtor(MockEngine) {
            engine {
                addHandler {
                    respond(
                        content = "",
                        status = HttpStatusCode.NotModified,
                    )
                }
            }
        }

        client.use { httpClient ->
            val result = httpClient.getConditional<JsonObject>("\"rates-v1\"") {
                url {
                    protocol = URLProtocol.HTTPS
                    host = "commonex.test"
                    pathSegments = listOf("api", "v3", "user", "currencies", "all")
                }
            }

            val notModified = assertIs<ConditionalGetResult.NotModified<JsonObject>>(result)
            assertEquals("\"rates-v1\"", notModified.eTag)
        }
    }

    @Test
    fun `getConditional throws client exception for non cache status`() = runTest {
        val client = createKtor(MockEngine) {
            engine {
                addHandler {
                    respond(
                        content = "error",
                        status = HttpStatusCode.NotFound,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Plain.toString()),
                    )
                }
            }
        }

        client.use { httpClient ->
            assertFailsWith<ClientRequestException> {
                httpClient.getConditional<JsonObject>(null) {
                    url {
                        protocol = URLProtocol.HTTPS
                        host = "commonex.test"
                        pathSegments = listOf("api", "v3", "user", "currencies", "all")
                    }
                }
            }
        }
    }
}
