package com.inwords.expenses.feature.expenses.domain

import com.inwords.expenses.feature.events.domain.model.Event
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

internal class RequestExpensesRefreshUseCaseTest {

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `requestRefresh emits requested event`() = runTest {
        val event = Event(10L, null, "Trip", "1234", 1L)
        val holder = ExpensesRefreshRequestsHolder()
        val useCase = RequestExpensesRefreshUseCase(
            expensesRefreshRequestsHolderLazy = lazyOf(holder),
        )

        val emittedEvent = async { holder.refreshExpensesRequests.first() }
        runCurrent()

        useCase.requestRefresh(event)

        assertEquals(event, emittedEvent.await())
    }
}
