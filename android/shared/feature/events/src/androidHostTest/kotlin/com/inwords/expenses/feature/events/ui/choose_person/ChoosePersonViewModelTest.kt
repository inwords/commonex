package com.inwords.expenses.feature.events.ui.choose_person

import app.cash.turbine.test
import com.inwords.expenses.core.navigation.Destination
import com.inwords.expenses.core.navigation.NavigationController
import com.inwords.expenses.core.ui.utils.SimpleScreenState
import com.inwords.expenses.feature.events.domain.GetCurrentEventStateUseCase
import com.inwords.expenses.feature.events.domain.model.Currency
import com.inwords.expenses.feature.events.domain.model.Event
import com.inwords.expenses.feature.events.domain.model.EventDetails
import com.inwords.expenses.feature.events.domain.model.Person
import com.inwords.expenses.feature.settings.api.SettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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
internal class ChoosePersonViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val navigationController = mockk<NavigationController>(relaxed = true)
    private val getCurrentEventStateUseCase = mockk<GetCurrentEventStateUseCase>(relaxed = true)
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val expensesScreenDestination = mockk<Destination>(relaxed = true)

    private val event = Event(1L, null, "Trip", "1234", 1L)
    private val person1 = Person(1L, null, "Alice")
    private val person2 = Person(2L, null, "Bob")
    private val eventDetails = EventDetails(
        event = event,
        currencies = emptyList(),
        persons = listOf(person1, person2),
        primaryCurrency = Currency(1L, null, "EUR", "Euro"),
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
    fun state_emitsSuccess_withPersonsListAndSelection() = runTest {
        val currentEventFlow = MutableStateFlow<EventDetails?>(eventDetails)
        every { getCurrentEventStateUseCase.currentEvent } returns currentEventFlow
        every { settingsRepository.getCurrentPersonId() } returns flowOf(null)

        val viewModel = ChoosePersonViewModel(
            navigationController = navigationController,
            getCurrentEventStateUseCase = getCurrentEventStateUseCase,
            settingsRepository = settingsRepository,
            expensesScreenDestination = expensesScreenDestination,
            viewModelScope = backgroundScope,
        )
        runCurrent()
        advanceUntilIdle()

        viewModel.state.test {
            skipItems(1)
            val item = awaitItem()
            assertTrue(item is SimpleScreenState.Success, "Expected Success, got $item")
            val data = item.data
            assertEquals(1L, data.eventId)
            assertEquals("Trip", data.eventName)
            assertEquals(2, data.persons.size)
            assertEquals("Alice", data.persons[0].name)
            assertEquals("Bob", data.persons[1].name)
            assertTrue(data.persons[0].selected)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onNavIconClicked_popsBackStack() = runTest {
        val currentEventFlow = MutableStateFlow<EventDetails?>(eventDetails)
        every { getCurrentEventStateUseCase.currentEvent } returns currentEventFlow
        every { settingsRepository.getCurrentPersonId() } returns flowOf(null)

        val viewModel = ChoosePersonViewModel(
            navigationController = navigationController,
            getCurrentEventStateUseCase = getCurrentEventStateUseCase,
            settingsRepository = settingsRepository,
            expensesScreenDestination = expensesScreenDestination,
            viewModelScope = backgroundScope,
        )
        runCurrent()
        advanceUntilIdle()

        viewModel.onNavIconClicked()

        verify(exactly = 1) { navigationController.popBackStack() }
    }

    @Test
    fun onPersonSelected_setsCurrentPersonAndPopsToExpensesScreen() = runTest {
        val currentEventFlow = MutableStateFlow<EventDetails?>(eventDetails)
        every { getCurrentEventStateUseCase.currentEvent } returns currentEventFlow
        every { settingsRepository.getCurrentPersonId() } returns flowOf(1L)
        coEvery { settingsRepository.setCurrentPersonId(any()) } returns Unit

        val viewModel = ChoosePersonViewModel(
            navigationController = navigationController,
            getCurrentEventStateUseCase = getCurrentEventStateUseCase,
            settingsRepository = settingsRepository,
            expensesScreenDestination = expensesScreenDestination,
            viewModelScope = backgroundScope,
        )
        runCurrent()
        advanceUntilIdle()

        viewModel.onPersonSelected(2L)
        runCurrent()
        advanceUntilIdle()

        coVerify(exactly = 1) { settingsRepository.setCurrentPersonId(2L) }
        verify(exactly = 1) {
            navigationController.popBackStack(
                toDestination = expensesScreenDestination,
                inclusive = false,
            )
        }
    }
}
