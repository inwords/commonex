package com.inwords.expenses.feature.events.domain.task

import com.inwords.expenses.core.observability.captureMessageIfNull
import com.inwords.expenses.core.storage.utils.TransactionHelper
import com.inwords.expenses.core.utils.IO
import com.inwords.expenses.core.utils.IoResult
import com.inwords.expenses.feature.events.domain.store.local.EventsLocalStore
import com.inwords.expenses.feature.events.domain.store.local.PersonsLocalStore
import com.inwords.expenses.feature.events.domain.store.remote.EventsRemoteStore
import kotlinx.coroutines.withContext

class EventPushTask internal constructor(
    transactionHelperLazy: Lazy<TransactionHelper>,
    eventsLocalStoreLazy: Lazy<EventsLocalStore>,
    eventsRemoteStoreLazy: Lazy<EventsRemoteStore>,
    personsLocalStoreLazy: Lazy<PersonsLocalStore>,
) {

    private val transactionHelper by transactionHelperLazy
    private val eventsLocalStore by eventsLocalStoreLazy
    private val eventsRemoteStore by eventsRemoteStoreLazy
    private val personsLocalStore by personsLocalStoreLazy

    /**
     * Prerequisites:
     * 1. Currencies are synced
     */
    suspend fun pushEvent(eventId: Long): IoResult<*> = withContext(IO) {
        val localEventDetails = eventsLocalStore.getEventWithDetails(eventId) ?: return@withContext IoResult.Error.Failure

        if (localEventDetails.event.serverId != null) return@withContext IoResult.Success(Unit)
        val primaryCurrencyServerId = localEventDetails.primaryCurrency.serverId
            .captureMessageIfNull("EventPushTask cannot push an event with an unsynced primary currency") {
                setContext("primary_currency_code", localEventDetails.primaryCurrency.code)
            }
            ?: return@withContext IoResult.Error.Failure

        val remoteEventDetailsResult = eventsRemoteStore.createEvent(
            event = localEventDetails.event,
            currencies = localEventDetails.currencies,
            primaryCurrencyServerId = primaryCurrencyServerId,
            localPersons = localEventDetails.persons
        )
        val networkEventDetails = when (remoteEventDetailsResult) {
            is IoResult.Success -> remoteEventDetailsResult.data
            is IoResult.Error -> return@withContext remoteEventDetailsResult
        }
        val networkServerId = networkEventDetails.event.serverId
            .captureMessageIfNull("EventPushTask received a created event without a server id")
            ?: return@withContext IoResult.Error.Failure

        val localPersons = localEventDetails.persons
        val updatedPersons = networkEventDetails.persons.mapIndexed { i, networkPerson ->
            localPersons[i].copy(serverId = networkPerson.serverId)
        }

        transactionHelper.immediateWriteTransaction {
            personsLocalStore.insertWithoutCrossRefs(updatedPersons)

            eventsLocalStore.update(eventId, networkServerId)
        }

        IoResult.Success(Unit)
    }
}
