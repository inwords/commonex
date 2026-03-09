package com.inwords.expenses.feature.expenses.domain.tasks

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
import com.inwords.expenses.feature.expenses.domain.store.ExpensesLocalStore
import com.inwords.expenses.feature.expenses.domain.store.ExpensesRemoteStore
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock

@OptIn(ExperimentalCoroutinesApi::class)
internal class EventExpensesPushTaskTest {

    private val mainDispatcher = StandardTestDispatcher()
    private val eventsLocalStore = mockk<EventsLocalStore>()
    private val expensesLocalStore = mockk<ExpensesLocalStore>(relaxed = true)
    private val expensesRemoteStore = mockk<ExpensesRemoteStore>()
    private val transactionHelper = mockk<TransactionHelper>()

    private val task = EventExpensesPushTask(
        eventsLocalStoreLazy = lazy { eventsLocalStore },
        expensesLocalStoreLazy = lazy { expensesLocalStore },
        expensesRemoteStoreLazy = lazy { expensesRemoteStore },
        transactionHelperLazy = lazy { transactionHelper },
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
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
        )
        coEvery { eventsLocalStore.getEventWithDetails(localDetails.event.id) } returns localDetails
        coEvery { expensesLocalStore.getExpenses(localDetails.event.id) } returns listOf(localExpense)
        coEvery {
            expensesRemoteStore.addExpensesToEvent(
                event = localDetails.event,
                expenses = listOf(localExpense),
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
    fun `pushEventExpenses persists only successful results from partial network response`() = runTest {
        val localDetails = eventDetails()
        val firstExpense = expense(expenseId = 1L, serverId = null)
        val secondExpense = expense(expenseId = 2L, serverId = null)
        val firstNetworkExpense = expense(expenseId = 1L, serverId = "srv-1", splitExchangeValues = listOf("11.11", "22.22"))
        coEvery { eventsLocalStore.getEventWithDetails(localDetails.event.id) } returns localDetails
        coEvery { expensesLocalStore.getExpenses(localDetails.event.id) } returns listOf(firstExpense, secondExpense)
        coEvery {
            expensesRemoteStore.addExpensesToEvent(
                event = localDetails.event,
                expenses = listOf(firstExpense, secondExpense),
                currencies = localDetails.currencies,
                persons = localDetails.persons,
            )
        } returns listOf(IoResult.Success(firstNetworkExpense), IoResult.Error.Retry)
        coEvery { transactionHelper.immediateWriteTransaction<Unit>(any()) } coAnswers {
            firstArg<suspend () -> Unit>().invoke()
        }
        coEvery { expensesLocalStore.updateExpenseServerId(firstExpense.expenseId, "srv-1") } returns true
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

        assertEquals(IoResult.Success(Unit), result)
        coVerify(exactly = 1) { expensesLocalStore.updateExpenseServerId(firstExpense.expenseId, "srv-1") }
        coVerify(exactly = 0) { expensesLocalStore.updateExpenseServerId(secondExpense.expenseId, any()) }
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
                expenses = listOf(firstExpense, secondExpense),
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

        assertEquals(IoResult.Success(Unit), result)
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
    fun `pushEventExpenses returns success with no updates when all network results are errors`() = runTest {
        val localDetails = eventDetails()
        val localExpense = expense(expenseId = 1L, serverId = null)
        coEvery { eventsLocalStore.getEventWithDetails(localDetails.event.id) } returns localDetails
        coEvery { expensesLocalStore.getExpenses(localDetails.event.id) } returns listOf(localExpense)
        coEvery {
            expensesRemoteStore.addExpensesToEvent(
                event = localDetails.event,
                expenses = listOf(localExpense),
                currencies = localDetails.currencies,
                persons = localDetails.persons,
            )
        } returns listOf(IoResult.Error.Retry)

        val result = task.pushEventExpenses(localDetails.event.id)

        assertEquals(IoResult.Success(Unit), result)
        coVerify(exactly = 0) { expensesLocalStore.updateExpenseServerId(any(), any()) }
        coVerify(exactly = 0) { expensesLocalStore.updateExpenseSplitExchangedAmount(any(), any()) }
    }

    private fun event(serverId: String? = "srv-event"): Event {
        return Event(id = 1L, serverId = serverId, name = "Trip", pinCode = "1234", primaryCurrencyId = 1L)
    }

    private fun person(serverId: String?): Person {
        return Person(id = 1L, serverId = serverId, name = "Person$1")
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
            timestamp = Clock.System.now(),
            description = "Expense$expenseId",
        )
    }
}
