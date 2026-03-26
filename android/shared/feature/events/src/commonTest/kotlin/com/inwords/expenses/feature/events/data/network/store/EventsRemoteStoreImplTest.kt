package com.inwords.expenses.feature.events.data.network.store

import com.inwords.expenses.core.network.DomainErrorCodes
import com.inwords.expenses.core.network.HostConfig
import com.inwords.expenses.core.utils.IoResult
import com.inwords.expenses.feature.events.domain.CreateShareTokenUseCase.CreateShareTokenResult
import com.inwords.expenses.feature.events.domain.model.Currency
import com.inwords.expenses.feature.events.domain.model.Event
import com.inwords.expenses.feature.events.domain.model.EventShareToken
import com.inwords.expenses.feature.events.domain.model.Person
import com.inwords.expenses.feature.events.domain.store.remote.EventsRemoteStore
import com.inwords.expenses.feature.events.domain.store.remote.EventsRemoteStore.EventNetworkError
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.time.Instant

internal class EventsRemoteStoreImplTest {

    @Test
    fun `getEventByAccessCode posts pinCode and maps event details`() = runTest {
        val client = createClient { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/api/v2/user/event/srv-event", request.url.encodedPath)

            val bodyJson = parseRequestBody(request.body)
            assertEquals("1234", bodyJson.getValue("pinCode").jsonPrimitive.content)
            assertFalse("token" in bodyJson)

            respondJson(eventResponseJson())
        }

        client.use { client ->
            val result = store(client).getEventByAccessCode(
                localId = 77L,
                serverId = "srv-event",
                pinCode = "1234",
                currencies = currencies(),
                localPersons = listOf(
                    Person(id = 101L, serverId = null, name = "Alice"),
                    Person(id = 202L, serverId = null, name = "Charlie"),
                ),
            )

            val event = assertIs<EventsRemoteStore.GetEventResult.Event<EventNetworkError.ByAccessCode>>(result)
            assertEquals(77L, event.event.event.id)
            assertEquals("srv-event", event.event.event.serverId)
            assertEquals("9999", event.event.event.pinCode)
            assertEquals(currencies().first().id, event.event.event.primaryCurrencyId)
            assertEquals("srv-eur", event.event.primaryCurrency.serverId)
            assertEquals(101L, event.event.persons[0].id)
            assertEquals(0L, event.event.persons[1].id)
            assertEquals("srv-user-1", event.event.persons[0].serverId)
            assertEquals("srv-user-2", event.event.persons[1].serverId)
            assertEquals("Alice", event.event.persons[0].name)
            assertEquals("Bob", event.event.persons[1].name)
        }
    }

    @Test
    fun `getEventByAccessCode maps errors by status`() = runTest {
        val cases = listOf(
            HttpStatusCode.Forbidden to EventNetworkError.InvalidAccessCode,
            HttpStatusCode.NotFound to EventNetworkError.NotFound,
            HttpStatusCode.Gone to EventNetworkError.Gone,
            HttpStatusCode.InternalServerError to EventNetworkError.OtherError,
        )

        cases.forEach { (status, expectedError) ->
            val client = createClient {
                respond(
                    content = "error",
                    status = status,
                    headers = headersOf(HttpHeaders.ContentType to listOf(ContentType.Text.Plain.toString())),
                )
            }

            client.use { httpClient ->
                val result = store(httpClient).getEventByAccessCode(
                    localId = 77L,
                    serverId = "srv-event",
                    pinCode = "1234",
                    currencies = currencies(),
                    localPersons = null,
                )

                val error = assertIs<EventsRemoteStore.GetEventResult.Error<EventNetworkError.ByAccessCode>>(result)
                assertEquals(expectedError, error.error)
            }
        }
    }

