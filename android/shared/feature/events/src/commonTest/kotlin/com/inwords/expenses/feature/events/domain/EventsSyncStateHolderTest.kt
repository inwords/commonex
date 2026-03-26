package com.inwords.expenses.feature.events.domain

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
internal class EventsSyncStateHolderTest {

    @Test
    fun getStateFor_emitsFalseThenTracksMembershipChanges() = runTest {
        val stateHolder = EventsSyncStateHolder()
        val emissions = mutableListOf<Boolean>()

        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            stateHolder.getStateFor(eventId = 7L).collect { emissions += it }
        }

        advanceUntilIdle()
        stateHolder.setSyncState(setOf(7L))
        advanceUntilIdle()
        stateHolder.setSyncState(setOf(9L))
        advanceUntilIdle()

        assertEquals(listOf(false, true, false), emissions)
        job.cancel()
    }

    @Test
    fun getStateFor_doesNotEmitWhenMembershipForEventDoesNotChange() = runTest {
        val stateHolder = EventsSyncStateHolder()
        val emissions = mutableListOf<Boolean>()

        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            stateHolder.getStateFor(eventId = 7L).collect { emissions += it }
        }

        advanceUntilIdle()
        stateHolder.setSyncState(setOf(9L))
        advanceUntilIdle()
        stateHolder.setSyncState(setOf(7L, 9L))
        advanceUntilIdle()
        stateHolder.setSyncState(setOf(7L))
        advanceUntilIdle()

        assertEquals(listOf(false, true), emissions)
        job.cancel()
    }
}
