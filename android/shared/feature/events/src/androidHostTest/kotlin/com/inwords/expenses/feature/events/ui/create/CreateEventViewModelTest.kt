package com.inwords.expenses.feature.events.ui.create

import app.cash.turbine.test
import com.inwords.expenses.core.navigation.NavigationController
import com.inwords.expenses.feature.events.domain.EventCreationStateHolder
import com.inwords.expenses.feature.events.domain.GetCurrenciesUseCase
import com.inwords.expenses.feature.events.domain.model.Currency
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
internal class CreateEventViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val currencyRub = Currency(id = 1L, serverId = null, code = "RUB", name = "Russian Ruble")
    private val currencyEur = Currency(id = 2L, serverId = null, code = "EUR", name = "Euro")

    private val navigationController = mockk<NavigationController>(relaxed = true) {
        justRun { popBackStack() }
    }
    private val getCurrenciesUseCase = mockk<GetCurrenciesUseCase> {
        every { getCurrencies() } returns flowOf(listOf(currencyRub, currencyEur))
    }

    @Test
    fun initialState_hasEmptyEventNameAndCurrenciesFromUseCase() = testScope.runTest {
        val stateHolder = EventCreationStateHolder()
        val viewModel = CreateEventViewModel(
            navigationController = navigationController,
            eventCreationStateHolder = stateHolder,
            getCurrenciesUseCase = getCurrenciesUseCase,
            viewModelScope = backgroundScope,
        )
        runCurrent()
        advanceUntilIdle()

        viewModel.state.test {
            skipItems(1)
            val initial = awaitItem()
            assertEquals("", initial.eventName)
            assertEquals(2, initial.currencies.size)
            assertTrue(initial.currencies.any { it.currencyCode == "RUB" && it.selected })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onEventNameChanged_updatesState() = testScope.runTest {
        val viewModel = CreateEventViewModel(
            navigationController = navigationController,
            eventCreationStateHolder = EventCreationStateHolder(),
            getCurrenciesUseCase = getCurrenciesUseCase,
            viewModelScope = backgroundScope,
        )
        runCurrent()
        advanceUntilIdle()

        viewModel.state.test {
            skipItems(1)
            awaitItem()
            viewModel.onEventNameChanged("  My Event  ")
            advanceUntilIdle()
            val updated = awaitItem()
            assertEquals("  My Event  ", updated.eventName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onCurrencyClicked_updatesSelectedCurrency() = testScope.runTest {
        val viewModel = CreateEventViewModel(
            navigationController = navigationController,
            eventCreationStateHolder = EventCreationStateHolder(),
            getCurrenciesUseCase = getCurrenciesUseCase,
            viewModelScope = backgroundScope,
        )
        runCurrent()
        advanceUntilIdle()

        viewModel.state.test {
            skipItems(1)
            awaitItem()
            val eurModel = CreateEventPaneUiModel.CurrencyInfoUiModel(
                currencyName = "Euro",
                currencyCode = "EUR",
                selected = false
            )
            viewModel.onCurrencyClicked(eurModel)
            advanceUntilIdle()
            val updated = awaitItem()
            assertTrue(updated.currencies.any { it.currencyCode == "EUR" && it.selected })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onConfirmClicked_withBlankEventName_doesNotNavigate() = testScope.runTest {
        val viewModel = CreateEventViewModel(
            navigationController = navigationController,
            eventCreationStateHolder = EventCreationStateHolder(),
            getCurrenciesUseCase = getCurrenciesUseCase,
            viewModelScope = backgroundScope,
        )
        runCurrent()
        advanceUntilIdle()

        viewModel.state.test {
            skipItems(1)
            awaitItem()
            viewModel.onEventNameChanged("")
            advanceUntilIdle()
            viewModel.onConfirmClicked()
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }
        verify(exactly = 0) { navigationController.navigateTo(any()) }
    }

    @Test
    fun onConfirmClicked_withNonBlankEventName_navigatesToAddPersons() = testScope.runTest {
        val stateHolder = EventCreationStateHolder()
        val viewModel = CreateEventViewModel(
            navigationController = navigationController,
            eventCreationStateHolder = stateHolder,
            getCurrenciesUseCase = getCurrenciesUseCase,
            viewModelScope = backgroundScope,
        )
        runCurrent()
        advanceUntilIdle()

        viewModel.state.test {
            skipItems(1)
            var state = awaitItem()
            viewModel.onEventNameChanged("Trip")
            advanceUntilIdle()
            state = awaitItem()
            assertEquals("Trip", state.eventName)
            viewModel.onConfirmClicked()
            runCurrent()
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }
        verify(exactly = 1) { navigationController.navigateTo(any()) }
        assertEquals("Trip", stateHolder.getDraftEventName())
        assertEquals(1L, stateHolder.getDraftPrimaryCurrencyId())
    }

    @Test
    fun onNavIconClicked_popsBackStack() = testScope.runTest {
        val viewModel = CreateEventViewModel(
            navigationController = navigationController,
            eventCreationStateHolder = EventCreationStateHolder(),
            getCurrenciesUseCase = getCurrenciesUseCase,
            viewModelScope = backgroundScope,
        )
        viewModel.onNavIconClicked()
        verify(exactly = 1) { navigationController.popBackStack() }
    }
}
