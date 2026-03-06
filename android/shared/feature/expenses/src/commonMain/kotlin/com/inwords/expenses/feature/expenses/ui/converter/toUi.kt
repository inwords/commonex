package com.inwords.expenses.feature.expenses.ui.converter

import com.inwords.expenses.core.ui.utils.formatLocalDateTime
import com.inwords.expenses.core.ui.utils.getDefaultDateTimeFormat
import com.inwords.expenses.feature.events.domain.model.Person
import com.inwords.expenses.feature.expenses.domain.model.BarterAccumulatedDebtSummary
import com.inwords.expenses.feature.expenses.domain.model.Expense
import com.inwords.expenses.feature.expenses.domain.model.ExpenseType
import com.inwords.expenses.feature.expenses.ui.common.DebtShortUiModel
import com.inwords.expenses.feature.expenses.ui.debts_list.DebtsListPaneUiModel
import com.inwords.expenses.feature.expenses.ui.list.ExpensesPaneUiModel
import com.inwords.expenses.feature.expenses.ui.utils.toRoundedString

internal fun Expense.toUiModel(
    primaryCurrencyName: String,
    currentPersonId: Long,
): ExpensesPaneUiModel.Expenses.ExpenseUiModel {
    val amountSign = when (expenseType) {
        ExpenseType.Spending -> "-"
        ExpenseType.Replenishment -> "+"
    }
    val currentPersonSplit = subjectExpenseSplitWithPersons.firstOrNull { it.person.id == currentPersonId }
    return ExpensesPaneUiModel.Expenses.ExpenseUiModel(
        expenseId = expenseId,
        currencyText = if (currency.name == primaryCurrencyName) {
            currency.name
        } else {
            "$primaryCurrencyName (${currency.name})"
        },
        expenseType = expenseType,
        personName = person.name,
        isPaidByCurrentPerson = person.id == currentPersonId,
        totalAmount = "$amountSign${totalAmount.toRoundedString()}",
        timestamp = timestamp.formatLocalDateTime(getDefaultDateTimeFormat()),
        description = description,
        currentPersonPartAmount = currentPersonSplit?.let { split ->
            "$amountSign${split.exchangedAmount.toRoundedString()}"
        },
    )
}

internal fun Person.toUiModel(): DebtsListPaneUiModel.PersonUiModel {
    return DebtsListPaneUiModel.PersonUiModel(
        personId = id,
        personName = name
    )
}

internal fun BarterAccumulatedDebtSummary.toCreditorDebtShortUiModel(): DebtShortUiModel {
    return DebtShortUiModel(
        personId = creditor.id,
        personName = creditor.name,
        currencyCode = currency.code,
        currencyName = currency.name,
        amount = amount.toRoundedString(),
    )
}
