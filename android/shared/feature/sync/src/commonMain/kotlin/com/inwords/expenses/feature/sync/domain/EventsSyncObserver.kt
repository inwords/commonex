package com.inwords.expenses.feature.sync.domain

import com.inwords.expenses.core.utils.flatMapLatestNoBuffer
import com.inwords.expenses.feature.events.domain.EventsSyncStateHolder
import com.inwords.expenses.feature.events.domain.GetCurrentEventStateUseCase
import com.inwords.expenses.feature.events.domain.model.Person
import com.inwords.expenses.feature.expenses.domain.ExpensesInteractor
import com.inwords.expenses.feature.expenses.domain.model.Expense
import com.inwords.expenses.feature.sync.data.EventsSyncManagerObserverDelegate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

class EventsSyncObserver internal constructor(
    getCurrentEventStateUseCaseLazy: Lazy<GetCurrentEventStateUseCase>,
    expensesInteractorLazy: Lazy<ExpensesInteractor>,
    eventsSyncStateHolderLazy: Lazy<EventsSyncStateHolder>,
    eventsSyncManagerLazy: Lazy<EventsSyncManagerObserverDelegate>
) {
    private val getCurrentEventStateUseCase by getCurrentEventStateUseCaseLazy
    private val expensesInteractor by expensesInteractorLazy
    private val eventsSyncStateHolder by eventsSyncStateHolderLazy
    private val eventsSyncManager by eventsSyncManagerLazy

    /**
     * Should be called only once per process lifecycle
     */
    fun observeNewEventsIn(scope: CoroutineScope) {
        scope.launch {
            merge(
                getCurrentEventStateUseCase.currentEvent
                    .map { currentEvent ->
                        currentEvent?.event?.id to currentEvent?.persons?.personsToIdsSet()
                    }
                    .distinctUntilChanged()
                    .flatMapLatestNoBuffer { (currentEventId, personIdsSet) ->
                        currentEventId ?: return@flatMapLatestNoBuffer emptyFlow()
                        expensesInteractor.getExpensesFlow(currentEventId).map { Triple(currentEventId, personIdsSet, it.expensesToIdsSet()) }
                    }
                    .distinctUntilChanged()
                    .map { it.first },
                expensesInteractor.refreshExpenses
                    .map { it.id }
            )
                .conflate()
                .collect { eventId ->
                    eventsSyncManager.pushAllEventInfo(eventId)
                    delay(3000) // do not launch sync more often than every 3 seconds
                }
        }

        scope.launch {
            eventsSyncManager.getSyncState().collect { syncingEvents ->
                eventsSyncStateHolder.setSyncState(syncingEvents)
            }
        }
    }

    private fun List<Person>.personsToIdsSet(): Set<Long> {
        return mapTo(HashSet(size)) { it.id }
    }

    private fun List<Expense>.expensesToIdsSet(): Set<Long> {
        return mapTo(HashSet(size)) { it.expenseId }
    }

}