    @Test
    fun `getEventByToken posts token and maps event details`() = runTest {
        val client = createClient { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/api/v2/user/event/srv-event", request.url.encodedPath)

            val bodyJson = parseRequestBody(request.body)
            assertEquals("token-123", bodyJson.getValue("token").jsonPrimitive.content)
            assertFalse("pinCode" in bodyJson)

            respondJson(eventResponseJson())
        }

        client.use { client ->
            val result = store(client).getEventByToken(
                localId = 88L,
                serverId = "srv-event",
                token = "token-123",
                currencies = currencies(),
                localPersons = null,
            )

            val event = assertIs<EventsRemoteStore.GetEventResult.Event<EventNetworkError.ByToken>>(result)
            assertEquals(88L, event.event.event.id)
            assertEquals("srv-event", event.event.event.serverId)
            assertEquals("9999", event.event.event.pinCode)
            assertEquals("srv-eur", event.event.primaryCurrency.serverId)
        }
    }

    @Test
    fun `getEventByToken maps 401 domain codes`() = runTest {
        val cases = listOf(
            errorResponseJson(HttpStatusCode.Unauthorized, DomainErrorCodes.INVALID_TOKEN) to EventNetworkError.InvalidToken,
            errorResponseJson(HttpStatusCode.Unauthorized, DomainErrorCodes.TOKEN_EXPIRED) to EventNetworkError.TokenExpired,
            errorResponseJson(HttpStatusCode.Unauthorized, "UNKNOWN_CODE") to EventNetworkError.InvalidToken,
            errorResponseJsonWithoutCode(HttpStatusCode.Unauthorized) to EventNetworkError.InvalidToken,
        )

        cases.forEach { (payload, expectedError) ->
            val client = createClient {
                respond(
                    content = payload,
                    status = HttpStatusCode.Unauthorized,
                    headers = headersOf(HttpHeaders.ContentType to listOf(ContentType.Application.Json.toString())),
                )
            }

            client.use { httpClient ->
                val result = store(httpClient).getEventByToken(
                    localId = 88L,
                    serverId = "srv-event",
                    token = "token-123",
                    currencies = currencies(),
                    localPersons = null,
                )

                val error = assertIs<EventsRemoteStore.GetEventResult.Error<EventNetworkError.ByToken>>(result)
                assertEquals(expectedError, error.error)
            }
        }
    }

    @Test
    fun `getEventByToken maps non-401 errors by status`() = runTest {
        val cases = listOf(
            HttpStatusCode.Forbidden to EventNetworkError.InvalidToken,
            HttpStatusCode.NotFound to EventNetworkError.NotFound,
            HttpStatusCode.Gone to EventNetworkError.Gone,
            HttpStatusCode.InternalServerError to EventNetworkError.OtherError,
        )

        cases.forEach { (status, expectedError) ->
            val client = createClient {
                respond(
                    content = "error",
                    status = status,
                    headers = headersOf(HttpHeaders.ContentType to listOf(ContentType.Text.Plain.toString())),
                )
            }

            client.use { httpClient ->
                val result = store(httpClient).getEventByToken(
                    localId = 88L,
                    serverId = "srv-event",
                    token = "token-123",
                    currencies = currencies(),
                    localPersons = null,
                )

                val error = assertIs<EventsRemoteStore.GetEventResult.Error<EventNetworkError.ByToken>>(result)
                assertEquals(expectedError, error.error)
            }
        }
    }

