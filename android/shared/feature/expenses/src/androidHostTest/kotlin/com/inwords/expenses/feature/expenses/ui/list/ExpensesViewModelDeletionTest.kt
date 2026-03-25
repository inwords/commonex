package com.inwords.expenses.feature.expenses.ui.list

import app.cash.turbine.test
import com.inwords.expenses.core.navigation.NavigationController
import com.inwords.expenses.core.ui.utils.SimpleScreenState
import com.inwords.expenses.feature.events.api.EventDeletionStateManager
import com.inwords.expenses.feature.events.api.EventDeletionStateManager.EventDeletionState
import com.inwords.expenses.feature.events.domain.DeleteEventUseCase
import com.inwords.expenses.feature.events.domain.EventsSyncStateHolder
import com.inwords.expenses.feature.events.domain.GetCurrentEventStateUseCase
import com.inwords.expenses.feature.events.domain.GetEventsUseCase
import com.inwords.expenses.feature.events.domain.JoinEventUseCase
import com.inwords.expenses.feature.events.domain.model.Event
import com.inwords.expenses.feature.events.ui.dialog.delete.DeleteEventDialogDestination
import com.inwords.expenses.feature.events.ui.local.LocalEventsUiModel.LocalEventUiModel
import com.inwords.expenses.feature.expenses.domain.GetExpensesDetailsUseCase
import com.inwords.expenses.feature.expenses.domain.RequestExpensesRefreshUseCase
import com.inwords.expenses.feature.expenses.ui.list.ExpensesPaneUiModel.LocalEvents
import com.inwords.expenses.feature.settings.api.SettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
internal class ExpensesViewModelDeletionTest {

    // region Test Fixtures
    private object TestFixtures {
        val syncedEvent = Event(
            id = 1L,
            serverId = "server-1",
            name = "Synced Event",
            pinCode = "1234",
            primaryCurrencyId = 100L
        )

        val localOnlyEvent = Event(
            id = 2L,
            serverId = null,
            name = "Local Event",
            pinCode = "5678",
            primaryCurrencyId = 100L
        )

        val syncedEventUiModel = LocalEventUiModel(
            eventId = syncedEvent.id,
            eventName = syncedEvent.name,
            isSynced = true,
            deletionState = EventDeletionState.None
        )

        val localEventUiModel = LocalEventUiModel(
            eventId = localOnlyEvent.id,
            eventName = localOnlyEvent.name,
            isSynced = false,
            deletionState = EventDeletionState.None
        )

        val remoteDeletionFailedEventUiModel = LocalEventUiModel(
            eventId = syncedEvent.id,
            eventName = syncedEvent.name,
            isSynced = true,
            deletionState = EventDeletionState.RemoteDeletionFailed
        )
    }
    // endregion

    // region Test Setup
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    // Controlled backing flows
    private val eventsFlow = MutableStateFlow(listOf(TestFixtures.syncedEvent, TestFixtures.localOnlyEvent))
    private val eventsDeletionStateFlow = MutableStateFlow<Map<Long, EventDeletionState>>(emptyMap())
    private val currentPersonIdFlow = MutableStateFlow<Long?>(null)

