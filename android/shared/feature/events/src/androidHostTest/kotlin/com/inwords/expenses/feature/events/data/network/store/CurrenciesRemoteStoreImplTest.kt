package com.inwords.expenses.feature.events.data.network.store

import com.inwords.expenses.core.network.HostConfig
import com.inwords.expenses.core.utils.IoResult
import com.inwords.expenses.feature.events.domain.store.remote.CurrenciesRemoteStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

internal class CurrenciesRemoteStoreImplTest {

    @Test
    fun `getCurrencies parses v3 payload and metadata`() = runTest {
        val client = HttpClient(MockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            engine {
                addHandler {
                    val json = """
                        {
                          "currencies": [
                            {"id": "srv-eur", "code": "EUR"},
                            {"id": "srv-usd", "code": "USD"}
                          ],
                          "exchangeRate": {
                            "EUR": 0.867713,
                            "USD": 1
                          }
                        }
                        """.trimIndent()
                    respond(
                        content = ByteReadChannel(json),
                        status = HttpStatusCode.OK,
                        headers = headersOf(
                            HttpHeaders.ContentType to listOf(ContentType.Application.Json.toString()),
                            HttpHeaders.ETag to listOf("\"rates-v1\""),
                        )
                    )
                }
            }
        }

        client.use { client ->
            val store = CurrenciesRemoteStoreImpl(
                client = { client },
                hostConfig = HostConfig(URLProtocol.HTTPS, "commonex.test"),
            )

            val result = store.getCurrencies(null)

            val success = assertIs<IoResult.Success<CurrenciesRemoteStore.GetCurrenciesResult>>(result)
            val modified = assertIs<CurrenciesRemoteStore.GetCurrenciesResult.Modified>(success.data)
            assertEquals("\"rates-v1\"", modified.eTag)
            assertEquals("srv-eur", modified.currencies.first().serverId)
            assertEquals("Euro", modified.currencies.first().name)
            assertEquals("0.8677", modified.currencies.first().rate.toStringExpanded())
        }
    }

    @Test
    fun `getCurrencies sends If-None-Match and handles 304`() = runTest {
        val client = HttpClient(MockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            engine {
                addHandler { request ->
                    assertEquals("\"cached-etag\"", request.headers[HttpHeaders.IfNoneMatch])
                    respond(
                        content = ByteReadChannel(""),
                        status = HttpStatusCode.NotModified,
                        headers = headersOf(HttpHeaders.ETag to listOf("\"cached-etag\"")),
                    )
                }
            }
        }

        client.use { client ->
            val store = CurrenciesRemoteStoreImpl(
                client = { client },
                hostConfig = HostConfig(URLProtocol.HTTPS, "commonex.test"),
            )

            val result = store.getCurrencies("\"cached-etag\"")

            val success = assertIs<IoResult.Success<CurrenciesRemoteStore.GetCurrenciesResult>>(result)
            val notModified = assertIs<CurrenciesRemoteStore.GetCurrenciesResult.NotModified>(success.data)
            assertEquals("\"cached-etag\"", notModified.eTag)
        }
    }
}
