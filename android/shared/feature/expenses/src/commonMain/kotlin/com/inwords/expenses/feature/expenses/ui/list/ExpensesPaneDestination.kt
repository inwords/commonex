package com.inwords.expenses.feature.expenses.ui.list

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.inwords.expenses.core.navigation.Destination
import com.inwords.expenses.core.navigation.NavModule
import com.inwords.expenses.core.navigation.NavigationController
import com.inwords.expenses.feature.expenses.api.ExpensesComponent
import kotlinx.serialization.Serializable

@Serializable
object ExpensesPaneDestination : Destination

internal fun ExpensesComponent.getExpensesPaneNavModule(
    navigationController: NavigationController,
): NavModule {
    return NavModule(ExpensesPaneDestination.serializer()) {
        entry<ExpensesPaneDestination> {
            val expensesViewModel = viewModel<ExpensesViewModel>(factory = viewModelFactory {
                initializer {
                    ExpensesViewModel(
                        navigationController = navigationController,
                        getCurrentEventStateUseCase = getCurrentEventStateUseCaseLazy.value,
                        eventDeletionStateManager = eventDeletionStateManagerLazy.value,
                        getEventsUseCase = getEventsUseCaseLazy.value,
                        joinEventUseCase = joinEventUseCaseLazy.value,
                        leaveEventUseCase = leaveEventUseCaseLazy.value,
                        deleteEventUseCase = deleteEventUseCaseLazy.value,
                        getExpensesDetailsUseCase = getExpensesDetailsUseCaseLazy.value,
                        requestExpensesRefreshUseCase = requestExpensesRefreshUseCaseLazy.value,
                        getBannerUseCase = getBannerUseCaseLazy.value,
                        dismissBannerUseCase = dismissBannerUseCaseLazy.value,
                        eventsSyncStateHolder = eventsSyncStateHolderLazy.value,
                        settingsRepository = settingsRepositoryLazy.value,
                    )
                }
            })
            ExpensesPane(
                state = expensesViewModel.state.collectAsStateWithLifecycle().value,
                appStoreUrl = appStoreUrl,
                onBannerDismissClick = expensesViewModel::onBannerDismissClick,
                onMenuClick = expensesViewModel::onMenuClick,
                onOpenEventsListClick = expensesViewModel::onOpenEventsListClick,
                onInviteClick = expensesViewModel::onInviteClick,
                onAddExpenseClick = expensesViewModel::onAddExpenseClick,
                onExpenseClick = expensesViewModel::onExpenseClick,
                onDebtsDetailsClick = expensesViewModel::onDebtsDetailsClick,
                onReplenishmentClick = expensesViewModel::onReplenishmentClick,
                onDayChipClick = expensesViewModel::onDayChipClick,
                onVisibleDayChanged = expensesViewModel::onVisibleDayChanged,
                onCreateEventClick = expensesViewModel::onCreateEventClick,
                onJoinEventClick = expensesViewModel::onJoinEventClick,
                onJoinLocalEventClick = expensesViewModel::onJoinLocalEventClick,
                onDeleteEventClick = expensesViewModel::onDeleteEventClick,
                onDeleteOnlyLocalEventClick = expensesViewModel::onDeleteOnlyLocalEventClick,
                onKeepLocalEventClick = expensesViewModel::onKeepLocalEventClick,
                onRefresh = expensesViewModel::onRefresh,
            )
        }
    }
}
