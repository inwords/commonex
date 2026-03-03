package com.inwords.expenses.feature.expenses.ui.list

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.inwords.expenses.core.navigation.Destination
import com.inwords.expenses.core.navigation.NavModule
import com.inwords.expenses.core.navigation.NavigationController
import com.inwords.expenses.feature.events.api.EventDeletionStateManager
import com.inwords.expenses.feature.events.domain.DeleteEventUseCase
import com.inwords.expenses.feature.events.domain.EventsSyncStateHolder
import com.inwords.expenses.feature.events.domain.GetCurrentEventStateUseCase
import com.inwords.expenses.feature.events.domain.GetEventsUseCase
import com.inwords.expenses.feature.events.domain.JoinEventUseCase
import com.inwords.expenses.feature.expenses.domain.ExpensesInteractor
import com.inwords.expenses.feature.settings.api.SettingsRepository
import kotlinx.serialization.Serializable

@Serializable
object ExpensesPaneDestination : Destination

fun getExpensesPaneNavModule(
    navigationController: NavigationController,
    getCurrentEventStateUseCaseLazy: Lazy<GetCurrentEventStateUseCase>,
    eventDeletionStateManagerLazy: Lazy<EventDeletionStateManager>,
    getEventsUseCaseLazy: Lazy<GetEventsUseCase>,
    deleteEventUseCaseLazy: Lazy<DeleteEventUseCase>,
    expensesInteractorLazy: Lazy<ExpensesInteractor>,
    eventsSyncStateHolderLazy: Lazy<EventsSyncStateHolder>,
    joinEventUseCaseLazy: Lazy<JoinEventUseCase>,
    settingsRepositoryLazy: Lazy<SettingsRepository>,
): NavModule {
    return NavModule(ExpensesPaneDestination.serializer()) {
        entry<ExpensesPaneDestination> {
            val viewModel = viewModel<ExpensesViewModel>(factory = viewModelFactory {
                initializer {
                    ExpensesViewModel(
                        navigationController = navigationController,
                        getCurrentEventStateUseCase = getCurrentEventStateUseCaseLazy.value,
                        eventDeletionStateManager = eventDeletionStateManagerLazy.value,
                        getEventsUseCase = getEventsUseCaseLazy.value,
                        joinEventUseCase = joinEventUseCaseLazy.value,
                        deleteEventUseCase = deleteEventUseCaseLazy.value,
                        expensesInteractor = expensesInteractorLazy.value,
                        eventsSyncStateHolder = eventsSyncStateHolderLazy.value,
                        settingsRepository = settingsRepositoryLazy.value,
                    )
                }
            })
            ExpensesPane(
                state = viewModel.state.collectAsStateWithLifecycle().value,
                onMenuClick = viewModel::onMenuClick,
                onAddExpenseClick = viewModel::onAddExpenseClick,
                onExpenseClick = viewModel::onExpenseClick,
                onDebtsDetailsClick = viewModel::onDebtsDetailsClick,
                onReplenishmentClick = viewModel::onReplenishmentClick,
                onCreateEventClick = viewModel::onCreateEventClick,
                onJoinEventClick = viewModel::onJoinEventClick,
                onJoinLocalEventClick = viewModel::onJoinLocalEventClick,
                onDeleteEventClick = viewModel::onDeleteEventClick,
                onDeleteOnlyLocalEventClick = viewModel::onDeleteOnlyLocalEventClick,
                onKeepLocalEventClick = viewModel::onKeepLocalEventClick,
                onRefresh = viewModel::onRefresh,
            )
        }
    }
}
