package com.inwords.expenses.feature.expenses.domain.model

import com.inwords.expenses.core.utils.sumOf
import com.inwords.expenses.feature.events.domain.model.Currency
import com.inwords.expenses.feature.events.domain.model.Person
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlin.time.Instant

data class Expense(
    val expenseId: Long,
    val serverId: String?,
    val currency: Currency,
    val expenseType: ExpenseType,
    val person: Person,
    val subjectExpenseSplitWithPersons: List<ExpenseSplitWithPerson>,
    val isCustomRate: Boolean,
    val timestamp: Instant,
    val description: String,
) {

    val totalAmount: BigDecimal = subjectExpenseSplitWithPersons.sumOf { it.exchangedAmount }
}
