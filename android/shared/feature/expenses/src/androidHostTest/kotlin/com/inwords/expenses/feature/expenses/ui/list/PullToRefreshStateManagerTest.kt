package com.inwords.expenses.feature.expenses.ui.list

import app.cash.turbine.test
import com.inwords.expenses.feature.events.domain.EventsSyncStateHolder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
internal class PullToRefreshStateManagerTest {

    @Test
    fun onUserTriggeredRefresh_marksEventRefreshingUntilSyncCompletesAndMinimumDisplayPasses() = runTest {
        val eventId = 42L
        val syncStateFlow = MutableStateFlow(false)
        val eventsSyncStateHolder = mockk<EventsSyncStateHolder> {
            every { getStateFor(eventId) } returns syncStateFlow
        }
        val stateManager = PullToRefreshStateManager(eventsSyncStateHolder)

        stateManager.isEventRefreshing(eventId).test {
            assertFalse(awaitItem())

            stateManager.onUserTriggeredRefresh(backgroundScope, eventId)
            assertTrue(awaitItem())

            syncStateFlow.value = true
            runCurrent()

            syncStateFlow.value = false
            advanceTimeBy(1900.milliseconds)
            runCurrent()
            expectNoEvents()

            advanceTimeBy(200.milliseconds)
            advanceUntilIdle()
            assertFalse(awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onUserTriggeredRefresh_forNewEventCancelsPreviousRefreshingEvent() = runTest {
        val firstEventFlow = MutableStateFlow(false)
        val secondEventFlow = MutableStateFlow(false)
        val eventsSyncStateHolder = mockk<EventsSyncStateHolder> {
            every { getStateFor(1L) } returns firstEventFlow
            every { getStateFor(2L) } returns secondEventFlow
        }
        val stateManager = PullToRefreshStateManager(eventsSyncStateHolder)

        stateManager.isEventRefreshing(1L).test {
            assertFalse(awaitItem())

            stateManager.onUserTriggeredRefresh(backgroundScope, 1L)
            assertTrue(awaitItem())

            stateManager.onUserTriggeredRefresh(backgroundScope, 2L)
            assertFalse(awaitItem())

            cancelAndIgnoreRemainingEvents()
        }

        stateManager.isEventRefreshing(2L).test {
            assertTrue(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
