package com.inwords.expenses.feature.events.domain

import com.inwords.expenses.feature.events.api.EventHooks
import com.inwords.expenses.feature.events.domain.model.Event
import com.inwords.expenses.feature.events.domain.store.local.EventsLocalStore
import com.inwords.expenses.feature.events.domain.store.remote.EventsRemoteStore
import com.inwords.expenses.feature.settings.api.SettingsRepository
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

internal class DeleteEventUseCaseTest {

    private val eventsLocalStore = mockk<EventsLocalStore>(relaxed = true)
    private val eventsRemoteStore = mockk<EventsRemoteStore>(relaxed = true)
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val hooks = mockk<EventHooks>(relaxed = true)

    private val useCase = DeleteEventUseCase(
        eventsLocalStoreLazy = lazy { eventsLocalStore },
        eventsRemoteStoreLazy = lazy { eventsRemoteStore },
        settingsRepositoryLazy = lazy { settingsRepository },
        hooksLazy = lazy { hooks },
    )

    private val localOnlyEvent = Event(
        id = 10L,
        serverId = null,
        name = "Local trip",
        pinCode = "1234",
        primaryCurrencyId = 1L,
    )

    private val syncedEvent = Event(
        id = 11L,
        serverId = "server-11",
        name = "Synced trip",
        pinCode = "5678",
        primaryCurrencyId = 1L,
    )

    @Test
    fun deleteRemoteAndLocalEvent_whenEventIsLocalOnly_clearsSelectionAndDeletesLocally() = runTest {
        coEvery { eventsLocalStore.getEvent(localOnlyEvent.id) } returns localOnlyEvent
        every { settingsRepository.getCurrentEventId() } returns flowOf(localOnlyEvent.id)
        coJustRun { hooks.onBeforeEventDeletion(localOnlyEvent.id) }

        val result = useCase.deleteRemoteAndLocalEvent(localOnlyEvent.id)

        val _ = assertIs<DeleteEventUseCase.DeleteEventResult.Deleted>(result)
        coVerify(exactly = 1) { hooks.onBeforeEventDeletion(localOnlyEvent.id) }
        coVerify(exactly = 0) { eventsRemoteStore.deleteEvent(any(), any()) }
        coVerify(exactly = 1) { settingsRepository.clearCurrentEventAndPerson() }
        coVerify(exactly = 1) { eventsLocalStore.delete(localOnlyEvent.id) }
    }

    @Test
    fun deleteRemoteAndLocalEvent_whenRemoteReportsGone_deletesLocalCopy() = runTest {
        coEvery { eventsLocalStore.getEvent(syncedEvent.id) } returns syncedEvent
        coEvery {
            eventsRemoteStore.deleteEvent(syncedEvent.serverId!!, syncedEvent.pinCode)
        } returns EventsRemoteStore.DeleteEventResult.Error(EventsRemoteStore.EventNetworkError.Gone)
        every { settingsRepository.getCurrentEventId() } returns flowOf(null)
        coJustRun { hooks.onBeforeEventDeletion(syncedEvent.id) }

        val result = useCase.deleteRemoteAndLocalEvent(syncedEvent.id)

        val _ = assertIs<DeleteEventUseCase.DeleteEventResult.Deleted>(result)
        coVerify(exactly = 1) { hooks.onBeforeEventDeletion(syncedEvent.id) }
        coVerify(exactly = 1) { eventsRemoteStore.deleteEvent("server-11", "5678") }
        coVerify(exactly = 1) { eventsLocalStore.delete(syncedEvent.id) }
        coVerify(exactly = 0) { settingsRepository.clearCurrentEventAndPerson() }
    }

    @Test
    fun deleteRemoteAndLocalEvent_whenRemoteRejectsAccessCode_keepsLocalCopy() = runTest {
        coEvery { eventsLocalStore.getEvent(syncedEvent.id) } returns syncedEvent
        coEvery {
            eventsRemoteStore.deleteEvent(syncedEvent.serverId!!, syncedEvent.pinCode)
        } returns EventsRemoteStore.DeleteEventResult.Error(EventsRemoteStore.EventNetworkError.InvalidAccessCode)
        coJustRun { hooks.onBeforeEventDeletion(syncedEvent.id) }

        val result = useCase.deleteRemoteAndLocalEvent(syncedEvent.id)

        val _ = assertIs<DeleteEventUseCase.DeleteEventResult.RemoteFailed>(result)
        coVerify(exactly = 1) { hooks.onBeforeEventDeletion(syncedEvent.id) }
        coVerify(exactly = 1) { eventsRemoteStore.deleteEvent("server-11", "5678") }
        coVerify(exactly = 0) { eventsLocalStore.delete(any()) }
        coVerify(exactly = 0) { settingsRepository.clearCurrentEventAndPerson() }
    }
}
