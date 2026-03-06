package com.inwords.expenses.feature.sync.data

import kotlinx.coroutines.flow.Flow

abstract class EventsSyncManagerObserverDelegate {

    internal abstract fun pushAllEventInfo(eventId: Long)

    internal abstract fun getSyncState(): Flow<Set<Long>>
}
