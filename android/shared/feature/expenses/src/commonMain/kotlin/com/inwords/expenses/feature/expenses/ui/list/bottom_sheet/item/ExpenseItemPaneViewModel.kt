package com.inwords.expenses.feature.expenses.ui.list.bottom_sheet.item

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inwords.expenses.core.navigation.NavigationController
import com.inwords.expenses.core.ui.utils.SimpleScreenState
import com.inwords.expenses.core.ui.utils.formatLocalDateTime
import com.inwords.expenses.core.ui.utils.getDefaultDateTimeFormat
import com.inwords.expenses.core.utils.IO
import com.inwords.expenses.core.utils.asImmutableListAdapter
import com.inwords.expenses.core.utils.flatMapLatestNoBuffer
import com.inwords.expenses.core.utils.stateInWhileSubscribed
import com.inwords.expenses.core.utils.sumOf
import com.inwords.expenses.feature.events.domain.GetCurrentEventStateUseCase
import com.inwords.expenses.feature.expenses.domain.model.Expense
import com.inwords.expenses.feature.expenses.domain.model.ExpenseType
import com.inwords.expenses.feature.expenses.domain.store.ExpensesLocalStore
import com.inwords.expenses.feature.expenses.ui.list.dialog.revert.ExpenseRevertDialogDestination
import com.inwords.expenses.feature.expenses.ui.utils.toRoundedString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

internal class ExpenseItemPaneViewModel(
    private val navigationController: NavigationController,
    getCurrentEventStateUseCase: GetCurrentEventStateUseCase,
    private val expensesLocalStore: ExpensesLocalStore,
    private val expenseId: Long,
    private val eventId: Long,
) : ViewModel(viewModelScope = CoroutineScope(SupervisorJob() + IO)) {

    val state: StateFlow<SimpleScreenState<ExpenseItemPaneUiModel>> = getCurrentEventStateUseCase.currentEvent
        .flatMapLatestNoBuffer { currentEvent ->
            currentEvent ?: return@flatMapLatestNoBuffer flowOf(SimpleScreenState.Error)

            expensesLocalStore.getExpenseFlow(expenseId).map { expense ->
                if (expense == null) {
                    SimpleScreenState.Error
                } else {
                    SimpleScreenState.Success(expense.toUiModel(primaryCurrencyCode = currentEvent.primaryCurrency.code))
                }
            }
        }
        .stateInWhileSubscribed(viewModelScope, initialValue = SimpleScreenState.Loading)

    fun onRevertExpenseClick() {
        val expenseDescription = (state.value as? SimpleScreenState.Success)?.data?.description ?: return

        navigationController.navigateTo(
            ExpenseRevertDialogDestination(
                expenseId = expenseId,
                eventId = eventId,
                expenseDescription = expenseDescription,
            )
        )
    }

    private fun Expense.toUiModel(primaryCurrencyCode: String): ExpenseItemPaneUiModel {
        val expense = this
        val amountSign = amountSign(expense.expenseType)
        val split = expense.subjectExpenseSplitWithPersons

        return ExpenseItemPaneUiModel(
            expense = expense,
            description = expense.description,
            totalAmount = "$amountSign${expense.totalAmount.toRoundedString()}",
            primaryCurrencyCode = primaryCurrencyCode,
            personName = expense.person.name,
            timestamp = expense.timestamp.formatLocalDateTime(getDefaultDateTimeFormat()),
            originalCurrencyCode = expense.currency.code,
            originalCurrencyName = expense.currency.name,
            exchangeRate = calculateExchangeRate(
                expense = expense,
                primaryCurrencyCode = primaryCurrencyCode,
            ),
            split = split.map { splitWithPerson ->
                ExpenseItemPaneUiModel.PersonSplitUiModel(
                    personName = splitWithPerson.person.name,
                    amount = "$amountSign${splitWithPerson.originalAmount.toRoundedString()}",
                )
            }.asImmutableListAdapter(),
        )
    }

    private fun calculateExchangeRate(expense: Expense, primaryCurrencyCode: String): String? {
        if (expense.currency.code == primaryCurrencyCode) {
            return null
        }

        val totalOriginalAmount = expense.subjectExpenseSplitWithPersons.sumOf { it.originalAmount }.abs()
        val totalExchangedAmount = expense.subjectExpenseSplitWithPersons.sumOf { it.exchangedAmount }.abs()
        return calculateExchangeRateValue(
            totalOriginalAmount = totalOriginalAmount,
            totalExchangedAmount = totalExchangedAmount,
        )
    }

    private fun amountSign(expenseType: ExpenseType): String {
        return when (expenseType) {
            ExpenseType.Spending -> "-"
            ExpenseType.Replenishment -> "+"
        }
    }

}
