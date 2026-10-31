package com.inwords.expenses.feature.events.domain

import com.inwords.expenses.core.storage.utils.TransactionHelper
import com.inwords.expenses.core.utils.IoResult
import com.inwords.expenses.feature.events.domain.model.Currency
import com.inwords.expenses.feature.events.domain.model.Event
import com.inwords.expenses.feature.events.domain.model.EventDetails
import com.inwords.expenses.feature.events.domain.model.Person
import com.inwords.expenses.feature.events.domain.store.local.CurrenciesLocalStore
import com.inwords.expenses.feature.events.domain.store.local.EventsLocalStore
import com.inwords.expenses.feature.events.domain.store.remote.EventsRemoteStore
import com.inwords.expenses.feature.events.domain.store.remote.EventsRemoteStore.GetEventResult
import com.inwords.expenses.feature.events.domain.task.CurrenciesPullTask
import com.inwords.expenses.feature.settings.api.SettingsRepository
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

internal class JoinEventUseCaseTest {

    private val transactionHelper = mockk<TransactionHelper>(relaxed = true)
    private val eventsLocalStore = mockk<EventsLocalStore>(relaxed = true)
    private val eventsRemoteStore = mockk<EventsRemoteStore>(relaxed = true)
    private val deleteEventUseCase = mockk<DeleteEventUseCase>(relaxed = true)
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val currenciesLocalStore = mockk<CurrenciesLocalStore>(relaxed = true)
    private val currenciesPullTask = mockk<CurrenciesPullTask>(relaxed = true)

    private val event = Event(id = 1L, serverId = "ev-1", clientCreateId = "event-client-1", name = "Trip", pinCode = "1234", primaryCurrencyId = 1L)
    private val person = Person(id = 1L, serverId = null, clientCreateId = "person-client-1", name = "Alice")
    private val currency = Currency(1L, "cur-1", "EUR", "Euro", BigDecimal.ONE)
    private val eventDetails = EventDetails(
        event = event,
        currencies = listOf(currency),
        persons = listOf(person),
        primaryCurrency = currency,
    )

    private val useCase = JoinEventUseCase(
        transactionHelperLazy = lazy { transactionHelper },
        eventsLocalStoreLazy = lazy { eventsLocalStore },
        eventsRemoteStoreLazy = lazy { eventsRemoteStore },
        deleteEventUseCase = lazy { deleteEventUseCase },
        settingsRepositoryLazy = lazy { settingsRepository },
        currenciesLocalStoreLazy = lazy { currenciesLocalStore },
        currenciesPullTaskLazy = lazy { currenciesPullTask },
    )

    @Test
    fun joinLocalEvent_whenEventExists_setsCurrentEventIdAndReturnsTrue() = runTest {
        coEvery { eventsLocalStore.getEvent(1L) } returns event
        coEvery { settingsRepository.setCurrentEventId(1L) } returns Unit

        val result = useCase.joinLocalEvent(1L)

        assertTrue(result)
        coVerify(exactly = 1) { settingsRepository.setCurrentEventId(1L) }
    }

    @Test
    fun joinLocalEvent_whenEventMissing_returnsFalse() = runTest {
        coEvery { eventsLocalStore.getEvent(2L) } returns null

        val result = useCase.joinLocalEvent(2L)

        assertFalse(result)
        coVerify(exactly = 0) { settingsRepository.setCurrentEventId(any()) }
    }

