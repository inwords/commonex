package com.inwords.expenses.feature.expenses.ui.list.dialog.revert

import com.inwords.expenses.core.navigation.NavigationController
import com.inwords.expenses.core.ui.utils.StringProvider
import com.inwords.expenses.feature.events.domain.model.Currency
import com.inwords.expenses.feature.events.domain.model.Event
import com.inwords.expenses.feature.events.domain.model.Person
import com.inwords.expenses.feature.events.domain.store.local.EventsLocalStore
import com.inwords.expenses.feature.expenses.domain.ExpensesInteractor
import com.inwords.expenses.feature.expenses.domain.model.Expense
import com.inwords.expenses.feature.expenses.domain.model.ExpenseSplitWithPerson
import com.inwords.expenses.feature.expenses.domain.model.ExpenseType
import com.inwords.expenses.feature.expenses.domain.store.ExpensesLocalStore
import com.inwords.expenses.feature.expenses.ui.list.ExpensesPaneDestination
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import io.mockk.coEvery
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.jetbrains.compose.resources.StringResource
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
internal class ExpenseRevertDialogViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val navigationController = mockk<NavigationController>(relaxed = true) {
        justRun { popBackStack() }
        justRun { popBackStack(any(), any()) }
    }
    private val expensesInteractor = mockk<ExpensesInteractor>(relaxed = true)
    private val expensesLocalStore = mockk<ExpensesLocalStore>(relaxed = true)
    private val eventsLocalStore = mockk<EventsLocalStore>(relaxed = true)

    private val event = Event(1L, "ev-1", "Trip", "1234", 1L)
    private val person = Person(1L, "p1", "Alice")
    private val currency = Currency(1L, null, "EUR", "Euro", rate = BigDecimal.ONE)
    private val expense = Expense(
        expenseId = 10L,
        serverId = "ex-10",
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
    fun onDismiss_popsBackStack() = runTest {
        val viewModel = ExpenseRevertDialogViewModel(
            navigationController = navigationController,
            expensesInteractor = expensesInteractor,
            expensesLocalStore = expensesLocalStore,
            eventsLocalStore = eventsLocalStore,
            expenseId = 10L,
            eventId = 1L,
            expenseDescription = "Lunch",
            stringProvider = object : StringProvider {
                override suspend fun getString(stringResource: StringResource): String = "Revert"
                override suspend fun getString(stringResource: StringResource, vararg formatArgs: Any): String = "Revert"
            },
            viewModelScope = backgroundScope,
        )

        viewModel.onDismiss()

        verify(exactly = 1) { navigationController.popBackStack() }
    }

    @Test
    fun onConfirmRevert_whenEventAndExpensePresent_revertsAndPopsToExpensesPane() = runTest {
        coEvery { eventsLocalStore.getEvent(1L) } returns event
        coEvery { expensesLocalStore.getExpense(10L) } returns expense

        val viewModel = ExpenseRevertDialogViewModel(
            navigationController = navigationController,
            expensesInteractor = expensesInteractor,
            expensesLocalStore = expensesLocalStore,
            eventsLocalStore = eventsLocalStore,
            expenseId = 10L,
            eventId = 1L,
            expenseDescription = "Lunch",
            stringProvider = object : StringProvider {
                override suspend fun getString(stringResource: StringResource): String = "Revert"
                override suspend fun getString(stringResource: StringResource, vararg formatArgs: Any): String = "Revert"
            },
            viewModelScope = backgroundScope,
        )

        viewModel.onConfirmRevert()
        runCurrent()
        advanceUntilIdle()

        verify(exactly = 1) {
            navigationController.popBackStack(
                toDestination = ExpensesPaneDestination,
                inclusive = false
            )
        }
    }
}
