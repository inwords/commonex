package com.inwords.expenses.feature.expenses.domain.tasks

import com.inwords.expenses.core.network.IdempotencyKeyGenerator
import com.inwords.expenses.core.storage.utils.TransactionHelper
import com.inwords.expenses.core.utils.IoResult
import com.inwords.expenses.feature.events.domain.model.Currency
import com.inwords.expenses.feature.events.domain.model.Event
import com.inwords.expenses.feature.events.domain.model.EventDetails
import com.inwords.expenses.feature.events.domain.model.Person
import com.inwords.expenses.feature.events.domain.store.local.EventsLocalStore
import com.inwords.expenses.feature.expenses.domain.model.Expense
import com.inwords.expenses.feature.expenses.domain.model.ExpenseSplitWithPerson
import com.inwords.expenses.feature.expenses.domain.model.ExpenseType
import com.inwords.expenses.feature.expenses.domain.store.ExpensePushItem
import com.inwords.expenses.feature.expenses.domain.store.ExpensesLocalStore
import com.inwords.expenses.feature.expenses.domain.store.ExpensesRemoteStore
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock

@OptIn(ExperimentalCoroutinesApi::class)
internal class EventExpensesPushTaskTest {

    private val mainDispatcher = StandardTestDispatcher()
    private val eventsLocalStore = mockk<EventsLocalStore>()
    private val expensesLocalStore = mockk<ExpensesLocalStore>(relaxed = true)
    private val expensesRemoteStore = mockk<ExpensesRemoteStore>()
    private val transactionHelper = mockk<TransactionHelper>()
    private val idempotencyKeyGenerator = mockk<IdempotencyKeyGenerator>()

