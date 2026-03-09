package com.inwords.expenses.feature.events.domain.task

import com.inwords.expenses.core.utils.IoResult
import com.inwords.expenses.feature.events.domain.model.Currency
import com.inwords.expenses.feature.events.domain.model.Event
import com.inwords.expenses.feature.events.domain.model.EventDetails
import com.inwords.expenses.feature.events.domain.model.Person
import com.inwords.expenses.feature.events.domain.store.local.EventsLocalStore
import com.inwords.expenses.feature.events.domain.store.remote.EventsRemoteStore
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
internal class EventPersonsPushTaskTest {

    private val mainDispatcher = StandardTestDispatcher()
    private val eventsLocalStore = mockk<EventsLocalStore>(relaxed = true)
    private val eventsRemoteStore = mockk<EventsRemoteStore>()

    private val task = EventPersonsPushTask(
        eventsLocalStoreLazy = lazy { eventsLocalStore },
        eventsRemoteStoreLazy = lazy { eventsRemoteStore },
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `pushEventPersons returns failure when local event is missing`() = runTest {
        coEvery { eventsLocalStore.getEventWithDetails(1L) } returns null

        val result = task.pushEventPersons(1L)

        assertEquals(IoResult.Error.Failure, result)
    }

    @Test
    fun `pushEventPersons returns failure when local event has no server id`() = runTest {
        val localDetails = eventDetails(event = event(serverId = null))
        coEvery { eventsLocalStore.getEventWithDetails(localDetails.event.id) } returns localDetails

        val result = task.pushEventPersons(localDetails.event.id)

        assertEquals(IoResult.Error.Failure, result)
    }

    @Test
    fun `pushEventPersons returns success without remote call when there are no unsynced persons`() = runTest {
        val localDetails = eventDetails(
            persons = listOf(
                person(id = 1L, serverId = "srv-p1"),
                person(id = 2L, serverId = "srv-p2"),
            )
        )
        coEvery { eventsLocalStore.getEventWithDetails(localDetails.event.id) } returns localDetails

        val result = task.pushEventPersons(localDetails.event.id)

        assertEquals(IoResult.Success(Unit), result)
        coVerify(exactly = 0) { eventsRemoteStore.addPersonsToEvent(any(), any(), any()) }
    }

    @Test
    fun `pushEventPersons pushes only unsynced persons and writes returned persons`() = runTest {
        val syncedPerson = person(id = 1L, serverId = "srv-p1")
        val unsyncedPerson = person(id = 2L, serverId = null)
        val localDetails = eventDetails(persons = listOf(syncedPerson, unsyncedPerson))
        val remotePersons = listOf(unsyncedPerson.copy(serverId = "srv-p2"))
        coEvery { eventsLocalStore.getEventWithDetails(localDetails.event.id) } returns localDetails
        coEvery {
            eventsRemoteStore.addPersonsToEvent(
                eventServerId = localDetails.event.serverId!!,
                pinCode = localDetails.event.pinCode,
                localPersons = listOf(unsyncedPerson),
            )
        } returns IoResult.Success(remotePersons)
        coEvery {
            eventsLocalStore.insertPersonsWithCrossRefs(
                localDetails.event.id,
                remotePersons,
                true,
            )
        } returns remotePersons

        val result = task.pushEventPersons(localDetails.event.id)

        assertEquals(IoResult.Success(Unit), result)
        coVerify(exactly = 1) {
            eventsLocalStore.insertPersonsWithCrossRefs(localDetails.event.id, remotePersons, true)
        }
    }

    @Test
    fun `pushEventPersons propagates remote errors unchanged`() = runTest {
        val localDetails = eventDetails(persons = listOf(person(id = 1L, serverId = null)))
        coEvery { eventsLocalStore.getEventWithDetails(localDetails.event.id) } returns localDetails

        val errors = listOf(IoResult.Error.Retry, IoResult.Error.Failure)

        errors.forEach { error ->
            coEvery { eventsRemoteStore.addPersonsToEvent(any(), any(), any()) } returns error

            val result = task.pushEventPersons(localDetails.event.id)

            assertEquals(error, result)
        }
    }

    private fun event(serverId: String? = "srv-event"): Event {
        return Event(id = 1L, serverId = serverId, name = "Trip", pinCode = "1234", primaryCurrencyId = 1L)
    }

    private fun person(id: Long, serverId: String?): Person {
        return Person(id = id, serverId = serverId, name = "Person$id")
    }

    private fun currency(serverId: String? = "srv-eur"): Currency {
        return Currency(id = 1L, serverId = serverId, code = "EUR", name = "Euro", rate = BigDecimal.ONE)
    }

    private fun eventDetails(
        event: Event = event(),
        persons: List<Person> = listOf(person(id = 1L, serverId = null)),
    ): EventDetails {
        val currencies = listOf(currency())
        return EventDetails(
            event = event,
            currencies = currencies,
            persons = persons,
            primaryCurrency = currencies.first(),
        )
    }
}
