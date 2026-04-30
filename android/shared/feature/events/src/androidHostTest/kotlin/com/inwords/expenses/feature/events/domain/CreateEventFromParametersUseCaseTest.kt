package com.inwords.expenses.feature.events.domain

import com.inwords.expenses.core.testutils.TestClientCreateIdGenerator
import com.inwords.expenses.feature.events.domain.model.Currency
import com.inwords.expenses.feature.events.domain.model.Event
import com.inwords.expenses.feature.events.domain.model.EventDetails
import com.inwords.expenses.feature.events.domain.model.Person
import com.inwords.expenses.feature.events.domain.store.local.EventsLocalStore
import com.inwords.expenses.feature.settings.api.SettingsRepository
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class CreateEventFromParametersUseCaseTest {

    private val eventsLocalStore = mockk<EventsLocalStore>(relaxed = true)
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val clientCreateIdGenerator = TestClientCreateIdGenerator(
        "owner-client-id",
        "bob-client-id",
        "charlie-client-id",
        "event-client-id",
    )
    private val useCase = CreateEventFromParametersUseCase(
        eventsLocalStoreLazy = lazy { eventsLocalStore },
        settingsRepositoryLazy = lazy { settingsRepository },
        clientCreateIdGeneratorLazy = lazy { clientCreateIdGenerator },
    )

    private val eventDetails = EventDetails(
        event = Event(id = 1L, serverId = null, clientCreateId = "persisted-event-client-id", name = "Trip", pinCode = "1234", primaryCurrencyId = 1L),
        currencies = emptyList(),
        persons = listOf(
            Person(id = 1L, serverId = null, clientCreateId = "persisted-person-1", name = "Alice"),
            Person(id = 2L, serverId = null, clientCreateId = "persisted-person-2", name = "Bob"),
        ),
        primaryCurrency = Currency(1L, null, "EUR", "Euro", BigDecimal.ONE),
    )

    @Test
    fun createEvent_trimsNameAndOwnerAndFiltersEmptyOthers_callsDeepInsertAndSetCurrentEventAndPerson() = runTest {
        val eventSlot = slot<Event>()
        val personsSlot = slot<List<Person>>()
        coEvery {
            eventsLocalStore.deepInsert(
                eventToInsert = capture(eventSlot),
                personsToInsert = capture(personsSlot),
                prefetchedLocalCurrencies = any(),
                inTransaction = true,
            )
        } returns eventDetails
        coEvery { settingsRepository.setCurrentEventAndPerson(any(), any()) } returns Unit

        val result = useCase.createEvent(
            name = "  Trip  ",
            owner = "  Alice  ",
            primaryCurrencyId = 1L,
            otherPersons = listOf(" Bob ", "", "  ", "Charlie"),
        )

        assertEquals(eventDetails, result)
        assertEquals("Trip", eventSlot.captured.name)
        assertEquals("event-client-id", eventSlot.captured.clientCreateId)
        assertEquals("Alice", personsSlot.captured.first().name)
        assertEquals("owner-client-id", personsSlot.captured.first().clientCreateId)
        assertEquals(3, personsSlot.captured.size)
        assertEquals("Bob", personsSlot.captured[1].name)
        assertEquals("bob-client-id", personsSlot.captured[1].clientCreateId)
        assertEquals("Charlie", personsSlot.captured[2].name)
        assertEquals("charlie-client-id", personsSlot.captured[2].clientCreateId)
        assertTrue(eventSlot.captured.pinCode.isNotEmpty())
        coVerify(exactly = 1) {
            settingsRepository.setCurrentEventAndPerson(
                eventId = eventDetails.event.id,
                personId = eventDetails.persons.first().id,
            )
        }
    }
}
