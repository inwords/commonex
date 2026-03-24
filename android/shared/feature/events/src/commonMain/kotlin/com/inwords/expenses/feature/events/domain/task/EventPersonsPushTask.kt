package com.inwords.expenses.feature.events.domain.task

import com.inwords.expenses.core.observability.captureMessageIfNull
import com.inwords.expenses.core.utils.IO
import com.inwords.expenses.core.utils.IoResult
import com.inwords.expenses.feature.events.domain.store.local.EventsLocalStore
import com.inwords.expenses.feature.events.domain.store.remote.EventsRemoteStore
import kotlinx.coroutines.withContext


class EventPersonsPushTask internal constructor(
    eventsLocalStoreLazy: Lazy<EventsLocalStore>,
    eventsRemoteStoreLazy: Lazy<EventsRemoteStore>,
) {

    private val eventsLocalStore by eventsLocalStoreLazy
    private val eventsRemoteStore by eventsRemoteStoreLazy

    /**
     * Prerequisites:
     * 1. Event is synced
     */
    suspend fun pushEventPersons(eventId: Long): IoResult<*> = withContext(IO) {
        val localEvent = eventsLocalStore.getEventWithDetails(eventId) ?: return@withContext IoResult.Error.Failure
        val eventServerId = localEvent.event.serverId
            .captureMessageIfNull("EventPersonsPushTask cannot push persons for an unsynced event")
            ?: return@withContext IoResult.Error.Failure

        val personsToAdd = localEvent.persons.filter { it.serverId == null }

        if (personsToAdd.isEmpty()) return@withContext IoResult.Success(Unit)

        val networkResult = eventsRemoteStore.addPersonsToEvent(
            eventServerId = eventServerId,
            pinCode = localEvent.event.pinCode,
            localPersons = personsToAdd
        )

        val networkPersons = when (networkResult) {
            is IoResult.Success -> networkResult.data
            is IoResult.Error -> return@withContext networkResult
        }

        eventsLocalStore.insertPersonsWithCrossRefs(eventId, networkPersons, inTransaction = true)

        IoResult.Success(Unit)
    }
}
