package com.inwords.expenses.feature.expenses.domain.store

import com.inwords.expenses.core.utils.IoResult
import com.inwords.expenses.feature.events.domain.model.Currency
import com.inwords.expenses.feature.events.domain.model.Event
import com.inwords.expenses.feature.events.domain.model.Person
import com.inwords.expenses.feature.expenses.domain.model.Expense

internal interface ExpensesRemoteStore {

    suspend fun getExpenses(
        event: Event,
        currencies: List<Currency>,
        persons: List<Person>
    ): IoResult<List<Expense>>

    suspend fun addExpensesToEvent(
        event: Event,
        expenses: List<ExpensePushItem>,
        currencies: List<Currency>,
        persons: List<Person>,
    ): List<IoResult<Expense>>

}
