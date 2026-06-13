package com.inwords.expenses.feature.expenses.domain.tasks

import com.inwords.expenses.core.utils.IO
import com.inwords.expenses.core.utils.IoResult
import com.inwords.expenses.feature.events.domain.model.Event
import com.inwords.expenses.feature.events.domain.store.local.EventsLocalStore
import com.inwords.expenses.feature.expenses.domain.model.Expense
import com.inwords.expenses.feature.expenses.domain.store.ExpensePullItem
import com.inwords.expenses.feature.expenses.domain.store.ExpensesLocalStore
import com.inwords.expenses.feature.expenses.domain.store.ExpensesRemoteStore
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class EventExpensesPullTask internal constructor(
    eventsLocalStoreLazy: Lazy<EventsLocalStore>,
    expensesLocalStoreLazy: Lazy<ExpensesLocalStore>,
    expensesRemoteStoreLazy: Lazy<ExpensesRemoteStore>,
) {

    private val eventsLocalStore by eventsLocalStoreLazy
    private val expensesLocalStore by expensesLocalStoreLazy
    private val expensesRemoteStore by expensesRemoteStoreLazy

    suspend fun pullEventExpenses(eventId: Long): IoResult<*> = withContext(IO) {
        val localEvent = eventsLocalStore.getEventWithDetails(eventId)
            ?.takeIf { details ->
                details.event.serverId != null &&
                    details.persons.all { it.serverId != null } &&
                    details.currencies.all { it.serverId != null }
            } ?: return@withContext IoResult.Error.Failure

        val remoteResult = expensesRemoteStore.getExpenses(
            event = localEvent.event,
            currencies = localEvent.currencies,
            persons = localEvent.persons
        )

        val remoteExpenseItems = when (remoteResult) {
            is IoResult.Success -> remoteResult.data
            is IoResult.Error -> return@withContext remoteResult
        }

        val localExpenses = expensesLocalStore.getExpenses(eventId)

        updateLocalExpenses(localEvent.event, localExpenses, remoteExpenseItems)

        IoResult.Success(Unit)
    }

    private suspend fun updateLocalExpenses(
        event: Event,
        localExpenses: List<Expense>,
        remoteExpenseItems: List<ExpensePullItem>
    ) {
        val localExpensesMap = localExpenses.mapNotNullTo(HashSet()) { it.serverId }

        val expensesToInsert = remoteExpenseItems.filter { remoteExpenseItem ->
            remoteExpenseItem.expense.serverId !in localExpensesMap
        }

        if (expensesToInsert.any { it.revertsExpenseServerId != null || it.replacesExpenseServerId != null }) {
            withContext(NonCancellable) {
                expensesLocalStore.reconcileCorrectionConflicts(event, expensesToInsert)
            }
        } else if (expensesToInsert.isNotEmpty()) {
            withContext(NonCancellable) {
                expensesLocalStore.upsert(event, expensesToInsert.map { it.expense })
            }
        }
    }

}
