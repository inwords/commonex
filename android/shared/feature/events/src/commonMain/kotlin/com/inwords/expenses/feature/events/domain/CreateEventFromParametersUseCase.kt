package com.inwords.expenses.feature.events.domain

import com.inwords.expenses.feature.events.domain.model.Currency
import com.inwords.expenses.feature.events.domain.model.Event
import com.inwords.expenses.feature.events.domain.model.EventDetails
import com.inwords.expenses.feature.events.domain.model.Person
import com.inwords.expenses.feature.events.domain.store.local.EventsLocalStore
import com.inwords.expenses.feature.settings.api.SettingsRepository

class CreateEventFromParametersUseCase internal constructor(
    eventsLocalStoreLazy: Lazy<EventsLocalStore>,
    settingsRepositoryLazy: Lazy<SettingsRepository>,
) {
    private val eventsLocalStore by eventsLocalStoreLazy
    private val settingsRepository by settingsRepositoryLazy

    suspend fun createEvent(
        name: String,
        owner: String,
        primaryCurrency: Currency,
        otherPersons: List<String> = emptyList(),
    ): EventDetails {
        return createEvent(
            name = name,
            owner = owner,
            primaryCurrencyId = primaryCurrency.id,
            otherPersons = otherPersons,
        )
    }

    suspend fun createEvent(
        name: String,
        owner: String,
        primaryCurrencyId: Long,
        otherPersons: List<String> = emptyList(),
    ): EventDetails {
        val personsToInsert = buildList {
            add(Person(id = 0L, serverId = null, name = owner.trim()))
            addAll(
                otherPersons
                    .map { personName -> personName.trim() }
                    .filter { personName -> personName.isNotEmpty() }
                    .map { personName -> Person(id = 0L, serverId = null, name = personName) },
            )
        }

        val eventToInsert = Event(
            id = 0L,
            serverId = null,
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
