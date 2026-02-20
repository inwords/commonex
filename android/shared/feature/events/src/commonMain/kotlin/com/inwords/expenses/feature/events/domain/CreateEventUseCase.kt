package com.inwords.expenses.feature.events.domain

import com.inwords.expenses.feature.events.domain.model.Event
import com.inwords.expenses.feature.events.domain.model.EventDetails
import com.inwords.expenses.feature.events.domain.model.Person
import com.inwords.expenses.feature.events.domain.store.local.EventsLocalStore
import com.inwords.expenses.feature.settings.api.SettingsRepository

class CreateEventUseCase internal constructor(
    eventsLocalStoreLazy: Lazy<EventsLocalStore>,
    settingsRepositoryLazy: Lazy<SettingsRepository>,
    eventCreationStateHolderLazy: Lazy<EventCreationStateHolder>,
) {
    private val eventsLocalStore by eventsLocalStoreLazy
    private val settingsRepository by settingsRepositoryLazy
    private val stateHolder by eventCreationStateHolderLazy

    suspend fun createEvent(): EventDetails {
        val personsToInsert = buildList {
            add(stateHolder.getDraftOwner())
            addAll(stateHolder.getDraftOtherPersons())
        }.map { personName ->
            Person(0L, null, personName)
        }

        val eventToInsert = Event(
            id = 0L,
            serverId = null,
            name = stateHolder.getDraftEventName(),
            pinCode = SecureRandomPinCode.nextPinCode(length = 4),
            primaryCurrencyId = stateHolder.getDraftPrimaryCurrencyId()
        )

        val eventDetails = eventsLocalStore.deepInsert(
            eventToInsert = eventToInsert,
            personsToInsert = personsToInsert,
            inTransaction = true
        )

        settingsRepository.setCurrentEventAndPerson(
            eventId = eventDetails.event.id,
            personId = eventDetails.persons.first().id
        )

        stateHolder.clear()

        return eventDetails
    }
}
