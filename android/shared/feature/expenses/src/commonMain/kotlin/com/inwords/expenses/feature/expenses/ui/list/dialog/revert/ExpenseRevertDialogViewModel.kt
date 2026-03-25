package com.inwords.expenses.feature.expenses.ui.list.dialog.revert

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inwords.expenses.core.navigation.NavigationController
import com.inwords.expenses.core.ui.utils.DefaultStringProvider
import com.inwords.expenses.core.ui.utils.StringProvider
import com.inwords.expenses.core.utils.IO
import com.inwords.expenses.feature.expenses.domain.RevertExpenseUseCase
import com.inwords.expenses.feature.expenses.ui.list.ExpensesPaneDestination
import expenses.shared.feature.expenses.generated.resources.Res
import expenses.shared.feature.expenses.generated.resources.expenses_revert_description
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal class ExpenseRevertDialogViewModel(
    private val navigationController: NavigationController,
    private val revertExpenseUseCase: RevertExpenseUseCase,
    private val expenseId: Long,
    private val eventId: Long,
    private val expenseDescription: String,
    private val stringProvider: StringProvider = DefaultStringProvider,
    viewModelScope: CoroutineScope = CoroutineScope(SupervisorJob() + IO),
) : ViewModel(viewModelScope = viewModelScope) {

    private var revertJob: Job? = null

    fun onConfirmRevert() {
        if (revertJob?.isActive == true) return

        revertJob = viewModelScope.launch {
            val reverted = revertExpenseUseCase.revertExpense(
                eventId = eventId,
                expenseId = expenseId,
                description = stringProvider.getString(
                    Res.string.expenses_revert_description,
                    expenseDescription,
                ),
            )

            if (!reverted) {
                navigationController.popBackStack()
                return@launch
            }

            navigationController.popBackStack(
                toDestination = ExpensesPaneDestination,
                inclusive = false,
            )
        }
    }

    fun onDismiss() {
        navigationController.popBackStack()
    }
}
