package com.inwords.expenses.feature.sync.data

import kotlinx.coroutines.flow.Flow

expect class EventsSyncManager : EventsSyncManagerObserverDelegate {

    override fun pushAllEventInfo(eventId: Long)

    suspend fun cancelEventSync(eventId: Long)

    override fun getSyncState(): Flow<Set<Long>>

}

internal expect class EventsSyncManagerFactory {

    fun create(): EventsSyncManager
}
