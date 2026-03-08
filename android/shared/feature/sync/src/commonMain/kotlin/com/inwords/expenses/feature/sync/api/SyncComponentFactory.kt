package com.inwords.expenses.feature.sync.api

import com.inwords.expenses.feature.events.domain.EventsSyncStateHolder
import com.inwords.expenses.feature.events.domain.GetCurrentEventStateUseCase
import com.inwords.expenses.feature.expenses.domain.ExpensesInteractor

expect class SyncComponentFactory {

    interface Deps : SyncComponentFactoryCommonDeps

    fun create(): SyncComponent
}

interface SyncComponentFactoryCommonDeps {

    val getCurrentEventStateUseCaseLazy: Lazy<GetCurrentEventStateUseCase>
    val expensesInteractorLazy: Lazy<ExpensesInteractor>
    val eventsSyncStateHolderLazy: Lazy<EventsSyncStateHolder>
}
