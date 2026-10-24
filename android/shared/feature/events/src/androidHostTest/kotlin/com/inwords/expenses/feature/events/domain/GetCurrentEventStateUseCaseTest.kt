package com.inwords.expenses.feature.events.domain

import app.cash.turbine.test
import com.inwords.expenses.feature.events.domain.model.Currency
import com.inwords.expenses.feature.events.domain.model.Event
import com.inwords.expenses.feature.events.domain.model.EventDetails
import com.inwords.expenses.feature.events.domain.store.local.EventsLocalStore
import com.inwords.expenses.feature.settings.api.SettingsRepository
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
internal class GetCurrentEventStateUseCaseTest {

    @Test
    fun currentEvent_tracksSelectionAndStopsObservingLeftEvents() = runTest {
        val selectedEventId = MutableStateFlow<Long?>(null)
        val firstDetails = eventDetails(1L)
        val secondDetails = eventDetails(2L)
        val firstEvent = MutableStateFlow<EventDetails?>(firstDetails)
        val secondEvent = MutableStateFlow<EventDetails?>(secondDetails)
        val eventsLocalStore = mockk<EventsLocalStore> {
            every { getEventWithDetailsFlow(1L) } returns firstEvent
            every { getEventWithDetailsFlow(2L) } returns secondEvent
        }
        val settingsRepository = mockk<SettingsRepository> {
            every { getCurrentEventId() } returns selectedEventId
        }
        coEvery { settingsRepository.clearCurrentEventAndPerson() } coAnswers { selectedEventId.value = null }
        val useCase = GetCurrentEventStateUseCase(lazyOf(eventsLocalStore), lazyOf(settingsRepository), backgroundScope)
        val leaveEvent = LeaveEventUseCase(lazyOf(settingsRepository))

        useCase.currentEvent.test {
            assertNull(awaitItem())
            selectedEventId.value = 1L
            assertEquals(firstDetails, awaitItem())
            selectedEventId.value = 2L
            assertEquals(secondDetails, awaitItem())

            firstEvent.value = firstDetails.copy(event = firstDetails.event.copy(name = "Old event changed"))
            runCurrent()
            expectNoEvents()

            val updatedSecondDetails = secondDetails.copy(event = secondDetails.event.copy(name = "Current event changed"))
            secondEvent.value = updatedSecondDetails
            assertEquals(updatedSecondDetails, awaitItem())

            leaveEvent.leaveEvent()
            assertNull(awaitItem())
            secondEvent.value = secondDetails
            runCurrent()
            expectNoEvents()
        }

        coVerify(exactly = 1) { settingsRepository.clearCurrentEventAndPerson() }
        coVerify(exactly = 0) { eventsLocalStore.delete(any()) }
    }

    private fun eventDetails(id: Long): EventDetails {
        val currency = Currency(1L, "currency-1", "EUR", "Euro", BigDecimal.ONE)
        return EventDetails(
            event = Event(id, "event-$id", "Event $id", "1234", currency.id, "client-$id"),
            currencies = listOf(currency),
            persons = emptyList(),
            primaryCurrency = currency,
        )
    }
}
