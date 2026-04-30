package com.inwords.expenses.feature.events.domain.store.remote

import com.inwords.expenses.core.utils.IoResult
import com.inwords.expenses.feature.events.domain.CreateShareTokenUseCase.CreateShareTokenResult
import com.inwords.expenses.feature.events.domain.model.Currency
import com.inwords.expenses.feature.events.domain.model.Event
import com.inwords.expenses.feature.events.domain.model.EventDetails
import com.inwords.expenses.feature.events.domain.model.Person

internal interface EventsRemoteStore {

    sealed interface EventNetworkError {
        sealed interface ByAccessCode : EventNetworkError
        sealed interface ByToken : EventNetworkError

        data object InvalidAccessCode : ByAccessCode
        data object InvalidToken : ByToken
        data object TokenExpired : ByToken
        data object NotFound : ByAccessCode, ByToken
        data object Gone : ByAccessCode, ByToken
        data object OtherError : ByAccessCode, ByToken
    }

    sealed interface GetEventResult<T : EventNetworkError> {
        data class Event<T : EventNetworkError>(val event: EventDetails) : GetEventResult<T>
        data class Error<T : EventNetworkError>(val error: T) : GetEventResult<T>
    }

    sealed interface DeleteEventResult {
        data object Deleted : DeleteEventResult
        data class Error(val error: EventNetworkError.ByAccessCode) : DeleteEventResult
    }

    suspend fun getEventByAccessCode(
        localId: Long,
        serverId: String,
        pinCode: String,
        currencies: List<Currency>,
        localPersons: List<Person>?,
    ): GetEventResult<EventNetworkError.ByAccessCode>

    suspend fun getEventByToken(
        localId: Long,
        serverId: String,
        token: String,
        currencies: List<Currency>,
        localPersons: List<Person>?,
    ): GetEventResult<EventNetworkError.ByToken>

    suspend fun createEvent(
        event: Event,
        currencies: List<Currency>,
        primaryCurrencyServerId: String,
        localPersons: List<Person>,
        idempotencyKey: String,
    ): IoResult<EventDetails>

    suspend fun deleteEvent(
        serverId: String,
        pinCode: String,
    ): DeleteEventResult

    suspend fun addPersonsToEvent(
        eventServerId: String,
        pinCode: String,
        localPersons: List<Person>,
        idempotencyKey: String,
    ): IoResult<List<Person>>

    suspend fun createEventShareToken(
        eventServerId: String,
        pinCode: String,
    ): CreateShareTokenResult

}
