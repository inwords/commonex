package com.inwords.expenses.feature.events.ui.create

import app.cash.turbine.test
import com.inwords.expenses.core.navigation.Destination
import com.inwords.expenses.core.navigation.NavigationController
import com.inwords.expenses.feature.events.domain.CreateEventFromParametersUseCase
import com.inwords.expenses.feature.events.domain.GetCurrenciesUseCase
import com.inwords.expenses.feature.events.domain.model.Currency
import com.inwords.expenses.feature.events.domain.model.EventDetails
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
internal class CreateEventViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val currencyRub = Currency(1L, null, "RUB", "Russian Ruble", BigDecimal.ONE)
    private val currencyEur = Currency(2L, null, "EUR", "Euro", BigDecimal.ONE)
    private val navigationController = mockk<NavigationController>(relaxed = true)
    private val createEventUseCase = mockk<CreateEventFromParametersUseCase>()
    private val expensesDestination = mockk<Destination>()
    private val eventDetails = mockk<EventDetails>()
    private val getCurrenciesUseCase = mockk<GetCurrenciesUseCase> {
        every { getCurrencies() } returns flowOf(listOf(currencyRub, currencyEur))
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
    fun initialState_hasEmptyNamesNoParticipantsAndDefaultCurrency() = runTest {
        val viewModel = createViewModel(backgroundScope)
        viewModel.state.test {
            awaitItem()
            runCurrent()
            val state = viewModel.state.value
            assertEquals("", state.eventName)
            assertEquals("", state.ownerName)
            assertTrue(state.persons.isEmpty())
            assertTrue(state.currencies.any { it.currencyCode == "RUB" && it.selected })
            assertEquals("RUB", state.selectedCurrencyCode)
            assertTrue(state.isCurrencySelectionEnabled)
            assertFalse(state.isConfirmEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun edits_updateOneCohesiveDraft() = runTest {
        val viewModel = createViewModel(backgroundScope)
        viewModel.state.test {
            awaitItem()
            runCurrent()
            viewModel.onEventNameChanged("  Weekend trip  ")
            viewModel.onOwnerNameChanged("Alex")
            viewModel.onAddParticipantClicked()
            viewModel.onParticipantNameChanged(0, "Sam")
            viewModel.onAddParticipantClicked()
            viewModel.onParticipantNameChanged(1, "Jo")
            viewModel.onCurrencyClicked(CreateEventPaneUiModel.CurrencyInfoUiModel("Euro", "EUR", false))
            runCurrent()

            val state = viewModel.state.value
            assertEquals("  Weekend trip  ", state.eventName)
            assertEquals("Alex", state.ownerName)
            assertEquals(listOf("Sam", "Jo"), state.persons)
            assertEquals("EUR", state.currencies.single { it.selected }.currencyCode)
            assertEquals("EUR", state.selectedCurrencyCode)
            assertTrue(state.isConfirmEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun confirm_requiresBothEventAndOwnerNames() = runTest {
        val viewModel = createViewModel(backgroundScope)
        viewModel.state.test {
            awaitItem()
            runCurrent()
            viewModel.onConfirmClicked()
            viewModel.onEventNameChanged("Trip")
            viewModel.onOwnerNameChanged("   ")
            runCurrent()
            viewModel.onConfirmClicked()
            viewModel.onEventNameChanged("   ")
            viewModel.onOwnerNameChanged("Alex")
            runCurrent()
            viewModel.onConfirmClicked()
            runCurrent()

            coVerify(exactly = 0) { createEventUseCase.createEvent(any(), any(), any(), any()) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun confirm_requiredNameCleared_doesNotCreate() = runTest {
        val viewModel = createViewModel(backgroundScope)
        viewModel.state.test {
            awaitItem()
            runCurrent()
            viewModel.onEventNameChanged("Trip")
            viewModel.onOwnerNameChanged("Alex")
            runCurrent()
            assertTrue(viewModel.state.value.isConfirmEnabled)

            viewModel.onEventNameChanged("   ")
            runCurrent()
            viewModel.onConfirmClicked()
            runCurrent()

            viewModel.onEventNameChanged("Trip")
            runCurrent()
            assertTrue(viewModel.state.value.isConfirmEnabled)

            viewModel.onOwnerNameChanged("   ")
            runCurrent()
            viewModel.onConfirmClicked()
            runCurrent()

            coVerify(exactly = 0) { createEventUseCase.createEvent(any(), any(), any(), any()) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun confirm_edits_usesLatestDraftAndCurrency() = runTest {
        coEvery { createEventUseCase.createEvent(any(), any(), any(), any()) } returns eventDetails
        val viewModel = createViewModel(backgroundScope)
        viewModel.state.test {
            awaitItem()
            runCurrent()
            viewModel.onEventNameChanged("Trip")
            viewModel.onOwnerNameChanged("Alex")
            viewModel.onAddParticipantClicked()
            viewModel.onParticipantNameChanged(0, "Sam")
            runCurrent()

            viewModel.onEventNameChanged("Weekend trip")
            viewModel.onOwnerNameChanged("Jo")
            viewModel.onParticipantNameChanged(0, "Pat")
            viewModel.onCurrencyClicked(CreateEventPaneUiModel.CurrencyInfoUiModel("Euro", "EUR", false))
            runCurrent()
            viewModel.onConfirmClicked()
            runCurrent()

            coVerify(exactly = 1) {
                createEventUseCase.createEvent("Weekend trip", "Jo", 2L, listOf("Pat"))
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun confirm_createsWithAllFieldsAndSelectedCurrencyThenReturnsToExpenses() = runTest {
        coEvery { createEventUseCase.createEvent(any(), any(), any(), any()) } returns eventDetails
        val viewModel = createViewModel(backgroundScope)
        viewModel.state.test {
            awaitItem()
            runCurrent()
            viewModel.onEventNameChanged("Trip")
            viewModel.onOwnerNameChanged("Alex")
            viewModel.onAddParticipantClicked()
            viewModel.onParticipantNameChanged(0, "Sam")
            viewModel.onCurrencyClicked(CreateEventPaneUiModel.CurrencyInfoUiModel("Euro", "EUR", false))
            runCurrent()
            viewModel.onConfirmClicked()
            runCurrent()

            coVerify(exactly = 1) {
                createEventUseCase.createEvent("Trip", "Alex", 2L, listOf("Sam"))
            }
            verify(exactly = 1) { navigationController.popBackStack(expensesDestination, false) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun confirm_whileCreating_preventsDuplicateSubmissionsAndDraftEdits() = runTest {
        val creation = CompletableDeferred<EventDetails>()
        coEvery { createEventUseCase.createEvent(any(), any(), any(), any()) } coAnswers { creation.await() }
        val viewModel = createViewModel(backgroundScope)
        viewModel.state.test {
            awaitItem()
            runCurrent()
            viewModel.onEventNameChanged("Trip")
            viewModel.onOwnerNameChanged("Alex")
            viewModel.onAddParticipantClicked()
            viewModel.onParticipantNameChanged(0, "Sam")
            runCurrent()
            viewModel.onConfirmClicked()
            viewModel.onConfirmClicked()
            viewModel.onEventNameChanged("Changed")
            viewModel.onOwnerNameChanged("Changed")
            viewModel.onParticipantNameChanged(0, "Changed")
            viewModel.onAddParticipantClicked()
            viewModel.onCurrencyClicked(CreateEventPaneUiModel.CurrencyInfoUiModel("Euro", "EUR", false))
            runCurrent()

            val state = viewModel.state.value
            assertEquals("Trip", state.eventName)
            assertEquals("Alex", state.ownerName)
            assertEquals(listOf("Sam"), state.persons)
            assertEquals("RUB", state.currencies.single { it.selected }.currencyCode)
            coVerify(exactly = 1) { createEventUseCase.createEvent(any(), any(), any(), any()) }

            creation.complete(eventDetails)
            runCurrent()
            verify(exactly = 1) { navigationController.popBackStack(expensesDestination, false) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun confirm_cancellation_unlocksDraftAndAllowsRetry() = runTest {
        coEvery { createEventUseCase.createEvent(any(), any(), any(), any()) } throws CancellationException()
        val viewModel = createViewModel(backgroundScope)
        viewModel.state.test {
            awaitItem()
            runCurrent()
            viewModel.onEventNameChanged("Trip")
            viewModel.onOwnerNameChanged("Alex")
            runCurrent()
            viewModel.onConfirmClicked()
            runCurrent()

            verify(exactly = 0) { navigationController.popBackStack(any(), any()) }

            viewModel.onEventNameChanged("Updated trip")
            coEvery { createEventUseCase.createEvent(any(), any(), any(), any()) } returns eventDetails
            runCurrent()
            viewModel.onConfirmClicked()
            runCurrent()

            coVerify { createEventUseCase.createEvent("Updated trip", "Alex", 1L, emptyList()) }
            verify(exactly = 1) { navigationController.popBackStack(expensesDestination, false) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun initialState_withoutRub_selectsFirstAvailableCurrency() = runTest {
        every { getCurrenciesUseCase.getCurrencies() } returns flowOf(listOf(currencyEur))
        val viewModel = createViewModel(backgroundScope)
        viewModel.state.test {
            awaitItem()
            runCurrent()
            assertEquals("EUR", viewModel.state.value.currencies.single { it.selected }.currencyCode)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun confirm_withoutCurrency_doesNotCreate() = runTest {
        every { getCurrenciesUseCase.getCurrencies() } returns flowOf(emptyList())
        val viewModel = createViewModel(backgroundScope)
        viewModel.state.test {
            awaitItem()
            runCurrent()
            assertNull(viewModel.state.value.selectedCurrencyCode)
            viewModel.onEventNameChanged("Trip")
            viewModel.onOwnerNameChanged("Alex")
            runCurrent()
            viewModel.onConfirmClicked()
            runCurrent()
            coVerify(exactly = 0) { createEventUseCase.createEvent(any(), any(), any(), any()) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun back_returnsToPreviousScreen() = runTest {
        createViewModel(backgroundScope).onNavIconClicked()
        verify(exactly = 1) { navigationController.popBackStack() }
    }

    private fun createViewModel(scope: CoroutineScope) = CreateEventViewModel(
        navigationController = navigationController,
        getCurrenciesUseCase = getCurrenciesUseCase,
        createEventFromParametersUseCase = createEventUseCase,
        expensesScreenDestination = expensesDestination,
        viewModelScope = scope,
    )
}
