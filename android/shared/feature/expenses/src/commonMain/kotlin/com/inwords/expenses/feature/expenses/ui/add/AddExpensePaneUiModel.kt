package com.inwords.expenses.feature.expenses.ui.add

import com.inwords.expenses.feature.expenses.domain.model.ExpenseType
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap

internal data class AddExpensePaneUiModel(
    val eventName: String,
    val expenseType: ExpenseType,
    val description: String,
    val wholeAmount: String,
    val totalAmount: String,
    val primaryCurrencyCode: String,
    val currencies: ImmutableList<CurrencyInfoUiModel>,
    val selectedCurrencyCode: String,
    val exchangeRate: ExchangeRateUiModel?,
    val persons: ImmutableList<PersonInfoUiModel>,
    val selectedPersonName: String,
    val subjectPersons: ImmutableList<PersonInfoUiModel>,
    val selectedSubjectPersonsCount: Int,
    val selectedSubjectPersonsNames: String,
    val equalSplit: Boolean,
    val equalShareAmount: String?,
    val split: ImmutableList<ExpenseSplitWithPersonUiModel>,
    val splitByPerson: ImmutableMap<Long, ExpenseSplitWithPersonUiModel>,
    val lastSplitPersonId: Long?,

    val canSave: Boolean,
) {

    data class CurrencyInfoUiModel(
        val currencyName: String,
        val currencyCode: String,
        val selected: Boolean,
    )

    data class ExchangeRateUiModel(
        val originalCurrencyCode: String,
        val primaryCurrencyCode: String,
        val rateRaw: String,
        val isCustom: Boolean,
        val isValid: Boolean,
        val convertedTotalAmount: String?,
    )

    data class PersonInfoUiModel(
        val personId: Long,
        val personName: String,
        val selected: Boolean,
    )

    data class ExpenseSplitWithPersonUiModel(
        val person: PersonInfoUiModel,
        val amount: String,
    )

}
