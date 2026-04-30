package com.inwords.expenses.feature.settings.data

import com.inwords.expenses.feature.settings.api.SettingsRepository
import kotlinx.coroutines.flow.Flow

internal class SettingsRepositoryImpl(
    private val settingsLocalDataSource: SettingsLocalDataSource
) : SettingsRepository {

    /**
     * Sets both the current event and the current person in a single atomic operation.
     *
     * This delegates to [SettingsLocalDataSource.setCurrentEventAndPerson] so that the
     * persisted values are updated together, avoiding transient states where the event
     * and person IDs no longer form a consistent pair.
     *
     * Use this method when switching the active event context along with the associated
     * person in one step (for example, when opening another event for a specific user).
     * Use [setCurrentEventId] and [setCurrentPersonId] separately only when you
     * intentionally want to change one of the values without updating the other.
     */
    override suspend fun setCurrentEventAndPerson(eventId: Long, personId: Long) {
        settingsLocalDataSource.setCurrentEventAndPerson(eventId, personId)
    }

    override suspend fun setCurrentEventId(eventId: Long) {
        settingsLocalDataSource.setCurrentEventId(eventId)
    }

    override fun getCurrentEventId(): Flow<Long?> {
        return settingsLocalDataSource.getCurrentEventId()
    }

    override suspend fun setCurrentPersonId(userId: Long) {
        settingsLocalDataSource.setCurrentPersonId(userId)
    }

    override fun getCurrentPersonId(): Flow<Long?> {
        return settingsLocalDataSource.getCurrentPersonId()
    }

    override suspend fun clearCurrentEventAndPerson() {
        settingsLocalDataSource.setCurrentEventAndPerson(eventId = -1, personId = -1)
    }

}
