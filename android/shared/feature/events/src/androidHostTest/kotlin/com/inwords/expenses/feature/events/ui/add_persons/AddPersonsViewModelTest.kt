package com.inwords.expenses.feature.events.ui.add_persons

import app.cash.turbine.test
import com.inwords.expenses.core.navigation.Destination
import com.inwords.expenses.core.navigation.NavigationController
import com.inwords.expenses.feature.events.domain.CreateEventUseCase
import com.inwords.expenses.feature.events.domain.EventCreationStateHolder
import com.inwords.expenses.feature.events.domain.model.Currency
import com.inwords.expenses.feature.events.domain.model.Event
import com.inwords.expenses.feature.events.domain.model.EventDetails
import com.inwords.expenses.feature.events.domain.model.Person
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import io.mockk.coEvery
import io.mockk.coVerify
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
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
internal class AddPersonsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val navigationController = mockk<NavigationController>(relaxed = true)
    private val eventCreationStateHolder = EventCreationStateHolder()
    private val createEventUseCase = mockk<CreateEventUseCase>(relaxed = true)
    private val expensesScreenDestination = mockk<Destination>(relaxed = true)

    private val eventDetails = EventDetails(
        event = Event(1L, null, "Trip", "1234", 1L),
        currencies = emptyList(),
        persons = listOf(Person(1L, null, "Alice"), Person(2L, null, "Bob")),
        primaryCurrency = Currency(1L, null, "EUR", "Euro", BigDecimal.ONE),
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
    fun initialState_hasEmptyOwnerNameAndEmptyPersons() = runTest {
        coEvery { createEventUseCase.createEvent() } returns eventDetails

        val viewModel = AddPersonsViewModel(
            navigationController = navigationController,
            eventCreationStateHolder = eventCreationStateHolder,
            createEventUseCase = createEventUseCase,
            expensesScreenDestination = expensesScreenDestination,
            viewModelScope = backgroundScope,
        )
        runCurrent()
        advanceUntilIdle()

        viewModel.state.test {
            val initial = awaitItem()
            assertEquals("", initial.ownerName)
            assertTrue(initial.persons.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onOwnerNameChanged_updatesState() = runTest {
        val viewModel = AddPersonsViewModel(
            navigationController = navigationController,
            eventCreationStateHolder = eventCreationStateHolder,
            createEventUseCase = createEventUseCase,
            expensesScreenDestination = expensesScreenDestination,
            viewModelScope = backgroundScope,
        )
        runCurrent()
        advanceUntilIdle()

        viewModel.state.test {
            assertEquals("", awaitItem().ownerName)
            viewModel.onOwnerNameChanged("Alice")
            assertEquals("Alice", awaitItem().ownerName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onAddParticipantClicked_addsEmptyParticipantRow() = runTest {
        val viewModel = AddPersonsViewModel(
            navigationController = navigationController,
            eventCreationStateHolder = eventCreationStateHolder,
            createEventUseCase = createEventUseCase,
            expensesScreenDestination = expensesScreenDestination,
            viewModelScope = backgroundScope,
        )
        runCurrent()
        advanceUntilIdle()

        viewModel.state.test {
            assertTrue(awaitItem().persons.isEmpty())
            viewModel.onAddParticipantClicked()
            val item = awaitItem()
            assertEquals(1, item.persons.size)
            assertEquals("", item.persons[0])
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onConfirmClicked_updatesStateHolderCallsCreateEventAndPops() = runTest {
        coEvery { createEventUseCase.createEvent() } returns eventDetails
        val viewModel = AddPersonsViewModel(
            navigationController = navigationController,
            eventCreationStateHolder = eventCreationStateHolder,
            createEventUseCase = createEventUseCase,
            expensesScreenDestination = expensesScreenDestination,
            viewModelScope = backgroundScope,
        )

        viewModel.onOwnerNameChanged("Alice")
        viewModel.onAddParticipantClicked()
        viewModel.onParticipantNameChanged(0, "Bob")
        viewModel.onConfirmClicked()
        runCurrent()
        advanceUntilIdle()

        assertEquals("Alice", eventCreationStateHolder.getDraftOwner())
        assertEquals(listOf("Bob"), eventCreationStateHolder.getDraftOtherPersons())
        coVerify(exactly = 1) { createEventUseCase.createEvent() }
        verify(exactly = 1) {
            navigationController.popBackStack(
                toDestination = expensesScreenDestination,
                inclusive = false,
            )
        }
    }

    @Test
    fun onNavIconClicked_popsBackStack() = runTest {
        val viewModel = AddPersonsViewModel(
            navigationController = navigationController,
            eventCreationStateHolder = eventCreationStateHolder,
            createEventUseCase = createEventUseCase,
            expensesScreenDestination = expensesScreenDestination,
            viewModelScope = backgroundScope,
        )

        viewModel.onNavIconClicked()

        verify(exactly = 1) { navigationController.popBackStack() }
    }
}
