package com.inwords.expenses.feature.events.domain.task

import com.inwords.expenses.core.storage.utils.TransactionHelper
import com.inwords.expenses.core.utils.IoResult
import com.inwords.expenses.feature.events.domain.model.Currency
import com.inwords.expenses.feature.events.domain.model.Event
import com.inwords.expenses.feature.events.domain.model.EventDetails
import com.inwords.expenses.feature.events.domain.model.Person
import com.inwords.expenses.feature.events.domain.store.local.EventsLocalStore
import com.inwords.expenses.feature.events.domain.store.local.PersonsLocalStore
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
internal class EventPushTaskTest {

    private val mainDispatcher = StandardTestDispatcher()
    private val transactionHelper = mockk<TransactionHelper>()
    private val eventsLocalStore = mockk<EventsLocalStore>(relaxed = true)
    private val eventsRemoteStore = mockk<EventsRemoteStore>()
    private val personsLocalStore = mockk<PersonsLocalStore>(relaxed = true)

    private val task = EventPushTask(
        transactionHelperLazy = lazy { transactionHelper },
        eventsLocalStoreLazy = lazy { eventsLocalStore },
        eventsRemoteStoreLazy = lazy { eventsRemoteStore },
        personsLocalStoreLazy = lazy { personsLocalStore },
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
    fun `pushEvent writes event and person server ids in one transaction`() = runTest {
        val localDetails = eventDetails(
            event = event(serverId = null),
            persons = listOf(person(id = 1L), person(id = 2L)),
        )
        val remoteDetails = localDetails.copy(
            event = localDetails.event.copy(serverId = "srv-event"),
            persons = listOf(
                localDetails.persons[0].copy(serverId = "srv-p1"),
                localDetails.persons[1].copy(serverId = "srv-p2"),
            ),
        )
        coEvery { eventsLocalStore.getEventWithDetails(localDetails.event.id) } returns localDetails
        coEvery {
            eventsRemoteStore.createEvent(
                event = localDetails.event,
                currencies = localDetails.currencies,
                primaryCurrencyServerId = localDetails.primaryCurrency.serverId!!,
                localPersons = localDetails.persons,
            )
        } returns IoResult.Success(remoteDetails)
        coEvery { transactionHelper.immediateWriteTransaction<Boolean>(any()) } coAnswers {
            firstArg<suspend () -> Boolean>().invoke()
        }
        coEvery { personsLocalStore.insertWithoutCrossRefs(any()) } answers { firstArg() }
        coEvery { eventsLocalStore.update(localDetails.event.id, "srv-event") } returns true

        val result = task.pushEvent(localDetails.event.id)

        assertEquals(IoResult.Success(Unit), result)
        coVerify(exactly = 1) { transactionHelper.immediateWriteTransaction<Boolean>(any()) }
        coVerify(exactly = 1) {
            personsLocalStore.insertWithoutCrossRefs(
                listOf(
                    localDetails.persons[0].copy(serverId = "srv-p1"),
                    localDetails.persons[1].copy(serverId = "srv-p2"),
                )
            )
        }
        coVerify(exactly = 1) { eventsLocalStore.update(localDetails.event.id, "srv-event") }
    }

    @Test
    fun `pushEvent returns failure when local event is missing`() = runTest {
        coEvery { eventsLocalStore.getEventWithDetails(1L) } returns null

        val result = task.pushEvent(1L)

        assertEquals(IoResult.Error.Failure, result)
        coVerify(exactly = 0) { eventsRemoteStore.createEvent(any(), any(), any(), any()) }
    }

    @Test
    fun `pushEvent returns success without remote call when event is already synced`() = runTest {
        val localDetails = eventDetails(event = event(serverId = "srv-event"))
        coEvery { eventsLocalStore.getEventWithDetails(localDetails.event.id) } returns localDetails

        val result = task.pushEvent(localDetails.event.id)

        assertEquals(IoResult.Success(Unit), result)
        coVerify(exactly = 0) { eventsRemoteStore.createEvent(any(), any(), any(), any()) }
    }

    @Test
    fun `pushEvent returns failure when primary currency server id is missing`() = runTest {
        val localDetails = eventDetails(primaryCurrency = currency(serverId = null))
        coEvery { eventsLocalStore.getEventWithDetails(localDetails.event.id) } returns localDetails

        val result = task.pushEvent(localDetails.event.id)

        assertEquals(IoResult.Error.Failure, result)
        coVerify(exactly = 0) { eventsRemoteStore.createEvent(any(), any(), any(), any()) }
    }

    @Test
    fun `pushEvent propagates retry without writes`() = runTest {
        val localDetails = eventDetails()
        coEvery { eventsLocalStore.getEventWithDetails(localDetails.event.id) } returns localDetails
        coEvery { eventsRemoteStore.createEvent(any(), any(), any(), any()) } returns IoResult.Error.Retry

        val result = task.pushEvent(localDetails.event.id)

        assertEquals(IoResult.Error.Retry, result)
        coVerify(exactly = 0) { personsLocalStore.insertWithoutCrossRefs(any()) }
        coVerify(exactly = 0) { eventsLocalStore.update(any(), any()) }
    }

    @Test
    fun `pushEvent propagates failure without writes`() = runTest {
        val localDetails = eventDetails()
        coEvery { eventsLocalStore.getEventWithDetails(localDetails.event.id) } returns localDetails
        coEvery { eventsRemoteStore.createEvent(any(), any(), any(), any()) } returns IoResult.Error.Failure

        val result = task.pushEvent(localDetails.event.id)

        assertEquals(IoResult.Error.Failure, result)
        coVerify(exactly = 0) { personsLocalStore.insertWithoutCrossRefs(any()) }
        coVerify(exactly = 0) { eventsLocalStore.update(any(), any()) }
    }

    private fun event(serverId: String? = null): Event {
        return Event(id = 1L, serverId = serverId, name = "Trip", pinCode = "1234", primaryCurrencyId = 1L)
    }

    private fun person(id: Long): Person {
        return Person(id = id, serverId = null, name = "Person$id")
    }

    private fun currency(serverId: String? = "srv-eur"): Currency {
        return Currency(id = 1L, serverId = serverId, code = "EUR", name = "Euro", rate = BigDecimal.ONE)
    }

    private fun eventDetails(
        event: Event = event(serverId = null),
        primaryCurrency: Currency = currency(),
        persons: List<Person> = listOf(person(id = 1L)),
    ): EventDetails {
        return EventDetails(
            event = event,
            currencies = listOf(primaryCurrency),
            persons = persons,
            primaryCurrency = primaryCurrency,
        )
    }
}
