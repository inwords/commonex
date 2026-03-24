package com.inwords.expenses.feature.events.domain

import com.inwords.expenses.core.observability.Observability
import com.inwords.expenses.feature.events.api.EventHooks
import com.inwords.expenses.feature.events.domain.store.local.EventsLocalStore
import com.inwords.expenses.feature.events.domain.store.remote.EventsRemoteStore
import com.inwords.expenses.feature.events.domain.store.remote.EventsRemoteStore.EventNetworkError
import com.inwords.expenses.feature.settings.api.SettingsRepository
import kotlinx.coroutines.flow.first

class DeleteEventUseCase internal constructor(
    eventsLocalStoreLazy: Lazy<EventsLocalStore>,
    eventsRemoteStoreLazy: Lazy<EventsRemoteStore>,
    settingsRepositoryLazy: Lazy<SettingsRepository>,
    hooksLazy: Lazy<EventHooks>,
) {

    sealed interface DeleteEventResult {
        data object Deleted : DeleteEventResult
        data object RemoteFailed : DeleteEventResult
    }

    private val eventsLocalStore by eventsLocalStoreLazy
    private val eventsRemoteStore by eventsRemoteStoreLazy
    private val settingsRepository by settingsRepositoryLazy
    private val hooks by hooksLazy

    internal suspend fun deleteLocalEventByServerId(serverId: String): DeleteEventResult {
        val event = eventsLocalStore.getEventByServerId(serverId) ?: return DeleteEventResult.Deleted
        return deleteLocalEvent(event.id)
    }

    suspend fun deleteRemoteAndLocalEvent(eventId: Long): DeleteEventResult {
        val event = eventsLocalStore.getEvent(eventId) ?: return DeleteEventResult.Deleted

        hooks.onBeforeEventDeletion(eventId)

        return if (event.serverId == null) {
            deleteLocalEvent(event.id)
        } else {
            when (val result = eventsRemoteStore.deleteEvent(event.serverId, event.pinCode)) {
                is EventsRemoteStore.DeleteEventResult.Deleted -> deleteLocalEvent(event.id)

                is EventsRemoteStore.DeleteEventResult.Error -> when (result.error) {
                    EventNetworkError.NotFound,
                    EventNetworkError.Gone -> deleteLocalEvent(event.id)

                    EventNetworkError.InvalidAccessCode -> {
                        Observability.captureMessage("DeleteEventUseCase received InvalidAccessCode while deleting a synced local event") {
                            setContext("event_server_id", event.serverId)
                        }
                        DeleteEventResult.RemoteFailed
                    }

                    EventNetworkError.OtherError -> DeleteEventResult.RemoteFailed
                }
            }
        }
    }

    suspend fun deleteLocalEvent(eventId: Long): DeleteEventResult.Deleted {
        val currentEventId = settingsRepository.getCurrentEventId().first()
        if (currentEventId == eventId) {
            settingsRepository.clearCurrentEventAndPerson()
        }
        eventsLocalStore.delete(eventId)
        return DeleteEventResult.Deleted
    }
}
