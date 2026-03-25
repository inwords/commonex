package com.inwords.expenses.feature.expenses.domain

import com.inwords.expenses.feature.events.domain.model.Event

class RequestExpensesRefreshUseCase internal constructor(
    expensesRefreshRequestsHolderLazy: Lazy<ExpensesRefreshRequestsHolder>,
) {

    private val expensesRefreshRequestsHolder by expensesRefreshRequestsHolderLazy

    suspend fun requestRefresh(event: Event) {
        expensesRefreshRequestsHolder.enqueueAsyncSync(event)
    }
}
