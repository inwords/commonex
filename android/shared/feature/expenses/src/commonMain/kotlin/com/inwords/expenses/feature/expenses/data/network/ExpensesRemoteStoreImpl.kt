package com.inwords.expenses.feature.expenses.data.network

import com.inwords.expenses.core.network.HostConfig
import com.inwords.expenses.core.network.requestWithExceptionHandling
import com.inwords.expenses.core.network.toIoResult
import com.inwords.expenses.core.network.url
import com.inwords.expenses.core.observability.captureMessageIfNull
import com.inwords.expenses.core.utils.IoResult
import com.inwords.expenses.core.utils.SuspendLazy
import com.inwords.expenses.feature.events.domain.model.Currency
import com.inwords.expenses.feature.events.domain.model.Event
import com.inwords.expenses.feature.events.domain.model.Person
import com.inwords.expenses.feature.expenses.data.network.dto.CreateExpenseRequest
import com.inwords.expenses.feature.expenses.data.network.dto.ExpenseDto
import com.inwords.expenses.feature.expenses.data.network.dto.GetEventExpensesRequest
import com.inwords.expenses.feature.expenses.data.network.dto.SplitInformationDto
import com.inwords.expenses.feature.expenses.data.network.dto.SplitInformationRequest
import com.inwords.expenses.feature.expenses.domain.model.Expense
import com.inwords.expenses.feature.expenses.domain.model.ExpenseSplitWithPerson
import com.inwords.expenses.feature.expenses.domain.model.ExpenseType
import com.inwords.expenses.feature.expenses.domain.store.ExpensesRemoteStore
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

internal class ExpensesRemoteStoreImpl(
    private val client: SuspendLazy<HttpClient>,
    private val hostConfig: HostConfig,
) : ExpensesRemoteStore {

    override suspend fun getExpenses(
        event: Event,
        currencies: List<Currency>,
        persons: List<Person>
    ): IoResult<List<Expense>> {
        val serverId = event.serverId
            .captureMessageIfNull("ExpensesRemoteStore.getExpenses called for an unsynced event")
            ?: return IoResult.Error.Failure
        return client.requestWithExceptionHandling {
            post {
                url(hostConfig) { pathSegments = listOf("api", "v2", "user", "event", serverId, "expenses") }
                contentType(ContentType.Application.Json)
                setBody(GetEventExpensesRequest(pinCode = event.pinCode))
            }.body<List<ExpenseDto>>().mapNotNull { it.toExpense(localExpense = null, currencies, persons) }
        }.toIoResult()
    }

    override suspend fun addExpensesToEvent(
        event: Event,
        expenses: List<Expense>,
        currencies: List<Currency>,
        persons: List<Person>
    ): List<IoResult<Expense>> = coroutineScope {
        expenses.map { expense ->
            async { addExpenseToEvent(event, expense, currencies, persons) }
        }.awaitAll()
    }

    private suspend fun addExpenseToEvent(
        event: Event,
        expense: Expense,
        currencies: List<Currency>,
        persons: List<Person>
    ): IoResult<Expense> {
        val serverId = event.serverId
            .captureMessageIfNull("ExpensesRemoteStore.addExpenseToEvent called for an unsynced event")
            ?: return IoResult.Error.Failure
        val userWhoPaidId = expense.person.serverId
            .captureMessageIfNull("ExpensesRemoteStore.addExpenseToEvent found an expense payer without a server id") {
                setContext("event_server_id", serverId)
            }
            ?: return IoResult.Error.Failure
        val currencyServerId = expense.currency.serverId
            .captureMessageIfNull("ExpensesRemoteStore.addExpenseToEvent found an expense currency without a server id") {
                setContext("event_server_id", serverId)
                setContext("currency_code", expense.currency.code)
            }
            ?: return IoResult.Error.Failure
        return client.requestWithExceptionHandling {
            post {
                url(hostConfig) { pathSegments = listOf("api", "v2", "user", "event", serverId, "expense") }
                contentType(ContentType.Application.Json)
                setBody(
                    CreateExpenseRequest(
                        currencyId = currencyServerId,
                        expenseType = when (expense.expenseType) {
                            ExpenseType.Spending -> "expense"
                            ExpenseType.Replenishment -> "refund"
                        },
                        userWhoPaidId = userWhoPaidId,
                        splitInformation = expense.subjectExpenseSplitWithPersons.map { expenseSplitWithPerson ->
                            val splitInformationUserId = expenseSplitWithPerson.person.serverId
                                .captureMessageIfNull("ExpensesRemoteStore.addExpenseToEvent found an expense split person without a server id") {
                                    setContext("event_server_id", serverId)
                                }
                                ?: return IoResult.Error.Failure
                            SplitInformationRequest(
                                userId = splitInformationUserId,
                                amount = expenseSplitWithPerson.originalAmount.doubleValue(false),
                                exchangedAmount = expenseSplitWithPerson.exchangedAmount
                                    .takeIf { expense.isCustomRate }
                                    ?.doubleValue(false),
                            )
                        },
                        description = expense.description,
                        pinCode = event.pinCode
                    )
                )
            }.body<ExpenseDto>().toExpense(expense, currencies, persons)
        }.toIoResult()
    }

    private fun ExpenseDto.toExpense(
        localExpense: Expense?,
        currencies: List<Currency>,
        persons: List<Person>
    ): Expense? {
        val currency = currencies.firstOrNull { it.serverId == currencyId }
            .captureMessageIfNull("ExpensesRemoteStore failed to resolve a currency returned by the backend") {
                setContext("expense_server_id", id)
                setContext("currency_server_id", currencyId)
            }
            ?: return null
        return Expense(
            expenseId = localExpense?.expenseId ?: 0L,
            serverId = id,
            currency = currency,
            expenseType = when (expenseType) {
                "expense" -> ExpenseType.Spending
                "refund" -> ExpenseType.Replenishment
                else -> return null
            },
            person = persons.firstOrNull { it.serverId == userWhoPaidId } ?: return null,
            subjectExpenseSplitWithPersons = splitInformation.map { it.toDomain(persons) ?: return null },
            isCustomRate = isCustomRate,
            timestamp = createdAt,
            description = description,
        )
    }

    private fun SplitInformationDto.toDomain(persons: List<Person>): ExpenseSplitWithPerson? {
        val person = persons.firstOrNull { it.serverId == userId } ?: return null
        val originalAmount = BigDecimal.fromDouble(amount)
        val exchangedAmount = BigDecimal.fromDouble(exchangedAmount)
        return ExpenseSplitWithPerson(
            expenseSplitId = 0L,
            expenseId = 0L,
            person = Person(
                id = person.id,
                serverId = userId,
                name = person.name,
            ),
            originalAmount = originalAmount,
            exchangedAmount = exchangedAmount,
        )
    }

}
