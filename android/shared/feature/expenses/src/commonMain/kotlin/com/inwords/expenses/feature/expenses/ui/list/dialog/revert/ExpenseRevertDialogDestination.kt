package com.inwords.expenses.feature.expenses.ui.list.dialog.revert

import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation3.scene.DialogSceneStrategy.Companion.dialog
import com.inwords.expenses.core.navigation.Destination
import com.inwords.expenses.core.navigation.NavModule
import com.inwords.expenses.core.navigation.NavigationController
import com.inwords.expenses.feature.events.domain.store.local.EventsLocalStore
import com.inwords.expenses.feature.expenses.domain.ExpensesInteractor
import com.inwords.expenses.feature.expenses.domain.store.ExpensesLocalStore
import kotlinx.serialization.Serializable

@Serializable
internal data class ExpenseRevertDialogDestination(
    val expenseId: Long,
    val eventId: Long,
    val expenseDescription: String,
) : Destination

fun getExpenseRevertDialogNavModule(
    navigationController: NavigationController,
    expensesInteractorLazy: Lazy<ExpensesInteractor>,
    expensesLocalStoreLazy: Lazy<ExpensesLocalStore>,
    eventsLocalStoreLazy: Lazy<EventsLocalStore>,
): NavModule {
    return NavModule(ExpenseRevertDialogDestination.serializer()) {
        entry<ExpenseRevertDialogDestination>(metadata = dialog()) { key ->
            val viewModel = viewModel<ExpenseRevertDialogViewModel>(factory = viewModelFactory {
                initializer {
                    ExpenseRevertDialogViewModel(
                        navigationController = navigationController,
                        expensesInteractor = expensesInteractorLazy.value,
                        expensesLocalStore = expensesLocalStoreLazy.value,
                        eventsLocalStore = eventsLocalStoreLazy.value,
                        expenseId = key.expenseId,
                        eventId = key.eventId,
                        expenseDescription = key.expenseDescription,
                    )
                }
            })

            ExpenseRevertDialog(
                expenseDescription = key.expenseDescription,
                onConfirmRevert = viewModel::onConfirmRevert,
                onDismiss = viewModel::onDismiss,
            )
        }
    }
}
