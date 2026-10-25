package com.inwords.expenses.feature.settings.data

import androidx.datastore.core.DataStore
import app.cash.turbine.test
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal class SettingsRepositoryTest {

    private val dataStore = InMemorySettingsStore()
    private val repository = SettingsRepositoryImpl(SettingsLocalDataSource(lazyOf(dataStore)))

    @Test
    fun `unset selection is exposed as absent rather than persisted sentinels`() = runTest {
        assertNull(repository.getCurrentEventId().first())
        assertNull(repository.getCurrentPersonId().first())
        assertEquals(emptyMap(), repository.getBannerDismissals().first())
        assertEquals(emptyList(), dataStore.writes)
    }

    @Test
    fun `switching and clearing selection persist consistent pairs atomically`() = runTest {
        repository.setCurrentEventAndPerson(eventId = 10L, personId = 20L)
        assertEquals(10L, repository.getCurrentEventId().first())
        assertEquals(20L, repository.getCurrentPersonId().first())

        repository.setCurrentEventAndPerson(eventId = 30L, personId = 40L)
        repository.clearCurrentEventAndPerson()

        assertEquals(
            listOf(
                Settings(current_event_id = 10L, current_person_id = 20L),
                Settings(current_event_id = 30L, current_person_id = 40L),
                Settings(current_event_id = -1L, current_person_id = -1L),
            ),
            dataStore.writes,
        )
        assertNull(repository.getCurrentEventId().first())
        assertNull(repository.getCurrentPersonId().first())
    }

    @Test
    fun `person-only updates preserve the selected event without duplicate event emissions`() = runTest {
        repository.setCurrentEventAndPerson(eventId = 10L, personId = 20L)

        repository.getCurrentEventId().test {
            assertEquals(10L, awaitItem())
            repository.setCurrentPersonId(21L)
            assertEquals(21L, repository.getCurrentPersonId().first())
            expectNoEvents()

            repository.setCurrentEventId(11L)
            assertEquals(11L, awaitItem())
            assertEquals(21L, repository.getCurrentPersonId().first())
            repository.setCurrentEventId(11L)
            expectNoEvents()
        }
    }

    @Test
    fun `event-only updates preserve the selected person without duplicate person emissions`() = runTest {
        repository.setCurrentEventAndPerson(eventId = 10L, personId = 20L)

        repository.getCurrentPersonId().test {
            assertEquals(20L, awaitItem())
            repository.setCurrentEventId(11L)
            assertEquals(11L, repository.getCurrentEventId().first())
            expectNoEvents()

            repository.setCurrentPersonId(21L)
            assertEquals(21L, awaitItem())
            assertEquals(11L, repository.getCurrentEventId().first())
            repository.setCurrentPersonId(21L)
            expectNoEvents()
        }
    }

    @Test
    fun `banner dismissals preserve selection and other messages across repository recreation`() = runTest {
        repository.setCurrentEventAndPerson(eventId = 10L, personId = 20L)
        repository.setBannerDismissal("warning:Maintenance", Long.MAX_VALUE)
        repository.setBannerDismissal("update:New version", 100L)
        repository.setBannerDismissal("update:New version", 200L)

        val restoredSettings = Settings.ADAPTER.decode(dataStore.writes.last().encode())
        val restoredStore = InMemorySettingsStore(restoredSettings)
        val restoredRepository = SettingsRepositoryImpl(SettingsLocalDataSource(lazyOf(restoredStore)))
        assertEquals(10L, restoredRepository.getCurrentEventId().first())
        assertEquals(20L, restoredRepository.getCurrentPersonId().first())
        assertEquals(
            mapOf("warning:Maintenance" to Long.MAX_VALUE, "update:New version" to 200L),
            restoredRepository.getBannerDismissals().first(),
        )

        restoredRepository.clearCurrentEventAndPerson()
        assertEquals(restoredSettings.banner_dismissals, restoredRepository.getBannerDismissals().first())
    }

    @Test
    fun `legacy settings decode without banner dismissals`() {
        val restored = Settings.ADAPTER.decode(byteArrayOf(0x08, 0x0a, 0x10, 0x14))

        assertEquals(10L, restored.current_event_id)
        assertEquals(20L, restored.current_person_id)
        assertEquals(emptyMap(), restored.banner_dismissals)
    }

    @Test
    fun `banner dismissal flow ignores unrelated and duplicate updates`() = runTest {
        repository.getBannerDismissals().test {
            assertEquals(emptyMap(), awaitItem())
            repository.setCurrentEventAndPerson(eventId = 10L, personId = 20L)
            expectNoEvents()

            repository.setBannerDismissal("warning:Maintenance", Long.MAX_VALUE)
            assertEquals(mapOf("warning:Maintenance" to Long.MAX_VALUE), awaitItem())
            repository.setBannerDismissal("warning:Maintenance", Long.MAX_VALUE)
            expectNoEvents()
        }
    }

    private class InMemorySettingsStore(
        initialSettings: Settings = Settings(current_event_id = -1L, current_person_id = -1L),
    ) : DataStore<Settings> {
        private val mutex = Mutex()
        private val state = MutableStateFlow(initialSettings)

        val writes = mutableListOf<Settings>()

        override val data: Flow<Settings> = state

        override suspend fun updateData(transform: suspend (Settings) -> Settings): Settings = mutex.withLock {
            transform(state.value).also { updated ->
                writes.add(updated)
                state.value = updated
            }
        }
    }
}
