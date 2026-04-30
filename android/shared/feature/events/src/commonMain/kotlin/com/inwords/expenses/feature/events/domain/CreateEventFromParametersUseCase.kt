package com.inwords.expenses.feature.events.domain

import com.inwords.expenses.core.utils.ClientCreateIdGenerator
import com.inwords.expenses.feature.events.domain.model.Event
import com.inwords.expenses.feature.events.domain.model.EventDetails
import com.inwords.expenses.feature.events.domain.model.Person
import com.inwords.expenses.feature.events.domain.store.local.EventsLocalStore
import com.inwords.expenses.feature.settings.api.SettingsRepository

class CreateEventFromParametersUseCase internal constructor(
    eventsLocalStoreLazy: Lazy<EventsLocalStore>,
    settingsRepositoryLazy: Lazy<SettingsRepository>,
    clientCreateIdGeneratorLazy: Lazy<ClientCreateIdGenerator>,
) {
    private val eventsLocalStore by eventsLocalStoreLazy
    private val settingsRepository by settingsRepositoryLazy
    private val clientCreateIdGenerator by clientCreateIdGeneratorLazy

    suspend fun createEvent(
        name: String,
        owner: String,
        primaryCurrencyId: Long,
        otherPersons: List<String>,
    ): EventDetails {
        val personsToInsert = buildList {
            add(
                Person(
                    id = 0L,
                    serverId = null,
                    clientCreateId = clientCreateIdGenerator.generate(),
                    name = owner.trim(),
                )
            )
            addAll(
                otherPersons
                    .map { personName -> personName.trim() }
                    .filter { personName -> personName.isNotEmpty() }
                    .map { personName ->
                        Person(
                            id = 0L,
                            serverId = null,
                            clientCreateId = clientCreateIdGenerator.generate(),
                            name = personName,
                        )
                    },
            )
        }

        val eventToInsert = Event(
            id = 0L,
            serverId = null,
            clientCreateId = clientCreateIdGenerator.generate(),
            name = name.trim(),
            pinCode = SecureRandomPinCode.nextPinCode(length = 4),
            primaryCurrencyId = primaryCurrencyId,
        )

        val eventDetails = eventsLocalStore.deepInsert(
            eventToInsert = eventToInsert,
            personsToInsert = personsToInsert,
            inTransaction = true,
        )

        settingsRepository.setCurrentEventAndPerson(
            eventId = eventDetails.event.id,
            personId = eventDetails.persons.first().id,
        )

        return eventDetails
    }
}
