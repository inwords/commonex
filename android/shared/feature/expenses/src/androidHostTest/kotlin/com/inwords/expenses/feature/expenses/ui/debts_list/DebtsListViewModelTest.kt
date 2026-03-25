package com.inwords.expenses.feature.expenses.ui.debts_list

import app.cash.turbine.test
import com.inwords.expenses.core.navigation.Destination
import com.inwords.expenses.core.navigation.NavigationController
import com.inwords.expenses.core.ui.utils.SimpleScreenState
import com.inwords.expenses.feature.events.domain.GetCurrentEventStateUseCase
import com.inwords.expenses.feature.events.domain.model.Currency
import com.inwords.expenses.feature.events.domain.model.Event
import com.inwords.expenses.feature.events.domain.model.EventDetails
import com.inwords.expenses.feature.events.domain.model.Person
import com.inwords.expenses.feature.expenses.domain.DebtCalculator
import com.inwords.expenses.feature.expenses.domain.GetExpensesDetailsUseCase
import com.inwords.expenses.feature.expenses.domain.model.ExpensesDetails
import com.inwords.expenses.feature.expenses.ui.add.AddExpensePaneDestination
import com.inwords.expenses.feature.expenses.ui.common.DebtShortUiModel
import com.ionspin.kotlin.bignum.decimal.BigDecimal
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

@OptIn(ExperimentalCoroutinesApi::class)
internal class DebtsListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val navigationController = mockk<NavigationController>(relaxed = true)
    private val getCurrentEventStateUseCase = mockk<GetCurrentEventStateUseCase>(relaxed = true)
    private val getExpensesDetailsUseCase = mockk<GetExpensesDetailsUseCase>(relaxed = true)

    private val event = Event(1L, null, "Trip", "1234", 1L)
    private val person = Person(1L, null, "Alice")
    private val currency = Currency(1L, null, "EUR", "Euro", rate = BigDecimal.ONE)
    private val eventDetails = EventDetails(
        event = event,
        currencies = listOf(currency),
        persons = listOf(person),
        primaryCurrency = currency,
    )
    private val expensesDetails = ExpensesDetails(
        event = eventDetails,
        expenses = emptyList(),
        debtCalculator = DebtCalculator(emptyList(), currency),
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
    fun state_emitsLoadingThenSuccess_whenEventAndExpensesDetailsEmitted() = runTest {
        val currentEventFlow = MutableStateFlow<EventDetails?>(eventDetails)
        every { getCurrentEventStateUseCase.currentEvent } returns currentEventFlow
        every { getExpensesDetailsUseCase.getExpensesDetails(any()) } returns flowOf(expensesDetails)

        val viewModel = DebtsListViewModel(
            navigationController = navigationController,
            getCurrentEventStateUseCase = getCurrentEventStateUseCase,
            getExpensesDetailsUseCase = getExpensesDetailsUseCase,
            viewModelScope = backgroundScope,
        )
        runCurrent()
        advanceUntilIdle()

        viewModel.state.test {
            skipItems(1)
            val item = awaitItem()
            assertTrue(item is SimpleScreenState.Success, "Expected Success, got $item")
            val data = item.data
            assertEquals("Trip", data.eventName)
            assertTrue(data.creditors.isEmpty())
        }
    }

    @Test
    fun onNavIconClicked_popsBackStack() = runTest {
        val currentEventFlow = MutableStateFlow<EventDetails?>(eventDetails)
        every { getCurrentEventStateUseCase.currentEvent } returns currentEventFlow
        every { getExpensesDetailsUseCase.getExpensesDetails(any()) } returns flowOf(expensesDetails)

        val viewModel = DebtsListViewModel(
            navigationController = navigationController,
            getCurrentEventStateUseCase = getCurrentEventStateUseCase,
            getExpensesDetailsUseCase = getExpensesDetailsUseCase,
            viewModelScope = backgroundScope,
        )
        runCurrent()
        advanceUntilIdle()

        viewModel.onNavIconClicked()

        verify(exactly = 1) { navigationController.popBackStack() }
    }

    @Test
    fun onReplenishmentClick_navigatesToAddExpensePaneDestinationWithReplenishment() = runTest {
        val currentEventFlow = MutableStateFlow<EventDetails?>(eventDetails)
        every { getCurrentEventStateUseCase.currentEvent } returns currentEventFlow
        every { getExpensesDetailsUseCase.getExpensesDetails(any()) } returns flowOf(expensesDetails)

        val viewModel = DebtsListViewModel(
            navigationController = navigationController,
            getCurrentEventStateUseCase = getCurrentEventStateUseCase,
            getExpensesDetailsUseCase = getExpensesDetailsUseCase,
            viewModelScope = backgroundScope,
        )
        runCurrent()
        advanceUntilIdle()

        val debtor = DebtsListPaneUiModel.PersonUiModel(personId = 1L, personName = "Alice")
        val creditor = DebtShortUiModel(
            personId = 2L,
            personName = "Bob",
            currencyCode = "EUR",
            currencyName = "Euro",
            amount = "10",
        )
        viewModel.onReplenishmentClick(debtor, creditor)

        val destSlot = slot<Destination>()
        verify(exactly = 1) { navigationController.navigateTo(capture(destSlot)) }
        val dest = destSlot.captured as AddExpensePaneDestination
        assertTrue(dest.replenishment != null)
        assertTrue(dest.replenishment.fromPersonId == 1L && dest.replenishment.toPersonId == 2L)
        assertTrue(dest.replenishment.currencyCode == "EUR" && dest.replenishment.amount == "10")
    }
}
