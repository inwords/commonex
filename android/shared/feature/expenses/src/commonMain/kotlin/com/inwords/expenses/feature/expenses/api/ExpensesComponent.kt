package com.inwords.expenses.feature.expenses.api

import com.inwords.expenses.core.navigation.NavModule
import com.inwords.expenses.core.navigation.NavigationController
import com.inwords.expenses.core.network.HostConfig
import com.inwords.expenses.core.storage.utils.TransactionHelper
import com.inwords.expenses.core.utils.Component
import com.inwords.expenses.core.utils.SuspendLazy
import com.inwords.expenses.feature.events.api.EventDeletionStateManager
import com.inwords.expenses.feature.events.domain.DeleteEventUseCase
import com.inwords.expenses.feature.events.domain.EventsSyncStateHolder
import com.inwords.expenses.feature.events.domain.GetCurrentEventStateUseCase
import com.inwords.expenses.feature.events.domain.GetEventsUseCase
import com.inwords.expenses.feature.events.domain.JoinEventUseCase
import com.inwords.expenses.feature.events.domain.store.local.CurrenciesLocalStore
import com.inwords.expenses.feature.events.domain.store.local.EventsLocalStore
import com.inwords.expenses.feature.expenses.data.db.ExpensesLocalStoreImpl
import com.inwords.expenses.feature.expenses.data.db.dao.ExpensesDao
import com.inwords.expenses.feature.expenses.data.network.ExpensesRemoteStoreImpl
import com.inwords.expenses.feature.expenses.domain.CurrencyRatesCache
import com.inwords.expenses.feature.expenses.domain.ExpensesInteractor
import com.inwords.expenses.feature.expenses.domain.store.ExpensesLocalStore
import com.inwords.expenses.feature.expenses.domain.tasks.EventExpensesPullTask
import com.inwords.expenses.feature.expenses.domain.tasks.EventExpensesPushTask
import com.inwords.expenses.feature.expenses.ui.add.getAddExpensePaneNavModule
import com.inwords.expenses.feature.expenses.ui.debts_list.getDebtsListPaneNavModule
import com.inwords.expenses.feature.expenses.ui.list.bottom_sheet.item.getExpenseItemPaneNavModule
import com.inwords.expenses.feature.expenses.ui.list.dialog.revert.getExpenseRevertDialogNavModule
import com.inwords.expenses.feature.expenses.ui.list.getExpensesPaneNavModule
import com.inwords.expenses.feature.settings.api.SettingsRepository
import io.ktor.client.HttpClient

class ExpensesComponent(private val deps: Deps) : Component {

    interface Deps {

        val expensesDao: ExpensesDao

        val client: SuspendLazy<HttpClient>
        val hostConfig: HostConfig

        val transactionHelper: TransactionHelper

        val eventsLocalStore: EventsLocalStore
        val currenciesLocalStore: CurrenciesLocalStore

        val getCurrentEventStateUseCaseLazy: Lazy<GetCurrentEventStateUseCase>
        val getEventsUseCaseLazy: Lazy<GetEventsUseCase>
        val joinEventUseCaseLazy: Lazy<JoinEventUseCase>
        val deleteEventUseCaseLazy: Lazy<DeleteEventUseCase>

        val eventDeletionStateManagerLazy: Lazy<EventDeletionStateManager>
        val eventsSyncStateHolderLazy: Lazy<EventsSyncStateHolder>

        val settingsRepositoryLazy: Lazy<SettingsRepository>
    }

    internal val getCurrentEventStateUseCaseLazy get() = deps.getCurrentEventStateUseCaseLazy
    internal val eventDeletionStateManagerLazy get() = deps.eventDeletionStateManagerLazy
    internal val getEventsUseCaseLazy get() = deps.getEventsUseCaseLazy
    internal val deleteEventUseCaseLazy get() = deps.deleteEventUseCaseLazy
    internal val eventsSyncStateHolderLazy get() = deps.eventsSyncStateHolderLazy
    internal val joinEventUseCaseLazy get() = deps.joinEventUseCaseLazy
    internal val settingsRepositoryLazy get() = deps.settingsRepositoryLazy
    internal val eventsLocalStore get() = deps.eventsLocalStore

    val expensesLocalStore: Lazy<ExpensesLocalStore> = lazy {
        ExpensesLocalStoreImpl(
            expensesDaoLazy = lazy { deps.expensesDao },
            transactionHelperLazy = lazy { deps.transactionHelper },
        )
    }

    private val expensesRemoteStore = lazy {
        ExpensesRemoteStoreImpl(
            client = deps.client,
            hostConfig = deps.hostConfig,
        )
    }

    private val currencyRatesCache = lazy {
        CurrencyRatesCache(
            currenciesLocalStore = deps.currenciesLocalStore,
        )
    }

    val eventExpensesPushTask: Lazy<EventExpensesPushTask> = lazy {
        EventExpensesPushTask(
            eventsLocalStoreLazy = lazy { deps.eventsLocalStore },
            expensesLocalStoreLazy = expensesLocalStore,
            expensesRemoteStoreLazy = expensesRemoteStore,
            transactionHelperLazy = lazy { deps.transactionHelper }
        )
    }

    val eventExpensesPullTask: Lazy<EventExpensesPullTask> = lazy {
        EventExpensesPullTask(
            eventsLocalStoreLazy = lazy { deps.eventsLocalStore },
            expensesLocalStoreLazy = expensesLocalStore,
            expensesRemoteStoreLazy = expensesRemoteStore
        )
    }

    val expensesInteractorLazy: Lazy<ExpensesInteractor> = lazy {
        ExpensesInteractor(
            expensesLocalStoreLazy = expensesLocalStore,
            currenciesLocalStoreLazy = lazy { deps.currenciesLocalStore },
            currencyRatesCacheLazy = currencyRatesCache,
        )
    }

    fun getNavModules(
        navigationController: NavigationController,
    ): List<NavModule> {
        return listOf(
            getAddExpensePaneNavModule(navigationController),
            getExpensesPaneNavModule(navigationController),
            getDebtsListPaneNavModule(navigationController),
            getExpenseItemPaneNavModule(navigationController),
            getExpenseRevertDialogNavModule(navigationController),
        )
    }
}
