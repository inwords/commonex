package com.inwords.expenses.feature.expenses.ui.list

import app.cash.turbine.test
import com.inwords.expenses.core.navigation.NavigationController
import com.inwords.expenses.core.ui.utils.SimpleScreenState
import com.inwords.expenses.feature.events.api.EventDeletionStateManager
import com.inwords.expenses.feature.events.api.EventDeletionStateManager.EventDeletionState
import com.inwords.expenses.feature.events.domain.DeleteEventUseCase
import com.inwords.expenses.feature.events.domain.EventsSyncStateHolder
import com.inwords.expenses.feature.events.domain.GetCurrentEventStateUseCase
import com.inwords.expenses.feature.events.domain.GetEventsUseCase
import com.inwords.expenses.feature.events.domain.JoinEventUseCase
import com.inwords.expenses.feature.events.domain.model.Currency
import com.inwords.expenses.feature.events.domain.model.Event
import com.inwords.expenses.feature.events.domain.model.EventDetails
import com.inwords.expenses.feature.events.domain.model.Person
import com.inwords.expenses.feature.expenses.domain.DebtCalculator
import com.inwords.expenses.feature.expenses.domain.GetExpensesDetailsUseCase
import com.inwords.expenses.feature.expenses.domain.RequestExpensesRefreshUseCase
import com.inwords.expenses.feature.expenses.domain.model.ExpensesDetails
import com.inwords.expenses.feature.expenses.ui.list.ExpensesPaneUiModel.Expenses
import com.inwords.expenses.feature.settings.api.SettingsRepository
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import io.mockk.coEvery
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
internal class ExpensesViewModelRefreshTest {

    private object TestFixtures {
        val primaryCurrency = Currency(
            id = 100L,
            serverId = "c1",
            code = "USD",
            name = "US Dollar",
            rate = BigDecimal.ONE,
        )

        val person = Person(
            id = 1L,
            serverId = "p1",
            name = "Alex"
        )

        val event = Event(
            id = 2L,
            serverId = "e2",
            name = "Trip",
            pinCode = "1234",
            primaryCurrencyId = primaryCurrency.id
        )

        val eventDetails = EventDetails(
            event = event,
            currencies = listOf(primaryCurrency),
            persons = listOf(person),
            primaryCurrency = primaryCurrency
        )

        val expensesDetails = ExpensesDetails(
            event = eventDetails,
            expenses = emptyList(),
            debtCalculator = DebtCalculator(emptyList(), primaryCurrency)
        )
    }

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val currentEventFlow = MutableStateFlow<EventDetails?>(null)
    private val currentPersonIdFlow = MutableStateFlow<Long?>(TestFixtures.person.id)
    private val eventsFlow = MutableStateFlow(emptyList<Event>())
    private val eventsDeletionStateFlow = MutableStateFlow<Map<Long, EventDeletionState>>(emptyMap())
    private val eventSyncStateFlow = MutableStateFlow(false)

    private val navigationController = mockk<NavigationController>(relaxed = true) {
        justRun { navigateTo(any()) }
    }
    private val getCurrentEventStateUseCase = mockk<GetCurrentEventStateUseCase>(relaxed = true) {
        every { currentEvent } returns currentEventFlow
    }
    private val eventDeletionStateManager = mockk<EventDeletionStateManager>(relaxed = true) {
        every { eventsDeletionState } returns eventsDeletionStateFlow
    }
    private val eventsSyncStateHolder = mockk<EventsSyncStateHolder>(relaxed = true) {
        every { getStateFor(any()) } returns eventSyncStateFlow
    }
    private val getEventsUseCase = mockk<GetEventsUseCase>(relaxed = true) {
        every { getEvents() } returns eventsFlow
    }
    private val joinEventUseCase = mockk<JoinEventUseCase>(relaxed = true)
    private val deleteEventUseCase = mockk<DeleteEventUseCase>(relaxed = true)
    private val getExpensesDetailsUseCase = mockk<GetExpensesDetailsUseCase>(relaxed = true) {
        every { getExpensesDetails(any()) } returns flowOf(TestFixtures.expensesDetails)
    }
    private val requestExpensesRefreshUseCase = mockk<RequestExpensesRefreshUseCase>(relaxed = true)
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true) {
        coEvery { getCurrentPersonId() } returns currentPersonIdFlow
    }

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `isRefreshing should reflect current event sync state`() = testScope.runTest {
        currentEventFlow.value = TestFixtures.eventDetails
        val viewModel = createViewModel()
        runCurrent()
        advanceUntilIdle()

        viewModel.state.test {
            val _ = assertIs<SimpleScreenState.Loading>(awaitItem())

            val initialState = assertIs<SimpleScreenState.Success<ExpensesPaneUiModel>>(awaitItem())
            val initialData = initialState.data as Expenses
            assertFalse(initialData.isRefreshing)

            // Trigger user refresh - this sets isRefreshing to true immediately
            viewModel.onRefresh()
            advanceUntilIdle()

            val refreshingState = assertIs<SimpleScreenState.Success<ExpensesPaneUiModel>>(awaitItem())
            val refreshingData = refreshingState.data as Expenses
            assertTrue(refreshingData.isRefreshing)

            // Set sync state to true - this should be detected by the refresh lifecycle
            // (debounce is 0ms when syncing, so it's detected immediately)
            eventSyncStateFlow.value = true
            advanceUntilIdle()

            // Set sync state to false - this triggers a 500ms debounce before the refresh lifecycle detects it
            eventSyncStateFlow.value = false
            // Advance time to pass the debounce (500ms) and minimum display time (1500ms)
            // The delay in onUserTriggeredRefresh ensures minimum display time is met
            advanceTimeBy(2100.milliseconds)
            advanceUntilIdle()

            val stoppedState = assertIs<SimpleScreenState.Success<ExpensesPaneUiModel>>(awaitItem())
            val stoppedData = stoppedState.data as Expenses
            assertFalse(stoppedData.isRefreshing)

            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun createViewModel(): ExpensesViewModel {
        return ExpensesViewModel(
            navigationController = navigationController,
            getCurrentEventStateUseCase = getCurrentEventStateUseCase,
            eventDeletionStateManager = eventDeletionStateManager,
            getEventsUseCase = getEventsUseCase,
            joinEventUseCase = joinEventUseCase,
            deleteEventUseCase = deleteEventUseCase,
            getExpensesDetailsUseCase = getExpensesDetailsUseCase,
            requestExpensesRefreshUseCase = requestExpensesRefreshUseCase,
            eventsSyncStateHolder = eventsSyncStateHolder,
            settingsRepository = settingsRepository,
            unconfinedDispatcher = testDispatcher,
            viewModelScope = this.testScope.backgroundScope,
        )
    }
}
