package com.inwords.expenses.feature.expenses.domain

import app.cash.turbine.test
import com.inwords.expenses.feature.events.domain.model.Event
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
internal class ExpensesRefreshRequestsHolderTest {

    @Test
    fun `enqueueAsyncSync emits requested event`() = runTest {
        val holder = ExpensesRefreshRequestsHolder()
        val event = Event(10L, null, "Trip", "1234", 1L)

        holder.refreshExpensesRequests.test {
            holder.enqueueAsyncSync(event)

            assertEquals(event, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `enqueueAsyncSync preserves request order`() = runTest {
        val holder = ExpensesRefreshRequestsHolder()
        val firstEvent = Event(10L, null, "Trip", "1234", 1L)
        val secondEvent = Event(11L, "server-11", "Party", "5678", 1L)

        holder.refreshExpensesRequests.test {
            holder.enqueueAsyncSync(firstEvent)
            holder.enqueueAsyncSync(secondEvent)

            assertEquals(firstEvent, awaitItem())
            assertEquals(secondEvent, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
