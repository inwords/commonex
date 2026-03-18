package com.inwords.expenses.feature.events.domain

import com.inwords.expenses.feature.events.domain.model.EventDetails

class CreateEventUseCase internal constructor(
    createEventFromParametersUseCaseLazy: Lazy<CreateEventFromParametersUseCase>,
    eventCreationStateHolderLazy: Lazy<EventCreationStateHolder>,
) {
    private val createEventFromParametersUseCase by createEventFromParametersUseCaseLazy
    private val stateHolder by eventCreationStateHolderLazy

    suspend fun createEvent(): EventDetails {
        val eventDetails = createEventFromParametersUseCase.createEvent(
            name = stateHolder.getDraftEventName(),
            owner = stateHolder.getDraftOwner(),
            primaryCurrencyId = stateHolder.getDraftPrimaryCurrencyId(),
            otherPersons = stateHolder.getDraftOtherPersons(),
        )

        stateHolder.clear()

        return eventDetails
    }
}
