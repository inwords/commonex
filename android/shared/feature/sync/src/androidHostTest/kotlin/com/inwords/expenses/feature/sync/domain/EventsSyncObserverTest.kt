package com.inwords.expenses.feature.sync.domain

import com.inwords.expenses.feature.events.domain.EventsSyncStateHolder
import com.inwords.expenses.feature.events.domain.GetCurrentEventStateUseCase
import com.inwords.expenses.feature.events.domain.model.Currency
import com.inwords.expenses.feature.events.domain.model.Event
import com.inwords.expenses.feature.events.domain.model.EventDetails
import com.inwords.expenses.feature.events.domain.model.Person
import com.inwords.expenses.feature.events.domain.model.SeededCurrencies
import com.inwords.expenses.feature.expenses.domain.ExpensesRefreshRequestsHolder
import com.inwords.expenses.feature.expenses.domain.GetExpensesUseCase
import com.inwords.expenses.feature.expenses.domain.model.Expense
import com.inwords.expenses.feature.expenses.domain.model.ExpenseType
import com.inwords.expenses.feature.sync.data.EventsSyncManagerObserverDelegate
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
internal class EventsSyncObserverTest {

    private object TestFixtures {
        val currency = Currency(
            id = 1L,
            serverId = "currency-1",
            code = "EUR",
            name = "Euro",
            rate = SeededCurrencies.usdToOtherRates.getValue("EUR"),
        )

        val event = Event(
            id = 10L,
            serverId = "event-10",
            name = "Trip",
            pinCode = "1234",
            primaryCurrencyId = currency.id
        )

        val alex = Person(
            id = 1L,
            serverId = "person-1",
            name = "Alex"
        )

        val sam = Person(
            id = 2L,
            serverId = null,
            name = "Sam"
        )

        fun eventDetails(persons: List<Person>) = EventDetails(
            event = event,
            currencies = listOf(currency),
            persons = persons,
            primaryCurrency = currency
        )

        val firstExpense = Expense(
            expenseId = 50L,
            serverId = "expense-50",
            currency = currency,
            expenseType = ExpenseType.Spending,
            person = alex,
            subjectExpenseSplitWithPersons = emptyList(),
            timestamp = kotlin.time.Instant.fromEpochMilliseconds(0),
            description = "Dinner"
        )
    }

    @Test
    fun `observeNewEventsIn triggers sync when participants are added`() = runTest {
        val currentEventFlow = MutableStateFlow<EventDetails?>(null)
        val expensesFlow = MutableStateFlow<List<Expense>>(emptyList())
        val harness = createHarness(
            currentEventState = currentEventFlow,
            expenseFlows = mapOf(TestFixtures.event.id to expensesFlow),
        )
        val observerScope = CoroutineScope(StandardTestDispatcher(testScheduler))

        harness.observer.observeNewEventsIn(observerScope)
        advanceUntilIdle()

        currentEventFlow.value = TestFixtures.eventDetails(persons = listOf(TestFixtures.alex))
        advanceUntilIdle()
        harness.assertPushedEventIds(listOf(TestFixtures.event.id))

        currentEventFlow.value = TestFixtures.eventDetails(persons = listOf(TestFixtures.alex, TestFixtures.sam))
        advanceUntilIdle()
        harness.assertPushedEventIds(listOf(TestFixtures.event.id, TestFixtures.event.id))
    }

    @Test
    fun `observeNewEventsIn triggers sync when expenses change`() = runTest {
        val currentEventFlow = MutableStateFlow<EventDetails?>(null)
        val expensesFlow = MutableStateFlow<List<Expense>>(emptyList())
        val harness = createHarness(
            currentEventState = currentEventFlow,
            expenseFlows = mapOf(TestFixtures.event.id to expensesFlow),
        )
        val observerScope = CoroutineScope(StandardTestDispatcher(testScheduler))

        harness.observer.observeNewEventsIn(observerScope)
        advanceUntilIdle()

        currentEventFlow.value = TestFixtures.eventDetails(persons = listOf(TestFixtures.alex))
        advanceUntilIdle()
        harness.assertPushedEventIds(listOf(TestFixtures.event.id))

        expensesFlow.value = listOf(TestFixtures.firstExpense)
        advanceUntilIdle()
        harness.assertPushedEventIds(listOf(TestFixtures.event.id, TestFixtures.event.id))
    }

    @Test
    fun `observeNewEventsIn triggers sync when refresh is requested`() = runTest {
        val harness = createHarness(
            currentEventState = MutableStateFlow(null),
            expenseFlows = emptyMap(),
        )
        val observerScope = CoroutineScope(StandardTestDispatcher(testScheduler))

        harness.observer.observeNewEventsIn(observerScope)
        advanceUntilIdle()
        harness.refreshExpenses.tryEmit(TestFixtures.event)
        advanceUntilIdle()

        harness.assertPushedEventIds(listOf(TestFixtures.event.id))
    }

    private fun createHarness(
        currentEventState: MutableStateFlow<EventDetails?>,
        expenseFlows: Map<Long, MutableStateFlow<List<Expense>>>,
    ): TestHarness {
        val refreshExpensesFlow = MutableSharedFlow<Event>(extraBufferCapacity = 4)
        val pushedEventIds = CopyOnWriteArrayList<Long>()
        val getCurrentEventStateUseCase = mockk<GetCurrentEventStateUseCase>(relaxed = true) {
            every { currentEvent } returns currentEventState
        }
        val getExpensesUseCase = mockk<GetExpensesUseCase>(relaxed = true) {
            expenseFlows.forEach { (eventId, expensesFlow) ->
                every { getExpensesFlow(eventId) } returns expensesFlow
            }
        }
        val expensesRefreshRequestsHolder = mockk<ExpensesRefreshRequestsHolder>(relaxed = true) {
            every { refreshExpensesRequests } returns refreshExpensesFlow
        }
        val eventsSyncManager = mockk<EventsSyncManagerObserverDelegate>(relaxed = true) {
            every { pushAllEventInfo(any()) } answers {
                pushedEventIds += firstArg<Long>()
            }
            every { getSyncState() } returns emptyFlow()
        }

        val observer = EventsSyncObserver(
            getCurrentEventStateUseCaseLazy = lazyOf(getCurrentEventStateUseCase),
            getExpensesUseCaseLazy = lazyOf(getExpensesUseCase),
            expensesRefreshRequestsHolderLazy = lazyOf(expensesRefreshRequestsHolder),
            eventsSyncStateHolderLazy = lazyOf(mockk<EventsSyncStateHolder>(relaxed = true)),
            eventsSyncManagerLazy = lazyOf(eventsSyncManager),
        )

        return TestHarness(
            observer = observer,
            refreshExpenses = refreshExpensesFlow,
            pushedEventIds = pushedEventIds,
        )
    }

    private data class TestHarness(
        val observer: EventsSyncObserver,
        val refreshExpenses: MutableSharedFlow<Event>,
        val pushedEventIds: CopyOnWriteArrayList<Long>,
    ) {
        fun assertPushedEventIds(expected: List<Long>) {
            assertEquals(expected, pushedEventIds.toList())
        }
    }
}
