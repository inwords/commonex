package com.inwords.expenses.feature.expenses.ui.list.bottom_sheet.item

import androidx.compose.ui.text.intl.Locale
import app.cash.turbine.test
import com.inwords.expenses.core.navigation.NavigationController
import com.inwords.expenses.core.ui.utils.SimpleScreenState
import com.inwords.expenses.core.ui.utils.StringProvider
import com.inwords.expenses.feature.events.domain.GetCurrentEventStateUseCase
import com.inwords.expenses.feature.events.domain.model.Currency
import com.inwords.expenses.feature.events.domain.model.Event
import com.inwords.expenses.feature.events.domain.model.EventDetails
import com.inwords.expenses.feature.events.domain.model.Person
import com.inwords.expenses.feature.expenses.domain.model.Expense
import com.inwords.expenses.feature.expenses.domain.model.ExpenseSplitWithPerson
import com.inwords.expenses.feature.expenses.domain.model.ExpenseType
import com.inwords.expenses.feature.expenses.domain.store.ExpensesLocalStore
import com.inwords.expenses.feature.expenses.ui.add.AddExpensePaneDestination
import com.inwords.expenses.feature.expenses.ui.list.ExpenseCorrectionStatusTextFactory
import com.inwords.expenses.feature.expenses.ui.list.dialog.revert.ExpenseRevertDialogDestination
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import expenses.shared.feature.expenses.generated.resources.Res
import expenses.shared.feature.expenses.generated.resources.expenses_status_edited_on
import expenses.shared.feature.expenses.generated.resources.expenses_status_reverted_on
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
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
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.StringResource
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
internal class ExpenseItemPaneViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val navigationController = mockk<NavigationController>(relaxed = true)
    private val getCurrentEventStateUseCase = mockk<GetCurrentEventStateUseCase>(relaxed = true)
    private val expensesLocalStore = mockk<ExpensesLocalStore>(relaxed = true)

    private val event = Event(1L, null, "Trip", "1234", 1L, "event-client-1")
    private val person = Person(1L, null, "Alice", "person-client-1")
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
        isCustomRate = false,
        timestamp = Instant.fromEpochMilliseconds(0),
        description = "Lunch",
        clientCreateId = "expense-client-10",
        revertsExpenseId = null,
        replacesExpenseId = null,
    )

    private val testStringProvider = object : StringProvider {
        override suspend fun getString(stringResource: StringResource): String {
            return error("Unexpected string resource request: ${stringResource.key}")
        }

        override suspend fun getString(stringResource: StringResource, vararg formatArgs: Any): String {
            return when (stringResource) {
                Res.string.expenses_status_edited_on -> "Edited on ${formatArgs.single()}"
                Res.string.expenses_status_reverted_on -> "Reverted on ${formatArgs.single()}"
                else -> error("Unexpected string resource request: ${stringResource.key}")
            }
        }
    }
    private val correctionStatusTextFactory = ExpenseCorrectionStatusTextFactory(
        stringProvider = testStringProvider,
        timeZoneProvider = { TimeZone.UTC },
        localeProvider = { Locale("en") },
    )

    private fun stubExpensePaneStore(
        targetExpense: Expense = expense,
        hasCorrection: Boolean = false,
        correction: Expense? = null,
    ) {
        every { expensesLocalStore.getExpenseFlow(targetExpense.expenseId) } returns flowOf(targetExpense)
        every { expensesLocalStore.hasCorrectionForFlow(targetExpense.expenseId) } returns flowOf(hasCorrection)
        every { expensesLocalStore.getCorrectionForTargetFlow(targetExpense.expenseId) } returns flowOf(correction)
    }

    private fun createViewModel(
        expenseId: Long = expense.expenseId,
        viewModelScope: CoroutineScope,
    ): ExpenseItemPaneViewModel {
        return ExpenseItemPaneViewModel(
            navigationController = navigationController,
            getCurrentEventStateUseCase = getCurrentEventStateUseCase,
            expensesLocalStore = expensesLocalStore,
            correctionStatusTextFactory = correctionStatusTextFactory,
            expenseId = expenseId,
            eventId = event.id,
            viewModelScope = viewModelScope,
        )
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
    fun state_emitsLoadingThenSuccess_whenEventAndExpensePresent() = runTest {
        val currentEventFlow = MutableStateFlow<EventDetails?>(eventDetails)
        every { getCurrentEventStateUseCase.currentEvent } returns currentEventFlow
        stubExpensePaneStore()

        val viewModel = createViewModel(viewModelScope = backgroundScope)
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
        stubExpensePaneStore()

        val viewModel = createViewModel(viewModelScope = backgroundScope)
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

    @Test
    fun onEditExpenseClick_navigatesToAddExpensePaneDestinationWithReplacementId() = runTest {
        val currentEventFlow = MutableStateFlow<EventDetails?>(eventDetails)
        every { getCurrentEventStateUseCase.currentEvent } returns currentEventFlow
        stubExpensePaneStore()

        val viewModel = createViewModel(viewModelScope = backgroundScope)
        runCurrent()
        advanceUntilIdle()

        viewModel.state.test {
            skipItems(1)
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.onEditExpenseClick()

        val destSlot = slot<com.inwords.expenses.core.navigation.Destination>()
        verify(exactly = 1) { navigationController.navigateTo(capture(destSlot)) }
        val dest = destSlot.captured as AddExpensePaneDestination
        assertEquals(10L, dest.replacesExpenseId)
    }

    @Test
    fun correctionActions_doNotNavigate_whenExpenseAlreadyHasCorrection() = runTest {
        val currentEventFlow = MutableStateFlow<EventDetails?>(eventDetails)
        val replacement = expense.copy(
            expenseId = 11L,
            clientCreateId = "replacement-client-11",
            replacesExpenseId = expense.expenseId,
        )
        every { getCurrentEventStateUseCase.currentEvent } returns currentEventFlow
        stubExpensePaneStore(hasCorrection = true)

        val viewModel = createViewModel(viewModelScope = backgroundScope)
        runCurrent()
        advanceUntilIdle()

        viewModel.state.test {
            skipItems(1)
            val item = awaitItem()
            assertTrue(item is SimpleScreenState.Success, "Expected Success, got $item")
            assertFalse(item.data.canCorrect)
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.onEditExpenseClick()
        viewModel.onRevertExpenseClick()

        verify(exactly = 0) { navigationController.navigateTo(any()) }
    }

    @Test
    fun state_exposesCorrectionStatus_whenExpenseAlreadyHasCorrection() = runTest {
        val currentEventFlow = MutableStateFlow<EventDetails?>(eventDetails)
        val replacement = expense.copy(
            expenseId = 11L,
            clientCreateId = "replacement-client-11",
            timestamp = Instant.parse("2026-06-12T10:15:00Z"),
            replacesExpenseId = expense.expenseId,
        )
        every { getCurrentEventStateUseCase.currentEvent } returns currentEventFlow
        stubExpensePaneStore(hasCorrection = true, correction = replacement)

        val viewModel = createViewModel(viewModelScope = backgroundScope)
        runCurrent()
        advanceUntilIdle()

        viewModel.state.test {
            skipItems(1)
            val item = awaitItem()
            assertTrue(item is SimpleScreenState.Success, "Expected Success, got $item")
            assertEquals("Edited on 12 June 2026", item.data.statusText)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
