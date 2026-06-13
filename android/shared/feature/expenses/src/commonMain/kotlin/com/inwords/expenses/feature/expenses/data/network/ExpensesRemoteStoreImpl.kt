package com.inwords.expenses.feature.expenses.data.network

import com.inwords.expenses.core.network.DomainErrorCodes
import com.inwords.expenses.core.network.HostConfig
import com.inwords.expenses.core.network.NetworkResult
import com.inwords.expenses.core.network.getErrorCode
import com.inwords.expenses.core.network.idempotencyKey
import com.inwords.expenses.core.network.requestWithExceptionHandling
import com.inwords.expenses.core.network.toIoResult
import com.inwords.expenses.core.network.url
import com.inwords.expenses.core.observability.captureMessageIfNull
import com.inwords.expenses.core.utils.ClientCreateId
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
import com.inwords.expenses.feature.expenses.domain.correctedExpenseId
import com.inwords.expenses.feature.expenses.domain.model.Expense
import com.inwords.expenses.feature.expenses.domain.model.ExpenseSplitWithPerson
import com.inwords.expenses.feature.expenses.domain.model.ExpenseType
import com.inwords.expenses.feature.expenses.domain.store.ExpensePullItem
import com.inwords.expenses.feature.expenses.domain.store.ExpensePushItem
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
    ): IoResult<List<ExpensePullItem>> {
        val serverId = event.serverId
            .captureMessageIfNull("ExpensesRemoteStore.getExpenses called for an unsynced event")
            ?: return IoResult.Error.Failure
        return client.requestWithExceptionHandling {
            post {
                url(hostConfig) { pathSegments = listOf("api", "v2", "user", "event", serverId, "expenses") }
                contentType(ContentType.Application.Json)
                setBody(GetEventExpensesRequest(pinCode = event.pinCode))
            }.body<List<ExpenseDto>>().mapNotNull { it.toPullItem(currencies, persons) }
        }.toIoResult()
    }

    override suspend fun addExpensesToEvent(
        event: Event,
        expenses: List<ExpensePushItem>,
        allExpenses: List<Expense>,
        currencies: List<Currency>,
        persons: List<Person>,
    ): List<IoResult<Expense>> = coroutineScope {
        val allExpensesById = allExpenses.associateBy { it.expenseId }
        expenses.map { expensePushItem ->
            async {
                addExpenseToEvent(
                    event = event,
                    expense = expensePushItem.expense,
                    allExpensesById = allExpensesById,
                    currencies = currencies,
                    persons = persons,
                    idempotencyKey = expensePushItem.idempotencyKey,
                )
            }
        }.awaitAll()
    }

    private suspend fun addExpenseToEvent(
        event: Event,
        expense: Expense,
        allExpensesById: Map<Long, Expense>,
        currencies: List<Currency>,
        persons: List<Person>,
        idempotencyKey: String,
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
        val revertsExpenseId = mapCorrectionServerId(
            localExpenseId = expense.revertsExpenseId,
            allExpensesById = allExpensesById,
            eventServerId = serverId,
            contextKey = "reverts_expense_id",
        ) { return it }
        val replacesExpenseId = mapCorrectionServerId(
            localExpenseId = expense.replacesExpenseId,
            allExpensesById = allExpensesById,
            eventServerId = serverId,
            contextKey = "replaces_expense_id",
        ) { return it }
        val result = client.requestWithExceptionHandling {
            post {
                url(hostConfig) { pathSegments = listOf("api", "v2", "user", "event", serverId, "expense") }
                idempotencyKey(idempotencyKey)
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
                                    .takeIf { expense.isCustomRate || expense.correctedExpenseId != null }
                                    ?.doubleValue(false),
                            )
                        },
                        description = expense.description,
                        pinCode = event.pinCode,
                        isCustomRate = expense.isCustomRate,
                        revertsExpenseId = revertsExpenseId,
                        replacesExpenseId = replacesExpenseId,
                    )
                )
            }.body<ExpenseDto>().toExpense(expense, currencies, persons)
        }
        return when (result) {
            is NetworkResult.Error.Http.Client -> {
                if (result.getErrorCode() in DomainErrorCodes.permanentExpenseCorrectionErrorCodes) {
                    IoResult.Error.Failure
                } else {
                    result.toIoResult()
                }
            }
            else -> result.toIoResult()
        }
    }

    private fun ExpenseDto.toPullItem(
        currencies: List<Currency>,
        persons: List<Person>
    ): ExpensePullItem? {
        return ExpensePullItem(
            expense = toExpense(localExpense = null, currencies, persons) ?: return null,
            revertsExpenseServerId = revertsExpenseId,
            replacesExpenseServerId = replacesExpenseId,
        )
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
            clientCreateId = localExpense?.clientCreateId ?: ClientCreateId.fromServerId(id),
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
            revertsExpenseId = localExpense?.revertsExpenseId,
            replacesExpenseId = localExpense?.replacesExpenseId,
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
                clientCreateId = person.clientCreateId,
                name = person.name,
            ),
            originalAmount = originalAmount,
            exchangedAmount = exchangedAmount,
        )
    }

    private inline fun mapCorrectionServerId(
        localExpenseId: Long?,
        allExpensesById: Map<Long, Expense>,
        eventServerId: String,
        contextKey: String,
        onError: (IoResult.Error) -> Nothing,
    ): String? {
        val expenseId = localExpenseId ?: return null
        return when (val result = resolveReferencedServerId(expenseId, allExpensesById, eventServerId, contextKey)) {
            is IoResult.Success -> result.data
            is IoResult.Error -> onError(result)
        }
    }

    private fun resolveReferencedServerId(
        expenseId: Long,
        allExpensesById: Map<Long, Expense>,
        eventServerId: String,
        contextKey: String,
    ): IoResult<String> {
        val referencedExpense = allExpensesById[expenseId]
            .captureMessageIfNull("ExpensesRemoteStore.addExpenseToEvent could not find referenced expense") {
                setContext("event_server_id", eventServerId)
                setContext(contextKey, expenseId.toString())
            }
            ?: return IoResult.Error.Failure
        return referencedExpense.serverId
            .captureMessageIfNull("ExpensesRemoteStore.addExpenseToEvent found referenced expense without a server id") {
                setContext("event_server_id", eventServerId)
                setContext(contextKey, expenseId.toString())
            }
            ?.let { IoResult.Success(it) }
            ?: IoResult.Error.Retry
    }

}
