package com.inwords.expenses.feature.expenses.data.network

import com.inwords.expenses.core.network.HostConfig
import com.inwords.expenses.core.network.IDEMPOTENCY_KEY_HEADER
import com.inwords.expenses.core.utils.IoResult
import com.inwords.expenses.feature.events.domain.model.Currency
import com.inwords.expenses.feature.events.domain.model.Event
import com.inwords.expenses.feature.events.domain.model.Person
import com.inwords.expenses.feature.expenses.domain.model.Expense
import com.inwords.expenses.feature.expenses.domain.model.ExpenseSplitWithPerson
import com.inwords.expenses.feature.expenses.domain.model.ExpenseType
import com.inwords.expenses.feature.expenses.domain.store.ExpensePushItem
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant

internal class ExpensesRemoteStoreImplTest {

    @Test
    fun `addExpensesToEvent omits exchangedAmount when expense uses automatic rate`() = runTest {
        val client = HttpClient(MockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            engine {
                addHandler { request ->
                    assertEquals(HttpMethod.Post, request.method)
                    assertEquals("/api/v2/user/event/srv-event/expense", request.url.encodedPath)
                    assertEquals("expense-add-key-1", request.headers[IDEMPOTENCY_KEY_HEADER])

                    val bodyText = extractRequestBody(request.body)
                    assertCreatePayload(bodyText, expenseType = "expense", customRate = false)

                    respond(
                        content = ByteReadChannel(successResponseJson()),
                        status = HttpStatusCode.Created,
                        headers = headersOf(
                            HttpHeaders.ContentType to listOf(ContentType.Application.Json.toString())
                        ),
                    )
                }
            }
        }

        client.use { client ->
            val result = ExpensesRemoteStoreImpl(
                client = { client },
                hostConfig = HostConfig(URLProtocol.HTTPS, "commonex.test"),
            ).addExpensesToEvent(
                event = event(),
                expenses = listOf(
                    ExpensePushItem(
                        expense = expense(isCustomRate = false),
                        idempotencyKey = "expense-add-key-1",
                    )
                ),
                currencies = listOf(currency()),
                persons = listOf(person(), subjectPerson()),
            )

            assertEquals(expectedRemoteExpense(isCustomRate = false), assertIs<IoResult.Success<Expense>>(result.single()).data)
        }
    }

    @Test
    fun `addExpensesToEvent includes exchangedAmount when expense uses custom rate`() = runTest {
        val client = HttpClient(MockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            engine {
                addHandler { request ->
                    assertEquals("expense-add-key-1", request.headers[IDEMPOTENCY_KEY_HEADER])

                    val bodyText = extractRequestBody(request.body)
                    assertCreatePayload(bodyText, expenseType = "expense", customRate = true)

                    respond(
                        content = ByteReadChannel(successResponseJson(isCustomRate = true)),
                        status = HttpStatusCode.Created,
                        headers = headersOf(
                            HttpHeaders.ContentType to listOf(ContentType.Application.Json.toString())
                        ),
                    )
                }
            }
        }

        client.use { client ->
            val result = ExpensesRemoteStoreImpl(
                client = { client },
                hostConfig = HostConfig(URLProtocol.HTTPS, "commonex.test"),
            ).addExpensesToEvent(
                event = event(),
                expenses = listOf(
                    ExpensePushItem(
                        expense = expense(isCustomRate = true),
                        idempotencyKey = "expense-add-key-1",
                    )
                ),
                currencies = listOf(currency()),
                persons = listOf(person(), subjectPerson()),
            )

            assertEquals(expectedRemoteExpense(isCustomRate = true), assertIs<IoResult.Success<Expense>>(result.single()).data)
        }
    }

    @Test
    fun `getExpenses maps remote identity amounts and custom rate`() = runTest {
        val client = HttpClient(MockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            engine {
                addHandler {
                    respond(
                        content = ByteReadChannel("[${successResponseJson(isCustomRate = true)}]"),
                        status = HttpStatusCode.OK,
                        headers = headersOf(
                            HttpHeaders.ContentType to listOf(ContentType.Application.Json.toString())
                        ),
                    )
                }
            }
        }

        client.use { client ->
            val result = ExpensesRemoteStoreImpl(
                client = { client },
                hostConfig = HostConfig(URLProtocol.HTTPS, "commonex.test"),
            ).getExpenses(
                event = event(),
                currencies = listOf(currency()),
                persons = listOf(person(), subjectPerson()),
            )

            assertEquals(
                expectedRemoteExpense(isCustomRate = true).copy(expenseId = 0L, clientCreateId = "server:srv-expense"),
                assertIs<IoResult.Success<List<Expense>>>(result).data.single(),
            )
        }
    }

