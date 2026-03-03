package com.inwords.expenses.feature.expenses.ui.list.dialog.revert

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inwords.expenses.core.navigation.NavigationController
import com.inwords.expenses.core.ui.utils.DefaultStringProvider
import com.inwords.expenses.core.ui.utils.StringProvider
import com.inwords.expenses.core.utils.IO
import com.inwords.expenses.feature.events.domain.store.local.EventsLocalStore
import com.inwords.expenses.feature.expenses.domain.ExpensesInteractor
import com.inwords.expenses.feature.expenses.domain.store.ExpensesLocalStore
import com.inwords.expenses.feature.expenses.ui.list.ExpensesPaneDestination
import expenses.shared.feature.expenses.generated.resources.Res
import expenses.shared.feature.expenses.generated.resources.expenses_revert_description
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal class ExpenseRevertDialogViewModel(
    private val navigationController: NavigationController,
    private val expensesInteractor: ExpensesInteractor,
    private val expensesLocalStore: ExpensesLocalStore,
    private val eventsLocalStore: EventsLocalStore,
    private val expenseId: Long,
    private val eventId: Long,
    private val expenseDescription: String,
    private val stringProvider: StringProvider = DefaultStringProvider,
) : ViewModel(viewModelScope = CoroutineScope(SupervisorJob() + IO)) {

    private var revertJob: Job? = null

    fun onConfirmRevert() {
        if (revertJob?.isActive == true) return

        revertJob = viewModelScope.launch {
            val event = eventsLocalStore.getEvent(eventId)
            val originalExpense = expensesLocalStore.getExpense(expenseId)
            if (event == null || originalExpense == null) {
                navigationController.popBackStack()
                return@launch
            }

            expensesInteractor.revertExpense(
                event = event,
                originalExpense = originalExpense,
                description = stringProvider.getString(
                    Res.string.expenses_revert_description,
                    expenseDescription,
                )
            )

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
