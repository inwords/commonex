package com.inwords.expenses.feature.expenses.ui.debts_list

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
object DebtsListPaneDestination : Destination

internal fun ExpensesComponent.getDebtsListPaneNavModule(
    navigationController: NavigationController,
): NavModule {
    return NavModule(DebtsListPaneDestination.serializer()) {
        entry<DebtsListPaneDestination> {
            val viewModel = viewModel<DebtsListViewModel>(factory = viewModelFactory {
                initializer {
                    DebtsListViewModel(
                        navigationController = navigationController,
                        getCurrentEventStateUseCase = getCurrentEventStateUseCaseLazy.value,
                        getExpensesDetailsUseCase = getExpensesDetailsUseCaseLazy.value,
                    )
                }
            })
            DebtsListPane(
                state = viewModel.state.collectAsStateWithLifecycle().value,
                onReplenishmentClick = viewModel::onReplenishmentClick,
                onNavIconClicked = viewModel::onNavIconClicked,
            )
        }
    }
}
