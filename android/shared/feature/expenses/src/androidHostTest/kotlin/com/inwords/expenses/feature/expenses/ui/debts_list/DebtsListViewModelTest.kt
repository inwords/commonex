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
import com.inwords.expenses.feature.expenses.domain.model.Expense
import com.inwords.expenses.feature.expenses.domain.model.ExpenseSplitWithPerson
import com.inwords.expenses.feature.expenses.domain.model.ExpenseType
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
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
internal class DebtsListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val navigationController = mockk<NavigationController>(relaxed = true)
    private val getCurrentEventStateUseCase = mockk<GetCurrentEventStateUseCase>(relaxed = true)
    private val getExpensesDetailsUseCase = mockk<GetExpensesDetailsUseCase>(relaxed = true)

    private val event = Event(1L, null, "Trip", "1234", 1L, "event-client-1")
    private val person = Person(1L, null, "Alice", "person-client-1")
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
            assertEquals("EUR", data.primaryCurrencyCode)
            assertTrue(data.debtors.isEmpty())
        }
    }

    @Test
    fun state_groupsAllDebtPairsAndUpdatesWhenExpensesClear() = runTest {
        val bob = Person(2L, null, "Bob", "person-client-2")
        val charlie = Person(3L, null, "Charlie", "person-client-3")
        val diana = Person(4L, null, "Diana", "person-client-4")
        val eventWithPersons = eventDetails.copy(persons = listOf(person, bob, charlie, diana))
        val expenses = listOf(
            createExpense(1L, person, listOf(bob to "10", charlie to "20")),
            createExpense(2L, diana, listOf(bob to "30")),
        )
        val expensesFlow = MutableStateFlow(
            ExpensesDetails(eventWithPersons, expenses, DebtCalculator(expenses, currency))
        )
        every { getCurrentEventStateUseCase.currentEvent } returns MutableStateFlow(eventWithPersons)
        every { getExpensesDetailsUseCase.getExpensesDetails(any()) } returns expensesFlow

        val viewModel = DebtsListViewModel(
            navigationController = navigationController,
            getCurrentEventStateUseCase = getCurrentEventStateUseCase,
            getExpensesDetailsUseCase = getExpensesDetailsUseCase,
            viewModelScope = backgroundScope,
        )

        viewModel.state.test {
            assertEquals(SimpleScreenState.Loading, awaitItem())
            val data = assertIs<SimpleScreenState.Success<DebtsListPaneUiModel>>(awaitItem()).data
            assertEquals("EUR", data.primaryCurrencyCode)
            assertEquals(2, data.debtors.size)
            val debtors = data.debtors.associateBy { it.person.personId }
            assertEquals(setOf(bob.id, charlie.id), debtors.keys)
            assertEquals(
                mapOf(
                    person.id to DebtShortUiModel(person.id, "Alice", "EUR", "Euro", "10"),
                    diana.id to DebtShortUiModel(diana.id, "Diana", "EUR", "Euro", "30"),
                ),
                debtors.getValue(bob.id).creditors.associateBy { it.personId },
            )
            assertEquals(
                listOf(DebtShortUiModel(person.id, "Alice", "EUR", "Euro", "20")),
                debtors.getValue(charlie.id).creditors,
            )

            expensesFlow.value = expensesDetails.copy(event = eventWithPersons)

            val settledData = assertIs<SimpleScreenState.Success<DebtsListPaneUiModel>>(awaitItem()).data
            assertEquals("Trip", settledData.eventName)
            assertEquals("EUR", settledData.primaryCurrencyCode)
            assertTrue(settledData.debtors.isEmpty())
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

    private fun createExpense(id: Long, payer: Person, splits: List<Pair<Person, String>>): Expense {
        return Expense(
            expenseId = id,
            serverId = null,
            currency = currency,
            expenseType = ExpenseType.Spending,
            person = payer,
            subjectExpenseSplitWithPersons = splits.mapIndexed { index, (subject, amount) ->
                ExpenseSplitWithPerson(
                    expenseSplitId = id * 100 + index,
                    expenseId = id,
                    person = subject,
                    originalAmount = BigDecimal.parseString(amount),
                    exchangedAmount = BigDecimal.parseString(amount),
                )
            },
            isCustomRate = false,
            timestamp = Instant.fromEpochSeconds(0),
            description = "Test expense",
            clientCreateId = "expense-$id",
        )
    }
}
