package com.inwords.expenses.feature.events.ui.add_participants

import app.cash.turbine.test
import com.inwords.expenses.core.navigation.NavigationController
import com.inwords.expenses.feature.events.domain.AddParticipantsToCurrentEventUseCase
import io.mockk.coVerify
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
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
internal class AddParticipantsToEventViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val navigationController = mockk<NavigationController>(relaxed = true) {
        justRun { popBackStack() }
    }
    private val addParticipantsToCurrentEventUseCase = mockk<AddParticipantsToCurrentEventUseCase>(relaxed = true)

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_hasOneEmptyParticipant_confirmDisabled() = runTest {
        val viewModel = AddParticipantsToEventViewModel(
            navigationController = navigationController,
            addParticipantsToCurrentEventUseCase = addParticipantsToCurrentEventUseCase,
            viewModelScope = backgroundScope,
        )
        runCurrent()
        advanceUntilIdle()

        viewModel.state.test {
            val initial = awaitItem()
            assertEquals(listOf(""), initial.participants)
            assertFalse(initial.isConfirmEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onParticipantNameChanged_updatesState() = runTest {
        val viewModel = AddParticipantsToEventViewModel(
            navigationController = navigationController,
            addParticipantsToCurrentEventUseCase = addParticipantsToCurrentEventUseCase,
            viewModelScope = backgroundScope,
        )
        runCurrent()
        advanceUntilIdle()

        viewModel.state.test {
            awaitItem()
            viewModel.onParticipantNameChanged(0, "Alice")
            val state = awaitItem()
            assertEquals(listOf("Alice"), state.participants)
            assertTrue(state.isConfirmEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onAddParticipantClicked_addsEmptyParticipant() = runTest {
        val viewModel = AddParticipantsToEventViewModel(
            navigationController = navigationController,
            addParticipantsToCurrentEventUseCase = addParticipantsToCurrentEventUseCase,
            viewModelScope = backgroundScope,
        )
        runCurrent()
        advanceUntilIdle()

        viewModel.state.test {
            awaitItem()
            viewModel.onAddParticipantClicked()
            assertEquals(listOf("", ""), awaitItem().participants)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onConfirmClicked_whenConfirmDisabled_doesNotCallUseCase() = runTest {
        val viewModel = AddParticipantsToEventViewModel(
            navigationController = navigationController,
            addParticipantsToCurrentEventUseCase = addParticipantsToCurrentEventUseCase,
            viewModelScope = backgroundScope,
        )
        runCurrent()
        advanceUntilIdle()

        viewModel.state.test {
            awaitItem()
            viewModel.onConfirmClicked()
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 0) { addParticipantsToCurrentEventUseCase.addParticipants(any()) }
        verify(exactly = 0) { navigationController.popBackStack() }
    }

    @Test
    fun onConfirmClicked_whenConfirmEnabled_callsUseCaseAndPopsBackStack() = runTest {
        val viewModel = AddParticipantsToEventViewModel(
            navigationController = navigationController,
            addParticipantsToCurrentEventUseCase = addParticipantsToCurrentEventUseCase,
            viewModelScope = backgroundScope,
        )
        runCurrent()
        advanceUntilIdle()

        viewModel.state.test {
            awaitItem()
            viewModel.onParticipantNameChanged(0, "Bob")
            awaitItem()
            viewModel.onConfirmClicked()
            cancelAndIgnoreRemainingEvents()
        }
        runCurrent()
        advanceUntilIdle()
        coVerify(exactly = 1) { addParticipantsToCurrentEventUseCase.addParticipants(listOf("Bob")) }
        verify(exactly = 1) { navigationController.popBackStack() }
    }

    @Test
    fun onConfirmClicked_withAllEmptyNames_confirmDisabled() = runTest {
        val viewModel = AddParticipantsToEventViewModel(
            navigationController = navigationController,
            addParticipantsToCurrentEventUseCase = addParticipantsToCurrentEventUseCase,
            viewModelScope = backgroundScope,
        )
        runCurrent()
        advanceUntilIdle()

        viewModel.state.test {
            awaitItem()
            viewModel.onAddParticipantClicked()
            val state = awaitItem()
            assertEquals(listOf("", ""), state.participants)
            assertFalse(state.isConfirmEnabled)
            viewModel.onConfirmClicked()
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 0) { addParticipantsToCurrentEventUseCase.addParticipants(any()) }
    }

    @Test
    fun onNavIconClicked_popsBackStack() = runTest {
        val viewModel = AddParticipantsToEventViewModel(
            navigationController = navigationController,
            addParticipantsToCurrentEventUseCase = addParticipantsToCurrentEventUseCase,
            viewModelScope = backgroundScope,
        )
        viewModel.onNavIconClicked()
        verify(exactly = 1) { navigationController.popBackStack() }
    }
}
