package com.inwords.expenses.feature.settings.data

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.okio.OkioSerializer
import com.inwords.expenses.core.storage.utils.DataStoreSingleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import okio.BufferedSink
import okio.BufferedSource
import okio.IOException

internal class SettingsLocalDataSource(settingsDataStoreLazy: Lazy<DataStore<Settings>>) {

    private val settingsDataStore: DataStore<Settings> by settingsDataStoreLazy

    suspend fun setCurrentEventId(eventId: Long) {
        settingsDataStore.updateData { currentSettings ->
            currentSettings.copy(current_event_id = eventId)
        }
    }

    fun getCurrentEventId(): Flow<Long?> {
        return settingsDataStore.data
            .map { settings -> settings.current_event_id.takeIf { it != -1L } }
            .distinctUntilChanged()
    }

    suspend fun setCurrentPersonId(userId: Long) {
        settingsDataStore.updateData { currentSettings ->
            currentSettings.copy(current_person_id = userId)
        }
    }

    fun getCurrentPersonId(): Flow<Long?> {
        return settingsDataStore.data
            .map { settings -> settings.current_person_id.takeIf { it != -1L } }
            .distinctUntilChanged()
    }

    suspend fun setCurrentEventAndPerson(eventId: Long, personId: Long) {
        settingsDataStore.updateData { currentSettings ->
            currentSettings.copy(
                current_event_id = eventId,
                current_person_id = personId
            )
        }
    }

}

internal const val settingsDsFileName = "expenses_settings.pb"

internal val settingsDataStore = DataStoreSingleton(SettingsSerializer())

private class SettingsSerializer : OkioSerializer<Settings> {

    override val defaultValue: Settings = Settings(
        current_event_id = -1,
        current_person_id = -1
    )

    override suspend fun readFrom(source: BufferedSource): Settings {
        try {
            return Settings.ADAPTER.decode(source)
        } catch (exception: IOException) {
            throw CorruptionException("Cannot read proto.", exception)
        }
    }

    override suspend fun writeTo(t: Settings, sink: BufferedSink) = t.encode(sink)
}
