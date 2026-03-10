package com.inwords.expenses.feature.events.domain.task

import com.inwords.expenses.core.storage.utils.TransactionHelper
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
internal class EventPullPersonsTaskTest {

    private val mainDispatcher = StandardTestDispatcher()
    private val transactionHelper = mockk<TransactionHelper>()
    private val eventsLocalStore = mockk<EventsLocalStore>(relaxed = true)
    private val eventsRemoteStore = mockk<EventsRemoteStore>()

    private val task = EventPullPersonsTask(
        transactionHelperLazy = lazy { transactionHelper },
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
    fun `pullEventPersons returns failure when local event is missing`() = runTest {
        coEvery { eventsLocalStore.getEventWithDetails(1L) } returns null

        val result = task.pullEventPersons(1L)

        assertEquals(IoResult.Error.Failure, result)
    }

    @Test
    fun `pullEventPersons returns failure when local event has no server id`() = runTest {
        val localDetails = eventDetails(event = event(serverId = null))
        coEvery { eventsLocalStore.getEventWithDetails(localDetails.event.id) } returns localDetails

        val result = task.pullEventPersons(localDetails.event.id)

        assertEquals(IoResult.Error.Failure, result)
    }

    @Test
    fun `pullEventPersons maps terminal remote errors to failure`() = runTest {
        val localDetails = eventDetails()
        coEvery { eventsLocalStore.getEventWithDetails(localDetails.event.id) } returns localDetails

        val errors = listOf(
            EventsRemoteStore.EventNetworkError.NotFound,
            EventsRemoteStore.EventNetworkError.Gone,
            EventsRemoteStore.EventNetworkError.InvalidAccessCode,
        )

        errors.forEach { error ->
            coEvery {
                eventsRemoteStore.getEventByAccessCode(
                    localId = localDetails.event.id,
                    serverId = localDetails.event.serverId!!,
                    pinCode = localDetails.event.pinCode,
                    currencies = localDetails.currencies,
                    localPersons = localDetails.persons,
                )
            } returns EventsRemoteStore.GetEventResult.Error(error)

            val result = task.pullEventPersons(localDetails.event.id)

            assertEquals(IoResult.Error.Failure, result)
        }
    }

    @Test
    fun `pullEventPersons maps other error to retry`() = runTest {
        val localDetails = eventDetails()
        coEvery { eventsLocalStore.getEventWithDetails(localDetails.event.id) } returns localDetails
        coEvery {
            eventsRemoteStore.getEventByAccessCode(
                localId = localDetails.event.id,
                serverId = localDetails.event.serverId!!,
                pinCode = localDetails.event.pinCode,
                currencies = localDetails.currencies,
                localPersons = localDetails.persons,
            )
        } returns EventsRemoteStore.GetEventResult.Error(EventsRemoteStore.EventNetworkError.OtherError)

        val result = task.pullEventPersons(localDetails.event.id)

        assertEquals(IoResult.Error.Retry, result)
    }

    @Test
    fun `pullEventPersons inserts only missing remote persons`() = runTest {
        val localDetails = eventDetails(
            persons = listOf(
                person(id = 1L, serverId = "srv-p1"),
                person(id = 2L, serverId = null),
            )
        )
        val remotePersons = listOf(
            person(id = 10L, serverId = "srv-p1"),
            person(id = 11L, serverId = "srv-p2"),
        )
        coEvery { eventsLocalStore.getEventWithDetails(localDetails.event.id) } returns localDetails
        coEvery {
            eventsRemoteStore.getEventByAccessCode(
                localId = localDetails.event.id,
                serverId = localDetails.event.serverId!!,
                pinCode = localDetails.event.pinCode,
                currencies = localDetails.currencies,
                localPersons = localDetails.persons,
            )
        } returns EventsRemoteStore.GetEventResult.Event(localDetails.copy(persons = remotePersons))
        coEvery { transactionHelper.immediateWriteTransaction<List<Person>>(any()) } coAnswers {
            firstArg<suspend () -> List<Person>>().invoke()
        }
        coEvery { eventsLocalStore.insertPersonsWithCrossRefs(localDetails.event.id, listOf(remotePersons[1]), false) } returns listOf(remotePersons[1])

        val result = task.pullEventPersons(localDetails.event.id)

        assertEquals(IoResult.Success(Unit), result)
        coVerify(exactly = 1) { transactionHelper.immediateWriteTransaction<List<Person>>(any()) }
        coVerify(exactly = 1) {
            eventsLocalStore.insertPersonsWithCrossRefs(localDetails.event.id, listOf(remotePersons[1]), false)
        }
    }

    @Test
    fun `pullEventPersons is noop when all remote persons are already local`() = runTest {
        val localDetails = eventDetails(
            persons = listOf(
                person(id = 1L, serverId = "srv-p1"),
                person(id = 2L, serverId = "srv-p2"),
            )
        )
        val remoteDetails = localDetails.copy(
            persons = listOf(
                person(id = 10L, serverId = "srv-p1"),
                person(id = 11L, serverId = "srv-p2"),
            )
        )
        coEvery { eventsLocalStore.getEventWithDetails(localDetails.event.id) } returns localDetails
        coEvery {
            eventsRemoteStore.getEventByAccessCode(
                localId = localDetails.event.id,
                serverId = localDetails.event.serverId!!,
                pinCode = localDetails.event.pinCode,
                currencies = localDetails.currencies,
                localPersons = localDetails.persons,
            )
        } returns EventsRemoteStore.GetEventResult.Event(remoteDetails)
        coEvery { transactionHelper.immediateWriteTransaction<List<Person>>(any()) } coAnswers {
            firstArg<suspend () -> List<Person>>().invoke()
        }

        val result = task.pullEventPersons(localDetails.event.id)

        assertEquals(IoResult.Success(Unit), result)
        coVerify(exactly = 0) { eventsLocalStore.insertPersonsWithCrossRefs(any(), any(), any()) }
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
        persons: List<Person> = listOf(person(id = 1L, serverId = "srv-p1")),
        currencies: List<Currency> = listOf(currency()),
    ): EventDetails {
        return EventDetails(
            event = event,
            currencies = currencies,
            persons = persons,
            primaryCurrency = currencies.first(),
        )
    }
}
