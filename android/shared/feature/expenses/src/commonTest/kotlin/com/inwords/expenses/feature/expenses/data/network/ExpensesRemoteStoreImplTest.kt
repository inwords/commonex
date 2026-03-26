package com.inwords.expenses.feature.expenses.data.network

import com.inwords.expenses.core.network.HostConfig
import com.inwords.expenses.core.utils.IoResult
import com.inwords.expenses.feature.events.domain.model.Currency
import com.inwords.expenses.feature.events.domain.model.Event
import com.inwords.expenses.feature.events.domain.model.Person
import com.inwords.expenses.feature.expenses.domain.model.Expense
import com.inwords.expenses.feature.expenses.domain.model.ExpenseSplitWithPerson
import com.inwords.expenses.feature.expenses.domain.model.ExpenseType
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
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
import kotlin.test.assertTrue
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
                    val bodyText = extractRequestBody(request.body)
                    val bodyJson = Json.parseToJsonElement(bodyText).jsonObject
                    val splitJson = bodyJson.getValue("splitInformation").jsonArray.single().jsonObject

                    assertFalse("exchangedAmount" in splitJson)

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
                expenses = listOf(expense(isCustomRate = false)),
                currencies = listOf(currency()),
                persons = listOf(person()),
            )

            assertIs<IoResult.Success<Expense>>(result.single())
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
                    val bodyText = extractRequestBody(request.body)
                    val bodyJson = Json.parseToJsonElement(bodyText).jsonObject
                    val splitJson = bodyJson.getValue("splitInformation").jsonArray.single().jsonObject

                    assertTrue("exchangedAmount" in splitJson)
                    assertEquals("12.5", splitJson.getValue("exchangedAmount").jsonPrimitive.content)

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
                expenses = listOf(expense(isCustomRate = true)),
                currencies = listOf(currency()),
                persons = listOf(person()),
            )

            assertIs<IoResult.Success<Expense>>(result.single())
        }
    }

    @Test
    fun `getExpenses preserves isCustomRate from remote response`() = runTest {
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
                persons = listOf(person()),
            )

            assertTrue(assertIs<IoResult.Success<List<Expense>>>(result).data.single().isCustomRate)
        }
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
                    person = person,
                    originalAmount = BigDecimal.parseString("10"),
                    exchangedAmount = BigDecimal.parseString("12.5"),
                )
            ),
            isCustomRate = isCustomRate,
            timestamp = Instant.parse("2026-01-01T00:00:00Z"),
            description = "Dinner",
        )
    }

    private fun successResponseJson(isCustomRate: Boolean = false): String {
        return """
            {
              "id": "srv-expense",
              "eventId": "srv-event",
              "description": "Dinner",
              "userWhoPaidId": "srv-person",
              "currencyId": "srv-eur",
              "expenseType": "expense",
              "splitInformation": [
                {
                  "userId": "srv-person",
                  "amount": 10.0,
                  "exchangedAmount": 12.5
                }
              ],
              "isCustomRate": $isCustomRate,
              "createdAt": "2026-01-01T00:00:00Z"
            }
        """.trimIndent()
    }
}
