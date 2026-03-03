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
import com.inwords.expenses.feature.events.domain.GetCurrentEventStateUseCase
import com.inwords.expenses.feature.expenses.domain.store.ExpensesLocalStore
import kotlinx.serialization.Serializable

@Serializable
internal data class ExpenseItemPaneDestination(
    val expenseId: Long,
    val eventId: Long,
) : Destination

@OptIn(ExperimentalMaterial3Api::class)
fun getExpenseItemPaneNavModule(
    navigationController: NavigationController,
    getCurrentEventStateUseCaseLazy: Lazy<GetCurrentEventStateUseCase>,
    expensesLocalStoreLazy: Lazy<ExpensesLocalStore>,
): NavModule {
    return NavModule(ExpenseItemPaneDestination.serializer()) {
        entry<ExpenseItemPaneDestination>(metadata = bottomSheet()) { key ->
            val viewModel = viewModel<ExpenseItemPaneViewModel>(factory = viewModelFactory {
                initializer {
                    ExpenseItemPaneViewModel(
                        navigationController = navigationController,
                        getCurrentEventStateUseCase = getCurrentEventStateUseCaseLazy.value,
                        expensesLocalStore = expensesLocalStoreLazy.value,
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
