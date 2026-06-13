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
import com.inwords.expenses.feature.events.domain.model.Currency
import com.inwords.expenses.feature.events.domain.model.Event
import com.inwords.expenses.feature.events.domain.model.EventDetails
import com.inwords.expenses.feature.events.domain.model.Person
import com.inwords.expenses.feature.expenses.domain.DebtCalculator
import com.inwords.expenses.feature.expenses.domain.GetExpensesDetailsUseCase
import com.inwords.expenses.feature.expenses.domain.RequestExpensesRefreshUseCase
import com.inwords.expenses.feature.expenses.domain.model.Expense
import com.inwords.expenses.feature.expenses.domain.model.ExpenseSplitWithPerson
import com.inwords.expenses.feature.expenses.domain.model.ExpenseType
import com.inwords.expenses.feature.expenses.domain.model.ExpensesDetails
import com.inwords.expenses.feature.expenses.ui.list.ExpensesPaneUiModel.Expenses
import com.inwords.expenses.feature.settings.api.SettingsRepository
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import expenses.shared.feature.expenses.generated.resources.Res
import expenses.shared.feature.expenses.generated.resources.expenses_status_edited
import expenses.shared.feature.expenses.generated.resources.expenses_status_reverted
import expenses.shared.feature.expenses.generated.resources.expenses_today
import expenses.shared.feature.expenses.generated.resources.expenses_yesterday
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
                revertsExpenseId = null,
                replacesExpenseId = null,
            )
        }
    }

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val currentEventFlow = MutableStateFlow<EventDetails?>(Fixtures.eventDetails)
    private val currentPersonIdFlow = MutableStateFlow<Long?>(Fixtures.currentPerson.id)

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
        every { getEvents() } returns MutableStateFlow(emptyList())
    }
    private val joinEventUseCase = mockk<JoinEventUseCase>(relaxed = true)
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
            timelineUiModelFactory = run {
                val stringProvider = object : StringProvider {
                    override suspend fun getString(stringResource: StringResource): String {
                        return when (stringResource) {
                            Res.string.expenses_today -> "Today"
                            Res.string.expenses_yesterday -> "Yesterday"
                            Res.string.expenses_status_edited -> "Edited"
                            Res.string.expenses_status_reverted -> "Reverted"
                            else -> error("Unexpected string resource request: ${stringResource.key}")
                        }
                    }

                    override suspend fun getString(stringResource: StringResource, vararg formatArgs: Any): String {
                        return getString(stringResource)
                    }
                }
                ExpensesTimelineUiModelFactory(
                    correctionStatusFactory = ExpenseCorrectionStatusTextFactory(
                        stringProvider = stringProvider,
                        timeZoneProvider = { TimeZone.UTC },
                        localeProvider = { Locale("en") },
                    ),
                    stringProvider = stringProvider,
                    timeZoneProvider = { TimeZone.UTC },
                    localeProvider = { Locale("en") },
                    nowProvider = { Instant.parse("2026-03-28T12:00:00Z") },
                )
            },
            unconfinedDispatcher = testDispatcher,
            viewModelScope = this.testScope.backgroundScope,
        )
    }

    private fun List<Expenses.DayChipUiModel>.selectedDayKey(): String {
        return single { it.isSelected }.dayKey
    }
}
