package com.inwords.expenses.feature.expenses.ui.list.bottom_sheet.item

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.inwords.expenses.core.navigation.BottomSheetSceneStrategy.Companion.bottomSheet
import com.inwords.expenses.core.navigation.Destination
import com.inwords.expenses.core.navigation.NavModule
import com.inwords.expenses.core.navigation.NavigationController
import com.inwords.expenses.feature.expenses.api.ExpensesComponent
import kotlinx.serialization.Serializable

@Serializable
internal data class ExpenseItemPaneDestination(
    val expenseId: Long,
    val eventId: Long,
) : Destination

@OptIn(ExperimentalMaterial3Api::class)
internal fun ExpensesComponent.getExpenseItemPaneNavModule(
    navigationController: NavigationController,
): NavModule {
    return NavModule(ExpenseItemPaneDestination.serializer()) {
        entry<ExpenseItemPaneDestination>(metadata = bottomSheet()) { key ->
            val viewModel = viewModel<ExpenseItemPaneViewModel>(factory = viewModelFactory {
                initializer {
                    ExpenseItemPaneViewModel(
                        navigationController = navigationController,
                        getCurrentEventStateUseCase = getCurrentEventStateUseCaseLazy.value,
                        expensesLocalStore = expensesLocalStore.value,
                        expenseId = key.expenseId,
                        eventId = key.eventId,
                    )
                }
            })
            ExpenseItemPane(
                state = viewModel.state.collectAsStateWithLifecycle().value,
                onRevertExpenseClick = viewModel::onRevertExpenseClick,
            )
        }
    }
}