    @Test
    fun `addExpensesToEvent sends and maps refund type`() = runTest {
        val client = responseClient(successResponseJson().replace("\"expense\"", "\"refund\"")) { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/api/v2/user/event/srv-event/expense", request.url.encodedPath)
            assertEquals("refund-add-key", request.headers[IDEMPOTENCY_KEY_HEADER])
            assertCreatePayload(extractRequestBody(request.body), expenseType = "refund", customRate = false)
        }

        client.use {
            val result = ExpensesRemoteStoreImpl(
                client = { client },
                hostConfig = HostConfig(URLProtocol.HTTPS, "commonex.test"),
            ).addExpensesToEvent(
                event = event(),
                expenses = listOf(
                    ExpensePushItem(expense(false).copy(expenseType = ExpenseType.Replenishment), "refund-add-key")
                ),
                currencies = listOf(currency()),
                persons = listOf(person(), subjectPerson()),
            )

            assertEquals(
                expectedRemoteExpense(false).copy(expenseType = ExpenseType.Replenishment),
                assertIs<IoResult.Success<Expense>>(result.single()).data,
            )
        }
    }

    @Test
    fun `getExpenses skips unmappable records while retaining valid siblings`() = runTest {
        val invalidFields = listOf(
            "currencyId" to "missing-currency",
            "userWhoPaidId" to "missing-payer",
            "expenseType" to "unknown-type",
        )
        val validJson = Json.parseToJsonElement(successResponseJson()).jsonObject
        val expected = expectedRemoteExpense(false).copy(expenseId = 0L, clientCreateId = "server:srv-expense")

        for ((field, value) in invalidFields) {
            val invalidJson = JsonObject(validJson + (field to JsonPrimitive(value)))
            val response = JsonArray(listOf(invalidJson, validJson)).toString()

            val result = pullExpenses(response)

            assertEquals(listOf(expected), assertIs<IoResult.Success<List<Expense>>>(result).data, field)
        }
    }

    @Test
    fun `getExpenses rejects the whole expense when a later split person is unknown`() = runTest {
        val validJson = Json.parseToJsonElement(successResponseJson()).jsonObject
        val validSplit = validJson.getValue("splitInformation").jsonArray.single().jsonObject
        val missingPersonSplit = JsonObject(validSplit + ("userId" to JsonPrimitive("missing-person")))
        val invalidExpense = JsonObject(
            validJson + ("splitInformation" to JsonArray(listOf(validSplit, missingPersonSplit)))
        )

        val result = pullExpenses(JsonArray(listOf(invalidExpense, validJson)).toString())

        assertEquals(
            listOf(expectedRemoteExpense(false).copy(expenseId = 0L, clientCreateId = "server:srv-expense")),
            assertIs<IoResult.Success<List<Expense>>>(result).data,
        )
    }

    @Test
    fun `unsynced expense prerequisites fail before sending HTTP requests`() = runTest {
        val validExpense = expense(false)
        val validSplit = validExpense.subjectExpenseSplitWithPersons.single()
        val cases = listOf(
            event().copy(serverId = null) to validExpense,
            event() to validExpense.copy(person = person().copy(serverId = null)),
            event() to validExpense.copy(currency = currency().copy(serverId = null)),
            event() to validExpense.copy(
                subjectExpenseSplitWithPersons = listOf(validSplit, validSplit.copy(person = subjectPerson().copy(serverId = null)))
            ),
        )
        responseClient(successResponseJson()) { error("Unsynced records must not produce HTTP requests") }.use { client ->
            val store = ExpensesRemoteStoreImpl({ client }, HostConfig(URLProtocol.HTTPS, "commonex.test"))
            assertEquals(
                IoResult.Error.Failure,
                store.getExpenses(event().copy(serverId = null), listOf(currency()), listOf(person(), subjectPerson())),
            )
            cases.forEach { (event, expense) ->
                val result = store.addExpensesToEvent(
                    event,
                    listOf(ExpensePushItem(expense, "pending-expense-key")),
                    listOf(currency()),
                    listOf(person(), subjectPerson()),
                )

                assertEquals(listOf(IoResult.Error.Failure), result)
            }
        }
    }

    @Test
    fun `concurrent expense uploads preserve input alignment across retryable and successful responses`() = runTest {
        val firstExpense = expense(false)
        val secondExpense = firstExpense.copy(expenseId = 2L, clientCreateId = "expense-client-2")
        val secondResponseReady = CompletableDeferred<Unit>()
        val client = HttpClient(MockEngine) {
            expectSuccess = true
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            engine {
                addHandler { request ->
                    assertEquals(HttpMethod.Post, request.method)
                    assertEquals("/api/v2/user/event/srv-event/expense", request.url.encodedPath)
                    when (request.headers[IDEMPOTENCY_KEY_HEADER]) {
                        "first-key" -> {
                            secondResponseReady.await()
                            respond("Conflict", HttpStatusCode.Conflict)
                        }

                        "second-key" -> {
                            val response = respond(
                                successResponseJson(),
                                HttpStatusCode.Created,
                                headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                            )
                            secondResponseReady.complete(Unit)
                            response
                        }

                        else -> error("Every expense must retain its idempotency key")
                    }
                }
            }
        }

        client.use {
            val result = ExpensesRemoteStoreImpl({ client }, HostConfig(URLProtocol.HTTPS, "commonex.test"))
                .addExpensesToEvent(
                    event(),
                    listOf(ExpensePushItem(firstExpense, "first-key"), ExpensePushItem(secondExpense, "second-key")),
                    listOf(currency()),
                    listOf(person(), subjectPerson()),
                )

            assertEquals(2, result.size)
            assertEquals(IoResult.Error.Retry, result[0])
            assertEquals(
                expectedRemoteExpense(false).copy(expenseId = 2L, clientCreateId = "expense-client-2"),
                assertIs<IoResult.Success<Expense>>(result[1]).data,
            )
        }
    }

