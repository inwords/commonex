package com.inwords.expenses.feature.events.domain

import com.inwords.expenses.core.storage.utils.TransactionHelper
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

    private val event = Event(1L, "ev-1", "Trip", "1234", 1L)
    private val person = Person(1L, null, "Alice")
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
        assert(newCurrentEvent.event.event.id == 1L)
        coVerify(exactly = 1) { settingsRepository.setCurrentEventId(1L) }
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
        } returns GetEventResult.Event(eventDetails)
        coEvery { transactionHelper.immediateWriteTransaction<EventDetails>(any()) } returns eventDetails
        coEvery { settingsRepository.setCurrentEventId(1L) } returns Unit

        val result = useCase.joinEvent("ev-1", "1234", null)

        val _ = assertIs<JoinEventUseCase.JoinEventResult.NewCurrentEvent>(result)
        coVerify(exactly = 1) { settingsRepository.setCurrentEventId(1L) }
    }
}