    private val task = EventExpensesPushTask(
        eventsLocalStoreLazy = lazy { eventsLocalStore },
        expensesLocalStoreLazy = lazy { expensesLocalStore },
        expensesRemoteStoreLazy = lazy { expensesRemoteStore },
        transactionHelperLazy = lazy { transactionHelper },
        idempotencyKeyGeneratorLazy = lazy { idempotencyKeyGenerator },
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        coEvery {
            idempotencyKeyGenerator.mobileIdempotencyKey("event.expense.add", "srv-event", "expense-1")
        } returns "mobile:$CLIENT_ID:event.expense.add:srv-event:expense-1"
        coEvery {
            idempotencyKeyGenerator.mobileIdempotencyKey("event.expense.add", "srv-event", "expense-2")
        } returns "mobile:$CLIENT_ID:event.expense.add:srv-event:expense-2"
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `pushEventExpenses returns failure when prerequisites are missing`() = runTest {
        val cases = listOf(
            null,
            eventDetails(event = event(serverId = null)),
            eventDetails(persons = listOf(person(serverId = null))),
            eventDetails(currencies = listOf(currency(serverId = null))),
        )

        cases.forEachIndexed { index, details ->
            coEvery { eventsLocalStore.getEventWithDetails(index.toLong()) } returns details

            val result = task.pushEventExpenses(index.toLong())

            assertEquals(IoResult.Error.Failure, result)
        }
    }

    @Test
    fun `pushEventExpenses returns success when there are no local expenses`() = runTest {
        val localDetails = eventDetails()
        coEvery { eventsLocalStore.getEventWithDetails(localDetails.event.id) } returns localDetails
        coEvery { expensesLocalStore.getExpenses(localDetails.event.id) } returns emptyList()

        val result = task.pushEventExpenses(localDetails.event.id)

        assertEquals(IoResult.Success(Unit), result)
        coVerify(exactly = 0) { expensesRemoteStore.addExpensesToEvent(any(), any(), any(), any()) }
    }

    @Test
    fun `pushEventExpenses returns success when there are no unsynced expenses`() = runTest {
        val localDetails = eventDetails()
        coEvery { eventsLocalStore.getEventWithDetails(localDetails.event.id) } returns localDetails
        coEvery { expensesLocalStore.getExpenses(localDetails.event.id) } returns listOf(expense(expenseId = 1L, serverId = "srv-1"))

        val result = task.pushEventExpenses(localDetails.event.id)

        assertEquals(IoResult.Success(Unit), result)
        coVerify(exactly = 0) { expensesRemoteStore.addExpensesToEvent(any(), any(), any(), any()) }
    }

    @Test
    fun `pushEventExpenses filters out expenses with unsynced payer or currency`() = runTest {
        val localDetails = eventDetails()
        val invalidExpense = expense(
            expenseId = 1L,
            serverId = null,
            person = person(serverId = null),
            currency = currency(serverId = null),
        )
        coEvery { eventsLocalStore.getEventWithDetails(localDetails.event.id) } returns localDetails
        coEvery { expensesLocalStore.getExpenses(localDetails.event.id) } returns listOf(invalidExpense)

        val result = task.pushEventExpenses(localDetails.event.id)

        assertEquals(IoResult.Success(Unit), result)
        coVerify(exactly = 0) { expensesRemoteStore.addExpensesToEvent(any(), any(), any(), any()) }
    }

    @Test
    fun `pushEventExpenses updates expense server id and split amounts for successful results`() = runTest {
        val localDetails = eventDetails()
        val localExpense = expense(expenseId = 1L, serverId = null)
        val networkExpense = expense(
            expenseId = 1L,
            serverId = "srv-1",
            splitExchangeValues = listOf("11.11", "22.22"),
        ).let { response ->
            response.copy(subjectExpenseSplitWithPersons = response.subjectExpenseSplitWithPersons.map { it.copy(expenseSplitId = 0L) })
        }
        coEvery { eventsLocalStore.getEventWithDetails(localDetails.event.id) } returns localDetails
        coEvery { expensesLocalStore.getExpenses(localDetails.event.id) } returns listOf(localExpense)
        coEvery {
            expensesRemoteStore.addExpensesToEvent(
                event = localDetails.event,
                expenses = listOf(
                    ExpensePushItem(
                        expense = localExpense,
                        idempotencyKey = "mobile:$CLIENT_ID:event.expense.add:srv-event:expense-1",
                    )
                ),
                currencies = localDetails.currencies,
                persons = localDetails.persons,
            )
        } returns listOf(IoResult.Success(networkExpense))
        coEvery { transactionHelper.immediateWriteTransaction<Unit>(any()) } coAnswers {
            firstArg<suspend () -> Unit>().invoke()
        }
        coEvery { expensesLocalStore.updateExpenseServerId(localExpense.expenseId, "srv-1") } returns true
        coEvery {
            expensesLocalStore.updateExpenseSplitExchangedAmount(
                localExpense.subjectExpenseSplitWithPersons[0].expenseSplitId,
                BigDecimal.parseString("11.11"),
            )
        } returns true
        coEvery {
            expensesLocalStore.updateExpenseSplitExchangedAmount(
                localExpense.subjectExpenseSplitWithPersons[1].expenseSplitId,
                BigDecimal.parseString("22.22"),
            )
        } returns true

        val result = task.pushEventExpenses(localDetails.event.id)

        assertEquals(IoResult.Success(Unit), result)
        coVerify(exactly = 1) { expensesLocalStore.updateExpenseServerId(localExpense.expenseId, "srv-1") }
        coVerify(exactly = 0) { expensesLocalStore.updateExpenseSplitExchangedAmount(0L, any()) }
        coVerify(exactly = 1) {
            expensesLocalStore.updateExpenseSplitExchangedAmount(
                localExpense.subjectExpenseSplitWithPersons[0].expenseSplitId,
                BigDecimal.parseString("11.11"),
            )
        }
        coVerify(exactly = 1) {
            expensesLocalStore.updateExpenseSplitExchangedAmount(
                localExpense.subjectExpenseSplitWithPersons[1].expenseSplitId,
                BigDecimal.parseString("22.22"),
            )
        }
    }

    @Test
    fun `pushEventExpenses preserves successes and retries only pending expenses after partial failure`() = runTest {
        val localDetails = eventDetails()
        val firstExpense = expense(expenseId = 1L, serverId = null)
        val secondExpense = expense(expenseId = 2L, serverId = null)
        val firstNetworkExpense = expense(expenseId = 1L, serverId = "srv-1", splitExchangeValues = listOf("11.11", "22.22"))
        val secondNetworkExpense = secondExpense.copy(serverId = "srv-2")
        var persistedExpenses = listOf(firstExpense, secondExpense)
        coEvery { eventsLocalStore.getEventWithDetails(localDetails.event.id) } returns localDetails
        coEvery { expensesLocalStore.getExpenses(localDetails.event.id) } coAnswers { persistedExpenses }
        coEvery {
            expensesRemoteStore.addExpensesToEvent(
                event = localDetails.event,
                expenses = listOf(
                    ExpensePushItem(
                        expense = firstExpense,
                        idempotencyKey = "mobile:$CLIENT_ID:event.expense.add:srv-event:expense-1",
                    ),
                    ExpensePushItem(
                        expense = secondExpense,
                        idempotencyKey = "mobile:$CLIENT_ID:event.expense.add:srv-event:expense-2",
                    ),
                ),
                currencies = localDetails.currencies,
                persons = localDetails.persons,
            )
        } returns listOf(IoResult.Success(firstNetworkExpense), IoResult.Error.Retry)
        coEvery {
            expensesRemoteStore.addExpensesToEvent(
                event = localDetails.event,
                expenses = listOf(
                    ExpensePushItem(secondExpense, "mobile:$CLIENT_ID:event.expense.add:srv-event:expense-2")
                ),
                currencies = localDetails.currencies,
                persons = localDetails.persons,
            )
        } returns listOf(IoResult.Success(secondNetworkExpense))
        coEvery { transactionHelper.immediateWriteTransaction<Unit>(any()) } coAnswers {
            firstArg<suspend () -> Unit>().invoke()
        }
        coEvery { expensesLocalStore.updateExpenseServerId(any(), any()) } coAnswers {
            val expenseId = firstArg<Long>()
            val serverId = secondArg<String>()
            persistedExpenses = persistedExpenses.map { if (it.expenseId == expenseId) it.copy(serverId = serverId) else it }
            true
        }
        coEvery {
            expensesLocalStore.updateExpenseSplitExchangedAmount(
                firstExpense.subjectExpenseSplitWithPersons[0].expenseSplitId,
                BigDecimal.parseString("11.11")
            )
        } returns true
        coEvery {
            expensesLocalStore.updateExpenseSplitExchangedAmount(
                firstExpense.subjectExpenseSplitWithPersons[1].expenseSplitId,
                BigDecimal.parseString("22.22")
            )
        } returns true

        val result = task.pushEventExpenses(localDetails.event.id)

        assertEquals(IoResult.Error.Retry, result)
        assertEquals(listOf("srv-1", null), persistedExpenses.map { it.serverId })
        assertEquals(listOf("expense-1", "expense-2"), persistedExpenses.map { it.clientCreateId })
        coVerify(exactly = 1) { expensesLocalStore.updateExpenseServerId(firstExpense.expenseId, "srv-1") }
        coVerify(exactly = 0) { expensesLocalStore.updateExpenseServerId(secondExpense.expenseId, any()) }

        val retryResult = task.pushEventExpenses(localDetails.event.id)

        assertEquals(IoResult.Success(Unit), retryResult)
        assertEquals(listOf("srv-1", "srv-2"), persistedExpenses.map { it.serverId })
        assertEquals(listOf("expense-1", "expense-2"), persistedExpenses.map { it.clientCreateId })
        coVerify(exactly = 1) { expensesLocalStore.updateExpenseServerId(firstExpense.expenseId, "srv-1") }
        coVerify(exactly = 1) { expensesLocalStore.updateExpenseServerId(secondExpense.expenseId, "srv-2") }
        firstNetworkExpense.subjectExpenseSplitWithPersons.forEach { split ->
            coVerify(exactly = 1) {
                expensesLocalStore.updateExpenseSplitExchangedAmount(split.expenseSplitId, split.exchangedAmount)
            }
        }
        secondNetworkExpense.subjectExpenseSplitWithPersons.forEach { split ->
            coVerify(exactly = 1) {
                expensesLocalStore.updateExpenseSplitExchangedAmount(split.expenseSplitId, split.exchangedAmount)
            }
        }
    }

    @Test
    fun `pushEventExpenses keeps success aligned when partial response starts with error`() = runTest {
        val localDetails = eventDetails()
        val firstExpense = expense(expenseId = 1L, serverId = null)
        val secondExpense = expense(expenseId = 2L, serverId = null)
        val secondNetworkExpense = expense(
            expenseId = 2L,
            serverId = "srv-2",
            splitExchangeValues = listOf("33.33", "44.44"),
        )
        coEvery { eventsLocalStore.getEventWithDetails(localDetails.event.id) } returns localDetails
        coEvery { expensesLocalStore.getExpenses(localDetails.event.id) } returns listOf(firstExpense, secondExpense)
        coEvery {
            expensesRemoteStore.addExpensesToEvent(
                event = localDetails.event,
                expenses = listOf(
                    ExpensePushItem(
                        expense = firstExpense,
                        idempotencyKey = "mobile:$CLIENT_ID:event.expense.add:srv-event:expense-1",
                    ),
                    ExpensePushItem(
                        expense = secondExpense,
                        idempotencyKey = "mobile:$CLIENT_ID:event.expense.add:srv-event:expense-2",
                    ),
                ),
                currencies = localDetails.currencies,
                persons = localDetails.persons,
            )
        } returns listOf(IoResult.Error.Retry, IoResult.Success(secondNetworkExpense))
        coEvery { transactionHelper.immediateWriteTransaction<Unit>(any()) } coAnswers {
            firstArg<suspend () -> Unit>().invoke()
        }
        coEvery { expensesLocalStore.updateExpenseServerId(secondExpense.expenseId, "srv-2") } returns true
        coEvery {
            expensesLocalStore.updateExpenseSplitExchangedAmount(
                secondExpense.subjectExpenseSplitWithPersons[0].expenseSplitId,
                BigDecimal.parseString("33.33"),
            )
        } returns true
        coEvery {
            expensesLocalStore.updateExpenseSplitExchangedAmount(
                secondExpense.subjectExpenseSplitWithPersons[1].expenseSplitId,
                BigDecimal.parseString("44.44"),
            )
        } returns true

        val result = task.pushEventExpenses(localDetails.event.id)

        assertEquals(IoResult.Error.Retry, result)
        coVerify(exactly = 0) { expensesLocalStore.updateExpenseServerId(firstExpense.expenseId, any()) }
        coVerify(exactly = 1) { expensesLocalStore.updateExpenseServerId(secondExpense.expenseId, "srv-2") }
        coVerify(exactly = 1) {
            expensesLocalStore.updateExpenseSplitExchangedAmount(
                secondExpense.subjectExpenseSplitWithPersons[0].expenseSplitId,
                BigDecimal.parseString("33.33"),
            )
        }
        coVerify(exactly = 1) {
            expensesLocalStore.updateExpenseSplitExchangedAmount(
                secondExpense.subjectExpenseSplitWithPersons[1].expenseSplitId,
                BigDecimal.parseString("44.44"),
            )
        }
    }

    @Test
    fun `pushEventExpenses leaves permanently rejected expenses pending without requesting retry`() = runTest {
        val localDetails = eventDetails()
        val localExpense = expense(expenseId = 1L, serverId = null)
        coEvery { eventsLocalStore.getEventWithDetails(localDetails.event.id) } returns localDetails
        coEvery { expensesLocalStore.getExpenses(localDetails.event.id) } returns listOf(localExpense)
        coEvery { expensesRemoteStore.addExpensesToEvent(any(), any(), any(), any()) } returns listOf(IoResult.Error.Failure)

        val result = task.pushEventExpenses(localDetails.event.id)

        assertEquals(IoResult.Success(Unit), result)
        coVerify(exactly = 0) { expensesLocalStore.updateExpenseServerId(any(), any()) }
        coVerify(exactly = 0) { expensesLocalStore.updateExpenseSplitExchangedAmount(any(), any()) }
    }

    @Test
    fun `pushEventExpenses retries transient errors with the same pending expense identity`() = runTest {
        val localDetails = eventDetails()
        val localExpense = expense(expenseId = 1L, serverId = null)
        val networkExpense = localExpense.copy(
            serverId = "srv-1",
            subjectExpenseSplitWithPersons = localExpense.subjectExpenseSplitWithPersons.map {
                it.copy(exchangedAmount = BigDecimal.parseString("12.34"))
            },
        )
        coEvery { eventsLocalStore.getEventWithDetails(localDetails.event.id) } returns localDetails
        coEvery { expensesLocalStore.getExpenses(localDetails.event.id) } returns listOf(localExpense)
        coEvery {
            expensesRemoteStore.addExpensesToEvent(
                event = localDetails.event,
                expenses = listOf(
                    ExpensePushItem(
                        expense = localExpense,
                        idempotencyKey = "mobile:$CLIENT_ID:event.expense.add:srv-event:expense-1",
                    )
                ),
                currencies = localDetails.currencies,
                persons = localDetails.persons,
            )
        } returnsMany listOf(listOf(IoResult.Error.Retry), listOf(IoResult.Success(networkExpense)))
        coEvery { transactionHelper.immediateWriteTransaction<Unit>(any()) } coAnswers {
            firstArg<suspend () -> Unit>().invoke()
        }

        val firstResult = task.pushEventExpenses(localDetails.event.id)

        assertEquals(IoResult.Error.Retry, firstResult)
        coVerify(exactly = 0) { expensesLocalStore.updateExpenseServerId(any(), any()) }
        coVerify(exactly = 0) { expensesLocalStore.updateExpenseSplitExchangedAmount(any(), any()) }

        val retryResult = task.pushEventExpenses(localDetails.event.id)

        assertEquals(IoResult.Success(Unit), retryResult)
        coVerify(exactly = 2) {
            expensesRemoteStore.addExpensesToEvent(
                event = localDetails.event,
                expenses = listOf(
                    ExpensePushItem(localExpense, "mobile:$CLIENT_ID:event.expense.add:srv-event:expense-1")
                ),
                currencies = localDetails.currencies,
                persons = localDetails.persons,
            )
        }
        coVerify(exactly = 1) { expensesLocalStore.updateExpenseServerId(localExpense.expenseId, "srv-1") }
        localExpense.subjectExpenseSplitWithPersons.forEach { split ->
            coVerify(exactly = 1) {
                expensesLocalStore.updateExpenseSplitExchangedAmount(split.expenseSplitId, BigDecimal.parseString("12.34"))
            }
        }
    }

    @Test
    fun `pushEventExpenses commits remote success even when caller is cancelled during persistence`() = runTest {
        val localDetails = eventDetails()
        val localExpense = expense(expenseId = 1L, serverId = null)
        val networkExpense = expense(expenseId = 1L, serverId = "srv-1", splitExchangeValues = listOf("11.11", "22.22"))
        val transactionStarted = CompletableDeferred<Unit>()
        val transactionCommitted = CompletableDeferred<Unit>()
        val allowCommit = CompletableDeferred<Unit>()
        coEvery { eventsLocalStore.getEventWithDetails(localDetails.event.id) } returns localDetails
        coEvery { expensesLocalStore.getExpenses(localDetails.event.id) } returns listOf(localExpense)
        coEvery {
            expensesRemoteStore.addExpensesToEvent(
                localDetails.event,
                listOf(ExpensePushItem(localExpense, "mobile:$CLIENT_ID:event.expense.add:srv-event:expense-1")),
                localDetails.currencies,
                localDetails.persons,
            )
        } returns listOf(IoResult.Success(networkExpense))
        coEvery { transactionHelper.immediateWriteTransaction<Unit>(any()) } coAnswers {
            transactionStarted.complete(Unit)
            allowCommit.await()
            firstArg<suspend () -> Unit>().invoke()
            coVerify(exactly = 1) { expensesLocalStore.updateExpenseServerId(localExpense.expenseId, "srv-1") }
            networkExpense.subjectExpenseSplitWithPersons.forEach { split ->
                coVerify(exactly = 1) {
                    expensesLocalStore.updateExpenseSplitExchangedAmount(split.expenseSplitId, split.exchangedAmount)
                }
            }
            transactionCommitted.complete(Unit)
        }

        val pushJob = launch { task.pushEventExpenses(localDetails.event.id) }
        try {
            transactionStarted.await()
            pushJob.cancel()

            assertFalse(pushJob.isCompleted)
            coVerify(exactly = 0) { expensesLocalStore.updateExpenseServerId(any(), any()) }
            coVerify(exactly = 0) { expensesLocalStore.updateExpenseSplitExchangedAmount(any(), any()) }
        } finally {
            allowCommit.complete(Unit)
            pushJob.cancelAndJoin()
        }

        assertTrue(pushJob.isCancelled)
        assertTrue(transactionCommitted.isCompleted)
        coVerify(exactly = 1) { transactionHelper.immediateWriteTransaction(any()) }
    }

    @Test
    fun `pushEventExpenses replays same durable request after local transaction failure`() = runTest {
        val localDetails = eventDetails()
        val localExpense = expense(expenseId = 1L, serverId = null)
        val networkExpense = expense(expenseId = 1L, serverId = "srv-1", splitExchangeValues = listOf("11.11", "22.22"))
        val pushItems = listOf(ExpensePushItem(localExpense, "mobile:$CLIENT_ID:event.expense.add:srv-event:expense-1"))
        val transactionFailure = IllegalStateException("Local transaction unavailable")
        var failTransaction = true
        coEvery { eventsLocalStore.getEventWithDetails(localDetails.event.id) } returns localDetails
        coEvery { expensesLocalStore.getExpenses(localDetails.event.id) } returns listOf(localExpense)
        coEvery {
            expensesRemoteStore.addExpensesToEvent(localDetails.event, pushItems, localDetails.currencies, localDetails.persons)
        } returns listOf(IoResult.Success(networkExpense))
        coEvery { transactionHelper.immediateWriteTransaction<Unit>(any()) } coAnswers {
            if (failTransaction) {
                failTransaction = false
                throw transactionFailure
            }
            firstArg<suspend () -> Unit>().invoke()
        }

        val failure = assertFailsWith<IllegalStateException> { task.pushEventExpenses(localDetails.event.id) }

        assertEquals(transactionFailure.message, failure.message)
        coVerify(exactly = 0) { expensesLocalStore.updateExpenseServerId(any(), any()) }
        coVerify(exactly = 0) { expensesLocalStore.updateExpenseSplitExchangedAmount(any(), any()) }

        val replayResult = task.pushEventExpenses(localDetails.event.id)

        assertEquals(IoResult.Success(Unit), replayResult)
        coVerify(exactly = 2) {
            expensesRemoteStore.addExpensesToEvent(localDetails.event, pushItems, localDetails.currencies, localDetails.persons)
        }
        coVerify(exactly = 1) { expensesLocalStore.updateExpenseServerId(localExpense.expenseId, "srv-1") }
        networkExpense.subjectExpenseSplitWithPersons.forEach { split ->
            coVerify(exactly = 1) {
                expensesLocalStore.updateExpenseSplitExchangedAmount(split.expenseSplitId, split.exchangedAmount)
            }
        }
    }

    private fun event(serverId: String? = "srv-event"): Event {
        return Event(id = 1L, serverId = serverId, name = "Trip", pinCode = "1234", primaryCurrencyId = 1L, clientCreateId = "event-1")
    }

    private fun person(serverId: String?): Person {
        return Person(id = 1L, serverId = serverId, name = "Person$1", clientCreateId = "person-1")
    }

    private fun currency(serverId: String? = "srv-eur"): Currency {
        return Currency(id = 1L, serverId = serverId, code = "EUR", name = "Euro", rate = BigDecimal.ONE)
    }

    private fun eventDetails(
        event: Event = event(),
        persons: List<Person> = listOf(person(serverId = "srv-p1")),
        currencies: List<Currency> = listOf(currency()),
    ): EventDetails {
        return EventDetails(
            event = event,
            currencies = currencies,
            persons = persons,
            primaryCurrency = currencies.first(),
        )
    }

    private fun expense(
        expenseId: Long,
        serverId: String?,
        person: Person = this.person(serverId = "srv-p1"),
        currency: Currency = this.currency(),
        splitExchangeValues: List<String> = listOf("10", "20"),
    ): Expense {
        return Expense(
            expenseId = expenseId,
            serverId = serverId,
            currency = currency,
            expenseType = ExpenseType.Spending,
            person = person,
            subjectExpenseSplitWithPersons = splitExchangeValues.mapIndexed { index, value ->
                ExpenseSplitWithPerson(
                    expenseSplitId = expenseId * 10 + index,
                    expenseId = expenseId,
                    person = person,
                    originalAmount = BigDecimal.parseString((index + 1).toString()),
                    exchangedAmount = BigDecimal.parseString(value),
                )
            },
            isCustomRate = false,
            timestamp = Clock.System.now(),
            description = "Expense$expenseId",
            clientCreateId = "expense-$expenseId",
        )
    }

    private companion object {
        const val CLIENT_ID = "00000000-0000-0000-0000-000000000001"
    }
}