    @Test
    fun `createEvent posts event payload and maps response`() = runTest {
        val localEvent = Event(
            id = 90L,
            serverId = null,
            name = "Trip",
            pinCode = "5555",
            primaryCurrencyId = currencies().first().id,
        )
        val localPersons = listOf(
            Person(id = 11L, serverId = null, name = "Alice"),
            Person(id = 12L, serverId = null, name = "Bob"),
        )
        val client = createClient { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/api/user/event", request.url.encodedPath)

            val bodyJson = parseRequestBody(request.body)
            assertEquals("Trip", bodyJson.getValue("name").jsonPrimitive.content)
            assertEquals("srv-eur", bodyJson.getValue("currencyId").jsonPrimitive.content)
            assertEquals("5555", bodyJson.getValue("pinCode").jsonPrimitive.content)
            assertEquals(listOf("Alice", "Bob"), bodyJson.getValue("users").jsonArray.map { it.jsonObject.getValue("name").jsonPrimitive.content })

            respondJson(eventResponseJson())
        }

        client.use { client ->
            val result = store(client).createEvent(
                event = localEvent,
                currencies = currencies(),
                primaryCurrencyServerId = "srv-eur",
                localPersons = localPersons,
            )

            val success = assertIs<IoResult.Success<com.inwords.expenses.feature.events.domain.model.EventDetails>>(result)
            assertEquals(90L, success.data.event.id)
            assertEquals("srv-event", success.data.event.serverId)
            assertEquals("9999", success.data.event.pinCode)
            assertEquals(11L, success.data.persons[0].id)
            assertEquals(12L, success.data.persons[1].id)
            assertEquals("srv-user-1", success.data.persons[0].serverId)
            assertEquals("srv-user-2", success.data.persons[1].serverId)
            assertEquals("srv-eur", success.data.primaryCurrency.serverId)
        }
    }

    @Test
    fun `deleteEvent sends pinCode and returns deleted`() = runTest {
        val client = createClient { request ->
            assertEquals(HttpMethod.Delete, request.method)
            assertEquals("/api/user/event/srv-event", request.url.encodedPath)

            val bodyJson = parseRequestBody(request.body)
            assertEquals("1234", bodyJson.getValue("pinCode").jsonPrimitive.content)

            respond(
                content = "",
                status = HttpStatusCode.NoContent,
            )
        }

        client.use { client ->
            val result = store(client).deleteEvent(serverId = "srv-event", pinCode = "1234")

            assertIs<EventsRemoteStore.DeleteEventResult.Deleted>(result)
        }
    }

    @Test
    fun `deleteEvent maps errors by status`() = runTest {
        val cases = listOf(
            HttpStatusCode.Forbidden to EventNetworkError.InvalidAccessCode,
            HttpStatusCode.NotFound to EventNetworkError.NotFound,
            HttpStatusCode.Gone to EventNetworkError.Gone,
            HttpStatusCode.InternalServerError to EventNetworkError.OtherError,
        )

        cases.forEach { (status, expectedError) ->
            val client = createClient {
                respond(
                    content = "error",
                    status = status,
                    headers = headersOf(HttpHeaders.ContentType to listOf(ContentType.Text.Plain.toString())),
                )
            }

            client.use { httpClient ->
                val result = store(httpClient).deleteEvent(serverId = "srv-event", pinCode = "1234")

                val error = assertIs<EventsRemoteStore.DeleteEventResult.Error>(result)
                assertEquals(expectedError, error.error)
            }
        }
    }

    @Test
    fun `addPersonsToEvent posts users and maps returned ids by index`() = runTest {
        val localPersons = listOf(
            Person(id = 31L, serverId = null, name = "Alice"),
            Person(id = 32L, serverId = null, name = "Bob"),
        )
        val client = createClient { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/api/v2/user/event/srv-event/users", request.url.encodedPath)

            val bodyJson = parseRequestBody(request.body)
            assertEquals("1234", bodyJson.getValue("pinCode").jsonPrimitive.content)
            assertEquals(listOf("Alice", "Bob"), bodyJson.getValue("users").jsonArray.map { it.jsonObject.getValue("name").jsonPrimitive.content })

            respondJson(
                """
                    [
                      {"name":"Server Alice","eventId":"srv-event","id":"srv-user-1"},
                      {"name":"Server Bob","eventId":"srv-event","id":"srv-user-2"}
                    ]
                """.trimIndent()
            )
        }

        client.use { client ->
            val result = store(client).addPersonsToEvent(
                eventServerId = "srv-event",
                pinCode = "1234",
                localPersons = localPersons,
            )

            val success = assertIs<IoResult.Success<List<Person>>>(result)
            assertEquals(31L, success.data[0].id)
            assertEquals(32L, success.data[1].id)
            assertEquals("srv-user-1", success.data[0].serverId)
            assertEquals("srv-user-2", success.data[1].serverId)
            assertEquals("Server Alice", success.data[0].name)
            assertEquals("Server Bob", success.data[1].name)
        }
    }

