package com.inwords.expenses.feature.sync.api

import com.inwords.expenses.core.utils.Component
import com.inwords.expenses.feature.sync.data.EventsSyncManager
import com.inwords.expenses.feature.sync.data.EventsSyncManagerFactory
import com.inwords.expenses.feature.sync.data.EventsSyncManagerObserverDelegate
import com.inwords.expenses.feature.sync.domain.EventsSyncObserver

class SyncComponent internal constructor(
    private val eventsSyncManagerFactory: EventsSyncManagerFactory,
    private val deps: SyncComponentFactory.Deps
) : Component {

    private val eventsSyncManagerLazy = lazy {
        eventsSyncManagerFactory.create()
    }
    private val eventsSyncManagerObserverDelegateLazy: Lazy<EventsSyncManagerObserverDelegate> = lazy {
        eventsSyncManagerLazy.value
    }

    val eventsSyncObserver: EventsSyncObserver by lazy {
        EventsSyncObserver(
            getCurrentEventStateUseCaseLazy = deps.getCurrentEventStateUseCaseLazy,
            getExpensesUseCaseLazy = deps.getExpensesUseCaseLazy,
            expensesRefreshRequestsHolderLazy = deps.expensesRefreshRequestsHolderLazy,
            eventsSyncStateHolderLazy = deps.eventsSyncStateHolderLazy,
            eventsSyncManagerLazy = eventsSyncManagerObserverDelegateLazy
        )
    }

    val eventsSyncManager: EventsSyncManager by eventsSyncManagerLazy

}
