package com.inwords.expenses.feature.events.data.network.store

import com.inwords.expenses.core.network.DomainErrorCodes
import com.inwords.expenses.core.network.HostConfig
import com.inwords.expenses.core.network.NetworkResult
import com.inwords.expenses.core.network.getErrorCode
import com.inwords.expenses.core.network.requestWithExceptionHandling
import com.inwords.expenses.core.network.toIoResult
import com.inwords.expenses.core.network.url
import com.inwords.expenses.core.utils.IoResult
import com.inwords.expenses.core.utils.SuspendLazy
import com.inwords.expenses.feature.events.data.network.dto.AddPersonsToEventRequest
import com.inwords.expenses.feature.events.data.network.dto.CreateEventRequest
import com.inwords.expenses.feature.events.data.network.dto.CreateEventShareTokenRequest
import com.inwords.expenses.feature.events.data.network.dto.CreateEventShareTokenResponse
import com.inwords.expenses.feature.events.data.network.dto.CreateUserDto
import com.inwords.expenses.feature.events.data.network.dto.DeleteEventRequest
import com.inwords.expenses.feature.events.data.network.dto.EventDto
import com.inwords.expenses.feature.events.data.network.dto.GetEventInfoRequest
import com.inwords.expenses.feature.events.data.network.dto.UserDto
import com.inwords.expenses.feature.events.domain.CreateShareTokenUseCase.CreateShareTokenResult
import com.inwords.expenses.feature.events.domain.model.Currency
import com.inwords.expenses.feature.events.domain.model.Event
import com.inwords.expenses.feature.events.domain.model.EventDetails
import com.inwords.expenses.feature.events.domain.model.EventShareToken
import com.inwords.expenses.feature.events.domain.model.Person
import com.inwords.expenses.feature.events.domain.store.remote.EventsRemoteStore
import com.inwords.expenses.feature.events.domain.store.remote.EventsRemoteStore.DeleteEventResult
import com.inwords.expenses.feature.events.domain.store.remote.EventsRemoteStore.EventNetworkError
import com.inwords.expenses.feature.events.domain.store.remote.EventsRemoteStore.GetEventResult
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType

internal class EventsRemoteStoreImpl(
    private val client: SuspendLazy<HttpClient>,
    private val hostConfig: HostConfig,
) : EventsRemoteStore {

    override suspend fun getEventByAccessCode(
        localId: Long,
        serverId: String,
        pinCode: String,
        currencies: List<Currency>,
        localPersons: List<Person>?
    ): GetEventResult<EventNetworkError.ByAccessCode> {
        val result = getEventInternal(
            localId = localId,
            serverId = serverId,
            pinCode = pinCode,
            token = null,
            currencies = currencies,
            localPersons = localPersons
        )

        return when (result) {
            is NetworkResult.Ok -> GetEventResult.Event(result.data)
            is NetworkResult.Error.Http.Client -> GetEventResult.Error(result.toEventNetworkErrorByAccessCode())
            is NetworkResult.Error -> GetEventResult.Error(EventNetworkError.OtherError)
        }
    }

    override suspend fun getEventByToken(
        localId: Long,
        serverId: String,
        token: String,
        currencies: List<Currency>,
        localPersons: List<Person>?
    ): GetEventResult<EventNetworkError.ByToken> {
        val result = getEventInternal(
            localId = localId,
            serverId = serverId,
            pinCode = null,
            token = token,
            currencies = currencies,
            localPersons = localPersons
        )

        return when (result) {
            is NetworkResult.Ok -> GetEventResult.Event(result.data)
            is NetworkResult.Error.Http.Client -> GetEventResult.Error(result.toEventNetworkErrorByToken())
            is NetworkResult.Error -> GetEventResult.Error(EventNetworkError.OtherError)
        }
    }

    private suspend fun getEventInternal(
        localId: Long,
        serverId: String,
        pinCode: String?,
        token: String?,
        currencies: List<Currency>,
        localPersons: List<Person>?,
    ): NetworkResult<EventDetails> {
        require((pinCode != null) xor (token != null)) {
            "Either pinCode or token must be provided"
        }

        return client.requestWithExceptionHandling {
            post {
                url(hostConfig) {
                    pathSegments = listOf("api", "v2", "user", "event", serverId)
                }
                contentType(ContentType.Application.Json)
                setBody(
                    GetEventInfoRequest(
                        pinCode = pinCode,
                        token = token
                    )
                )
            }.body<EventDto>().toEventDetails(
                localEventId = localId,
                localPersons = localPersons,
                currencies = currencies
            )
        }
    }

    override suspend fun createEvent(
        event: Event,
        currencies: List<Currency>,
        primaryCurrencyServerId: String,
        localPersons: List<Person>,
    ): IoResult<EventDetails> {
        return client.requestWithExceptionHandling {
            post {
                url(hostConfig) { pathSegments = listOf("api", "user", "event") }
                contentType(ContentType.Application.Json)
                setBody(
                    CreateEventRequest(
                        name = event.name,
                        currencyId = primaryCurrencyServerId,
                        users = localPersons.map { it.toCreateUserDto() },
                        pinCode = event.pinCode,
                    )
                )
            }.body<EventDto>().toEventDetails(event.id, localPersons, currencies)
        }.toIoResult()
    }

    override suspend fun deleteEvent(serverId: String, pinCode: String): DeleteEventResult {
        val result = client.requestWithExceptionHandling {
            delete {
                url(hostConfig) {
                    pathSegments = listOf("api", "user", "event", serverId)
                }
                setBody(DeleteEventRequest(pinCode = pinCode))
                header(HttpHeaders.ContentType, ContentType.Application.Json)
            }.body<Unit>()
        }

        return when (result) {
            is NetworkResult.Ok -> DeleteEventResult.Deleted
            is NetworkResult.Error.Http.Client -> DeleteEventResult.Error(result.toEventNetworkErrorByAccessCode())
            is NetworkResult.Error -> DeleteEventResult.Error(EventNetworkError.OtherError)
        }
    }

    override suspend fun addPersonsToEvent(
        eventServerId: String,
        pinCode: String,
        localPersons: List<Person>
    ): IoResult<List<Person>> {
        return client.requestWithExceptionHandling {
            post {
                url(hostConfig) {
                    pathSegments = listOf("api", "v2", "user", "event", eventServerId, "users")
                }
                contentType(ContentType.Application.Json)
                setBody(
                    AddPersonsToEventRequest(
                        users = localPersons.map { it.toCreateUserDto() },
                        pinCode = pinCode
                    )
                )
            }.body<List<UserDto>>().mapIndexed { i, dto ->
                dto.toPerson(localPersonId = localPersons[i].id)
            }
        }.toIoResult()
    }

    override suspend fun createEventShareToken(
        eventServerId: String,
        pinCode: String,
    ): CreateShareTokenResult {
        val result = client.requestWithExceptionHandling {
            post {
                url(hostConfig) {
                    pathSegments = listOf("api", "v2", "user", "event", eventServerId, "share-token")
                }
                contentType(ContentType.Application.Json)
                setBody(CreateEventShareTokenRequest(pinCode = pinCode))
            }.body<CreateEventShareTokenResponse>().let { response ->
                EventShareToken(
                    token = response.token,
                    expiresAt = response.expiresAt,
                )
            }
        }.toIoResult()

        return when (result) {
            is IoResult.Success -> CreateShareTokenResult.Created(result.data)
            is IoResult.Error -> CreateShareTokenResult.RemoteFailed
        }
    }

    private fun EventDto.toEventDetails(localEventId: Long, localPersons: List<Person>?, currencies: List<Currency>): EventDetails {
        val primaryCurrency = currencies.first { it.serverId == currencyId }
        return EventDetails(
            event = Event(id = localEventId, serverId = id, name = name, pinCode = pinCode, primaryCurrencyId = primaryCurrency.id),
            currencies = currencies,
            persons = users.mapIndexed { i, dto ->
                dto.toPerson(localPersonId = localPersons?.getOrNull(i)?.takeIf { it.name == dto.name }?.id)
            },
            primaryCurrency = primaryCurrency,
        )
    }

    private fun Person.toCreateUserDto(): CreateUserDto {
        return CreateUserDto(name = name)
    }

    private fun UserDto.toPerson(localPersonId: Long?): Person {
        return Person(id = localPersonId ?: 0L, serverId = id, name = name)
    }

    private fun NetworkResult.Error.Http.Client.toEventNetworkErrorByAccessCode(): EventNetworkError.ByAccessCode {
        return when (exception.response.status) {
            HttpStatusCode.Forbidden -> EventNetworkError.InvalidAccessCode
            HttpStatusCode.NotFound -> EventNetworkError.NotFound
            HttpStatusCode.Gone -> EventNetworkError.Gone
            else -> EventNetworkError.OtherError
        }
    }

    private suspend fun NetworkResult.Error.Http.Client.toEventNetworkErrorByToken(): EventNetworkError.ByToken {
        return when (exception.response.status) {
            HttpStatusCode.Unauthorized -> {
                when (getErrorCode()) {
                    DomainErrorCodes.INVALID_TOKEN -> EventNetworkError.InvalidToken
                    DomainErrorCodes.TOKEN_EXPIRED -> EventNetworkError.TokenExpired
                    else -> EventNetworkError.InvalidToken
                }
            }

            HttpStatusCode.Forbidden -> EventNetworkError.InvalidToken
            HttpStatusCode.NotFound -> EventNetworkError.NotFound
            HttpStatusCode.Gone -> EventNetworkError.Gone
            else -> EventNetworkError.OtherError
        }
    }

}