    // Mocks
    private val navigationController = mockk<NavigationController>(relaxed = true) {
        justRun { navigateTo(any()) }
    }
    private val getCurrentEventStateUseCase = mockk<GetCurrentEventStateUseCase>(relaxed = true) {
        every { currentEvent } returns MutableStateFlow(null)
    }
    private val eventDeletionStateManager = mockk<EventDeletionStateManager>(relaxed = true) {
        every { eventsDeletionState } returns eventsDeletionStateFlow
        justRun { clearEventDeletionState(any()) }
    }
    private val getEventsUseCase = mockk<GetEventsUseCase>(relaxed = true) {
        every { getEvents() } returns eventsFlow
    }
    private val joinEventUseCase = mockk<JoinEventUseCase>(relaxed = true)
    private val deleteEventUseCase = mockk<DeleteEventUseCase>(relaxed = true)
    private val getExpensesDetailsUseCase = mockk<GetExpensesDetailsUseCase>(relaxed = true)
    private val requestExpensesRefreshUseCase = mockk<RequestExpensesRefreshUseCase>(relaxed = true)
    private val eventsSyncStateHolder = mockk<EventsSyncStateHolder>(relaxed = true) {
        every { getStateFor(any()) } returns MutableStateFlow(false)
    }
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true) {
        coEvery { getCurrentPersonId() } returns currentPersonIdFlow
    }

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }
    // endregion

    // region Delete Event Tests

    @Test
    fun `onDeleteEventClick should navigate to DeleteEventDialog for synced event`() = testScope.runTest {
        // Given
        val viewModel = createViewModel()

        // When
        viewModel.onDeleteEventClick(TestFixtures.syncedEventUiModel)

        // Then
        verify(exactly = 1) {
            navigationController.navigateTo(
                DeleteEventDialogDestination(
                    eventId = TestFixtures.syncedEvent.id,
                    eventName = TestFixtures.syncedEvent.name
                )
            )
        }
    }

    @Test
    fun `onDeleteEventClick should navigate to DeleteEventDialog for local-only event`() = testScope.runTest {
        // Given
        val viewModel = createViewModel()

        // When
        viewModel.onDeleteEventClick(TestFixtures.localEventUiModel)

        // Then
        verify(exactly = 1) {
            navigationController.navigateTo(
                DeleteEventDialogDestination(
                    eventId = TestFixtures.localOnlyEvent.id,
                    eventName = TestFixtures.localOnlyEvent.name
                )
            )
        }
    }
    // endregion

    // region Delete Only Local Event Tests

    @Test
    fun `onDeleteOnlyLocalEventClick should call deleteLocalEvent use case`() = testScope.runTest {
        // Given
        val viewModel = createViewModel()

        // When
        viewModel.onDeleteOnlyLocalEventClick(TestFixtures.localEventUiModel)
        runCurrent()

        // Then
        coVerify(exactly = 1) {
            deleteEventUseCase.deleteLocalEvent(TestFixtures.localOnlyEvent.id)
        }
    }

    @Test
    fun `onDeleteOnlyLocalEventClick should work for synced event with RemoteDeletionFailed`() = testScope.runTest {
        // Given
        val viewModel = createViewModel()

        // When
        viewModel.onDeleteOnlyLocalEventClick(TestFixtures.remoteDeletionFailedEventUiModel)
        runCurrent()

        // Then
        coVerify(exactly = 1) {
            deleteEventUseCase.deleteLocalEvent(TestFixtures.syncedEvent.id)
        }
    }
    // endregion

    // region Keep Local Event Tests

    @Test
    fun `onKeepLocalEventClick should clear event deletion state`() = testScope.runTest {
        // Given
        val viewModel = createViewModel()

        // When
        viewModel.onKeepLocalEventClick(TestFixtures.remoteDeletionFailedEventUiModel)

        // Then
        verify(exactly = 1) {
            eventDeletionStateManager.clearEventDeletionState(TestFixtures.syncedEvent.id)
        }
    }

    @Test
    fun `onKeepLocalEventClick should clear event deletion state for local-only event`() = testScope.runTest {
        // Given
        val viewModel = createViewModel()

        // When
        viewModel.onKeepLocalEventClick(TestFixtures.localEventUiModel)

        // Then
        verify(exactly = 1) {
            eventDeletionStateManager.clearEventDeletionState(TestFixtures.localOnlyEvent.id)
        }
    }
    // endregion

    // region State Tests

    @Test
    fun `state should reflect local events with correct deletion states`() = testScope.runTest {
        // Given
        eventsDeletionStateFlow.value = mapOf(
            TestFixtures.syncedEvent.id to EventDeletionState.RemoteDeletionFailed
        )
        val viewModel = createViewModel()
        runCurrent()
        advanceUntilIdle()

        // When & Then
        viewModel.state.test {
            // Skip loading state
            skipItems(1)

            val state = awaitItem()
            val _ = assertIs<SimpleScreenState.Success<ExpensesPaneUiModel>>(state)
            val localEvents = state.data as LocalEvents

            val events = localEvents.localEvents.events
            assertEquals(2, events.size)

            // First event should have RemoteDeletionFailed state
            val syncedEventUi = events.first { it.eventId == TestFixtures.syncedEvent.id }
            assertEquals(EventDeletionState.RemoteDeletionFailed, syncedEventUi.deletionState)
            assertTrue(syncedEventUi.isSynced)

            // Second event should have None state
            val localEventUi = events.first { it.eventId == TestFixtures.localOnlyEvent.id }
            assertEquals(EventDeletionState.None, localEventUi.deletionState)
            assertTrue(!localEventUi.isSynced)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `state should update when deletion state changes`() = testScope.runTest {
        // Given
        val viewModel = createViewModel()
        runCurrent()
        advanceUntilIdle()

        // When & Then
        viewModel.state.test {
            skipItems(1) // Skip loading

            // Initial state - no deletion in progress
            val initialState = assertIs<SimpleScreenState.Success<ExpensesPaneUiModel>>(awaitItem())
            val initialEvents = (initialState.data as LocalEvents).localEvents.events
            val initialSyncedEvent = initialEvents.first { it.eventId == TestFixtures.syncedEvent.id }
            assertEquals(EventDeletionState.None, initialSyncedEvent.deletionState)

            // Simulate deletion state change to Loading
            eventsDeletionStateFlow.value = mapOf(
                TestFixtures.syncedEvent.id to EventDeletionState.Loading
            )
            advanceUntilIdle()

            val loadingState = assertIs<SimpleScreenState.Success<ExpensesPaneUiModel>>(awaitItem())
            val loadingEvents = (loadingState.data as LocalEvents).localEvents.events
            val loadingSyncedEvent = loadingEvents.first { it.eventId == TestFixtures.syncedEvent.id }
            assertEquals(EventDeletionState.Loading, loadingSyncedEvent.deletionState)

            // Simulate deletion state change to RemoteDeletionFailed
            eventsDeletionStateFlow.value = mapOf(
                TestFixtures.syncedEvent.id to EventDeletionState.RemoteDeletionFailed
            )
            advanceUntilIdle()

            val failedState = assertIs<SimpleScreenState.Success<ExpensesPaneUiModel>>(awaitItem())
            val failedEvents = (failedState.data as LocalEvents).localEvents.events
            val failedSyncedEvent = failedEvents.first { it.eventId == TestFixtures.syncedEvent.id }
            assertEquals(EventDeletionState.RemoteDeletionFailed, failedSyncedEvent.deletionState)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `state should show empty when all events are deleted`() = testScope.runTest {
        // Given
        val viewModel = createViewModel()
        runCurrent()
        advanceUntilIdle()

        // When & Then
        viewModel.state.test {
            skipItems(1) // Skip loading

            // Initial state with events
            val _ = assertIs<SimpleScreenState.Success<ExpensesPaneUiModel>>(awaitItem())

            // Remove all events
            eventsFlow.value = emptyList()
            advanceUntilIdle()

            val _ = assertIs<SimpleScreenState.Empty>(awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }
    // endregion

    // region Helper Methods
    private fun createViewModel(): ExpensesViewModel {
        return ExpensesViewModel(
            navigationController = navigationController,
            getCurrentEventStateUseCase = getCurrentEventStateUseCase,
            eventDeletionStateManager = eventDeletionStateManager,
            getEventsUseCase = getEventsUseCase,
            joinEventUseCase = joinEventUseCase,
            deleteEventUseCase = deleteEventUseCase,
            getExpensesDetailsUseCase = getExpensesDetailsUseCase,
            requestExpensesRefreshUseCase = requestExpensesRefreshUseCase,
            eventsSyncStateHolder = eventsSyncStateHolder,
            settingsRepository = settingsRepository,
            unconfinedDispatcher = testDispatcher,
            viewModelScope = this.testScope.backgroundScope,
        )
    }
    // endregion
}
