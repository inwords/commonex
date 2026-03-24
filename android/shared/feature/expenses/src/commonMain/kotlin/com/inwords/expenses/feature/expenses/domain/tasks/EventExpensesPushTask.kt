package com.inwords.expenses.feature.expenses.domain.tasks

import com.inwords.expenses.core.observability.Observability
import com.inwords.expenses.core.observability.captureMessageIfNull
import com.inwords.expenses.core.storage.utils.TransactionHelper
import com.inwords.expenses.core.utils.IO
import com.inwords.expenses.core.utils.IoResult
import com.inwords.expenses.feature.events.domain.store.local.EventsLocalStore
import com.inwords.expenses.feature.expenses.domain.store.ExpensesLocalStore
import com.inwords.expenses.feature.expenses.domain.store.ExpensesRemoteStore
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class EventExpensesPushTask internal constructor(
    eventsLocalStoreLazy: Lazy<EventsLocalStore>,
    expensesLocalStoreLazy: Lazy<ExpensesLocalStore>,
    expensesRemoteStoreLazy: Lazy<ExpensesRemoteStore>,
    transactionHelperLazy: Lazy<TransactionHelper>,
) {

    private val eventsLocalStore by eventsLocalStoreLazy
    private val expensesLocalStore by expensesLocalStoreLazy
    private val expensesRemoteStore by expensesRemoteStoreLazy
    private val transactionHelper by transactionHelperLazy

    /**
     * Prerequisites:
     * 1. Currencies are synced
     * 2. Event is synced
     * 3. Persons are synced
     */
    suspend fun pushEventExpenses(eventId: Long): IoResult<*> = withContext(IO) {
        val localEvent = eventsLocalStore.getEventWithDetails(eventId)
            ?.takeIf { details ->
                details.event.serverId != null &&
                    details.persons.all { it.serverId != null } &&
                    details.currencies.all { it.serverId != null }
            } ?: return@withContext IoResult.Error.Failure

        val localExpenses = expensesLocalStore.getExpenses(eventId)
        if (localExpenses.isEmpty()) return@withContext IoResult.Success(Unit)

        val expensesToAdd = localExpenses.filter { it.serverId == null }
        if (expensesToAdd.isEmpty()) return@withContext IoResult.Success(Unit)

        val expensesToAddFiltered = expensesToAdd.filter { it.person.serverId != null && it.currency.serverId != null }
        val eventServerId = localEvent.event.serverId ?: return@withContext IoResult.Error.Failure
        if (expensesToAddFiltered.isEmpty()) {
            Observability.captureMessage("EventExpensesPushTask found pending expenses without synced person or currency data") {
                setContext("event_server_id", eventServerId)
                setContext("pending_expenses_count", expensesToAdd.size.toString())
            }
            return@withContext IoResult.Success(Unit)
        }

        val networkResults = expensesRemoteStore.addExpensesToEvent(
            event = localEvent.event,
            expenses = expensesToAddFiltered,
            currencies = localEvent.currencies,
            persons = localEvent.persons
        )

        withContext(NonCancellable) {
            networkResults.forEachIndexed { expenseIndex, networkResult ->
                val networkExpense = when (networkResult) {
                    is IoResult.Success -> networkResult.data
                    is IoResult.Error -> return@forEachIndexed
                }
                val networkExpenseServerId = networkExpense.serverId
                    .captureMessageIfNull("EventExpensesPushTask received a pushed expense without a server id") {
                        setContext("event_server_id", eventServerId)
                    }
                    ?: return@forEachIndexed
                transactionHelper.immediateWriteTransaction {
                    expensesLocalStore.updateExpenseServerId(networkExpense.expenseId, networkExpenseServerId)
                    networkExpense.subjectExpenseSplitWithPersons.forEachIndexed { splitIndex, networkSplit ->
                        expensesLocalStore.updateExpenseSplitExchangedAmount(
                            expenseSplitId = expensesToAddFiltered[expenseIndex].subjectExpenseSplitWithPersons[splitIndex].expenseSplitId,
                            exchangedAmount = networkSplit.exchangedAmount
                        )
                    }
                }
            }
        }

        IoResult.Success(Unit)
    }
}
