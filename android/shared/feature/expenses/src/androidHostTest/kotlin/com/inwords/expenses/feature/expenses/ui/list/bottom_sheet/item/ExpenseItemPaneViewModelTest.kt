package com.inwords.expenses.feature.expenses.ui.list.bottom_sheet.item

import app.cash.turbine.test
import com.inwords.expenses.core.navigation.NavigationController
import com.inwords.expenses.core.ui.utils.SimpleScreenState
import com.inwords.expenses.feature.events.domain.GetCurrentEventStateUseCase
import com.inwords.expenses.feature.events.domain.model.Currency
import com.inwords.expenses.feature.events.domain.model.Event
import com.inwords.expenses.feature.events.domain.model.EventDetails
import com.inwords.expenses.feature.events.domain.model.Person
import com.inwords.expenses.feature.expenses.domain.model.Expense
import com.inwords.expenses.feature.expenses.domain.model.ExpenseSplitWithPerson
import com.inwords.expenses.feature.expenses.domain.model.ExpenseType
import com.inwords.expenses.feature.expenses.domain.store.ExpensesLocalStore
import com.inwords.expenses.feature.expenses.ui.list.dialog.revert.ExpenseRevertDialogDestination
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
internal class ExpenseItemPaneViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val navigationController = mockk<NavigationController>(relaxed = true)
    private val getCurrentEventStateUseCase = mockk<GetCurrentEventStateUseCase>(relaxed = true)
    private val expensesLocalStore = mockk<ExpensesLocalStore>(relaxed = true)

    private val event = Event(1L, null, "Trip", "1234", 1L)
    private val person = Person(1L, null, "Alice")
    private val currency = Currency(1L, null, "EUR", "Euro", rate = BigDecimal.ONE)
    private val eventDetails = EventDetails(
        event = event,
        currencies = listOf(currency),
        persons = listOf(person),
        primaryCurrency = currency,
    )
    private val expense = Expense(
        expenseId = 10L,
        serverId = null,
        currency = currency,
        expenseType = ExpenseType.Spending,
        person = person,
        subjectExpenseSplitWithPersons = listOf(
            ExpenseSplitWithPerson(1L, 10L, person, 10.toBigDecimal(), 10.toBigDecimal())
        ),
        timestamp = Instant.fromEpochMilliseconds(0),
        description = "Lunch",
    )

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun state_emitsLoadingThenSuccess_whenEventAndExpensePresent() = runTest {
        val currentEventFlow = MutableStateFlow<EventDetails?>(eventDetails)
        every { getCurrentEventStateUseCase.currentEvent } returns currentEventFlow
        every { expensesLocalStore.getExpenseFlow(10L) } returns flowOf(expense)

        val viewModel = ExpenseItemPaneViewModel(
            navigationController = navigationController,
            getCurrentEventStateUseCase = getCurrentEventStateUseCase,
            expensesLocalStore = expensesLocalStore,
            expenseId = 10L,
            eventId = 1L,
            viewModelScope = backgroundScope,
        )
        runCurrent()
        advanceUntilIdle()

        viewModel.state.test {
            skipItems(1)
            val item = awaitItem()
            assertTrue(item is SimpleScreenState.Success, "Expected Success, got $item")
            assertEquals("Lunch", item.data.description)
        }
    }

    @Test
    fun onRevertExpenseClick_navigatesToExpenseRevertDialogDestination() = runTest {
        val currentEventFlow = MutableStateFlow<EventDetails?>(eventDetails)
        every { getCurrentEventStateUseCase.currentEvent } returns currentEventFlow
        every { expensesLocalStore.getExpenseFlow(10L) } returns flowOf(expense)

        val viewModel = ExpenseItemPaneViewModel(
            navigationController = navigationController,
            getCurrentEventStateUseCase = getCurrentEventStateUseCase,
            expensesLocalStore = expensesLocalStore,
            expenseId = 10L,
            eventId = 1L,
            viewModelScope = backgroundScope,
        )
        runCurrent()
        advanceUntilIdle()

        viewModel.state.test {
            skipItems(1)
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.onRevertExpenseClick()

        val destSlot = slot<com.inwords.expenses.core.navigation.Destination>()
        verify(exactly = 1) { navigationController.navigateTo(capture(destSlot)) }
        val dest = destSlot.captured as ExpenseRevertDialogDestination
        assertTrue(dest.expenseId == 10L && dest.eventId == 1L && dest.expenseDescription == "Lunch")
    }
}