    private suspend fun pullExpenses(response: String): IoResult<List<Expense>> = responseClient(response).use { client ->
        ExpensesRemoteStoreImpl(
            client = { client },
            hostConfig = HostConfig(URLProtocol.HTTPS, "commonex.test"),
        ).getExpenses(event(), listOf(currency()), listOf(person(), subjectPerson()))
    }

    private fun responseClient(response: String, checkRequest: (HttpRequestData) -> Unit = {}): HttpClient =
        HttpClient(MockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            engine {
                addHandler { request ->
                    checkRequest(request)
                    respond(
                        content = ByteReadChannel(response),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            }
        }

    private fun assertCreatePayload(body: String, expenseType: String, customRate: Boolean) {
        val exchangedAmount = if (customRate) ", \"exchangedAmount\": 12.5" else ""
        val expected = """
            {
              "description": "Dinner",
              "userWhoPaidId": "srv-person",
              "currencyId": "srv-eur",
              "expenseType": "$expenseType",
              "pinCode": "1234",
              "splitInformation": [{"userId": "srv-subject", "amount": 10.0$exchangedAmount}]
            }
        """.trimIndent()
        assertEquals(Json.parseToJsonElement(expected), Json.parseToJsonElement(body))
    }

    private fun extractRequestBody(body: Any): String {
        val content = body as? OutgoingContent.ByteArrayContent
            ?: error("Unexpected request body type: ${body::class}")
        return content.bytes().decodeToString()
    }

    private fun event(): Event {
        return Event(
            id = 1L,
            serverId = "srv-event",
            name = "Trip",
            pinCode = "1234",
            primaryCurrencyId = 1L,
            clientCreateId = "event-client-1",
        )
    }

    private fun currency(): Currency {
        return Currency(
            id = 1L,
            serverId = "srv-eur",
            code = "EUR",
            name = "Euro",
            rate = BigDecimal.parseString("0.85"),
        )
    }

    private fun person(): Person {
        return Person(
            id = 1L,
            serverId = "srv-person",
            name = "Alice",
            clientCreateId = "person-client-1",
        )
    }

    private fun expense(isCustomRate: Boolean): Expense {
        val person = person()
        val currency = currency()
        return Expense(
            expenseId = 1L,
            serverId = null,
            currency = currency,
            expenseType = ExpenseType.Spending,
            person = person,
            subjectExpenseSplitWithPersons = listOf(
                ExpenseSplitWithPerson(
                    expenseSplitId = 10L,
                    expenseId = 1L,
                    person = subjectPerson(),
                    originalAmount = BigDecimal.parseString("10"),
                    exchangedAmount = BigDecimal.parseString("12.5"),
                )
            ),
            isCustomRate = isCustomRate,
            timestamp = Instant.parse("2026-01-01T00:00:00Z"),
            description = "Dinner",
            clientCreateId = "expense-client-1",
        )
    }

    private fun subjectPerson(): Person = Person(
        id = 2L,
        serverId = "srv-subject",
        name = "Bob",
        clientCreateId = "person-client-2",
    )

    private fun expectedRemoteExpense(isCustomRate: Boolean): Expense = expense(isCustomRate).copy(
        serverId = "srv-expense",
        description = "Dinner confirmed",
        timestamp = Instant.parse("2026-01-02T03:04:05Z"),
        subjectExpenseSplitWithPersons = listOf(
            ExpenseSplitWithPerson(
                expenseSplitId = 0L,
                expenseId = 0L,
                person = subjectPerson(),
                originalAmount = BigDecimal.parseString("11.25"),
                exchangedAmount = BigDecimal.parseString("13.75"),
            )
        ),
    )

    private fun successResponseJson(isCustomRate: Boolean = false): String {
        return """
            {
              "id": "srv-expense",
              "eventId": "srv-event",
              "description": "Dinner confirmed",
              "userWhoPaidId": "srv-person",
              "currencyId": "srv-eur",
              "expenseType": "expense",
              "splitInformation": [
                {
                  "userId": "srv-subject",
                  "amount": 11.25,
                  "exchangedAmount": 13.75
                }
              ],
              "isCustomRate": $isCustomRate,
              "createdAt": "2026-01-02T03:04:05Z"
            }
        """.trimIndent()
    }
}
