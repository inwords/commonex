package com.inwords.expenses.feature.sync.api

import com.inwords.expenses.feature.events.domain.EventsSyncStateHolder
import com.inwords.expenses.feature.events.domain.GetCurrentEventStateUseCase
import com.inwords.expenses.feature.expenses.domain.ExpensesRefreshRequestsHolder
import com.inwords.expenses.feature.expenses.domain.GetExpensesUseCase

expect class SyncComponentFactory {

    interface Deps : SyncComponentFactoryCommonDeps

    fun create(): SyncComponent
}

interface SyncComponentFactoryCommonDeps {

    val getCurrentEventStateUseCaseLazy: Lazy<GetCurrentEventStateUseCase>
    val getExpensesUseCaseLazy: Lazy<GetExpensesUseCase>
    val expensesRefreshRequestsHolderLazy: Lazy<ExpensesRefreshRequestsHolder>
    val eventsSyncStateHolderLazy: Lazy<EventsSyncStateHolder>
}
