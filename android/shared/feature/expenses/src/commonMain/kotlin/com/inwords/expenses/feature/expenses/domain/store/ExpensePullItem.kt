package com.inwords.expenses.feature.expenses.domain.store

import com.inwords.expenses.feature.expenses.domain.model.Expense

data class ExpensePullItem(
    val expense: Expense,
    val revertsExpenseServerId: String?,
    val replacesExpenseServerId: String?,
)