    @Test
    fun joinEvent_whenLocalEventExists_returnsNewCurrentEventAndSetsCurrentEventId() = runTest {
        coEvery { eventsLocalStore.getEventWithDetailsByServerId("ev-1") } returns eventDetails
        coEvery { settingsRepository.setCurrentEventId(1L) } returns Unit

        val result = useCase.joinEvent("ev-1", "1234", null)

        val newCurrentEvent = assertIs<JoinEventUseCase.JoinEventResult.NewCurrentEvent>(result)
        assertEquals(eventDetails, newCurrentEvent.event)
        coVerify(exactly = 1) { settingsRepository.setCurrentEventId(1L) }
        coVerify(exactly = 0) { eventsRemoteStore.getEventByAccessCode(any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { eventsRemoteStore.getEventByToken(any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { currenciesPullTask.pullCurrencies() }
    }

    @Test
    fun joinEvent_whenRemoteReturnsInvalidAccessCode_returnsInvalidAccessCode() = runTest {
        coEvery { eventsLocalStore.getEventWithDetailsByServerId("ev-1") } returns null
        coEvery { currenciesLocalStore.getCurrencies() } returns flowOf(listOf(currency))
        coEvery {
            eventsRemoteStore.getEventByAccessCode(
                localId = 0L,
                serverId = "ev-1",
                pinCode = "1234",
                currencies = any(),
                localPersons = any(),
            )
        } returns GetEventResult.Error(EventsRemoteStore.EventNetworkError.InvalidAccessCode)

        val result = useCase.joinEvent("ev-1", "1234", null)

        val _ = assertIs<JoinEventUseCase.JoinEventResult.Error.InvalidAccessCode>(result)
    }

    @Test
    fun joinEvent_whenRemoteReturnsInvalidToken_returnsInvalidToken() = runTest {
        coEvery { eventsLocalStore.getEventWithDetailsByServerId("ev-1") } returns null
        coEvery { currenciesLocalStore.getCurrencies() } returns flowOf(listOf(currency))
        coEvery {
            eventsRemoteStore.getEventByToken(
                localId = 0L,
                serverId = "ev-1",
                token = "bad-token",
                currencies = any(),
                localPersons = any(),
            )
        } returns GetEventResult.Error(EventsRemoteStore.EventNetworkError.InvalidToken)

        val result = useCase.joinEvent("ev-1", null, "bad-token")

        val _ = assertIs<JoinEventUseCase.JoinEventResult.Error.InvalidToken>(result)
    }

    @Test
    fun joinEvent_whenPinJoinRequiresUpdate_preservesSelectionAndDoesNotInsertEvent() = runTest {
        coEvery { eventsLocalStore.getEventWithDetailsByServerId("ev-1") } returns null
        coEvery { currenciesLocalStore.getCurrencies() } returns flowOf(listOf(currency))
        coEvery {
            eventsRemoteStore.getEventByAccessCode(0L, "ev-1", "1234", listOf(currency), null)
        } returns GetEventResult.Error(EventsRemoteStore.EventNetworkError.UpdateRequired)

        val result = useCase.joinEvent("ev-1", "1234", null)

        assertEquals(JoinEventUseCase.JoinEventResult.Error.UpdateRequired, result)
        coVerify(exactly = 0) { settingsRepository.setCurrentEventId(any()) }
        coVerify(exactly = 0) { eventsLocalStore.deepInsert(any(), any(), any(), any()) }
        coVerify(exactly = 0) { deleteEventUseCase.deleteLocalEventByServerId(any()) }
    }

    @Test
    fun joinEvent_whenTokenJoinRequiresUpdate_preservesSelectionAndDoesNotInsertEvent() = runTest {
        coEvery { eventsLocalStore.getEventWithDetailsByServerId("ev-1") } returns null
        coEvery { currenciesLocalStore.getCurrencies() } returns flowOf(listOf(currency))
        coEvery {
            eventsRemoteStore.getEventByToken(0L, "ev-1", "token", listOf(currency), null)
        } returns GetEventResult.Error(EventsRemoteStore.EventNetworkError.UpdateRequired)

        val result = useCase.joinEvent("ev-1", null, "token")

        assertEquals(JoinEventUseCase.JoinEventResult.Error.UpdateRequired, result)
        coVerify(exactly = 0) { settingsRepository.setCurrentEventId(any()) }
        coVerify(exactly = 0) { eventsLocalStore.deepInsert(any(), any(), any(), any()) }
        coVerify(exactly = 0) { deleteEventUseCase.deleteLocalEventByServerId(any()) }
    }

    @Test
    fun joinEvent_whenRemoteReturnsEventNotFound_returnsEventNotFound() = runTest {
        coEvery { eventsLocalStore.getEventWithDetailsByServerId("ev-1") } returns null
        coEvery { currenciesLocalStore.getCurrencies() } returns flowOf(listOf(currency))
        coEvery {
            eventsRemoteStore.getEventByAccessCode(
                localId = 0L,
                serverId = "ev-1",
                pinCode = "1234",
                currencies = any(),
                localPersons = any(),
            )
        } returns GetEventResult.Error(EventsRemoteStore.EventNetworkError.NotFound)

        val result = useCase.joinEvent("ev-1", "1234", null)

        val _ = assertIs<JoinEventUseCase.JoinEventResult.Error.EventNotFound>(result)
    }

    @Test
    fun joinEvent_whenRemoteSuccess_insertsAndReturnsNewCurrentEvent() = runTest {
        val remoteDetails = eventDetails.copy(event = event.copy(id = 0L))
        val insertedDetails = eventDetails.copy(event = event.copy(id = 55L))
        coEvery { eventsLocalStore.getEventWithDetailsByServerId("ev-1") } returns null
        coEvery { currenciesLocalStore.getCurrencies() } returns flowOf(listOf(currency))
        coEvery {
            eventsRemoteStore.getEventByAccessCode(
                localId = 0L,
                serverId = "ev-1",
                pinCode = "1234",
                currencies = any(),
                localPersons = any(),
            )
        } returns GetEventResult.Event(remoteDetails)
        coEvery {
            eventsLocalStore.deepInsert(remoteDetails.event, remoteDetails.persons, remoteDetails.currencies, false)
        } returns insertedDetails
        coEvery { transactionHelper.immediateWriteTransaction<EventDetails>(any()) } coAnswers {
            val inserted = firstArg<suspend () -> EventDetails>().invoke()
            coVerify(exactly = 0) { settingsRepository.setCurrentEventId(any()) }
            inserted
        }

        val result = useCase.joinEvent("ev-1", "1234", null)

        assertEquals(JoinEventUseCase.JoinEventResult.NewCurrentEvent(insertedDetails), result)
        coVerify(exactly = 1) {
            eventsLocalStore.deepInsert(remoteDetails.event, remoteDetails.persons, remoteDetails.currencies, false)
        }
        coVerify(exactly = 1) { settingsRepository.setCurrentEventId(55L) }
    }

    @Test
    fun joinEvent_requiresExactlyOneCredentialBeforeAnySideEffects() = runTest {
        listOf(null to null, "1234" to "token").forEach { (accessCode, token) ->
            assertFailsWith<IllegalArgumentException> { useCase.joinEvent("ev-1", accessCode, token) }
        }

        coVerify(exactly = 0) { eventsLocalStore.getEventWithDetailsByServerId(any()) }
        coVerify(exactly = 0) { settingsRepository.setCurrentEventId(any()) }
    }

    @Test
    fun joinEvent_whenRequiredCurrencySyncFails_keepsSelectionAndSkipsRemoteJoin() = runTest {
        coEvery { eventsLocalStore.getEventWithDetailsByServerId("ev-1") } returns null
        val incompleteCurrencies = listOf(emptyList(), listOf(currency.copy(serverId = null)))
        incompleteCurrencies.forEach { currencies ->
            coEvery { currenciesLocalStore.getCurrencies() } returns flowOf(currencies)
            listOf(IoResult.Error.Retry, IoResult.Error.Failure).forEach { error ->
                coEvery { currenciesPullTask.pullCurrencies() } returns error

                assertEquals(JoinEventUseCase.JoinEventResult.Error.OtherError, useCase.joinEvent("ev-1", null, "token"))
            }
        }

        coVerify(exactly = 0) { eventsRemoteStore.getEventByAccessCode(any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { eventsRemoteStore.getEventByToken(any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { eventsLocalStore.deepInsert(any(), any(), any(), any()) }
        coVerify(exactly = 0) { settingsRepository.setCurrentEventId(any()) }
    }

    @Test
    fun joinEvent_whenCurrencySyncCompletes_usesSyncedCurrenciesForTokenJoin() = runTest {
        coEvery { eventsLocalStore.getEventWithDetailsByServerId("ev-1") } returns null
        coEvery { currenciesLocalStore.getCurrencies() } returns flowOf(listOf(currency.copy(serverId = null)))
        coEvery { currenciesPullTask.pullCurrencies() } returns IoResult.Success(listOf(currency))
        coEvery {
            eventsRemoteStore.getEventByToken(0L, "ev-1", "token", listOf(currency), null)
        } returns GetEventResult.Event(eventDetails)
        coEvery {
            eventsLocalStore.deepInsert(eventDetails.event, eventDetails.persons, eventDetails.currencies, false)
        } returns eventDetails
        coEvery { transactionHelper.immediateWriteTransaction<EventDetails>(any()) } coAnswers {
            firstArg<suspend () -> EventDetails>().invoke()
        }

        val result = useCase.joinEvent("ev-1", null, "token")

        assertEquals(JoinEventUseCase.JoinEventResult.NewCurrentEvent(eventDetails), result)
        coVerify(exactly = 1) { eventsRemoteStore.getEventByToken(0L, "ev-1", "token", listOf(currency), null) }
        coVerify(exactly = 0) { eventsRemoteStore.getEventByAccessCode(any(), any(), any(), any(), any()) }
        coVerify(exactly = 1) { settingsRepository.setCurrentEventId(event.id) }
    }

    @Test
    fun joinEvent_whenRemoteRejectsToken_preservesSelectionAndCleansUpOnlyGoneEvents() = runTest {
        coEvery { eventsLocalStore.getEventWithDetailsByServerId("ev-1") } returns null
        coEvery { currenciesLocalStore.getCurrencies() } returns flowOf(listOf(currency))
        val errors = listOf(
            EventsRemoteStore.EventNetworkError.TokenExpired to JoinEventUseCase.JoinEventResult.Error.TokenExpired,
            EventsRemoteStore.EventNetworkError.OtherError to JoinEventUseCase.JoinEventResult.Error.OtherError,
            EventsRemoteStore.EventNetworkError.Gone to JoinEventUseCase.JoinEventResult.Error.EventNotFound,
        )
        errors.forEach { (networkError, expected) ->
            coEvery {
                eventsRemoteStore.getEventByToken(0L, "ev-1", "token", listOf(currency), null)
            } returns GetEventResult.Error(networkError)

            assertEquals(expected, useCase.joinEvent("ev-1", null, "token"))
            coVerify(exactly = if (networkError == EventsRemoteStore.EventNetworkError.Gone) 1 else 0) {
                deleteEventUseCase.deleteLocalEventByServerId("ev-1")
            }
        }

        coVerify(exactly = 0) { settingsRepository.setCurrentEventId(any()) }
        coVerify(exactly = 0) { eventsLocalStore.deepInsert(any(), any(), any(), any()) }
    }
}
