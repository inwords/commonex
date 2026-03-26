package com.inwords.expenses.feature.expenses.data.db.converter

import com.inwords.expenses.feature.events.domain.model.Event
import com.inwords.expenses.feature.expenses.data.db.entity.ExpenseEntity
import com.inwords.expenses.feature.expenses.data.db.entity.ExpenseSplitEntity
import com.inwords.expenses.feature.expenses.domain.model.Expense
import com.inwords.expenses.feature.expenses.domain.model.ExpenseSplitWithPerson

internal fun Expense.toEntity(event: Event): ExpenseEntity {
    return ExpenseEntity(
        eventId = event.id,
        serverId = this.serverId,
        currencyId = this.currency.id,
        expenseType = this.expenseType,
        personId = this.person.id,
        isCustomRate = this.isCustomRate,
        timestamp = this.timestamp,
        description = this.description,
    )
}

internal fun ExpenseSplitWithPerson.toEntity(): ExpenseSplitEntity {
    return ExpenseSplitEntity(
        expenseSplitId = this.expenseSplitId,
        expenseId = this.expenseId,
        personId = this.person.id,
        originalAmountUnscaled = this.originalAmount.significand,
        originalAmountScale = this.originalAmount.exponent,
        exchangedAmountUnscaled = this.exchangedAmount.significand,
        exchangedAmountScale = this.exchangedAmount.exponent,
    )
}
