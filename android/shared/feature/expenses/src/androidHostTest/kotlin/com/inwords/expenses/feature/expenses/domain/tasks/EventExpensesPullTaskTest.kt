package com.inwords.expenses.feature.expenses.domain.tasks

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
internal class EventExpensesPullTaskTest {

    private val mainDispatcher = StandardTestDispatcher()
    private val eventsLocalStore = mockk<EventsLocalStore>()
    private val expensesLocalStore = mockk<ExpensesLocalStore>(relaxed = true)
    private val expensesRemoteStore = mockk<ExpensesRemoteStore>()

    private val task = EventExpensesPullTask(
        eventsLocalStoreLazy = lazy { eventsLocalStore },
        expensesLocalStoreLazy = lazy { expensesLocalStore },
        expensesRemoteStoreLazy = lazy { expensesRemoteStore },
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
    fun `pullEventExpenses returns failure when prerequisites are missing`() = runTest {
        val cases = listOf(
            null,
            eventDetails(event = event(serverId = null)),
            eventDetails(persons = listOf(person(serverId = null))),
            eventDetails(currencies = listOf(currency(serverId = null))),
        )

        cases.forEachIndexed { index, details ->
            coEvery { eventsLocalStore.getEventWithDetails(index.toLong()) } returns details

            val result = task.pullEventExpenses(index.toLong())

            assertEquals(IoResult.Error.Failure, result)
        }
    }

    @Test
    fun `pullEventExpenses propagates remote errors unchanged`() = runTest {
        val localDetails = eventDetails()
        coEvery { eventsLocalStore.getEventWithDetails(localDetails.event.id) } returns localDetails

        listOf(IoResult.Error.Retry, IoResult.Error.Failure).forEach { error ->
            coEvery {
                expensesRemoteStore.getExpenses(
                    event = localDetails.event,
                    currencies = localDetails.currencies,
                    persons = localDetails.persons,
                )
            } returns error

            val result = task.pullEventExpenses(localDetails.event.id)

            assertEquals(error, result)
        }
    }

    @Test
    fun `pullEventExpenses inserts only missing remote expenses`() = runTest {
        val localDetails = eventDetails()
        val existingExpense = expense(expenseId = 1L, serverId = "srv-1")
        val newExpense = expense(expenseId = 2L, serverId = "srv-2")
        coEvery { eventsLocalStore.getEventWithDetails(localDetails.event.id) } returns localDetails
        coEvery {
            expensesRemoteStore.getExpenses(
                event = localDetails.event,
                currencies = localDetails.currencies,
                persons = localDetails.persons,
            )
        } returns IoResult.Success(listOf(existingExpense.copy(description = "remote copy"), newExpense))
        coEvery { expensesLocalStore.getExpenses(localDetails.event.id) } returns listOf(existingExpense)
        coEvery { expensesLocalStore.upsert(localDetails.event, listOf(newExpense)) } returns listOf(newExpense)

        val result = task.pullEventExpenses(localDetails.event.id)

        assertEquals(IoResult.Success(Unit), result)
        coVerify(exactly = 1) { expensesLocalStore.upsert(localDetails.event, listOf(newExpense)) }
    }

    @Test
    fun `pullEventExpenses is insert only when all remote expenses already exist`() = runTest {
        val localDetails = eventDetails()
        val localExpense = expense(expenseId = 1L, serverId = "srv-1", description = "local")
        val remoteExpense = localExpense.copy(description = "remote changed")
        coEvery { eventsLocalStore.getEventWithDetails(localDetails.event.id) } returns localDetails
        coEvery {
            expensesRemoteStore.getExpenses(
                event = localDetails.event,
                currencies = localDetails.currencies,
                persons = localDetails.persons,
            )
        } returns IoResult.Success(listOf(remoteExpense))
        coEvery { expensesLocalStore.getExpenses(localDetails.event.id) } returns listOf(localExpense)

        val result = task.pullEventExpenses(localDetails.event.id)

        assertEquals(IoResult.Success(Unit), result)
        coVerify(exactly = 0) { expensesLocalStore.upsert(localDetails.event, any<List<Expense>>()) }
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
        description: String = "Dinner",
    ): Expense {
        val person = person(serverId = "srv-p1")
        val currency = currency()
        return Expense(
            expenseId = expenseId,
            serverId = serverId,
            currency = currency,
            expenseType = ExpenseType.Spending,
            person = person,
            subjectExpenseSplitWithPersons = listOf(
                ExpenseSplitWithPerson(
                    expenseSplitId = expenseId * 10,
                    expenseId = expenseId,
                    person = person,
                    originalAmount = BigDecimal.parseString("10"),
                    exchangedAmount = BigDecimal.parseString("10"),
                )
            ),
            timestamp = Clock.System.now(),
            description = description,
        )
    }
}
