package com.inwords.expenses.feature.events.domain

import com.inwords.expenses.core.testutils.TestClientCreateIdGenerator
import com.inwords.expenses.feature.events.domain.model.Person
import com.inwords.expenses.feature.events.domain.store.local.EventsLocalStore
import com.inwords.expenses.feature.settings.api.SettingsRepository
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

internal class AddParticipantsToCurrentEventUseCaseTest {

    private val eventsLocalStore = mockk<EventsLocalStore>(relaxed = true)
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val clientCreateIdGenerator = TestClientCreateIdGenerator("alice-client-id", "bob-client-id")

    private val useCase = AddParticipantsToCurrentEventUseCase(
        eventsLocalStoreLazy = lazy { eventsLocalStore },
        settingsRepositoryLazy = lazy { settingsRepository },
        clientCreateIdGeneratorLazy = lazy { clientCreateIdGenerator },
    )

    @Test
    fun addParticipants_whenCurrentEventIsMissing_doesNotInsertParticipants() = runTest {
        every { settingsRepository.getCurrentEventId() } returns flowOf(null)

        useCase.addParticipants(listOf("Alice", "Bob"))

        coVerify(exactly = 0) { eventsLocalStore.insertPersonsWithCrossRefs(any(), any(), any()) }
    }

    @Test
    fun addParticipants_trimsBlankNamesBeforeInsertingForCurrentEvent() = runTest {
        every { settingsRepository.getCurrentEventId() } returns flowOf(42L)

        useCase.addParticipants(listOf(" Alice ", "", "  ", "Bob"))

        coVerify(exactly = 1) {
            eventsLocalStore.insertPersonsWithCrossRefs(
                eventId = 42L,
                persons = listOf(
                    Person(id = 0L, serverId = null, clientCreateId = "alice-client-id", name = "Alice"),
                    Person(id = 0L, serverId = null, clientCreateId = "bob-client-id", name = "Bob"),
                ),
                inTransaction = true,
            )
        }
    }
}
