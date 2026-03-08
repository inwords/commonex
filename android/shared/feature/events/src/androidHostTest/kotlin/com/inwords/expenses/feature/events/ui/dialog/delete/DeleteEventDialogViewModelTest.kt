package com.inwords.expenses.feature.events.ui.dialog.delete

import com.inwords.expenses.core.navigation.NavigationController
import com.inwords.expenses.feature.events.api.EventDeletionStateManager
import com.inwords.expenses.feature.events.api.EventDeletionStateManager.EventDeletionState
import com.inwords.expenses.feature.events.domain.DeleteEventUseCase
import com.inwords.expenses.feature.events.domain.DeleteEventUseCase.DeleteEventResult
import io.mockk.coEvery
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

@OptIn(ExperimentalCoroutinesApi::class)
internal class DeleteEventDialogViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val navigationController = mockk<NavigationController>(relaxed = true)
    private val eventDeletionStateManager = mockk<EventDeletionStateManager>(relaxed = true)
    private val deleteEventUseCase = mockk<DeleteEventUseCase>(relaxed = true)

    private val eventId = 1L

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
        val viewModel = DeleteEventDialogViewModel(
            navigationController = navigationController,
            eventDeletionStateManager = eventDeletionStateManager,
            deleteEventUseCase = deleteEventUseCase,
            eventId = eventId,
            scope = backgroundScope,
        )

        viewModel.onDismiss()

        verify(exactly = 1) { navigationController.popBackStack() }
    }

    @Test
    fun onConfirmDelete_whenDeleted_setsLoadingPopsClearsState() = runTest {
        coEvery { deleteEventUseCase.deleteRemoteAndLocalEvent(eventId) } returns DeleteEventResult.Deleted

        val viewModel = DeleteEventDialogViewModel(
            navigationController = navigationController,
            eventDeletionStateManager = eventDeletionStateManager,
            deleteEventUseCase = deleteEventUseCase,
            eventId = eventId,
            scope = backgroundScope,
            workDispatcher = testDispatcher,
        )

        viewModel.onConfirmDelete()
        runCurrent()
        advanceUntilIdle()

        verify(exactly = 1) { eventDeletionStateManager.setEventDeletionState(eventId, EventDeletionState.Loading) }
        verify(exactly = 1) { navigationController.popBackStack() }
        verify(exactly = 1) { eventDeletionStateManager.clearEventDeletionState(eventId) }
    }

    @Test
    fun onConfirmDelete_whenRemoteFailed_setsRemoteDeletionFailed() = runTest {
        coEvery { deleteEventUseCase.deleteRemoteAndLocalEvent(eventId) } returns DeleteEventResult.RemoteFailed

        val viewModel = DeleteEventDialogViewModel(
            navigationController = navigationController,
            eventDeletionStateManager = eventDeletionStateManager,
            deleteEventUseCase = deleteEventUseCase,
            eventId = eventId,
            scope = backgroundScope,
            workDispatcher = testDispatcher,
        )

        viewModel.onConfirmDelete()
        runCurrent()
        advanceUntilIdle()

        verify(exactly = 1) { eventDeletionStateManager.setEventDeletionState(eventId, EventDeletionState.Loading) }
        verify(exactly = 1) { navigationController.popBackStack() }
        verify(exactly = 1) { eventDeletionStateManager.setEventDeletionState(eventId, EventDeletionState.RemoteDeletionFailed) }
    }
}
