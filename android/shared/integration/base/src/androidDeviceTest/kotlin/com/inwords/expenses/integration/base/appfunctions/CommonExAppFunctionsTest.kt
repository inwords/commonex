package com.inwords.expenses.integration.base.appfunctions

import androidx.appfunctions.AppFunctionContext
import androidx.appfunctions.AppFunctionElementAlreadyExistsException
import androidx.appfunctions.AppFunctionElementNotFoundException
import androidx.appfunctions.AppFunctionInvalidArgumentException
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inwords.expenses.core.locator.ComponentsMap
import com.inwords.expenses.feature.events.api.EventsComponent
import com.inwords.expenses.feature.events.domain.CreateEventFromParametersUseCase
import com.inwords.expenses.feature.events.domain.GetCurrenciesUseCase
import com.inwords.expenses.feature.events.domain.GetEventsUseCase
import com.inwords.expenses.feature.events.domain.model.Currency
import com.inwords.expenses.feature.events.domain.model.Event
import com.inwords.expenses.feature.events.domain.model.EventDetails
import com.inwords.expenses.feature.events.domain.store.local.EventsLocalStore
import com.inwords.expenses.feature.expenses.api.ExpensesComponent
import com.inwords.expenses.feature.expenses.domain.ExpensesInteractor
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class CommonExAppFunctionsTest {

    private lateinit var appFunctions: CommonExAppFunctions
    private lateinit var appFunctionContext: AppFunctionContext

    private lateinit var eventsComponent: EventsComponent
    private lateinit var expensesComponent: ExpensesComponent

    private lateinit var createEventFromParametersUseCase: CreateEventFromParametersUseCase
    private lateinit var getCurrenciesUseCase: GetCurrenciesUseCase
    private lateinit var getEventsUseCase: GetEventsUseCase
    private lateinit var eventsLocalStore: EventsLocalStore
    private lateinit var expensesInteractor: ExpensesInteractor

    @Before
    fun setup() {
        appFunctions = CommonExAppFunctions()
        appFunctionContext = mockk(relaxed = true)

        eventsComponent = mockk(relaxed = true)
        expensesComponent = mockk(relaxed = true)

        createEventFromParametersUseCase = mockk(relaxed = true)
        getCurrenciesUseCase = mockk(relaxed = true)
        getEventsUseCase = mockk(relaxed = true)
        eventsLocalStore = mockk(relaxed = true)
        expensesInteractor = mockk(relaxed = true)

        every { eventsComponent.createEventFromParametersUseCaseLazy } returns lazy { createEventFromParametersUseCase }
        every { eventsComponent.getCurrenciesUseCaseLazy } returns lazy { getCurrenciesUseCase }
        every { eventsComponent.getEventsUseCaseLazy } returns lazy { getEventsUseCase }
        every { eventsComponent.eventsLocalStore } returns lazy { eventsLocalStore }

        every { expensesComponent.expensesInteractorLazy } returns lazy { expensesInteractor }

        ComponentsMap.registerComponent(EventsComponent::class, lazy { eventsComponent })
        ComponentsMap.registerComponent(ExpensesComponent::class, lazy { expensesComponent })
    }

    @After
    fun teardown() {
        // Not safely supported to clear ComponentsMap in this simple mock, but good enough for test scope
    }

    @Test
    fun listCurrencies_shouldReturnSortedCurrencies() = runBlocking {
        every { getCurrenciesUseCase.getCurrencies() } returns flowOf(
            listOf(
                Currency(id = 2, serverId = null, code = "USD", name = "Dollar"),
                Currency(id = 1, serverId = null, code = "EUR", name = "Euro"),
            )
        )

        val result = appFunctions.listCurrencies(appFunctionContext)

        assertEquals(listOf("EUR", "USD"), result.map { currency -> currency.code })
        assertEquals(listOf("Euro", "Dollar"), result.map { currency -> currency.name })
    }

    @Test
    fun createEvent_shouldUseRequestedCurrencyAndOwnerIdentity() = runBlocking {
        val createdEventDetails = EventDetails(
            event = Event(id = 7, serverId = null, name = "Weekend Trip", pinCode = "1234", primaryCurrencyId = 1),
            currencies = emptyList(),
            persons = emptyList(),
            primaryCurrency = Currency(id = 1, serverId = null, code = "EUR", name = "Euro"),
        )
        every { getCurrenciesUseCase.getCurrencies() } returns flowOf(
            listOf(
                Currency(id = 2, serverId = null, code = "USD", name = "Dollar"),
                Currency(id = 1, serverId = null, code = "EUR", name = "Euro"),
            )
        )
        coEvery {
            createEventFromParametersUseCase.createEvent(
                any<String>(),
                any<String>(),
                any<Currency>(),
                any<List<String>>(),
            )
        } returns createdEventDetails

        val result = appFunctions.createEvent(
            appFunctionContext = appFunctionContext,
            name = "Weekend Trip",
            primaryCurrencyCode = "eur",
            ownerName = "Vasya",
        )

        assertEquals(7L, result.id)
        assertEquals("Weekend Trip", result.name)
        assertEquals(0, result.participantCount)
        assertEquals("EUR", result.primaryCurrencyCode)
        coVerify {
            createEventFromParametersUseCase.createEvent(
                name = "Weekend Trip",
                owner = "Vasya",
                primaryCurrency = match { it.id == 1L && it.code == "EUR" },
                otherPersons = emptyList(),
            )
        }
    }

    @Test
    fun createEvent_shouldRejectBlankOwnerName() = runBlocking {
        try {
            appFunctions.createEvent(
                appFunctionContext = appFunctionContext,
                name = "Weekend Trip",
                primaryCurrencyCode = "EUR",
                ownerName = "   ",
            )
            fail("Expected AppFunctionInvalidArgumentException to be thrown.")
        } catch (exception: AppFunctionInvalidArgumentException) {
            assertEquals("Owner name cannot be empty.", exception.message)
        }
    }

    @Test
    fun createEvent_shouldExplainSupportedCurrenciesWhenCodeIsUnknown() = runBlocking {
        every { getCurrenciesUseCase.getCurrencies() } returns flowOf(
            listOf(
                Currency(id = 2, serverId = null, code = "USD", name = "Dollar"),
                Currency(id = 1, serverId = null, code = "EUR", name = "Euro"),
            )
        )

        try {
            appFunctions.createEvent(
                appFunctionContext = appFunctionContext,
                name = "Weekend Trip",
                primaryCurrencyCode = "GBP",
                ownerName = "Vasya",
            )
            fail("Expected AppFunctionElementNotFoundException to be thrown.")
        } catch (exception: AppFunctionElementNotFoundException) {
            assertEquals(
                "Currency 'GBP' is not available. Supported currency codes: EUR, USD",
                exception.message,
            )
        }
    }

    @Test
    fun listEvents_shouldReturnDetailedSummaries() = runBlocking {
        val events = listOf(
            Event(id = 1, serverId = null, name = "Trip", pinCode = "1234", primaryCurrencyId = 1),
            Event(id = 2, serverId = null, name = "Dinner", pinCode = "5678", primaryCurrencyId = 1)
        )
        val currency = Currency(id = 1, serverId = null, code = "EUR", name = "Euro")
        every { getEventsUseCase.getEvents() } returns flowOf(events)
        coEvery { eventsLocalStore.getEventWithDetails(1) } returns EventDetails(
            event = events[0],
            currencies = listOf(currency),
            persons = listOf(
                com.inwords.expenses.feature.events.domain.model.Person(id = 1, serverId = null, name = "Alice"),
                com.inwords.expenses.feature.events.domain.model.Person(id = 2, serverId = null, name = "Bob"),
            ),
            primaryCurrency = currency,
        )
        coEvery { eventsLocalStore.getEventWithDetails(2) } returns EventDetails(
            event = events[1],
            currencies = listOf(currency),
            persons = listOf(
                com.inwords.expenses.feature.events.domain.model.Person(id = 3, serverId = null, name = "Chris"),
            ),
            primaryCurrency = currency,
        )

        val result = appFunctions.listEvents(appFunctionContext)

        assertEquals(2, result.size)
        assertEquals("Trip", result[0].name)
        assertEquals(2, result[0].participantCount)
        assertEquals("EUR", result[0].primaryCurrencyCode)
        assertEquals("Dinner", result[1].name)
        assertEquals(1, result[1].participantCount)
        assertEquals("EUR", result[1].primaryCurrencyCode)
    }

    @Test
    fun listEvents_shouldReturnEmptyListWhenNoEventsExist() = runBlocking {
        every { getEventsUseCase.getEvents() } returns flowOf(emptyList())

        val result = appFunctions.listEvents(appFunctionContext)

        assertTrue(result.isEmpty())
    }

    @Test
    fun addParticipant_shouldThrowWhenEventIsMissing() = runBlocking {
        every { eventsLocalStore.getEventsFlow() } returns flowOf(emptyList())

        try {
            appFunctions.addParticipant(appFunctionContext, "Unknown Event", "Alice")
            fail("Expected AppFunctionElementNotFoundException to be thrown.")
        } catch (exception: AppFunctionElementNotFoundException) {
            assertEquals("Event 'Unknown Event' was not found.", exception.message)
        }
    }

    @Test
    fun addParticipant_shouldAddPersonSuccessfully() = runBlocking {
        val event = Event(id = 1, serverId = null, name = "Trip", pinCode = "1234", primaryCurrencyId = 1)
        val currency = Currency(id = 1, serverId = null, code = "EUR", name = "Euro")
        val eventDetails = EventDetails(
            event = event,
            currencies = listOf(currency),
            persons = emptyList(),
            primaryCurrency = currency,
        )
        every { eventsLocalStore.getEventsFlow() } returns flowOf(listOf(event))
        coEvery { eventsLocalStore.getEventWithDetails(1) } returns eventDetails
        coEvery { eventsLocalStore.insertPersonsWithCrossRefs(any(), any(), any()) } returns mockk()

        val result = appFunctions.addParticipant(appFunctionContext, "Trip", "Alice")

        assertEquals("Trip", result.event.name)
        assertEquals(1, result.event.participantCount)
        assertEquals("EUR", result.event.primaryCurrencyCode)
        assertEquals("Alice", result.participantName)
        coVerify {
            eventsLocalStore.insertPersonsWithCrossRefs(
                eventId = 1,
                persons = match { it.size == 1 && it.first().name == "Alice" },
                inTransaction = true
            )
        }
    }

    @Test
    fun addParticipant_shouldRejectDuplicateNamesIgnoringCase() = runBlocking {
        val event = Event(id = 1, serverId = null, name = "Trip", pinCode = "1234", primaryCurrencyId = 1)
        val currency = Currency(id = 1, serverId = null, code = "EUR", name = "Euro")
        val eventDetails = EventDetails(
            event = event,
            currencies = listOf(currency),
            persons = listOf(
                com.inwords.expenses.feature.events.domain.model.Person(id = 11, serverId = null, name = "Alice"),
            ),
            primaryCurrency = currency,
        )
        every { eventsLocalStore.getEventsFlow() } returns flowOf(listOf(event))
        coEvery { eventsLocalStore.getEventWithDetails(1) } returns eventDetails

        try {
            appFunctions.addParticipant(appFunctionContext, "Trip", "alice")
            fail("Expected AppFunctionElementAlreadyExistsException to be thrown.")
        } catch (exception: AppFunctionElementAlreadyExistsException) {
            assertEquals(
                "Participant 'alice' already exists in event 'Trip'.",
                exception.message,
            )
        }
    }
}
