package com.inwords.expenses.feature.expenses.ui.list

import androidx.compose.ui.text.intl.Locale
import app.cash.turbine.test
import com.inwords.expenses.core.navigation.NavigationController
import com.inwords.expenses.core.ui.utils.SimpleScreenState
import com.inwords.expenses.core.ui.utils.StringProvider
import com.inwords.expenses.feature.events.api.EventDeletionStateManager
import com.inwords.expenses.feature.events.domain.DeleteEventUseCase
import com.inwords.expenses.feature.events.domain.EventsSyncStateHolder
import com.inwords.expenses.feature.events.domain.GetCurrentEventStateUseCase
import com.inwords.expenses.feature.events.domain.GetEventsUseCase
import com.inwords.expenses.feature.events.domain.JoinEventUseCase
import com.inwords.expenses.feature.events.domain.LeaveEventUseCase
import com.inwords.expenses.feature.events.domain.model.Currency
import com.inwords.expenses.feature.events.domain.model.Event
import com.inwords.expenses.feature.events.domain.model.EventDetails
import com.inwords.expenses.feature.events.domain.model.Person
import com.inwords.expenses.feature.events.ui.join_invitation.JoinInvitationPaneDestination
import com.inwords.expenses.feature.expenses.domain.DebtCalculator
import com.inwords.expenses.feature.expenses.domain.GetExpensesDetailsUseCase
import com.inwords.expenses.feature.expenses.domain.RequestExpensesRefreshUseCase
import com.inwords.expenses.feature.expenses.domain.model.Expense
import com.inwords.expenses.feature.expenses.domain.model.ExpenseSplitWithPerson
import com.inwords.expenses.feature.expenses.domain.model.ExpenseType
import com.inwords.expenses.feature.expenses.domain.model.ExpensesDetails
import com.inwords.expenses.feature.expenses.ui.add.AddExpensePaneDestination
import com.inwords.expenses.feature.expenses.ui.common.DebtShortUiModel
import com.inwords.expenses.feature.expenses.ui.list.ExpensesPaneUiModel.Expenses
import com.inwords.expenses.feature.expenses.ui.list.ExpensesPaneUiModel.LocalEvents
import com.inwords.expenses.feature.expenses.ui.list.bottom_sheet.item.ExpenseItemPaneDestination
import com.inwords.expenses.feature.menu.ui.InviteEventPaneDestination
import com.inwords.expenses.feature.settings.api.SettingsRepository
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import expenses.shared.feature.expenses.generated.resources.Res
import expenses.shared.feature.expenses.generated.resources.expenses_today
import expenses.shared.feature.expenses.generated.resources.expenses_yesterday
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
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
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.StringResource
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
internal class ExpensesViewModelTimelineTest {

    private object Fixtures {
        val primaryCurrency = Currency(
            id = 1L,
            serverId = "currency-1",
            code = "EUR",
            name = "Euro",
            rate = BigDecimal.ONE,
        )
        val currentPerson = Person(
            id = 1L,
            serverId = "person-1",
            name = "Alex",
            clientCreateId = "person-1",
        )
        val otherPerson = Person(
            id = 2L,
            serverId = "person-2",
            name = "Ben",
            clientCreateId = "person-2",
        )
        val event = Event(
            id = 10L,
            serverId = "event-10",
            name = "Trip",
            pinCode = "1234",
            primaryCurrencyId = primaryCurrency.id,
            clientCreateId = "event-10",
        )
        val eventDetails = EventDetails(
            event = event,
            currencies = listOf(primaryCurrency),
            persons = listOf(currentPerson, otherPerson),
            primaryCurrency = primaryCurrency,
        )
        val expensesDetails = ExpensesDetails(
            event = eventDetails,
            expenses = listOf(
                expense(
                    expenseId = 3L,
                    expenseType = ExpenseType.Spending,
                    totalAmount = 120,
                    timestamp = "2026-03-28T20:00:00Z",
                    description = "Dinner",
                ),
                expense(
                    expenseId = 2L,
                    expenseType = ExpenseType.Replenishment,
                    totalAmount = 15,
                    timestamp = "2026-03-28T10:00:00Z",
                    description = "Refund",
                ),
                expense(
                    expenseId = 1L,
                    expenseType = ExpenseType.Spending,
                    totalAmount = 60,
                    timestamp = "2026-03-27T23:00:00Z",
                    description = "Museum",
                ),
            ),
            debtCalculator = DebtCalculator(emptyList(), primaryCurrency),
        )

