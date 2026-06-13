package com.inwords.expenses.feature.expenses.ui.list.bottom_sheet.item

import androidx.lifecycle.ViewModel
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
import com.inwords.expenses.feature.expenses.ui.add.AddExpensePaneDestination
import com.inwords.expenses.feature.expenses.ui.list.ExpenseCorrectionStatusTextFactory
import com.inwords.expenses.feature.expenses.ui.list.dialog.revert.ExpenseRevertDialogDestination
import com.inwords.expenses.feature.expenses.ui.utils.toRoundedString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

internal class ExpenseItemPaneViewModel(
    private val navigationController: NavigationController,
    getCurrentEventStateUseCase: GetCurrentEventStateUseCase,
    private val expensesLocalStore: ExpensesLocalStore,
    private val correctionStatusTextFactory: ExpenseCorrectionStatusTextFactory,
    private val expenseId: Long,
    private val eventId: Long,
    viewModelScope: CoroutineScope = CoroutineScope(SupervisorJob() + IO),
) : ViewModel(viewModelScope = viewModelScope) {

    val state: StateFlow<SimpleScreenState<ExpenseItemPaneUiModel>> = getCurrentEventStateUseCase.currentEvent
        .flatMapLatestNoBuffer { currentEvent ->
            currentEvent ?: return@flatMapLatestNoBuffer flowOf(SimpleScreenState.Error)

            combine(
                expensesLocalStore.getExpenseFlow(expenseId),
                expensesLocalStore.hasCorrectionForFlow(expenseId),
                expensesLocalStore.getCorrectionForTargetFlow(expenseId),
            ) { expense, hasCorrection, correction ->
                if (expense == null) {
                    SimpleScreenState.Error
                } else {
                    SimpleScreenState.Success(
                        expense.toUiModel(
                            primaryCurrencyCode = currentEvent.primaryCurrency.code,
                            canCorrect = !hasCorrection,
                            statusText = correction?.let { correctionStatusTextFactory.createStatusForCorrection(it) },
                        )
                    )
                }
            }
        }
        .stateInWhileSubscribed(viewModelScope, initialValue = SimpleScreenState.Loading)

    fun onRevertExpenseClick() {
        val item = (state.value as? SimpleScreenState.Success)?.data?.takeIf { it.canCorrect } ?: return

        navigationController.navigateTo(
            ExpenseRevertDialogDestination(
                expenseId = expenseId,
                eventId = eventId,
                expenseDescription = item.description,
            )
        )
    }

    fun onEditExpenseClick() {
        val item = (state.value as? SimpleScreenState.Success)?.data?.takeIf { it.canCorrect } ?: return
        navigationController.navigateTo(
            AddExpensePaneDestination(
                replenishment = null,
                replacesExpenseId = item.expenseId,
            )
        )
    }

    private fun Expense.toUiModel(
        primaryCurrencyCode: String,
        canCorrect: Boolean,
        statusText: String?,
    ): ExpenseItemPaneUiModel {
        val expense = this
        val amountSign = amountSign(expense.expenseType)
        val split = expense.subjectExpenseSplitWithPersons

        return ExpenseItemPaneUiModel(
            expenseId = expense.expenseId,
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
            canCorrect = canCorrect,
            statusText = statusText,
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
