package com.inwords.expenses.feature.events.domain

import com.inwords.expenses.core.storage.utils.TransactionHelper
import com.inwords.expenses.core.utils.IoResult
import com.inwords.expenses.feature.events.domain.model.Currency
import com.inwords.expenses.feature.events.domain.model.EventDetails
import com.inwords.expenses.feature.events.domain.store.local.CurrenciesLocalStore
import com.inwords.expenses.feature.events.domain.store.local.EventsLocalStore
import com.inwords.expenses.feature.events.domain.store.remote.EventsRemoteStore
import com.inwords.expenses.feature.events.domain.store.remote.EventsRemoteStore.EventNetworkError
import com.inwords.expenses.feature.events.domain.store.remote.EventsRemoteStore.GetEventResult
import com.inwords.expenses.feature.events.domain.task.CurrenciesPullTask
import com.inwords.expenses.feature.settings.api.SettingsRepository
import kotlinx.coroutines.flow.first

class JoinEventUseCase internal constructor(
    transactionHelperLazy: Lazy<TransactionHelper>,
    eventsLocalStoreLazy: Lazy<EventsLocalStore>,
    eventsRemoteStoreLazy: Lazy<EventsRemoteStore>,
    deleteEventUseCase: Lazy<DeleteEventUseCase>,
    settingsRepositoryLazy: Lazy<SettingsRepository>,
    currenciesLocalStoreLazy: Lazy<CurrenciesLocalStore>,
    currenciesPullTaskLazy: Lazy<CurrenciesPullTask>,
) {

    internal sealed interface JoinEventResult {
        data class NewCurrentEvent(val event: EventDetails) : JoinEventResult

        sealed interface Error : JoinEventResult {
            data object InvalidAccessCode : Error
            data object InvalidToken : Error
            data object TokenExpired : Error
            data object UpdateRequired : Error
            data object EventNotFound : Error
            data object OtherError : Error
        }
    }

    private val transactionHelper by transactionHelperLazy
    private val eventsLocalStore by eventsLocalStoreLazy
    private val eventsRemoteStore by eventsRemoteStoreLazy
    private val deleteEventUseCase by deleteEventUseCase
    private val settingsRepository by settingsRepositoryLazy
    private val currenciesLocalStore by currenciesLocalStoreLazy
    private val currenciesPullTask by currenciesPullTaskLazy

    suspend fun joinLocalEvent(eventId: Long): Boolean {
        val localEvent = eventsLocalStore.getEvent(eventId)
        return if (localEvent == null) {
            false
        } else {
            settingsRepository.setCurrentEventId(localEvent.id)
            true
        }
    }

    internal suspend fun joinEvent(
        eventServerId: String,
        accessCode: String?,
        token: String?
    ): JoinEventResult {
        require((accessCode != null) xor (token != null)) {
            "Either pinCode or token must be provided"
        }

        val localEvent = eventsLocalStore.getEventWithDetailsByServerId(eventServerId)
        if (localEvent != null) {
            settingsRepository.setCurrentEventId(localEvent.event.id)
            return JoinEventResult.NewCurrentEvent(localEvent)
        }

        val joinEventResult = joinRemoteEvent(serverId = eventServerId, pinCode = accessCode, token = token)

        when (joinEventResult) {
            is JoinEventResult.NewCurrentEvent -> {
                settingsRepository.setCurrentEventId(joinEventResult.event.event.id)
            }

            JoinEventResult.Error.EventNotFound,
            JoinEventResult.Error.InvalidAccessCode,
            JoinEventResult.Error.InvalidToken,
            JoinEventResult.Error.TokenExpired,
            JoinEventResult.Error.UpdateRequired,
            JoinEventResult.Error.OtherError -> Unit
        }

        return joinEventResult
    }

    private suspend fun joinRemoteEvent(
        serverId: String,
        pinCode: String?,
        token: String?,
    ): JoinEventResult {
        val currenciesBeforeSync = currenciesLocalStore.getCurrencies().first()
        val currencies = if (currenciesBeforeSync.isEmpty() || currenciesBeforeSync.any { it.serverId == null }) {
            when (val currencies = currenciesPullTask.pullCurrencies()) {
                is IoResult.Success<List<Currency>> -> currencies.data
                is IoResult.Error -> return JoinEventResult.Error.OtherError
            }
        } else {
            currenciesBeforeSync
        }

        val getEventResult = if (token == null) {
            eventsRemoteStore.getEventByAccessCode(
                localId = 0L,
                serverId = serverId,
                pinCode = pinCode!!,
                currencies = currencies,
                localPersons = null
            )
        } else {
            eventsRemoteStore.getEventByToken(
                localId = 0L,
                serverId = serverId,
                token = token,
                currencies = currencies,
                localPersons = null
            )
        }

        val remoteEvent = when (getEventResult) {
            is GetEventResult.Event -> getEventResult.event

            is GetEventResult.Error -> return when (getEventResult.error) {
                EventNetworkError.InvalidAccessCode -> JoinEventResult.Error.InvalidAccessCode
                EventNetworkError.InvalidToken -> JoinEventResult.Error.InvalidToken
                EventNetworkError.TokenExpired -> JoinEventResult.Error.TokenExpired
                EventNetworkError.UpdateRequired -> JoinEventResult.Error.UpdateRequired
                EventNetworkError.NotFound -> JoinEventResult.Error.EventNotFound

                EventNetworkError.Gone -> {
                    deleteEventUseCase.deleteLocalEventByServerId(serverId)
                    JoinEventResult.Error.EventNotFound
                }

                EventNetworkError.OtherError -> JoinEventResult.Error.OtherError
            }
        }

        val eventDetails = transactionHelper.immediateWriteTransaction {
            eventsLocalStore.deepInsert(
                eventToInsert = remoteEvent.event,
                personsToInsert = remoteEvent.persons,
                prefetchedLocalCurrencies = remoteEvent.currencies,
                inTransaction = false,
            )
        }

        return JoinEventResult.NewCurrentEvent(eventDetails)
    }

}
