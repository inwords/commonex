package com.inwords.expenses.feature.expenses.domain.store

import com.inwords.expenses.feature.expenses.domain.model.Expense

internal data class ExpensePushItem(
    val expense: Expense,
    val idempotencyKey: String,
)
