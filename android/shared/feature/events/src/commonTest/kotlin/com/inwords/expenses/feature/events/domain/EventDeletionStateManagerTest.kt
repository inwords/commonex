package com.inwords.expenses.feature.events.domain

import com.inwords.expenses.feature.events.api.EventDeletionStateManager.EventDeletionState
import kotlin.test.Test
import kotlin.test.assertEquals

internal class EventDeletionStateManagerTest {

    @Test
    fun deletionChoicesAndFailuresRemainScopedToTheirEvent() {
        val manager = EventDeletionStateManagerImpl()
        manager.setEventDeletionState(1L, EventDeletionState.PendingDeletionChoice)
        manager.setEventDeletionState(2L, EventDeletionState.Loading)
        manager.setEventDeletionState(1L, EventDeletionState.RemoteDeletionFailed)

        assertEquals(
            mapOf(1L to EventDeletionState.RemoteDeletionFailed, 2L to EventDeletionState.Loading),
            manager.eventsDeletionState.value,
        )

        manager.clearEventDeletionState(1L)
        manager.clearEventDeletionState(99L)

        assertEquals(mapOf(2L to EventDeletionState.Loading), manager.eventsDeletionState.value)
    }
}