    @Test
    fun `createEventShareToken posts pinCode and maps response`() = runTest {
        val expiresAt = Instant.parse("2026-03-01T10:00:00Z")
        val client = createClient { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/api/v2/user/event/srv-event/share-token", request.url.encodedPath)

            val bodyJson = parseRequestBody(request.body)
            assertEquals("1234", bodyJson.getValue("pinCode").jsonPrimitive.content)

            respondJson(
                """
                    {
                      "token": "share-token-1",
                      "expiresAt": "$expiresAt"
                    }
                """.trimIndent()
            )
        }

        client.use { client ->
            val result = store(client).createEventShareToken(
                eventServerId = "srv-event",
                pinCode = "1234",
            )

            val created = assertIs<CreateShareTokenResult.Created>(result)
            assertEquals(EventShareToken(token = "share-token-1", expiresAt = expiresAt), created.token)
        }
    }

    @Test
    fun `createEventShareToken maps remote failures`() = runTest {
        val client = createClient {
            respond(
                content = "error",
                status = HttpStatusCode.InternalServerError,
                headers = headersOf(HttpHeaders.ContentType to listOf(ContentType.Text.Plain.toString())),
            )
        }

        client.use { client ->
            val result = store(client).createEventShareToken(
                eventServerId = "srv-event",
                pinCode = "1234",
            )

            assertIs<CreateShareTokenResult.RemoteFailed>(result)
        }
    }

    private fun createClient(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): HttpClient {
        return HttpClient(MockEngine) {
            expectSuccess = true
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            engine {
                addHandler(handler)
            }
        }
    }

    private fun store(client: HttpClient): EventsRemoteStoreImpl {
        return EventsRemoteStoreImpl(
            client = { client },
            hostConfig = HostConfig(URLProtocol.HTTPS, "commonex.test"),
        )
    }

    private fun parseRequestBody(body: Any) = Json.parseToJsonElement(extractRequestBody(body)).jsonObject

    private fun extractRequestBody(body: Any): String {
        val content = body as? OutgoingContent.ByteArrayContent
            ?: error("Unexpected request body type: ${body::class}")
        return content.bytes().decodeToString()
    }

    private fun MockRequestHandleScope.respondJson(content: String): HttpResponseData {
        return respond(
            content = ByteReadChannel(content),
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType to listOf(ContentType.Application.Json.toString())),
        )
    }

    private fun currencies(): List<Currency> {
        return listOf(
            Currency(
                id = 1L,
                serverId = "srv-eur",
                code = "EUR",
                name = "Euro",
                rate = com.ionspin.kotlin.bignum.decimal.BigDecimal.parseString("0.8677"),
            ),
            Currency(
                id = 2L,
                serverId = "srv-usd",
                code = "USD",
                name = "US Dollar",
                rate = com.ionspin.kotlin.bignum.decimal.BigDecimal.parseString("1"),
            ),
        )
    }

    private fun eventResponseJson(): String {
        return """
            {
              "id": "srv-event",
              "name": "Trip",
              "currencyId": "srv-eur",
              "pinCode": "9999",
              "users": [
                {"name":"Alice","eventId":"srv-event","id":"srv-user-1"},
                {"name":"Bob","eventId":"srv-event","id":"srv-user-2"}
              ]
            }
        """.trimIndent()
    }

    private fun errorResponseJson(status: HttpStatusCode, code: String): String {
        return """
            {
              "statusCode": ${status.value},
              "code": "$code",
              "message": "failure"
            }
        """.trimIndent()
    }

    private fun errorResponseJsonWithoutCode(status: HttpStatusCode): String {
        return """
            {
              "statusCode": ${status.value},
              "message": "failure"
            }
        """.trimIndent()
    }
}