        private fun expense(
            expenseId: Long,
            expenseType: ExpenseType,
            totalAmount: Int,
            timestamp: String,
            description: String,
        ): Expense {
            return Expense(
                expenseId = expenseId,
                serverId = "expense-$expenseId",
                currency = primaryCurrency,
                expenseType = expenseType,
                person = currentPerson,
                subjectExpenseSplitWithPersons = listOf(
                    ExpenseSplitWithPerson(
                        expenseSplitId = expenseId,
                        expenseId = expenseId,
                        person = otherPerson,
                        originalAmount = totalAmount.toBigDecimal(),
                        exchangedAmount = totalAmount.toBigDecimal(),
                    ),
                ),
                isCustomRate = false,
                timestamp = Instant.parse(timestamp),
                description = description,
                clientCreateId = "expense-$expenseId",
            )
        }
    }

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val currentEventFlow = MutableStateFlow<EventDetails?>(Fixtures.eventDetails)
    private val currentPersonIdFlow = MutableStateFlow<Long?>(Fixtures.currentPerson.id)
    private val eventsFlow = MutableStateFlow(emptyList<Event>())

    private val navigationController = mockk<NavigationController>(relaxed = true) {
        justRun { navigateTo(any()) }
    }
    private val getCurrentEventStateUseCase = mockk<GetCurrentEventStateUseCase>(relaxed = true) {
        every { currentEvent } returns currentEventFlow
    }
    private val eventDeletionStateManager = mockk<EventDeletionStateManager>(relaxed = true) {
        every { eventsDeletionState } returns MutableStateFlow(emptyMap())
    }
    private val getEventsUseCase = mockk<GetEventsUseCase>(relaxed = true) {
        every { getEvents() } returns eventsFlow
    }
    private val joinEventUseCase = mockk<JoinEventUseCase>(relaxed = true)
    private val leaveEventUseCase = mockk<LeaveEventUseCase>(relaxed = true)
    private val deleteEventUseCase = mockk<DeleteEventUseCase>(relaxed = true)
    private val getExpensesDetailsUseCase = mockk<GetExpensesDetailsUseCase>(relaxed = true) {
        every { getExpensesDetails(any()) } returns flowOf(Fixtures.expensesDetails)
    }
    private val requestExpensesRefreshUseCase = mockk<RequestExpensesRefreshUseCase>(relaxed = true)
    private val eventsSyncStateHolder = mockk<EventsSyncStateHolder>(relaxed = true) {
        every { getStateFor(any()) } returns MutableStateFlow(false)
    }
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
    fun `state should expose timeline data and react to visible section changes`() = testScope.runTest {
        val viewModel = createViewModel()
        runCurrent()
        advanceUntilIdle()

        viewModel.state.test {
            val initial = when (val firstState = awaitItem()) {
                is SimpleScreenState.Loading -> assertIs<SimpleScreenState.Success<ExpensesPaneUiModel>>(awaitItem())
                is SimpleScreenState.Success<*> -> assertIs<SimpleScreenState.Success<ExpensesPaneUiModel>>(firstState)
                else -> error("Unexpected initial state: $firstState")
            }
            val initialData = initial.data as Expenses
            assertEquals("180 EUR", initialData.totalSpending)
            assertEquals(listOf("2026-03-28", "2026-03-27"), initialData.dayChips.map { it.dayKey })
            assertEquals("2026-03-28", initialData.dayChips.selectedDayKey())

            viewModel.onVisibleDayChanged("2026-03-27")
            advanceUntilIdle()

            val afterScroll = assertIs<SimpleScreenState.Success<ExpensesPaneUiModel>>(awaitItem())
            val afterScrollData = afterScroll.data as Expenses
            assertEquals("2026-03-27", afterScrollData.dayChips.selectedDayKey())

            viewModel.onDayChipClick("2026-03-28")
            advanceUntilIdle()

            val afterChipClick = assertIs<SimpleScreenState.Success<ExpensesPaneUiModel>>(awaitItem())
            val afterChipClickData = afterChipClick.data as Expenses
            assertEquals("2026-03-28", afterChipClickData.dayChips.selectedDayKey())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event without expenses exposes ready pane metadata`() = testScope.runTest {
        every { getExpensesDetailsUseCase.getExpensesDetails(any()) } returns flowOf(Fixtures.expensesDetails.copy(expenses = emptyList()))
        val viewModel = createViewModel()
        viewModel.state.test {
            runCurrent()
            advanceUntilIdle()

            val state = assertIs<SimpleScreenState.Success<ExpensesPaneUiModel>>(viewModel.state.value)
            val eventExpenses = assertIs<Expenses>(state.data)
            assertEquals(emptyList(), eventExpenses.daySections)
            assertEquals("Trip", eventExpenses.eventName)
            assertEquals("Alex", eventExpenses.currentPersonName)
            assertEquals(2, eventExpenses.personCount)
            assertEquals("EUR", eventExpenses.currencyCode)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `first use actions navigate to invite and invitation panes`() = testScope.runTest {
        val viewModel = createViewModel()
        viewModel.onInviteClick()
        viewModel.onJoinEventClick()

        verify { navigationController.navigateTo(InviteEventPaneDestination) }
        verify { navigationController.navigateTo(JoinInvitationPaneDestination) }
    }

    @Test
    fun `opening events list clears selection once while the operation is active`() = testScope.runTest {
        val leaving = CompletableDeferred<Unit>()
        coEvery { leaveEventUseCase.leaveEvent() } coAnswers { leaving.await() }
        val viewModel = createViewModel()
        viewModel.onOpenEventsListClick()
        viewModel.onOpenEventsListClick()
        runCurrent()

        coVerify(exactly = 1) { leaveEventUseCase.leaveEvent() }

        leaving.complete(Unit)
        runCurrent()
        viewModel.onOpenEventsListClick()
        runCurrent()
        coVerify(exactly = 2) { leaveEventUseCase.leaveEvent() }
    }

    @Test
    fun `switching person recomputes debts and prefills repayment from the new identity`() = testScope.runTest {
        val expenses = listOf(Fixtures.expensesDetails.expenses.first())
        every { getExpensesDetailsUseCase.getExpensesDetails(any()) } returns flowOf(
            Fixtures.expensesDetails.copy(
                expenses = expenses,
                debtCalculator = DebtCalculator(expenses, Fixtures.primaryCurrency),
            )
        )
        val viewModel = createViewModel()

        viewModel.state.test {
            runCurrent()
            val initial = assertIs<Expenses>(assertIs<SimpleScreenState.Success<ExpensesPaneUiModel>>(expectMostRecentItem()).data)
            assertEquals(emptyList(), initial.debts)
            assertEquals("0 EUR", initial.totalDebt)

            currentPersonIdFlow.value = Fixtures.otherPerson.id
            runCurrent()

            val updated = assertIs<Expenses>(assertIs<SimpleScreenState.Success<ExpensesPaneUiModel>>(expectMostRecentItem()).data)
            assertEquals(Fixtures.otherPerson.id, updated.currentPersonId)
            assertEquals("Ben", updated.currentPersonName)
            assertEquals("120 EUR", updated.totalDebt)
            val debt = updated.debts.single()
            assertEquals(Fixtures.currentPerson.id, debt.personId)
            assertEquals("Alex", debt.personName)
            assertEquals("120", debt.amount)
            assertEquals("EUR", debt.currencyCode)

            viewModel.onReplenishmentClick(debt)

            verify {
                navigationController.navigateTo(
                    AddExpensePaneDestination(
                        replenishment = AddExpensePaneDestination.Replenishment(
                            fromPersonId = Fixtures.otherPerson.id,
                            toPersonId = Fixtures.currentPerson.id,
                            currencyCode = "EUR",
                            amount = "120",
                        )
                    )
                )
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `unknown selected person returns to local groups and recovers when a member is selected`() = testScope.runTest {
        eventsFlow.value = listOf(Fixtures.event)
        currentPersonIdFlow.value = 999L
        val viewModel = createViewModel()

        viewModel.state.test {
            runCurrent()
            val local = assertIs<LocalEvents>(assertIs<SimpleScreenState.Success<ExpensesPaneUiModel>>(expectMostRecentItem()).data)
            assertEquals(listOf(Fixtures.event.id), local.localEvents.events.map { it.eventId })

            currentPersonIdFlow.value = Fixtures.currentPerson.id
            runCurrent()

            val recovered = assertIs<Expenses>(assertIs<SimpleScreenState.Success<ExpensesPaneUiModel>>(expectMostRecentItem()).data)
            assertEquals(Fixtures.event.id, recovered.eventId)
            assertEquals("Alex", recovered.currentPersonName)
            assertEquals("180 EUR", recovered.totalSpending)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clearing current group cancels its expense updates and shows saved local groups`() = testScope.runTest {
        eventsFlow.value = listOf(Fixtures.event)
        val expenseDetails = MutableStateFlow(Fixtures.expensesDetails)
        every { getExpensesDetailsUseCase.getExpensesDetails(any()) } returns expenseDetails
        val viewModel = createViewModel()

        viewModel.state.test {
            runCurrent()
            assertIs<Expenses>(assertIs<SimpleScreenState.Success<ExpensesPaneUiModel>>(expectMostRecentItem()).data)

            currentEventFlow.value = null
            runCurrent()

            val local = assertIs<LocalEvents>(assertIs<SimpleScreenState.Success<ExpensesPaneUiModel>>(expectMostRecentItem()).data)
            assertEquals(listOf("Trip"), local.localEvents.events.map { it.eventName })

            expenseDetails.value = Fixtures.expensesDetails.copy(expenses = emptyList())
            advanceTimeBy(501.milliseconds)
            runCurrent()
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `switching group resets day selection and opens expenses with the new group identity`() = testScope.runTest {
        val secondEvent = Fixtures.eventDetails.copy(
            event = Fixtures.event.copy(id = 20L, serverId = "event-20", name = "Another trip", clientCreateId = "event-20")
        )
        every { getExpensesDetailsUseCase.getExpensesDetails(secondEvent) } returns flowOf(
            Fixtures.expensesDetails.copy(event = secondEvent)
        )
        val viewModel = createViewModel()

        viewModel.state.test {
            runCurrent()
            assertIs<Expenses>(assertIs<SimpleScreenState.Success<ExpensesPaneUiModel>>(expectMostRecentItem()).data)
            viewModel.onDayChipClick("2026-03-27")
            runCurrent()
            val selected = assertIs<Expenses>(assertIs<SimpleScreenState.Success<ExpensesPaneUiModel>>(expectMostRecentItem()).data)
            assertEquals("2026-03-27", selected.dayChips.selectedDayKey())

            currentEventFlow.value = secondEvent
            runCurrent()

            val switched = assertIs<Expenses>(assertIs<SimpleScreenState.Success<ExpensesPaneUiModel>>(expectMostRecentItem()).data)
            assertEquals(20L, switched.eventId)
            assertEquals("Another trip", switched.eventName)
            assertEquals("2026-03-28", switched.dayChips.selectedDayKey())
            val expense = switched.daySections.first().expenses.first()
            viewModel.onExpenseClick(expense)
            verify { navigationController.navigateTo(ExpenseItemPaneDestination(expenseId = expense.expenseId, eventId = 20L)) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `expense actions do not navigate while loading or showing local groups`() = testScope.runTest {
        currentPersonIdFlow.value = null
        eventsFlow.value = listOf(Fixtures.event)
        val viewModel = createViewModel()
        val expense = mockk<Expenses.ExpenseUiModel>()
        val creditor = mockk<DebtShortUiModel>()

        viewModel.onExpenseClick(expense)
        viewModel.onReplenishmentClick(creditor)
        verify(exactly = 0) { navigationController.navigateTo(any()) }

        viewModel.state.test {
            runCurrent()
            assertIs<LocalEvents>(assertIs<SimpleScreenState.Success<ExpensesPaneUiModel>>(expectMostRecentItem()).data)
            viewModel.onExpenseClick(expense)
            viewModel.onReplenishmentClick(creditor)
            verify(exactly = 0) { navigationController.navigateTo(any()) }
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
            leaveEventUseCase = leaveEventUseCase,
            deleteEventUseCase = deleteEventUseCase,
            getExpensesDetailsUseCase = getExpensesDetailsUseCase,
            requestExpensesRefreshUseCase = requestExpensesRefreshUseCase,
            eventsSyncStateHolder = eventsSyncStateHolder,
            settingsRepository = settingsRepository,
            timelineUiModelFactory = ExpensesTimelineUiModelFactory(
                stringProvider = object : StringProvider {
                    override suspend fun getString(stringResource: StringResource): String {
                        return when (stringResource) {
                            Res.string.expenses_today -> "Today"
                            Res.string.expenses_yesterday -> "Yesterday"
                            else -> error("Unexpected string resource request: $stringResource")
                        }
                    }

                    override suspend fun getString(stringResource: StringResource, vararg formatArgs: Any): String {
                        return getString(stringResource)
                    }
                },
                timeZoneProvider = { TimeZone.UTC },
                localeProvider = { Locale("en") },
                nowProvider = { Instant.parse("2026-03-28T12:00:00Z") },
            ),
            unconfinedDispatcher = testDispatcher,
            viewModelScope = this.testScope.backgroundScope,
        )
    }

    private fun List<Expenses.DayChipUiModel>.selectedDayKey(): String {
        return single { it.isSelected }.dayKey
    }
}
