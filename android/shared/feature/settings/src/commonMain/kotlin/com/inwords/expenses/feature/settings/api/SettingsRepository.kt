package com.inwords.expenses.feature.settings.api

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {

    suspend fun setCurrentEventAndPerson(eventId: Long, personId: Long)

    suspend fun setCurrentEventId(eventId: Long)
    fun getCurrentEventId(): Flow<Long?>

    suspend fun setCurrentPersonId(userId: Long)
    fun getCurrentPersonId(): Flow<Long?>

    suspend fun clearCurrentEventAndPerson()

    fun getBannerDismissals(): Flow<Map<String, Long>>
    suspend fun setBannerDismissal(key: String, untilEpochMillis: Long)
}
